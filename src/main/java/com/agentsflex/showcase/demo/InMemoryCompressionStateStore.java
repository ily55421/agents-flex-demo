package com.agentsflex.showcase.demo;

import com.agentsflex.agent.compression.AgentContextCompressionState;
import com.agentsflex.agent.compression.AgentContextCompressionStateStore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Showcase 增量压缩策略使用的进程内 CAS 状态存储。
 */
public final class InMemoryCompressionStateStore implements AgentContextCompressionStateStore {

    private final ConcurrentMap<String, AgentContextCompressionState> states =
            new ConcurrentHashMap<>();

    /**
     * 按会话读取最近一次成功保存的压缩状态。
     *
     * @param conversationId ChatMemory 会话 ID
     * @return 当前压缩状态；首次压缩前返回 {@code null}
     */
    @Override
    public AgentContextCompressionState load(String conversationId) {
        return states.get(conversationId);
    }

    /**
     * 使用乐观版本号原子保存压缩状态，防止并发压缩覆盖较新的摘要。
     *
     * @param conversationId  ChatMemory 会话 ID
     * @param state           要保存的新状态
     * @param expectedVersion 调用方读取状态时看到的版本号
     * @return 版本匹配并保存成功返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public synchronized boolean save(String conversationId,
                                     AgentContextCompressionState state,
                                     long expectedVersion) {
        AgentContextCompressionState current = states.get(conversationId);
        long actualVersion = current == null ? 0 : current.getVersion();
        if (actualVersion != expectedVersion) {
            return false;
        }
        states.put(conversationId, state);
        return true;
    }
}
