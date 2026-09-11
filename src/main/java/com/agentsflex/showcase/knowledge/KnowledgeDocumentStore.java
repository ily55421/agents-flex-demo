package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档元数据的 DuckDB 存储。
 *
 * <p>向量与切片正文保存在 RogueMemory mmap 中；本表只保存文档级清单
 * （标题、来源、切片数、字符数、生效的 embedding 签名），用于列表展示、
 * 状态统计和删除时定位 namespace。删除文档时先删 RogueMemory，再删本表行。</p>
 */
@Component
public class KnowledgeDocumentStore {

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS knowledge_document ("
            + " doc_id VARCHAR PRIMARY KEY,"
            + " title VARCHAR NOT NULL,"
            + " source VARCHAR NOT NULL,"
            + " chunk_count INTEGER NOT NULL,"
            + " char_count BIGINT NOT NULL,"
            + " embedding_signature VARCHAR,"
            + " created_at BIGINT NOT NULL)";

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与 Run 归档共享数据源）
     */
    public KnowledgeDocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等建表。
     */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute(CREATE_TABLE);
    }

    /**
     * 登记一个已入库文档。
     *
     * @param docId      文档 ID，同时是 RogueMemory namespace
     * @param title      展示标题
     * @param source     来源类型：MANUAL / FILE / BUILTIN
     * @param chunkCount 切片数量
     * @param charCount  原文字符数
     * @param signature  入库时生效的 embedding 签名
     */
    public void insert(String docId, String title, String source, int chunkCount,
                       long charCount, String signature) {
        jdbc.update("INSERT INTO knowledge_document(doc_id, title, source, chunk_count,"
                + " char_count, embedding_signature, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                docId, title, source, chunkCount, charCount, signature, System.currentTimeMillis());
    }

    /**
     * @return 全部文档元数据，按创建时间倒序
     */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> values = jdbc.query(
                "SELECT doc_id, title, source, chunk_count, char_count, embedding_signature,"
                        + " created_at FROM knowledge_document ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("docId", rs.getString("doc_id"));
                    row.put("title", rs.getString("title"));
                    row.put("source", rs.getString("source"));
                    row.put("chunkCount", rs.getInt("chunk_count"));
                    row.put("charCount", rs.getLong("char_count"));
                    row.put("embeddingSignature", rs.getString("embedding_signature"));
                    row.put("createdAt", rs.getLong("created_at"));
                    return row;
                });
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * @param docId 文档 ID
     */
    public void delete(String docId) {
        jdbc.update("DELETE FROM knowledge_document WHERE doc_id = ?", docId);
    }

    /**
     * 清空全部文档元数据，配合 RogueMemory 重建使用。
     */
    public void clear() {
        jdbc.update("DELETE FROM knowledge_document");
    }

    /**
     * @return 文档总数
     */
    public int count() {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_document", Integer.class);
        return value == null ? 0 : value;
    }

    /**
     * @return 全部文档的切片总数；用于状态面板展示向量规模
     */
    public long totalChunks() {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(chunk_count), 0) FROM knowledge_document", Long.class);
        return value == null ? 0L : value;
    }

    /**
     * @return 已登记文档中出现过的 embedding 签名集合（去重）
     */
    public List<String> signatures() {
        List<String> values = new ArrayList<>(jdbc.queryForList(
                "SELECT DISTINCT embedding_signature FROM knowledge_document", String.class));
        values.remove(null);
        return values;
    }
}
