package com.agentsflex.showcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动自包含的 Agents-Flex Runtime Showcase API。
 */
@SpringBootApplication
public class ShowcaseApplication {

    /**
     * 创建 Spring 应用上下文并启动内嵌 Tomcat。
     *
     * @param args JVM 启动参数，Spring Boot 会解析其中的配置覆盖项
     */
    public static void main(String[] args) {
        SpringApplication.run(ShowcaseApplication.class, args);
    }
}
