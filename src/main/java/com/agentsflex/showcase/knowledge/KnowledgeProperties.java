package com.agentsflex.showcase.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 知识库配置，映射 application.yml 的 agents-flex.knowledge.*。
 * embedding 三项提供服务端默认值；UI 提交的向量档案优先级更高。
 */
@Component
@ConfigurationProperties(prefix = "agents-flex.knowledge")
public class KnowledgeProperties {

    private String mmapPath = "./data/knowledge-mem";
    /** embedding 设置持久化文件：最近应用的向量配置与用户自建预设。 */
    private String settingsPath = "./data/knowledge-settings.json";
    private String searchMode = "HYBRID";
    private int topK = 5;
    private long maxUploadBytes = 2 * 1024 * 1024L;
    /** 单文件解析上限（字节）：与上传上限分离，供文档解析器保护大文件场景。 */
    private long maxParseBytes = 50L * 1024 * 1024;
    /** 切片窗口字符数；对齐 WeKnora chunker 默认 512。 */
    private int chunkSize = 512;
    /** 相邻切片重叠字符数；对齐 WeKnora chunker 默认 80。 */
    private int chunkOverlap = 80;
    /** 异步灌入并发线程数；默认 2，避免多任务并行向量化的限流冲突。 */
    private int ingestConcurrency = 2;
    /** 异步灌入排队上限；队列满时提交立即失败为 FAILED 任务。 */
    private int ingestQueueCapacity = 100;
    private String embeddingEndpoint = "";
    private String embeddingApiKey = "";
    private String embeddingModel = "bge-m3";

    public String getMmapPath() {
        return mmapPath;
    }

    public void setMmapPath(String mmapPath) {
        this.mmapPath = mmapPath;
    }

    public String getSettingsPath() {
        return settingsPath;
    }

    public void setSettingsPath(String settingsPath) {
        this.settingsPath = settingsPath;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public long getMaxParseBytes() {
        return maxParseBytes;
    }

    public void setMaxParseBytes(long maxParseBytes) {
        this.maxParseBytes = maxParseBytes;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getIngestConcurrency() {
        return ingestConcurrency;
    }

    public void setIngestConcurrency(int ingestConcurrency) {
        this.ingestConcurrency = ingestConcurrency;
    }

    public int getIngestQueueCapacity() {
        return ingestQueueCapacity;
    }

    public void setIngestQueueCapacity(int ingestQueueCapacity) {
        this.ingestQueueCapacity = ingestQueueCapacity;
    }

    public String getEmbeddingEndpoint() {
        return embeddingEndpoint;
    }

    public void setEmbeddingEndpoint(String embeddingEndpoint) {
        this.embeddingEndpoint = embeddingEndpoint;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
