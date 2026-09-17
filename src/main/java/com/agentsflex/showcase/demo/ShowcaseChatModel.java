package com.agentsflex.showcase.demo;

import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.message.Message;
import com.agentsflex.core.message.ToolCall;
import com.agentsflex.core.message.ToolMessage;
import com.agentsflex.core.model.chat.BaseChatConfig;
import com.agentsflex.core.model.chat.BaseChatModel;
import com.agentsflex.core.model.chat.ChatContext;
import com.agentsflex.core.model.chat.ChatContextHolder;
import com.agentsflex.core.model.chat.StreamResponseListener;
import com.agentsflex.core.model.chat.response.AiMessageResponse;
import com.agentsflex.core.model.client.ChatClient;
import com.agentsflex.core.model.client.ChatRequestSpec;
import com.agentsflex.core.model.client.StreamContext;
import com.agentsflex.core.prompt.Prompt;

import java.util.Collections;

/**
 * 可离线复现的确定性 ChatModel。
 *
 * <p>模型仍通过原生 ChatModel、流式响应和 ToolCall 协议工作，因此 Chat 可观测拦截器、
 * Token 统计与 AgentRunner 执行链都是真实的。这里只固定“模型下一步说什么”，挂起、重试、
 * 审批、预算和事件仍由 AgentRunner 决定。</p>
 */
public final class ShowcaseChatModel extends BaseChatModel<BaseChatConfig> {

    /**
     * 配置离线请求规格与同步/流式客户端，使调用仍经过 BaseChatModel 的原生拦截链。
     */
    public ShowcaseChatModel() {
        super(config());
        setChatRequestSpecBuilder(new com.agentsflex.core.model.client.ChatRequestSpecBuilder() {
            /**
             * 构造不会真正发出网络请求的 showcase 协议规格。
             *
             * @return 供 BaseChatModel 记录 endpoint 与请求元数据的规格
             */
            @Override
            public ChatRequestSpec buildRequestSpec(Prompt prompt,
                                                    com.agentsflex.core.model.chat.ChatOptions options, BaseChatConfig config) {
                return new ChatRequestSpec("showcase://deterministic", Collections.emptyMap(), 0, 0);
            }

            /**
             * 生成最小确定性请求体，仅保留 streaming 标志供 Trace 捕获。
             *
             * @return JSON 请求体文本
             */
            @Override
            public String buildRequestBody(Prompt prompt,
                                           com.agentsflex.core.model.chat.ChatOptions options, BaseChatConfig config,
                                           boolean streaming) {
                return streaming ? "{\"stream\":true}" : "{\"stream\":false}";
            }
        });
        // 同时实现同步和流式入口；Demo 开启 streaming，因此流式入口会产生 reasoning + 完整消息。
        setChatClient(new ChatClient(this) {
            /**
             * 同步模型入口，根据当前 ChatContext 生成下一条确定性消息。
             *
             * @param body BaseChatModel 生成的请求体，本实现不需要解析
             * @return 完整模型响应
             */
            @Override
            public AiMessageResponse chat(String body) {
                ChatContext context = ChatContextHolder.currentContext();
                return new AiMessageResponse(context, null, nextMessage(context.getPrompt()));
            }

            /**
             * 流式发送 reasoning/tool-call 增量和最终完整消息，完整模拟原生流式生命周期。
             *
             * @param body 请求体，本实现不需要解析
             * @param listener 接收 open、message 和 close 回调的监听器
             */
            @Override
            public void chatStream(String body, StreamResponseListener listener) {
                ChatContext context = ChatContextHolder.currentContext();
                AiMessage full = nextMessage(context.getPrompt());
                StreamContext stream = new StreamContext(ShowcaseChatModel.this, context, null);
                listener.onOpen(stream);
                AiMessage delta = new AiMessage();
                if (full.hasToolCalls()) {
                    delta.setReasoningContent("正在检查研究流程的下一个安全执行点");
                    delta.setToolCalls(full.getToolCalls());
                } else {
                    delta.setContent(full.getContent());
                }
                listener.onMessage(stream, new AiMessageResponse(context, null, delta));
                full.setFinished(true);
                stream.setFullMessage(full);
                listener.onMessage(stream, new AiMessageResponse(context, null, full));
                listener.onClose(stream);
            }
        });
    }

