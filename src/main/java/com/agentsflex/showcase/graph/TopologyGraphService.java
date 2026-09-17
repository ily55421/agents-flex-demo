package com.agentsflex.showcase.graph;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;

/**
 * 电力拓扑本体图谱服务：把 {@link PowerTopologyModel} 加载的图谱资源
 * 版本化入库到内嵌 Neo4j，并提供面向界面与 Agent 工具的查询。
 *
 * <p>图谱模型（与图谱资源一一对应）：
 * <ul>
 *   <li>{@code (:Entity:Station {uri, cls, label, station, voltage, aliases, attrsJson})}
 *       等 693 个实例节点；第二个标签为本体类别（Station/Bus/Breaker…）</li>
 *   <li>{@code (:Entity)-[:has_bus {pred, pred_cn}]->(:Entity)} 等 3598 条关系，
 *       关系类型即谓词名（has_bus / connects_bus / directly_connected_to…）</li>
 *   <li>{@code (:GraphMeta {key, sourceHash, importedAt})} 入库版本标记，
 *       资源更新（SHA-256 变化）后启动时自动整库重建</li>
 * </ul></p>
 *
 * <p>配套的站点档案与事实由 {@link GraphArchive} 入 DuckDB；本服务在启动时
 * 统一协调两处入库（幂等，可经 REST 手动触发）。</p>
 */
@Service
public class TopologyGraphService {

    private static final Logger log = LoggerFactory.getLogger(TopologyGraphService.class);

    private final GraphDatabaseService graphDb;
    private final PowerTopologyModel model;
    private final GraphArchive archive;
    private final AtomicBoolean importing = new AtomicBoolean();
    private volatile String importedAt;
    private volatile boolean schemaReady;

    public TopologyGraphService(GraphDatabaseService graphDb, PowerTopologyModel model, GraphArchive archive) {
        this.graphDb = graphDb;
        this.model = model;
        this.archive = archive;
    }

    /** 应用启动后台预导入：首次创建 Neo4j 存储需要数秒，不阻塞主线程启动。 */
    @PostConstruct
    public void importInBackground() {
        Thread worker = new Thread(() -> {
            try {
                Map<String, Object> stats = importIfNeeded();
                if (Boolean.TRUE.equals(stats.get("changed"))) {
                    log.info("图谱启动入库完成: {}", stats);
                }
            } catch (RuntimeException error) {
                log.error("图谱后台入库失败", error);
            }
        }, "topology-graph-import");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 版本化入库：比对 Neo4j GraphMeta.sourceHash 与资源指纹，
     * 一致则跳过（幂等），不一致（首次/资源更新/旧文本图谱）则整库重建。
     *
     * @return 入库统计（changed=true 表示执行了重建）
     */
    public synchronized Map<String, Object> importIfNeeded() {
        if (!importing.compareAndSet(false, true)) {
            throw new IllegalStateException("图谱入库正在进行中，请稍后再试");
        }
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            String stored = readStoredHash();
            boolean changed = stored == null || !stored.equals(model.sourceHash());
            if (changed) {
                rebuildGraphDatabase();
                importedAt = java.time.LocalDateTime.now().withNano(0).toString();
            }
            Map<String, Object> archiveStats = archive.refresh(model);
            stats.put("changed", changed || Boolean.TRUE.equals(archiveStats.get("changed")));
            stats.put("graph", summary());
            stats.put("archive", archiveStats);
            return stats;
        } finally {
            importing.set(false);
        }
    }

    /** 读取已入库的图谱指纹；库为空或无标记返回 null。 */
    private String readStoredHash() {
        try (Transaction tx = graphDb.beginTx()) {
            Result rows = tx.execute("MATCH (m:GraphMeta {key: 'power-topology'}) "
                    + "RETURN m.sourceHash AS hash");
            String hash = rows.hasNext() ? String.valueOf(rows.next().get("hash")) : null;
            tx.commit();
            return hash;
        }
    }

