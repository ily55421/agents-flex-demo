package com.agentsflex.showcase.blog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文档博客的 SQLite 存储层：接管原 Python 服务的建库、增量导入、全文检索与关系图谱。
 *
 * <p>检索能力来自 SQLite 的 FTS5 {@code trigram} 分词器——它按三字符滑窗建索引，
 * 因此中文子串（如「垃圾回收」）无需分词即可命中，这是选它而非 DuckDB 的原因。</p>
 *
 * <p>并发模型：SQLite 开启 WAL 后支持多读单写，读操作各开独立连接，
 * 写操作统一由 {@link #writeLock} 串行化，配合 {@code busy_timeout} 避免多线程下
 * 出现 {@code SQLITE_BUSY}。文档正文按原始 Markdown 存储，渲染交给前端，
 * 后端只为检索保存一份清洗后的副本，避免重复实现 Markdown 渲染。</p>
 */
@Component
public class BlogStore {

    private static final Logger log = LoggerFactory.getLogger(BlogStore.class);

    /** 检索副本的清洗规则：去掉 Markdown 标记符号，只保留可检索的正文。 */
    private static final Pattern SEARCH_NOISE = Pattern.compile("[#*`\\-\\[\\]()>|]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** FTS5 MATCH 的查询串清洗：只保留字母、数字、下划线、汉字与空白，其余转空格。 */
    private static final Pattern QUERY_NOISE = Pattern.compile("[^\\p{L}\\p{N}_\\s]");
    /** 标题关键词提取：连续汉字（≥2）或英文单词，用于相似度计算。 */
    private static final Pattern TITLE_KEYWORDS = Pattern.compile("[\\u4e00-\\u9fff]{2,}|[a-zA-Z]+");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** FTS5 trigram 至少需要 3 个字符才能匹配，短查询直接走 LIKE。 */
    private static final int TRIGRAM_MIN_LENGTH = 3;
    /** 每页条数上限，防止一次请求拖垮内存。 */
    private static final int MAX_LIMIT = 100;

    private final BlogProperties properties;
    private final ReentrantLock writeLock = new ReentrantLock();

    /** 源文档根目录的绝对路径，启动时解析一次。 */
    private volatile Path docsRoot;

    public BlogStore(BlogProperties properties) {
        this.properties = properties;
    }

    // ─── 初始化 ──────────────────────────────────────────────

    /**
     * 建表（幂等）：表结构与原 Python 服务保持一致，便于在两种实现间迁移数据库文件。
     * 已存在的数据库只补缺失的列，不删除任何数据。
     */
    public void initSchema() {
        writeLock.lock();
        try (Connection conn = open()) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS categories (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            slug        TEXT UNIQUE NOT NULL,
                            name        TEXT NOT NULL,
                            icon        TEXT DEFAULT '📁',
                            sort_order  INTEGER DEFAULT 0
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS documents (
                            id           INTEGER PRIMARY KEY AUTOINCREMENT,
                            category_id  INTEGER NOT NULL REFERENCES categories(id),
                            filename     TEXT NOT NULL,
                            title        TEXT NOT NULL,
                            filepath     TEXT NOT NULL UNIQUE,
                            content      TEXT NOT NULL,
                            search_text  TEXT NOT NULL DEFAULT '',
                            word_count   INTEGER DEFAULT 0,
                            file_mtime   REAL DEFAULT 0,
                            created_at   TEXT,
                            updated_at   TEXT,
                            deleted_at   TEXT DEFAULT NULL
                        )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_doc_category ON documents(category_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_doc_title ON documents(title)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_doc_mtime ON documents(file_mtime)");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS tags (
                            id    INTEGER PRIMARY KEY AUTOINCREMENT,
                            name  TEXT UNIQUE NOT NULL,
                            slug  TEXT UNIQUE NOT NULL,
                            color TEXT DEFAULT '#6c8cff'
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS document_tags (
                            document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                            tag_id      INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                            PRIMARY KEY (document_id, tag_id)
                        )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_doc_tags_doc ON document_tags(document_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_doc_tags_tag ON document_tags(tag_id)");
                st.execute("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts USING fts5(
                            title,
                            content,
                            category_name,
                            tokenize='trigram'
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS doc_relations (
                            id         INTEGER PRIMARY KEY AUTOINCREMENT,
                            source_id  INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                            target_id  INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                            rel_type   TEXT NOT NULL,
                            weight     REAL DEFAULT 0.5,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE(source_id, target_id, rel_type)
                        )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_rel_source ON doc_relations(source_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_rel_target ON doc_relations(target_id)");
            }
            ensureColumn(conn, "documents", "deleted_at", "TEXT DEFAULT NULL");
            ensureColumn(conn, "documents", "search_text", "TEXT NOT NULL DEFAULT ''");
            conn.commit();
        } catch (SQLException error) {
            throw new IllegalStateException("初始化文档博客数据库失败: " + error.getMessage(), error);
        } finally {
            writeLock.unlock();
        }
    }

    /** 为旧库补齐缺失列；{@code ALTER TABLE} 不支持 IF NOT EXISTS，只能先查后加。 */
    private void ensureColumn(Connection conn, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    /**
     * @return 当前库中的文档总数（含回收站），供启动时判断是否需要全量导入
     */
    public int documentCount() {
        try (Connection conn = open();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM documents")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException error) {
            throw new IllegalStateException("读取文档总数失败: " + error.getMessage(), error);
        }
    }

    // ─── 导入 ────────────────────────────────────────────────

    /**
     * 扫描源目录并同步到数据库。
     *
     * <p>增量模式按「文件路径 + mtime」判断变更，只处理新增与修改过的文件，
     * 并把已从磁盘消失的文档标记为软删除（保留在回收站可恢复）；
     * 全量模式先清空文档与索引再重新导入。</p>
     *
     * @param incremental true 为增量同步，false 为全量重建
     * @return 本次导入统计：imported / updated / skipped / deleted
     */
    public Map<String, Object> importDocuments(boolean incremental) {
        Path root = resolveDocsRoot();
        writeLock.lock();
        try (Connection conn = open()) {
            Map<String, Long> categoryIds = ensureCategories(conn);
            if (!incremental) {
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM docs_fts");
                    st.execute("DELETE FROM documents");
                }
            }
            // 已入库文档：绝对路径 → {id, mtime}
            Map<String, long[]> existing = new HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT id, filepath, file_mtime FROM documents WHERE deleted_at IS NULL")) {
                while (rs.next()) {
                    existing.put(rs.getString("filepath"), new long[]{rs.getLong("id"), (long) rs.getDouble("file_mtime")});
                }
            }

            ImportCounter counter = new ImportCounter();
            Set<String> seen = new HashSet<>();
            for (Path file : scanMarkdownFiles(root)) {
                processFile(conn, categoryIds, existing, counter, seen, file, incremental);
            }

            if (incremental) {
                String now = STAMP.format(java.time.LocalDateTime.now());
                for (Map.Entry<String, long[]> entry : existing.entrySet()) {
                    if (seen.contains(entry.getKey())) {
                        continue;
                    }
                    long id = entry.getValue()[0];
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE documents SET deleted_at = ? WHERE id = ?")) {
                        ps.setString(1, now);
                        ps.setLong(2, id);
                        ps.executeUpdate();
                    }
                    deleteFts(conn, id);
                    counter.deleted++;
                }
            }
            // 清理孤儿关联：软删除文档不再参与标签与图谱
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        DELETE FROM document_tags WHERE document_id IN (
                            SELECT id FROM documents WHERE deleted_at IS NOT NULL)""");
                st.execute("""
                        DELETE FROM doc_relations WHERE source_id IN (
                            SELECT id FROM documents WHERE deleted_at IS NOT NULL)
                        OR target_id IN (SELECT id FROM documents WHERE deleted_at IS NOT NULL)""");
            }
            conn.commit();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imported", counter.imported);
            result.put("updated", counter.updated);
            result.put("skipped", counter.skipped);
            result.put("deleted", counter.deleted);
            log.info("[blog] 导入完成（增量={}）新增 {} / 更新 {} / 跳过 {} / 软删除 {}",
                    incremental, counter.imported, counter.updated, counter.skipped, counter.deleted);
            return result;
        } catch (SQLException | IOException error) {
            throw new IllegalStateException("导入文档失败: " + error.getMessage(), error);
        } finally {
            writeLock.unlock();
        }
    }

    /** 导入计数：用可变对象在递归/循环中累计，避免方法返回值层层传递。 */
    private static final class ImportCounter {
        private int imported;
        private int updated;
        private int skipped;
        private int deleted;
    }

    /**
     * 枚举待导入的 Markdown：先扫 {@code docs/<分类名>/}，再扫根目录。
     * 排序保证导入顺序稳定，便于复现问题。
     */
    private List<Path> scanMarkdownFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            log.warn("[blog] 源目录不存在，跳过导入: {}", root);
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        Path categoriesDir = root.resolve("docs");
        if (Files.isDirectory(categoriesDir)) {
            try (Stream<Path> subdirs = Files.list(categoriesDir)) {
                for (Path subdir : subdirs.filter(Files::isDirectory).sorted().toList()) {
                    try (Stream<Path> found = Files.list(subdir)) {
                        found.filter(BlogStore::isMarkdown)
                                // 分类总览文件（00_ 前缀）是索引页，不单独入库
                                .filter(path -> !path.getFileName().toString().startsWith("00_"))
                                .sorted()
                                .forEach(files::add);
                    }
                }
            }
        }
        try (Stream<Path> found = Files.list(root)) {
            found.filter(BlogStore::isMarkdown)
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0;
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    private static boolean isMarkdown(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** 处理单个文件：判断是否需要写入，并同步维护 FTS 索引。 */
    private void processFile(Connection conn, Map<String, Long> categoryIds, Map<String, long[]> existing,
                            ImportCounter counter, Set<String> seen, Path file, boolean incremental)
            throws IOException, SQLException {
        String key = file.toAbsolutePath().normalize().toString();
        if (!seen.add(key)) {
            return;
        }
        double mtime = Files.getLastModifiedTime(file).toMillis() / 1000.0;
        long[] previous = existing.get(key);
        if (incremental && previous != null && Math.abs(previous[1] - mtime) < 1) {
            counter.skipped++;
            return;
        }
        if (previous != null) {
            deleteFts(conn, previous[0]);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM documents WHERE id = ?")) {
                ps.setLong(1, previous[0]);
                ps.executeUpdate();
            }
            counter.updated++;
        }

        String raw = Files.readString(file, StandardCharsets.UTF_8);
        String filename = file.getFileName().toString();
        // 目录名优先决定分类；根目录文件按文件名与正文关键词自动判定
        String dirSlug = BlogCategoryRules.slugOfDirectory(file.getParent().getFileName().toString());
        String slug = dirSlug != null ? dirSlug
                : BlogCategoryRules.classify(filename, raw.substring(0, Math.min(500, raw.length())));
        long categoryId = categoryIds.getOrDefault(slug, categoryIds.get(BlogCategoryRules.OTHER_SLUG));

        String title = filename.replaceAll("(?i)\\.md$", "");
        for (String line : raw.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("#")) {
                title = stripped.replaceAll("^#+\\s*", "").strip();
                break;
            }
        }
        String searchText = toSearchText(raw);
        String created = DAY.format(Instant.ofEpochMilli((long) (mtime * 1000))
                .atZone(ZoneId.systemDefault()).toLocalDate());
        String categoryName = categoryName(conn, categoryId);

        long id;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO documents
                    (category_id, filename, title, filepath, content, search_text, word_count,
                     file_mtime, created_at, updated_at, deleted_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,NULL)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, categoryId);
            ps.setString(2, filename);
            ps.setString(3, title);
            ps.setString(4, key);
            ps.setString(5, raw);
            ps.setString(6, searchText);
            ps.setInt(7, searchText.length());
            ps.setDouble(8, mtime);
            ps.setString(9, created);
            ps.setString(10, created);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getLong(1) : -1;
            }
        }
        insertFts(conn, id, title, searchText, categoryName);
        if (previous == null) {
            counter.imported++;
        }
    }

    /** 检索副本：去掉 Markdown 标记符号并压缩空白，让正文词元不被符号割裂。 */
    private static String toSearchText(String markdown) {
        String cleaned = SEARCH_NOISE.matcher(markdown).replaceAll(" ");
        return WHITESPACE.matcher(cleaned).replaceAll(" ").strip();
    }

    /** upsert 全部分类规则，返回 slug → 分类 id。 */
    private Map<String, Long> ensureCategories(Connection conn) throws SQLException {
        Map<String, Long> ids = new HashMap<>();
        int order = 0;
        for (BlogCategoryRules.Rule rule : BlogCategoryRules.rules()) {
            Long id = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM categories WHERE slug = ?")) {
                ps.setString(1, rule.slug());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getLong(1);
                    }
                }
            }
            if (id == null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO categories (slug, name, icon, sort_order) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, rule.slug());
                    ps.setString(2, rule.name());
                    ps.setString(3, rule.icon());
                    ps.setInt(4, order);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        id = keys.next() ? keys.getLong(1) : -1L;
                    }
                }
            }
            ids.put(rule.slug(), id);
            order++;
        }
        return ids;
    }

    private String categoryName(Connection conn, long categoryId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM categories WHERE id = ?")) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    private void insertFts(Connection conn, long id, String title, String searchText, String categoryName)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO docs_fts(rowid, title, content, category_name) VALUES (?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, title);
            ps.setString(3, searchText);
            ps.setString(4, categoryName);
            ps.executeUpdate();
        }
    }

    private void deleteFts(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM docs_fts WHERE rowid = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ─── 查询 API ────────────────────────────────────────────

    /** @return 全部分类及在用文档数，按 sort_order 排序 */
    public Map<String, Object> categories() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = open();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT c.id, c.slug, c.name, c.icon, COUNT(d.id) AS doc_count
                     FROM categories c
                     LEFT JOIN documents d ON d.category_id = c.id AND d.deleted_at IS NULL
                     GROUP BY c.id ORDER BY c.sort_order""")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("slug", rs.getString("slug"));
                row.put("name", rs.getString("name"));
                row.put("icon", rs.getString("icon"));
                row.put("doc_count", rs.getInt("doc_count"));
                rows.add(row);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("查询分类失败: " + error.getMessage(), error);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", rows);
        return result;
    }

    /**
     * 文档列表（不含回收站）。
     *
     * @param categorySlug 分类 slug，为空表示全部分类
     * @param page         页码，从 1 开始
     * @param limit        每页条数
     * @return documents / total / page / limit / pages
     */
    public Map<String, Object> documents(String categorySlug, int page, int limit) {
        int safeLimit = clampLimit(limit);
        int safePage = Math.max(1, page);
        String where = "WHERE d.deleted_at IS NULL";
        List<Object> params = new ArrayList<>();
        if (categorySlug != null && !categorySlug.isBlank()) {
            where += " AND c.slug = ?";
            params.add(categorySlug);
        }
        try (Connection conn = open()) {
            long total = count(conn, "SELECT COUNT(*) FROM documents d JOIN categories c ON d.category_id = c.id "
                    + where, params);
            List<Map<String, Object>> documents = querySummaries(conn, where, params, safePage, safeLimit);
            return page(documents, total, safePage, safeLimit);
        } catch (SQLException error) {
            throw new IllegalStateException("查询文档列表失败: " + error.getMessage(), error);
        }
    }

    /**
     * 全文检索：优先 FTS5 trigram，短查询或无结果时回退 LIKE 子串匹配。
     *
     * @param query        原始查询串
     * @param page         页码
     * @param limit        每页条数
     * @param categorySlug 分类过滤，可为空
     * @return documents（含 snippet）/ total / page / limit / pages / query
     */
    public Map<String, Object> search(String query, int page, int limit, String categorySlug) {
        int safeLimit = clampLimit(limit);
        int safePage = Math.max(1, page);
        String clean = cleanQuery(query);
        if (clean.isEmpty()) {
            Map<String, Object> empty = page(List.of(), 0, safePage, safeLimit);
            empty.put("query", query == null ? "" : query);
            return empty;
        }
        String categoryFilter = categorySlug != null && !categorySlug.isBlank() ? " AND c.slug = ?" : "";
        List<Object> categoryParams = categorySlug != null && !categorySlug.isBlank()
                ? List.of(categorySlug) : List.of();
        long total = 0;
        List<Map<String, Object>> documents = List.of();
        try (Connection conn = open()) {
            if (clean.length() >= TRIGRAM_MIN_LENGTH) {
                try {
                    total = count(conn,
                            "SELECT COUNT(*) FROM docs_fts JOIN documents d ON d.id = docs_fts.rowid "
                                    + "JOIN categories c ON d.category_id = c.id "
                                    + "WHERE docs_fts MATCH ? AND d.deleted_at IS NULL" + categoryFilter,
                            prepend(clean, categoryParams));
                    documents = querySearchFts(conn, clean, categoryFilter, categoryParams, safePage, safeLimit);
                } catch (SQLException error) {
                    // trigram 索引不可用（如查询串含 FTS 语法字符）时静默降级
                    log.debug("[blog] FTS 检索失败，回退 LIKE: {}", error.getMessage());
                    documents = List.of();
                    total = 0;
                }
            }
            if (documents.isEmpty()) {
                String like = "%" + clean + "%";
                String likeWhere = "WHERE d.deleted_at IS NULL AND (d.title LIKE ? OR d.search_text LIKE ?)"
                        + categoryFilter;
                List<Object> likeParams = prepend(like, prepend(like, categoryParams));
                total = count(conn, "SELECT COUNT(*) FROM documents d JOIN categories c ON d.category_id = c.id "
                        + likeWhere, likeParams);
                documents = querySearchLike(conn, likeWhere, likeParams, safePage, safeLimit, clean);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("检索失败: " + error.getMessage(), error);
        }
        Map<String, Object> result = page(documents, total, safePage, safeLimit);
        result.put("query", query == null ? "" : query);
        return result;
    }

    /** FTS5 分支：命中结果按 bm25 rank 升序（相关度降序）。 */
    private List<Map<String, Object>> querySearchFts(Connection conn, String clean, String categoryFilter,
                                                     List<Object> categoryParams, int page, int limit)
            throws SQLException {
        String sql = """
                SELECT d.id, d.title, d.filename, d.search_text, d.word_count, d.created_at, d.updated_at,
                       c.name AS category_name, c.slug AS category_slug, c.icon
                FROM docs_fts
                JOIN documents d ON d.id = docs_fts.rowid
                JOIN categories c ON d.category_id = c.id
                WHERE docs_fts MATCH ? AND d.deleted_at IS NULL""" + categoryFilter
                + " ORDER BY rank LIMIT ? OFFSET ?";
        List<Object> params = prepend(clean, categoryParams);
        params.add(limit);
        params.add((page - 1) * limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(toSearchHit(rs, clean));
                }
            }
        }
        return rows;
    }

    /** LIKE 分支：短词（1-2 字）与 FTS 无结果时的兜底。 */
    private List<Map<String, Object>> querySearchLike(Connection conn, String where, List<Object> params,
                                                      int page, int limit, String clean) throws SQLException {
        String sql = """
                SELECT d.id, d.title, d.filename, d.search_text, d.word_count, d.created_at, d.updated_at,
                       c.name AS category_name, c.slug AS category_slug, c.icon
                FROM documents d JOIN categories c ON d.category_id = c.id""" + where
                + " ORDER BY d.id DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(limit);
        queryParams.add((page - 1) * limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(toSearchHit(rs, clean));
                }
            }
        }
        return rows;
    }

    /** 检索结果：附带命中位置附近的纯文本摘要，正文本身不回传（体积大）。 */
    private Map<String, Object> toSearchHit(ResultSet rs, String clean) throws SQLException {
        Map<String, Object> row = summary(rs);
        String text = rs.getString("search_text");
        row.put("snippet", snippet(text, clean));
        return row;
    }

    /**
     * 截取命中词附近的摘要，便于前端展示上下文。
     *
     * @param content  已清洗的正文
     * @param keyword  清洗后的查询串
     * @return 最多 200 字的片段，命中不在开头时前后加省略号
     */
    private static String snippet(String content, String keyword) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int position = -1;
        for (String token : keyword.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            position = content.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
            if (position >= 0) {
                break;
            }
        }
        if (position < 0) {
            position = 0;
        }
        int start = Math.max(0, position - 60);
        int end = Math.min(content.length(), start + 200);
        StringBuilder text = new StringBuilder();
        if (start > 0) {
            text.append("...");
        }
        text.append(content, start, end);
        if (end < content.length()) {
            text.append("...");
        }
        return text.toString();
    }

    /**
     * 搜索建议：标题命中优先，用于输入框下拉。
     *
     * @param query 原始查询串
     * @param limit 建议条数上限
     * @return suggestions 列表
     */
    public Map<String, Object> suggest(String query, int limit) {
        String clean = cleanQuery(query);
        int safeLimit = Math.max(1, Math.min(20, limit));
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (!clean.isEmpty()) {
            String like = "%" + clean + "%";
            try (Connection conn = open()) {
                if (clean.length() >= TRIGRAM_MIN_LENGTH) {
                    try {
                        suggestions = suggestFts(conn, clean, like, safeLimit);
                    } catch (SQLException error) {
                        log.debug("[blog] 建议检索降级到 LIKE: {}", error.getMessage());
                        suggestions = List.of();
                    }
                }
                if (suggestions.isEmpty()) {
                    suggestions = suggestLike(conn, like, safeLimit);
                }
            } catch (SQLException error) {
                throw new IllegalStateException("搜索建议失败: " + error.getMessage(), error);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestions", suggestions);
        return result;
    }

    private List<Map<String, Object>> suggestFts(Connection conn, String clean, String like, int limit)
            throws SQLException {
        String sql = """
                SELECT d.id, d.title, c.name AS category_name, c.slug AS category_slug, c.icon,
                       CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END AS title_match
                FROM docs_fts
                JOIN documents d ON d.id = docs_fts.rowid
                JOIN categories c ON d.category_id = c.id
                WHERE docs_fts MATCH ? AND d.deleted_at IS NULL
                ORDER BY title_match ASC, rank LIMIT ?""";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, clean);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(suggestion(rs));
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> suggestLike(Connection conn, String like, int limit) throws SQLException {
        String sql = """
                SELECT d.id, d.title, c.name AS category_name, c.slug AS category_slug, c.icon,
                       CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END AS title_match
                FROM documents d
                JOIN categories c ON d.category_id = c.id
                WHERE d.deleted_at IS NULL AND (d.title LIKE ? OR d.search_text LIKE ?)
                ORDER BY title_match ASC, d.id DESC LIMIT ?""";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(suggestion(rs));
                }
            }
        }
        return rows;
    }

    private Map<String, Object> suggestion(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("title", rs.getString("title"));
        row.put("category_name", rs.getString("category_name"));
        row.put("category_slug", rs.getString("category_slug"));
        row.put("icon", rs.getString("icon"));
        row.put("title_match", rs.getInt("title_match"));
        return row;
    }

    /**
     * 单篇文档详情，含原始 Markdown 全文供前端渲染。
     *
     * @param id 文档 id
     * @return document 字段；文档不存在时抛 {@link IllegalArgumentException} 由全局处理器转 409
     */
    public Map<String, Object> document(long id) {
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT d.id, d.category_id, d.filename, d.title, d.filepath, d.content, d.word_count,
                            d.created_at, d.updated_at, d.deleted_at,
                            c.name AS category_name, c.slug AS category_slug, c.icon
                     FROM documents d JOIN categories c ON d.category_id = c.id
                     WHERE d.id = ? AND d.deleted_at IS NULL""")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("文档不存在或已删除: #" + id);
                }
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("id", rs.getLong("id"));
                doc.put("category_id", rs.getLong("category_id"));
                doc.put("filename", rs.getString("filename"));
                doc.put("title", rs.getString("title"));
                doc.put("filepath", rs.getString("filepath"));
                doc.put("content", rs.getString("content"));
                doc.put("word_count", rs.getInt("word_count"));
                doc.put("read_time", readTime(rs.getInt("word_count")));
                doc.put("created_at", rs.getString("created_at"));
                doc.put("updated_at", rs.getString("updated_at"));
                doc.put("deleted_at", rs.getString("deleted_at"));
                doc.put("category_name", rs.getString("category_name"));
                doc.put("category_slug", rs.getString("category_slug"));
                doc.put("icon", rs.getString("icon"));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("document", doc);
                return result;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("查询文档详情失败: " + error.getMessage(), error);
        }
    }

    /**
     * 相关文档推荐：同分类内按标题关键词重叠度排序。
     *
     * @param id    中心文档 id
     * @param limit 返回条数
     * @return documents 列表
     */
    public Map<String, Object> related(long id, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        try (Connection conn = open()) {
            String title;
            long categoryId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT title, category_id FROM documents WHERE id = ? AND deleted_at IS NULL")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        Map<String, Object> empty = new LinkedHashMap<>();
                        empty.put("documents", List.of());
                        return empty;
                    }
                    title = rs.getString("title");
                    categoryId = rs.getLong("category_id");
                }
            }
            Set<String> keywords = titleKeywords(title);
            List<Map<String, Object>> candidates = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                           c.name AS category_name, c.slug AS category_slug, c.icon
                    FROM documents d JOIN categories c ON d.category_id = c.id
                    WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL
                    ORDER BY d.id DESC LIMIT 50""")) {
                ps.setLong(1, categoryId);
                ps.setLong(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = summary(rs);
                        int overlap = 0;
                        for (String keyword : titleKeywords(rs.getString("title"))) {
                            if (keywords.contains(keyword)) {
                                overlap++;
                            }
                        }
                        // 关键词完全为空的标题（如纯符号）也保留，避免相关列表整块空掉
                        if (overlap > 0 || keywords.isEmpty()) {
                            row.put("_overlap", overlap);
                            candidates.add(row);
                        }
                    }
                }
            }
            candidates.sort(Comparator
                    .comparingInt((Map<String, Object> row) -> (Integer) row.get("_overlap")).reversed()
                    .thenComparing(row -> -((Number) row.get("id")).longValue()));
            List<Map<String, Object>> documents = new ArrayList<>();
            for (Map<String, Object> row : candidates) {
                row.remove("_overlap");
                documents.add(row);
                if (documents.size() >= safeLimit) {
                    break;
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("documents", documents);
            return result;
        } catch (SQLException error) {
            throw new IllegalStateException("查询相关文档失败: " + error.getMessage(), error);
        }
    }

    /** @return 站点统计：在用文档数、分类数、总字数与回收站数量 */
    public Map<String, Object> stats() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total_documents", scalar(st, "SELECT COUNT(*) FROM documents WHERE deleted_at IS NULL"));
            result.put("total_categories", scalar(st, "SELECT COUNT(*) FROM categories"));
            result.put("total_words",
                    scalar(st, "SELECT COALESCE(SUM(word_count),0) FROM documents WHERE deleted_at IS NULL"));
            result.put("trashed", scalar(st, "SELECT COUNT(*) FROM documents WHERE deleted_at IS NOT NULL"));
            return result;
        } catch (SQLException error) {
            throw new IllegalStateException("查询统计失败: " + error.getMessage(), error);
        }
    }

    private long scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** @return 全部标签及文档数（标签表为空时返回空列表，前端按空态展示） */
    public Map<String, Object> tags() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = open();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT t.id, t.name, t.slug, t.color, COUNT(dt.document_id) AS doc_count
                     FROM tags t
                     LEFT JOIN document_tags dt ON dt.tag_id = t.id
                     GROUP BY t.id ORDER BY doc_count DESC, t.name""")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", rs.getString("name"));
                row.put("slug", rs.getString("slug"));
                row.put("color", rs.getString("color"));
                row.put("doc_count", rs.getInt("doc_count"));
                rows.add(row);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("查询标签失败: " + error.getMessage(), error);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tags", rows);
        return result;
    }

    /**
     * 标签下的文档列表。
     *
     * @param slug  标签 slug
     * @param page  页码
     * @param limit 每页条数
     * @return 分页结构
     */
    public Map<String, Object> tagDocuments(String slug, int page, int limit) {
        int safeLimit = clampLimit(limit);
        int safePage = Math.max(1, page);
        if (slug == null || slug.isBlank()) {
            return page(List.of(), 0, safePage, safeLimit);
        }
        try (Connection conn = open()) {
            long total = count(conn, """
                    SELECT COUNT(*) FROM document_tags dt
                    JOIN tags t ON dt.tag_id = t.id
                    JOIN documents d ON dt.document_id = d.id
                    WHERE t.slug = ? AND d.deleted_at IS NULL""", List.of(slug));
            String sql = """
                    SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                           c.name AS category_name, c.slug AS category_slug, c.icon
                    FROM document_tags dt
                    JOIN tags t ON dt.tag_id = t.id
                    JOIN documents d ON dt.document_id = d.id
                    JOIN categories c ON d.category_id = c.id
                    WHERE t.slug = ? AND d.deleted_at IS NULL
                    ORDER BY d.id DESC LIMIT ? OFFSET ?""";
            List<Map<String, Object>> documents = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, slug);
                ps.setInt(2, safeLimit);
                ps.setInt(3, (safePage - 1) * safeLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        documents.add(summary(rs));
                    }
                }
            }
            return page(documents, total, safePage, safeLimit);
        } catch (SQLException error) {
            throw new IllegalStateException("查询标签文档失败: " + error.getMessage(), error);
        }
    }

    /**
     * 回收站列表。
     *
     * @param page  页码
     * @param limit 每页条数
     * @return 分页结构，条目额外带 deleted_at
     */
    public Map<String, Object> trash(int page, int limit) {
        int safeLimit = clampLimit(limit);
        int safePage = Math.max(1, page);
        try (Connection conn = open()) {
            long total = count(conn, "SELECT COUNT(*) FROM documents WHERE deleted_at IS NOT NULL", List.of());
            List<Map<String, Object>> documents = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at, d.deleted_at,
                           c.name AS category_name, c.slug AS category_slug, c.icon
                    FROM documents d JOIN categories c ON d.category_id = c.id
                    WHERE d.deleted_at IS NOT NULL
                    ORDER BY d.deleted_at DESC LIMIT ? OFFSET ?""")) {
                ps.setInt(1, safeLimit);
                ps.setInt(2, (safePage - 1) * safeLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = summary(rs);
                        row.put("deleted_at", rs.getString("deleted_at"));
                        documents.add(row);
                    }
                }
            }
            return page(documents, total, safePage, safeLimit);
        } catch (SQLException error) {
            throw new IllegalStateException("查询回收站失败: " + error.getMessage(), error);
        }
    }

    // ─── 管理操作 ────────────────────────────────────────────

    /**
     * 移入回收站：只标记 deleted_at 并摘除检索索引，源文件保持不变。
     *
     * @param id 文档 id
     * @return status / message
     */
    public Map<String, Object> softDelete(long id) {
        return mutateDocument(id, """
                UPDATE documents SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL""",
                "文档已移入回收站", true);
    }

    /**
     * 从回收站恢复：清除标记并重建检索索引。
     *
     * @param id 文档 id
     * @return status / message
     */
    public Map<String, Object> restore(long id) {
        return mutateDocument(id, "UPDATE documents SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL",
                "文档已恢复", false);
    }

    /** 彻底删除：连同检索索引、标签关联与图谱关系一并清除。 */
    public Map<String, Object> purge(long id) {
        writeLock.lock();
        try (Connection conn = open()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM documents WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("文档不存在: #" + id);
                    }
                }
            }
            deleteFts(conn, id);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM document_tags WHERE document_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM doc_relations WHERE source_id = ? OR target_id = ?")) {
                ps.setLong(1, id);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM documents WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            conn.commit();
            return ok("文档已彻底删除");
        } catch (SQLException error) {
            throw new IllegalStateException("彻底删除失败: " + error.getMessage(), error);
        } finally {
            writeLock.unlock();
        }
    }

    /** 软删除/恢复共用的状态迁移：先校验当前状态，再更新并按需同步 FTS 索引。 */
    private Map<String, Object> mutateDocument(long id, String sql, String message, boolean removingFromIndex) {
        writeLock.lock();
        try (Connection conn = open()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (removingFromIndex) {
                    ps.setString(1, STAMP.format(java.time.LocalDateTime.now()));
                    ps.setLong(2, id);
                } else {
                    ps.setLong(1, id);
                }
                if (ps.executeUpdate() == 0) {
                    throw new IllegalArgumentException("文档状态不允许该操作: #" + id);
                }
            }
            if (removingFromIndex) {
                deleteFts(conn, id);
            } else {
                // 恢复时索引已被摘除，需要按最新正文重建
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT d.title, d.search_text, c.name AS category_name FROM documents d "
                                + "JOIN categories c ON d.category_id = c.id WHERE d.id = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            insertFts(conn, id, rs.getString("title"), rs.getString("search_text"),
                                    rs.getString("category_name"));
                        }
                    }
                }
            }
            conn.commit();
            return ok(message);
        } catch (SQLException error) {
            throw new IllegalStateException("更新文档状态失败: " + error.getMessage(), error);
        } finally {
            writeLock.unlock();
        }
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", message);
        return result;
    }

    // ─── 知识图谱 ────────────────────────────────────────────

    /**
     * 文档关系图谱：中心文档 + 一跳邻居。
     *
     * <p>优先读取预计算的 {@code doc_relations}；为空时按「同分类 + 标题关键词重叠」实时计算，
     * 因此首次访问无需先跑重建任务。</p>
     *
     * @param id 中心文档 id
     * @return center / nodes / edges
     */
    public Map<String, Object> graph(long id) {
        try (Connection conn = open()) {
            Map<String, Object> center = null;
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT d.id, d.title, c.name AS category_name, c.slug AS category_slug, c.icon
                    FROM documents d JOIN categories c ON d.category_id = c.id
                    WHERE d.id = ? AND d.deleted_at IS NULL""")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        center = new LinkedHashMap<>();
                        center.put("id", rs.getLong("id"));
                        center.put("title", rs.getString("title"));
                        center.put("category_name", rs.getString("category_name"));
                        center.put("category_slug", rs.getString("category_slug"));
                        center.put("icon", rs.getString("icon"));
                    }
                }
            }
            if (center == null) {
                throw new IllegalArgumentException("文档不存在或已删除: #" + id);
            }
            List<Map<String, Object>> relations = storedRelations(conn, id);
            if (relations.isEmpty()) {
                relations = computeRelations(conn, id);
            }
            Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
            nodes.put(id, center);
            List<Map<String, Object>> edges = new ArrayList<>();
            for (Map<String, Object> relation : relations) {
                long other = ((Number) relation.get("rel_doc_id")).longValue();
                nodes.computeIfAbsent(other, key -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", key);
                    node.put("title", relation.get("title"));
                    node.put("icon", relation.get("icon"));
                    node.put("category_slug", relation.get("category_slug"));
                    return node;
                });
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", relation.get("source_id"));
                edge.put("target", relation.get("target_id"));
                edge.put("type", relation.get("rel_type"));
                edge.put("weight", relation.get("weight"));
                edges.add(edge);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("center", center);
            result.put("nodes", new ArrayList<>(nodes.values()));
            result.put("edges", edges);
            return result;
        } catch (SQLException error) {
            throw new IllegalStateException("查询文档图谱失败: " + error.getMessage(), error);
        }
    }

    /** 读取预计算关系；每条关系只保留「另一端」的信息，中心文档自身不回填。 */
    private List<Map<String, Object>> storedRelations(Connection conn, long id) throws SQLException {
        List<Map<String, Object>> relations = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT r.source_id, r.target_id, r.rel_type, r.weight,
                       other.id AS rel_doc_id, other.title, c.icon, c.slug AS category_slug
                FROM doc_relations r
                JOIN documents other
                  ON other.id = CASE WHEN r.source_id = ? THEN r.target_id ELSE r.source_id END
                JOIN categories c ON other.category_id = c.id
                WHERE (r.source_id = ? OR r.target_id = ?)
                  AND other.deleted_at IS NULL
                ORDER BY r.weight DESC LIMIT 20""")) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            ps.setLong(3, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(relation(rs));
                }
            }
        }
        return relations;
    }

    /**
     * 实时计算文档关系：同分类最多 10 条（权重 0.6），
     * 标题关键词重叠度 > 0.3 记一条 similar，最终按权重取前 15。
     */
    private List<Map<String, Object>> computeRelations(Connection conn, long id) throws SQLException {
        String title;
        long categoryId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT title, category_id FROM documents WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return List.of();
                }
                title = rs.getString("title");
                categoryId = rs.getLong("category_id");
            }
        }
        List<Map<String, Object>> relations = new ArrayList<>();
        Set<Long> sameCategory = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT d.id, d.title, c.icon, c.slug AS category_slug
                FROM documents d JOIN categories c ON d.category_id = c.id
                WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL LIMIT 10""")) {
            ps.setLong(1, categoryId);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sameCategory.add(rs.getLong("id"));
                    relations.add(buildRelation(id, rs, "same_category", 0.6));
                }
            }
        }
        Set<String> keywords = titleKeywords(title);
        if (!keywords.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT d.id, d.title, c.icon, c.slug AS category_slug
                    FROM documents d JOIN categories c ON d.category_id = c.id
                    WHERE d.id != ? AND d.deleted_at IS NULL""")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long otherId = rs.getLong("id");
                        if (sameCategory.contains(otherId)) {
                            continue;
                        }
                        Set<String> otherKeywords = titleKeywords(rs.getString("title"));
                        if (otherKeywords.isEmpty()) {
                            continue;
                        }
                        Set<String> common = new HashSet<>(keywords);
                        common.retainAll(otherKeywords);
                        if (common.isEmpty()) {
                            continue;
                        }
                        double weight = Math.min((double) common.size()
                                / Math.max(keywords.size(), otherKeywords.size()), 1.0);
                        if (weight > 0.3) {
                            relations.add(buildRelation(id, rs, "similar", Math.round(weight * 100) / 100.0));
                        }
                    }
                }
            }
        }
        relations.sort(Comparator.comparingDouble((Map<String, Object> row) ->
                -((Number) row.get("weight")).doubleValue()));
        return relations.size() > 15 ? new ArrayList<>(relations.subList(0, 15)) : relations;
    }

    private Map<String, Object> buildRelation(long centerId, ResultSet rs, String type, double weight)
            throws SQLException {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("source_id", centerId);
        relation.put("target_id", rs.getLong("id"));
        relation.put("rel_type", type);
        relation.put("weight", weight);
        relation.put("rel_doc_id", rs.getLong("id"));
        relation.put("title", rs.getString("title"));
        relation.put("icon", rs.getString("icon"));
        relation.put("category_slug", rs.getString("category_slug"));
        return relation;
    }

    private Map<String, Object> relation(ResultSet rs) throws SQLException {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("source_id", rs.getLong("source_id"));
        relation.put("target_id", rs.getLong("target_id"));
        relation.put("rel_type", rs.getString("rel_type"));
        relation.put("weight", rs.getDouble("weight"));
        relation.put("rel_doc_id", rs.getLong("rel_doc_id"));
        relation.put("title", rs.getString("title"));
        relation.put("icon", rs.getString("icon"));
        relation.put("category_slug", rs.getString("category_slug"));
        return relation;
    }

    /**
     * 全量重建文档关系并落库，供后续查询直接命中索引表。
     *
     * @return status / relations_count
     */
    public Map<String, Object> graphRebuild() {
        writeLock.lock();
        try (Connection conn = open()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM doc_relations");
            }
            List<Long> ids = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT id FROM documents WHERE deleted_at IS NULL ORDER BY id")) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR IGNORE INTO doc_relations (source_id, target_id, rel_type, weight)
                    VALUES (?,?,?,?)""")) {
                for (Long id : ids) {
                    for (Map<String, Object> relation : computeRelations(conn, id)) {
                        long source = ((Number) relation.get("source_id")).longValue();
                        long target = ((Number) relation.get("target_id")).longValue();
                        ps.setLong(1, Math.min(source, target));
                        ps.setLong(2, Math.max(source, target));
                        ps.setString(3, String.valueOf(relation.get("rel_type")));
                        ps.setDouble(4, ((Number) relation.get("weight")).doubleValue());
                        ps.addBatch();
                        count++;
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("relations_count", count);
            log.info("[blog] 关系图谱重建完成，共 {} 条", count);
            return result;
        } catch (SQLException error) {
            throw new IllegalStateException("重建图谱失败: " + error.getMessage(), error);
        } finally {
            writeLock.unlock();
        }
    }

    // ─── 公共辅助 ────────────────────────────────────────────

    /**
     * 打开一个 SQLite 连接。
     *
     * <p>WAL 提升并发读性能，{@code busy_timeout} 让写冲突自旋等待而非直接报错。
     * 必须显式关闭 auto-commit：sqlite-jdbc 默认 auto-commit=true，此时调用
     * {@code commit()} 会抛「database in auto-commit mode」，使导入与删除等
     * 事务化写入全部失败。</p>
     */
    private Connection open() throws SQLException {
        Path dbPath = resolveDbPath();
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    /** 解析数据库绝对路径，并确保父目录存在（首次启动时 data/blog 可能还不存在）。 */
    private Path resolveDbPath() {
        Path path = Path.of(properties.getDbPath()).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException error) {
                throw new IllegalStateException("创建博客数据目录失败: " + parent, error);
            }
        }
        return path;
    }

    /** 解析源文档根目录，结果缓存，避免每次导入都做一次路径规范化。 */
    private Path resolveDocsRoot() {
        Path cached = docsRoot;
        if (cached == null) {
            cached = Path.of(properties.getDocsSrc()).toAbsolutePath().normalize();
            docsRoot = cached;
        }
        return cached;
    }

    /** 分类/全量列表的公共查询：字段与排序固定，只由调用方决定 where 与分页。 */
    private List<Map<String, Object>> querySummaries(Connection conn, String where, List<Object> params,
                                                    int page, int limit) throws SQLException {
        String sql = """
                SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                       c.name AS category_name, c.slug AS category_slug, c.icon
                FROM documents d JOIN categories c ON d.category_id = c.id""" + " " + where
                + " ORDER BY d.id DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(limit);
        queryParams.add((page - 1) * limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(summary(rs));
                }
            }
        }
        return rows;
    }

    /** 列表行统一字段：word_count 换算出的 read_time 由后端算好，避免前端重复约定。 */
    private Map<String, Object> summary(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("title", rs.getString("title"));
        row.put("filename", rs.getString("filename"));
        row.put("word_count", rs.getInt("word_count"));
        row.put("read_time", readTime(rs.getInt("word_count")));
        row.put("created_at", rs.getString("created_at"));
        row.put("updated_at", rs.getString("updated_at"));
        row.put("category_name", rs.getString("category_name"));
        row.put("category_slug", rs.getString("category_slug"));
        row.put("icon", rs.getString("icon"));
        return row;
    }

    /** 阅读时长按每分钟 300 字估算，至少 1 分钟。 */
    private static int readTime(int wordCount) {
        return Math.max(1, (int) Math.round(wordCount / 300.0));
    }

    private Map<String, Object> page(List<Map<String, Object>> documents, long total, int page, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", documents);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("pages", Math.max(1, (int) ((total + limit - 1) / limit)));
        return result;
    }

    private long count(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Number number) {
                ps.setLong(i + 1, number.longValue());
            } else {
                ps.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private static List<Object> prepend(Object head, List<Object> tail) {
        List<Object> merged = new ArrayList<>(tail.size() + 1);
        merged.add(head);
        merged.addAll(tail);
        return merged;
    }

    /** 清洗查询串：剔除 FTS5 语法字符，只保留词元字符与空白。 */
    static String cleanQuery(String query) {
        if (query == null) {
            return "";
        }
        return WHITESPACE.matcher(QUERY_NOISE.matcher(query).replaceAll(" ")).replaceAll(" ").strip();
    }

    /** 提取标题关键词，用于相似度比较。 */
    static Set<String> titleKeywords(String title) {
        Set<String> keywords = new LinkedHashSet<>();
        if (title == null) {
            return keywords;
        }
        Matcher matcher = TITLE_KEYWORDS.matcher(title.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            keywords.add(matcher.group());
        }
        return keywords;
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }
}
