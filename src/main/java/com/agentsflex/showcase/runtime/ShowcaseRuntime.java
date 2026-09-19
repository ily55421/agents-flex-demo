package com.agentsflex.showcase.runtime;

import com.agentsflex.agent.Agent;
import com.agentsflex.agent.AgentResumeCommand;
import com.agentsflex.agent.AgentRunner;
import com.agentsflex.agent.AgentSuspension;
import com.agentsflex.agent.AgentTurn;
import com.agentsflex.agent.AgentTurnOptions;
import com.agentsflex.agent.AgentTurnStatus;
import com.agentsflex.agent.AgentWorker;
import com.agentsflex.agent.event.AgentEvent;
import com.agentsflex.agent.event.AgentEventType;
import com.agentsflex.agent.compression.AgentContextCompressionState;
import com.agentsflex.agent.loader.InMemoryAgentLoader;
import com.agentsflex.agent.store.InMemoryAgentTurnStore;
import com.agentsflex.core.memory.DefaultChatMemory;
import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.message.Message;
import com.agentsflex.core.message.UserMessage;
import com.agentsflex.showcase.model.ApprovalRequest;
import com.agentsflex.showcase.model.CreateAgentRequest;
import com.agentsflex.showcase.model.CreateRunRequest;
import com.agentsflex.showcase.model.UpdateModelRequest;
import com.agentsflex.showcase.demo.InMemoryCompressionStateStore;
import com.agentsflex.showcase.demo.ResearchAgentFactory;
import com.agentsflex.showcase.demo.ShowcaseTokenEstimator;
import com.agentsflex.showcase.observability.InMemorySpanExporter;
import com.agentsflex.showcase.observability.InMemoryMetricExporter;
import com.agentsflex.showcase.persistence.RunArchive;
import com.agentsflex.core.observability.Observability;
import com.agentsflex.core.observability.ObservabilityAttributeKeys;
import com.agentsflex.core.observability.SpanProcessingMode;
import com.agentsflex.core.observability.TelemetryDestination;
import com.agentsflex.core.observability.TelemetryRoute;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于真实 Agents-Flex {@link AgentRunner} 的统一控制面。
 *
 * <p>Controller 只提交命令。本类负责把命令交给 AgentRunner，并协调异步 Step、Worker 重试、
 * SSE 和 OTel 作用域；状态转换、表单/审批关联、Snapshot 持久化、预算判定与压缩仍由
 * Agents-Flex 原生实现，不在 Demo 中复制一套状态机。</p>
 */
@Service
public class ShowcaseRuntime {

    private final ConcurrentMap<String, DemoRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DemoAgent> agents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> archivedAgents = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ResearchAgentFactory agentFactory;
    private final RunArchive archive;

    /**
     * 初始化 Demo 控制面，并在调用方未显式配置时开启 OTel 内容采集。
     * 内容采集用于在 Trace 面板展示模型输出、工具参数和工具结果。
     * 该构造器仅用于测试直接装配；Spring 容器使用带 RunArchive 的双参构造器。
     *
     * @param agentFactory Agent 工厂
     */
    public ShowcaseRuntime(ResearchAgentFactory agentFactory) {
        this(agentFactory, null);
    }

    /**
     * 初始化 Demo 控制面，并注入 DuckDB 持久化归档。
     * 未配置 API Key 或归档不可用时不会阻断启动；archive 为空时全部持久化调用自动跳过。
     *
     * @param agentFactory Agent 工厂
     * @param archive      DuckDB 归档；测试或降级场景可传 {@code null}
     */
    @Autowired
    public ShowcaseRuntime(ResearchAgentFactory agentFactory, RunArchive archive) {
        this.agentFactory = agentFactory;
        this.archive = archive;
        if (System.getProperty("agentsflex.otel.capture.content") == null) {
            System.setProperty("agentsflex.otel.capture.content", "true");
        }
    }

    /**
     * 启动时从 DuckDB 恢复历史 Agent 定义视图，供左侧配置面板与归档查询使用。
     * 归档中的定义不含 API Key，因此只做展示性恢复，不重建可执行 Agent。
     */
    @PostConstruct
    public void restoreArchivedAgents() {
        if (archive == null) return;
        for (Map<String, Object> view : archive.loadAgentDefinitions()) {
            Object agentId = view.get("agentId");
            if (agentId instanceof String string) {
                archivedAgents.put(string, view);
            }
        }
    }

