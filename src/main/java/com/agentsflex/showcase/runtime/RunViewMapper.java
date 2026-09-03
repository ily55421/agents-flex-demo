package com.agentsflex.showcase.runtime;

import com.agentsflex.agent.AgentSuspension;
import com.agentsflex.agent.AgentTurn;
import com.agentsflex.core.message.Message;
import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.message.ToolMessage;
import com.agentsflex.core.message.UserMessage;
import com.agentsflex.core.message.ToolCall;
import com.agentsflex.core.observability.ObservabilityAttributeKeys;
import com.agentsflex.showcase.model.CreateAgentRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Agents-Flex Snapshot 与 Demo 补充数据映射为稳定的前端 API 契约。
 *
 * <p>该类只做只读投影，不推进 Agent。预算、重试、压缩和 Trace 均保留来源标记，前端可以
 * 明确区分原生能力、OTel 数据与仅用于演示的成本估算。</p>
 */
final class RunViewMapper {

    /**
     * 工具类不持有状态，禁止实例化。
     */
    private RunViewMapper() {
    }

    /**
     * 合并原生 Turn Snapshot 与 Demo 控制面字段，生成主 Run API 响应。
     *
     * @param run  单次运行容器，提供预算、事件和控制状态
     * @param turn 从 TurnStore 恢复的最新原生 Snapshot
     * @return 前端所有运行面板共享的有序字段映射
     */
    static Map<String, Object> map(DemoRun run, AgentTurn turn) {
        // manualPause 只有在框架 Snapshot 已进入 WAITING_FOR_USER 后才对外生效，保证快照自洽。
        boolean manualPauseActive = run.manualPause
                && turn.getStatus() == com.agentsflex.agent.AgentTurnStatus.WAITING_FOR_USER;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", turn.getId());
        view.put("agentId", turn.getAgent().getId());
        view.put("agentVersion", turn.getAgent().getVersion());
        view.put("agent", run.agentDefinition.toView());
        view.put("conversationId", turn.getConversationId());
        view.put("task", run.task);
        view.put("status", turn.getStatus().name());
        view.put("phase", turn.getExecutionPoint().name());
        view.put("manualPause", manualPauseActive);
        view.put("pauseRequested", !manualPauseActive && run.pauseRequested.get());
        view.put("pauseRequestedAt", run.pauseRequestedAt);
        view.put("processing", run.processing.get());
        view.put("createdAt", turn.getCreatedAt());
        view.put("completedAt", turn.getCompletedAt());
        view.put("iterationCount", turn.getIterationCount());
        view.put("maxIterations", turn.getExecutionPolicy().getMaxIterations());
        view.put("stepCount", turn.getStepCount());
        view.put("maxSteps", turn.getExecutionPolicy().getMaxSteps());
        view.put("retryCount", turn.getRetryCount());
        view.put("maxRetries", run.maxRetries);
        view.put("toolCallCount", turn.getToolCallCount());
        view.put("finalOutput", turn.getFinalOutput());
        view.put("error", run.runtimeError != null ? run.runtimeError
                : turn.getError() == null ? null : turn.getError().getMessage());
        view.put("budgetExceededReason", turn.getBudgetExceededReason());
        view.put("pendingTask", pendingTask(turn));
        view.put("pendingToolCalls", toolCalls(turn.getPendingToolCalls()));
        view.put("messages", messages(run.chatMemory.getMessages(Integer.MAX_VALUE)));
        view.put("suspension", suspension(turn.getSuspension()));
        view.put("budget", budget(run, turn));
        view.put("compression", compression(run));
        view.put("retryPolicy", retryPolicy(turn));
        view.put("events", new ArrayList<>(run.events));
        view.put("capabilities", capabilities());
        return view;
    }

    /**
     * 将真实 OTel 导出数据与 Turn Token 统计组合为 Trace API 响应。
     *
     * @param run  包含该 Run 专属 Span/Metric exporter 的容器
     * @param turn 当前 Turn Snapshot
     * @return Trace、Metric、Token 与补充事件时间线
     */
    static Map<String, Object> trace(DemoRun run, AgentTurn turn) {
        // Span/Metric 是真实 OTel 导出；AgentEvent 作为补充生命周期时间线单独保留。
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("runId", turn.getId());
        trace.put("agentId", turn.getAgent().getId());
        trace.put("agentVersion", turn.getAgent().getVersion());
        trace.put("status", turn.getStatus().name());
        trace.put("durationMs", duration(turn));
        trace.put("inputTokens", turn.getInputTokens());
        trace.put("outputTokens", turn.getOutputTokens());
        trace.put("totalTokens", turn.getTotalTokens());
        trace.put("spans", spans(run));
        trace.put("metrics", metrics(run));
        trace.put("events", new ArrayList<>(run.events));
        trace.put("source", "AGENTS_FLEX_OPENTELEMETRY");
        return trace;
    }

