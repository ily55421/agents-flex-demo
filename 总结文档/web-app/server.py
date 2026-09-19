#!/usr/bin/env python3
"""
文档博客 Web 服务
- SQLite FTS5 全文检索（trigram tokenizer）
- 增量导入：通过 filepath + mtime 判断新增/变更
- 智能分类：文件名关键词 + 内容关键词双重判断
- 静态文件服务 + REST API
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
from pathlib import Path
from datetime import datetime

# Windows 控制台 UTF-8 修复
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

# ─── 配置外置化 ───────────────────────────────────────────
BASE_DIR = Path(__file__).parent

DEFAULT_CONFIG = {
    "port": 8080,
    "host": "127.0.0.1",
    "db_path": "data/docs.db",
    "docs_src": "../",
    "page_limit": 20,
    "theme_default": "dark",
    "enable_suggest": True,
    "enable_highlight": True,
    "log_level": "INFO",
    "hot_reload": False,
    "watch_paths": ["server.py"],
    "admin_token": None,
    "cors_origin": None
}

def load_config():
    """加载配置文件，不存在则使用默认值"""
    config_path = BASE_DIR / "config.json"
    config = dict(DEFAULT_CONFIG)
    if config_path.exists():
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
            config.update(user_config)
            print(f"[config] 已加载配置文件: {config_path}")
        except Exception as e:
            print(f"[config] 配置文件读取失败，使用默认配置: {e}")
    else:
        print(f"[config] 配置文件不存在，使用默认配置")
    return config

CONFIG = load_config()

def update_config(updates):
    """动态更新配置（用于桌面应用等场景）"""
    global CONFIG, DATA_DIR, DB_PATH, DOCS_SRC, DOCS_CATEGORIES_DIR, PORT, HOST, ADMIN_TOKEN, CORS_ORIGIN

    CONFIG.update(updates)

    # 重新解析路径配置（支持绝对路径和相对路径）
    docs_src = CONFIG.get("docs_src", "../")
    DOCS_SRC = Path(docs_src).resolve() if not Path(docs_src).is_absolute() else Path(docs_src)

    db_path_val = CONFIG.get("db_path", "data/docs.db")
    DB_PATH = Path(db_path_val).resolve() if not Path(db_path_val).is_absolute() else Path(db_path_val)

    DOCS_CATEGORIES_DIR = DOCS_SRC / "docs"
    DATA_DIR = DB_PATH.parent
    PORT = int(CONFIG.get("port", 8080))
    HOST = CONFIG.get("host", "127.0.0.1")
    ADMIN_TOKEN = CONFIG.get("admin_token")
    CORS_ORIGIN = CONFIG.get("cors_origin")

    print(f"[config] 已更新配置: docs_src={DOCS_SRC}, db_path={DB_PATH}")

# 解析路径配置
DATA_DIR = BASE_DIR / "data"
DB_PATH = BASE_DIR / CONFIG.get("db_path", "data/docs.db")
DOCS_SRC = (BASE_DIR / CONFIG.get("docs_src", "../")).resolve()
DOCS_CATEGORIES_DIR = DOCS_SRC / "docs"
PORT = int(CONFIG.get("port", 8080))
HOST = CONFIG.get("host", "127.0.0.1")
ADMIN_TOKEN = CONFIG.get("admin_token")
CORS_ORIGIN = CONFIG.get("cors_origin")

# ─── 日志系统 ─────────────────────────────────────────────
DATA_DIR.mkdir(parents=True, exist_ok=True)
LOG_PATH = DATA_DIR / "server.log"
LOG_LEVEL = getattr(logging, CONFIG.get("log_level", "INFO").upper(), logging.INFO)
logging.basicConfig(
    level=LOG_LEVEL,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler(LOG_PATH, encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger('docblog')

# ─── 参数校验工具 ─────────────────────────────────────────
def parse_int(value, default=1, min_val=1, max_val=100):
    """安全解析整数，限制范围"""
    try:
        num = int(value) if value is not None else default
    except (ValueError, TypeError):
        return default
    return max(min_val, min(max_val, num))

# ─── 性能监控 ─────────────────────────────────────────────
_request_stats = {"count": 0, "total_time": 0.0, "paths": {}}

def record_request(path, elapsed):
    _request_stats["count"] += 1
    _request_stats["total_time"] += elapsed
    _request_stats["paths"][path] = _request_stats["paths"].get(path, 0) + 1

# ─── 分类规则 ─────────────────────────────────────────────
# 顺序越靠前优先级越高，匹配到即停止。"other" 兜底。
# 每个规则: (slug, 显示名, icon, [关键词列表])
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
        "selenium",  # 移动端/WEB自动化测试
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
    ("other",      "其他",             "📁", []),  # 兜底，必须放最后
]

# ─── 数据库初始化（幂等，不删除已有数据）──────────────────────

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn

def _column_exists(conn, table, column):
    """检查表中是否存在指定列"""
    rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
    return any(r['name'] == column for r in rows)

def init_db():
    """建表（IF NOT EXISTS），不删除已有数据，支持增量"""
    DATA_DIR.mkdir(exist_ok=True)
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

        -- 标签系统
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

        -- FTS5 全文检索虚拟表（trigram 支持中文子串匹配）
        CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts USING fts5(
            title,
            content,
            category_name,
            tokenize='trigram'
        );

        -- 文档关系图谱（知识图谱）
        CREATE TABLE IF NOT EXISTS doc_relations (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id   INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            target_id   INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            rel_type    TEXT NOT NULL,  -- 'similar':相似, 'reference':引用, 'same_category':同分类, 'shared_tag':共享标签
            weight      REAL DEFAULT 0.5,  -- 关系权重 0-1
            created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(source_id, target_id, rel_type)
        );
        CREATE INDEX IF NOT EXISTS idx_rel_source ON doc_relations(source_id);
        CREATE INDEX IF NOT EXISTS idx_rel_target ON doc_relations(target_id);
        CREATE INDEX IF NOT EXISTS idx_rel_type ON doc_relations(rel_type);
    """)

    # 回收站：软删除标记（兼容旧数据库，幂等添加）
    if not _column_exists(conn, 'documents', 'deleted_at'):
        conn.execute("ALTER TABLE documents ADD COLUMN deleted_at TEXT DEFAULT NULL")

    conn.commit()
    conn.close()