    /**
     * 构建 BaseChatModel 的供应商、模型、端点和重试配置。
     *
     * @return 关闭 SDK 内部重试且不访问网络的配置
     */
    private static BaseChatConfig config() {
        BaseChatConfig config = new BaseChatConfig();
        config.setProvider("showcase");
        config.setModel("showcase-deterministic-1");
        config.setEndpoint("showcase://deterministic");
        // 显式开启模型层 OTel 拦截器，保证测试替身与生产模型共享 Trace 契约。
        config.setObservabilityEnabled(true);
        config.setLogEnabled(false);
        config.setRetryEnabled(false);
        return config;
    }

    /**
     * 依据最后一条 ToolMessage 决定下一次 ToolCall 或最终回答。
     *
     * @param prompt 包含历史消息和工具结果的完整 Prompt
     * @return 带固定 usage 的下一条 AiMessage
     */
    private AiMessage nextMessage(Prompt prompt) {
        // 最后一条 ToolMessage 相当于确定性流程游标，按表单→研究→校验→发布→总结推进。
        ToolMessage lastTool = lastToolMessage(prompt);
        AiMessage message;
        if (lastTool == null) {
            message = toolCall("form-details", "request_user_input",
                    "{\"formKey\":\"research_brief\"}");
        } else if ("form-details".equals(lastTool.getToolCallId())) {
            message = toolCall("research-1", "research_market",
                    "{\"query\":\"2026 China AI Agent market\"}");
        } else if ("research-1".equals(lastTool.getToolCallId())) {
            message = toolCall("research-retry", "verify_sources", "{}");
        } else if ("research-retry".equals(lastTool.getToolCallId())) {
            message = toolCall("publish-report", "publish_report",
                    "{\"channel\":\"executive-briefing\"}");
        } else {
            String content = lastTool.getTextContent();
            message = new AiMessage(content != null && content.contains("tool_rejected")
                    ? "发布操作已被拒绝。研究结论已保留为内部草稿，未产生外部副作用。"
                    : "研究任务已完成。报告已生成并发布到 executive-briefing，所有关键执行节点均已进入 Trace。");
        }
        applyUsage(message);
        return message;
    }

    /**
     * 从 Prompt 尾部查找最近的工具结果，用作确定性流程游标。
     *
     * @param prompt 当前模型 Prompt
     * @return 最后一条 ToolMessage；尚未调用工具时返回 {@code null}
     */
    private static ToolMessage lastToolMessage(Prompt prompt) {
        ToolMessage last = null;
        for (Message message : prompt.getMessages()) {
            if (message instanceof ToolMessage toolMessage) {
                last = toolMessage;
            }
        }
        return last;
    }

    /**
     * 创建仅包含一个原生 ToolCall 的 AiMessage。
     *
     * @param id        ToolCall 关联 ID
     * @param name      工具注册名
     * @param arguments JSON 参数文本
     * @return 可由 AgentRunner 解析执行的模型消息
     */
    private static AiMessage toolCall(String id, String name, String arguments) {
        AiMessage message = new AiMessage();
        message.setToolCalls(Collections.singletonList(new ToolCall(id, name, arguments)));
        return message;
    }

    /**
     * 写入固定模型名与 Token usage，供预算控制和 OTel GenAI 属性统计。
     *
     * @param message 要补充 usage 的模型消息
     */
    private static void applyUsage(AiMessage message) {
        // 固定 usage 便于稳定演示预算累计，同时会进入原生 OTel GenAI Span 属性。
        message.setModel("showcase-deterministic-1");
        message.setPromptTokens(168);
        message.setCompletionTokens(44);
        message.setTotalTokens(212);
    }
}
