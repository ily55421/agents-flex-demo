"""
DocBlog Android 服务端适配
- 基于 server.py，移除 Windows 依赖和文件扫描导入
- 数据库和静态文件从 assets 解压到内部存储后直接使用
- 由 MainActivity 通过 Chaquopy 调用 start_server() 启动
"""

import http.server
import json
import os
import re
import sqlite3
import sys
import urllib.parse
import html as html_mod
import logging
import time
import hashlib
import shutil
from pathlib import Path
from datetime import datetime

# ─── 全局变量 ───────────────────────────────────────────
BASE_DIR = None
DATA_DIR = None
DB_PATH = None
STATIC_DIR = None
PORT = 8080
HOST = "127.0.0.1"
_server_instance = None

# ─── 日志 ──────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger('docblog')

# ─── 参数校验 ──────────────────────────────────────────
def parse_int(value, default=1, min_val=1, max_val=100):
    try:
        num = int(value) if value is not None else default
    except (ValueError, TypeError):
        return default
    return max(min_val, min(max_val, num))

# ─── 性能监控 ──────────────────────────────────────────
_request_stats = {"count": 0, "total_time": 0.0, "paths": {}}

def record_request(path, elapsed):
    _request_stats["count"] += 1
    _request_stats["total_time"] += elapsed
    _request_stats["paths"][path] = _request_stats["paths"].get(path, 0) + 1

# ─── 分类规则 ──────────────────────────────────────────
CATEGORY_RULES = [
    ("ai",         "AI与机器学习",    "🤖", [
        "ai", "人工智能", "机器学习", "深度学习", "大模型", "llm", "gpt",
        "cursor", "视觉ai", "geo实战", "deepseek", "通用人工智能",
        "神经网络", "transformer", "自然语言", "nlp", "计算机视觉",
        "ai辅助编程", "不写代码", "2022十大科技趋势",
    ]),
    ("mobile",     "移动端开发",      "📱", [
        "android", "ios", "swift", "kotlin", "flutter", "react native",
        "rn ", "rn.", "uniapp", "uni-app", "小程序", "微信开发",
        "移动端", "app开发", "手机开发", "objective-c", "xcode",
        "selenium",
    ]),
    ("interview",  "面试与求职",       "🎯", [
        "面试题大全", "面试突击", "程序员代码面试指南",
        "java面试", "java基础面试", "面试题(java", "leetcode solutions",
        "leetcode 前", "算法题解析",
    ]),
    ("java",       "Java与JVM",       "☕", [
        "java", "jvm", "spring", "springboot", "springcloud",
        "maven", "gradle", "tomcat", "servlet", "hibernate", "mybatis",
        "netty", "dubbo", "log4j", "slf4j", "logback", "junit",
        "mockito", "swagger", "lombok", "jwt", "oauth", "jpa", "jdbc",
        "effective java", "java并发", "java编程思想", "java核心",
        "java基础", "java优化", "java性能", "java架构",
        "java数据结构和算法", "java机器学习", "java程序设计",
        "java课程", "java夜未眠", "码出高效", "阿里巴巴java",
        "java开发手册", "java面试", "java程序员", "教妹学",
        "java从小白", "javafx", "camel in action", "深入剖析tomcat",
        "深入理解java虚拟机", "实战java", "java高并发", "javaguide",
        "hutool", "guns", "rocketmq", "mafka", "hibernate validator",
        "selenium3",
    ]),
    ("database",   "数据库与存储",    "🗄️", [
        "mysql", "redis", "mongo", "neo4j", "sql", "数据库", "索引优化",
        "向量存储", "新型数据库", "elasticsearch", "clickhouse", "tidb",
        "数据库原理", "数据库技术",
    ]),
    ("bigdata",    "大数据与分布式",  "🌐", [
        "spark", "hadoop", "flink", "kafka", "rocketmq", "mafka",
        "微服务", "云原生", "kubernetes", "k8s", "docker", "容器",
        "springcloud", "spring cloud", "nacos", "sentinel", "seata",
        "knative", "serverless", "分布式", "大数据", "hive", "hbase",
        "zookeeper", "dubbo", "rpc", "消息队列", "中间件",
        "大型网站系统", "性能优化手册",
    ]),
    ("frontend",   "前端与Web",       "🎨", [
        "前端", "webpack", "vue", "react", "angular", "typescript",
        "javascript", "html", "css", "node", "jquery", "bootstrap",
        "ria", "web", "浏览器", "http", "https", "dns", "小程序",
    ]),
    ("arch",       "架构与设计",       "🏗️", [
        "架构", "设计模式", "重构", "领域驱动", "ddd", "微服务架构",
        "系统架构", "软件架构", "架构师", "架构决策", "研发管理",
        "端到端流程", "流程管理", "大话架构思维",
    ]),
    ("os",         "操作系统与底层",   "⚙️", [
        "操作系统", "linux", "windows", "编译原理", "编译器", "自制编译",
        "计算机组成", "计算机原理", "cpu", "内存管理", "进程", "线程底层",
        "鸟哥", "私房菜", "日志系统", "自制操作系统", "30天自制",
        "大话计算机",
    ]),
    ("philosophy", "哲学与人文",      "📖", [
        "哲学", "黑格尔", "老子", "孟子", "春秋", "资本论", "马克思",
        "贝克莱", "唯心", "唯物", "逻辑", "中国思想", "中国历代词",
        "柳如是", "文学", "历史", "文革", "神逻辑", "熊逸",
        "哲学大全", "小逻辑", "精神哲学", "高效阅读法", "数学思维",
    ]),
    ("management", "管理与经营",      "💼", [
        "稻盛", "京瓷", "活法", "干法", "经营", "阿米巴", "六项精进",
        "敬天爱人", "日航", "企业家", "创业", "管理", "华为", "熵减",
        "技术创新", "研发能力", "跨文化管理", "商道", "人生哲学",
        "你的梦想", "创造高收益", "经营十二条", "实学", "拯救人类",
        "今生无憾", "回归哲学", "在萧条中", "企业家成功",
    ]),
    ("algo",       "数学与算法",       "🔢", [
        "算法", "数据结构", "数学", "线性代数", "概率", "统计", "离散",
        "编程珠玑", "程序员数学", "算法面试", "分支限界", "动态规划",
        "贪心", "回溯", "排序", "查找", "图论", "树", "链表",
        "leetcode", "算法题", "十五个经典算法",
    ]),
    ("tools",      "工具与实践",       "🔧", [
        "git", "maven", "gradle", "vim", "ide", "cursor",
        "开发工具", "构建工具", "版本控制", "调试",
    ]),
    ("edu",        "教育与考试",       "🎓", [
        "北外", "课程", "考试", "系统分析师", "软考", "复习", "教程",
        "入门指南", "学习", "培训", "作业", "实验", "课程设计",
        "网站建设", "网站设计", "计算机图像", "大数据分析",
        "互联网软件", "管理信息系统", "软件工程",
    ]),
    ("network",    "网络与安全",       "🔒", [
        "网络", "tcp", "ip", "http", "安全", "加密", "防火墙", "ddos",
        "xss", "sql注入", "csrf", "漏洞", "渗透", "实名举报", "学术造假",
        "计算机网络",
    ]),
    ("other",      "其他",             "📁", []),
]

