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
    private String searchMode = "HYBRID";
    private int topK = 5;
    private long maxUploadBytes = 2 * 1024 * 1024L;
    private String embeddingEndpoint = "";
    private String embeddingApiKey = "";
    private String embeddingModel = "bge-m3";

    public String getMmapPath() {
        return mmapPath;
    }

    public void setMmapPath(String mmapPath) {
        this.mmapPath = mmapPath;
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
