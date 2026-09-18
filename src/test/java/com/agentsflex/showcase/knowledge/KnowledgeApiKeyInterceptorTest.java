package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 轻量多租户验证：Key 创建/校验、只读能力位放行检索并拒绝写操作、
 * 无 Key 本机 Demo 模式全放行。
 */
class KnowledgeApiKeyInterceptorTest {

    private Path tempDir;
    private KnowledgeApiKeyStore store;
    private KnowledgeApiKeyInterceptor interceptor;

    /** 前置：独立临时 DuckDB + 拦截器（手动 ensureSchema，模拟非 Spring 环境）。 */
    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("apikey-test-");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl("jdbc:duckdb:" + tempDir.resolve("meta.duckdb")
                .toString().replace('\\', '/'));
        store = new KnowledgeApiKeyStore(new JdbcTemplate(dataSource));
        store.ensureSchema();
        interceptor = new KnowledgeApiKeyInterceptor(store);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDir.resolve("meta.duckdb"));
    }

    private MockHttpServletRequest request(String method, String uri, String bearer) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (bearer != null) request.addHeader("Authorization", "Bearer " + bearer);
        return request;
    }

    /**
     * RETRIEVE 能力位：放行 GET 与检索 POST，拒绝写操作。
     */
    @Test
    void retrieveCapabilityAllowsReadsAndRejectsWrites() throws Exception {
        Map<String, Object> created = store.create("RETRIEVE");
        String key = String.valueOf(created.get("key"));

        assertThat(interceptor.preHandle(request("GET", "/api/knowledge/status", key),
                new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/knowledge/search", key),
                new MockHttpServletResponse(), null)).isTrue();

        // 写操作被 403 拒绝；每次请求用全新 response，避免 committed 状态残留
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("POST", "/api/knowledge/documents", key),
                writeResponse, null)).isFalse();
        assertThat(writeResponse.getStatus()).isEqualTo(403);

        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("DELETE", "/api/knowledge/documents/kb-x", key),
                deleteResponse, null)).isFalse();
        assertThat(deleteResponse.getStatus()).isEqualTo(403);
    }

    /**
     * 无 Key 请求本机 Demo 模式全放行；无效 Key 返回 401。
     */
    @Test
    void noKeyAllowsAllAndInvalidKeyRejected() throws Exception {
        assertThat(interceptor.preHandle(request("POST", "/api/knowledge/documents", null),
                new MockHttpServletResponse(), null)).isTrue();
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("POST", "/api/knowledge/documents", "kfk-invalid"),
                invalidResponse, null)).isFalse();
        assertThat(invalidResponse.getStatus()).isEqualTo(401);
    }

    /**
     * MANAGE 能力位全放行；Key 管理端点即使 MANAGE Key 也拒绝。
     */
    @Test
    void manageCapabilityAllowsAllButNotKeyManagement() throws Exception {
        Map<String, Object> created = store.create("MANAGE");
        String key = String.valueOf(created.get("key"));
        assertThat(interceptor.preHandle(request("DELETE", "/api/knowledge/documents/kb-x", key),
                new MockHttpServletResponse(), null)).isTrue();
        MockHttpServletResponse keyMgmtResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("GET", "/api/knowledge/api-keys", key),
                keyMgmtResponse, null)).isFalse();
        assertThat(keyMgmtResponse.getStatus()).isEqualTo(403);
    }

    /**
     * 存储只存哈希：明文不可从存储反查；校验仅通过 capabilityOf。
     */
    @Test
    void storesHashNotPlaintext() {
        Map<String, Object> created = store.create("RETRIEVE");
        String plaintext = String.valueOf(created.get("key"));
        assertThat(store.list().get(0)).doesNotContainKey("key");
        assertThat(store.capabilityOf(plaintext)).isEqualTo("RETRIEVE");
        assertThat(store.capabilityOf("kfk-wrong")).isNull();
    }
}
