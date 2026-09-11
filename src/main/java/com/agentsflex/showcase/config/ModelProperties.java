package com.agentsflex.showcase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 映射 application.yml 中的 OpenAI-compatible 模型配置。
 * apiKey 只在后端内存中使用，任何对外状态接口都不会返回其原文。
 */
@Component
@ConfigurationProperties(prefix = "agents-flex.model")
public class ModelProperties {

    private String provider = "openai";
    private String endpoint = "https://api.openai.com";
    private String requestPath = "/v1/chat/completions";
    private String apiKey;
    private String model = "gpt-4o-mini";
    private float temperature = 0.2f;
    private boolean thinkingEnabled;
    private String thinkingProtocol = "none";
    private String seed;
    private Float topP;
    private Integer topK;
    private Integer maxTokens;
    private List<String> stop;
    private Boolean includeUsage = true;
    private String responseFormat = "NONE";
    private boolean retryEnabled = true;
    private int retryCount = 2;
    private int retryInitialDelayMillis = 600;

    /**
     * @return 模型服务商标识，用于 OTel provider 属性和界面状态
     */
    public String getProvider() {
        return provider;
    }

    /**
     * @param provider 模型服务商标识，例如 openai、deepseek 或 qwen
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * @return OpenAI-compatible 服务根地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @param endpoint 不包含 chat completions 路径的服务根地址
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * @return Chat Completions 请求路径
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * @param requestPath 服务商兼容的 Chat Completions 路径
     */
    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    /**
     * @return 仅供后端创建 ChatModel 使用的 API Key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * @param apiKey 模型服务密钥，推荐由 LLM_API_KEY 环境变量注入
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @return 实际请求的模型名称
     */
    public String getModel() {
        return model;
    }

    /**
     * @param model 服务商支持的模型 ID
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * @return 请求级生成温度
     */
    public float getTemperature() {
        return temperature;
    }

    /**
     * @param temperature 建议在工具调用场景使用 0.1 到 0.3
     */
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    /**
     * @return 是否请求模型启用显式思考模式
     */
    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    /**
     * @param thinkingEnabled 模型支持时是否启用思考模式
     */
    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    /**
     * @return 推理协议名称，例如 deepseek、qwen 或 none
     */
    public String getThinkingProtocol() {
        return thinkingProtocol;
    }

    /**
     * @param thinkingProtocol Agents-Flex 用于解析推理内容的协议
     */
    public void setThinkingProtocol(String thinkingProtocol) {
        this.thinkingProtocol = thinkingProtocol;
    }

    /**
     * @return 用于复现模型采样结果的可选随机种子
     */
    public String getSeed() {
        return seed;
    }

    /**
     * @param seed 供应商支持的随机种子文本；为空时不发送该参数
     */
    public void setSeed(String seed) {
        this.seed = seed;
    }

    /**
     * @return 核采样累计概率上限；为空时沿用模型默认值
     */
    public Float getTopP() {
        return topP;
    }

    /**
     * @param topP 0 到 1 的核采样累计概率
     */
    public void setTopP(Float topP) {
        this.topP = topP;
    }

    /**
     * @return 候选 Token 数量上限；为空时不发送该参数
     */
    public Integer getTopK() {
        return topK;
    }

    /**
     * @param topK 大于 0 的候选 Token 数量上限
     */
    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    /**
     * @return 单次模型响应允许生成的最大 Token 数
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * @param maxTokens 大于 0 的单次输出 Token 上限
     */
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * @return 命中任一值时停止生成的序列列表
     */
    public List<String> getStop() {
        return stop;
    }

    /**
     * @param stop 发送给模型供应商的停止序列；为空表示不限制
     */
    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    /**
     * @return 流式响应是否要求供应商附带 Token Usage
     */
    public Boolean getIncludeUsage() {
        return includeUsage;
    }

    /**
     * @param includeUsage 是否在流式请求中开启 Usage 返回
     */
    public void setIncludeUsage(Boolean includeUsage) {
        this.includeUsage = includeUsage;
    }

    /**
     * @return NONE 或 JSON_OBJECT 响应格式策略
     */
    public String getResponseFormat() {
        return responseFormat;
    }

    /**
     * @param responseFormat 模型默认格式或 JSON Object 强制格式
     */
    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    /**
     * @return 是否启用模型 HTTP 请求内部重试
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    /**
     * @param retryEnabled 是否启用 OpenAI-compatible 客户端请求重试
     */
    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    /**
     * @return 单次模型调用内部最多重试次数
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * @param retryCount 单次模型调用内部最多重试次数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * @return 模型请求首次重试前等待毫秒数
     */
    public int getRetryInitialDelayMillis() {
        return retryInitialDelayMillis;
    }

    /**
     * @param retryInitialDelayMillis 模型请求首次重试前等待毫秒数
     */
    public void setRetryInitialDelayMillis(int retryInitialDelayMillis) {
        this.retryInitialDelayMillis = retryInitialDelayMillis;
    }

    /**
     * 判断端点是否指向本机或内网服务（Ollama、vLLM、Xinference、自建网关等）。
     * 这类 OpenAI 兼容服务通常不校验 Authorization，因此不要求填写 API Key。
     *
     * @return 端点主机为 localhost / ::1 / 回环或私有网段时返回 true
     */
    public boolean isLocalEndpoint() {
        if (endpoint == null || endpoint.trim().isEmpty()) return false;
        String value = endpoint.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("localhost") || value.contains("://localhost")
                || value.contains("://127.") || value.contains("://[::1]")
                || value.contains("://0.0.0.0") || value.contains("host.docker.internal")) {
            return true;
        }
        // 常见内网段：192.168.x.x、10.x.x.x、172.16~31.x.x
        return value.contains("://192.168.") || value.contains("://10.")
                || value.matches(".*://172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    /**
     * 判断是否已经提供可用密钥。空值和示例占位值都视为未配置；
     * 本机或内网端点属于免鉴权部署，视为已配置。
     *
     * @return 可以创建真实模型请求时返回 true
     */
    public boolean isConfigured() {
        if (isLocalEndpoint()) return true;
        return apiKey != null && !apiKey.trim().isEmpty()
                && !"your-api-key".equalsIgnoreCase(apiKey.trim());
    }

    /**
     * 生成可安全返回前端的模型状态，刻意排除 apiKey。
     *
     * @return provider、model、endpoint、temperature 和配置状态
     */
    public Map<String, Object> publicView() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("configured", isConfigured());
        value.put("provider", provider);
        value.put("model", model);
        value.put("endpoint", endpoint);
        value.put("temperature", temperature);
        value.put("thinkingEnabled", thinkingEnabled);
        value.put("thinkingProtocol", thinkingProtocol);
        value.put("seed", seed);
        value.put("topP", topP);
        value.put("topK", topK);
        value.put("maxTokens", maxTokens);
        value.put("stop", stop);
        value.put("includeUsage", includeUsage);
        value.put("responseFormat", responseFormat);
        value.put("retryEnabled", retryEnabled);
        value.put("retryCount", retryCount);
        value.put("retryInitialDelayMillis", retryInitialDelayMillis);
        return value;
    }
}
