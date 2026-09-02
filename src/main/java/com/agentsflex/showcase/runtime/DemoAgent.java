package com.agentsflex.showcase.runtime;

import com.agentsflex.agent.Agent;
import com.agentsflex.showcase.model.CreateAgentRequest;
import com.agentsflex.showcase.demo.InMemoryCompressionStateStore;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保存用户已经创建、但尚未绑定具体对话的真实 Agent 定义。
 * Agent 本身是不可变对象，可被同一配置下的多个新对话安全复用；压缩状态按 conversationId 隔离。
 */
final class DemoAgent {

    final String id;
    final Agent agent;
    final CreateAgentRequest configuration;
    final InMemoryCompressionStateStore compressionStore;
    final long createdAt;

    /**
     * @param id               服务端生成的稳定 Agent ID
     * @param agent            已完成 Agents-Flex Builder 校验的真实 Agent
     * @param configuration    创建该 Agent 时使用的完整配置
     * @param compressionStore 按 conversationId 保存摘要的共享 CAS 存储
     */
    DemoAgent(String id, Agent agent, CreateAgentRequest configuration,
              InMemoryCompressionStateStore compressionStore) {
        this.id = id;
        this.agent = agent;
        this.configuration = configuration;
        this.compressionStore = compressionStore;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 生成可安全回显给浏览器的 Agent 配置，不包含模型 API Key 或可执行函数对象。
     *
     * @return 左侧配置状态和 Run Snapshot 共用的有序字段映射
     */
    Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("agentId", id);
        view.put("name", configuration.getName());
        view.put("version", configuration.getVersion());
        view.put("description", configuration.getDescription());
        view.put("instructions", configuration.getInstructions());
        view.put("maxIterations", configuration.getMaxIterations());
        view.put("maxSteps", configuration.getMaxSteps());
        view.put("maxAttachedTurns", configuration.getMaxAttachedTurns());
        view.put("maxAttachedTokens", configuration.getMaxAttachedTokens());
        view.put("maxAttachedMessages", configuration.getMaxAttachedMessages());
        // 返回非敏感模型参数，便于前端重载配置；API Key 永远不进入该视图。
        view.put("modelProvider", configuration.getModelProvider());
        view.put("modelEndpoint", configuration.getModelEndpoint());
        view.put("modelRequestPath", configuration.getModelRequestPath());
        view.put("modelName", configuration.getModelName());
        view.put("modelTemperature", configuration.getModelTemperature());
        view.put("modelThinkingEnabled", configuration.getModelThinkingEnabled());
        view.put("modelThinkingProtocol", configuration.getModelThinkingProtocol());
        view.put("modelSeed", configuration.getModelSeed());
        view.put("modelTopP", configuration.getModelTopP());
        view.put("modelTopK", configuration.getModelTopK());
        view.put("modelMaxTokens", configuration.getModelMaxTokens());
        view.put("modelStop", configuration.getModelStop());
        view.put("modelIncludeUsage", configuration.getModelIncludeUsage());
        view.put("modelResponseFormat", configuration.getModelResponseFormat());
        view.put("modelRetryEnabled", configuration.getModelRetryEnabled());
        view.put("modelRetryCount", configuration.getModelRetryCount());
        view.put("modelRetryInitialDelayMillis", configuration.getModelRetryInitialDelayMillis());
        view.put("maxInputTokens", configuration.getMaxInputTokens());
        view.put("maxOutputTokens", configuration.getMaxOutputTokens());
        view.put("maxTotalTokens", configuration.getMaxTotalTokens());
        view.put("maxToolCalls", configuration.getMaxToolCalls());
        view.put("maxDurationMillis", configuration.getMaxDurationMillis());
        view.put("maxRetries", configuration.getMaxRetries());
        view.put("initialDelayMillis", configuration.getInitialDelayMillis());
        view.put("maxDelayMillis", configuration.getMaxDelayMillis());
        view.put("retryMultiplier", configuration.getRetryMultiplier());
        view.put("compressionDecider", configuration.getCompressionDecider());
        view.put("compressionMessageThreshold", configuration.getCompressionMessageThreshold());
        view.put("compressionTurnThreshold", configuration.getCompressionTurnThreshold());
        view.put("compressionTokenThreshold", configuration.getCompressionTokenThreshold());
        view.put("compressionMode", configuration.getCompressionMode());
        view.put("toolErrorStrategy", configuration.getToolErrorStrategy());
        view.put("interruptedToolMessageTemplate", configuration.getInterruptedToolMessageTemplate());
        view.put("interruptedTurnMessageTemplate", configuration.getInterruptedTurnMessageTemplate());
        view.put("cancellationReason", configuration.getCancellationReason());
        view.put("modelCallTimeoutMillis", configuration.getModelCallTimeoutMillis());
        view.put("toolExecutionTimeoutMillis", configuration.getToolExecutionTimeoutMillis());
        view.put("externalToolTimeoutMillis", configuration.getExternalToolTimeoutMillis());
        view.put("approvalTimeoutMillis", configuration.getApprovalTimeoutMillis());
        view.put("userInputTimeoutMillis", configuration.getUserInputTimeoutMillis());
        view.put("suspensionExpirationStrategy", configuration.getSuspensionExpirationStrategy());
        view.put("toolResultMaxCharacters", configuration.getToolResultMaxCharacters());
        view.put("externalToolResultMaxCharacters", configuration.getExternalToolResultMaxCharacters());
        view.put("toolResultOverflowStrategy", configuration.getToolResultOverflowStrategy());
        view.put("toolExecutionMode", configuration.getToolExecutionMode());
        view.put("maxParallelToolCalls", configuration.getMaxParallelToolCalls());
        view.put("parallelFailureStrategy", configuration.getParallelFailureStrategy());
        view.put("compactCompletedToolTurns", configuration.isCompactCompletedToolTurns());
        view.put("compressionKeepRecentTurns", configuration.getCompressionKeepRecentTurns());
        view.put("compressionFailureStrategy", configuration.getCompressionFailureStrategy());
        view.put("compressionInstruction", configuration.getCompressionInstruction());
        view.put("compressionHistoryHeader", configuration.getCompressionHistoryHeader());
        view.put("compressionSummaryPrefix", configuration.getCompressionSummaryPrefix());
        view.put("compressionPerMessageRequest", configuration.getCompressionPerMessageRequest());
        view.put("compressionModelCallTimeoutMillis", configuration.getCompressionModelCallTimeoutMillis());
        view.put("compressionMaxInputCharacters", configuration.getCompressionMaxInputCharacters());
        view.put("compressionMaxOutputCharacters", configuration.getCompressionMaxOutputCharacters());
        view.put("tools", Arrays.asList("request_user_input", "research_market",
                "verify_sources", "publish_report"));
        view.put("approvalPolicy", "发布类副作用工具必须人工审批");
        view.put("createdAt", createdAt);
        return view;
    }
}
