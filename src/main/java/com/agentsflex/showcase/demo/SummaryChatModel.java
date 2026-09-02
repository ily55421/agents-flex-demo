package com.agentsflex.showcase.demo;

import com.agentsflex.core.message.AiMessage;
import com.agentsflex.core.model.chat.ChatContext;
import com.agentsflex.core.model.chat.ChatModel;
import com.agentsflex.core.model.chat.ChatOptions;
import com.agentsflex.core.model.chat.StreamResponseListener;
import com.agentsflex.core.model.chat.response.AiMessageResponse;
import com.agentsflex.core.prompt.Prompt;

/**
 * 通过 AgentContextCompressors.model 调用的离线上下文摘要模型。
 */
public final class SummaryChatModel implements ChatModel {

    /**
     * 同步生成稳定的中文语义摘要，并附带可计入预算的固定 Token usage。
     *
     * @param prompt  待压缩的历史上下文
     * @param options 本次模型调用选项，Demo 不需要读取其中的供应商参数
     * @return 包含摘要内容和 usage 的 AiMessageResponse
     */
    @Override
    public AiMessageResponse chat(Prompt prompt, ChatOptions options) {
        AiMessage summary = new AiMessage(
                "历史摘要：用户关注中国 AI Agent 市场规模、企业采用、基础设施成熟度与监管变化；"
                        + "既有约束要求中文输出、保留来源、区分事实与判断，并在外部发布前取得批准。");
        summary.setModel("showcase-summary-1");
        summary.setPromptTokens(320);
        summary.setCompletionTokens(72);
        summary.setTotalTokens(392);
        ChatContext context = new ChatContext();
        context.setPrompt(prompt);
        return new AiMessageResponse(context, null, summary);
    }

    /**
     * 明确拒绝流式摘要调用，因为增量压缩器在本 Demo 中只使用同步接口。
     *
     * @throws UnsupportedOperationException 任何流式调用都会抛出
     */
    @Override
    public void chatStream(Prompt prompt, StreamResponseListener listener, ChatOptions options) {
        throw new UnsupportedOperationException("Summary compression uses synchronous model calls");
    }
}
