package com.agentsflex.showcase.demo;

import com.agentsflex.agent.Agent;
import com.agentsflex.agent.AgentBudget;
import com.agentsflex.agent.AgentExecutionPolicy;
import com.agentsflex.agent.AgentRetryPolicy;
import com.agentsflex.agent.AgentSuspensionExpirationStrategy;
import com.agentsflex.agent.AgentToolExecutionMode;
import com.agentsflex.agent.AgentToolResultOverflowStrategy;
import com.agentsflex.agent.AgentParallelFailureStrategy;
import com.agentsflex.agent.compression.AgentContextCompressionDecider;
import com.agentsflex.agent.compression.AgentContextCompressionDeciders;
import com.agentsflex.agent.compression.AgentContextCompressor;
import com.agentsflex.agent.compression.AgentContextCompressionPolicy;
import com.agentsflex.agent.compression.AgentContextCompressors;
import com.agentsflex.agent.compression.AgentContextModelCompressorOptions;
import com.agentsflex.agent.compression.AgentCompressionFailureStrategy;
import com.agentsflex.agent.tool.AgentFormDefinition;
import com.agentsflex.agent.tool.AgentToolContext;
import com.agentsflex.agent.tool.AgentUserInputTool;
import com.agentsflex.agent.tool.ToolApprovalDecision;
import com.agentsflex.agent.tool.ToolErrorStrategy;
import com.agentsflex.core.model.chat.tool.Parameter;
import com.agentsflex.core.model.chat.tool.Tool;
import com.agentsflex.core.model.chat.ChatModel;
import com.agentsflex.core.model.chat.ChatOptions;
import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.message.Message;
import com.agentsflex.showcase.config.ModelProperties;
import com.agentsflex.showcase.config.ModelConfiguration;
import com.agentsflex.showcase.knowledge.KnowledgeService;
import com.agentsflex.showcase.model.CreateAgentRequest;
import com.agentsflex.showcase.model.UpdateModelRequest;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 根据页面完整配置构建可供多个对话复用的真实 Agents-Flex Agent 定义。
 *
 * <p>五个工具覆盖动态表单、普通工具、可重试工具、需审批副作用工具和 RAG 知识库检索；
 * 执行策略同时启用 Token/工具/时长预算与上下文压缩。确定性仅来自 ChatModel 和工具返回值，
 * Runtime 行为仍全部经过 Agents-Flex。</p>
 */
@Component
public final class ResearchAgentFactory {

    private final ChatModel chatModel;
    private final ChatModel compressionModel;
    private final ModelProperties modelProperties;
    private final ModelConfiguration modelConfiguration;
    private final KnowledgeService knowledgeService;

    private final ConcurrentMap<String, AtomicInteger> unstableAttempts =
            new ConcurrentHashMap<>();

    /**
     * 创建生产环境 Agent 工厂。主对话和摘要压缩复用同一个真实模型实例，统一供应商配置与可观测链路。
     *
     * @param chatModel       application.yml 创建的 OpenAI-compatible 模型
     * @param modelProperties 不含业务状态的模型连接配置
     * @param modelConfiguration 真实模型构建器
     * @param knowledgeService RAG 知识库服务，供 search_knowledge 工具调用
     */
    @Autowired
    public ResearchAgentFactory(ChatModel chatModel, ModelProperties modelProperties,
                                ModelConfiguration modelConfiguration, KnowledgeService knowledgeService) {
        this(chatModel, chatModel, modelProperties, modelConfiguration, knowledgeService);
    }

    /**
     * 使用固定模型创建工厂，供不需要运行时重建模型的测试或嵌入场景使用。
     *
     * @param chatModel       主对话与摘要共用的模型
     * @param modelProperties 模型状态配置
     */
    public ResearchAgentFactory(ChatModel chatModel, ModelProperties modelProperties) {
        this(chatModel, chatModel, modelProperties, null, null);
    }

