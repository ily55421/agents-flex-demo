---
title: 文档博客 Web 服务 — 数据规范
version: 1.0
date_created: 2026-05-23
last_updated: 2026-05-23
owner: 文档博客团队
tags: [data, schema, database]
---

# 文档博客 Web 服务 — 数据规范

## 1. Purpose & Scope

本规范定义了文档博客系统的数据库 Schema、分类规则、文档导入流程及数据一致性约束。确保数据层的设计清晰、可维护，并支持增量更新。

**适用范围**：`data/docs.db` 数据库及 `server.py` 中所有数据操作逻辑。

## 2. 定义

| 术语 | 定义 |
|------|------|
| slug | 分类唯一标识符，小写英文，如 `java`、`ai` |
| mtime | 文件最后修改时间戳（Unix epoch，浮点数） |
| filepath | 文件的绝对路径，作为文档的唯一业务主键 |
| 增量导入 | 仅处理新增/变更文件的导入模式 |
| 全量重建 | 清空所有数据后重新扫描的导入模式 |
| FTS5 | SQLite Full-Text Search v5 虚拟表 |
| trigram | 三字符分词器，支持中文子串匹配 |

## 3. Requirements, Constraints & Guidelines

### 3.1 数据库约束

- **REQ-001**: 数据库必须使用 SQLite，启用 WAL 模式（`PRAGMA journal_mode=WAL`）
- **REQ-002**: 数据库文件路径为 `data/docs.db`，首次启动自动创建
- **REQ-003**: Schema 变更必须通过重建数据库实现（开发阶段），不提供迁移脚本
- **REQ-004**: `documents` 表的 `filepath` 字段必须为 UNIQUE，作为增量判断的业务主键
- **REQ-005**: `documents` 表的 `file_mtime` 字段类型为 REAL，存储文件 mtime
- **REQ-006**: FTS5 表必须手动维护，不使用数据库触发器

### 3.2 分类规则约束

- **REQ-007**: 分类规则定义在 `CATEGORY_RULES` 列表中，顺序即为匹配优先级
- **REQ-008**: `other` 分类必须位于列表末尾，作为兜底
- **REQ-009**: 分类 slug 必须唯一，使用小写英文或缩写
- **REQ-010**: 每个分类必须包含至少 3 个关键词

### 3.3 文档导入约束

- **REQ-011**: 空文件（`st_size == 0`）必须跳过
- **REQ-012**: 文件名以 `00_` 开头的索引文件必须跳过
- **REQ-013**: 文档标题从第一个 `#` 标题行提取，若无则使用文件名（去掉 `.md`）
- **REQ-014**: `content` 字段存储去除 Markdown 标记后的纯文本
- **REQ-015**: `html_content` 字段存储 Markdown → HTML 转换后的富文本
- **REQ-016**: `word_count` 为纯文本内容的字符数

### 3.4 增量导入约束

- **REQ-017**: 增量模式下，mtime 差值 < 1.0 秒视为未变更
- **REQ-018**: 增量模式下，变更文件先删除旧记录再重新插入
- **REQ-019**: 增量模式下，FTS5 索引必须同步更新（删除旧 rowid，插入新 rowid）
- **REQ-020**: 全量模式下，先清空 FTS5 表，再清空 documents 表，最后清空 categories 表

### 3.5 数据一致性

- **CON-001**: `documents.category_id` 必须引用 `categories.id`
- **CON-002**: FTS5 表的 rowid 必须与 `documents.id` 一一对应
- **CON-003**: 删除文档时必须同步删除 FTS5 索引

## 4. Interfaces & Data Contracts

### 4.1 categories 表

```sql
CREATE TABLE IF NOT EXISTS categories (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    slug        TEXT UNIQUE NOT NULL,
    name        TEXT NOT NULL,
    icon        TEXT DEFAULT '📁',
    sort_order  INTEGER DEFAULT 0
);
```

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, AUTOINCREMENT | 自增主键 |
| slug | TEXT | UNIQUE NOT NULL | 如 `java`, `ai`, `other` |
| name | TEXT | NOT NULL | 如 `Java与JVM` |
| icon | TEXT | DEFAULT '📁' | Emoji 图标 |
| sort_order | INTEGER | DEFAULT 0 | 越小越靠前 |

### 4.2 documents 表

```sql
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
```

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, AUTOINCREMENT | 自增主键 |
| category_id | INTEGER | NOT NULL, FK | → categories.id |
| filename | TEXT | NOT NULL | 原始文件名（含 .md） |
| title | TEXT | NOT NULL | 从首行 `#` 标题提取 |
| filepath | TEXT | UNIQUE NOT NULL | 绝对路径 |
| content | TEXT | NOT NULL | 纯文本（搜索用） |
| html_content | TEXT | NOT NULL | HTML（渲染用） |
| word_count | INTEGER | DEFAULT 0 | 纯文本字符数 |
| file_mtime | REAL | DEFAULT 0 | 文件修改时间戳 |
| created_at | TEXT | — | `YYYY-MM-DD` |
| updated_at | TEXT | — | `YYYY-MM-DD` |

