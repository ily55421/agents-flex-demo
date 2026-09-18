package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重排提供方验证：默认未启用；禁用时绝不发起网络调用并保持检索原序。
 */
class RerankProviderTest {

    /**
     * 默认配置（开关关闭或地址/模型缺失）时不可用，rerank 返回 null。
     */
    @Test
    void unavailableWhenNotConfigured() {
        KnowledgeProperties properties = new KnowledgeProperties();
        RerankProvider provider = new RerankProvider(properties);
        assertThat(provider.available()).isFalse();
        assertThat(provider.rerank("查询", List.of("片段一", "片段二"))).isNull();

        properties.setRerankEnabled(true);
        // 开关打开但地址/模型缺失：仍不可用
        assertThat(provider.available()).isFalse();
        assertThat(provider.rerank("查询", List.of("片段一"))).isNull();
    }

    /**
     * 空候选列表不发起调用，直接返回 null。
     */
    @Test
    void skipsEmptyDocuments() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setRerankEnabled(true);
        properties.setRerankEndpoint("http://127.0.0.1:9/v1");
        properties.setRerankModel("test-rerank");
        RerankProvider provider = new RerankProvider(properties);
        assertThat(provider.available()).isTrue();
        assertThat(provider.rerank("查询", List.of())).isNull();
    }
}
