package com.agentsflex.showcase.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

/**
 * 图谱配套数据（站点档案 + 知识问答事实）的 DuckDB 入库与查询。
 *
 * <p>与 {@link TopologyGraphService} 的 Neo4j 图入库互补：图数据库存实体关系，
 * 关系库存站点档案明细（母线/主变/间隔/设备/连接/质量，JSON 文档列）与
 * 1215 条事实行（可按站/类别/关键词 SQL 检索）。资源指纹存于 graph_meta，
 * 资源更新后启动时自动刷新，也可经 REST 手动重建。</p>
 */
@Component
public class GraphArchive {

    private static final Logger log = LoggerFactory.getLogger(GraphArchive.class);

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS graph_meta ("
                    + " key VARCHAR PRIMARY KEY,"
                    + " value VARCHAR NOT NULL)",
            "CREATE TABLE IF NOT EXISTS graph_station_doc ("
                    + " station_name VARCHAR PRIMARY KEY,"
                    + " base_voltage VARCHAR,"
                    + " summary_json VARCHAR NOT NULL,"
                    + " doc_json VARCHAR NOT NULL,"
                    + " updated_at BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS graph_fact ("
                    + " seq BIGINT PRIMARY KEY,"
                    + " station VARCHAR NOT NULL,"
                    + " category VARCHAR NOT NULL,"
                    + " text VARCHAR NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_graph_fact_station ON graph_fact(station)",
            "CREATE INDEX IF NOT EXISTS idx_graph_fact_category ON graph_fact(category)",
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GraphArchive(JdbcTemplate showcaseJdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = showcaseJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSchema() {
        for (String ddl : SCHEMA) {
            jdbc.execute(ddl);
        }
    }

    /**
     * 按资源指纹幂等刷新入库内容（整表替换，保证与资源一致）。
     *
     * @param model 已加载的图谱模型
     * @return 入库统计
     */
    public synchronized Map<String, Object> refresh(PowerTopologyModel model) {
        String fingerprint = model.sourceHash() + ":" + model.kbBundle().generated();
        String stored = queryMeta("fingerprint");
        if (fingerprint.equals(stored)) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("changed", false);
            stats.put("stations", stationCount());
            stats.put("facts", factCount());
            return stats;
        }
        List<PowerTopologyModel.GraphFact> facts = model.facts();
        List<Map<String, Object>> stations = model.kbBundle().stations();
        jdbc.update("DELETE FROM graph_fact");
        jdbc.update("DELETE FROM graph_station_doc");
        jdbc.batchUpdate("INSERT INTO graph_fact (seq, station, category, text) VALUES (?, ?, ?, ?)",
                facts, facts.size(), (ps, fact) -> {
                    ps.setLong(1, fact.seq());
                    ps.setString(2, fact.station());
                    ps.setString(3, fact.category());
                    ps.setString(4, fact.text());
                });
        jdbc.batchUpdate("INSERT INTO graph_station_doc "
                        + "(station_name, base_voltage, summary_json, doc_json, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(station_name) DO UPDATE SET base_voltage = excluded.base_voltage,"
                        + " summary_json = excluded.summary_json, doc_json = excluded.doc_json,"
                        + " updated_at = excluded.updated_at",
                stations, stations.size(), (ps, station) -> {
                    String name = String.valueOf(station.get("station_name"));
                    ps.setString(1, name);
                    ps.setString(2, String.valueOf(station.getOrDefault("base_voltage", "")));
                    ps.setString(3, toJson(station.getOrDefault("summary", Map.of())));
                    ps.setString(4, toJson(station));
                    ps.setLong(5, System.currentTimeMillis());
                });
        upsertMeta("fingerprint", fingerprint);
        upsertMeta("refreshed_at", String.valueOf(System.currentTimeMillis()));
        log.info("图谱配套数据已入库: {} 站点档案 / {} 事实", stations.size(), facts.size());
        return Map.of("changed", true, "stations", stations.size(), "facts", facts.size());
    }

    /**
     * 事实检索：站点、类别、关键词（LIKE）组合过滤，按入库顺序分页。
     *
     * @param station  站名精确匹配，null/blank 不过滤
     * @param category 类别精确匹配，null/blank 不过滤
     * @param keyword  文本包含匹配，null/blank 不过滤
     * @param limit    单页条数（≤500）
     * @param offset   起始偏移
     * @return total + rows
     */
    public Map<String, Object> searchFacts(String station, String category, String keyword,
                                           int limit, int offset) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (station != null && !station.isBlank()) {
            where.append(" AND station = ?");
            args.add(station);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            args.add(category);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND text LIKE ?");
            args.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM graph_fact" + where, Long.class, args.toArray());
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeLimit);
        pageArgs.add(safeOffset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT seq, station, category, text FROM graph_fact" + where
                        + " ORDER BY seq LIMIT ? OFFSET ?", pageArgs.toArray());
        return Map.of("total", total == null ? 0 : total, "rows", rows);
    }

    /** 事实类别清单（带计数，供前端类别过滤）。 */
    public List<Map<String, Object>> factCategories() {
        return jdbc.queryForList("SELECT category, count(*) AS count FROM graph_fact "
                + "GROUP BY category ORDER BY count(*) DESC");
    }

    /** 站点档案行：站名、基础电压、概要统计（summary JSON 已解析为 Map）。 */
    public List<Map<String, Object>> stationSummaries() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT station_name, base_voltage, summary_json FROM graph_station_doc "
                        + "ORDER BY station_name");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("station", row.get("station_name"));
            item.put("baseVoltage", row.get("base_voltage"));
            item.put("summary", fromJson(String.valueOf(row.get("summary_json"))));
            result.add(item);
        }
        return result;
    }

    /** 单站完整档案（buses/transformers/bays/devices/connections/quality…）。 */
    public Map<String, Object> stationDocument(String station) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT doc_json FROM graph_station_doc WHERE station_name = ?", station);
        if (rows.isEmpty()) return null;
        return fromJson(String.valueOf(rows.get(0).get("doc_json")));
    }

    public long stationCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM graph_station_doc", Long.class);
        return count == null ? 0 : count;
    }

    public long factCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM graph_fact", Long.class);
        return count == null ? 0 : count;
    }

    public String queryMeta(String key) {
        List<String> values = jdbc.queryForList(
                "SELECT value FROM graph_meta WHERE key = ?", String.class, key);
        return values.isEmpty() ? null : values.get(0);
    }

    private void upsertMeta(String key, String value) {
        // DuckDB 1.5 不支持 MERGE INTO ... KEY 的旧 upsert 语法，统一用 ON CONFLICT
        jdbc.update("INSERT INTO graph_meta(key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("站点档案序列化失败", error);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception error) {
            log.warn("站点档案 JSON 解析失败: {}", error.getMessage());
            return Map.of();
        }
    }
}