    /**
     * 投影 Token、工具和墙钟时长预算，并计算仅用于 Demo 的成本估算。
     *
     * @param run  保存用户设置的预算上限
     * @param turn 提供框架累计用量
     * @return BudgetPanel 使用的预算视图
     */
    private static Map<String, Object> budget(DemoRun run, AgentTurn turn) {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("inputTokens", turn.getInputTokens());
        budget.put("outputTokens", turn.getOutputTokens());
        budget.put("usedTokens", turn.getTotalTokens());
        budget.put("tokenLimit", run.tokenBudget);
        budget.put("usedToolCalls", turn.getToolCallCount());
        budget.put("toolCallLimit", run.toolCallBudget);
        budget.put("usedDurationMs", duration(turn));
        budget.put("durationLimitMs",
                turn.getExecutionPolicy().getBudget().getMaxDurationMillis());
        budget.put("estimatedCost", Math.round(turn.getTotalTokens() * 0.0000025d * 10000d) / 10000d);
        budget.put("costSource", "DEMO_PROJECTION");
        budget.put("durationSemantics", "WALL_CLOCK_INCLUDING_HUMAN_WAIT");
        return budget;
    }

    /**
     * 投影上下文压缩前后计数、摘要、触发条件和生命周期时间。
     *
     * @param run 保存事件监听器累计的压缩信息
     * @return CompressionPanel 使用的压缩视图
     */
    private static Map<String, Object> compression(DemoRun run) {
        Map<String, Object> compression = new LinkedHashMap<>();
        compression.put("beforeMessages", run.compressionBeforeMessages);
        compression.put("pendingMessages", run.compressionPendingMessages);
        compression.put("afterMessages", run.compressionAfterMessages);
        compression.put("beforeTokens", run.compressionBeforeTokens);
        compression.put("afterTokens", run.compressionAfterTokens);
        compression.put("originalContext", new ArrayList<>(run.compressionOriginalContext));
        compression.put("summary", run.compressedSummary);
        compression.put("status", "SKIPPED".equals(run.compressionStatus)
                && run.compressedSummary == null ? "PENDING" : run.compressionStatus);
        compression.put("startedAt", run.compressionStartedAt);
        compression.put("completedAt", run.compressionCompletedAt);
        String decider = run.agentDefinition.configuration.getCompressionDecider();
        compression.put("trigger", decider);
        compression.put("condition", compressionCondition(run));
        compression.put("source", "AGENTS_FLEX_NATIVE");
        return compression;
    }

    /**
     * 将原生压缩决策器选择投影为便于界面识别的稳定表达式。
     */
    private static String compressionCondition(DemoRun run) {
        CreateAgentRequest configuration = run.agentDefinition.configuration;
        switch (configuration.getCompressionDecider()) {
            case "PENDING_TURNS":
                return "pendingTurnsAtLeast(" + configuration.getCompressionTurnThreshold() + ")";
            case "PENDING_TOKENS":
                return "pendingTokensAtLeast(" + configuration.getCompressionTokenThreshold() + ")";
            case "ALWAYS":
                return "always()";
            case "NEVER":
                return "never()";
            case "PENDING_MESSAGES":
            default:
                return "pendingMessagesAtLeast(" + configuration.getCompressionMessageThreshold() + ")";
        }
    }

