package com.agentsflex.showcase.knowledge;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 知识库 API Key 拦截器：带 Bearer Key 的请求按能力位放行，无 Key 视为本机 Demo 全放行。
 *
 * <p>降级适配 WeKnora 的 RBAC：不引入用户体系，仅区分 RETRIEVE（只读：状态/清单/检索）
 * 与 MANAGE（全部读写）两档能力位。管理端点（/api-keys 自身）恒要求本机直连（无 Key
 * 头）才能访问，防止只读 Key 管理凭据。</p>
 */
@Component
public class KnowledgeApiKeyInterceptor implements HandlerInterceptor {

    /** 只读能力位允许的路径与方法组合。 */
    private static final List<String> RETRIEVE_PATHS = List.of(
            "/status", "/documents", "/search", "/tasks", "/parsers", "/bases");

    private final KnowledgeApiKeyStore apiKeyStore;

    /**
     * @param apiKeyStore API Key 存储；@Lazy 延迟解析，兼容 WebMvcTest 切片上下文
     *                    （该上下文只扫 HandlerInterceptor，不扫 JdbcTemplate）
     */
    public KnowledgeApiKeyInterceptor(
            @org.springframework.context.annotation.Lazy KnowledgeApiKeyStore apiKeyStore) {
        this.apiKeyStore = apiKeyStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            // 本机 Demo 模式：无 Key 全放行；但 Key 管理端点仅限无 Key 头的本机访问
            return true;
        }
        String plaintext = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim() : authorization.trim();
        String capability = apiKeyStore.capabilityOf(plaintext);
        if (capability == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "无效的 API Key");
            return false;
        }
        // Key 管理端点不接受 Key 访问（含 MANAGE），避免凭据被远程枚举
        if (request.getRequestURI().contains("/api-keys")) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "API Key 管理端点不接受 Key 访问");
            return false;
        }
        if (KnowledgeApiKeyStore.CAPABILITY_MANAGE.equals(capability)) {
            return true;
        }
        // RETRIEVE：仅放行 GET 与检索类 POST（/search）
        String method = request.getMethod();
        String path = request.getRequestURI().replaceFirst("^/api/knowledge", "");
        boolean readPath = RETRIEVE_PATHS.stream().anyMatch(path::startsWith);
        boolean allowed = "GET".equals(method) && readPath
                || ("POST".equals(method) && path.startsWith("/search"));
        if (!allowed) {
            response.sendError(HttpStatus.FORBIDDEN.value(),
                    "当前 API Key 为只读能力（RETRIEVE），无权执行该操作");
        }
        return allowed;
    }
}
