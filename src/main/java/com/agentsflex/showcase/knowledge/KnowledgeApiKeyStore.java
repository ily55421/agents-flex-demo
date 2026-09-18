package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库 API Key 的 DuckDB 存储（轻量多租户，对齐 WeKnora tenant_api_keys 的能力位思路）。
 *
 * <p>能力位两种：RETRIEVE（只读：状态/清单/检索）与 MANAGE（读写全部）。仅保存
 * SHA-256 哈希，明文只在创建时返回一次；无 Key 请求视为本机 Demo 模式全放行。</p>
 */
@Component
public class KnowledgeApiKeyStore {

    /** 能力位：只读检索 / 全部读写。 */
    public static final String CAPABILITY_RETRIEVE = "RETRIEVE";
    public static final String CAPABILITY_MANAGE = "MANAGE";

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与文档元数据共享数据源）
     */
    public KnowledgeApiKeyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等建表。 */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_api_key ("
                + " key_id VARCHAR PRIMARY KEY,"
                + " key_prefix VARCHAR NOT NULL,"
                + " key_hash VARCHAR NOT NULL,"
                + " capability VARCHAR NOT NULL,"
                + " created_at BIGINT NOT NULL)");
    }

    /**
     * 创建 API Key。
     *
     * @param capability RETRIEVE / MANAGE
     * @return 含明文 key 的视图（仅此一次返回明文）
     */
    public Map<String, Object> create(String capability) {
        ensureSchema();
        String normalized = CAPABILITY_MANAGE.equals(capability) ? CAPABILITY_MANAGE : CAPABILITY_RETRIEVE;
        String keyId = "key-" + UUID.randomUUID();
        String plaintext = "kfk-" + UUID.randomUUID().toString().replace("-", "");
        String hash = sha256(plaintext);
        String prefix = plaintext.substring(0, 8);
        jdbc.update("INSERT INTO knowledge_api_key(key_id, key_prefix, key_hash, capability, created_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                keyId, prefix, hash, normalized, System.currentTimeMillis());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("keyId", keyId);
        view.put("key", plaintext);
        view.put("keyPrefix", prefix);
        view.put("capability", normalized);
        return view;
    }

    /**
     * 校验明文 Key。
     *
     * @param plaintext 请求携带的明文 Key
     * @return 能力位；Key 不存在返回 {@code null}
     */
    public String capabilityOf(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        List<String> rows = jdbc.query("SELECT capability FROM knowledge_api_key WHERE key_hash = ?",
                (rs, rowNum) -> rs.getString(1), sha256(plaintext));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** @return 全部 Key（脱敏：仅前缀与能力位） */
    public List<Map<String, Object>> list() {
        ensureSchema();
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT key_id, key_prefix, capability, created_at FROM knowledge_api_key"
                        + " ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("keyId", rs.getString("key_id"));
                    view.put("keyPrefix", rs.getString("key_prefix"));
                    view.put("capability", rs.getString("capability"));
                    view.put("createdAt", rs.getLong("created_at"));
                    return view;
                });
        return rows == null ? Collections.emptyList() : rows;
    }

    /**
     * @param keyId Key ID
     * @return 是否删除
     */
    public boolean delete(String keyId) {
        ensureSchema();
        return jdbc.update("DELETE FROM knowledge_api_key WHERE key_id = ?", keyId) > 0;
    }

    /** SHA-256 十六进制摘要。 */
    private static String sha256(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }
}