    /**
     * 创建可替换摘要模型的 Agent 工厂，供集成测试注入确定性模型而不访问外部服务。
     *
     * @param chatModel        执行 Agent 决策的模型
     * @param compressionModel 生成上下文摘要的模型
     * @param modelProperties  模型配置及可用状态
     */
    public ResearchAgentFactory(ChatModel chatModel, ChatModel compressionModel,
                                ModelProperties modelProperties) {
        this(chatModel, compressionModel, modelProperties, null, null);
    }

    /**
     * 创建支持运行时 UI 模型配置的 Agent 工厂。
     *
     * @param chatModel          启动时模型或测试替身
     * @param compressionModel   上下文摘要模型或测试替身
     * @param modelProperties    服务端默认模型配置
     * @param modelConfiguration 真实模型构建器
     * @param knowledgeService   RAG 知识库服务；测试可传 {@code null} 跳过知识工具
     */
    public ResearchAgentFactory(ChatModel chatModel, ChatModel compressionModel,
                                ModelProperties modelProperties, ModelConfiguration modelConfiguration,
                                KnowledgeService knowledgeService) {
        this.chatModel = chatModel;
        this.compressionModel = compressionModel;
        this.modelProperties = modelProperties;
        this.modelConfiguration = modelConfiguration;
        this.knowledgeService = knowledgeService;
    }

    /**
     * 根据用户提交的完整配置创建 Agent，并绑定按 conversationId 隔离的压缩状态存储。
     *
     * @param agentId          服务端为本 Agent 分配的稳定 ID
     * @param request          Agent、执行策略、预算、重试与压缩配置
     * @param compressionStore 当前 Agent 下按会话隔离的压缩 CAS 存储
     * @return 配置表单、工具、审批、压缩和执行策略的 Agent
     */
    public synchronized Agent create(String agentId, CreateAgentRequest request,
                                     InMemoryCompressionStateStore compressionStore) {
        applyModelRequest(request);
        if (!modelProperties.isConfigured()) {
            throw new IllegalStateException(
                    "尚未配置真实大模型 API Key，请在页面左侧“模型连接”中填写后重新创建 Agent；"
                            + "部署环境也可以通过 LLM_API_KEY 注入默认值。");
        }
        // 知识库 embedding 与聊天模型一起提交；失败不阻断 Agent 创建，状态在知识库面板可见。
        if (knowledgeService != null && request.getEmbeddingEndpoint() != null
                && !request.getEmbeddingEndpoint().trim().isEmpty()) {
            try {
                knowledgeService.configure(request.getEmbeddingEndpoint(), request.getEmbeddingApiKey(),
                        request.getEmbeddingModel(), request.getKnowledgeSearchMode());
            } catch (RuntimeException error) {
                // 例如签名冲突需要先重建；保留给用户可见的错误在 KnowledgeService.status 中。
            }
        }
        request.setEmbeddingApiKey(null);
        // 用户输入工具携带 JSON Schema；前端只负责通用渲染，不知道研究表单有哪些字段。
        Tool inputTool = AgentUserInputTool.builder()
                .form(researchBriefForm())
                .build();

        // 普通研究工具主动发送进度，并保留短暂执行窗口用于演示运行中协作式暂停。
        Tool researchTool = Tool.builder("research_market", "检索并归纳市场资料")
                .addParameter(Parameter.builder().name("query").type("string").required(true).build())
                .function(arguments -> {
                    AgentToolContext.current().emitProgress("已检索产业报告", percent(35));
                    // 120ms 让人工操作可观察，同时远低于预算与测试超时。
                    LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(120));
                    AgentToolContext.current().emitProgress("正在交叉验证样本", percent(78));
                    return "已整理 12 条来源：企业采用加速，工具调用可靠性与可观测性成为采购重点。";
                })
                .build();

        // 按 Turn 和工具名记录恢复次数；这样 AgentWorker 重试或模型重新生成 ToolCall ID
        // 时仍共享同一个“上游服务恢复”过程，不会因为新的 ID 把 attempt 重置为 1。
        Tool unstableTool = Tool.builder("verify_sources", "验证来源可访问性，前两次调用会失败")
                .function(arguments -> {
                    AgentToolContext context = AgentToolContext.current();
                    String key = context.getTurnId() + ":" + context.getToolName();
                    int attempt = unstableAttempts
                            .computeIfAbsent(key, ignored -> new AtomicInteger())
                            .incrementAndGet();
                    if (attempt < 3) {
                        throw new IllegalStateException("上游来源服务暂时不可用，attempt=" + attempt);
                    }
                    return "来源验证完成，12/12 可追溯";
                })
                .build();

