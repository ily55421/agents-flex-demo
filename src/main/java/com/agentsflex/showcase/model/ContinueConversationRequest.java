package com.agentsflex.showcase.model;

import javax.validation.constraints.NotBlank;

/**
 * 定义同一 ChatMemory 会话中新一轮用户消息的请求体。
 */
public class ContinueConversationRequest {

    @NotBlank
    private String message;

    /**
     * @return 用户发送的新一轮非空消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message 要追加到当前会话并创建新 AgentTurn 的文本
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
