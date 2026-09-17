package com.agentsflex.showcase.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将跨域 API 访问限制在本地 Vite 开发服务器，并让后端托管前端构建产物。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册 /api 下的 CORS 规则，仅开放 Demo 使用的 GET、POST、PUT、DELETE 和预检请求。
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
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * 单 jar 部署时后端直接托管前端构建产物：
     * <ul>
     *   <li>优先读取 jar 工作目录的 {@code ./frontend/dist}（服务器上更新前端无需重新打包）；</li>
     *   <li>回退到 jar 内 {@code classpath:/static}（打包脚本把 frontend/dist 拷入该目录）。</li>
     * </ul>
     * 前端路由用 URL 查询参数（?view=...），单入口 index.html 即可，无需 SPA 回退规则。
     *
     * @param registry 静态资源注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:./frontend/dist/", "classpath:/static/");
    }
}
