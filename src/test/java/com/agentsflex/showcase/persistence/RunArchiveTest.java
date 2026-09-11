package com.agentsflex.showcase.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DuckDB 归档的跨实例持久化验证。
 * 使用临时文件数据库模拟“重启”：两个归档实例先后读写同一文件，确认 Agent 定义、
 * Run 快照与事件历史在实例之间保持一致。
 */
class RunArchiveTest {

    private Path dbFile;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("showcase-archive-", ".duckdb");
        Files.deleteIfExists(dbFile);
        url = "jdbc:duckdb:" + dbFile.toAbsolutePath().toString().replace('\\', '/');
    }

    @AfterEach
    void tearDown() {
        try {
            Files.deleteIfExists(dbFile);
        } catch (Exception ignored) {
            // 临时文件清理失败不影响断言结论
        }
    }

    /**
     * 创建指向临时 DuckDB 文件的归档实例，并完成幂等建表。
     *
     * @return 已就绪的 RunArchive
     */
    private RunArchive archive() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl(url);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        RunArchive archive = new RunArchive(jdbc, new ObjectMapper());
        archive.ensureSchema();
        return archive;
    }

    /**
     * 验证 Agent 定义、Run 快照与事件日志的写入、读取和跨实例恢复。
     */
    @Test
    void persistsAgentRunsAndEventsAcrossInstances() {
        Map<String, Object> agentView = map("agentId", "agent-1");

        Map<String, Object> runView = new LinkedHashMap<>();
        runView.put("runId", "run-1");
        runView.put("conversationId", "conv-1");
        runView.put("status", "COMPLETED");
        runView.put("createdAt", 1000L);
        runView.put("task", "市场研究");
        runView.put("events", new ArrayList<Object>());

        RunArchive first = archive();
        first.saveAgentDefinition("agent-1", agentView, 1000L);
        first.saveRunSnapshot("run-1", "conv-1", "agent-1", "COMPLETED", runView);
        first.appendEvent("run-1", "evt-1", 1, "MODEL_STARTED", 1100L,
                map("content", "思考中"));
        first.appendEvent("run-1", "evt-2", 2, "MODEL_TEXT_DELTA", 1200L,
                map("content", "结论"));

        assertThat(first.loadRunSnapshot("run-1")).containsEntry("runId", "run-1");
        assertThat(first.countAgentDefinitions()).isEqualTo(1);

        // 第二个实例读取同一文件，等价于重启后的归档恢复
        RunArchive second = archive();
        assertThat(second.loadAgentDefinitions()).hasSize(1);
        Map<String, Object> restored = second.loadRunSnapshot("run-1");
        assertThat(restored).containsEntry("status", "COMPLETED");
        assertThat(second.loadRunSnapshots()).hasSize(1);
        assertThat(second.loadEvents("run-1")).hasSize(2);

        // 快照更新后读取应返回最新状态，且不产生重复行
        runView.put("status", "FAILED");
        second.saveRunSnapshot("run-1", "conv-1", "agent-1", "FAILED", runView);
        assertThat(second.loadRunSnapshot("run-1")).containsEntry("status", "FAILED");
        assertThat(second.loadRunSnapshots()).hasSize(1);
    }

    /**
     * 验证同一归档实例内的增删改查与事件顺序。
     */
    @Test
    void eventsAreOrderedBySequenceAndIsolatedPerRun() {
        RunArchive archive = archive();
        archive.appendEvent("run-a", "a-1", 1, "MODEL_STARTED", 100L, map("seq", 1));
        archive.appendEvent("run-a", "a-2", 2, "MODEL_TEXT_DELTA", 200L, map("seq", 2));
        archive.appendEvent("run-b", "b-1", 5, "MODEL_STARTED", 300L, map("seq", 5));

        List<Map<String, Object>> events = archive.loadEvents("run-a");
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).containsEntry("seq", 1);
        assertThat(events.get(1)).containsEntry("seq", 2);
        assertThat(archive.loadEvents("run-b")).hasSize(1);
    }

    /**
     * 构造单字段有序映射，避免测试直接依赖 JDK 9+ 的 Map.of。
     *
     * @param key   字段名
     * @param value 字段值
     * @return 可变的有序映射
     */
    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
