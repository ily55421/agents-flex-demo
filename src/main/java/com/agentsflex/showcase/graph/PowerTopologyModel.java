package com.agentsflex.showcase.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

/**
 * 电力拓扑本体图谱的只读领域模型：加载 classpath 资源并建立检索索引。
 *
 * <p>资源由 CAD 拓扑解析工程（cad-to-svg-converter）产出：
 * <ul>
 *   <li>{@code graph/power-topology-graph.json} —— 图谱本体：meta（谓词中文词典）、
 *       tbox（23 类本体层级）、nodes（693 实例节点）、edges（3598 实例关系）</li>
 *   <li>{@code graph/power-topology-kb.json} —— 站点档案：9 个变电站的母线/主变/
 *       间隔/设备/连接/质量明细（源自《电力拓扑知识库》页面内嵌数据）</li>
 *   <li>{@code graph/power-topology-facts.jsonl} —— 1215 条“站|类别|事实”知识问答行</li>
 * </ul></p>
 *
 * <p>模型只负责解析与索引（按 id / 类别 / 站点 / 文本检索），不持有数据库连接；
 * Neo4j 入库见 {@link TopologyGraphService}，DuckDB 入库见 {@link GraphArchive}。</p>
 */
@Component
public class PowerTopologyModel {

    private static final Logger log = LoggerFactory.getLogger(PowerTopologyModel.class);

    private final ObjectMapper objectMapper;

    private volatile GraphBundle bundle;
    private volatile KbBundle kbBundle;
    private volatile List<GraphFact> facts = List.of();
    /** 图谱资源指纹，入库版本比对依据（资源变化 → 重新入库）。 */
    private volatile String sourceHash = "";
    private volatile long loadedAt;

    /** 节点 id → 节点、类别 → 节点、站点 → 节点 索引；加载后只读。 */
    private volatile Map<String, GraphNode> nodeById = Map.of();
    private volatile Map<String, List<GraphNode>> nodesByClass = Map.of();
    private volatile Map<String, List<GraphNode>> nodesByStation = Map.of();
    /** 相邻关系索引：节点 id → 关联边（含方向）。 */
    private volatile Map<String, List<Adjacency>> adjacency = Map.of();
    /** 小写检索索引：标签/别名/URI → 节点。 */
    private volatile Map<String, List<GraphNode>> searchIndex = Map.of();

    public PowerTopologyModel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 应用启动时加载资源并建索引；失败直接抛出（数据是图谱功能的根依赖）。 */
    @PostConstruct
    public void load() {
        try {
            byte[] graphBytes = readResource("graph/power-topology-graph.json");
            bundle = objectMapper.readValue(graphBytes, GraphBundle.class);
            kbBundle = objectMapper.readValue(
                    readResource("graph/power-topology-kb.json"), KbBundle.class);
            facts = parseFacts(readResource("graph/power-topology-facts.jsonl"));
            sourceHash = sha256(graphBytes);
            loadedAt = System.currentTimeMillis();
            buildIndexes();
            log.info("本体图谱模型已加载: {} 节点 / {} 关系 / {} 本体类 / {} 站点 / {} 事实",
                    bundle.nodes().size(), bundle.edges().size(),
                    bundle.tbox().nodes().size(), kbBundle.stations().size(), facts.size());
        } catch (IOException error) {
            throw new UncheckedIOException("本体图谱资源加载失败", error);
        }
    }

    // ---------------------------------------------------------------- 资源视图

