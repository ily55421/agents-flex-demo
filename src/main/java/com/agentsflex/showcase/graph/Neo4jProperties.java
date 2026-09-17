package com.agentsflex.showcase.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内嵌 Neo4j 图数据库配置，映射 application.yml 的 agents-flex.graph.*。
 */
@Component
@ConfigurationProperties(prefix = "agents-flex.graph")
public class Neo4jProperties {

    /** 图数据库存储目录（内嵌模式，随应用启停） */
    private String storePath = "./data/neo4j";

    /** 页面缓存大小（Neo4j pagecache），如 256m / 512m；留空使用默认值 */
    private String pageCacheSize = "";

    public String getStorePath() {
        return storePath;
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    public String getPageCacheSize() {
        return pageCacheSize;
    }

    public void setPageCacheSize(String pageCacheSize) {
        this.pageCacheSize = pageCacheSize;
    }
}
