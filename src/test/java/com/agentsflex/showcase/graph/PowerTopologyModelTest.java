package com.agentsflex.showcase.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本体图谱模型加载与索引的回归测试：锁定图谱资源的规模口径，
 * 防止资源被误改导致前端画布与入库数据悄然失真。
 */
class PowerTopologyModelTest {

    private PowerTopologyModel model;

    @BeforeEach
    void setUp() {
        model = new PowerTopologyModel(new ObjectMapper());
        model.load();
    }

    @Test
    void 资源规模与产出口径一致() {
        PowerTopologyModel.GraphBundle bundle = model.bundle();
        assertEquals(693, bundle.nodes().size());
        assertEquals(3598, bundle.edges().size());
        assertEquals(23, bundle.tbox().nodes().size());
        assertEquals(9, model.kbBundle().stations().size());
        assertEquals(1215, model.facts().size());
        assertNotNull(model.sourceHash());
        assertEquals(64, model.sourceHash().length());
    }

    @Test
    void 全部关系端点均存在对应节点() {
        PowerTopologyModel.GraphBundle bundle = model.bundle();
        long dangling = bundle.edges().stream()
                .filter(edge -> model.node(edge.source()) == null || model.node(edge.target()) == null)
                .count();
        assertEquals(0, dangling);
    }

    @Test
    void 关键词检索命中站与主变() {
        List<PowerTopologyModel.GraphNode> hits = model.search("藏木变", null, 10);
        assertTrue(hits.stream().anyMatch(node -> "Station".equals(node.cls())
                && node.label().contains("藏木")));
        List<PowerTopologyModel.GraphNode> transformer = model.search("主变102000147", null, 5);
        assertTrue(transformer.stream().anyMatch(node ->
                "PowerTransformer".equals(node.cls()) && node.label().contains("102000147")));
    }

    @Test
    void 站点子图包含站内实体与电压等级() {
        PowerTopologyModel.SubGraph subGraph = model.stationSubGraph("35kV藏木变");
        assertNotNull(subGraph);
        assertTrue(subGraph.nodes().size() > 20, "藏木变子图节点过少");
        assertTrue(subGraph.nodes().stream().anyMatch(node -> "VoltageLevel".equals(node.cls())));
        assertTrue(subGraph.nodes().stream().anyMatch(node -> "Bus".equals(node.cls())));
        assertTrue(subGraph.edges().size() > 20, "藏木变子图关系过少");
        // 未知的站返回 null
        assertTrue(model.stationSubGraph("不存在的站") == null);
    }

    @Test
    void 谓词中文与类中文可翻译() {
        assertEquals("下辖母线", model.predicateCn("has_bus"));
        assertEquals("unknown_pred", model.predicateCn("unknown_pred"));
        assertEquals("变电站", model.classLabel("Station"));
    }
}
