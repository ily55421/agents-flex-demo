package com.agentsflex.showcase.graph;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力拓扑本体图谱的 HTTP 边界：图谱画布视图、统计、站点档案、事实检索、
 * 节点详情、单站子图、只读 Cypher 与手动重建。控制器只做输入校验，
 * 解析与查询逻辑位于 {@link PowerTopologyModel}/{@link TopologyGraphService}/{@link GraphArchive}。
 */
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final TopologyGraphService graphService;
    private final PowerTopologyModel model;
    private final GraphArchive archive;

    public GraphController(TopologyGraphService graphService, PowerTopologyModel model, GraphArchive archive) {
        this.graphService = graphService;
        this.model = model;
        this.archive = archive;
    }

    /**
     * @return 图谱统计（实体/关系/本体类计数、按类别与谓词分布、入库状态）
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return graphService.summary();
    }

    /**
     * @return 图谱画布全量视图：谓词词典 + TBox 本体层级 + 实例节点（友好字段名）+ 关系
     */
    @GetMapping("/view")
    public Map<String, Object> view() {
        PowerTopologyModel.GraphBundle bundle = model.bundle();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("meta", Map.of("ns", bundle.meta().ns(), "inst", bundle.meta().inst(),
                "predCn", bundle.meta().predCn()));
        view.put("tbox", bundle.tbox());
        view.put("nodes", bundle.nodes().stream().map(graphService::nodeView).toList());
        view.put("edges", bundle.edges());
        return view;
    }

    /**
     * 站点清单：合并图谱实体计数与档案概要统计。
     *
     * @return 站列表（name/baseVoltage/summary/entities/classCounts）
     */
    @GetMapping("/stations")
    public List<Map<String, Object>> stations() {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : archive.stationSummaries()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.get("station"));
            row.put("baseVoltage", item.getOrDefault("baseVoltage", ""));
            row.put("summary", item.getOrDefault("summary", Map.of()));
            merged.put(String.valueOf(item.get("station")), row);
        }
        for (Map<String, Object> item : graphService.stations()) {
            merged.computeIfAbsent(String.valueOf(item.get("name")), name -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("baseVoltage", "");
                row.put("summary", Map.of());
                return row;
            });
            merged.get(String.valueOf(item.get("name"))).put("entities", item.get("entities"));
            merged.get(String.valueOf(item.get("name"))).put("classCounts", item.get("classCounts"));
        }
        return List.copyOf(merged.values());
    }

    /**
     * 单站完整档案：母线/主变/间隔/设备/连接/质量明细。
     *
     * @param name 站名
     * @return 档案文档；站不存在返回 404 语义错误
     */
    @GetMapping("/station/{name}")
    public Map<String, Object> stationDocument(@PathVariable String name) {
        Map<String, Object> doc = archive.stationDocument(name);
        if (doc == null) throw new IllegalArgumentException("图谱档案中不存在变电站: " + name);
        return doc;
    }

    /**
     * 单站子图：站内实体 + 相连电压等级/线路，可直接渲染站级拓扑。
     *
     * @param name 站名
     * @return nodes / edges 视图
     */
    @GetMapping("/station/{name}/subgraph")
    public Map<String, Object> stationSubGraph(@PathVariable String name) {
        Map<String, Object> view = graphService.stationGraph(name);
        if (view == null) throw new IllegalArgumentException("图谱中不存在变电站: " + name);
        return view;
    }

    /**
     * 知识问答事实检索：站点/类别/关键词组合过滤 + 类别清单。
     *
     * @param station  站名精确匹配（可选）
     * @param category 类别精确匹配（可选）
     * @param q        文本包含匹配（可选）
     * @param limit    单页条数，默认 50（≤500）
     * @param offset   起始偏移，默认 0
     * @return total / rows / categories
     */
    @GetMapping("/facts")
    public Map<String, Object> facts(@RequestParam(required = false) String station,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "50") int limit,
                                     @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> result = new LinkedHashMap<>(archive.searchFacts(station, category, q, limit, offset));
        result.put("categories", archive.factCategories());
        return result;
    }

    /**
     * 节点详情：属性/别名 + 按中文谓词分组的邻接关系。
     *
     * @param id 节点 URI（query 参数，URI 含斜杠不适合作路径段）
     * @return 详情视图；节点不存在返回 404 语义错误
     */
    @GetMapping("/node")
    public Map<String, Object> nodeDetail(@RequestParam String id) {
        Map<String, Object> detail = graphService.nodeDetail(id);
        if (detail == null) throw new IllegalArgumentException("图谱中不存在节点: " + id);
        return detail;
    }

    /**
     * 手动触发图谱重建入库（资源指纹比对，幂等）。
     *
     * @return 入库统计
     */
    @PostMapping("/import")
    public Map<String, Object> importGraph() {
        return graphService.importIfNeeded();
    }

    /**
     * 只读 Cypher 查询：供探索 Neo4j 图库；写操作关键字会被拒绝。
     *
     * @param body cypher 字段必填
     * @return 结果行（值统一转字符串，最多 200 行）
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, String> body) {
        List<Map<String, Object>> rows = graphService.runReadOnlyCypher(body.get("cypher"));
        return Map.of("rows", rows, "count", rows.size());
    }
}
