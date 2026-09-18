package com.agentsflex.showcase.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 检索结果重排：调用 OpenAI 兼容的 /rerank 接口对候选片段重排序。
 *
 * <p>对齐 WeKnora 的 Reranker 抽象：接口保持最小（Rerank(query, documents) → 分数），
 * 供应商差异由 endpoint 与模型名承担；Jina / 硅基流动等 OpenAI 兼容服务的响应均为
 * {@code {results:[{index, relevance_score}]}} 结构。未配置或调用失败时返回 null，
 * 由调用方保持向量/BM25 原始顺序——重排是增强而非必需依赖，任何故障不得阻断检索。</p>
 */
@Component
public class RerankProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * @param properties rerank 配置（开关、地址、密钥、模型、召回上限）
     */
    public RerankProvider(KnowledgeProperties properties) {
        this.properties = properties;
    }

    /** @return 是否已配置可用的重排服务 */
    public boolean available() {
        return properties.isRerankEnabled()
                && notBlank(properties.getRerankEndpoint())
                && notBlank(properties.getRerankModel());
    }

    /**
     * 对候选片段重排。
     *
     * @param query     查询文本
     * @param documents 候选片段正文（与返回分数按位置一一对应）
     * @return 每个候选的相关性分数；未启用或失败返回 null
     */
    public float[] rerank(String query, List<String> documents) {
        if (!available() || documents == null || documents.isEmpty()) return null;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", properties.getRerankModel().trim());
            body.put("query", query == null ? "" : query);
            ArrayNode docs = body.putArray("documents");
            for (String document : documents) docs.add(document == null ? "" : document);
            body.put("top_n", documents.size());

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (notBlank(properties.getRerankApiKey())) {
                builder.header("Authorization", "Bearer " + properties.getRerankApiKey().trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode results = MAPPER.readTree(response.body()).path("results");
            if (!results.isArray() || results.isEmpty()) return null;
            float[] scores = new float[documents.size()];
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                // 兼容 relevance_score / score 两种字段名（对齐 WeKnora RankResult）
                JsonNode score = result.has("relevance_score")
                        ? result.get("relevance_score") : result.get("score");
                if (index >= 0 && index < scores.length && score != null && score.isNumber()) {
                    scores[index] = score.floatValue();
                }
            }
            return scores;
        } catch (Exception error) {
            // 重排失败按未启用处理：保持混合检索原始顺序
            return null;
        }
    }

    /** 服务地址归一：允许填根地址（…/v1）或完整路由（…/v1/rerank）。 */
    private String normalizedEndpoint() {
        String endpoint = properties.getRerankEndpoint().trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        return endpoint.endsWith("/rerank") ? endpoint : endpoint + "/rerank";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
