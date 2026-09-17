package com.agentsflex.showcase.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义使用已创建 Agent 开启新对话所需的最小请求。
 */
public class CreateRunRequest {

    @NotBlank
    private String agentId;
    @NotBlank
    private String task;

    /**
     * @return 由独立 Agent 创建接口返回的服务端 Agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * @param agentId 已成功创建且仍存在于当前服务进程的 Agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * @return 用户提交给 Agent 的完整研究任务
     */
    public String getTask() {
        return task;
    }

    /**
     * @param task 非空的研究任务文本
     */
    public void setTask(String task) {
        this.task = task;
    }

}
