package com.agentsflex.showcase.config;

import com.agentsflex.core.model.chat.ChatModel;
import com.agentsflex.model.chat.openai.OpenAIChatConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据 application.yml 创建生产环境唯一的真实 OpenAI-compatible ChatModel。
 */
@Configuration
public class ModelConfiguration {

    /**
     * 构建支持工具调用、流式输出、思考内容和原生可观测性的 ChatModel。
     * 未配置密钥时使用不可用占位值让应用正常启动，创建 Agent 时会返回明确配置错误。
     *
     * @param properties application.yml 模型配置
     * @return Agents-Flex OpenAIChatModel
     */
    @Bean
    public ChatModel showcaseChatModel(ModelProperties properties) {
        return buildChatModel(properties);
    }

    /**
     * 按当前内存中的模型配置创建独立 ChatModel，支持 UI 在运行时为新 Agent 提供连接参数。
     *
     * @param properties 已合并环境变量或 UI 配置的模型参数
     * @return 支持工具、流式输出和可观测性的真实模型
     */
    public ChatModel buildChatModel(ModelProperties properties) {
        String apiKey = properties.isConfigured() ? properties.getApiKey() : "not-configured";
        return OpenAIChatConfig.builder()
                .provider(properties.getProvider())
                .endpoint(properties.getEndpoint())
                .requestPath(properties.getRequestPath())
                .apiKey(apiKey)
                .model(properties.getModel())
                .supportTool(true)
                .supportToolMessage(true)
                .supportThinking(true)
                .thinkingEnabled(properties.isThinkingEnabled())
                .thinkingProtocol(properties.getThinkingProtocol())
                .preserveThinkingEnable(true)
                .observabilityEnabled(true)
                .logEnabled(true)
                .retryEnabled(properties.isRetryEnabled())
                .retryCount(properties.getRetryCount())
                .retryInitialDelayMs(properties.getRetryInitialDelayMillis())
                .buildModel();
    }
}