def ensure_categories(conn):
    """确保所有分类规则都在数据库中（幂等 upsert）"""
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

# ─── FTS5 辅助 ─────────────────────────────────────────────

def fts_index_doc(conn, doc_id, title, content, category_name):
    conn.execute(
        "INSERT OR REPLACE INTO docs_fts(rowid, title, content, category_name) VALUES (?,?,?,?)",
        (doc_id, title, content, category_name)
    )

def fts_delete_doc(conn, doc_id):
    conn.execute("DELETE FROM docs_fts WHERE rowid = ?", (doc_id,))

# ─── Markdown → HTML 简易转换 ──────────────────────────────

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

        # 代码块
        if stripped.startswith('```'):
            close_lists()
            if in_code:
                code_buf.append(html_mod.escape(line))
                # 清洗语言名：只允许字母、数字、短横线、下划线
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

        # 表格（安全渲染）
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

        # 标题
        if stripped.startswith('#'):
            close_lists()
            m = re.match(r'^(#{1,6})\s+(.*)', stripped)
            if m:
                level = len(m.group(1))
                t = m.group(2)
                anchor = re.sub(r'[^\w一-鿿]+', '-', t.lower()).strip('-')
                html_lines.append(f'<h{level} id="{anchor}">{inline_html(t)}</h{level}>')
                continue

        # 无序列表
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

        # 有序列表
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

        # 引用
        if stripped.startswith('>'):
            html_lines.append(f'<blockquote>{inline_html(stripped.lstrip("> "))}</blockquote>')
            continue

        # 分隔线
        if re.match(r'^[-*_]{3,}$', stripped):
            html_lines.append('<hr/>')
            continue

        # 空行
        if not stripped:
            html_lines.append('')
            continue

        # 普通段落
        html_lines.append(f'<p>{inline_html(stripped)}</p>')

    if in_table:
        html_lines.append('</tbody></table>')
    close_lists()
    return '\n'.join(html_lines)

def _sanitize_url(url):
    """链接协议白名单：只允许 http/https/相对路径/锚点"""
    if not url:
        return ''
    url = url.strip()
    lower = url.lower()
    # 允许相对路径和锚点
    if url.startswith('/') or url.startswith('#') or url.startswith('./'):
        return url
    # 允许 http/https
    if lower.startswith('http://') or lower.startswith('https://'):
        return url
    # 拒绝 javascript:、data: 等危险协议
    if re.match(r'^[a-z][a-z0-9+.-]*:', lower):
        return '#blocked'
    return url

def inline_html(text):
    """安全的行内 HTML 渲染（v1.3 安全修复）"""
    # 先转义 HTML 特殊字符
    text = html_mod.escape(text)
    
    # 图片（仅允许安全协议）
    def img_repl(m):
        alt = m.group(1)
        src = _sanitize_url(m.group(2))
        return f'<img src="{src}" alt="{alt}" />'
    text = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', img_repl, text)
    
    # 链接（仅允许安全协议）
    def link_repl(m):
        label = m.group(1)
        href = _sanitize_url(m.group(2))
        return f'<a href="{href}">{label}</a>'
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link_repl, text)
    
    # 粗体、斜体、行内代码
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'\*(.+?)\*', r'<em>\1</em>', text)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    return text

# ─── 分类判断 ──────────────────────────────────────────────

def classify_file(filename: str, content_preview: str = "") -> str:
    """
    双重分类判断：
    1. 文件名关键词匹配（优先级高）
    2. 内容前 500 字关键词匹配（兜底）
    返回 slug
    """
    name_lower = filename.lower()

    # 第一重：文件名匹配
    for slug, cat_name, icon, keywords in CATEGORY_RULES:
        if slug == "other":
            continue
        for kw in keywords:
            if kw.lower() in name_lower:
                return slug

    # 第二重：内容匹配（取前 500 字）
    if content_preview:
        content_lower = content_preview[:500].lower()
        for slug, cat_name, icon, keywords in CATEGORY_RULES:
            if slug == "other":
                continue
            for kw in keywords:
                if kw.lower() in content_lower:
                    return slug

    return "other"

# ─── 增量导入 ──────────────────────────────────────────────

def _file_fingerprint(filepath: Path):
    """计算文件指纹：mtime + size"""
    stat = filepath.stat()
    return (stat.st_mtime, stat.st_size)