    /** 整库重建：清空旧图（含旧文本解析图谱），按资源批量写入实例图与版本标记。 */
    private void rebuildGraphDatabase() {
        long startedAt = System.currentTimeMillis();
        ensureSchema();
        PowerTopologyModel.GraphBundle bundle = model.bundle();
        try (Transaction tx = graphDb.beginTx()) {
            tx.execute("MATCH (n) DETACH DELETE n");
            tx.commit();
        }
        try (Transaction tx = graphDb.beginTx()) {
            for (PowerTopologyModel.GraphNode node : bundle.nodes()) {
                tx.execute("CREATE (n:Entity {uri: $uri, cls: $cls, label: $label, "
                                + "station: $station, voltage: $voltage, aliases: $aliases, attrsJson: $attrs}) "
                                + "SET n:`" + safeLabel(node.cls()) + "`",
                        Map.of("uri", node.id(),
                                "cls", node.cls(),
                                "label", node.label(),
                                "station", node.station() == null ? "" : node.station(),
                                "voltage", node.voltage() == null ? "" : node.voltage(),
                                "aliases", node.aliasList(),
                                "attrs", jsonOf(node.attrMap())));
            }
            for (PowerTopologyModel.GraphEdge edge : bundle.edges()) {
                tx.execute("MATCH (a:Entity {uri: $s}), (b:Entity {uri: $o}) "
                                + "CREATE (a)-[r:`" + safeRelType(edge.predicate()) + "` "
                                + "{pred: $p, pred_cn: $pc}]->(b)",
                        Map.of("s", edge.source(), "o", edge.target(),
                                "p", edge.predicate(), "pc", model.predicateCn(edge.predicate())));
            }
            tx.execute("MERGE (m:GraphMeta {key: 'power-topology'}) "
                            + "SET m.sourceHash = $hash, m.nodeCount = $nodes, m.edgeCount = $edges, "
                            + "m.importedAt = $ts",
                    Map.of("hash", model.sourceHash(), "nodes", bundle.nodes().size(),
                            "edges", bundle.edges().size(),
                            "ts", java.time.LocalDateTime.now().withNano(0).toString()));
            tx.commit();
        }
        log.info("Neo4j 图谱入库完成: {} 节点 / {} 关系，耗时 {} ms",
                bundle.nodes().size(), bundle.edges().size(), System.currentTimeMillis() - startedAt);
    }

    /** 建立 Entity.uri 索引（已存在则忽略）。 */
    private void ensureSchema() {
        if (schemaReady) return;
        try (Transaction tx = graphDb.beginTx()) {
            tx.schema().indexFor(Label.label("Entity")).on("uri").create();
            tx.commit();
            log.info("已创建 Neo4j Entity.uri 索引");
        } catch (Exception alreadyExists) {
            // 重复启动时索引已存在，忽略约束冲突即可
        }
        schemaReady = true;
    }

    /**
     * 图谱统计：模型资源统计 + Neo4j 实际库内计数 + 入库时间与指纹一致性。
     *
     * @return 统计视图
     */
    public Map<String, Object> summary() {
        PowerTopologyModel.GraphBundle bundle = model.bundle();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("source", "graph/power-topology-graph.json");
        view.put("sourceHash", model.sourceHash());
        view.put("hashMatch", model.sourceHash().equals(readStoredHash()));
        view.put("importedAt", importedAt);
        view.put("entities", bundle.nodes().size());
        view.put("edges", bundle.edges().size());
        view.put("tboxClasses", bundle.tbox().nodes().size());
        view.put("tboxEdges", bundle.tbox().edges().size());
        view.put("stations", model.kbBundle().stations().size());
        view.put("archiveStations", archive.stationCount());
        view.put("archiveFacts", archive.factCount());
        Map<String, Long> byClass = new LinkedHashMap<>();
        model.nodesByClass().entrySet().stream()
                .sorted(Map.Entry.<String, List<PowerTopologyModel.GraphNode>>comparingByValue(
                        java.util.Comparator.comparingInt(List::size)).reversed())
                .forEach(entry -> byClass.put(entry.getKey(), (long) entry.getValue().size()));
        view.put("byClass", byClass);
        Map<String, Long> byPredicate = new LinkedHashMap<>();
        bundle.edges().forEach(edge ->
                byPredicate.merge(edge.predicate(), 1L, Long::sum));
        view.put("byPredicate", byPredicate);
        return view;
    }

