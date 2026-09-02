package com.agentsflex.showcase.runtime;

import com.agentsflex.agent.event.AgentEvent;

import java.util.Map;

/**
 * 将框架不可变 AgentEvent 投影为可稳定序列化的 JSON 对象。
 */
public final class DemoEvent {

    private final String eventId;
    private final String runId;
    private final String agentId;
    private final String agentVersion;
    private final long sequence;
    private final String type;
    private final long occurredAt;
    private final Map<String, Object> data;
    private final String source;

    /**
     * 复制原生事件的身份、顺序、时间和业务数据，并显式标记数据来源。
     *
     * @param event Agents-Flex 发布的原生事件
     */
    public DemoEvent(AgentEvent event) {
        this.eventId = event.getEventId();
        this.runId = event.getTurnId();
        this.agentId = event.getAgentId();
        this.agentVersion = event.getAgentVersion();
        this.sequence = event.getSequence();
        this.type = event.getType().name();
        this.occurredAt = event.getOccurredAt();
        this.data = event.getData();
        this.source = "AGENTS_FLEX_NATIVE";
    }

    /**
     * @return 全局唯一事件 ID，前端用它合并 SSE 与 Snapshot 事件
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @return 产生该事件的 Agent Turn ID，也就是 Demo 的 runId
     */
    public String getRunId() {
        return runId;
    }

    /**
     * @return Agent 定义 ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * @return Agent 定义版本，用于识别恢复时采用的配置
     */
    public String getAgentVersion() {
        return agentVersion;
    }

    /**
     * @return 同一 Turn 内单调递增的事件序号
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * @return 原生 AgentEventType 名称
     */
    public String getType() {
        return type;
    }

    /**
     * @return 事件发生时的 Unix 毫秒时间戳
     */
    public long getOccurredAt() {
        return occurredAt;
    }

    /**
     * @return 事件携带的结构化上下文数据
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * @return 固定来源标记 AGENTS_FLEX_NATIVE
     */
    public String getSource() {
        return source;
    }
}
