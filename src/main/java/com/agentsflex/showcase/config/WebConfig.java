package com.agentsflex.showcase.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将跨域 API 访问限制在本地 Vite 开发服务器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册 /api 下的 CORS 规则，仅开放 Demo 使用的 GET、POST、DELETE 和预检请求。
     * 端口覆盖 Vite 默认的 5173 及其被占用时自动递增的 5174/5175。
     *
     * @param registry Spring MVC 的全局跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173",
                        "http://localhost:5174", "http://127.0.0.1:5174",
                        "http://localhost:5175", "http://127.0.0.1:5175")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
