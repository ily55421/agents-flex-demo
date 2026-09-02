package com.agentsflex.showcase.demo;

import com.agentsflex.agent.Agent;
import com.agentsflex.agent.AgentExecutionPolicy;
import com.agentsflex.agent.compression.AgentCompressionFailureStrategy;
import com.agentsflex.agent.compression.AgentContextCompressionResult;
import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.message.Message;
import com.agentsflex.core.message.UserMessage;
import com.agentsflex.core.model.chat.ChatOptions;
import com.agentsflex.core.model.chat.ChatContext;
import com.agentsflex.core.model.chat.ChatModel;
import com.agentsflex.core.model.chat.StreamResponseListener;
import com.agentsflex.core.model.chat.response.AiMessageResponse;
import com.agentsflex.core.prompt.Prompt;
import com.agentsflex.showcase.config.ModelProperties;
import com.agentsflex.showcase.model.CreateAgentRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证页面配置最终进入 Agents-Flex 不可变对象，而不只停留在 Demo 的回显 Map 中。
 */
class ResearchAgentFactoryConfigurationTest {

    /**
     * 覆盖 ChatOptions、Agent、ExecutionPolicy、Budget、Retry 和 Compression 的完整映射。
     */
    @Test
    void mapsEverySerializableConfigurationIntoNativeAgentObjects() {
        ModelProperties properties = configuredModel();
        CreateAgentRequest request = new CreateAgentRequest();
        request.setModelSeed("42");
        request.setModelTopP(0.75f);
        request.setModelTopK(40);
        request.setModelMaxTokens(2048);
        request.setModelStop(Arrays.asList("END", "###"));
        request.setModelIncludeUsage(false);
        request.setModelResponseFormat("JSON_OBJECT");
        request.setModelRetryEnabled(false);
        request.setModelRetryCount(5);
        request.setModelRetryInitialDelayMillis(321);
        request.setMaxIterations(9);
        request.setMaxSteps(45);
        request.setMaxAttachedTurns(4);
        request.setMaxAttachedMessages(28);
        request.setMaxAttachedTokens(6000);
        request.setMaxInputTokens(7000);
        request.setMaxOutputTokens(3000);
        request.setMaxTotalTokens(9000);
        request.setMaxToolCalls(12);
        request.setMaxDurationMillis(600000);
        request.setMaxRetries(4);
        request.setInitialDelayMillis(250);
        request.setMaxDelayMillis(4000);
        request.setRetryMultiplier(1.8d);
        request.setCompressionDecider("PENDING_TOKENS");
        request.setCompressionTokenThreshold(1234);
        request.setCompressionMode("PER_MESSAGE");
        request.setCompressionPerMessageRequest("仅返回逐消息 JSON 摘要");
        request.setToolErrorStrategy("RETURN_ERROR_TO_MODEL");
        request.setInterruptedToolMessageTemplate("工具中断：{reason}");
        request.setInterruptedTurnMessageTemplate("Turn 中断：{reason}");
        request.setCancellationReason("测试取消");
        request.setModelCallTimeoutMillis(120000);
        request.setToolExecutionTimeoutMillis(30000);
        request.setExternalToolTimeoutMillis(180000);
        request.setApprovalTimeoutMillis(240000);
        request.setUserInputTimeoutMillis(300000);
        request.setSuspensionExpirationStrategy("FAIL_TURN");
        request.setToolResultMaxCharacters(12000);
        request.setExternalToolResultMaxCharacters(16000);
        request.setToolResultOverflowStrategy("TRUNCATE");
        request.setToolExecutionMode("PARALLEL");
        request.setMaxParallelToolCalls(6);
        request.setParallelFailureStrategy("RETURN_ERRORS_TO_MODEL");
        request.setCompactCompletedToolTurns(false);
        request.setCompressionKeepRecentTurns(3);
        request.setCompressionFailureStrategy("USE_ORIGINAL");

        Agent agent = new ResearchAgentFactory(new ShowcaseChatModel(), new SummaryChatModel(), properties)
                .create("native-config-test", request, new InMemoryCompressionStateStore());

        ChatOptions options = agent.getChatOptions();
        assertThat(options.getSeed()).isEqualTo("42");
        assertThat(options.getTopP()).isEqualTo(0.75f);
        assertThat(options.getTopK()).isEqualTo(40);
        assertThat(options.getMaxTokens()).isEqualTo(2048);
        assertThat(options.getStop()).containsExactly("END", "###");
        assertThat(options.getIncludeUsage()).isFalse();
        assertThat(options.getResponseFormat()).containsEntry("type", "json_object");
        assertThat(options.getRetryEnabled()).isFalse();
        assertThat(options.getRetryCount()).isEqualTo(5);
        assertThat(options.getRetryInitialDelayMs()).isEqualTo(321);
        assertThat(agent.getMaxAttachedTurns()).isEqualTo(4);
        assertThat(agent.getMaxAttachedMessages()).isEqualTo(28);
        assertThat(agent.getMaxAttachedTokens()).isEqualTo(6000);

        AgentExecutionPolicy policy = agent.getExecutionPolicy();
        assertThat(policy.getMaxIterations()).isEqualTo(9);
        assertThat(policy.getMaxSteps()).isEqualTo(45);
        assertThat(policy.getToolErrorStrategy().name()).isEqualTo("RETURN_ERROR_TO_MODEL");
        assertThat(policy.getInterruptedToolMessageTemplate()).isEqualTo("工具中断：{reason}");
        assertThat(policy.getInterruptedTurnMessageTemplate()).isEqualTo("Turn 中断：{reason}");
        assertThat(policy.getCancellationReason()).isEqualTo("测试取消");
        assertThat(policy.getModelCallTimeoutMillis()).isEqualTo(120000);
        assertThat(policy.getToolExecutionTimeoutMillis()).isEqualTo(30000);
        assertThat(policy.getExternalToolTimeoutMillis()).isEqualTo(180000);
        assertThat(policy.getApprovalTimeoutMillis()).isEqualTo(240000);
        assertThat(policy.getUserInputTimeoutMillis()).isEqualTo(300000);
        assertThat(policy.getSuspensionExpirationStrategy().name()).isEqualTo("FAIL_TURN");
        assertThat(policy.getToolResultMaxCharacters()).isEqualTo(12000);
        assertThat(policy.getExternalToolResultMaxCharacters()).isEqualTo(16000);
        assertThat(policy.getToolResultOverflowStrategy().name()).isEqualTo("TRUNCATE");
        assertThat(policy.getToolExecutionMode().name()).isEqualTo("PARALLEL");
        assertThat(policy.getMaxParallelToolCalls()).isEqualTo(6);
        assertThat(policy.getParallelFailureStrategy().name()).isEqualTo("RETURN_ERRORS_TO_MODEL");
        assertThat(policy.getBudget().getMaxInputTokens()).isEqualTo(7000);
        assertThat(policy.getBudget().getMaxOutputTokens()).isEqualTo(3000);
        assertThat(policy.getBudget().getMaxTotalTokens()).isEqualTo(9000);
        assertThat(policy.getBudget().getMaxToolCalls()).isEqualTo(12);
        assertThat(policy.getBudget().getMaxDurationMillis()).isEqualTo(600000);
        assertThat(policy.getRetryPolicy().getMaxRetries()).isEqualTo(4);
        assertThat(policy.getRetryPolicy().getInitialDelayMillis()).isEqualTo(250);
        assertThat(policy.getRetryPolicy().getMaxDelayMillis()).isEqualTo(4000);
        assertThat(policy.getRetryPolicy().getMultiplier()).isEqualTo(1.8d);
        assertThat(agent.getCompressionPolicy().isCompactCompletedToolTurns()).isFalse();
        assertThat(agent.getCompressionPolicy().getKeepRecentTurns()).isEqualTo(3);
        assertThat(agent.getCompressionPolicy().getCompressionFailureStrategy())
                .isEqualTo(AgentCompressionFailureStrategy.USE_ORIGINAL);
    }