def import_documents(incremental=True):
    """
    扫描并导入 .md 文件（v1.3 增强版）。
    incremental=True  （默认）：只新增/更新有变化的文件，删除已不存在的文件
    incremental=False ：全量重建（清空后重新导入）
    """
    conn = get_db()
    cat_ids = ensure_categories(conn)

    if not incremental:
        conn.execute("DELETE FROM docs_fts")
        conn.execute("DELETE FROM documents")

    # 已入库的文档：filepath → {id, mtime, size}
    existing = {}
    rows = conn.execute("SELECT id, filepath, file_mtime FROM documents WHERE deleted_at IS NULL").fetchall()
    for r in rows:
        existing[r['filepath']] = {'id': r['id'], 'mtime': r['file_mtime']}

    import_count = 0
    update_count = 0
    skip_count = 0
    delete_count = 0
    seen_files = set()

    def _process_file(filepath: Path, cat_id: int):
        nonlocal import_count, update_count, skip_count
        fkey = str(filepath.resolve())
        if fkey in seen_files:
            return
        seen_files.add(fkey)

        mtime_val, size_val = _file_fingerprint(filepath)

        # 增量判断：mtime + size 组合判断变更
        if incremental and fkey in existing:
            old = existing[fkey]
            if abs(old['mtime'] - mtime_val) < 1.0:
                skip_count += 1
                return
            # 文件有变更，先删旧记录
            fts_delete_doc(conn, old['id'])
            conn.execute("DELETE FROM documents WHERE id = ?", (old['id'],))
            update_count += 1

        content = filepath.read_text(encoding='utf-8', errors='replace')
        filename = filepath.name

        # 如果未指定分类，自动判断
        if cat_id is None:
            slug = classify_file(filename, content[:500])
            cat_id = cat_ids.get(slug, cat_ids['other'])

        # 提取标题
        title = filename.replace('.md', '')
        for line in content.split('\n'):
            stripped = line.strip()
            if stripped.startswith('#'):
                title = stripped.lstrip('# ').strip()
                break

        # 清理搜索内容
        search_content = re.sub(r'[#*`\-\[\]()>|]', ' ', content)
        search_content = re.sub(r'\s+', ' ', search_content).strip()
        word_count = len(search_content)
        html_content = md_to_html(content)
        created = datetime.fromtimestamp(mtime_val).strftime('%Y-%m-%d')
        updated = datetime.fromtimestamp(mtime_val).strftime('%Y-%m-%d')

        cur = conn.execute("""
            INSERT INTO documents
                (category_id, filename, title, filepath, content, html_content,
                 word_count, file_mtime, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """, (cat_id, filename, title, fkey, search_content, html_content,
              word_count, mtime_val, created, updated))

        cat_name = conn.execute("SELECT name FROM categories WHERE id = ?", (cat_id,)).fetchone()['name']
        fts_index_doc(conn, cur.lastrowid, title, search_content, cat_name)

        if not (incremental and fkey in existing):
            import_count += 1

    # 1. 扫描 docs/ 子目录下的 .md 文件（目录结构决定分类）
    docs_dir = DOCS_CATEGORIES_DIR
    if docs_dir.exists():
        for subdir in sorted(docs_dir.iterdir()):
            if not subdir.is_dir():
                continue
            dir_slug = None
            for slug, name, _, _ in CATEGORY_RULES:
                if name == subdir.name:
                    dir_slug = slug
                    break
            dir_cat_id = cat_ids.get(dir_slug, cat_ids.get("other")) if dir_slug else None
            for md_file in sorted(subdir.glob("*.md")):
                if md_file.name.startswith("00_"):
                    continue
                _process_file(md_file, dir_cat_id)

    # 2. 扫描根目录下的 .md 文件，自动分类
    for md_file in sorted(DOCS_SRC.glob("*.md")):
        if md_file.stat().st_size == 0:
            continue
        _process_file(md_file, None)  # None = 自动分类

    # 3. 删除已不存在的源文件（软删除）
    if incremental:
        missing = set(existing.keys()) - seen_files
        for fkey in missing:
            old = existing[fkey]
            conn.execute("UPDATE documents SET deleted_at = ? WHERE id = ?",
                        (datetime.now().strftime('%Y-%m-%d %H:%M:%S'), old['id']))
            fts_delete_doc(conn, old['id'])
            delete_count += 1

    conn.commit()

    # 4. 清理孤儿数据（标签关系、图谱关系）
    conn.execute("""
        DELETE FROM document_tags WHERE document_id IN (
            SELECT id FROM documents WHERE deleted_at IS NOT NULL
        )
    """)
    conn.execute("""
        DELETE FROM doc_relations WHERE source_id IN (
            SELECT id FROM documents WHERE deleted_at IS NOT NULL
        ) OR target_id IN (
            SELECT id FROM documents WHERE deleted_at IS NOT NULL
        )
    """)
    conn.commit()

    # 打印分类统计
    stats = conn.execute("""
        SELECT c.name, c.icon, COUNT(d.id) as cnt
        FROM categories c
        LEFT JOIN documents d ON d.category_id = c.id AND d.deleted_at IS NULL
        GROUP BY c.id
        HAVING cnt > 0
        ORDER BY cnt DESC
    """).fetchall()
    conn.close()

    total = import_count + update_count + skip_count + delete_count
    logger.info(f"[import] 增量={incremental} | 新增 {import_count} | 更新 {update_count} | 跳过 {skip_count} | 删除 {delete_count} | 总计扫描 {total}")
    logger.info(f"[import] 当前分类统计：")
    for row in stats:
        logger.info(f"  {row['icon']} {row['name']}: {row['cnt']} 篇")
    return {"imported": import_count, "updated": update_count, "skipped": skip_count, "deleted": delete_count}


# ─── HTTP 请求处理器 ────────────────────────────────────────

