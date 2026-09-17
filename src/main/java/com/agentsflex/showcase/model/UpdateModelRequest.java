package com.agentsflex.showcase.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 模型连接配置更新请求。
 *
 * <p>只包含聊天模型的连接与采样字段，用于页面在“应用配置”时立即把连接参数同步到后端内存，
 * 而不必先创建 Agent。非空字段覆盖当前配置，留空字段沿用服务端默认值；
 * apiKey 只写入后端内存，任何状态接口都不会回显。</p>
 */
public class UpdateModelRequest {

    @Size(max = 80)
    private String modelProvider;

    @Size(max = 2048)
    private String modelEndpoint;

    @Size(max = 512)
    private String modelRequestPath;

    @Size(max = 4096)
    private String modelApiKey;

    @Size(max = 200)
    private String modelName;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Float modelTemperature;

    private Boolean modelThinkingEnabled;

    @Size(max = 80)
    private String modelThinkingProtocol;

    @Size(max = 200)
    private String modelSeed;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Float modelTopP;

    @Min(1)
    @Max(1000000)
    private Integer modelTopK;

    @Min(1)
    @Max(1000000)
    private Integer modelMaxTokens;

    @Size(max = 20)
    private List<String> modelStop;

    private Boolean modelIncludeUsage;

    @Size(max = 32)
    private String modelResponseFormat;

    private Boolean modelRetryEnabled;

    @Min(0)
    @Max(20)
    private Integer modelRetryCount;

    @Min(0)
    @Max(300000)
    private Integer modelRetryInitialDelayMillis;

    @Min(0)
    @Max(1000000)
    private Long maxInputTokens;

    @Min(0)
    @Max(1000000)
    private Long maxOutputTokens;

    @Min(0)
    @Max(1000000)
    private Long maxTotalTokens;

    @Min(0)
    @Max(10000000)
    private Long maxAttachedTokens;

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String value) {
        this.modelProvider = value;
    }

    public String getModelEndpoint() {
        return modelEndpoint;
    }

    public void setModelEndpoint(String value) {
        this.modelEndpoint = value;
    }

    public String getModelRequestPath() {
        return modelRequestPath;
    }

    public void setModelRequestPath(String value) {
        this.modelRequestPath = value;
    }

    public String getModelApiKey() {
        return modelApiKey;
    }

    public void setModelApiKey(String value) {
        this.modelApiKey = value;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String value) {
        this.modelName = value;
    }

    public Float getModelTemperature() {
        return modelTemperature;
    }

    public void setModelTemperature(Float value) {
        this.modelTemperature = value;
    }

    public Boolean getModelThinkingEnabled() {
        return modelThinkingEnabled;
    }

    public void setModelThinkingEnabled(Boolean value) {
        this.modelThinkingEnabled = value;
    }

    public String getModelThinkingProtocol() {
        return modelThinkingProtocol;
    }

    public void setModelThinkingProtocol(String value) {
        this.modelThinkingProtocol = value;
    }

    public String getModelSeed() {
        return modelSeed;
    }

    public void setModelSeed(String value) {
        this.modelSeed = value;
    }

    public Float getModelTopP() {
        return modelTopP;
    }

    public void setModelTopP(Float value) {
        this.modelTopP = value;
    }

    public Integer getModelTopK() {
        return modelTopK;
    }

    public void setModelTopK(Integer value) {
        this.modelTopK = value;
    }

    public Integer getModelMaxTokens() {
        return modelMaxTokens;
    }

    public void setModelMaxTokens(Integer value) {
        this.modelMaxTokens = value;
    }

    public List<String> getModelStop() {
        return modelStop;
    }

    public void setModelStop(List<String> value) {
        this.modelStop = value;
    }

    public Boolean getModelIncludeUsage() {
        return modelIncludeUsage;
    }

    public void setModelIncludeUsage(Boolean value) {
        this.modelIncludeUsage = value;
    }

    public String getModelResponseFormat() {
        return modelResponseFormat;
    }

    public void setModelResponseFormat(String value) {
        this.modelResponseFormat = value;
    }

    public Boolean getModelRetryEnabled() {
        return modelRetryEnabled;
    }

    public void setModelRetryEnabled(Boolean value) {
        this.modelRetryEnabled = value;
    }

    public Integer getModelRetryCount() {
        return modelRetryCount;
    }

    public void setModelRetryCount(Integer value) {
        this.modelRetryCount = value;
    }

    public Integer getModelRetryInitialDelayMillis() {
        return modelRetryInitialDelayMillis;
    }

    public void setModelRetryInitialDelayMillis(Integer value) {
        this.modelRetryInitialDelayMillis = value;
    }

    public Long getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(Long value) {
        this.maxInputTokens = value;
    }

    public Long getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Long value) {
        this.maxOutputTokens = value;
    }

    public Long getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(Long value) {
        this.maxTotalTokens = value;
    }

    public Long getMaxAttachedTokens() {
        return maxAttachedTokens;
    }

    public void setMaxAttachedTokens(Long value) {
        this.maxAttachedTokens = value;
    }
}