# ─── 数据库 ────────────────────────────────────────────
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn

def _column_exists(conn, table, column):
    rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
    return any(r['name'] == column for r in rows)

def init_db():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS categories (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            slug        TEXT UNIQUE NOT NULL,
            name        TEXT NOT NULL,
            icon        TEXT DEFAULT '📁',
            sort_order  INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS documents (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            category_id  INTEGER NOT NULL REFERENCES categories(id),
            filename     TEXT NOT NULL,
            title        TEXT NOT NULL,
            filepath     TEXT NOT NULL UNIQUE,
            content      TEXT NOT NULL,
            html_content TEXT NOT NULL,
            word_count   INTEGER DEFAULT 0,
            file_mtime   REAL DEFAULT 0,
            created_at   TEXT,
            updated_at   TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_doc_category ON documents(category_id);
        CREATE INDEX IF NOT EXISTS idx_doc_title    ON documents(title);
        CREATE INDEX IF NOT EXISTS idx_doc_mtime    ON documents(file_mtime);
        CREATE TABLE IF NOT EXISTS tags (
            id   INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            slug TEXT UNIQUE NOT NULL,
            color TEXT DEFAULT '#6c8cff'
        );
        CREATE TABLE IF NOT EXISTS document_tags (
            document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            tag_id      INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
            PRIMARY KEY (document_id, tag_id)
        );
        CREATE INDEX IF NOT EXISTS idx_doc_tags_doc ON document_tags(document_id);
        CREATE INDEX IF NOT EXISTS idx_doc_tags_tag ON document_tags(tag_id);
        CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts USING fts5(
            title, content, category_name, tokenize='trigram'
        );
        CREATE TABLE IF NOT EXISTS doc_relations (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id   INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            target_id   INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            rel_type    TEXT NOT NULL,
            weight      REAL DEFAULT 0.5,
            created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(source_id, target_id, rel_type)
        );
        CREATE INDEX IF NOT EXISTS idx_rel_source ON doc_relations(source_id);
        CREATE INDEX IF NOT EXISTS idx_rel_target ON doc_relations(target_id);
        CREATE INDEX IF NOT EXISTS idx_rel_type ON doc_relations(rel_type);
    """)
    if not _column_exists(conn, 'documents', 'deleted_at'):
        conn.execute("ALTER TABLE documents ADD COLUMN deleted_at TEXT DEFAULT NULL")
    conn.commit()
    conn.close()

def ensure_categories(conn):
    cat_ids = {}
    for i, (slug, name, icon, _) in enumerate(CATEGORY_RULES):
        row = conn.execute("SELECT id FROM categories WHERE slug = ?", (slug,)).fetchone()
        if row:
            cat_ids[slug] = row['id']
        else:
            cur = conn.execute(
                "INSERT INTO categories (slug, name, icon, sort_order) VALUES (?,?,?,?)",
                (slug, name, icon, i)
            )
            cat_ids[slug] = cur.lastrowid
    return cat_ids

def fts_index_doc(conn, doc_id, title, content, category_name):
    conn.execute(
        "INSERT OR REPLACE INTO docs_fts(rowid, title, content, category_name) VALUES (?,?,?,?)",
        (doc_id, title, content, category_name)
    )

def fts_delete_doc(conn, doc_id):
    conn.execute("DELETE FROM docs_fts WHERE rowid = ?", (doc_id,))

# ─── Markdown → HTML ──────────────────────────────────
def md_to_html(text):
    lines = text.split('\n')
    html_lines = []
    in_code = False
    in_table = False
    in_ul = False
    in_ol = False
    code_buf = []

    def close_lists():
        nonlocal in_ul, in_ol
        if in_ul:
            html_lines.append('</ul>')
            in_ul = False
        if in_ol:
            html_lines.append('</ol>')
            in_ol = False

    code_lang = ''

    for line in lines:
        stripped = line.strip()

        if stripped.startswith('```'):
            close_lists()
            if in_code:
                code_buf.append(html_mod.escape(line))
                safe_lang = re.sub(r'[^a-zA-Z0-9_\-]', '', code_lang)[:20]
                lang_attr = f' class="language-{safe_lang}"' if safe_lang else ''
                html_lines.append(f'<pre><code{lang_attr}>' + '\n'.join(code_buf) + '</code></pre>')
                code_buf = []
                code_lang = ''
                in_code = False
            else:
                in_code = True
                code_lang = stripped[3:].strip()
            continue
        if in_code:
            code_buf.append(html_mod.escape(line))
            continue

        if '|' in stripped and stripped.startswith('|'):
            close_lists()
            cells = [c.strip() for c in stripped.strip('|').split('|')]
            if all(re.match(r'^[-:]+$', c.replace(' ', '')) for c in cells if c):
                continue
            if not in_table:
                html_lines.append('<table><thead><tr>')
                for c in cells:
                    html_lines.append(f'<th>{html_mod.escape(c)}</th>')
                html_lines.append('</tr></thead><tbody>')
                in_table = True
            else:
                html_lines.append('<tr>')
                for c in cells:
                    html_lines.append(f'<td>{html_mod.escape(c)}</td>')
                html_lines.append('</tr>')
            continue
        else:
            if in_table:
                html_lines.append('</tbody></table>')
                in_table = False

        if stripped.startswith('#'):
            close_lists()
            m = re.match(r'^(#{1,6})\s+(.*)', stripped)
            if m:
                level = len(m.group(1))
                t = m.group(2)
                anchor = re.sub(r'[^\w一-鿿]+', '-', t.lower()).strip('-')
                html_lines.append(f'<h{level} id="{anchor}">{inline_html(t)}</h{level}>')
                continue

        m_ul = re.match(r'^[-*+]\s+(.*)', stripped)
        if m_ul:
            if not in_ul:
                html_lines.append('<ul>')
                in_ul = True
            html_lines.append(f'<li>{inline_html(m_ul.group(1))}</li>')
            continue
        elif in_ul:
            html_lines.append('</ul>')
            in_ul = False

        m_ol = re.match(r'^\d+\.\s+(.*)', stripped)
        if m_ol:
            if not in_ol:
                html_lines.append('<ol>')
                in_ol = True
            html_lines.append(f'<li>{inline_html(m_ol.group(1))}</li>')
            continue
        elif in_ol:
            html_lines.append('</ol>')
            in_ol = False

        if stripped.startswith('>'):
            html_lines.append(f'<blockquote>{inline_html(stripped.lstrip("> "))}</blockquote>')
            continue

        if re.match(r'^[-*_]{3,}$', stripped):
            html_lines.append('<hr/>')
            continue

        if not stripped:
            html_lines.append('')
            continue

        html_lines.append(f'<p>{inline_html(stripped)}</p>')

    if in_table:
        html_lines.append('</tbody></table>')
    close_lists()
    return '\n'.join(html_lines)

def _sanitize_url(url):
    if not url:
        return ''
    url = url.strip()
    lower = url.lower()
    if url.startswith('/') or url.startswith('#') or url.startswith('./'):
        return url
    if lower.startswith('http://') or lower.startswith('https://'):
        return url
    if re.match(r'^[a-z][a-z0-9+.-]*:', lower):
        return '#blocked'
    return url

def inline_html(text):
    text = html_mod.escape(text)
    def img_repl(m):
        alt = m.group(1)
        src = _sanitize_url(m.group(2))
        return f'<img src="{src}" alt="{alt}" />'
    text = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', img_repl, text)
    def link_repl(m):
        label = m.group(1)
        href = _sanitize_url(m.group(2))
        return f'<a href="{href}">{label}</a>'
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link_repl, text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'\*(.+?)\*', r'<em>\1</em>', text)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    return text

# ─── HTTP 请求处理器 ────────────────────────────────────
class DocHandler(http.server.SimpleHTTPRequestHandler):

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC_DIR), **kwargs)

    def do_GET(self):
        start_time = time.time()
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/') or '/'
        qs = urllib.parse.parse_qs(parsed.query)

        if path.startswith('/css/') or path.startswith('/js/') or path.startswith('/images/'):
            return self._serve_static(path)

        if path == '/':
            self.path = '/index.html'
            return self._serve_static('/index.html')

        try:
            if path == '/api/categories':
                return self._json(self._api_categories())
            if path == '/api/documents':
                cat = qs.get('category', [None])[0]
                page = parse_int(qs.get('page', [1])[0], 1, 1, 100)
                limit = parse_int(qs.get('limit', [20])[0], 20, 1, 100)
                return self._json(self._api_documents(cat, page, limit))
            if path == '/api/search':
                q = qs.get('q', [''])[0]
                page = parse_int(qs.get('page', [1])[0], 1, 1, 100)
                limit = parse_int(qs.get('limit', [20])[0], 20, 1, 100)
                category = qs.get('category', [None])[0]
                return self._json(self._api_search(q, page, limit, category))
            if path == '/api/search-suggest':
                q = qs.get('q', [''])[0]
                limit_s = parse_int(qs.get('limit', [8])[0], 8, 1, 20)
                return self._json(self._api_search_suggest(q, limit_s))
            if path == '/api/document':
                doc_id = qs.get('id', [None])[0]
                return self._json(self._api_document(doc_id))
            if path == '/api/related':
                doc_id = qs.get('id', [None])[0]
                limit_r = parse_int(qs.get('limit', [5])[0], 5, 1, 50)
                return self._json(self._api_related(doc_id, limit_r))
            if path == '/api/stats':
                return self._json(self._api_stats())
            if path == '/api/health':
                return self._json(self._api_health())
            if path == '/api/graph':
                doc_id = qs.get('id', [None])[0]
                depth = parse_int(qs.get('depth', [1])[0], 1, 1, 3)
                return self._json(self._api_graph(doc_id, depth))
            if path == '/api/tags':
                return self._json(self._api_tags())
            if path == '/api/tag':
                tag_slug = qs.get('slug', [None])[0]
                page = parse_int(qs.get('page', [1])[0], 1, 1, 100)
                limit = parse_int(qs.get('limit', [20])[0], 20, 1, 100)
                return self._json(self._api_tag_documents(tag_slug, page, limit))
            if path == '/api/document-tags':
                doc_id = qs.get('id', [None])[0]
                return self._json(self._api_document_tags(doc_id))
            if path == '/api/trash':
                page = parse_int(qs.get('page', [1])[0], 1, 1, 100)
                limit = parse_int(qs.get('limit', [20])[0], 20, 1, 100)
                return self._json(self._api_trash(page, limit))
            return self._serve_static(path)
        finally:
            elapsed = time.time() - start_time
            record_request(path, elapsed)

    def _serve_static(self, path):
        decoded = urllib.parse.unquote(path)
        safe_path = (STATIC_DIR / decoded.lstrip('/')).resolve()
        static_root = STATIC_DIR.resolve()
        try:
            safe_path.relative_to(static_root)
        except ValueError:
            self.send_error(403, "Forbidden")
            return
        file_path = safe_path
        if not file_path.exists() or not file_path.is_file():
            self.send_error(404)
            return
        ext = file_path.suffix.lower()
        content_types = {
            '.html': 'text/html; charset=utf-8',
            '.css': 'text/css; charset=utf-8',
            '.js': 'application/javascript; charset=utf-8',
            '.json': 'application/json; charset=utf-8',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.jpeg': 'image/jpeg',
            '.gif': 'image/gif',
            '.svg': 'image/svg+xml',
            '.ico': 'image/x-icon',
        }
        content_type = content_types.get(ext, 'application/octet-stream')
        try:
            content = file_path.read_bytes()
        except Exception:
            self.send_error(500)
            return
        etag = hashlib.md5(content).hexdigest()[:16]
        if_none_match = self.headers.get('If-None-Match')
        if if_none_match and if_none_match == etag:
            self.send_response(304)
            self.end_headers()
            return
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', len(content))
        self.send_header('ETag', etag)
        if ext in ('.css', '.js'):
            self.send_header('Cache-Control', 'public, max-age=86400')
        else:
            self.send_header('Cache-Control', 'public, max-age=3600')
        self.end_headers()
        self.wfile.write(content)

    def _json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False, default=str).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', len(body))
        self.end_headers()
        self.wfile.write(body)

    # ─── API 实现 ──────────────────────────────────

    def _api_categories(self):
        conn = get_db()
        cats = conn.execute("""
            SELECT c.id, c.slug, c.name, c.icon, COUNT(d.id) as doc_count
            FROM categories c
            LEFT JOIN documents d ON d.category_id = c.id AND d.deleted_at IS NULL
            GROUP BY c.id ORDER BY c.sort_order
        """).fetchall()
        conn.close()
        return {"categories": [dict(r) for r in cats]}

    def _api_documents(self, category_slug=None, page=1, limit=20):
        conn = get_db()
        offset = (page - 1) * limit
        where, params = "WHERE d.deleted_at IS NULL", []
        if category_slug:
            where += " AND c.slug = ?"
            params.append(category_slug)
        total = conn.execute(
            f"SELECT COUNT(*) FROM documents d JOIN categories c ON d.category_id = c.id {where}", params
        ).fetchone()[0]
        docs = conn.execute(f"""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            {where} ORDER BY d.id DESC LIMIT ? OFFSET ?
        """, params + [limit, offset]).fetchall()
        conn.close()
        results = []
        for r in docs:
            d = dict(r)
            d['read_time'] = max(1, round(d.get('word_count', 0) / 300))
            results.append(d)
        return {"documents": results, "total": total,
                "page": page, "limit": limit, "pages": max(1, (total + limit - 1) // limit)}

    def _api_search(self, query, page=1, limit=20, category=None):
        conn = get_db()
        offset = (page - 1) * limit
        if not query.strip():
            conn.close()
            return {"documents": [], "total": 0, "page": page,
                    "limit": limit, "pages": 0, "query": query}
        clean_q = re.sub(r'[^\w一-鿿\s]', ' ', query).strip()
        if not clean_q:
            conn.close()
            return {"documents": [], "total": 0, "page": page,
                    "limit": limit, "pages": 0, "query": query}
        cat_filter = " AND d.deleted_at IS NULL"
        cat_params = []
        if category:
            cat_filter += " AND c.slug = ?"
            cat_params.append(category)
        try:
            total = conn.execute(
                f"SELECT COUNT(*) FROM docs_fts JOIN documents d ON d.id = docs_fts.rowid JOIN categories c ON d.category_id = c.id WHERE docs_fts MATCH ?{cat_filter}",
                (clean_q,) + tuple(cat_params)
            ).fetchone()[0]
            rows = conn.execute(f"""
                SELECT d.id, d.title, d.filename, d.content, d.word_count,
                       d.created_at, d.updated_at,
                       c.name as category_name, c.slug as category_slug, c.icon, rank
                FROM docs_fts JOIN documents d ON d.id = docs_fts.rowid
                JOIN categories c ON d.category_id = c.id
                WHERE docs_fts MATCH ?{cat_filter}
                ORDER BY rank LIMIT ? OFFSET ?
            """, (clean_q,) + tuple(cat_params) + (limit, offset)).fetchall()
        except sqlite3.OperationalError:
            like_q = f'%{clean_q}%'
            total = conn.execute(
                f"SELECT COUNT(*) FROM documents d JOIN categories c ON d.category_id = c.id WHERE d.deleted_at IS NULL AND (d.title LIKE ? OR d.content LIKE ?){cat_filter}",
                (like_q, like_q) + tuple(cat_params)
            ).fetchone()[0]
            rows = conn.execute(f"""
                SELECT d.id, d.title, d.filename, d.content, d.word_count,
                       d.created_at, d.updated_at,
                       c.name as category_name, c.slug as category_slug, c.icon, 0 as rank
                FROM documents d JOIN categories c ON d.category_id = c.id
                WHERE d.deleted_at IS NULL AND (d.title LIKE ? OR d.content LIKE ?){cat_filter}
                ORDER BY d.id DESC LIMIT ? OFFSET ?
            """, (like_q, like_q) + tuple(cat_params) + (limit, offset)).fetchall()
        conn.close()
        results = []
        for r in rows:
            d = dict(r)
            d['read_time'] = max(1, round(d.get('word_count', 0) / 300))
            d['snippet'] = self._make_snippet(d['content'], clean_q.split())
            del d['content']
            results.append(d)
        return {"documents": results, "total": total, "page": page,
                "limit": limit, "pages": max(1, (total + limit - 1) // limit), "query": query}

    def _api_search_suggest(self, query, limit=8):
        if not query or not query.strip():
            return {"suggestions": []}
        conn = get_db()
        clean_q = re.sub(r'[^\w一-鿿\s]', ' ', query).strip()
        if not clean_q:
            conn.close()
            return {"suggestions": []}
        like_q = f'%{clean_q}%'
        rows = []
        can_use_fts = len(clean_q) >= 3
        if can_use_fts:
            try:
                rows = conn.execute("""
                    SELECT d.id, d.title, c.name as category_name,
                           c.slug as category_slug, c.icon,
                           CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END as title_match
                    FROM docs_fts JOIN documents d ON d.id = docs_fts.rowid
                    JOIN categories c ON d.category_id = c.id
                    WHERE docs_fts MATCH ? ORDER BY title_match ASC, rank LIMIT ?
                """, (like_q, clean_q, limit)).fetchall()
            except sqlite3.OperationalError:
                rows = []
        if not rows:
            rows = conn.execute("""
                SELECT d.id, d.title, c.name as category_name,
                       c.slug as category_slug, c.icon,
                       CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END as title_match
                FROM documents d JOIN categories c ON d.category_id = c.id
                WHERE d.deleted_at IS NULL AND (d.title LIKE ? OR d.content LIKE ?)
                ORDER BY title_match ASC, d.id DESC LIMIT ?
            """, (like_q, like_q, like_q, limit)).fetchall()
        conn.close()
        return {"suggestions": [dict(r) for r in rows]}

    def _api_related(self, doc_id, limit=5):
        conn = get_db()
        doc = conn.execute("""
            SELECT d.title, d.content, d.category_id, c.name as category_name
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.id = ? AND d.deleted_at IS NULL
        """, (doc_id,)).fetchone()
        if not doc:
            conn.close()
            return {"documents": []}
        title = doc['title']
        category_id = doc['category_id']
        keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}|[a-zA-Z]+', title.lower()))
        rows = conn.execute("""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL
            ORDER BY d.id DESC LIMIT 50
        """, (category_id, doc_id)).fetchall()
        conn.close()
        scored = []
        for r in rows:
            other_title = r['title'].lower()
            other_keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}|[a-zA-Z]+', other_title))
            overlap = len(keywords & other_keywords)
            if overlap > 0 or len(keywords) == 0:
                scored.append((overlap, r))
        scored.sort(key=lambda x: (-x[0], -x[1]['id']))
        results = []
        for _, r in scored[:limit]:
            d = dict(r)
            d['read_time'] = max(1, round(d.get('word_count', 0) / 300))
            results.append(d)
        return {"documents": results}

    def _make_snippet(self, content, keywords, max_len=200):
        if not content:
            return ""
        best_pos = -1
        content_lower = content.lower()
        for kw in keywords:
            pos = content_lower.find(kw.lower())
            if pos >= 0:
                best_pos = pos
                break
        if best_pos < 0:
            best_pos = 0
        start = max(0, best_pos - 60)
        end = min(len(content), start + max_len)
        snippet = content[start:end]
        if start > 0:
            snippet = '...' + snippet
        if end < len(content):
            snippet = snippet + '...'
        return snippet

    def _api_document(self, doc_id):
        conn = get_db()
        doc = conn.execute("""
            SELECT d.*, c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.id = ? AND d.deleted_at IS NULL
        """, (doc_id,)).fetchone()
        conn.close()
        if not doc:
            return {"error": "not found"}
        doc = dict(doc)
        doc['read_time'] = max(1, round(doc.get('word_count', 0) / 300))
        return {"document": doc}

    def _api_stats(self):
        conn = get_db()
        total_docs = conn.execute("SELECT COUNT(*) FROM documents WHERE deleted_at IS NULL").fetchone()[0]
        total_cats = conn.execute("SELECT COUNT(*) FROM categories").fetchone()[0]
        total_words = conn.execute("SELECT COALESCE(SUM(word_count),0) FROM documents WHERE deleted_at IS NULL").fetchone()[0]
        trashed = conn.execute("SELECT COUNT(*) FROM documents WHERE deleted_at IS NOT NULL").fetchone()[0]
        conn.close()
        return {"total_documents": total_docs, "total_categories": total_cats,
                "total_words": total_words, "trashed": trashed}

    def _api_health(self):
        try:
            conn = get_db()
            conn.execute("SELECT 1").fetchone()
            conn.close()
            db_status = "ok"
        except Exception as e:
            db_status = f"error: {e}"
        return {"status": "ok", "database": db_status,
                "uptime": _request_stats["count"],
                "avg_response_ms": round((_request_stats["total_time"] / max(_request_stats["count"], 1)) * 1000, 2)}

    def _api_tags(self):
        conn = get_db()
        rows = conn.execute("""
            SELECT t.id, t.name, t.slug, t.color, COUNT(dt.document_id) as doc_count
            FROM tags t LEFT JOIN document_tags dt ON dt.tag_id = t.id
            GROUP BY t.id ORDER BY doc_count DESC, t.name
        """).fetchall()
        conn.close()
        return {"tags": [dict(r) for r in rows]}

    def _api_tag_documents(self, tag_slug, page=1, limit=20):
        if not tag_slug:
            return {"documents": [], "total": 0, "page": page, "limit": limit, "pages": 0}
        conn = get_db()
        offset = (page - 1) * limit
        total = conn.execute("""
            SELECT COUNT(*) FROM document_tags dt
            JOIN tags t ON dt.tag_id = t.id JOIN documents d ON dt.document_id = d.id
            WHERE t.slug = ? AND d.deleted_at IS NULL
        """, (tag_slug,)).fetchone()[0]
        rows = conn.execute("""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM document_tags dt JOIN tags t ON dt.tag_id = t.id
            JOIN documents d ON dt.document_id = d.id
            JOIN categories c ON d.category_id = c.id
            WHERE t.slug = ? AND d.deleted_at IS NULL
            ORDER BY d.id DESC LIMIT ? OFFSET ?
        """, (tag_slug, limit, offset)).fetchall()
        conn.close()
        results = []
        for r in rows:
            d = dict(r)
            d['read_time'] = max(1, round(d.get('word_count', 0) / 300))
            results.append(d)
        return {"documents": results, "total": total,
                "page": page, "limit": limit, "pages": max(1, (total + limit - 1) // limit)}

    def _api_document_tags(self, doc_id):
        conn = get_db()
        rows = conn.execute("""
            SELECT t.id, t.name, t.slug, t.color
            FROM tags t JOIN document_tags dt ON dt.tag_id = t.id
            WHERE dt.document_id = ?
        """, (doc_id,)).fetchall()
        conn.close()
        return {"tags": [dict(r) for r in rows]}

    def _api_trash(self, page=1, limit=20):
        conn = get_db()
        offset = (page - 1) * limit
        total = conn.execute("SELECT COUNT(*) FROM documents WHERE deleted_at IS NOT NULL").fetchone()[0]
        rows = conn.execute("""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at, d.deleted_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.deleted_at IS NOT NULL
            ORDER BY d.deleted_at DESC LIMIT ? OFFSET ?
        """, (limit, offset)).fetchall()
        conn.close()
        results = []
        for r in rows:
            d = dict(r)
            d['read_time'] = max(1, round(d.get('word_count', 0) / 300))
            results.append(d)
        return {"documents": results, "total": total,
                "page": page, "limit": limit, "pages": max(1, (total + limit - 1) // limit)}

    def _api_graph(self, doc_id, depth=1):
        conn = get_db()
        center = conn.execute("""
            SELECT d.id, d.title, c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.id = ? AND d.deleted_at IS NULL
        """, (doc_id,)).fetchone()
        if not center:
            conn.close()
            return {"error": "文档不存在"}
        relations = conn.execute("""
            SELECT r.source_id, r.target_id, r.rel_type, r.weight,
                   d.title, d.id as rel_doc_id, c.icon, c.slug as category_slug
            FROM doc_relations r
            JOIN documents d ON (d.id = r.target_id OR d.id = r.source_id)
            JOIN categories c ON d.category_id = c.id
            WHERE (r.source_id = ? OR r.target_id = ?) AND d.id != ? AND d.deleted_at IS NULL
            ORDER BY r.weight DESC LIMIT 20
        """, (doc_id, doc_id, doc_id)).fetchall()
        if not relations:
            relations = self._compute_doc_relations(conn, doc_id)
        nodes = {doc_id: dict(center)}
        edges = []
        for rel in relations:
            rel_doc_id = rel['rel_doc_id']
            if rel_doc_id not in nodes:
                nodes[rel_doc_id] = {
                    'id': rel_doc_id, 'title': rel['title'],
                    'icon': rel['icon'], 'category_slug': rel['category_slug']
                }
            edges.append({
                'source': rel['source_id'], 'target': rel['target_id'],
                'type': rel['rel_type'], 'weight': rel['weight']
            })
        conn.close()
        return {"center": dict(center), "nodes": list(nodes.values()), "edges": edges}

    def _compute_doc_relations(self, conn, doc_id):
        doc = conn.execute("SELECT id, title, content, category_id FROM documents WHERE id = ?", (doc_id,)).fetchone()
        if not doc:
            return []
        relations = []
        same_cat = conn.execute("""
            SELECT d.id, d.title, c.icon, c.slug as category_slug
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL LIMIT 10
        """, (doc['category_id'], doc_id)).fetchall()
        for r in same_cat:
            relations.append({
                'source_id': doc_id, 'target_id': r['id'],
                'rel_type': 'same_category', 'weight': 0.6,
                'rel_doc_id': r['id'], 'title': r['title'],
                'icon': r['icon'], 'category_slug': r['category_slug']
            })
        keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}', doc['title']))
        if keywords:
            all_docs = conn.execute("""
                SELECT d.id, d.title, c.icon, c.slug as category_slug
                FROM documents d JOIN categories c ON d.category_id = c.id
                WHERE d.id != ? AND d.deleted_at IS NULL
            """, (doc_id,)).fetchall()
            for other in all_docs:
                other_keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}', other['title']))
                if other_keywords:
                    common = keywords & other_keywords
                    if common:
                        weight = min(len(common) / max(len(keywords), len(other_keywords)), 1.0)
                        if weight > 0.3:
                            relations.append({
                                'source_id': doc_id, 'target_id': other['id'],
                                'rel_type': 'similar', 'weight': round(weight, 2),
                                'rel_doc_id': other['id'], 'title': other['title'],
                                'icon': other['icon'], 'category_slug': other['category_slug']
                            })
        relations.sort(key=lambda x: x['weight'], reverse=True)
        return relations[:15]

    def do_POST(self):
        start_time = time.time()
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/') or '/'
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            try:
                data = json.loads(body) if body else {}
            except json.JSONDecodeError:
                data = {}
            if path == '/api/log':
                logger.error(f"[frontend] {data.get('type')} error: {data.get('message')}")
                return self._json({"status": "ok"})
            return self._json({"error": "not found"}, status=404)
        except Exception as e:
            logger.error(f"[POST] Error: {e}")
            return self._json({"error": str(e)}, status=500)
        finally:
            elapsed = time.time() - start_time
            record_request(path, elapsed)

    def log_message(self, fmt, *args):
        logger.debug(fmt % args)


# ─── Android 入口 ──────────────────────────────────────
def start_server(data_dir_path, port=8080):
    """由 MainActivity 调用，在后台线程启动 HTTP 服务器"""
    global BASE_DIR, DATA_DIR, DB_PATH, STATIC_DIR, PORT, HOST, _server_instance

    BASE_DIR = Path(data_dir_path)
    DATA_DIR = BASE_DIR / "data"
    DB_PATH = DATA_DIR / "docs.db"
    STATIC_DIR = BASE_DIR / "static"
    PORT = port
    HOST = "127.0.0.1"

    DATA_DIR.mkdir(parents=True, exist_ok=True)

    logger.info(f"[android] 数据目录: {BASE_DIR}")
    logger.info(f"[android] 数据库: {DB_PATH}")
    logger.info(f"[android] 静态文件: {STATIC_DIR}")

    init_db()
    conn = get_db()
    try:
        ensure_categories(conn)
        conn.commit()
    finally:
        conn.close()

    _server_instance = http.server.HTTPServer((HOST, PORT), DocHandler)
    logger.info(f"[android] 服务已启动: http://{HOST}:{PORT}")
    _server_instance.serve_forever()


def stop_server():
    """停止服务器"""
    global _server_instance
    if _server_instance:
        _server_instance.shutdown()
        _server_instance = None
        logger.info("[android] 服务已停止")
