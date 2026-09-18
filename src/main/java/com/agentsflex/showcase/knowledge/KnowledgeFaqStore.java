package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ 条目的 DuckDB 存储。
 *
 * <p>对齐 WeKnora 的 FAQ 知识库：标准问 + 相似问 + 答案，检索粒度到条目。每个条目
 * 物化为一条特殊文档（docId = faq-{entryId}，source = FAQ），复用文档的两级 namespace、
 * 检索与清单链路；本表保存结构化字段，供编辑与重建内容使用。</p>
 */
@Component
public class KnowledgeFaqStore {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与文档元数据共享数据源）
     */
    public KnowledgeFaqStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等建表。 */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_faq_entry ("
                + " entry_id VARCHAR PRIMARY KEY,"
                + " doc_id VARCHAR NOT NULL,"
                + " knowledge_base_id VARCHAR NOT NULL,"
                + " standard_question VARCHAR NOT NULL,"
                + " similar_questions VARCHAR,"
                + " answer VARCHAR NOT NULL,"
                + " created_at BIGINT NOT NULL,"
                + " updated_at BIGINT NOT NULL)");
    }

    /**
     * 登记一个 FAQ 条目（文档由 KnowledgeService 负责物化）。
     *
     * @return entryId / docId 视图
     */
    public Map<String, Object> insert(String entryId, String docId, String kbId,
                                      String standardQuestion, List<String> similarQuestions,
                                      String answer) {
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO knowledge_faq_entry(entry_id, doc_id, knowledge_base_id,"
                        + " standard_question, similar_questions, answer, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entryId, docId, kbId, standardQuestion,
                similarQuestions == null ? "[]" : KnowledgeFaqStore.toJsonArray(similarQuestions),
                answer, now, now);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("entryId", entryId);
        view.put("docId", docId);
        view.put("kbId", kbId);
        view.put("standardQuestion", standardQuestion);
        view.put("similarQuestions", similarQuestions == null ? Collections.emptyList() : similarQuestions);
        view.put("answer", answer);
        return view;
    }

    /**
     * @param entryId 条目 ID
     * @return 条目视图；不存在返回 {@code null}
     */
    public Map<String, Object> find(String entryId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT entry_id, doc_id, knowledge_base_id, standard_question, similar_questions,"
                        + " answer, created_at, updated_at FROM knowledge_faq_entry WHERE entry_id = ?",
                (rs, rowNum) -> rowView(rs.getString("entry_id"), rs.getString("doc_id"),
                        rs.getString("knowledge_base_id"), rs.getString("standard_question"),
                        fromJsonArray(rs.getString("similar_questions")), rs.getString("answer"),
                        rs.getLong("created_at"), rs.getLong("updated_at")),
                entryId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * @param kbId 知识库 ID
     * @return 该库全部 FAQ 条目
     */
    public List<Map<String, Object>> listByKb(String kbId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT entry_id, doc_id, knowledge_base_id, standard_question, similar_questions,"
                        + " answer, created_at, updated_at FROM knowledge_faq_entry"
                        + " WHERE knowledge_base_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rowView(rs.getString("entry_id"), rs.getString("doc_id"),
                        rs.getString("knowledge_base_id"), rs.getString("standard_question"),
                        fromJsonArray(rs.getString("similar_questions")), rs.getString("answer"),
                        rs.getLong("created_at"), rs.getLong("updated_at")),
                kbId);
        return rows == null ? Collections.emptyList() : rows;
    }

    /** @param entryId 条目 ID */
    public void delete(String entryId) {
        jdbc.update("DELETE FROM knowledge_faq_entry WHERE entry_id = ?", entryId);
    }

    /** 更新条目结构化字段（文档内容重建由 KnowledgeService 负责）。 */
    public void update(String entryId, String standardQuestion, List<String> similarQuestions,
                       String answer) {
        jdbc.update("UPDATE knowledge_faq_entry SET standard_question = ?, similar_questions = ?,"
                        + " answer = ?, updated_at = ? WHERE entry_id = ?",
                standardQuestion, toJsonArray(similarQuestions), answer,
                System.currentTimeMillis(), entryId);
    }

    /** @return 该库 FAQ 条目数 */
    public int countByKb(String kbId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_faq_entry WHERE knowledge_base_id = ?",
                Integer.class, kbId);
        return count == null ? 0 : count;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static Map<String, Object> rowView(String entryId, String docId, String kbId,
                                               String standardQuestion, List<String> similarQuestions,
                                               String answer, long createdAt, long updatedAt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("entryId", entryId);
        view.put("docId", docId);
        view.put("kbId", kbId);
        view.put("standardQuestion", standardQuestion);
        view.put("similarQuestions", similarQuestions);
        view.put("answer", answer);
        view.put("createdAt", createdAt);
        view.put("updatedAt", updatedAt);
        return view;
    }

    /** 简单 JSON 数组序列化（元素为无引号风险的文本由 Jackson 处理）。 */
    private static String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) sb.append(',');
            sb.append('"').append(values.get(index).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** 解析 JSON 数组文本；空或畸形返回空表。 */
    private static List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
        } catch (Exception error) {
            return Collections.emptyList();
        }
    }
}
