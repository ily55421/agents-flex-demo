package com.agentsflex.showcase.knowledge;

import com.agentsflex.showcase.knowledge.parser.DocumentParserRegistry;
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

/**
 * 异步灌入流水线验证：KEYWORD_ONLY 模式下不依赖任何外部服务，
 * 覆盖提交→完成、失败收尾、排队取消与任务查询。
 */
class IngestPipelineTest {

    private Path tempDir;
    private com.zaxxer.hikari.HikariDataSource dataSource;
    private KnowledgeService service;
    private IngestPipeline pipeline;
    private IngestTaskStore taskStore;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ingest-test-");
        // 流水线是多线程写同一 DuckDB 文件：必须用单池复用连接（与生产 DuckDbConfig 一致），
        // DriverManagerDataSource 每操作新开连接会触发 DuckDB 文件锁互斥卡死
        dataSource = new com.zaxxer.hikari.HikariDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setJdbcUrl("jdbc:duckdb:" + tempDir.resolve("meta.duckdb")
                .toString().replace('\\', '/'));
        dataSource.setMaximumPoolSize(2);
        dataSource.setMinimumIdle(1);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        KnowledgeDocumentStore store = new KnowledgeDocumentStore(jdbc);
        store.ensureSchema();
        taskStore = new IngestTaskStore(jdbc);
        taskStore.ensureSchema();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setMmapPath(tempDir.resolve("mem").toString().replace('\\', '/'));
        properties.setSettingsPath(tempDir.resolve("settings.json").toString().replace('\\', '/'));
        properties.setSearchMode("KEYWORD_ONLY");
        properties.setTopK(3);
        properties.setEmbeddingEndpoint("");
        properties.setEmbeddingModel("");
        properties.setIngestConcurrency(1);
        properties.setIngestQueueCapacity(1);
        service = new KnowledgeService(properties, store, new KnowledgeSettingsStore(properties));
        DocumentParserRegistry registry = new DocumentParserRegistry(List.of());
        pipeline = new IngestPipeline(service, registry, taskStore, properties);
        pipeline.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        pipeline.shutdown();
        service.shutdown();
        dataSource.close();
        deleteRecursively(tempDir);
    }

    /**
     * 文本任务异步执行到 COMPLETED：docId 回填、文档可检索、进度 100。
     *
     * <p>标记与查询全部用中文：Tokenizer 对 CJK 占比过半的文本用 bigram，混入大段
     * ASCII 的标记会导致内容侧与查询侧分词策略不一致而永不命中（非线程问题）。</p>
     */
    @Test
    void completesTextTaskAndIndexesDocument() throws Exception {
        Map<String, Object> task = pipeline.submitText("异步文档", "可检索 独特标记异步入库 内容。");

        Map<String, Object> finished = awaitTerminal(task);
        assertThat(finished.get("status")).isEqualTo("COMPLETED");
        assertThat(String.valueOf(finished.get("docId"))).startsWith("kb-");
        assertThat((Integer) finished.get("progress")).isEqualTo(100);
        assertThat(finished.get("error")).isNull();
        assertThat((Integer) finished.get("chunkCount")).isGreaterThan(0);
        assertThat(service.search("独特标记异步入库", 3)).isNotEmpty();
    }

    /**
     * 空内容任务进入 FAILED，error 为中文原因，调用方不抛异常。
     */
    @Test
    void failsTaskOnBlankContentWithoutThrowing() throws Exception {
        Map<String, Object> task = pipeline.submitText("空白任务", "   ");

        Map<String, Object> finished = awaitTerminal(task);
        assertThat(finished.get("status")).isEqualTo("FAILED");
        assertThat(String.valueOf(finished.get("error"))).isNotBlank();
    }

    /**
     * 不存在的文档重建任务直接 FAILED 且给出中文原因。
     */
    @Test
    void failsReparseForMissingDocument() throws Exception {
        Map<String, Object> task = pipeline.submitReparse("kb-not-exist");

        Map<String, Object> finished = awaitTerminal(task);
        assertThat(finished.get("status")).isEqualTo("FAILED");
        assertThat(String.valueOf(finished.get("error"))).contains("无法重建");
    }

    /**
     * 排队中任务取消后进入 CANCELLED，不再执行入库。
     */
    @Test
    void cancelsQueuedTask() throws Exception {
        // 并发 1、队列容量 1：先占住工作线程，再提交并立即取消排队任务
        pipeline.submitText("占位任务", "先执行的内容，确保工作线程被占用。");
        Map<String, Object> queued = pipeline.submitText("被取消任务", "不应入库的标记CANCELXYZ。");
        assertThat(pipeline.cancel(String.valueOf(queued.get("taskId")))).isTrue();

        Map<String, Object> finished = awaitTerminal(queued);
        assertThat(finished.get("status")).isEqualTo("CANCELLED");
    }

    /**
     * 任务列表可查询且终态任务存在；不存在的任务查询返回 null。
     */
    @Test
    void listsRecentTasks() throws Exception {
        Map<String, Object> task = pipeline.submitText("列表任务", "列表可见性验证标记LISTX。");
        awaitTerminal(task);

        List<Map<String, Object>> tasks = pipeline.tasks();
        assertThat(tasks).isNotEmpty();
        assertThat(tasks.get(0).get("taskId")).isEqualTo(task.get("taskId"));
        assertThat(pipeline.task("task-none")).isNull();
    }

    /** 轮询等待任务进入终态（上限 10 秒），每次循环重新读取最新状态，避免测试脆裂。 */
    private Map<String, Object> awaitTerminal(Map<String, Object> task) throws InterruptedException {
        String taskId = String.valueOf(task.get("taskId"));
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> current = taskStore.find(taskId);
            if (current != null
                    && IngestTaskStore.terminalStatuses().contains(String.valueOf(current.get("status")))) {
                return current;
            }
            Thread.sleep(50);
        }
        return taskStore.find(taskId);
    }

    /** 递归删除临时目录；句柄未释放时静默跳过。 */
    private static void deleteRecursively(Path root) {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // Windows mmap 句柄释放滞后，留给系统临时目录清理
                }
            });
        } catch (java.io.IOException ignored) {
            // 同上
        }
    }
}