    /**
     * 使用页面提交的完整配置立即构建真实 Agents-Flex Agent，并注册为可发起对话的资源。
     * 创建过程会先检查模型密钥和 Builder 参数，因此接口成功即可视为 Agent 已就绪。
     *
     * @param request Agent、预算、重试、上下文和压缩配置
     * @return 不含 API Key 的 Agent 完整配置视图
     */
    @CacheEvict(cacheNames = "modelStatus", allEntries = true)
    public Map<String, Object> createAgent(CreateAgentRequest request) {
        if (request.getInitialDelayMillis() > request.getMaxDelayMillis()) {
            throw new IllegalArgumentException("最大重试间隔不能小于初始重试间隔");
        }
        String agentId = "showcase-agent-" + definitionFingerprint(request.getName(), request.getVersion());
        InMemoryCompressionStateStore compressionStore = new InMemoryCompressionStateStore();
        Agent agent = agentFactory.create(agentId, request, compressionStore);
        // API Key 已固化进该 Agent 的 ChatModel；配置快照无需继续持有或回显密钥。
        request.setModelApiKey(null);
        DemoAgent definition = new DemoAgent(agentId, agent, request, compressionStore);
        agents.put(agentId, definition);
        if (archive != null) {
            archive.saveAgentDefinition(agentId, definition.toView(), definition.createdAt);
            archivedAgents.put(agentId, definition.toView());
        }
        return definition.toView();
    }

