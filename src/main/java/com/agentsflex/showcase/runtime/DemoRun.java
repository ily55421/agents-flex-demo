package com.agentsflex.showcase.runtime;

import com.agentsflex.agent.Agent;
import com.agentsflex.agent.AgentRunner;
import com.agentsflex.agent.store.InMemoryAgentTurnStore;
import com.agentsflex.core.memory.DefaultChatMemory;
import com.agentsflex.showcase.demo.InMemoryCompressionStateStore;
import com.agentsflex.showcase.observability.InMemorySpanExporter;
import com.agentsflex.showcase.observability.InMemoryMetricExporter;
import com.agentsflex.core.observability.TelemetryRoute;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次 Demo Run 的隔离容器。
 *
 * <p>每个 Run 都拥有独立的 AgentRunner、TurnStore、ChatMemory 和 OTel 导出器；不可变 Agent
 * 定义可以复用，压缩状态则由 Store 按 conversationId 隔离。因此多个浏览器任务不会共享
 * 重试次数、上下文或 Trace。真正的 Agent 生命周期仍以 AgentTurn Snapshot 为准。</p>
 */
final class DemoRun {

    volatile String task;
    final String conversationId;
    final long tokenBudget;
    final int toolCallBudget;
    final int maxRetries;
    final DemoAgent agentDefinition;
    final Agent agent;
    final AgentRunner runner;
    final InMemoryAgentTurnStore turnStore;
    final DefaultChatMemory chatMemory;
    final InMemoryCompressionStateStore compressionStore;
    final InMemorySpanExporter spanExporter;
    final InMemoryMetricExporter metricExporter;
    final TelemetryRoute telemetryRoute;
    volatile Span rootSpan;
    volatile Context rootContext;
    final List<DemoEvent> events = new CopyOnWriteArrayList<>();
    final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // processing 保证同一个 Turn 同时最多只有一个推进线程，pauseRequested 支持协作式暂停。
    final AtomicBoolean processing = new AtomicBoolean();
    final AtomicBoolean pauseRequested = new AtomicBoolean();
    final AtomicBoolean telemetryEnded = new AtomicBoolean();
    volatile String runId;
    volatile boolean manualPause;
    volatile long pauseRequestedAt;
    volatile String runtimeError;
    volatile int compressionBeforeMessages;
    volatile int compressionPendingMessages;
    volatile int compressionAfterMessages;
    volatile long compressionBeforeTokens;
    volatile long compressionAfterTokens;
    volatile List<String> compressionOriginalContext = Collections.emptyList();
    volatile String compressedSummary;
    volatile String compressionStatus = "PENDING";
    volatile long compressionStartedAt;
    volatile long compressionCompletedAt;

    /**
     * 注入单个 Run 所需的全部隔离依赖与展示配置。
     * Runner、Memory 和 Telemetry 由 ShowcaseRuntime.create 在同一创建事务中生成；
     * agentDefinition 是此前已由用户显式创建的不可变配置。
     *
     * @param task            用户任务原文
     * @param conversationId  ChatMemory 会话 ID
     * @param agentDefinition 用户预先创建的 Agent 定义及完整配置
     * @param runner          执行与恢复 Turn 的 AgentRunner
     * @param turnStore       持久化 Snapshot 的进程内存储
     * @param chatMemory      当前会话的消息内存
     * @param spanExporter    当前 Run 的 OTel Span exporter
     * @param metricExporter  当前 Run 的 OTel Metric exporter
     * @param telemetryRoute  隔离的 OTel SDK 路由
     * @param rootSpan        跨越完整运行生命周期的根 Span
     * @param rootContext     根 Span 对应的 OTel Context
     */
    DemoRun(String task, String conversationId, DemoAgent agentDefinition, AgentRunner runner,
            InMemoryAgentTurnStore turnStore, DefaultChatMemory chatMemory,
            InMemorySpanExporter spanExporter, InMemoryMetricExporter metricExporter,
            TelemetryRoute telemetryRoute,
            Span rootSpan, Context rootContext) {
        this.task = task;
        this.conversationId = conversationId;
        this.agentDefinition = agentDefinition;
        this.tokenBudget = agentDefinition.configuration.getMaxTotalTokens();
        this.toolCallBudget = agentDefinition.configuration.getMaxToolCalls();
        this.maxRetries = agentDefinition.configuration.getMaxRetries();
        this.agent = agentDefinition.agent;
        this.runner = runner;
        this.turnStore = turnStore;
        this.chatMemory = chatMemory;
        this.compressionStore = agentDefinition.compressionStore;
        this.spanExporter = spanExporter;
        this.metricExporter = metricExporter;
        this.telemetryRoute = telemetryRoute;
        this.rootSpan = rootSpan;
        this.rootContext = rootContext;
    }

    /**
     * 将原生事件保存到 Snapshot 投影并广播给所有 SSE 订阅者。
     * 发送失败的连接会立即移除，避免后续事件反复写入失效 emitter。
     *
     * @param event 已转换为 JSON 安全结构的 Agent 事件
     */
    void publish(DemoEvent event) {
        events.add(event);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.getEventId())
                        .name("agent-event")
                        .data(event));
            } catch (IOException | IllegalStateException error) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}