    /**
     * 将压缩输入限制设为极小值，验证限制真正进入原生压缩器，并由 USE_ORIGINAL 策略安全降级。
     */
    @Test
    void appliesCompressionSizeLimitsToTheNativeCompressor() {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setCompressionMessageThreshold(2);
        request.setCompressionMaxInputCharacters(1);
        request.setCompressionFailureStrategy("USE_ORIGINAL");
        Agent agent = new ResearchAgentFactory(
                new ShowcaseChatModel(), new SummaryChatModel(), configuredModel())
                .create("compression-limit-test", request, new InMemoryCompressionStateStore());
        List<Message> history = Arrays.<Message>asList(
                new UserMessage("需要保留的较长历史输入"),
                new AiMessage("需要保留的较长历史回答"));

        AgentContextCompressionResult result = agent.getCompressionPolicy()
                .compress("compression-limit-conversation", history);

        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getModelMessages())
                .extracting(Message::getTextContent)
                .containsExactly("需要保留的较长历史输入", "需要保留的较长历史回答");
    }

    /**
     * NEVER 决策器必须保留原上下文，证明页面选择实际进入原生压缩策略。
     */
    @Test
    void appliesNeverCompressionDeciderToNativePolicy() {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setCompressionDecider("NEVER");
        Agent agent = new ResearchAgentFactory(
                new ShowcaseChatModel(), new SummaryChatModel(), configuredModel())
                .create("compression-never-test", request, new InMemoryCompressionStateStore());
        List<Message> history = Arrays.<Message>asList(
                new UserMessage("原始用户消息"), new AiMessage("原始模型消息"));

        AgentContextCompressionResult result = agent.getCompressionPolicy()
                .compress("compression-never-conversation", history);

        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getModelMessages()).extracting(Message::getTextContent)
                .containsExactly("原始用户消息", "原始模型消息");
    }

    /**
     * 逐消息模式必须保留消息角色和 ID，并使用模型返回的逐条摘要替换正文。
     */
    @Test
    void appliesPerMessageCompressionModeToNativeCompressor() {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setCompressionDecider("ALWAYS");
        request.setCompressionMode("PER_MESSAGE");
        request.setCompressionPerMessageRequest("返回 messageId 和 summary 的 JSON 数组");
        request.setModelResponseFormat("JSON_OBJECT");
        UserMessage user = new UserMessage("很长的用户历史");
        user.setMessageId("user-1");
        AiMessage assistant = new AiMessage("很长的模型历史");
        assistant.setMessageId("assistant-1");
        Agent agent = new ResearchAgentFactory(
                new ShowcaseChatModel(), perMessageSummaryModel(), configuredModel())
                .create("compression-per-message-test", request, new InMemoryCompressionStateStore());

        AgentContextCompressionResult result = agent.getCompressionPolicy().compress(
                "compression-per-message-conversation", Arrays.<Message>asList(user, assistant));

        assertThat(result.isCompressed()).isTrue();
        assertThat(result.getModelMessages()).extracting(Message::getMessageId)
                .containsExactly("user-1", "assistant-1");
        assertThat(result.getModelMessages()).extracting(Message::getTextContent)
                .containsExactly("用户摘要", "模型摘要");
    }

    /**
     * 创建严格返回逐消息 JSON 协议的离线摘要模型。
     */
    private static ChatModel perMessageSummaryModel() {
        return new ChatModel() {
            @Override
            public AiMessageResponse chat(Prompt prompt, ChatOptions options) {
                assertThat(options.getResponseFormat()).isNull();
                ChatContext context = new ChatContext();
                context.setPrompt(prompt);
                return new AiMessageResponse(context, null, new AiMessage(
                        "[{\"messageId\":\"user-1\",\"summary\":\"用户摘要\"},"
                                + "{\"messageId\":\"assistant-1\",\"summary\":\"模型摘要\"}]"));
            }

            @Override
            public void chatStream(Prompt prompt, StreamResponseListener listener, ChatOptions options) {
                throw new UnsupportedOperationException("compression uses synchronous chat");
            }
        };
    }

    /**
     * 创建不访问网络但会通过工厂可用性检查的模型配置。
     */
    private static ModelProperties configuredModel() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("showcase-test");
        properties.setEndpoint("showcase://deterministic");
        properties.setApiKey("configuration-test-key");
        properties.setModel("showcase-deterministic-1");
        return properties;
    }
}