        // sideEffect 元数据由审批策略读取，任何外部发布都必须先进入 TOOL_APPROVAL Suspension。
        Tool publishTool = Tool.builder("publish_report", "将报告发布到管理层简报频道")
                .addParameter(Parameter.builder().name("channel").type("string").required(true).build())
                .metadata("sideEffect", true)
                .metadata("riskLevel", "HIGH")
                .function(arguments -> "报告已发布到 " + arguments.get("channel"))
                .build();

        // RAG 知识库检索工具：走 RogueMemory 混合检索（向量 ANN + BM25），命中片段带标题、
        // 片段序号与相关度分数返回，模型引用时可标注来源。知识库未配置时返回说明文本。
        // Agent 可绑定知识库范围（knowledgeNamespace），实现按文档检索。
        final String knowledgeNamespace = request.getKnowledgeNamespace();
        Tool knowledgeTool = Tool.builder("search_knowledge",
                        "检索本地 RAG 知识库，返回与问题最相关的资料片段（含来源与相关度）")
                .addParameter(Parameter.builder().name("query").type("string").required(true).build())
                .function(arguments -> knowledgeService == null
                        ? "知识库服务未启用。"
                        : knowledgeService.searchForTool(String.valueOf(arguments.get("query")),
                        knowledgeNamespace))
                .build();

        // 按页面选择的 Decider 与 Compressor 组合真实增量压缩，不预置虚假历史。
        ChatModel activeChatModel = modelConfiguration == null ? chatModel : modelConfiguration.buildChatModel(modelProperties);
        ChatModel activeCompressionModel = modelConfiguration == null ? compressionModel : activeChatModel;
        ChatOptions compressionChatOptions = chatOptions(modelProperties);
        // 逐消息压缩协议要求顶层 JSON 数组，不能继承主对话的 JSON_OBJECT 响应约束。
        if ("PER_MESSAGE".equals(request.getCompressionMode())) {
            compressionChatOptions.setResponseFormat(null);
        }
        AgentContextModelCompressorOptions compressorOptions = AgentContextModelCompressorOptions.builder()
                .instruction(request.getCompressionInstruction())
                .historyHeader(request.getCompressionHistoryHeader())
                .perMessageRequest(request.getCompressionPerMessageRequest())
                .summaryPrefix(request.getCompressionSummaryPrefix())
                .chatOptions(compressionChatOptions)
                .modelCallTimeoutMillis(request.getCompressionModelCallTimeoutMillis())
                .maxInputCharacters(request.getCompressionMaxInputCharacters())
                .maxOutputCharacters(request.getCompressionMaxOutputCharacters())
                .build();
        AgentContextCompressor compressor = "PER_MESSAGE".equals(request.getCompressionMode())
                ? perMessageCompressor(activeCompressionModel, compressorOptions)
                : AgentContextCompressors.model(activeCompressionModel, compressorOptions);
        AgentContextCompressionPolicy compression = AgentContextCompressionPolicy.builder()
                .stateStore(compressionStore)
                .decider(compressionDecider(request))
                .compressor(compressor)
                .tokenEstimator(ShowcaseTokenEstimator::estimate)
                .compactCompletedToolTurns(request.isCompactCompletedToolTurns())
                .keepRecentTurns(request.getCompressionKeepRecentTurns())
                .compressionFailureStrategy(AgentCompressionFailureStrategy.valueOf(
                        request.getCompressionFailureStrategy()))
                .build();

