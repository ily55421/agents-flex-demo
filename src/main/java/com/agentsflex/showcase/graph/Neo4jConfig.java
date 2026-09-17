package com.agentsflex.showcase.graph;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 内嵌 Neo4j 图数据库的 Bean 装配。
 *
 * <p>数据库以嵌入式模式随应用启停：存储目录默认 ./data/neo4j，JVM 关闭时由
 * {@link DatabaseManagementService#shutdown()} 落盘并释放文件句柄（Windows 上
 * 不显式 shutdown 会锁定目录，导致下次启动失败）。</p>
 */
@Configuration
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    /**
     * 创建图数据库管理服务（生命周期与应用一致）。
     *
     * @param properties 存储路径与页面缓存配置
     * @return 管理服务；Spring 关闭时调用其 shutdown 方法
     */
    @Bean(destroyMethod = "shutdown")
    public DatabaseManagementService databaseManagementService(Neo4jProperties properties) throws IOException {
        Path storePath = Path.of(properties.getStorePath()).toAbsolutePath().normalize();
        Files.createDirectories(storePath);
        DatabaseManagementServiceBuilder builder = new DatabaseManagementServiceBuilder(storePath);
        if (properties.getPageCacheSize() != null && !properties.getPageCacheSize().isBlank()) {
            builder.setConfig(GraphDatabaseSettings.pagecache_memory, parseByteAmount(properties.getPageCacheSize()));
        }
        DatabaseManagementService service = builder.build();
        log.info("内嵌 Neo4j 已启动，存储目录: {}", storePath);
        return service;
    }

    /**
     * 暴露默认数据库（neo4j）的图数据库服务。
     *
     * @param managementService 管理服务
     * @return 默认数据库实例
     */
    @Bean
    public GraphDatabaseService graphDatabase(DatabaseManagementService managementService) {
        return managementService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    }

    /**
     * 解析 “256m / 512k / 1073741824” 形式的容量字符串为字节数。
     *
     * @param value 容量字符串
     * @return 字节数
     * @throws IllegalArgumentException 格式非法
     */
    private static long parseByteAmount(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d+)\\s*(k|m|g)?b?$").matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("非法的 pagecache 容量配置: " + value + "（示例: 256m）");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2) == null ? "" : matcher.group(2)) {
            case "k" -> amount * 1024L;
            case "m" -> amount * 1024L * 1024L;
            case "g" -> amount * 1024L * 1024L * 1024L;
            default -> amount;
        };
    }
}
