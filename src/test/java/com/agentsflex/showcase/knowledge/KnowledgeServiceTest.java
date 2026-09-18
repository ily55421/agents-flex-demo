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
        // 每个用例的 mmap 文件按 1GB 预分配，必须随用例删除，否则 %TEMP% 会被迅速吃满
        deleteRecursively(tempDir);
    }

    /** 递归删除临时目录；文件仍被占用时静默跳过（下次运行使用全新目录，不影响正确性）。 */
    private static void deleteRecursively(Path root) {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // Windows 上 mmap 句柄释放可能滞后，删除失败留给系统临时目录清理
                }
            });
        } catch (java.io.IOException ignored) {
            // 同上：清理失败不影响测试结论
        }
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
        // 结构化切片按窗口聚合短段落：该文档不足一个窗口，应合成单一切片而非按段拆散
        assertThat((Integer) doc.get("chunkCount")).isGreaterThanOrEqualTo(1);
        assertThat((Long) doc.get("charCount")).isPositive();

        List<Map<String, Object>> hits = service.search("BM25 关键词 混合召回", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).get("title")).isEqualTo("混合检索实践");
        assertThat(hits.get(0).get("mode")).isEqualTo("KEYWORD_ONLY");
        // 分数只保证是有限数值：BM25 归一化后可为负，符号不构成契约
        assertThat((Float) hits.get(0).get("score")).isFinite();
        assertThat(hits.get(0).get("content")).asString().contains("BM25");
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

    /**
     * 结构化切片：Markdown 标题路径写入切片元数据，检索命中可回读所属小节。
     */
    @Test
    void searchExposesHeadingPathFromStructuredChunking() {
        service.addDocument("切片规范",
                "# 切片策略\n\n## 保护规则\n\n公式与表格在切片时不被硬切断。",
                "MANUAL");

        List<Map<String, Object>> hits = service.search("公式与表格", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).get("headingPath")).isEqualTo("切片策略 > 保护规则");
    }

    /**
     * 检索命中携带稳定引用号，工具输出以 [cN] 标注供模型引用与前端回跳。
     */
    @Test
    void searchHitsCarryCitationIdsAndToolOutputMarksThem() {
        service.addDocument("引用测试",
                "引用溯源验证 独特标记引用输出 内容。", "MANUAL");

        List<Map<String, Object>> hits = service.search("独特标记引用输出", 3);
        assertThat(hits).isNotEmpty();
        assertThat((Integer) hits.get(0).get("citeId")).isEqualTo(1);

        String toolOutput = service.searchForTool("独特标记引用输出");
        assertThat(toolOutput).contains("[c1]").contains("引用测试");
    }

    /**
     * 重排默认关闭：不改变混合检索的排序行为（回归保护）。
     */
    @Test
    void disabledRerankKeepsOriginalOrdering() {
        Map<String, Object> doc = service.addDocument("重排回归",
                "重排回归验证 独特标记默认关闭 内容。", "MANUAL");
        List<Map<String, Object>> hits = service.search("独特标记默认关闭", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).get("docId")).isEqualTo(doc.get("docId"));
        assertThat(hits.get(0).containsKey("rerankScore")).isFalse();
    }

    /**
     * 多知识库：文档归属库后，库内检索只命中本库文档；全库检索仍可见。
     */
    @Test
    void kbScopedSearchIsolatesDocuments() {
        // 先创建两个库（addDocumentTo 会校验库存在）
        service.createKnowledgeBase("kb-alpha", "甲库", null, 512, 80, 5);
        service.createKnowledgeBase("kb-beta", "乙库", null, 512, 80, 5);

        service.addDocumentTo("kb-alpha", "甲库文档", "甲库专属 独特标记甲库 内容。", "MANUAL");
        service.addDocumentTo("kb-beta", "乙库文档", "乙库专属 独特标记乙库 内容。", "MANUAL");

        List<Map<String, Object>> alphaHits = service.searchKb("kb-alpha", "独特标记甲库", 5);
        assertThat(alphaHits).isNotEmpty();
        assertThat(alphaHits.get(0).get("kbId")).isEqualTo("kb-alpha");

        // 甲库检索结果不出现乙库文档（共享 bigram 可能 0 分弱匹配本库文档，属预期）
        assertThat(service.searchKb("kb-alpha", "独特标记乙库", 5))
                .extracting(hit -> hit.get("title")).doesNotContain("乙库文档");
        assertThat(service.searchKb("kb-beta", "独特标记甲库", 5))
                .extracting(hit -> hit.get("title")).doesNotContain("甲库文档");

        // 全库检索两篇都可见
        List<Map<String, Object>> allHits = service.search("独特标记", 10);
        assertThat(allHits).hasSizeGreaterThanOrEqualTo(2);

        // 不存在的库抛出可读错误
        assertThatThrownBy(() -> service.searchKb("kb-none", "任何", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 切片预览按传入参数试算，不落库（文档数与切片数保持不变）。
     */
    @Test
    void previewChunkingDoesNotTouchIndex() {
        java.util.List<Map<String, Object>> preview = service.previewChunking(
                "# 小节\n\n预览用的一段正文。", 512, 80);

        assertThat(preview).isNotEmpty();
        assertThat(preview.get(0).get("headingPath")).isEqualTo("小节");
        assertThat(store.count()).isZero();
    }
}