### 4.3 docs_fts 表

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts USING fts5(
    title,
    content,
    category_name,
    tokenize='trigram'
);
```

| 字段 | 说明 |
|------|------|
| title | 文档标题，用于搜索匹配 |
| content | 文档纯文本内容，用于搜索匹配 |
| category_name | 分类名称，用于分类过滤搜索 |
| rowid | 隐式字段，手动设置为 `documents.id` |

> **注意**：FTS5 表不使用 `content=` 外部内容表模式，所有数据手动同步维护。

### 4.4 分类规则数据结构

```python
CATEGORY_RULES = [
    # (slug, display_name, icon, [keywords])
    ("ai", "AI与机器学习", "🤖", ["ai", "人工智能", "机器学习", ...]),
    ("java", "Java与JVM", "☕", ["java", "jvm", "spring", ...]),
    # ...
    ("other", "其他", "📁", []),  # 兜底，必须最后
]
```

## 5. Acceptance Criteria

- **AC-001**: 给定空数据库，当执行全量导入后，Then `categories` 表包含所有 `CATEGORY_RULES` 中的分类
- **AC-002**: 给定空数据库，当执行全量导入后，Then `documents` 表记录数 = 源目录中非空 `.md` 文件数（排除 `00_` 开头文件）
- **AC-003**: 给定文档已入库，当文件 mtime 未变时执行增量导入，Then 该文档记录不变（skipped +1）
- **AC-004**: 给定文档已入库，当文件 mtime 变更后执行增量导入，Then 该文档 `content`、`html_content`、`word_count` 更新
- **AC-005**: 给定新增文件，当执行增量导入后，Then `documents` +1，FTS5 表 +1
- **AC-006**: 给定调用 `/api/reindex`，Then 所有数据重新导入，分类与源文件一致
- **AC-007**: 给定文档标题含 "Android"，当导入后，Then `category_id` 指向 "移动端开发" 分类
- **AC-008**: 给定 FTS5 搜索 "关键词"，Then 返回的每行 `rowid` 都能在 `documents` 表中找到对应记录
- **AC-009**: 给定删除 FTS5 中的某个 rowid，Then 搜索结果不再包含该文档

## 6. Test Automation Strategy

- **单元测试**：使用 `pytest` + 临时内存数据库（`:memory:`）
- **集成测试**：使用临时文件数据库，测试完整导入流程
- **测试数据**：准备 5-10 个不同分类的 `.md` 样本文件
- **覆盖率目标**：数据操作函数覆盖率 ≥ 85%

## 7. Rationale & Context

### 7.1 为什么 filepath 作为业务主键？

- 文件路径在文件系统中天然唯一
- 增量导入时，通过 filepath 快速判断文件是否已入库
- 避免使用文件名（可能重复）或内容哈希（计算成本高）作为主键

### 7.2 为什么 FTS5 手动维护而非使用触发器？

- FTS5 的 `content=` 外部内容表模式不支持触发器中的子查询
- 手动维护更可控，便于调试和错误处理
- 文档数量少（~200），手动维护的性能开销可忽略

### 7.3 为什么 content 和 html_content 分开存储？

- `content` 用于搜索（纯文本，去除 Markdown 标记）
- `html_content` 用于渲染（保留 HTML 结构）
- 避免每次搜索时重复清理 Markdown，避免每次渲染时重复转换

### 7.4 为什么使用 trigram tokenizer？

- SQLite 内置，无需额外配置
- 支持中文子串匹配（最少 3 字符）
- 对于 200 篇文档的规模，索引大小和查询性能均可接受

## 8. Dependencies & External Integrations

- **PLT-001**: Python 3.10+ `sqlite3` 模块，需支持 FTS5 trigram
- **DAT-001**: Markdown 源文件，UTF-8 编码，存放在 `DOCS_SRC` 目录

## 9. Examples & Edge Cases

### 9.1 增量导入示例

```
初始状态: documents 有 209 条记录

场景 1: 无变更
  遍历 209 个文件 → 全部 mtime 匹配 → skipped=209, imported=0, updated=0

场景 2: 新增 1 个文件
  遍历 210 个文件 → 209 个跳过，1 个新增 → skipped=209, imported=1, updated=0

场景 3: 修改 1 个文件
  遍历 209 个文件 → 208 个跳过，1 个 mtime 不匹配 → skipped=208, imported=0, updated=1
```

### 9.2 分类匹配示例

```
文件: "《Android开发范例代码大全（第2版）》章节总结.md"
Step 1: 文件名匹配 → "android" ∈ filename → slug = "mobile" ✓

文件: "《JavaGuide面试突击版》章节总结.md"
Step 1: 文件名匹配 → "java面试" ∈ filename → slug = "interview" ✓
（interview 规则在 java 规则之前）

文件: "《Hadoop: The Definitive Guide》章节总结.md"
Step 1: 文件名匹配 → "hadoop" ∈ filename → slug = "bigdata" ✓

文件: "某个未知文档.md"
Step 1: 文件名匹配 → 无匹配
Step 2: 内容前 500 字匹配 → 包含 "spring boot" → slug = "java" ✓

文件: "完全无关.md"
Step 1: 文件名匹配 → 无匹配
Step 2: 内容匹配 → 无匹配
Step 3: 兜底 → slug = "other"
```

### 9.3 Edge Cases

| 场景 | 处理方式 |
|------|---------|
| 空文件（0 字节） | 跳过，不导入 |
| 文件名以 `00_` 开头 | 跳过（索引文件） |
| 文件无 `#` 标题 | 使用文件名（去 .md）作为 title |
| 文件 mtime 差值恰好为 1.0 | 视为未变更（`< 1.0` 判断） |
| FTS5 查询语法错误 | 降级为 LIKE 查询 |
| 数据库文件损坏 | 手动删除 `docs.db` 后重启 |

## 10. Validation Criteria

- `documents` 表中无重复 `filepath`
- `documents` 表中无 `word_count == 0` 的非空文档
- FTS5 表行数 = `documents` 表行数
- 所有 `category_id` 都能在 `categories` 表中找到
- 增量导入前后，未变更文档的 `id` 保持不变

## 11. Related Specifications / Further Reading

- [架构规范](./spec-architecture.md) — 系统整体架构
- [技术文档](./docs/技术文档.md) — 详细技术实现文档