class DocHandler(http.server.SimpleHTTPRequestHandler):

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(BASE_DIR / "static"), **kwargs)

    def do_GET(self):
        start_time = time.time()
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/') or '/'
        qs = urllib.parse.parse_qs(parsed.query)
        logger.info(f"[request] {self.path} -> path={path}")

        # 静态文件缓存控制（v1.1 新增）
        if path.startswith('/css/') or path.startswith('/js/') or path.startswith('/images/'):
            return self._serve_static(path)

        # 根路径重定向到 index.html
        if path == '/':
            self.path = '/index.html'
            return self._serve_static('/index.html')

        try:
            # API 路由
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

            # 知识图谱 API
            if path == '/api/graph':
                doc_id = qs.get('id', [None])[0]
                depth = parse_int(qs.get('depth', [1])[0], 1, 1, 3)
                return self._json(self._api_graph(doc_id, depth))
            if path == '/api/graph-rebuild':
                return self._json(self._api_graph_rebuild())

            # ─── 标签系统 API ──────────────────────────
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

            # ─── 回收站 API（只读列表） ─────────────────
            if path == '/api/trash':
                page = parse_int(qs.get('page', [1])[0], 1, 1, 100)
                limit = parse_int(qs.get('limit', [20])[0], 20, 1, 100)
                return self._json(self._api_trash(page, limit))

            # 静态文件兜底
            return self._serve_static(path)
        finally:
            elapsed = time.time() - start_time
            record_request(path, elapsed)

    def _serve_static(self, path):
        """静态文件服务，带缓存控制（v1.3 安全修复）"""
        # 路径规范化：解码 URL、解析 ..、确保在 static/ 目录内
        decoded = urllib.parse.unquote(path)
        safe_path = (BASE_DIR / 'static' / decoded.lstrip('/')).resolve()
        static_root = (BASE_DIR / 'static').resolve()
        
        try:
            safe_path.relative_to(static_root)
        except ValueError:
            logger.warning(f"[SECURITY] Path escape attempt: {path}")
            self.send_error(403, "Forbidden")
            return
        
        file_path = safe_path
        if not file_path.exists() or not file_path.is_file():
            self.send_error(404)
            return

        # 根据扩展名设置 Content-Type
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

        # 读取文件内容
        try:
            content = file_path.read_bytes()
        except Exception:
            self.send_error(500)
            return

        # 计算 ETag
        import hashlib
        etag = hashlib.md5(content).hexdigest()[:16]

        # 检查 If-None-Match
        if_none_match = self.headers.get('If-None-Match')
        if if_none_match and if_none_match == etag:
            self.send_response(304)
            self.end_headers()
            return

        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', len(content))
        self.send_header('ETag', etag)

        # CSS/JS 文件添加长期缓存
        if ext in ('.css', '.js'):
            self.send_header('Cache-Control', 'public, max-age=86400')
            self.send_header('Expires', self.date_time_string(time.time() + 86400))
        else:
            self.send_header('Cache-Control', 'public, max-age=3600')

        self.end_headers()
        self.wfile.write(content)

    # ─── 响应工具 ──────────────────────────────────

    def _check_admin_token(self):
        """校验管理操作 Token，未配置则放行（向后兼容）"""
        if not ADMIN_TOKEN:
            return True
        token = self.headers.get('X-Admin-Token', '')
        return token == ADMIN_TOKEN

    def _json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False, default=str).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', len(body))
        # CORS: 默认同源，配置 cors_origin 后允许指定来源
        origin = self.headers.get('Origin', '')
        if CORS_ORIGIN:
            if CORS_ORIGIN == '*' or origin == CORS_ORIGIN:
                self.send_header('Access-Control-Allow-Origin', origin or CORS_ORIGIN)
                self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
                self.send_header('Access-Control-Allow-Headers', 'Content-Type, X-Admin-Token')
        self.end_headers()
        self.wfile.write(body)

    def _ok(self, data=None):
        """统一成功响应"""
        return {"status": "ok", "data": data}

    def _error(self, message, code="bad_request"):
        """统一错误响应"""
        return {"status": "error", "code": code, "message": message}

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

        # 构建分类过滤条件
        cat_filter = " AND d.deleted_at IS NULL"
        cat_params = []
        if category:
            cat_filter += " AND c.slug = ?"
            cat_params.append(category)

        # FTS5 trigram 搜索
        try:
            total = conn.execute(
                f"SELECT COUNT(*) FROM docs_fts JOIN documents d ON d.id = docs_fts.rowid JOIN categories c ON d.category_id = c.id WHERE docs_fts MATCH ?{cat_filter}",
                (clean_q,) + tuple(cat_params)
            ).fetchone()[0]
            rows = conn.execute(f"""
                SELECT d.id, d.title, d.filename, d.content, d.word_count,
                       d.created_at, d.updated_at,
                       c.name as category_name, c.slug as category_slug, c.icon, rank
                FROM docs_fts
                JOIN documents d ON d.id = docs_fts.rowid
                JOIN categories c ON d.category_id = c.id
                WHERE docs_fts MATCH ?{cat_filter}
                ORDER BY rank LIMIT ? OFFSET ?
            """, (clean_q,) + tuple(cat_params) + (limit, offset)).fetchall()
        except sqlite3.OperationalError:
            # 降级 LIKE
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
        """搜索建议：基于全文检索返回匹配文档，优先展示标题匹配的结果"""
        if not query or not query.strip():
            return {"suggestions": []}
        conn = get_db()
        clean_q = re.sub(r'[^\w一-鿿\s]', ' ', query).strip()
        if not clean_q:
            conn.close()
            return {"suggestions": []}

        like_q = f'%{clean_q}%'
        rows = []

        # 判断是否适合 FTS5 trigram：≥3 个字符的词才能被 trigram tokenizer 拆分
        # 短中文词（1-2字）直接用 LIKE，长词优先 FTS5
        can_use_fts = len(clean_q) >= 3

        if can_use_fts:
            try:
                rows = conn.execute("""
                    SELECT d.id, d.title, c.name as category_name,
                           c.slug as category_slug, c.icon,
                           CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END as title_match
                    FROM docs_fts
                    JOIN documents d ON d.id = docs_fts.rowid
                    JOIN categories c ON d.category_id = c.id
                    WHERE docs_fts MATCH ?
                    ORDER BY title_match ASC, rank
                    LIMIT ?
                """, (like_q, clean_q, limit)).fetchall()
            except sqlite3.OperationalError:
                rows = []

        # FTS5 无结果或不可用时，降级到 LIKE（同时搜索 title + content）
        if not rows:
            rows = conn.execute("""
                SELECT d.id, d.title, c.name as category_name,
                       c.slug as category_slug, c.icon,
                       CASE WHEN d.title LIKE ? THEN 0 ELSE 1 END as title_match
                FROM documents d
                JOIN categories c ON d.category_id = c.id
                WHERE d.deleted_at IS NULL
                  AND (d.title LIKE ? OR d.content LIKE ?)
                ORDER BY title_match ASC, d.id DESC
                LIMIT ?
            """, (like_q, like_q, like_q, limit)).fetchall()

        conn.close()
        return {"suggestions": [dict(r) for r in rows]}

    def _api_related(self, doc_id, limit=5):
        """相似文档推荐：基于相同分类 + 标题关键词重叠度"""
        conn = get_db()
        doc = conn.execute("""
            SELECT d.title, d.content, d.category_id, c.name as category_name
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.id = ? AND d.deleted_at IS NULL
        """, (doc_id,)).fetchone()
        if not doc:
            conn.close()
            return {"documents": []}

        # 提取标题中的关键词（中文字符和英文单词）
        title = doc['title']
        category_id = doc['category_id']
        keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}|[a-zA-Z]+', title.lower()))

        # 查找同分类的其他文档，计算标题关键词重叠度
        rows = conn.execute("""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL
            ORDER BY d.id DESC
            LIMIT 50
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
        return {"total_documents": total_docs, "total_categories": total_cats, "total_words": total_words, "trashed": trashed}

    def _api_health(self):
        """健康检查接口"""
        try:
            conn = get_db()
            conn.execute("SELECT 1").fetchone()
            conn.close()
            db_status = "ok"
        except Exception as e:
            db_status = f"error: {e}"
        import shutil
        disk = shutil.disk_usage(BASE_DIR)
        return {
            "status": "ok",
            "database": db_status,
            "uptime": _request_stats["count"],
            "avg_response_ms": round((_request_stats["total_time"] / max(_request_stats["count"], 1)) * 1000, 2),
            "hot_paths": sorted(_request_stats["paths"].items(), key=lambda x: -x[1])[:5],
            "disk": {
                "total_gb": round(disk.total / (1024**3), 2),
                "used_gb": round(disk.used / (1024**3), 2),
                "free_gb": round(disk.free / (1024**3), 2),
                "percent": round(disk.used / disk.total * 100, 1)
            },
            "memory": {
                "rss_mb": round(__import__('psutil').Process().memory_info().rss / (1024**2), 2) if 'psutil' in sys.modules else None
            }
        }

    def _api_log(self, error_info):
        """接收前端错误日志"""
        logger.error(f"[frontend] {error_info.get('type')} error: {error_info.get('message')} at {error_info.get('url')}")
        conn = get_db()
        try:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS frontend_errors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT,
                    message TEXT,
                    filename TEXT,
                    lineno INTEGER,
                    stack TEXT,
                    url TEXT,
                    user_agent TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            conn.execute("""
                INSERT INTO frontend_errors (type, message, filename, lineno, stack, url, user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (
                error_info.get('type'),
                error_info.get('message'),
                error_info.get('filename'),
                error_info.get('lineno'),
                error_info.get('stack'),
                error_info.get('url'),
                error_info.get('userAgent')
            ))
            conn.commit()
        except Exception as e:
            logger.error(f"[frontend] Failed to store error: {e}")
        finally:
            conn.close()
        return {"status": "ok"}

    def _api_performance(self, metrics):
        """接收前端性能指标"""
        logger.info(f"[performance] FCP={metrics.get('fcp')}ms LCP={metrics.get('lcp')}ms CLS={metrics.get('cls')}")
        conn = get_db()
        try:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS performance_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fcp INTEGER,
                    lcp INTEGER,
                    cls REAL,
                    url TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            conn.execute("""
                INSERT INTO performance_metrics (fcp, lcp, cls, url)
                VALUES (?, ?, ?, ?)
            """, (
                metrics.get('fcp'),
                metrics.get('lcp'),
                metrics.get('cls'),
                metrics.get('url')
            ))
            conn.commit()
        except Exception as e:
            logger.error(f"[performance] Failed to store metrics: {e}")
        finally:
            conn.close()
        return {"status": "ok"}

    def _api_recategorize(self, doc_id, new_slug):
        """手动修正文档分类"""
        conn = get_db()
        cat = conn.execute("SELECT id FROM categories WHERE slug = ?", (new_slug,)).fetchone()
        if not cat:
            conn.close()
            return {"error": f"分类 '{new_slug}' 不存在"}
        row = conn.execute("SELECT id, title, content FROM documents WHERE id = ?", (doc_id,)).fetchone()
        if not row:
            conn.close()
            return {"error": f"文档 #{doc_id} 不存在"}
        conn.execute("UPDATE documents SET category_id = ? WHERE id = ?", (cat['id'], doc_id))
        cat_name = conn.execute("SELECT name FROM categories WHERE id = ?", (cat['id'],)).fetchone()['name']
        fts_delete_doc(conn, row['id'])
        fts_index_doc(conn, row['id'], row['title'], row['content'], cat_name)
        conn.commit()
        conn.close()
        return {"status": "ok", "message": f"文档 #{doc_id} 已移至分类 '{cat_name}'"}

    # ─── 标签系统 API ──────────────────────────

    def _api_tags(self):
        """获取所有标签及文档数量"""
        conn = get_db()
        rows = conn.execute("""
            SELECT t.id, t.name, t.slug, t.color, COUNT(dt.document_id) as doc_count
            FROM tags t
            LEFT JOIN document_tags dt ON dt.tag_id = t.id
            GROUP BY t.id
            ORDER BY doc_count DESC, t.name
        """).fetchall()
        conn.close()
        return {"tags": [dict(r) for r in rows]}

    def _api_tag_documents(self, tag_slug, page=1, limit=20):
        """获取某标签下的文档列表"""
        if not tag_slug:
            return {"documents": [], "total": 0, "page": page, "limit": limit, "pages": 0}
        conn = get_db()
        offset = (page - 1) * limit
        total = conn.execute("""
            SELECT COUNT(*) FROM document_tags dt
            JOIN tags t ON dt.tag_id = t.id
            JOIN documents d ON dt.document_id = d.id
            WHERE t.slug = ? AND d.deleted_at IS NULL
        """, (tag_slug,)).fetchone()[0]
        rows = conn.execute("""
            SELECT d.id, d.title, d.filename, d.word_count, d.created_at, d.updated_at,
                   c.name as category_name, c.slug as category_slug, c.icon
            FROM document_tags dt
            JOIN tags t ON dt.tag_id = t.id
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
        """获取文档的标签列表"""
        conn = get_db()
        rows = conn.execute("""
            SELECT t.id, t.name, t.slug, t.color
            FROM tags t
            JOIN document_tags dt ON dt.tag_id = t.id
            WHERE dt.document_id = ?
        """, (doc_id,)).fetchall()
        conn.close()
        return {"tags": [dict(r) for r in rows]}

    # ─── 回收站 API ────────────────────────────

    def _api_soft_delete(self, doc_id):
        """软删除文档（移入回收站）"""
        if not doc_id:
            return {"error": "缺少文档 ID"}
        conn = get_db()
        row = conn.execute("SELECT id FROM documents WHERE id = ? AND deleted_at IS NULL", (doc_id,)).fetchone()
        if not row:
            conn.close()
            return {"error": f"文档 #{doc_id} 不存在或已被删除"}
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        conn.execute("UPDATE documents SET deleted_at = ? WHERE id = ?", (now, doc_id))
        conn.commit()
        conn.close()
        logger.info(f"[trash] 文档 #{doc_id} 已移入回收站")
        return {"status": "ok", "message": f"文档 #{doc_id} 已移入回收站"}

    def _api_restore(self, doc_id):
        """从回收站恢复文档"""
        if not doc_id:
            return {"error": "缺少文档 ID"}
        conn = get_db()
        row = conn.execute("SELECT id FROM documents WHERE id = ? AND deleted_at IS NOT NULL", (doc_id,)).fetchone()
        if not row:
            conn.close()
            return {"error": f"文档 #{doc_id} 不在回收站中"}
        conn.execute("UPDATE documents SET deleted_at = NULL WHERE id = ?", (doc_id,))
        conn.commit()
        conn.close()
        logger.info(f"[trash] 文档 #{doc_id} 已从回收站恢复")
        return {"status": "ok", "message": f"文档 #{doc_id} 已恢复"}

    def _api_purge(self, doc_id):
        """彻底删除文档"""
        if not doc_id:
            return {"error": "缺少文档 ID"}
        conn = get_db()
        row = conn.execute("SELECT id, title FROM documents WHERE id = ?", (doc_id,)).fetchone()
        if not row:
            conn.close()
            return {"error": f"文档 #{doc_id} 不存在"}
        fts_delete_doc(conn, doc_id)
        conn.execute("DELETE FROM document_tags WHERE document_id = ?", (doc_id,))
        conn.execute("DELETE FROM documents WHERE id = ?", (doc_id,))
        conn.commit()
        conn.close()
        logger.info(f"[trash] 文档 #{doc_id} ({row['title']}) 已彻底删除")
        return {"status": "ok", "message": f"文档 #{doc_id} 已彻底删除"}

    def _api_trash(self, page=1, limit=20):
        """获取回收站文档列表"""
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

    # ─── 批量操作 API ──────────────────────────

    def _api_batch_recategorize(self, doc_ids_str, new_slug):
        """批量修改文档分类"""
        if not doc_ids_str or not new_slug:
            return {"error": "缺少参数"}
        conn = get_db()
        cat = conn.execute("SELECT id, name FROM categories WHERE slug = ?", (new_slug,)).fetchone()
        if not cat:
            conn.close()
            return {"error": f"分类 '{new_slug}' 不存在"}
        doc_ids = [x.strip() for x in doc_ids_str.split(',') if x.strip()]
        updated = 0
        for doc_id in doc_ids:
            row = conn.execute("SELECT id, title, content FROM documents WHERE id = ? AND deleted_at IS NULL", (doc_id,)).fetchone()
            if row:
                conn.execute("UPDATE documents SET category_id = ? WHERE id = ?", (cat['id'], doc_id))
                fts_delete_doc(conn, row['id'])
                fts_index_doc(conn, row['id'], row['title'], row['content'], cat['name'])
                updated += 1
        conn.commit()
        conn.close()
        logger.info(f"[batch] 批量修改分类: {updated} 篇文档移至 '{cat['name']}'")
        return {"status": "ok", "updated": updated, "category": cat['name']}

    def _api_graph(self, doc_id, depth=1):
        """获取文档知识图谱"""
        conn = get_db()
        
        # 获取中心文档
        center = conn.execute("""
            SELECT d.id, d.title, c.name as category_name, c.slug as category_slug, c.icon
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.id = ? AND d.deleted_at IS NULL
        """, (doc_id,)).fetchone()
        
        if not center:
            conn.close()
            return {"error": "文档不存在"}
        
        # 获取直接关系
        relations = conn.execute("""
            SELECT r.source_id, r.target_id, r.rel_type, r.weight,
                   d.title, d.id as rel_doc_id, c.icon, c.slug as category_slug
            FROM doc_relations r
            JOIN documents d ON (d.id = r.target_id OR d.id = r.source_id)
            JOIN categories c ON d.category_id = c.id
            WHERE (r.source_id = ? OR r.target_id = ?) 
              AND d.id != ? AND d.deleted_at IS NULL
            ORDER BY r.weight DESC
            LIMIT 20
        """, (doc_id, doc_id, doc_id)).fetchall()
        
        # 如果没有预计算的关系，实时计算
        if not relations:
            relations = self._compute_doc_relations(conn, doc_id)
        
        # 构建节点和边
        nodes = {doc_id: dict(center)}
        edges = []
        
        for rel in relations:
            rel_doc_id = rel['rel_doc_id']
            if rel_doc_id not in nodes:
                nodes[rel_doc_id] = {
                    'id': rel_doc_id,
                    'title': rel['title'],
                    'icon': rel['icon'],
                    'category_slug': rel['category_slug']
                }
            edges.append({
                'source': rel['source_id'],
                'target': rel['target_id'],
                'type': rel['rel_type'],
                'weight': rel['weight']
            })
        
        conn.close()
        
        return {
            "center": dict(center),
            "nodes": list(nodes.values()),
            "edges": edges
        }

    def _compute_doc_relations(self, conn, doc_id):
        """实时计算文档关系"""
        doc = conn.execute("SELECT id, title, content, category_id FROM documents WHERE id = ?", (doc_id,)).fetchone()
        if not doc:
            return []
        
        relations = []
        
        # 1. 同分类关系
        same_cat = conn.execute("""
            SELECT d.id, d.title, c.icon, c.slug as category_slug
            FROM documents d JOIN categories c ON d.category_id = c.id
            WHERE d.category_id = ? AND d.id != ? AND d.deleted_at IS NULL
            LIMIT 10
        """, (doc['category_id'], doc_id)).fetchall()
        
        for r in same_cat:
            relations.append({
                'source_id': doc_id,
                'target_id': r['id'],
                'rel_type': 'same_category',
                'weight': 0.6,
                'rel_doc_id': r['id'],
                'title': r['title'],
                'icon': r['icon'],
                'category_slug': r['category_slug']
            })
        
        # 2. 内容相似度（基于关键词匹配）
        # 提取标题关键词
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
                        if weight > 0.3:  # 阈值
                            relations.append({
                                'source_id': doc_id,
                                'target_id': other['id'],
                                'rel_type': 'similar',
                                'weight': round(weight, 2),
                                'rel_doc_id': other['id'],
                                'title': other['title'],
                                'icon': other['icon'],
                                'category_slug': other['category_slug']
                            })
        
        # 按权重排序，取前15
        relations.sort(key=lambda x: x['weight'], reverse=True)
        return relations[:15]

    def _api_graph_rebuild(self):
        """重建所有文档关系图谱"""
        conn = get_db()
        
        # 清空现有关系
        conn.execute("DELETE FROM doc_relations")
        
        # 获取所有文档
        docs = conn.execute("SELECT id, title, content, category_id FROM documents WHERE deleted_at IS NULL").fetchall()
        
        count = 0
        for i, doc in enumerate(docs):
            # 同分类关系
            same_cat = conn.execute("""
                SELECT id FROM documents 
                WHERE category_id = ? AND id != ? AND deleted_at IS NULL
            """, (doc['category_id'], doc['id'])).fetchall()
            
            for other in same_cat[:5]:  # 每个分类最多5个
                try:
                    conn.execute("""
                        INSERT INTO doc_relations (source_id, target_id, rel_type, weight)
                        VALUES (?, ?, 'same_category', 0.6)
                    """, (min(doc['id'], other['id']), max(doc['id'], other['id'])))
                    count += 1
                except sqlite3.IntegrityError:
                    pass
            
            # 相似度关系
            keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}', doc['title']))
            if keywords:
                for other in docs[i+1:]:
                    other_keywords = set(re.findall(r'[\u4e00-\u9fff]{2,}', other['title']))
                    if other_keywords:
                        common = keywords & other_keywords
                        if common:
                            weight = min(len(common) / max(len(keywords), len(other_keywords)), 1.0)
                            if weight > 0.5:
                                try:
                                    conn.execute("""
                                        INSERT INTO doc_relations (source_id, target_id, rel_type, weight)
                                        VALUES (?, ?, 'similar', ?)
                                    """, (min(doc['id'], other['id']), max(doc['id'], other['id']), round(weight, 2)))
                                    count += 1
                                except sqlite3.IntegrityError:
                                    pass
        
        conn.commit()
        conn.close()
        logger.info(f"[graph] 重建知识图谱完成，共 {count} 条关系")
        return {"status": "ok", "relations_count": count}

    def do_POST(self):
        """处理 POST 请求（v1.3 管理操作需 Token）"""
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
            
            # 前端日志和性能上报（无需 Token）
            if path == '/api/log':
                return self._json(self._api_log(data))
            if path == '/api/performance':
                return self._json(self._api_performance(data))
            
            # ─── 管理操作（需校验 Token）────────────────
            if not self._check_admin_token():
                return self._json(self._error("未授权的管理操作", "unauthorized"), status=403)
            
            # 文档管理
            if path == '/api/delete':
                return self._json(self._api_soft_delete(data.get('id')))
            if path == '/api/restore':
                return self._json(self._api_restore(data.get('id')))
            if path == '/api/purge':
                return self._json(self._api_purge(data.get('id')))
            if path == '/api/recategorize':
                return self._json(self._api_recategorize(data.get('id'), data.get('slug')))
            if path == '/api/batch-recategorize':
                return self._json(self._api_batch_recategorize(data.get('ids'), data.get('slug')))
            
            # 索引与同步
            if path == '/api/sync':
                result = import_documents(incremental=True)
                return self._json(self._ok(result))
            if path == '/api/reindex':
                result = import_documents(incremental=False)
                return self._json(self._ok({"message": "全量重建完成", **result}))
            
            # 知识图谱
            if path == '/api/graph-rebuild':
                return self._json(self._api_graph_rebuild())
            
            return self._json(self._error("未知接口", "not_found"), status=404)
        except Exception as e:
            logger.error(f"[POST] Error: {e}")
            return self._json(self._error(str(e), "internal_error"), status=500)
        finally:
            elapsed = time.time() - start_time
            record_request(path, elapsed)

    def log_message(self, fmt, *args):
        logger.info(fmt % args)


# ─── 启动 ────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  📚 文档博客 Web 服务")
    print("=" * 60)

    init_db()
    # 数据库为空则全量导入，否则增量导入
    conn = get_db()
    count = conn.execute("SELECT COUNT(*) FROM documents").fetchone()[0]
    conn.close()
    if count == 0:
        print("[init] 数据库为空，执行全量导入...")
        import_documents(incremental=False)
    else:
        print(f"[init] 已有 {count} 篇文档，执行增量导入...")
        import_documents(incremental=True)

    server = http.server.HTTPServer((HOST, PORT), DocHandler)
    print(f"\n  🌐 服务已启动: http://{HOST}:{PORT}")
    if HOST == '127.0.0.1':
        print(f"  🔒 仅本机可访问（如需局域网共享，修改 config.json host 为 0.0.0.0）")
    print(f"  📁 文档目录: {DOCS_SRC}")
    print(f"  🗃️  数据库: {DB_PATH}")
    print(f"\n  API 接口：")
    print(f"    GET /api/sync        增量同步（新增/变更）")
    print(f"    GET /api/reindex     全量重建索引")
    print(f"    GET /api/recategorize?id=1&slug=java  修正分类")
    print(f"    GET /api/search-suggest?q=xxx         搜索建议")
    print(f"    GET /api/related?id=1                 相似文档")
    print(f"    GET /api/health                       健康检查")
    print(f"    GET /api/tags                         标签列表")
    print(f"    GET /api/tag?slug=xxx                 标签文档")
    print(f"    GET /api/trash                        回收站")
    print(f"    GET /api/delete?id=1                  移入回收站")
    print(f"    GET /api/restore?id=1                 恢复文档")
    print(f"    GET /api/purge?id=1                   彻底删除")
    print(f"    GET /api/batch-recategorize?ids=1,2&slug=xxx  批量改分类")
    print(f"\n  Ctrl+C 停止服务\n")

    # 热重载模式
    if CONFIG.get("hot_reload", False):
        try:
            import threading
            watch_paths = CONFIG.get("watch_paths", ["server.py"])
            _file_mtimes = {}
            for wp in watch_paths:
                p = BASE_DIR / wp
                if p.exists():
                    _file_mtimes[str(p)] = p.stat().st_mtime

            def check_reload():
                while True:
                    time.sleep(2)
                    for path_str, old_mtime in list(_file_mtimes.items()):
                        p = Path(path_str)
                        if p.exists():
                            new_mtime = p.stat().st_mtime
                            if new_mtime != old_mtime:
                                print(f"\n  🔄 检测到文件变更: {p.name}，正在重启...\n")
                                os.execv(sys.executable, [sys.executable] + sys.argv)

            reload_thread = threading.Thread(target=check_reload, daemon=True)
            reload_thread.start()
            print(f"  🔄 热重载已启用，监听: {', '.join(watch_paths)}\n")
        except Exception as e:
            print(f"  ⚠️ 热重载启动失败: {e}\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n  👋 服务已停止")
        server.server_close()

if __name__ == '__main__':
    main()