    /**
     * 同名同版本确定性派生 agentId：归档 Agent 重建（重启后自动恢复、页面重复提交）
     * 会命中同一行定义做幂等覆盖，而不是每次都新增一行归档副本；
     * 需要迭代配置时应递增版本号，不同版本各自独立。
     */
    private static String definitionFingerprint(String name, Object version) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (String.valueOf(name) + "#v" + String.valueOf(version))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 6);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    /**
     * 使用已注册 Agent 创建隔离的 Runner、存储、Memory 和 Telemetry Route，并生成 READY Turn。
     * 本方法不会执行第一个 Step，实际执行由 {@link #start(String)} 触发。
     *
     * @param request 已创建 Agent 的 ID 与首条用户消息
     * @return 新 Run 的完整前端 Snapshot
     */
    public Map<String, Object> create(CreateRunRequest request) {
        DemoAgent agentDefinition = requireAgent(request.getAgentId());
        // 每次创建都分配独立 conversation、Memory 和 OTel Route；Agent 配置保持不变，上下文互相隔离。
        String conversationId = "research-" + UUID.randomUUID();
        DefaultChatMemory memory = new DefaultChatMemory(conversationId);

        InMemorySpanExporter spanExporter = new InMemorySpanExporter();
        InMemoryMetricExporter metricExporter = new InMemoryMetricExporter();
        TelemetryRoute telemetryRoute = TelemetryRoute.builder("showcase-" + conversationId)
                .serviceName("agents-flex-showcase")
                .serviceVersion("1.0.0")
                .addDestination(TelemetryDestination.builder("run-memory")
                        .spanExporter(spanExporter)
                        .metricExporter(metricExporter)
                        .spanProcessingMode(SpanProcessingMode.SIMPLE)
                        .metricExportInterval(java.time.Duration.ofSeconds(1))
                        .build())
                .build();
        Agent agent = agentDefinition.agent;
        InMemoryAgentTurnStore turnStore = new InMemoryAgentTurnStore();
        AgentRunner runner = AgentRunner.builder()
                .turnStore(turnStore)
                .agentLoader(new InMemoryAgentLoader(agent))
                .chatMemoryProvider(id -> memory)
                .build();

        // 根 Span 跨越完整 Run，包括表单和审批等待；原生 Chat/Tool Span 自动挂在其下。
        Attributes initialAttributes = Attributes.of(
                ObservabilityAttributeKeys.CONVERSATION_ID, conversationId);
        Span rootSpan;
        Context rootContext;
        try (Scope runtimeScope = Observability.useRuntime(telemetryRoute, initialAttributes)) {
            rootSpan = telemetryRoute.getTracer().spanBuilder("agent.run")
                    .setAttribute(ObservabilityAttributeKeys.CONVERSATION_ID, conversationId)
                    .setAttribute("agentsflex.showcase.scenario", "ai-market-research")
                    .startSpan();
            rootContext = Context.current().with(rootSpan);
        }
        DemoRun run = new DemoRun(request.getTask(), conversationId, agentDefinition,
                runner, turnStore, memory, spanExporter, metricExporter,
                telemetryRoute, rootSpan, rootContext);
        runner.addEventListener(event -> onEvent(run, event));

        // runner.start 只创建 READY Snapshot；用户点击“启动”后才异步推进第一个 Step。
        AgentTurn turn;
        try (Scope rootScope = rootContext.makeCurrent();
             Scope runtimeScope = Observability.useRuntime(telemetryRoute, initialAttributes)) {
            turn = runner.start(agent, conversationId, new UserMessage(request.getTask()),
                    AgentTurnOptions.builder()
                            .metadata("scenario", "ai-market-research")
                            .metadata("requestId", UUID.randomUUID().toString())
                            .metadata("capabilitySource", "agents-flex-2.2.8")
                            .streaming(true)
                            .build());
        }
        run.runId = turn.getId();
        rootSpan.setAttribute(ObservabilityAttributeKeys.TURN_ID, turn.getId());
        runs.put(turn.getId(), run);
        Map<String, Object> view = RunViewMapper.map(run, turn);
        if (archive != null) {
            archive.saveRunSnapshot(run.runId, run.conversationId, run.agent.getId(),
                    turn.getStatus().name(), view);
        }
        return view;
    }

    /**
     * 异步启动 READY Run；重复启动或从其他状态启动会返回状态冲突。
     *
     * @param runId 要启动的 Turn ID
     * @return 提交推进任务时的 Snapshot，后续状态通过 SSE 或查询更新
     */
    public Map<String, Object> start(String runId) {
        DemoRun run = requireRun(runId);
        AgentTurn current = run.runner.restore(runId);
        if (current.getStatus() != AgentTurnStatus.READY) {
            throw new IllegalStateException("Only a READY run can be started");
        }
        scheduleAdvance(run, false);
        return RunViewMapper.map(run, current);
    }

    /**
     * 在同一 conversationId 和 ChatMemory 下创建下一轮 AgentTurn。
     * 旧 Turn 必须已经终止；新 Turn 复用 Agent、工具、压缩存储和 TelemetryRoute，但拥有独立根 Span。
     *
     * @param runId   当前会话最后一个 Turn ID
     * @param message 用户的新一轮消息
     * @return 新 Turn 的 READY Snapshot
     */
    public synchronized Map<String, Object> continueConversation(String runId, String message) {
        DemoRun run = requireRun(runId);
        AgentTurn previous = run.runner.restore(runId);
        if (previous == null || !previous.getStatus().isTerminal()) {
            throw new IllegalStateException("当前回复尚未结束，暂时不能发送下一条消息");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("对话消息不能为空");
        }

        // 新一轮沿用同一业务会话，但重置所有 Turn 级控制状态和事件订阅。
        for (SseEmitter emitter : run.emitters) emitter.complete();
        run.emitters.clear();
        run.events.clear();
        run.task = message.trim();
        run.runtimeError = null;
        run.manualPause = false;
        run.pauseRequested.set(false);
        run.pauseRequestedAt = 0;
        run.telemetryEnded.set(false);

        Attributes attributes = Attributes.of(
                ObservabilityAttributeKeys.CONVERSATION_ID, run.conversationId);
        try (Scope runtimeScope = Observability.useRuntime(run.telemetryRoute, attributes)) {
            run.rootSpan = run.telemetryRoute.getTracer().spanBuilder("agent.run")
                    .setAttribute(ObservabilityAttributeKeys.CONVERSATION_ID, run.conversationId)
                    .setAttribute("agentsflex.showcase.scenario", "ai-market-research-follow-up")
                    .startSpan();
            run.rootContext = Context.current().with(run.rootSpan);
        }

        AgentTurn turn;
        try (Scope rootScope = run.rootContext.makeCurrent();
             Scope runtimeScope = Observability.useRuntime(run.telemetryRoute, attributes)) {
            turn = run.runner.start(run.agent, run.conversationId, new UserMessage(run.task),
                    AgentTurnOptions.builder()
                            .metadata("scenario", "ai-market-research-follow-up")
                            .metadata("requestId", UUID.randomUUID().toString())
                            .metadata("capabilitySource", "agents-flex-2.2.8")
                            .streaming(true)
                            .build());
        }
        runs.remove(runId, run);
        run.runId = turn.getId();
        run.rootSpan.setAttribute(ObservabilityAttributeKeys.TURN_ID, turn.getId());
        runs.put(turn.getId(), run);
        Map<String, Object> view = RunViewMapper.map(run, turn);
        if (archive != null) {
            archive.saveRunSnapshot(run.runId, run.conversationId, run.agent.getId(),
                    turn.getStatus().name(), view);
        }
        return view;
    }

    /**
     * 恢复指定 Turn 的最新持久化 Snapshot，并合并 Demo 补充状态。
     *
     * @param runId Turn ID
     * @return 当前 Run 的只读 API 投影
     */
    public Map<String, Object> get(String runId) {
        DemoRun run = runs.get(runId);
        if (run != null) {
            return RunViewMapper.map(run, run.runner.restore(runId));
        }
        // 重启后的历史 Run 无法重建 Runner/Memory，直接返回 DuckDB 中的归档视图。
        if (archive != null) {
            Map<String, Object> archived = archive.loadRunSnapshot(runId);
            if (archived != null) {
                Map<String, Object> view = new LinkedHashMap<>(archived);
                view.put("archived", true);
                return view;
            }
        }
        throw new IllegalArgumentException("Agent run not found: " + runId);
    }

    /**
     * 枚举当前进程内创建的所有 Run，并逐个从 TurnStore 恢复最新状态。
     *
     * @return Run Snapshot 列表；未规定排序，调用方不能依赖创建顺序
     */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> values = new ArrayList<>();
        for (DemoRun run : runs.values()) {
            values.add(RunViewMapper.map(run, run.runner.restore(run.runId)));
        }
        // 重启后内存中的 Run 已消失，但 DuckDB 归档的终态快照仍然可查。
        if (archive != null) {
            for (Map<String, Object> archived : archive.loadRunSnapshots()) {
                Object runId = archived.get("runId");
                if (!(runId instanceof String) || !runs.containsKey(runId)) {
                    values.add(archived);
                }
            }
        }
        return values;
    }

    /**
     * 枚举当前进程内所有已创建的 Agent 定义视图，并合并 DuckDB 归档的历史 Agent。
     * 返回视图均不含 API Key，可直接提供给浏览器选择与展示。
     *
     * <p>每个视图带 {@code runnable} 标记：只有仍在进程内注册的 Agent 才能直接发起对话；
     * 仅存在于归档中的历史 Agent 需要重新配置模型并创建（API Key 不持久化）。</p>
     *
     * @return 按创建时间倒序的 Agent 安全视图列表
     */
    public List<Map<String, Object>> listAgents() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (DemoAgent definition : agents.values()) {
            Map<String, Object> view = new LinkedHashMap<>(definition.toView());
            view.put("runnable", true);
            byId.put(definition.id, view);
        }
        if (archive != null) {
            for (Map<String, Object> archived : archive.loadAgentDefinitions()) {
                Object agentId = archived.get("agentId");
                if (!(agentId instanceof String)) continue;
                Map<String, Object> view = new LinkedHashMap<>(archived);
                view.put("runnable", agents.containsKey(agentId));
                byId.putIfAbsent((String) agentId, view);
            }
        }
        List<Map<String, Object>> values = new ArrayList<>(byId.values());
        values.sort((left, right) -> Long.compare(
                number(right.get("createdAt")), number(left.get("createdAt"))));
        return values;
    }

    /**
     * 查询当前真实模型的公开配置状态。返回值永远不包含 API Key，可直接提供给浏览器。
     *
     * @return provider、model、endpoint、温度、思考模式和是否已配置
     */
    @Cacheable(cacheNames = "modelStatus", key = "'current'")
    public Map<String, Object> modelStatus() {
        return agentFactory.modelInfo();
    }

    /**
     * 仅把页面弹窗中的模型连接配置同步到服务端内存，使模型状态立即生效。
     * 该操作不构建 Agent，也不改变任何已有 Run；创建 Agent 时会再次合并同一份配置。
     *
     * @param request 模型连接与采样参数
     * @return 应用后的不含 API Key 模型状态
     */
    @CacheEvict(cacheNames = "modelStatus", allEntries = true)
    public Map<String, Object> updateModel(UpdateModelRequest request) {
        return agentFactory.applyModelConnection(request);
    }

    /**
     * 将动态表单值作为 USER_INPUT ResumeCommand 提交给当前 Suspension。
     * 仅允许普通 WAITING_FOR_USER 状态，手工暂停必须走 {@link #resume(String)}。
     *
     * @param runId  被表单挂起的 Turn ID
     * @param values 根据 Suspension JSON Schema 收集的字段值
     * @return 恢复后的 Snapshot
     */
    public Map<String, Object> submitForm(String runId, Map<String, Object> values) {
        DemoRun run = requireRun(runId);
        AgentTurn turn = run.runner.restore(runId);
        requireStatus(turn, AgentTurnStatus.WAITING_FOR_USER);
        if (run.manualPause) {
            throw new IllegalStateException("Manual pause must be resumed through /resume");
        }
        // 必须使用 Suspension correlationId 恢复原 ToolCall，不能新建 Turn 或重新发送任务。
        String callId = turn.getSuspension().getCorrelationId();
        AgentTurn resumed = run.runner.submitResume(runId,
                AgentResumeCommand.userInput(callId, values)
                        .withMetadata("submittedBy", "showcase-user")
                        .withMetadata("source", "dynamic-json-schema-form"));
        scheduleAdvance(run, false);
        return RunViewMapper.map(run, resumed);
    }

    /**
     * 批准或拒绝当前待审批工具，并继续推进同一个 Turn。
     * 拒绝不会令 Run 失败，而是把拒绝结果作为 ToolMessage 交给模型收尾。
     *
     * @param runId   处于 WAITING_FOR_APPROVAL 的 Turn ID
     * @param request 人工审批结果、原因和审批人
     * @return 提交审批 ResumeCommand 后的 Snapshot
     */
    public Map<String, Object> approve(String runId, ApprovalRequest request) {
        DemoRun run = requireRun(runId);
        AgentTurn turn = run.runner.restore(runId);
        requireStatus(turn, AgentTurnStatus.WAITING_FOR_APPROVAL);
        String callId = turn.getSuspension().getCorrelationId();
        // 批准与拒绝都是原生 ResumeCommand；拒绝结果会作为 ToolMessage 返回模型继续收尾。
        AgentResumeCommand command = request.isApproved()
                ? AgentResumeCommand.approveTool(callId)
                : AgentResumeCommand.rejectTool(callId,
                request.getReason() == null ? "审批人拒绝外部发布" : request.getReason());
        AgentTurn resumed = run.runner.submitResume(runId, command
                .withMetadata("approverId", request.getApprover())
                .withMetadata("channel", "showcase-console"));
        scheduleAdvance(run, false);
        return RunViewMapper.map(run, resumed);
    }

    /**
     * 请求手工挂起 Run。READY 状态会立即挂起；RUNNING 状态会等待当前原子 Step 完成。
     * 原生表单、审批或重试阻塞点不允许被手工暂停覆盖。
     *
     * @param runId 目标 Turn ID
     * @return 已挂起或已记录 pauseRequested 的最新 Snapshot
     */
    public Map<String, Object> suspend(String runId) {
        DemoRun run = requireRun(runId);
        AgentTurn turn = run.runner.restore(runId);
        if (run.manualPause) return RunViewMapper.map(run, turn);
        if (turn.getStatus().isTerminal()) {
            throw new IllegalStateException("A terminal run cannot be suspended");
        }
        if (turn.getStatus().isBlocked()) {
            throw new IllegalStateException(
                    "Complete the current form, approval, or retry wait before requesting a manual pause");
        }
        // RUNNING 时不抢占正在执行的原子 Step，只记录请求并在下一个安全检查点挂起。
        run.pauseRequested.set(true);
        run.pauseRequestedAt = System.currentTimeMillis();
        if (run.processing.get()) {
            return RunViewMapper.map(run, turn);
        }
        AgentTurn suspended = applyManualPause(run, turn);
        return RunViewMapper.map(run, suspended);
    }

    /**
     * 恢复由 Demo 手工控制产生的 USER_INPUT Suspension，并继续原 Turn。
     *
     * @param runId 已手工挂起的 Turn ID
     * @return 清除手工暂停标记后的 Snapshot
     */
    public Map<String, Object> resume(String runId) {
        DemoRun run = requireRun(runId);
        if (!run.manualPause) {
            throw new IllegalStateException("Run is not manually paused");
        }
        // 手工暂停也使用原生 USER_INPUT Suspension，恢复后继续同一个 Turn 和 Step 序列。
        AgentTurn resumed = run.runner.submitResume(runId,
                AgentResumeCommand.userInput("操作员确认继续执行")
                        .withMetadata("source", "manual-showcase-control"));
        run.manualPause = false;
        run.pauseRequested.set(false);
        scheduleAdvance(run, false);
        return RunViewMapper.map(run, resumed);
    }

    /**
     * 取消指定 Run，并让 Worker 路径处理可能遗留的持久化任务状态。
     *
     * @param runId 要取消的 Turn ID
     * @return CANCELLED Snapshot
     */
    public Map<String, Object> cancel(String runId) {
        DemoRun run = requireRun(runId);
        AgentTurn turn = run.runner.cancel(runId);
        scheduleAdvance(run, true);
        return RunViewMapper.map(run, turn);
    }

    /**
     * 强制刷新该 Run 的 OTel Route，并构造 Span、Metric 与生命周期事件投影。
     *
     * @param runId Turn ID
     * @return Trace 面板使用的结构化数据
     */
    public Map<String, Object> trace(String runId) {
        DemoRun run = runs.get(runId);
        if (run != null) {
            AgentTurn turn = run.runner.restore(runId);
            finishTelemetry(run, turn);
            // 查询 Trace 前主动 flush，避免周期 Metric exporter 尚未导出导致面板短暂为空。
            run.telemetryRoute.forceFlush().join(2, TimeUnit.SECONDS);
            return RunViewMapper.trace(run, turn);
        }
        // 归档 Run 的 Span/Metric 随进程失效，但快照里的事件与预算仍可只读展示，不能因此 409。
        if (archive != null) {
            Map<String, Object> archived = archive.loadRunSnapshot(runId);
            if (archived != null) {
                return RunViewMapper.archivedTrace(archived);
            }
        }
        throw new IllegalArgumentException("Agent run not found: " + runId);
    }

    /**
     * 为指定 Run 建立不超时的 SSE 连接。连接成功后先发送 connected Snapshot，
     * 后续由 {@link DemoRun#publish(DemoEvent)} 增量推送 agent-event。
     *
     * @param runId 要订阅的 Turn ID
     * @return 已注册生命周期回调的 SSE emitter
     */
    public SseEmitter subscribe(String runId) {
        DemoRun run = requireRun(runId);
        SseEmitter emitter = new SseEmitter(0L);
        run.emitters.add(emitter);
        emitter.onCompletion(() -> run.emitters.remove(emitter));
        emitter.onTimeout(() -> run.emitters.remove(emitter));
        emitter.onError(error -> run.emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(get(runId)));
        } catch (Exception error) {
            run.emitters.remove(emitter);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    /**
     * 将 Turn 推进提交到后台执行器，使 HTTP 命令可以立即返回。
     *
     * @param run       单次运行容器
     * @param viaWorker 是否通过 AgentWorker lease 路径推进重试任务
     */
    private void scheduleAdvance(DemoRun run, boolean viaWorker) {
        executor.submit(() -> advance(run, viaWorker));
    }

    /**
     * 在正确的 Root Span 和 Telemetry Route 作用域中推进 Run。
     * 方法通过 CAS 防止并发推进，并在终态、原生阻塞点或安全暂停点停止循环。
     *
     * @param run       单次运行容器
     * @param viaWorker {@code true} 表示领取持久化重试任务，否则直接调用 runner.step
     */
    private void advance(DemoRun run, boolean viaWorker) {
        // CAS 是每个 Run 的单飞门闩：重复点击或密集 SSE 刷新不会并发推进同一个 Turn。
        if (!run.processing.compareAndSet(false, true)) return;
        try (Scope parentScope = run.rootContext.makeCurrent();
             Scope runtimeScope = Observability.useRuntime(run.telemetryRoute, Attributes.of(
                     ObservabilityAttributeKeys.TURN_ID, run.runId,
                     ObservabilityAttributeKeys.CONVERSATION_ID, run.conversationId))) {
            AgentTurn result;
            if (viaWorker) {
                // RETRY_SCHEDULED 必须通过 AgentWorker 领取 lease，演示真实的持久化重试路径。
                try (AgentWorker worker = new AgentWorker(
                        "showcase-worker-" + run.runId.substring(0, 8), run.runner, 30_000)) {
                    List<AgentTurn> processed = worker.pollAndRun(1);
                    result = processed.isEmpty() ? run.runner.restore(run.runId) : processed.get(0);
                }
            } else {
                result = run.runner.restore(run.runId);
                // 普通推进持续执行 Step，直到终态、原生阻塞点或用户请求的安全暂停点。
                while (!result.getStatus().isTerminal() && !result.getStatus().isBlocked()) {
                    if (run.pauseRequested.get()) {
                        result = applyManualPause(run, result);
                        break;
                    }
                    run.runner.step(result);
                    if (run.pauseRequested.get() && !result.getStatus().isTerminal()
                            && !result.getStatus().isBlocked()) {
                        result = applyManualPause(run, result);
                        break;
                    }
                }
            }
            if (run.pauseRequested.get() && !result.getStatus().isTerminal()
                    && !result.getStatus().isBlocked()) {
                result = applyManualPause(run, result);
            }
            if (run.pauseRequested.get()
                    && (result.getStatus().isBlocked() || result.getStatus().isTerminal())) {
                // 表单、审批、重试等原生阻塞点优先，避免用手工 Suspension 覆盖必要的关联信息。
                run.pauseRequested.set(false);
            }
            if (result.getStatus() == AgentTurnStatus.RETRY_SCHEDULED) {
                scheduler.schedule(() -> advance(run, true), 25, TimeUnit.MILLISECONDS);
            }
            finishTelemetry(run, result);
            if (archive != null) {
                archive.saveRunSnapshot(run.runId, run.conversationId, run.agent.getId(),
                        result.getStatus().name(), RunViewMapper.map(run, result));
            }
        } catch (RuntimeException error) {
            run.runtimeError = error.getMessage();
        } finally {
            run.processing.set(false);
        }
    }

    /**
     * 接收 Agents-Flex 原生事件，维护压缩面板的补充投影并推送 SSE。
     * 非压缩事件不修改内容，确保事件流仍忠实反映框架行为。
     *
     * @param run   事件所属 Run
     * @param event AgentRunner 发布的不可变事件
     */
    private void onEvent(DemoRun run, AgentEvent event) {
        // 压缩详情来自原生事件与 CompressionStateStore；其余事件不加工，直接进入 SSE 时间线。
        if (event.getType() == AgentEventType.CONTEXT_COMPRESSION_STARTED) {
            run.compressionStatus = "RUNNING";
            run.compressionStartedAt = event.getOccurredAt();
            run.compressionBeforeMessages = number(event.getData().get("historyMessageCount"));
            run.compressionPendingMessages = number(event.getData().get("pendingMessageCount"));
            List<Message> memory = run.chatMemory.getMessages(Integer.MAX_VALUE);
            List<Message> original = memory.subList(0,
                    Math.min(run.compressionBeforeMessages, memory.size()));
            run.compressionBeforeTokens = ShowcaseTokenEstimator.estimate(original);
            run.compressionOriginalContext = contextPreview(original);
        } else if (event.getType() == AgentEventType.CONTEXT_COMPRESSION_COMPLETED) {
            run.compressionStatus = "COMPLETED";
            run.compressionCompletedAt = event.getOccurredAt();
            run.compressionAfterMessages = number(event.getData().get("modelMessageCount"));
            AgentContextCompressionState state = run.compressionStore.load(run.conversationId);
            List<Message> summaryMessages = state == null
                    ? Collections.emptyList() : state.getSummaryMessages();
            run.compressionAfterTokens = ShowcaseTokenEstimator.estimate(summaryMessages);
            if (!summaryMessages.isEmpty()) {
                run.compressionAfterMessages = summaryMessages.size();
                run.compressedSummary = lastText(summaryMessages);
            }
        } else if (event.getType() == AgentEventType.CONTEXT_COMPRESSION_FAILED) {
            run.compressionStatus = "FAILED";
            run.compressionCompletedAt = event.getOccurredAt();
        }
        DemoEvent demoEvent = new DemoEvent(event);
        if (archive != null) {
            archive.appendEvent(demoEvent.getRunId(), demoEvent.getEventId(), demoEvent.getSequence(),
                    demoEvent.getType(), demoEvent.getOccurredAt(), demoEvent.getData());
        }
        run.publish(demoEvent);
    }

    /**
     * 把压缩前 Message 列表转换为带角色前缀的只读文本预览。
     *
     * @param messages 原始上下文消息
     * @return 过滤空内容后的 User/Assistant 文本列表
     */
    private static List<String> contextPreview(List<Message> messages) {
        List<String> values = new ArrayList<>();
        for (Message message : messages) {
            String text = message == null ? null : message.getTextContent();
            if (text == null || text.trim().isEmpty()) continue;
            String role = message instanceof UserMessage ? "User" : "Assistant";
            values.add(role + ": " + text);
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * 从消息列表尾部查找最后一条非空文本，用作压缩摘要展示。
     *
     * @param messages 压缩器生成的摘要消息
     * @return 最后一条有效文本；不存在时返回 {@code null}
     */
    private static String lastText(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            String text = messages.get(index).getTextContent();
            if (text != null && !text.trim().isEmpty()) return text;
        }
        return null;
    }

    /**
     * 安全读取事件 data 中的数值字段，避免缺失或类型异常影响事件线程。
     *
     * @param value 待转换值
     * @return Number 的 int 值，非数值统一返回 0
     */
    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /**
     * 根据 Turn ID 获取运行容器，并把不存在的 Run 统一转换为业务参数异常。
     *
     * @param runId Turn ID
     * @return 对应的 DemoRun
     * @throws IllegalArgumentException Run 不存在时抛出
     */
    private DemoRun requireRun(String runId) {
        DemoRun run = runs.get(runId);
        if (run == null) throw new IllegalArgumentException("Agent run not found: " + runId);
        return run;
    }

    /**
     * 根据 Agent ID 读取已经通过 Builder 校验的定义，禁止绕过“先创建 Agent”直接创建 Run。
     *
     * @param agentId Agent 创建接口返回的 ID
     * @return 可复用的真实 Agent 注册记录
     * @throws IllegalArgumentException Agent 不存在或服务重启后已失效时抛出
     */
    private DemoAgent requireAgent(String agentId) {
        DemoAgent agent = agents.get(agentId);
        if (agent == null) throw new IllegalArgumentException("Agent not found: " + agentId);
        return agent;
    }

    /**
     * 使用原生 USER_INPUT Suspension 在安全检查点持久化手工暂停。
     * 状态持久化成功后才发布 manualPause，防止 API 返回不一致快照。
     *
     * @param run  单次运行容器
     * @param turn 当前可推进 Turn
     * @return WAITING_FOR_USER 的持久化 Snapshot
     */
    private static AgentTurn applyManualPause(DemoRun run, AgentTurn turn) {
        // 先持久化 Suspension，再发布 manualPause，防止 API 暴露“已暂停但仍是 RUNNING”的瞬态。
        AgentTurn suspended = run.runner.suspend(turn,
                AgentSuspension.userInput("操作员请求已在当前原子 Step 结束后的安全检查点生效"));
        run.pauseRequested.set(false);
        run.manualPause = true;
        return suspended;
    }

    /**
     * 在 Turn 首次进入终态时结束 Root Span，并根据失败类型设置 Span 状态。
     *
     * @param run  单次运行容器
     * @param turn 当前 Turn Snapshot
     */
    private static void finishTelemetry(DemoRun run, AgentTurn turn) {
        // 根 Span 只在终态结束一次；等待人工输入期间保持开放，以便准确记录完整墙钟时长。
        if (!turn.getStatus().isTerminal() || !run.telemetryEnded.compareAndSet(false, true)) return;
        if (turn.getStatus() == AgentTurnStatus.FAILED
                || turn.getStatus() == AgentTurnStatus.BUDGET_EXCEEDED) {
            run.rootSpan.setStatus(StatusCode.ERROR,
                    turn.getError() == null ? turn.getStatus().name() : turn.getError().getMessage());
        } else {
            run.rootSpan.setStatus(StatusCode.OK);
        }
        run.rootSpan.setAttribute("agentsflex.turn.status", turn.getStatus().name());
        run.rootSpan.end();
    }

    /**
     * 校验命令执行前的精确状态，防止错误 ResumeCommand 破坏 Suspension 关联。
     *
     * @param turn     当前 Turn Snapshot
     * @param expected 命令要求的状态
     * @throws IllegalStateException 实际状态不匹配时抛出
     */
    private static void requireStatus(AgentTurn turn, AgentTurnStatus expected) {
        if (turn.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected + " but was " + turn.getStatus());
        }
    }

    /**
     * Spring 容器关闭时终止线程池、结束未闭合 Root Span 并释放 OTel SDK 资源。
     */
    @PreDestroy
    public void shutdown() {
        // 应用关闭时终止执行器并释放每个 Route 的 SDK 资源，避免 exporter 后台线程泄漏。
        executor.shutdownNow();
        scheduler.shutdownNow();
        for (DemoRun run : runs.values()) {
            if (run.telemetryEnded.compareAndSet(false, true)) run.rootSpan.end();
            run.telemetryRoute.close();
        }
    }
}
