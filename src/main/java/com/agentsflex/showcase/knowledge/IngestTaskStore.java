package com.agentsflex.showcase.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识灌入任务的 DuckDB 存储。
 *
 * <p>对齐 WeKnora 的任务状态机（parse_status: pending→processing→finalizing→completed）：
 * 上传请求只负责登记任务并立即返回，解析/切片/向量化在后台线程推进，状态与阶段
 * 随写随查。任务记录在服务重启后仍可浏览（终态任务由查询端过滤归档）。</p>
 */
@Component
public class IngestTaskStore {

    /** 终态集合：不再推进的任务，任务列表轮询可据此停止。 */
    private static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "FAILED", "CANCELLED");

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc DuckDB 连接的 JdbcTemplate（与文档元数据共享数据源）
     */
    public IngestTaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等建表。 */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_ingest_task ("
                + " task_id VARCHAR PRIMARY KEY,"
                + " doc_id VARCHAR,"
                + " title VARCHAR NOT NULL,"
                + " source VARCHAR NOT NULL,"
                + " file_name VARCHAR,"
                + " status VARCHAR NOT NULL,"
                + " stage VARCHAR,"
                + " progress INTEGER NOT NULL,"
                + " chunk_count INTEGER NOT NULL,"
                + " error VARCHAR,"
                + " created_at BIGINT NOT NULL,"
                + " updated_at BIGINT NOT NULL)");
    }

    /**
     * 登记一个新任务（PENDING，进度 0）。
     *
     * @param taskId 任务 ID
     * @param title  文档标题
     * @param source 来源类型：FILE / MANUAL / REPARSE
     * @param fileName 原始文件名；文本入库为 null
     */
    public void insert(String taskId, String title, String source, String fileName) {
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO knowledge_ingest_task(task_id, doc_id, title, source, file_name,"
                        + " status, stage, progress, chunk_count, error, created_at, updated_at)"
                        + " VALUES (?, NULL, ?, ?, ?, 'PENDING', NULL, 0, 0, NULL, ?, ?)",
                taskId, title, source, fileName, now, now);
    }

    /**
     * 推进任务阶段与进度。
     *
     * @param taskId   任务 ID
     * @param stage    阶段名：PARSING / INDEXING / FINALIZING
     * @param progress 0-100
     */
    public void updateStage(String taskId, String stage, int progress) {
        jdbc.update("UPDATE knowledge_ingest_task SET stage = ?, progress = ?, updated_at = ?"
                        + " WHERE task_id = ?",
                stage, progress, System.currentTimeMillis(), taskId);
    }

    /**
     * 任务收尾：写入终态与产出信息。
     *
     * <p>进度语义：COMPLETED 固定 100；FAILED / CANCELLED 保留最后阶段进度，
     * 不凭空回退或编造。</p>
     *
     * @param taskId     任务 ID
     * @param status     COMPLETED / FAILED / CANCELLED
     * @param docId      成功入库的文档 ID；失败或取消为 null
     * @param chunkCount 成功时的切片数；其余为 0
     * @param error      失败原因；成功或取消为 null
     */
    public void finish(String taskId, String status, String docId, int chunkCount, String error) {
        if ("COMPLETED".equals(status)) {
            jdbc.update("UPDATE knowledge_ingest_task SET status = ?, doc_id = ?, chunk_count = ?,"
                            + " error = NULL, progress = 100, updated_at = ? WHERE task_id = ?",
                    status, docId, chunkCount, System.currentTimeMillis(), taskId);
        } else {
            jdbc.update("UPDATE knowledge_ingest_task SET status = ?, doc_id = ?, chunk_count = ?,"
                            + " error = ?, updated_at = ? WHERE task_id = ?",
                    status, docId, chunkCount, error, System.currentTimeMillis(), taskId);
        }
    }

    /**
     * @param taskId 任务 ID
     * @return 任务视图；不存在返回 {@code null}
     */
    public Map<String, Object> find(String taskId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT task_id, doc_id, title, source, file_name, status, stage, progress,"
                        + " chunk_count, error, created_at, updated_at"
                        + " FROM knowledge_ingest_task WHERE task_id = ?",
                (rs, rowNum) -> rowView(rs.getString("task_id"), rs.getString("doc_id"),
                        rs.getString("title"), rs.getString("source"), rs.getString("file_name"),
                        rs.getString("status"), rs.getString("stage"), rs.getInt("progress"),
                        rs.getInt("chunk_count"), rs.getString("error"), rs.getLong("created_at"),
                        rs.getLong("updated_at")),
                taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * @return 最近任务（上限 50，按创建时间倒序），供任务面板轮询
     */
    public List<Map<String, Object>> listRecent() {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT task_id, doc_id, title, source, file_name, status, stage, progress,"
                        + " chunk_count, error, created_at, updated_at"
                        + " FROM knowledge_ingest_task ORDER BY created_at DESC LIMIT 50",
                (rs, rowNum) -> rowView(rs.getString("task_id"), rs.getString("doc_id"),
                        rs.getString("title"), rs.getString("source"), rs.getString("file_name"),
                        rs.getString("status"), rs.getString("stage"), rs.getInt("progress"),
                        rs.getInt("chunk_count"), rs.getString("error"), rs.getLong("created_at"),
                        rs.getLong("updated_at")));
        return rows == null ? Collections.emptyList() : rows;
    }

    /** @return 是否存在未终态任务（前端轮询停止条件） */
    public boolean hasActive() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_ingest_task WHERE status IN ('PENDING', 'PROCESSING')",
                Integer.class);
        return count != null && count > 0;
    }

    /** 组装前端任务视图。 */
    private static Map<String, Object> rowView(String taskId, String docId, String title, String source,
                                               String fileName, String status, String stage, int progress,
                                               int chunkCount, String error, long createdAt, long updatedAt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("taskId", taskId);
        view.put("docId", docId);
        view.put("title", title);
        view.put("source", source);
        view.put("fileName", fileName);
        view.put("status", status);
        view.put("stage", stage);
        view.put("progress", progress);
        view.put("chunkCount", chunkCount);
        view.put("error", error);
        view.put("createdAt", createdAt);
        view.put("updatedAt", updatedAt);
        return view;
    }

    /** @return 终态状态集合 */
    public static List<String> terminalStatuses() {
        return TERMINAL_STATUSES;
    }
}