    /**
     * 从 AgentExecutionPolicy 读取真实重试参数，避免前端复制默认值。
     *
     * @param turn 当前 Turn Snapshot
     * @return 最大次数、间隔、退避倍率和来源标记
     */
    private static Map<String, Object> retryPolicy(AgentTurn turn) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("maxRetries", turn.getExecutionPolicy().getRetryPolicy().getMaxRetries());
        value.put("initialDelayMillis",
                turn.getExecutionPolicy().getRetryPolicy().getInitialDelayMillis());
        value.put("maxDelayMillis",
                turn.getExecutionPolicy().getRetryPolicy().getMaxDelayMillis());
        value.put("multiplier", turn.getExecutionPolicy().getRetryPolicy().getMultiplier());
        value.put("source", "AGENTS_FLEX_NATIVE");
        return value;
    }

    /**
     * 将当前 Suspension 的恢复关联信息安全投影为 JSON。
     *
     * @param suspension 原生 Suspension，可为空
     * @return 表单/审批/重试恢复所需字段；无挂起时返回 {@code null}
     */
    private static Map<String, Object> suspension(AgentSuspension suspension) {
        if (suspension == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", suspension.getType().name());
        value.put("correlationId", suspension.getCorrelationId());
        value.put("message", suspension.getMessage());
        value.put("resumePhase", suspension.getResumeExecutionPoint().name());
        // metadata JSON 契约，确保既兼容现有前端，也不会丢失新版字段。
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (suspension.getMetadata() != null) {
            metadata.putAll(suspension.getMetadata());
        }
        putIfPresent(metadata, "schema", suspension.getSchema());
        putIfPresent(metadata, "formKey", suspension.getFormKey());
        putIfPresent(metadata, "inputTarget", suspension.getInputTarget());
        putIfPresent(metadata, "toolName", suspension.getToolName());
        putIfPresent(metadata, "approvalCode", suspension.getApprovalCode());
        putIfPresent(metadata, "approvalReason", suspension.getApprovalReason());
        putIfPresent(metadata, "approvalOutcome", suspension.getApprovalOutcome());
        putIfPresent(metadata, "arguments", suspension.getArguments());
        value.put("metadata", metadata);
        return value;
    }

    /**
     * 仅将非空的新版 Suspension 字段写入兼容 metadata，避免无关 null 污染响应。
     */
    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.putIfAbsent(key, value);
    }

    /**
     * 把待执行 ToolCall 转换为前端可展示的 ID、名称和结构化参数。
     *
     * @param calls 原生待处理 ToolCall 列表
     * @return JSON 安全的工具调用列表
     */
    private static List<Map<String, Object>> toolCalls(List<ToolCall> calls) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (calls == null) return values;
        for (ToolCall call : calls) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", call.getId());
            value.put("name", call.getName());
            value.put("arguments", call.getArgsMap());
            values.add(value);
        }
        return values;
    }

    /**
     * 将 Agents-Flex ChatMemory 投影为聊天消息。工具调用保留结构化参数，API 不拼接伪造文本。
     *
     * @param source 当前会话中的原生消息列表
     * @return 按时间顺序排列的用户、助手和工具消息
     */
    private static List<Map<String, Object>> messages(List<Message> source) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Message message : source) {
            if (message == null) continue;
            String content = message.getTextContent();
            List<Map<String, Object>> calls = message instanceof AiMessage
                    ? toolCalls(((AiMessage) message).getToolCalls()) : new ArrayList<>();
            if ((content == null || content.trim().isEmpty()) && calls.isEmpty()) continue;
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", message.getMessageId());
            value.put("role", message instanceof UserMessage ? "user"
                    : message instanceof ToolMessage ? "tool" : "assistant");
            value.put("content", content);
            value.put("reasoning", message instanceof AiMessage
                    ? ((AiMessage) message).getReasoningContent() : null);
            value.put("toolCalls", calls);
            values.add(value);
        }
        return values;
    }

    /**
     * 根据 Suspension、待处理工具和当前 Phase 生成操作员可读的待办说明。
     *
     * @param turn 当前 Turn Snapshot
     * @return 待办文本；终态且无待办时返回 {@code null}
     */
    private static String pendingTask(AgentTurn turn) {
        if (turn.getSuspension() != null) return turn.getSuspension().getMessage();
        if (!turn.getPendingToolCalls().isEmpty()) {
            return "等待执行工具 " + turn.getPendingToolCalls().get(0).getName();
        }
        if (turn.getStatus().isTerminal()) return null;
        return turn.getExecutionPoint() == com.agentsflex.agent.AgentTurnExecutionPoint.INVOKE_MODEL
                ? "等待模型推进" : "等待 Runtime 推进";
    }

    /**
     * 计算从创建到完成或当前时刻的墙钟时长，人工等待时间也包含在内。
     *
     * @param turn 当前 Turn Snapshot
     * @return 非负毫秒时长
     */
    private static long duration(AgentTurn turn) {
        long end = turn.getCompletedAt() > 0 ? turn.getCompletedAt() : System.currentTimeMillis();
        return Math.max(0, end - turn.getCreatedAt());
    }

    /**
     * 声明 Showcase 面板展示的能力及其真实来源，帮助区分原生与 Demo 控制能力。
     *
     * @return 固定顺序的能力来源列表
     */
    private static List<Map<String, String>> capabilities() {
        List<Map<String, String>> values = new ArrayList<>();
        values.add(capability("Event", "NATIVE"));
        values.add(capability("Human Approval", "NATIVE"));
        values.add(capability("Form Input", "NATIVE"));
        values.add(capability("Message Compression", "NATIVE"));
        values.add(capability("Suspend / Resume", "NATIVE + DEMO CONTROL"));
        values.add(capability("Budget Control", "NATIVE"));
        values.add(capability("Retry", "NATIVE"));
        values.add(capability("Trace + Metrics", "NATIVE OTEL"));
        return values;
    }

    /**
     * 将 SpanData 展平为 Trace 树需要的身份、父子关系、耗时和 GenAI 语义字段。
     *
     * @param run 提供按时间排序的 Span 快照
     * @return 可序列化的 Span 列表
     */
    private static List<Map<String, Object>> spans(DemoRun run) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (SpanData span : run.spanExporter.snapshot()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("traceId", span.getTraceId());
            value.put("spanId", span.getSpanId());
            value.put("parentSpanId", span.getParentSpanId());
            value.put("name", span.getName());
            value.put("kind", span.getKind().name());
            value.put("status", span.getStatus().getStatusCode().name());
            value.put("statusDescription", span.getStatus().getDescription());
            value.put("startTimeMs", span.getStartEpochNanos() / 1_000_000L);
            value.put("endTimeMs", span.getEndEpochNanos() / 1_000_000L);
            value.put("durationMs", Math.max(0,
                    (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000L));
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (Map.Entry<AttributeKey<?>, Object> entry : span.getAttributes().asMap().entrySet()) {
                attributes.put(entry.getKey().getKey(), entry.getValue());
            }
            value.put("attributes", attributes);
            value.put("provider", attributes.get(ObservabilityAttributeKeys.GEN_AI_PROVIDER_NAME.getKey()));
            value.put("model", attributes.get(ObservabilityAttributeKeys.GEN_AI_REQUEST_MODEL.getKey()));
            value.put("inputTokens", attributes.get(ObservabilityAttributeKeys.GEN_AI_USAGE_INPUT_TOKENS.getKey()));
            value.put("outputTokens", attributes.get(ObservabilityAttributeKeys.GEN_AI_USAGE_OUTPUT_TOKENS.getKey()));
            value.put("toolName", attributes.get(ObservabilityAttributeKeys.GEN_AI_TOOL_NAME.getKey()));
            value.put("arguments", attributes.get(ObservabilityAttributeKeys.GEN_AI_TOOL_ARGUMENTS.getKey()));
            value.put("result", attributes.get(ObservabilityAttributeKeys.GEN_AI_TOOL_RESULT.getKey()));
            value.put("output", attributes.get(ObservabilityAttributeKeys.GEN_AI_RESPONSE_CONTENT.getKey()));
            values.add(value);
        }
        return values;
    }

    /**
     * 将 OTel Sum/Histogram 指标统一归一为 value 与 count，供前端统计卡读取。
     *
     * @param run 提供最近一次累计 Metric 导出
     * @return 可序列化的 Metric 列表
     */
    private static List<Map<String, Object>> metrics(DemoRun run) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (MetricData metric : run.metricExporter.snapshot()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", metric.getName());
            value.put("unit", metric.getUnit());
            value.put("type", metric.getType().name());
            double sum = 0;
            long count = 0;
            for (PointData point : metric.getData().getPoints()) {
                if (metric.getType() == MetricDataType.LONG_SUM) {
                    sum += ((io.opentelemetry.sdk.metrics.data.LongPointData) point).getValue();
                } else if (metric.getType() == MetricDataType.DOUBLE_SUM) {
                    sum += ((io.opentelemetry.sdk.metrics.data.DoublePointData) point).getValue();
                } else if (metric.getType() == MetricDataType.HISTOGRAM) {
                    HistogramPointData histogram = (HistogramPointData) point;
                    sum += histogram.getSum();
                    count += histogram.getCount();
                }
            }
            value.put("value", sum);
            value.put("count", count);
            values.add(value);
        }
        return values;
    }

    /**
     * 创建单条能力来源记录。
     *
     * @param name   能力名称
     * @param source NATIVE、NATIVE OTEL 或 Demo 控制说明
     * @return 包含 name/source 的有序映射
     */
    private static Map<String, String> capability(String name, String source) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("source", source);
        return value;
    }
}