        AgentExecutionPolicy policy = AgentExecutionPolicy.builder()
                .maxIterations(request.getMaxIterations())
                .maxSteps(request.getMaxSteps())
                .toolErrorStrategy(ToolErrorStrategy.valueOf(request.getToolErrorStrategy()))
                .retryPolicy(AgentRetryPolicy.builder()
                        .maxRetries(request.getMaxRetries())
                        .initialDelayMillis(request.getInitialDelayMillis())
                        .maxDelayMillis(request.getMaxDelayMillis())
                        .multiplier(request.getRetryMultiplier())
                        .build())
                .interruptedToolMessageTemplate(request.getInterruptedToolMessageTemplate())
                .interruptedTurnMessageTemplate(request.getInterruptedTurnMessageTemplate())
                .cancellationReason(request.getCancellationReason())
                .modelCallTimeoutMillis(request.getModelCallTimeoutMillis())
                .toolExecutionTimeoutMillis(request.getToolExecutionTimeoutMillis())
                .externalToolTimeoutMillis(request.getExternalToolTimeoutMillis())
                .approvalTimeoutMillis(request.getApprovalTimeoutMillis())
                .userInputTimeoutMillis(request.getUserInputTimeoutMillis())
                .suspensionExpirationStrategy(AgentSuspensionExpirationStrategy.valueOf(
                        request.getSuspensionExpirationStrategy()))
                .toolResultMaxCharacters(request.getToolResultMaxCharacters())
                .externalToolResultMaxCharacters(request.getExternalToolResultMaxCharacters())
                .toolResultOverflowStrategy(AgentToolResultOverflowStrategy.valueOf(
                        request.getToolResultOverflowStrategy()))
                .toolExecutionMode(AgentToolExecutionMode.valueOf(request.getToolExecutionMode()))
                .maxParallelToolCalls(request.getMaxParallelToolCalls())
                .parallelFailureStrategy(AgentParallelFailureStrategy.valueOf(
                        request.getParallelFailureStrategy()))
                .budget(AgentBudget.builder()
                        .maxInputTokens(request.getMaxInputTokens())
                        .maxOutputTokens(request.getMaxOutputTokens())
                        .maxTotalTokens(request.getMaxTotalTokens())
                        .maxToolCalls(request.getMaxToolCalls())
                        .maxDurationMillis(request.getMaxDurationMillis())
                        .build())
                .build();

