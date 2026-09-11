package com.agentsflex.showcase.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DuckDB 嵌入式数据库连接配置。
 *
 * <p>DuckDB 与 SQLite 一样是单文件嵌入式数据库，无需独立服务进程；默认连接
 * {@code ./data/showcase.duckdb}，父目录不存在时自动创建。测试与需要隔离的
 * 场景可以通过环境变量 {@code DUCKDB_URL} 覆盖为内存或临时文件连接。</p>
 */
@Configuration
public class DuckDbConfig {

    private static final String JDBC_PREFIX = "jdbc:duckdb:";

    @Value("${agents-flex.persistence.duckdb-url:jdbc:duckdb:./data/showcase.duckdb}")
    private String duckDbUrl;

    /**
     * 创建 DuckDB JDBC 数据源。DuckDB 驱动通过 JDBC 4 自动注册，这里显式声明驱动类名
     * 以兼容旧式 DriverManager 查找路径；文件型连接会先确保父目录存在。
     * DriverManagerDataSource 按调用创建短连接、不持有连接池，因此无需销毁方法。
     *
     * @return 可注入 JdbcTemplate 的 DataSource
     */
    @Bean
    public DataSource showcaseDataSource() throws Exception {
        String filePart = duckDbUrl.startsWith(JDBC_PREFIX)
                ? duckDbUrl.substring(JDBC_PREFIX.length()) : duckDbUrl;
        if (filePart != null && !filePart.trim().isEmpty()
                && !filePart.trim().startsWith(":memory:")) {
            Path file = Paths.get(filePart.trim()).toAbsolutePath();
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl(duckDbUrl);
        return dataSource;
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
