package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeService 的 KEYWORD_ONLY 链路验证：不依赖任何外部 embedding 服务，
 * 覆盖入库、BM25 检索、文档删除、重建与工具输出格式。
 */
class KnowledgeServiceTest {

    private Path tempDir;
    private KnowledgeService service;
    private KnowledgeDocumentStore store;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("knowledge-test-");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl("jdbc:duckdb:" + tempDir.resolve("meta.duckdb")
                .toString().replace('\\', '/'));
        store = new KnowledgeDocumentStore(new JdbcTemplate(dataSource));
        store.ensureSchema();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setMmapPath(tempDir.resolve("mem").toString().replace('\\', '/'));
        properties.setSettingsPath(tempDir.resolve("settings.json").toString().replace('\\', '/'));
        properties.setSearchMode("KEYWORD_ONLY");
        properties.setTopK(3);
        // 不提供 embedding 默认值：整个链路应退化为 BM25 关键词检索
        properties.setEmbeddingEndpoint("");
        properties.setEmbeddingModel("");
        service = new KnowledgeService(properties, store, new KnowledgeSettingsStore(properties));
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
    }

    /**
     * 无 embedding 配置时仍可入库与关键词检索，命中携带标题与分数元数据。
     */
    @Test
    void storesAndSearchesWithKeywordModeWithoutEmbedding() {
        Map<String, Object> doc = service.addDocument("混合检索实践",
                "生产 RAG 系统采用向量 ANN 与 BM25 关键词混合召回，再用 RRF 融合排序。\n\n切片应保留段落边界并做少量重叠。",
                "MANUAL");
        assertThat(doc.get("docId")).asString().startsWith("kb-");
        assertThat((Integer) doc.get("chunkCount")).isGreaterThanOrEqualTo(2);

        List<Map<String, Object>> hits = service.search("BM25 关键词 混合召回", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).get("title")).isEqualTo("混合检索实践");
        assertThat(hits.get(0).get("mode")).isEqualTo("KEYWORD_ONLY");
        assertThat((Float) hits.get(0).get("score")).isGreaterThan(0f);
    }

    /**
     * 删除文档后其 namespace 的切片不再命中，DuckDB 元数据同步移除。
     */
    @Test
    void deletesDocumentChunksAndMetadata() {
        Map<String, Object> doc = service.addDocument("临时文档", "只有这份文档包含 独特标记XYZ 内容。", "MANUAL");
        assertThat(service.search("独特标记XYZ", 3)).isNotEmpty();

        service.deleteDocument((String) doc.get("docId"));
        assertThat(service.search("独特标记XYZ", 3)).isEmpty();
        assertThat(store.count()).isZero();
    }

    /**
     * 空内容拒绝入库；工具检索在无命中时返回说明文本而非异常。
     */
    @Test
    void rejectsBlankContentAndExplainsEmptyToolResult() {
        assertThatThrownBy(() -> service.addDocument("空", "  ", "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class);
        service.addDocument("有内容", "可检索的中文资料片段。", "MANUAL");
        assertThat(service.searchForTool("完全无关的zzz查询")).contains("没有找到");
    }

    /**
     * 重建清空向量与元数据，之后检索不再命中旧内容。
     */
    @Test
    void rebuildClearsVectorsAndMetadata() {
        service.addDocument("旧文档", "旧知识 内容标记ABC。", "MANUAL");
        assertThat(store.count()).isEqualTo(1);
        service.rebuild();
        assertThat(store.count()).isZero();
        assertThat(service.search("内容标记ABC", 3)).isEmpty();
    }
}