    /**
     * 站点清单：站内实体计数与主要类别分解。
     *
     * @return 站清单（按站名排序）
     */
    public List<Map<String, Object>> stations() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : model.nodesByStation().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            Map<String, Long> classCounts = new LinkedHashMap<>();
            for (PowerTopologyModel.GraphNode node : entry.getValue()) {
                classCounts.merge(node.cls(), 1L, Long::sum);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("entities", entry.getValue().size());
            item.put("classCounts", classCounts);
            result.add(item);
        }
        return result;
    }

    /**
     * 单站子图：站内实体 + 相连的电压等级/线路节点与关系，可直接渲染拓扑图。
     *
     * @param name 站名
     * @return nodes / edges 视图；站不存在返回 null
     */
    public Map<String, Object> stationGraph(String name) {
        PowerTopologyModel.SubGraph subGraph = model.stationSubGraph(name);
        if (subGraph == null) return null;
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (PowerTopologyModel.GraphNode node : subGraph.nodes()) {
            nodes.add(nodeView(node));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (PowerTopologyModel.GraphEdge edge : subGraph.edges()) {
            edges.add(Map.of("from", edge.source(), "to", edge.target(),
                    "type", edge.predicate(), "typeCn", model.predicateCn(edge.predicate())));
        }
        return Map.of("station", name, "nodes", nodes, "edges", edges);
    }

    /**
     * 节点详情：节点视图 + 按谓词分组的邻接关系（中文谓词名）。
     *
     * @param id 节点 URI
     * @return 详情视图；节点不存在返回 null
     */
    public Map<String, Object> nodeDetail(String id) {
        PowerTopologyModel.GraphNode node = model.node(id);
        if (node == null) return null;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("node", nodeView(node));
        Map<String, List<Map<String, Object>>> outgoing = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> incoming = new LinkedHashMap<>();
        for (PowerTopologyModel.Adjacency adjacency : model.neighborsOf(id)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", adjacency.peer().id());
            entry.put("label", adjacency.peer().label());
            entry.put("cls", adjacency.peer().cls());
            entry.put("clsCn", model.classLabel(adjacency.peer().cls()));
            if (adjacency.outgoing()) {
                outgoing.computeIfAbsent(model.predicateCn(adjacency.predicate()),
                        key -> new ArrayList<>()).add(entry);
            } else {
                incoming.computeIfAbsent(model.predicateCn(adjacency.predicate()),
                        key -> new ArrayList<>()).add(entry);
            }
        }
        view.put("outgoing", outgoing);
        view.put("incoming", incoming);
        return view;
    }

    /**
     * 供 Agent 工具 query_topology 调用的关键词检索：命中标签/别名的实体，
     * 返回带中文谓词的邻接关系文本。
     *
     * @param query 关键词（可多个，空格/逗号分隔）
     * @return 可读文本；无命中时返回说明
     */
    public String searchForTool(String query) {
        List<PowerTopologyModel.GraphNode> hits = model.search(query, null, 12);
        if (hits.isEmpty()) {
            return "图谱中没有找到与「" + query + "」相关的实体。可换关键词（如站名、断路器编号、母线、线路名）重试。";
        }
        StringBuilder output = new StringBuilder("电力拓扑图谱命中 ").append(hits.size()).append(" 个实体：\n");
        for (PowerTopologyModel.GraphNode hit : hits) {
            output.append("【").append(model.classLabel(hit.cls())).append("】").append(hit.label());
            if (hit.station() != null && !hit.station().isBlank()) {
                output.append("（").append(hit.station()).append("）");
            }
            output.append('\n');
            int listed = 0;
            for (PowerTopologyModel.Adjacency adjacency : model.neighborsOf(hit.id())) {
                if (listed++ >= 8) {
                    output.append("  … 其余 ").append(model.neighborsOf(hit.id()).size() - 8).append(" 条关系略\n");
                    break;
                }
                output.append(adjacency.outgoing() ? "  → " : "  ← ")
                        .append(model.predicateCn(adjacency.predicate())).append(' ')
                        .append(model.classLabel(adjacency.peer().cls())).append('：')
                        .append(adjacency.peer().label()).append('\n');
            }
        }
        return output.toString().trim();
    }

    /**
     * 执行只读 Cypher：供界面探索；拦截写关键字，兜底只读事务。
     *
     * @param cypher Cypher 查询
     * @return 行列表（值统一转字符串，最多 200 行）
     * @throws IllegalArgumentException 含写操作关键字或缺少 RETURN 时
     */
    public List<Map<String, Object>> runReadOnlyCypher(String cypher) {
        if (cypher == null || cypher.isBlank()) throw new IllegalArgumentException("Cypher 不能为空");
        String upper = cypher.toUpperCase(Locale.ROOT);
        for (String forbidden : new String[]{"CREATE", "MERGE", "DELETE", "DETACH", "SET ", "REMOVE",
                "DROP", "LOAD CSV", "FOREACH", "CALL {"}) {
            if (upper.contains(forbidden)) {
                throw new IllegalArgumentException("只读接口禁止包含写操作关键字: " + forbidden.trim());
            }
        }
        if (!upper.contains("RETURN")) throw new IllegalArgumentException("查询必须包含 RETURN");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            Result result = tx.execute(cypher, Collections.emptyMap());
            int limit = 200;
            while (result.hasNext() && rows.size() < limit) {
                Map<String, Object> row = result.next();
                Map<String, Object> safeRow = new LinkedHashMap<>();
                row.forEach((key, value) -> safeRow.put(key, String.valueOf(value)));
                rows.add(safeRow);
            }
            tx.commit();
        }
        return rows;
    }

    // ---------------------------------------------------------------- 内部

    /** 节点的 JSON 安全视图（供 REST 返回与前端渲染，字段名友好化）。 */
    public Map<String, Object> nodeView(PowerTopologyModel.GraphNode node) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", node.id());
        view.put("cls", node.cls());
        view.put("clsCn", model.classLabel(node.cls()));
        view.put("label", node.label());
        view.put("station", node.station());
        view.put("voltage", node.voltage());
        view.put("aliases", node.aliasList());
        view.put("attrs", node.attrMap());
        return view;
    }

    /** 类别名作为 Neo4j 标签是安全的（字母数字），防御性替换下划线。 */
    private static String safeLabel(String cls) {
        return cls.replaceAll("[^A-Za-z0-9]", "_");
    }

    /** 谓词名作为关系类型（has_bus 等），防御性替换非法字符。 */
    private static String safeRelType(String predicate) {
        return predicate.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String jsonOf(Map<String, Object> attrs) {
        if (attrs.isEmpty()) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(attrs);
        } catch (Exception error) {
            return "{}";
        }
    }
}
