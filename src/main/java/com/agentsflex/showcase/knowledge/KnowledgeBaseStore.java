package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多知识库的 DuckDB 存储。
 *
 * <p>对齐 WeKnora 的 knowledge_bases 表：每个库绑定切片参数与检索参数，文档归属
 * 某个库（knowledge_document.knowledge_base_id）。首次访问自举默认库并把无归属的
 * 历史文档原地回填（无需重建索引——mmap 每次启动都从 DuckDB 重建，两层 namespace
 * {@code kbId/docId} 在重建时自然生效）。</p>
 */
@Component
public class KnowledgeBaseStore {

    /** 默认知识库 ID：历史文档与未指定库的新文档归属于此。 */
    public static final String DEFAULT_KB_ID = "kb-default";

    private final JdbcTemplate jdbc;
    private volatile boolean bootstrapped;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与文档元数据共享数据源）
     */
    public KnowledgeBaseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等建表，并给历史文档表补 knowledge_base_id 列。 */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_base ("
                + " kb_id VARCHAR PRIMARY KEY,"
                + " name VARCHAR NOT NULL,"
                + " description VARCHAR,"
                + " chunk_size INTEGER NOT NULL,"
                + " chunk_overlap INTEGER NOT NULL,"
                + " top_k INTEGER NOT NULL,"
                + " created_at BIGINT NOT NULL,"
                + " updated_at BIGINT NOT NULL,"
                + " deleted BOOLEAN NOT NULL)");
        try {
            jdbc.execute("ALTER TABLE knowledge_document ADD COLUMN knowledge_base_id VARCHAR");
        } catch (Exception ignored) {
            // 列已存在时 ALTER 报错，忽略即可。
        }
    }

    /**
     * 自举默认库：先确保 schema（测试环境不走 Spring 的 @PostConstruct），库表为空时
     * 创建 kb-default，并把无归属文档原地回填。幂等；首次调用后置位，后续直接返回。
     */
    public synchronized void bootstrapDefault() {
        if (bootstrapped) return;
        ensureSchema();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_base", Integer.class);
        if (count != null && count == 0) {
            long now = System.currentTimeMillis();
            jdbc.update("INSERT INTO knowledge_base(kb_id, name, description, chunk_size,"
                            + " chunk_overlap, top_k, created_at, updated_at, deleted)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)",
                    DEFAULT_KB_ID, "默认知识库", "历史与默认文档归属库", 512, 80, 5, now, now);
        }
        jdbc.update("UPDATE knowledge_document SET knowledge_base_id = ?"
                + " WHERE knowledge_base_id IS NULL", DEFAULT_KB_ID);
        bootstrapped = true;
    }

    /**
     * 创建知识库。
     *
     * @param kbId        库 ID
     * @param name        名称（必填）
     * @param description 描述（可空）
     * @param chunkSize   切片窗口；小于等于 0 时用 512
     * @param chunkOverlap 重叠；负数时用 80
     * @param topK        库级检索条数；小于等于 0 时用 5
     * @return 库视图
     */
    public Map<String, Object> create(String kbId, String name, String description,
                                      int chunkSize, int chunkOverlap, int topK) {
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO knowledge_base(kb_id, name, description, chunk_size,"
                        + " chunk_overlap, top_k, created_at, updated_at, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)",
                kbId, name, description,
                chunkSize > 0 ? chunkSize : 512,
                chunkOverlap >= 0 ? chunkOverlap : 80,
                topK > 0 ? topK : 5, now, now);
        return get(kbId);
    }

    /** 更新库名称/描述/参数（保留创建时间）。 */
    public void update(String kbId, String name, String description,
                       int chunkSize, int chunkOverlap, int topK) {
        jdbc.update("UPDATE knowledge_base SET name = ?, description = ?, chunk_size = ?,"
                        + " chunk_overlap = ?, top_k = ?, updated_at = ? WHERE kb_id = ?",
                name, description, chunkSize, chunkOverlap, topK,
                System.currentTimeMillis(), kbId);
    }

    /**
     * 软删除知识库（文档须已清空，由调用方校验）。
     *
     * @return 是否删除了记录
     */
    public boolean softDelete(String kbId) {
        return jdbc.update("UPDATE knowledge_base SET deleted = TRUE, updated_at = ?"
                + " WHERE kb_id = ?", System.currentTimeMillis(), kbId) > 0;
    }

    /**
     * @param kbId 库 ID
     * @return 库视图；不存在或已删除返回 {@code null}
     */
    public Map<String, Object> get(String kbId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT kb_id, name, description, chunk_size, chunk_overlap, top_k,"
                        + " created_at, updated_at FROM knowledge_base"
                        + " WHERE kb_id = ? AND deleted = FALSE",
                (rs, rowNum) -> rowView(rs.getString("kb_id"), rs.getString("name"),
                        rs.getString("description"), rs.getInt("chunk_size"),
                        rs.getInt("chunk_overlap"), rs.getInt("top_k"),
                        rs.getLong("created_at"), rs.getLong("updated_at")),
                kbId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** @return 全部未删除库，按创建时间正序 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT kb_id, name, description, chunk_size, chunk_overlap, top_k,"
                        + " created_at, updated_at FROM knowledge_base WHERE deleted = FALSE"
                        + " ORDER BY created_at",
                (rs, rowNum) -> rowView(rs.getString("kb_id"), rs.getString("name"),
                        rs.getString("description"), rs.getInt("chunk_size"),
                        rs.getInt("chunk_overlap"), rs.getInt("top_k"),
                        rs.getLong("created_at"), rs.getLong("updated_at")));
        return rows == null ? Collections.emptyList() : rows;
    }

    /** @return 指定库下未删除文档的 docId 集合 */
    public List<String> docIdsOf(String kbId) {
        return new ArrayList<>(jdbc.queryForList(
                "SELECT doc_id FROM knowledge_document WHERE knowledge_base_id = ?",
                String.class, kbId));
    }

    /** @return 文档归属的库 ID；无归属返回默认库 */
    public String kbIdOfDoc(String docId) {
        List<String> rows = jdbc.query("SELECT knowledge_base_id FROM knowledge_document"
                        + " WHERE doc_id = ?",
                (rs, rowNum) -> {
                    String value = rs.getString(1);
                    return value == null ? DEFAULT_KB_ID : value;
                }, docId);
        return rows.isEmpty() ? DEFAULT_KB_ID : rows.get(0);
    }

    /** 设置文档归属库（跨库移动用）。 */
    public void assignDoc(String docId, String kbId) {
        jdbc.update("UPDATE knowledge_document SET knowledge_base_id = ? WHERE doc_id = ?",
                kbId, docId);
    }

    /** @return 指定库下文档数 */
    public int docCountOf(String kbId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE knowledge_base_id = ?",
                Integer.class, kbId);
        return count == null ? 0 : count;
    }

    /** 组装前端库视图。 */
    private static Map<String, Object> rowView(String kbId, String name, String description,
                                               int chunkSize, int chunkOverlap, int topK,
                                               long createdAt, long updatedAt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("kbId", kbId);
        view.put("name", name);
        view.put("description", description);
        view.put("chunkSize", chunkSize);
        view.put("chunkOverlap", chunkOverlap);
        view.put("topK", topK);
        view.put("createdAt", createdAt);
        view.put("updatedAt", updatedAt);
        return view;
    }
}