        return Agent.builder(request.getName())
                .id(agentId)
                .version(request.getVersion())
                .name(request.getName())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .chatModel(activeChatModel)
                .chatOptions(chatOptions(modelProperties))
                .tools(Arrays.asList(inputTool, researchTool, unstableTool, publishTool, knowledgeTool))
                .toolApprovalPolicy((turn, call, tool) ->
                        Boolean.TRUE.equals(tool.getMetadata().get("sideEffect"))
                                ? ToolApprovalDecision.requireApproval()
                                .code("EXTERNAL_PUBLISH_REVIEW")
                                .message("报告即将发布到外部可见频道")
                                .reason("该操作会产生可见的外部副作用")
                                .metadata("riskLevel", tool.getMetadata().get("riskLevel"))
                                .build()
                                : ToolApprovalDecision.ALLOW)
                .compressionPolicy(compression)
                .executionPolicy(policy)
                // 复用 Showcase 的确定性 Token 估算器，启用 Agents-Flex 原生按完整 Turn 裁剪上下文。
                .maxAttachedTokens(request.getMaxAttachedTokens(), ShowcaseTokenEstimator::estimate)
                .maxAttachedTurns(request.getMaxAttachedTurns())
                .maxAttachedMessages(request.getMaxAttachedMessages())
                .build();
    }

    /**
     * 仅应用聊天模型连接配置到服务端内存，不构建 Agent。
     *
     * <p>页面“应用配置”可以立即让模型状态生效并反映到头部与输入框可用性上；后续创建 Agent
     * 仍会再次合并同一份配置。apiKey 只留在内存，返回值使用不含密钥的公开视图。</p>
     *
     * @param request 模型连接与采样参数；留空字段沿用当前服务端配置
     * @return 应用后的不含 API Key 模型状态
     */
    public synchronized Map<String, Object> applyModelConnection(UpdateModelRequest request) {
        CreateAgentRequest carrier = new CreateAgentRequest();
        carrier.setModelProvider(request.getModelProvider());
        carrier.setModelEndpoint(request.getModelEndpoint());
        carrier.setModelRequestPath(request.getModelRequestPath());
        carrier.setModelApiKey(request.getModelApiKey());
        carrier.setModelName(request.getModelName());
        carrier.setModelTemperature(request.getModelTemperature());
        carrier.setModelThinkingEnabled(request.getModelThinkingEnabled());
        carrier.setModelThinkingProtocol(request.getModelThinkingProtocol());
        carrier.setModelSeed(request.getModelSeed());
        carrier.setModelTopP(request.getModelTopP());
        carrier.setModelTopK(request.getModelTopK());
        carrier.setModelMaxTokens(request.getModelMaxTokens());
        carrier.setModelStop(request.getModelStop());
        carrier.setModelIncludeUsage(request.getModelIncludeUsage());
        carrier.setModelResponseFormat(request.getModelResponseFormat());
        carrier.setModelRetryEnabled(request.getModelRetryEnabled());
        carrier.setModelRetryCount(request.getModelRetryCount());
        carrier.setModelRetryInitialDelayMillis(request.getModelRetryInitialDelayMillis());
        if (request.getMaxInputTokens() != null) carrier.setMaxInputTokens(request.getMaxInputTokens());
        if (request.getMaxOutputTokens() != null) carrier.setMaxOutputTokens(request.getMaxOutputTokens());
        if (request.getMaxTotalTokens() != null) carrier.setMaxTotalTokens(request.getMaxTotalTokens());
        if (request.getMaxAttachedTokens() != null) carrier.setMaxAttachedTokens(request.getMaxAttachedTokens());
        applyModelRequest(carrier);
        return modelProperties.publicView();
    }

    /**
     * 将 UI 提交的非空模型字段覆盖到当前内存配置；未填写字段继续使用 application.yml/环境变量值。
     * API Key 只停留在后端内存，不会进入 AgentDefinition 的回显视图。
     *
     * @param request Agent 创建请求
     */
    private void applyModelRequest(CreateAgentRequest request) {
        if (request.getModelProvider() != null && !request.getModelProvider().trim().isEmpty())
            modelProperties.setProvider(request.getModelProvider().trim());
        if (request.getModelEndpoint() != null && !request.getModelEndpoint().trim().isEmpty())
            modelProperties.setEndpoint(request.getModelEndpoint().trim());
        if (request.getModelRequestPath() != null && !request.getModelRequestPath().trim().isEmpty())
            modelProperties.setRequestPath(request.getModelRequestPath().trim());
        if (request.getModelApiKey() != null && !request.getModelApiKey().trim().isEmpty())
            modelProperties.setApiKey(request.getModelApiKey().trim());
        if (request.getModelName() != null && !request.getModelName().trim().isEmpty())
            modelProperties.setModel(request.getModelName().trim());
        if (request.getModelTemperature() != null) modelProperties.setTemperature(request.getModelTemperature());
        if (request.getModelThinkingEnabled() != null)
            modelProperties.setThinkingEnabled(request.getModelThinkingEnabled());
        if (request.getModelThinkingProtocol() != null && !request.getModelThinkingProtocol().trim().isEmpty())
            modelProperties.setThinkingProtocol(request.getModelThinkingProtocol().trim());
        if (request.getModelSeed() != null) modelProperties.setSeed(request.getModelSeed().trim());
        if (request.getModelTopP() != null) modelProperties.setTopP(request.getModelTopP());
        if (request.getModelTopK() != null) modelProperties.setTopK(request.getModelTopK());
        if (request.getModelMaxTokens() != null) modelProperties.setMaxTokens(request.getModelMaxTokens());
        if (request.getModelStop() != null) modelProperties.setStop(request.getModelStop());
        if (request.getModelIncludeUsage() != null) modelProperties.setIncludeUsage(request.getModelIncludeUsage());
        if (request.getModelResponseFormat() != null && !request.getModelResponseFormat().trim().isEmpty())
            modelProperties.setResponseFormat(request.getModelResponseFormat().trim());
        if (request.getModelRetryEnabled() != null)
            modelProperties.setRetryEnabled(request.getModelRetryEnabled());
        if (request.getModelRetryCount() != null)
            modelProperties.setRetryCount(request.getModelRetryCount());
        if (request.getModelRetryInitialDelayMillis() != null)
            modelProperties.setRetryInitialDelayMillis(request.getModelRetryInitialDelayMillis());

        // 将未填写的可公开字段回填为当前服务端默认值，使 AgentDefinition 快照与实际 ChatModel 一致。
        if (isBlank(request.getModelProvider())) request.setModelProvider(modelProperties.getProvider());
        if (isBlank(request.getModelEndpoint())) request.setModelEndpoint(modelProperties.getEndpoint());
        if (isBlank(request.getModelRequestPath())) request.setModelRequestPath(modelProperties.getRequestPath());
        if (isBlank(request.getModelApiKey())) request.setModelApiKey(modelProperties.getApiKey());
        if (isBlank(request.getModelName())) request.setModelName(modelProperties.getModel());
        if (request.getModelTemperature() == null) request.setModelTemperature(modelProperties.getTemperature());
        if (request.getModelThinkingEnabled() == null)
            request.setModelThinkingEnabled(modelProperties.isThinkingEnabled());
        if (isBlank(request.getModelThinkingProtocol()))
            request.setModelThinkingProtocol(modelProperties.getThinkingProtocol());
        if (request.getModelSeed() == null) request.setModelSeed(modelProperties.getSeed());
        if (request.getModelTopP() == null) request.setModelTopP(modelProperties.getTopP());
        if (request.getModelTopK() == null) request.setModelTopK(modelProperties.getTopK());
        if (request.getModelMaxTokens() == null) request.setModelMaxTokens(modelProperties.getMaxTokens());
        if (request.getModelStop() == null) request.setModelStop(modelProperties.getStop());
        if (request.getModelIncludeUsage() == null) request.setModelIncludeUsage(modelProperties.getIncludeUsage());
        if (isBlank(request.getModelResponseFormat()))
            request.setModelResponseFormat(modelProperties.getResponseFormat());
        if (request.getModelRetryEnabled() == null)
            request.setModelRetryEnabled(modelProperties.isRetryEnabled());
        if (request.getModelRetryCount() == null)
            request.setModelRetryCount(modelProperties.getRetryCount());
        if (request.getModelRetryInitialDelayMillis() == null)
            request.setModelRetryInitialDelayMillis(modelProperties.getRetryInitialDelayMillis());
    }

    /**
     * 判断文本是否为空，集中处理 UI 可选模型字段的默认值回填。
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 将 UI 和 application.yml 中的通用 ChatOptions 映射到每个 Agent 的请求参数模板。
     * 空值不写入模型请求，避免覆盖供应商自身默认值。
     */
    private ChatOptions chatOptions(ModelProperties properties) {
        ChatOptions.Builder builder = ChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .thinkingEnabled(properties.isThinkingEnabled())
                .includeUsage(properties.getIncludeUsage())
                .retryEnabled(properties.isRetryEnabled())
                .retryCount(properties.getRetryCount())
                .retryInitialDelayMs(properties.getRetryInitialDelayMillis());
        if (properties.getSeed() != null && !properties.getSeed().trim().isEmpty())
            builder.seed(properties.getSeed().trim());
        if (properties.getTopP() != null) builder.topP(properties.getTopP());
        if (properties.getTopK() != null) builder.topK(properties.getTopK());
        if (properties.getMaxTokens() != null) builder.maxTokens(properties.getMaxTokens());
        if (properties.getStop() != null && !properties.getStop().isEmpty()) builder.stop(properties.getStop());
        if ("JSON_OBJECT".equalsIgnoreCase(properties.getResponseFormat())) builder.responseFormatToJsonObject();
        return builder.build();
    }

    /**
     * 将页面中的声明式触发方式映射为 Agents-Flex 原生压缩决策器。
     * 回调实现仍由框架工厂提供，浏览器只选择稳定枚举和阈值。
     */
    private static AgentContextCompressionDecider compressionDecider(CreateAgentRequest request) {
        switch (request.getCompressionDecider()) {
            case "PENDING_TURNS":
                return AgentContextCompressionDeciders.pendingTurnsAtLeast(
                        request.getCompressionTurnThreshold());
            case "PENDING_TOKENS":
                return AgentContextCompressionDeciders.pendingTokensAtLeast(
                        request.getCompressionTokenThreshold());
            case "ALWAYS":
                return AgentContextCompressionDeciders.always();
            case "NEVER":
                return AgentContextCompressionDeciders.never();
            case "PENDING_MESSAGES":
            default:
                return AgentContextCompressionDeciders.pendingMessagesAtLeast(
                        request.getCompressionMessageThreshold());
        }
    }

    /**
     * 使用原生逐消息压缩器，并修正 2.2.8 中 AiMessage.setContent 会累计 fullContent 的兼容问题。
     * UserMessage 和工具协议消息保持框架原始处理；这里只让模型可见正文与压缩后的 content 一致。
     */
    private static AgentContextCompressor perMessageCompressor(
            ChatModel model, AgentContextModelCompressorOptions options) {
        AgentContextCompressor delegate = AgentContextCompressors.perMessageModel(model, options);
        return messages -> {
            List<Message> compressed = delegate.compress(messages);
            for (Message message : compressed) {
                if (message instanceof AiMessage && !((AiMessage) message).hasToolCalls()) {
                    AiMessage aiMessage = (AiMessage) message;
                    aiMessage.setFullContent(aiMessage.getContent());
                }
            }
            return compressed;
        };
    }

    /**
     * 返回不包含 API Key 的模型公开状态，供健康检查和聊天区标题栏展示。
     *
     * @return 可安全序列化到浏览器的模型信息
     */
    public Map<String, Object> modelInfo() {
        return modelProperties.publicView();
    }

    /**
     * 构造由 Runtime 下发的市场研究 JSON Schema 表单。
     *
     * @return research_brief 表单定义，包含行业、市场、时间和深度字段
     */
    private static AgentFormDefinition researchBriefForm() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", "补充研究范围");
        schema.put("description", "这些字段来自 Agent Runtime 固化的 JSON Schema。前端不包含业务表单定义。");
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        // 行业不能使用封闭枚举，否则“新能源”等未列举主题会被迫替换成错误的预设值。
        properties.put("industry", field("string", "目标行业", null, null,
                "请输入具体研究行业，例如：新能源汽车、光伏储能、低空经济或金融科技"));
        properties.put("market", field("string", "目标市场", "中国", null,
                "请输入国家、地区或细分市场"));
        properties.put("timeRange", field("string", "时间范围", "2025-2026", null,
                "请输入研究覆盖的年份或时间区间"));
        properties.put("depth", field("string", "报告深度", null,
                Arrays.asList("执行摘要", "标准分析", "深度研究"),
                "请选择期望的分析详细程度"));
        schema.put("properties", properties);
        schema.put("required", Arrays.asList("industry", "market", "timeRange", "depth"));

        return AgentFormDefinition.builder("research_brief")
                .description("市场研究的行业、市场、时间范围和报告深度")
                .schema(schema)
                .build();
    }

    /**
     * 创建单个 JSON Schema 属性，按需附加默认值、枚举约束和填写说明。
     *
     * @param type         JSON Schema 基础类型
     * @param title        中文字段标题
     * @param defaultValue 可选默认值
     * @param enums        可选枚举值列表
     * @param description  可选字段说明，用于指导用户填写但不限制可选内容
     * @return 可直接写入 properties 的字段映射
     */
    private static Map<String, Object> field(String type, String title, String defaultValue,
                                             java.util.List<String> enums, String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", type);
        field.put("title", title);
        if (defaultValue != null) field.put("default", defaultValue);
        if (enums != null) field.put("enum", enums);
        if (description != null) field.put("description", description);
        return field;
    }

    /**
     * 组装工具进度事件要求的 metadata 结构。
     *
     * @param value 0 到 100 的完成百分比
     * @return 仅包含 percent 字段的不可变映射
     */
    private static Map<String, Object> percent(int value) {
        return Collections.<String, Object>singletonMap("percent", value);
    }
}
