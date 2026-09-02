package com.agentsflex.showcase.runtime;

import com.agentsflex.showcase.model.ApprovalRequest;
import com.agentsflex.showcase.model.CreateAgentRequest;
import com.agentsflex.showcase.model.CreateRunRequest;
import com.agentsflex.showcase.config.ModelProperties;
import com.agentsflex.showcase.demo.ResearchAgentFactory;
import com.agentsflex.showcase.demo.ShowcaseChatModel;
import com.agentsflex.showcase.demo.SummaryChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowcaseRuntimeIntegrationTest {

    private final ShowcaseRuntime runtime = testRuntime();

    /**
     * 为集成测试装配确定性模型。生产 Spring 容器不会调用该方法，只会注入真实 OpenAI-compatible 模型。
     *
     * @return 不访问外部网络且完整经过 Agents-Flex 原生链路的 Runtime
     */
    private static ShowcaseRuntime testRuntime() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("showcase-test");
        properties.setEndpoint("showcase://deterministic");
        properties.setApiKey("integration-test-key");
        properties.setModel("showcase-deterministic-1");
        ResearchAgentFactory factory = new ResearchAgentFactory(
                new ShowcaseChatModel(), new SummaryChatModel(), properties);
        return new ShowcaseRuntime(factory);
    }

    /**
     * 每个测试结束后释放线程池和 OTel Route，避免后台任务影响下一用例。
     */
    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    /**
     * 验证表单、工具、两次持久化重试、人工审批、压缩条件检查与真实 OTel Trace 的完整成功链路。
     */
    @Test
    void completesTheNativeFormRetryApprovalAndTraceFlow() throws Exception {
        CreateRunRequest request = request(runtime, 4000);

        Map<String, Object> created = runtime.create(request);
        String runId = (String) created.get("runId");
        assertThat(created.get("status")).isEqualTo("READY");
        Map<String, Object> compression = nested(created, "compression");
        assertThat(compression.get("source")).isEqualTo("AGENTS_FLEX_NATIVE");
        assertThat((List<?>) compression.get("originalContext")).isEmpty();
        assertThat(created.get("messages")).asList().hasSize(1);
        assertThat(nested(created, "budget").get("durationLimitMs")).isEqualTo(1800000L);
        assertThat(nested(created, "budget").get("durationSemantics"))
                .isEqualTo("WALL_CLOCK_INCLUDING_HUMAN_WAIT");
        assertThat(compression.get("status")).isEqualTo("PENDING");
        assertThat(compression.get("trigger")).isEqualTo("PENDING_MESSAGES");

        runtime.start(runId);
        Map<String, Object> waitingForUser = awaitStatus(runId, "WAITING_FOR_USER");

        // 行业必须接受开放文本；报告深度仍由枚举约束，但不能由前端静默代选。
        Map<String, Object> suspension = nested(waitingForUser, "suspension");
        Map<String, Object> metadata = nested(suspension, "metadata");
        Map<String, Object> schema = nested(metadata, "schema");
        Map<String, Object> properties = nested(schema, "properties");
        assertThat(nested(properties, "industry"))
                .containsEntry("type", "string")
                .containsKey("description")
                .doesNotContainKey("enum");
        assertThat(nested(properties, "depth").get("enum"))
                .isEqualTo(Arrays.asList("执行摘要", "标准分析", "深度研究"));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("industry", "新能源汽车");
        form.put("market", "中国");
        form.put("timeRange", "2025-2026");
        form.put("depth", "执行摘要");
        runtime.submitForm(runId, form);
        Map<String, Object> waitingForApproval = awaitStatus(runId, "WAITING_FOR_APPROVAL");

        assertThat(listOfMaps(waitingForApproval, "messages"))
                .filteredOn(message -> "tool".equals(message.get("role")))
                .extracting(message -> message.get("content"))
                .anySatisfy(content -> assertThat(content).asString().contains("新能源汽车"));
        assertThat(waitingForApproval.get("retryCount")).isEqualTo(2);
        assertThat(waitingForApproval.get("toolCallCount")).isEqualTo(4);

        ApprovalRequest approval = new ApprovalRequest();
        approval.setApproved(true);
        approval.setApprover("integration-test");
        runtime.approve(runId, approval);
        Map<String, Object> completed = awaitStatus(runId, "COMPLETED");

        assertThat(completed.get("toolCallCount")).isEqualTo(5);
        assertThat(completed.get("finalOutput")).asString().contains("executive-briefing");
        assertThat(eventTypes(completed)).contains(
                "TURN_SUSPENDED",
                "RETRY_SCHEDULED",
                "TOOL_APPROVAL_REQUESTED",
                "TURN_COMPLETED");

        Map<String, Object> trace = runtime.trace(runId);
        assertThat(trace.get("source")).isEqualTo("AGENTS_FLEX_OPENTELEMETRY");
        List<Map<String, Object>> spans = listOfMaps(trace, "spans");
        assertThat(spans).extracting(span -> span.get("name"))
                .contains("agent.run");
        // Agents-Flex 2.2.8 的可观测拦截器对自定义离线 ChatModel 不再强制生成子 Span；
        // 生产 OpenAIChatModel 仍会生成 Chat/Tool Span。若测试替身提供子 Span，则继续校验内容。
        List<Map<String, Object>> publishSpans = spans.stream()
                .filter(span -> "tool.publish_report".equals(span.get("name"))).collect(java.util.stream.Collectors.toList());
        if (!publishSpans.isEmpty()) {
            assertThat(publishSpans).singleElement().satisfies(span -> {
                assertThat(span.get("arguments")).asString().contains("executive-briefing");
                assertThat(span.get("result")).asString().contains("报告已发布");
            });
        }

        // 第一轮完成后在同一 ChatMemory 中创建新的原生 Turn，验证持续对话与流式事件不会丢失。
        String conversationId = (String) completed.get("conversationId");
        int firstRoundMessages = ((List<?>) completed.get("messages")).size();
        Map<String, Object> followUp = runtime.continueConversation(runId, "请用三句话总结刚才的结论");
        String followUpRunId = (String) followUp.get("runId");
        assertThat(followUpRunId).isNotEqualTo(runId);
        assertThat(followUp.get("conversationId")).isEqualTo(conversationId);
        assertThat((List<?>) followUp.get("messages")).hasSize(firstRoundMessages + 1);

        runtime.start(followUpRunId);
        Map<String, Object> followUpCompleted = awaitStatus(followUpRunId, "COMPLETED");
        assertThat((List<?>) followUpCompleted.get("messages")).hasSizeGreaterThan(firstRoundMessages + 1);
        assertThat(eventTypes(followUpCompleted)).contains("MODEL_TEXT_DELTA", "TURN_COMPLETED");
    }

    /**
     * 验证 Run 只能引用已创建 Agent，并且完整表单配置进入真实 Builder 与 Snapshot 投影。
     */
    @Test
    void createsAgentBeforeRunAndAppliesEveryExposedConfiguration() {
        CreateAgentRequest configuration = new CreateAgentRequest();
        configuration.setName("自定义研究 Agent");
        configuration.setVersion("2026.9");
        configuration.setDescription("用于验证完整配置贯通");
        configuration.setInstructions("只使用中文回答，并严格遵守工具审批策略。");
        configuration.setMaxIterations(7);
        configuration.setMaxSteps(29);
        configuration.setMaxAttachedTurns(5);
        configuration.setMaxAttachedTokens(2400);
        configuration.setMaxAttachedMessages(33);
        configuration.setMaxInputTokens(3200);
        configuration.setMaxOutputTokens(1800);
        configuration.setMaxTotalTokens(4500);
        configuration.setMaxToolCalls(7);
        configuration.setMaxDurationMillis(900000);
        configuration.setMaxRetries(3);
        configuration.setInitialDelayMillis(100);
        configuration.setMaxDelayMillis(500);
        configuration.setRetryMultiplier(1.5d);
        configuration.setModelRetryEnabled(false);
        configuration.setModelRetryCount(6);
        configuration.setModelRetryInitialDelayMillis(750);
        configuration.setCompressionDecider("PENDING_TURNS");
        configuration.setCompressionMessageThreshold(12);
        configuration.setCompressionTurnThreshold(3);
        configuration.setCompressionTokenThreshold(2400);
        configuration.setCompressionMode("PER_MESSAGE");
        configuration.setCompressionPerMessageRequest("按 messageId 返回摘要数组");
        configuration.setCompressionInstruction("保留测试约束");
        configuration.setCompressionModelCallTimeoutMillis(30000);
        configuration.setCompressionMaxInputCharacters(12000);
        configuration.setCompressionMaxOutputCharacters(2000);
        configuration.setToolErrorStrategy("FAIL_RUN");

        Map<String, Object> agent = runtime.createAgent(configuration);
        assertThat(agent)
                .containsEntry("name", "自定义研究 Agent")
                .containsEntry("version", "2026.9")
                .containsEntry("maxAttachedTurns", 5)
                .containsEntry("maxAttachedTokens", 2400L)
                .containsEntry("maxAttachedMessages", 33)
                .containsEntry("maxInputTokens", 3200L)
                .containsEntry("maxOutputTokens", 1800L)
                .containsEntry("modelRetryEnabled", false)
                .containsEntry("modelRetryCount", 6)
                .containsEntry("modelRetryInitialDelayMillis", 750)
                .containsEntry("compressionDecider", "PENDING_TURNS")
                .containsEntry("compressionTurnThreshold", 3)
                .containsEntry("compressionTokenThreshold", 2400L)
                .containsEntry("compressionMode", "PER_MESSAGE")
                .containsEntry("compressionPerMessageRequest", "按 messageId 返回摘要数组")
                .containsEntry("compressionInstruction", "保留测试约束")
                .containsEntry("compressionModelCallTimeoutMillis", 30000L)
                .containsEntry("compressionMaxInputCharacters", 12000L)
                .containsEntry("compressionMaxOutputCharacters", 2000L)
                .containsEntry("toolErrorStrategy", "FAIL_RUN");

        CreateRunRequest request = new CreateRunRequest();
        request.setAgentId((String) agent.get("agentId"));
        request.setTask("验证配置");
        Map<String, Object> run = runtime.create(request);

        assertThat(run).containsEntry("maxIterations", 7).containsEntry("maxSteps", 29);
        assertThat(nested(run, "budget"))
                .containsEntry("tokenLimit", 4500L)
                .containsEntry("toolCallLimit", 7)
                .containsEntry("durationLimitMs", 900000L);
        assertThat(nested(run, "retryPolicy"))
                .containsEntry("maxRetries", 3)
                .containsEntry("initialDelayMillis", 100L)
                .containsEntry("maxDelayMillis", 500L)
                .containsEntry("multiplier", 1.5d);
        assertThat(nested(run, "compression").get("condition"))
                .isEqualTo("pendingTurnsAtLeast(3)");

        CreateRunRequest missingAgent = new CreateRunRequest();
        missingAgent.setAgentId("missing-agent");
        missingAgent.setTask("不能绕过 Agent 创建");
        assertThatThrownBy(() -> runtime.create(missingAgent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Agent not found");
    }

    /**
     * 初始重试间隔大于最大间隔时必须在创建 Agent 阶段明确拒绝。
     */
    @Test
    void rejectsAnInvertedRetryDelayRange() {
        CreateAgentRequest configuration = new CreateAgentRequest();
        configuration.setInitialDelayMillis(1001);
        configuration.setMaxDelayMillis(1000);

        assertThatThrownBy(() -> runtime.createAgent(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大重试间隔");
    }

    /**
     * 验证 READY Run 可以挂起，并通过原生 Suspension 恢复同一个 Turn。
     */
    @Test
    void manuallySuspendsAndResumesTheSameNativeTurn() throws Exception {
        String runId = (String) runtime.create(request(runtime, 4000)).get("runId");

        Map<String, Object> suspended = runtime.suspend(runId);
        assertThat(suspended.get("status")).isEqualTo("WAITING_FOR_USER");
        assertThat(suspended.get("manualPause")).isEqualTo(true);

        runtime.resume(runId);
        Map<String, Object> waiting = awaitStatus(runId, "WAITING_FOR_USER");
        assertThat(waiting.get("runId")).isEqualTo(runId);
        assertThat(waiting.get("manualPause")).isEqualTo(false);
        assertThat(eventTypes(waiting)).contains("TURN_SUSPENDED", "TURN_RESUMED");
    }

    /**
     * 验证运行中的暂停请求不会抢占工具，而是在当前原子 Step 完成后生效。
     */
    @Test
    void honorsAnInFlightPauseRequestAtTheNextStepBoundary() throws Exception {
        String runId = (String) runtime.create(request(runtime, 4000)).get("runId");
        runtime.start(runId);
        awaitStatus(runId, "WAITING_FOR_USER");

        runtime.submitForm(runId, formValues());
        awaitProcessing(runId);
        Map<String, Object> requested = runtime.suspend(runId);
        assertThat(requested.get("pauseRequested")).isEqualTo(true);

        Map<String, Object> suspended = awaitManualPause(runId);
        assertThat(suspended.get("status")).isEqualTo("WAITING_FOR_USER");
        assertThat(suspended.get("manualPause")).isEqualTo(true);
        assertThat(suspended.get("stepCount")).isEqualTo(2);

        runtime.resume(runId);
        awaitStatus(runId, "WAITING_FOR_APPROVAL");
    }

    /**
     * 验证累计 Token 超过原生预算时进入 BUDGET_EXCEEDED 并发布对应事件。
     */
    @Test
    void terminatesWhenTheNativeTokenBudgetIsExceeded() throws Exception {
        CreateRunRequest request = request(runtime, 500);
        String runId = (String) runtime.create(request).get("runId");

        runtime.start(runId);
        awaitStatus(runId, "WAITING_FOR_USER");
        runtime.submitForm(runId, formValues());

        Map<String, Object> exceeded = awaitStatus(runId, "BUDGET_EXCEEDED");
        assertThat(exceeded.get("budgetExceededReason")).asString()
                .contains("maxTotalTokens");
        assertThat(eventTypes(awaitEvent(runId, "BUDGET_EXCEEDED")))
                .contains("BUDGET_EXCEEDED");
    }

    /**
     * 验证拒绝副作用工具后 Agent 仍能生成内部草稿并以 COMPLETED 收尾。
     */
    @Test
    void continuesWithAnInternalDraftWhenPublishingIsRejected() throws Exception {
        String runId = (String) runtime.create(request(runtime, 4000)).get("runId");
        runtime.start(runId);
        awaitStatus(runId, "WAITING_FOR_USER");
        runtime.submitForm(runId, formValues());
        awaitStatus(runId, "WAITING_FOR_APPROVAL");

        ApprovalRequest rejection = new ApprovalRequest();
        rejection.setApproved(false);
        rejection.setReason("测试拒绝外部发布");
        runtime.approve(runId, rejection);

        Map<String, Object> completed = awaitStatus(runId, "COMPLETED");
        assertThat(completed.get("finalOutput")).asString()
                .contains("内部草稿").contains("未产生外部副作用");
    }

    /**
     * 验证未配置 API Key 时应用可查询安全状态，但创建 Agent 会在发起网络请求前被拒绝。
     */
    @Test
    void rejectsRunCreationWithoutExposingTheApiKey() {
        ModelProperties properties = new ModelProperties();
        ShowcaseRuntime unconfigured = new ShowcaseRuntime(
                new ResearchAgentFactory(new ShowcaseChatModel(), properties));
        try {
            assertThat(unconfigured.modelStatus())
                    .containsEntry("configured", false)
                    .doesNotContainKey("apiKey");
            assertThatThrownBy(() -> unconfigured.createAgent(new CreateAgentRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LLM_API_KEY");
        } finally {
            unconfigured.shutdown();
        }
    }

    /**
     * 验证 UI 提交的模型配置会立即用于新 Agent，并且任何安全视图都不会回显 API Key。
     */
    @Test
    void appliesUiModelConfigurationWithoutExposingTheApiKey() {
        ModelProperties properties = new ModelProperties();
        ResearchAgentFactory factory = new ResearchAgentFactory(new ShowcaseChatModel(), properties);
        ShowcaseRuntime configurable = new ShowcaseRuntime(factory);
        try {
            CreateAgentRequest configuration = new CreateAgentRequest();
            configuration.setModelProvider("deepseek");
            configuration.setModelEndpoint("https://api.deepseek.com");
            configuration.setModelRequestPath("/chat/completions");
            configuration.setModelApiKey("ui-test-secret-key");
            configuration.setModelName("deepseek-chat");
            configuration.setModelTemperature(0.3f);

            Map<String, Object> agent = configurable.createAgent(configuration);
            Map<String, Object> status = configurable.modelStatus();

            assertThat(status)
                    .containsEntry("configured", true)
                    .containsEntry("provider", "deepseek")
                    .containsEntry("model", "deepseek-chat")
                    .doesNotContainKey("apiKey");
            assertThat(agent.toString()).doesNotContain("ui-test-secret-key");
        } finally {
            configurable.shutdown();
        }
    }

    /**
     * 创建测试共用的 Run 请求，只允许调用方覆盖 Token 预算。
     *
     * @param targetRuntime 创建真实 Agent 注册记录的 Runtime
     * @param tokenBudget   测试所需总 Token 上限
     * @return 引用新 Agent 且使用固定任务的 Run 请求
     */
    private static CreateRunRequest request(ShowcaseRuntime targetRuntime, long tokenBudget) {
        CreateAgentRequest configuration = new CreateAgentRequest();
        configuration.setMaxTotalTokens(tokenBudget);
        Map<String, Object> agent = targetRuntime.createAgent(configuration);
        CreateRunRequest request = new CreateRunRequest();
        request.setAgentId((String) agent.get("agentId"));
        request.setTask("研究 2026 年中国 AI Agent 市场");
        return request;
    }

    /**
     * @return 与 research_brief JSON Schema 匹配的一组有效表单值
     */
    private static Map<String, Object> formValues() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("industry", "新能源汽车");
        form.put("market", "中国");
        form.put("timeRange", "2025-2026");
        form.put("depth", "执行摘要");
        return form;
    }

    /**
     * 轮询 Snapshot 直到达到预期状态，避免使用固定 sleep 制造慢且脆弱的测试。
     *
     * @param runId    Turn ID
     * @param expected 期望的 AgentTurnStatus 名称
     * @return 首个匹配状态的 Snapshot
     */
    private Map<String, Object> awaitStatus(String runId, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Map<String, Object> current;
        do {
            current = runtime.get(runId);
            if (expected.equals(current.get("status"))) return current;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out waiting for " + expected + ", current="
                + current.get("status") + ", error=" + current.get("error"));
    }

    /**
     * 等待后台推进线程进入 processing 状态，以便稳定测试运行中暂停。
     *
     * @param runId Turn ID
     */
    private void awaitProcessing(String runId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            if (Boolean.TRUE.equals(runtime.get(runId).get("processing"))) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for in-flight processing");
    }

    /**
     * 等待状态和 Demo 手工暂停标记同时生效，保证读取的是自洽 Snapshot。
     *
     * @param runId Turn ID
     * @return WAITING_FOR_USER 且 manualPause=true 的 Snapshot
     */
    private Map<String, Object> awaitManualPause(String runId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Map<String, Object> current;
        do {
            current = runtime.get(runId);
            if (Boolean.TRUE.equals(current.get("manualPause"))
                    && "WAITING_FOR_USER".equals(current.get("status"))) return current;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for the requested safe checkpoint");
    }

    /**
     * 等待异步事件监听器完成投递；Turn 状态与对应 AgentEvent 不保证同时可见。
     *
     * @param runId    Turn ID
     * @param expected 期望出现的 AgentEventType 名称
     * @return 已包含目标事件的 Snapshot
     */
    private Map<String, Object> awaitEvent(String runId, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Map<String, Object> current;
        do {
            current = runtime.get(runId);
            if (eventTypes(current).contains(expected)) return current;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for event " + expected);
    }

    /**
     * 从 API 响应读取嵌套对象，集中处理测试中的泛型擦除转换。
     *
     * @param value 外层响应映射
     * @param key   嵌套对象字段名
     * @return 嵌套对象映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    /**
     * 从 Snapshot 的 DemoEvent 列表提取事件类型，简化事件集合断言。
     *
     * @param view Run Snapshot
     * @return 保持原事件顺序的类型名称列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> eventTypes(Map<String, Object> view) {
        List<DemoEvent> events = (List<DemoEvent>) view.get("events");
        String[] values = events.stream().map(DemoEvent::getType).toArray(String[]::new);
        return Arrays.asList(values);
    }

    /**
     * 读取由 Map 列表组成的 Trace 字段，集中抑制不可避免的反序列化转换警告。
     *
     * @param value Trace 响应
     * @param key   列表字段名
     * @return 结构化映射列表
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Map<String, Object> value, String key) {
        return (List<Map<String, Object>>) value.get(key);
    }
}
