package com.agentsflex.showcase.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 归档 Run 的只读 Trace 视图投影验证。
 * 覆盖重启后从 DuckDB 快照读回的 Run：Span/Metric 已随进程失效，但事件与预算计数仍可展示。
 */
class RunViewMapperArchivedTraceTest {

    /**
     * 快照含 budget 与 events 时，数值取自预算、事件原样带出、Span 与 Metric 为空。
     */
    @Test
    void projectsBudgetAndEventsWithoutSpans() {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("inputTokens", 54654);
        budget.put("outputTokens", 3481);
        budget.put("usedTokens", 58135);
        budget.put("usedDurationMs", 40544);
        List<Map<String, Object>> events = List.of(Map.of("eventId", "e1", "type", "TURN_COMPLETED"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", "run-1");
        snapshot.put("agentId", "agent-1");
        snapshot.put("agentVersion", "1");
        snapshot.put("status", "COMPLETED");
        snapshot.put("budget", budget);
        snapshot.put("events", new ArrayList<>(events));

        Map<String, Object> trace = RunViewMapper.archivedTrace(snapshot);

        assertThat(trace)
                .containsEntry("runId", "run-1")
                .containsEntry("status", "COMPLETED")
                .containsEntry("inputTokens", 54654L)
                .containsEntry("outputTokens", 3481L)
                .containsEntry("totalTokens", 58135L)
                .containsEntry("durationMs", 40544L)
                .containsEntry("archived", true);
        assertThat((List<?>) trace.get("spans")).isEmpty();
        assertThat((List<?>) trace.get("metrics")).isEmpty();
        assertThat((List<?>) trace.get("events")).hasSize(1);
    }

    /**
     * 缺 budget 或 events 的历史快照不能抛异常，数值需回落为 0 供面板直接参与运算。
     */
    @Test
    void toleratesSnapshotWithoutBudget() {
        Map<String, Object> snapshot = Map.of("runId", "run-2", "status", "FAILED");

        Map<String, Object> trace = RunViewMapper.archivedTrace(snapshot);

        assertThat(trace)
                .containsEntry("totalTokens", 0L)
                .containsEntry("durationMs", 0L)
                .containsEntry("archived", true);
        assertThat((List<?>) trace.get("events")).isEmpty();
    }
}
