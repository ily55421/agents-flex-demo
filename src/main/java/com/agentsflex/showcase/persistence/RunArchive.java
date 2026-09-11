package com.agentsflex.showcase.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 DuckDB 的进程内持久化归档。
 *
 * <p>保存三类数据：Agent 定义视图、Run 快照视图与事件审计日志。快照视图就是
 * {@code RunViewMapper.map} 的 JSON 安全映射，因此重启后控制台仍可浏览历史 Run，
 * 只是无法重建 Runner/Memory 继续执行。热读路径使用 Ehcache 缓存：Agent 定义不可变，
 * 保存即整表失效；终态 Run 快照读取按 runId 缓存，写入时逐条失效，保证活跃 Run 始终新鲜。</p>
 */
@Component
public class RunArchive {

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS agent_definition ("
                    + " agent_id VARCHAR PRIMARY KEY,"
                    + " payload VARCHAR NOT NULL,"
                    + " created_at BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS run_snapshot ("
                    + " run_id VARCHAR PRIMARY KEY,"
                    + " conversation_id VARCHAR NOT NULL,"
                    + " agent_id VARCHAR,"
                    + " status VARCHAR,"
                    + " payload VARCHAR NOT NULL,"
                    + " created_at BIGINT NOT NULL,"
                    + " updated_at BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS run_event ("
                    + " event_id VARCHAR PRIMARY KEY,"
                    + " run_id VARCHAR NOT NULL,"
                    + " sequence BIGINT NOT NULL,"
                    + " event_type VARCHAR,"
                    + " payload VARCHAR NOT NULL,"
                    + " occurred_at BIGINT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_run_event_run ON run_event(run_id)",
            "CREATE INDEX IF NOT EXISTS idx_run_snapshot_agent ON run_snapshot(agent_id)"
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * @param jdbc   DuckDB 连接的 JdbcTemplate
     * @param mapper Jackson 序列化器，用于视图与事件 JSON 的读写
     */
    public RunArchive(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * 应用启动与测试装配时幂等建表。DuckDB 的 CREATE TABLE IF NOT EXISTS 保证重复执行安全。
     */
    @PostConstruct
    public void ensureSchema() {
        for (String statement : SCHEMA) {
            jdbc.execute(statement);
        }
    }

    /**
     * 保存不含 API Key 的 Agent 定义视图，并以 agentId 为主键幂等更新。
     * 保存后整表缓存失效，下次读取会从 DuckDB 重新加载。
     *
     * @param agentId   服务端生成的 Agent ID
     * @param view      DemoAgent.toView() 的 JSON 安全映射
     * @param createdAt 创建时间戳
     */
    @CacheEvict(cacheNames = "agentDefinitions", allEntries = true)
    public void saveAgentDefinition(String agentId, Map<String, Object> view, long createdAt) {
        jdbc.update(
                "INSERT INTO agent_definition(agent_id, payload, created_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(agent_id) DO UPDATE SET payload = excluded.payload,"
                        + " created_at = excluded.created_at",
                agentId, write(view), createdAt);
    }

    /**
     * 读取全部已归档 Agent 定义。结果整体缓存，键固定为 all；保存新 Agent 时整表失效。
     *
     * @return 按创建时间排序的定义视图列表
     */
    @Cacheable(cacheNames = "agentDefinitions", key = "'all'")
    public List<Map<String, Object>> loadAgentDefinitions() {
        List<Map<String, Object>> values = jdbc.query(
                "SELECT payload FROM agent_definition ORDER BY created_at",
                (rs, rowNum) -> read(rs.getString("payload")));
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * 幂等保存 Run 快照视图；同一 runId 只保留最新状态，保留首次 createdAt。
     * 写入会失效该 runId 的读取缓存，保证活跃 Run 的读取始终来自最新快照。
     *
     * @param runId          Agent Turn ID
     * @param conversationId ChatMemory 会话 ID
     * @param agentId        Agent 定义 ID
     * @param status         当前原生状态名
     * @param view           RunViewMapper.map 的 JSON 安全视图
     */
    @CacheEvict(cacheNames = "archivedRunSnapshots", key = "#runId")
    public void saveRunSnapshot(String runId, String conversationId, String agentId,
                                String status, Map<String, Object> view) {
        Object createdAtValue = view.get("createdAt");
        long createdAt = createdAtValue instanceof Number
                ? ((Number) createdAtValue).longValue() : System.currentTimeMillis();
        jdbc.update(
                "INSERT INTO run_snapshot(run_id, conversation_id, agent_id, status, payload,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(run_id) DO UPDATE SET status = excluded.status,"
                        + " payload = excluded.payload, updated_at = excluded.updated_at",
                runId, conversationId, agentId, status, write(view), createdAt,
                System.currentTimeMillis());
    }

    /**
     * 按 runId 读取最新归档快照。终态 Run 不再写入，因此读取会被缓存命中；
     * 活跃 Run 每次写入都会逐条失效，读取仍从 DuckDB 获取最新数据。
     *
     * @param runId Agent Turn ID
     * @return 归档视图；不存在时返回 {@code null}
     */
    @Cacheable(cacheNames = "archivedRunSnapshots", key = "#runId")
    public Map<String, Object> loadRunSnapshot(String runId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT payload FROM run_snapshot WHERE run_id = ?",
                (rs, rowNum) -> read(rs.getString("payload")), runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按更新时间列出全部归档 Run 快照，用于重启后历史 Run 列表。
     *
     * @return 全量归档视图列表
     */
    public List<Map<String, Object>> loadRunSnapshots() {
        List<Map<String, Object>> values = jdbc.query(
                "SELECT payload FROM run_snapshot ORDER BY updated_at",
                (rs, rowNum) -> read(rs.getString("payload")));
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * 追加一条事件审计记录。事件表只追加不更新，保留每次模型调用、工具执行与生命周期变更。
     *
     * @param runId     事件所属 Turn ID
     * @param eventId   全局唯一事件 ID
     * @param sequence  同一 Turn 内单调递增序号
     * @param type      AgentEventType 名称
     * @param occurredAt 事件发生时间戳
     * @param payload   事件业务数据映射
     */
    public void appendEvent(String runId, String eventId, long sequence, String type,
                            long occurredAt, Map<String, Object> payload) {
        jdbc.update(
                "INSERT INTO run_event(event_id, run_id, sequence, event_type, payload, occurred_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                eventId, runId, sequence, type, write(payload == null
                        ? Collections.emptyMap() : payload), occurredAt);
    }

    /**
     * 按序号读取指定 Run 的事件审计日志，供测试与后续扩展使用。
     *
     * @param runId Agent Turn ID
     * @return 按 sequence 升序的事件 payload 列表
     */
    public List<Map<String, Object>> loadEvents(String runId) {
        List<Map<String, Object>> values = jdbc.query(
                "SELECT payload FROM run_event WHERE run_id = ? ORDER BY sequence",
                (rs, rowNum) -> read(rs.getString("payload")), runId);
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * 将视图或事件 payload 序列化为 JSON 文本；失败时以运行时异常中断持久化，避免静默丢数据。
     *
     * @param value 任意 JSON 安全对象
     * @return JSON 字符串
     */
    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("序列化持久化数据失败", error);
        }
    }

    /**
     * 将 JSON 文本反序列化为通用有序映射；失败时以运行时异常中断读取。
     *
     * @param json 持久化的 JSON 字符串
     * @return LinkedHashMap 视图
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        try {
            return (Map<String, Object>) mapper.readValue(json, Map.class);
        } catch (Exception error) {
            throw new IllegalStateException("反序列化持久化数据失败", error);
        }
    }

    /**
     * @return 当前归档内的 Agent 定义数量，供启动恢复与监控面板展示
     */
    public int countAgentDefinitions() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_definition", Integer.class);
        return count == null ? 0 : count;
    }
}
