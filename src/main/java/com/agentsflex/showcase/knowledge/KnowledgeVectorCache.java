package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * embedding 向量的 DuckDB 内容寻址缓存。
 *
 * <p>键为 (embedding 签名, 文本 SHA-256)。应用每次启动都会丢弃 mmap 索引并从 DuckDB
 * 全量重建，重建期间 RogueMemory 的每次 add() 都会调用 embedding 服务；本缓存让
 * 重启后的重建退化为本地向量读取，不再产生网络调用。检索时的查询向量也途经同一
 * 缓存，重复问题的检索同样免网络。</p>
 */
@Component
public class KnowledgeVectorCache {

    /** 单签名下的缓存条数上限；超出后按写入时间淘汰最旧一批，防止无限增长。 */
    private static final int MAX_ROWS_PER_SIGNATURE = 20_000;
    private static final int TRIM_BATCH = 5_000;

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与文档元数据共享数据源）
     */
    public KnowledgeVectorCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等建表。旧缓存随代码升级天然失效：键里含签名，文本变化即哈希变化。
     */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_vector_cache ("
                + " signature VARCHAR NOT NULL,"
                + " text_hash VARCHAR NOT NULL,"
                + " dim INTEGER NOT NULL,"
                + " vector BLOB NOT NULL,"
                + " created_at BIGINT NOT NULL,"
                + " PRIMARY KEY (signature, text_hash))");
    }

    /**
     * 读取缓存向量；未命中返回 {@code null}。
     *
     * <p>DuckDB JDBC 的 BLOB 列不支持 {@code getBytes()}，必须经 {@code getBlob()}
     * 读取，否则每次取向量都会抛异常导致整篇文档重建失败。</p>
     *
     * @param signature embedding 签名（endpoint|model）
     * @param textHash  文本 SHA-256 十六进制串
     * @return 向量；字节序与 {@link #saveVector} 一致（大端）
     */
    public float[] loadVector(String signature, String textHash) {
        List<java.sql.Blob> rows = jdbc.query(
                "SELECT vector FROM knowledge_vector_cache WHERE signature = ? AND text_hash = ?",
                (rs, rowNum) -> rs.getBlob("vector"), signature, textHash);
        if (rows.isEmpty() || rows.get(0) == null) return null;
        try {
            java.sql.Blob blob = rows.get(0);
            ByteBuffer buffer = ByteBuffer.wrap(blob.getBytes(1, (int) blob.length()));
            float[] vector = new float[buffer.remaining() / Float.BYTES];
            buffer.asFloatBuffer().get(vector);
            return vector;
        } catch (java.sql.SQLException error) {
            // 读取失败按未命中处理，调用方回退真实 embedding。
            return null;
        }
    }

    /** @return 指定签名下已缓存的文本哈希集合（导入去重计数用） */
    public java.util.Set<String> existingTextHashes(String signature) {
        if (signature == null) return java.util.Set.of();
        return new java.util.HashSet<>(jdbc.queryForList(
                "SELECT text_hash FROM knowledge_vector_cache WHERE signature = ?",
                String.class, signature));
    }

    /**
     * @return 全部缓存行（导出快照用）。vector 为原始小端序 float 字节，
     *         由 Jackson 序列化为 Base64 字符串。
     */
    public List<Map<String, Object>> listAllVectors() {
        return jdbc.query("SELECT signature, text_hash, dim, vector, created_at"
                        + " FROM knowledge_vector_cache ORDER BY signature, created_at",
                (rs, rowNum) -> {
                    try {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("signature", rs.getString("signature"));
                        row.put("textHash", rs.getString("text_hash"));
                        row.put("dim", rs.getInt("dim"));
                        java.sql.Blob blob = rs.getBlob("vector");
                        row.put("vector", blob.getBytes(1, (int) blob.length()));
                        row.put("createdAt", rs.getLong("created_at"));
                        return row;
                    } catch (java.sql.SQLException error) {
                        throw new IllegalStateException("读取向量缓存失败", error);
                    }
                });
    }

    /**
     * 写入或覆盖缓存向量；超过单签名容量上限时按写入时间淘汰最旧一批。
     *
     * @param signature embedding 签名
     * @param textHash  文本 SHA-256 十六进制串
     * @param vector    向量
     */
    public synchronized void saveVector(String signature, String textHash, float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.asFloatBuffer().put(vector);
        jdbc.update("INSERT INTO knowledge_vector_cache(signature, text_hash, dim, vector, created_at)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (signature, text_hash)"
                        + " DO UPDATE SET dim = excluded.dim, vector = excluded.vector,"
                        + " created_at = excluded.created_at",
                signature, textHash, vector.length, buffer.array(), System.currentTimeMillis());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_vector_cache WHERE signature = ?",
                Integer.class, signature);
        if (count != null && count > MAX_ROWS_PER_SIGNATURE) {
            jdbc.update("DELETE FROM knowledge_vector_cache WHERE rowid IN ("
                            + "SELECT rowid FROM knowledge_vector_cache WHERE signature = ?"
                            + " ORDER BY created_at LIMIT ?)",
                    signature, count - MAX_ROWS_PER_SIGNATURE + TRIM_BATCH);
        }
    }
}
