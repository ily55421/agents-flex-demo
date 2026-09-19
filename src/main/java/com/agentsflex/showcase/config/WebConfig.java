package com.agentsflex.showcase.config;

import com.agentsflex.showcase.knowledge.KnowledgeApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将跨域 API 访问限制在本地 Vite 开发服务器，并让后端托管前端构建产物。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final org.springframework.beans.factory.ObjectProvider<KnowledgeApiKeyInterceptor> knowledgeApiKeyInterceptor;

    /**
     * @param knowledgeApiKeyInterceptor 知识库 API Key 拦截器（轻量多租户能力位）；
     *                                   用 ObjectProvider 以兼容切片测试上下文
     */
    public WebConfig(org.springframework.beans.factory.ObjectProvider<KnowledgeApiKeyInterceptor> knowledgeApiKeyInterceptor) {
        this.knowledgeApiKeyInterceptor = knowledgeApiKeyInterceptor;
    }

    /**
     * 注册知识库 API Key 拦截器：带 Bearer Key 按 RETRIEVE/MANAGE 能力位放行，
     * 无 Key 视为本机 Demo 模式全放行（保持现有使用体验）。拦截器不存在时跳过注册。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        KnowledgeApiKeyInterceptor interceptor = knowledgeApiKeyInterceptor.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor).addPathPatterns("/api/knowledge/**");
        }
    }

    /**
     * 注册 /api 下的 CORS 规则，仅开放 Demo 使用的 GET、POST、PUT、DELETE 和预检请求。
     * 端口覆盖 Vite 默认端口（当前 15173，历史 5173）及其被占用时自动递增的相邻端口。
     *
     * @param registry Spring MVC 的全局跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:15173", "http://127.0.0.1:15173",
                        "http://localhost:15174", "http://127.0.0.1:15174",
                        "http://localhost:15175", "http://127.0.0.1:15175",
                        "http://localhost:5173", "http://127.0.0.1:5173",
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
