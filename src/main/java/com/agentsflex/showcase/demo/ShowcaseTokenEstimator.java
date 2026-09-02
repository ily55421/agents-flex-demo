package com.agentsflex.showcase.demo;

import com.agentsflex.core.message.Message;

import java.util.List;

/**
 * 压缩策略和 UI 指标共用的确定性 Token 估算器。
 */
public final class ShowcaseTokenEstimator {

    /**
     * 工具类不持有状态，禁止实例化。
     */
    private ShowcaseTokenEstimator() {
    }

    /**
     * 按文本长度近似估算 Token；每条非空 Message 至少计为一个 Token。
     * 该估算只用于压缩阈值和展示，不替代模型返回的真实 usage。
     *
     * @param messages 要估算的上下文消息，可为空
     * @return 确定性的非负 Token 估算值
     */
    public static long estimate(List<Message> messages) {
        long total = 0;
        if (messages == null) return total;
        for (Message message : messages) {
            if (message == null) continue;
            String text = message.getTextContent();
            total += Math.max(1, text == null ? 0 : text.length() / 2);
        }
        return total;
    }
}