    /** 图谱 JSON 的顶层结构（meta/tbox/nodes/edges，键名与源数据保持一致）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphBundle(
            @JsonProperty("meta") Meta meta,
            @JsonProperty("tbox") TBox tbox,
            @JsonProperty("nodes") List<GraphNode> nodes,
            @JsonProperty("edges") List<GraphEdge> edges) {
    }

    /** 谓词中文词典与命名空间。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String ns, String inst,
                       @JsonProperty("pred_cn") Map<String, String> predCn) {
    }

    /** 本体类层级（TBox）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TBox(List<TBoxNode> nodes, List<TBoxEdge> edges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TBoxNode(String id, String l, String en, String parent) {
        public String label() {
            return l;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TBoxEdge(String s, String p, String pc, String o) {
        public String predicate() {
            return p;
        }

        public String predCn() {
            return pc;
        }
    }

    /** 实例节点：URI、类别、标签、所属站、电压、别名、属性。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphNode(
            String id,
            @JsonProperty("c") String cls,
            @JsonProperty("l") String label,
            @JsonProperty("st") String station,
            @JsonProperty("v") String voltage,
            @JsonProperty("al") List<String> aliases,
            @JsonProperty("at") Map<String, Object> attrs) {

        public List<String> aliasList() {
            return aliases == null ? List.of() : aliases;
        }

        public Map<String, Object> attrMap() {
            return attrs == null ? Map.of() : attrs;
        }
    }

    /** 实例关系：源 URI、谓词、目标 URI。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphEdge(@JsonProperty("s") String source,
                            @JsonProperty("p") String predicate,
                            @JsonProperty("o") String target) {
    }

    /** 站点档案 JSON 顶层（generated + stations）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KbBundle(String generated, List<Map<String, Object>> stations) {
    }

    /** 知识问答事实行（station/category/text）。 */
    public record GraphFact(int seq, String station, String category, String text) {
    }

    /** 邻接关系：方向（out=从本节点出发 / in=指向本节点）、谓词、对端节点。 */
    public record Adjacency(boolean outgoing, String predicate, GraphNode peer) {
    }

    // ---------------------------------------------------------------- 访问器

    public GraphBundle bundle() {
        return bundle;
    }

    public KbBundle kbBundle() {
        return kbBundle;
    }

    public List<GraphFact> facts() {
        return facts;
    }

    public String sourceHash() {
        return sourceHash;
    }

    public long loadedAt() {
        return loadedAt;
    }

    public Map<String, String> predicateCn() {
        return bundle.meta().predCn();
    }

    /** 谓词中文名；未知谓词回退原词。 */
    public String predicateCn(String predicate) {
        return bundle.meta().predCn().getOrDefault(predicate, predicate);
    }

    public GraphNode node(String id) {
        return nodeById.get(id);
    }

    public Map<String, List<GraphNode>> nodesByClass() {
        return nodesByClass;
    }

    public Map<String, List<GraphNode>> nodesByStation() {
        return nodesByStation;
    }

