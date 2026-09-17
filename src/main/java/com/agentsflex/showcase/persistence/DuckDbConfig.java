package com.agentsflex.showcase.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DuckDB 嵌入式数据库连接配置。
 *
 * <p>DuckDB 与 SQLite 一样是单文件嵌入式数据库，无需独立服务进程；默认连接
 * {@code jdbc:duckdb:./data/showcase.duckdb}，父目录不存在时自动创建。测试与需要隔离的
 * 场景可以通过环境变量 {@code DUCKDB_URL} 覆盖为内存或临时文件连接。</p>
 *
 * <p>连接通过 HikariCP 池化管理：DuckDB 0.9.x 并发打开同一文件会因文件锁竞态失败，
 * 池内首个连接打开实例后其余连接复用同一数据库，读取也保持小池并发。</p>
 */
@Configuration
public class DuckDbConfig {

    private static final String JDBC_PREFIX = "jdbc:duckdb:";

    @Value("${agents-flex.persistence.duckdb-url:jdbc:duckdb:./data/showcase.duckdb}")
    private String duckDbUrl;

    /**
     * 创建带连接池的 DuckDB 数据源。文件型连接会先确保父目录存在；
     * 池大小限制为 2，兼顾演示并发与文件锁安全。
     *
     * @return 可注入 JdbcTemplate 的 DataSource
     */
    @Bean(destroyMethod = "close")
    public DataSource showcaseDataSource() throws Exception {
        String filePart = duckDbUrl.startsWith(JDBC_PREFIX)
                ? duckDbUrl.substring(JDBC_PREFIX.length()) : duckDbUrl;
        if (filePart != null && !filePart.trim().isEmpty()
                && !filePart.trim().startsWith(":memory:")) {
            Path file = Path.of(filePart.trim()).toAbsolutePath();
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setJdbcUrl(duckDbUrl);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setPoolName("duckdb-showcase");
        return new HikariDataSource(config);
    }

    /**
     * 基于 DuckDB 数据源的 JdbcTemplate，供持久化 Store 执行建表与读写。
     *
     * @param showcaseDataSource 上面创建的 DuckDB 数据源
     * @return 共享的 JdbcTemplate
     */
    @Bean
    public JdbcTemplate showcaseJdbcTemplate(DataSource showcaseDataSource) {
        return new JdbcTemplate(showcaseDataSource);
    }
}