    public List<Adjacency> neighborsOf(String nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    /** 类别中文/英文标签：TBox 中查中文，缺失回退类别名。 */
    public String classLabel(String cls) {
        return bundle.tbox().nodes().stream()
                .filter(node -> node.id().equals("cls:" + cls))
                .map(TBoxNode::label)
                .findFirst()
                .orElse(cls);
    }

    /**
     * 关键词检索节点：命中标签、别名或 URI（包含匹配，全部关键词需命中其一字段）。
     *
     * @param query   关键词（空格/逗号分隔多词，取交集）
     * @param station 站点过滤，null/blank 不过滤
     * @param limit   最大命中数
     * @return 按标签长度升序（更精确的短名优先）的节点列表
     */
    public List<GraphNode> search(String query, String station, int limit) {
        List<String> keywords = splitKeywords(query);
        if (keywords.isEmpty()) return List.of();
        List<GraphNode> hits = new ArrayList<>();
        for (List<GraphNode> bucket : searchIndex.values()) {
            for (GraphNode node : bucket) {
                if (hits.size() >= limit * 4) break;
                if (station != null && !station.isBlank() && !station.equals(node.station())) continue;
                String haystack = searchableText(node);
                boolean all = keywords.stream().allMatch(keyword -> {
                    String lower = keyword.toLowerCase(Locale.ROOT);
                    return haystack.contains(lower);
                });
                if (all) hits.add(node);
            }
        }
        return hits.stream()
                .distinct()
                .sorted(Comparator.comparingInt(node -> node.label().length()))
                .limit(limit)
                .toList();
    }

    /**
     * 站点子图：站内节点 + 与之相连的共享节点（电压等级/线路）及其关系。
     *
     * @param station 站名
     * @return nodes / edges；站点未知返回 null
     */
    public SubGraph stationSubGraph(String station) {
        List<GraphNode> stationNodes = nodesByStation.get(station);
        if (stationNodes == null) return null;
        Map<String, GraphNode> included = new LinkedHashMap<>();
        for (GraphNode node : stationNodes) included.put(node.id(), node);
        // 补齐与站内节点相连的共享实体（VoltageLevel / PowerLine 等）
        for (GraphNode node : List.copyOf(included.values())) {
            for (Adjacency adj : neighborsOf(node.id())) {
                GraphNode peer = adj.peer();
                if ("VoltageLevel".equals(peer.cls()) || "PowerLine".equals(peer.cls())) {
                    included.putIfAbsent(peer.id(), peer);
                }
            }
        }
        List<GraphEdge> edges = bundle.edges().stream()
                .filter(edge -> included.containsKey(edge.source()) && included.containsKey(edge.target()))
                .toList();
        return new SubGraph(station, List.copyOf(included.values()), edges);
    }

    /** 站点子图视图。 */
    public record SubGraph(String station, List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    // ---------------------------------------------------------------- 内部

    /** 建立四类只读索引；幂等，仅启动时调用一次。 */
    private void buildIndexes() {
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        Map<String, List<GraphNode>> byClass = new LinkedHashMap<>();
        Map<String, List<GraphNode>> byStation = new LinkedHashMap<>();
        Map<String, List<GraphNode>> search = new LinkedHashMap<>();
        for (GraphNode node : bundle.nodes()) {
            byId.put(node.id(), node);
            byClass.computeIfAbsent(node.cls(), key -> new ArrayList<>()).add(node);
            if (node.station() != null && !node.station().isBlank()) {
                byStation.computeIfAbsent(node.station(), key -> new ArrayList<>()).add(node);
            }
            for (String term : searchTerms(node)) {
                search.computeIfAbsent(term, key -> new ArrayList<>()).add(node);
            }
        }
        Map<String, List<Adjacency>> adj = new LinkedHashMap<>();
        for (GraphEdge edge : bundle.edges()) {
            GraphNode source = byId.get(edge.source());
            GraphNode target = byId.get(edge.target());
            if (source == null || target == null) continue;
            adj.computeIfAbsent(edge.source(), key -> new ArrayList<>())
                    .add(new Adjacency(true, edge.predicate(), target));
            adj.computeIfAbsent(edge.target(), key -> new ArrayList<>())
                    .add(new Adjacency(false, edge.predicate(), source));
        }
        nodeById = Map.copyOf(byId);
        nodesByClass = byClass.entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), List.copyOf(e.getValue())), Map::putAll);
        nodesByStation = byStation.entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), List.copyOf(e.getValue())), Map::putAll);
        adjacency = adj.entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), List.copyOf(e.getValue())), Map::putAll);
        searchIndex = search;
    }

    /** 检索词：标签 + 别名 + URI 去尾段，统一小写。 */
    private static List<String> searchTerms(GraphNode node) {
        List<String> terms = new ArrayList<>();
        terms.add(node.label().toLowerCase(Locale.ROOT));
        for (String alias : node.aliasList()) {
            terms.add(alias.toLowerCase(Locale.ROOT));
        }
        int slash = node.id().lastIndexOf('/');
        if (slash >= 0) terms.add(node.id().substring(slash + 1).toLowerCase(Locale.ROOT));
        return terms;
    }

    private static String searchableText(GraphNode node) {
        StringBuilder text = new StringBuilder(node.label().toLowerCase(Locale.ROOT)).append('\n');
        for (String alias : node.aliasList()) text.append(alias.toLowerCase(Locale.ROOT)).append('\n');
        return text.toString();
    }

    private static List<String> splitKeywords(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> keywords = new ArrayList<>();
        for (String part : query.split("[\\s,，、]+")) {
            if (!part.isBlank()) keywords.add(part.trim());
        }
        return keywords;
    }

    private List<GraphFact> parseFacts(byte[] jsonl) {
        List<GraphFact> result = new ArrayList<>();
        int seq = 0;
        for (String line : new String(jsonl, StandardCharsets.UTF_8).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            try {
                var node = objectMapper.readTree(trimmed);
                String station = node.path("station").asText("");
                String category = node.path("category").asText("");
                String text = node.path("text").asText("");
                if (text.isBlank()) continue;
                result.add(new GraphFact(++seq, station, category, text));
            } catch (IOException error) {
                log.warn("跳过无法解析的知识问答行: {}", trimmed.substring(0, Math.min(80, trimmed.length())));
            }
        }
        return List.copyOf(result);
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }
}
