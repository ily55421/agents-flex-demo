---
title: 文档博客 Web 服务 — 架构规范
version: 1.0
date_created: 2026-05-23
last_updated: 2026-05-23
owner: 文档博客团队
tags: [architecture, design, web-app]
---

# 文档博客 Web 服务 — 架构规范

## 1. Purpose & Scope

本规范定义了文档博客 Web 服务的整体架构设计、技术选型、数据流、模块划分及各组件间的接口契约。目标是确保系统的可维护性、可扩展性和零依赖部署能力。

**适用范围**：`web-app/` 目录下的所有源代码，包括后端服务（`server.py`）、前端应用（`static/`）及数据层（`data/docs.db`）。

**设计原则**：
- **零外部依赖**：仅使用 Python 标准库，不引入第三方包
- **单文件后端**：所有后端逻辑集中在 `server.py` 中
- **静态前端**：无构建工具，无框架，纯 HTML/CSS/JS
- **增量优先**：所有数据操作默认增量模式，避免全量重建

## 2. 定义

| 术语 | 定义 |
|------|------|
| FTS5 | SQLite Full-Text Search version 5，全文检索虚拟表引擎 |
| trigram | FTS5 的一种分词器，将文本拆分为 3 字符片段，支持中文子串匹配 |
| mtime | 文件最后修改时间（modification time），用于增量变更检测 |
| slug | 分类的唯一标识符，如 `java`、`ai`、`other` |
| 增量导入 | 仅处理新增或变更文件的导入模式，跳过未变更文件 |
| 全量重建 | 清空所有数据后重新扫描全部文件的导入模式 |
| SPA | Single Page Application，单页应用，前端通过 Hash 路由切换视图 |
| WAL | Write-Ahead Logging，SQLite 的预写日志模式，提升并发读写性能 |
| TOC | Table of Contents，章节导航，自动从文章标题提取 |

## 3. Requirements, Constraints & Guidelines

### 3.1 后端约束

- **REQ-001**: 后端必须使用 Python 标准库，不依赖任何第三方包
- **REQ-002**: HTTP 服务必须基于 `http.server.SimpleHTTPRequestHandler` 实现
- **REQ-003**: 数据库必须使用 SQLite，启用 WAL 模式
- **REQ-004**: 全文检索必须使用 FTS5 虚拟表，tokenizer 为 `trigram`
- **REQ-005**: 文档导入必须支持增量模式（mtime 判断）和全量重建两种模式
- **REQ-006**: 分类判断必须采用双重匹配策略（文件名 + 内容前 500 字）
- **REQ-007**: API 响应必须为 JSON 格式，Content-Type 为 `application/json; charset=utf-8`

### 3.2 前端约束

- **REQ-008**: 前端必须使用纯原生 HTML/CSS/JS，不使用任何框架或构建工具
- **REQ-009**: 路由必须使用 Hash 路由（`#/`），支持 `home`、`category`、`article`、`search` 四种视图
- **REQ-010**: 主题切换必须使用 CSS 变量 + `data-theme` 属性，偏好持久化到 `localStorage`
- **REQ-011**: 搜索输入必须有 300ms 防抖，支持 `Enter` 执行、`Escape` 清空
- **REQ-012**: 文章页必须自动生成右侧 TOC 导航，支持滚动高亮

### 3.3 数据约束

- **REQ-013**: `documents` 表的 `filepath` 字段必须唯一，作为增量判断的主键
- **REQ-014**: `documents` 表必须包含 `file_mtime` 字段，类型为 REAL
- **REQ-015**: FTS5 表必须手动维护（插入/删除），不使用触发器
- **REQ-016**: 分类规则 `CATEGORY_RULES` 列表顺序即为匹配优先级，`other` 必须位于末尾

### 3.4 性能约束

- **CON-001**: 增量导入 200 篇文档的耗时必须 < 1 秒
- **CON-002**: 搜索响应时间必须 < 100ms（200 篇文档规模）
- **CON-003**: 全量导入 200 篇文档的耗时必须 < 5 秒
- **CON-004**: 服务启动时间（含增量导入）必须 < 3 秒

### 3.5 安全约束

- **SEC-001**: 服务仅监听 `0.0.0.0`，不提供认证机制（定位为本地单机使用）
- **SEC-002**: 所有 API 响应必须设置 `Access-Control-Allow-Origin: *`
- **SEC-003**: 搜索查询必须过滤特殊字符，防止 FTS5 查询语法错误
- **SEC-004**: HTML 渲染必须对原始内容进行转义，防止 XSS

### 3.6 编码规范

- **GUD-001**: Python 代码使用 UTF-8 编码，Windows 控制台输出需做编码修复
- **GUD-002**: 前端代码使用 UTF-8 编码，HTML 声明 `<meta charset="UTF-8">`
- **GUD-003**: 所有用户可见文本使用中文
- **GUD-004**: 数据库字段命名使用 snake_case

## 4. Interfaces & Data Contracts

### 4.1 HTTP API 接口

#### GET /api/stats

返回系统统计信息。

```json
{
  "total_documents": 211,
  "total_categories": 16,
  "total_words": 1974338
}
```

#### GET /api/categories

返回分类列表（含文档计数）。

```json
{
  "categories": [
    {
      "id": 1,
      "slug": "java",
      "name": "Java与JVM",
      "icon": "☕",
      "doc_count": 58
    }
  ]
}
```

#### GET /api/documents

参数：`category`（可选，slug）、`page`（默认 1）、`limit`（默认 20）

```json
{
  "documents": [
    {
      "id": 100,
      "title": "《Effective Java》章节总结",
      "filename": "《Effective Java 中文版》章节总结.md",
      "word_count": 12000,
      "created_at": "2026-05-23",
      "updated_at": "2026-05-23",
      "category_name": "Java与JVM",
      "category_slug": "java",
      "icon": "☕"
    }
  ],
  "total": 58,
  "page": 1,
  "limit": 20,
  "pages": 3
}
```

#### GET /api/search

参数：`q`（查询词）、`page`（默认 1）、`limit`（默认 20）

```json
{
  "documents": [
    {
      "id": 100,
      "title": "《Effective Java》章节总结",
      "filename": "《Effective Java 中文版》章节总结.md",
      "word_count": 12000,
      "created_at": "2026-05-23",
      "updated_at": "2026-05-23",
      "category_name": "Java与JVM",
      "category_slug": "java",
      "icon": "☕",
      "rank": -5.06,
      "snippet": "...核心论点 Java不仅仅是一门编程语言..."
    }
  ],
  "total": 15,
  "page": 1,
  "limit": 20,
  "pages": 1,
  "query": "Java"
}
```

#### GET /api/document

参数：`id`（文档 ID）

```json
{
  "document": {
    "id": 100,
    "category_id": 2,
    "filename": "《Effective Java 中文版》章节总结.md",
    "title": "《Effective Java》章节总结",
    "filepath": "D:\\阅读\\总结文档\\《Effective Java 中文版》章节总结.md",
    "content": "纯文本内容...",
    "html_content": "<h1 id=\"...\">...",
    "word_count": 12000,
    "file_mtime": 1716480000.0,
    "created_at": "2026-05-23",
    "updated_at": "2026-05-23",
    "category_name": "Java与JVM",
    "category_slug": "java",
    "icon": "☕"
  }
}
```

#### GET /api/sync

增量同步接口，仅处理新增/变更文件。

```json
{
  "status": "ok",
  "imported": 2,
  "updated": 0,
  "skipped": 209
}
```

#### GET /api/reindex

全量重建索引接口。

```json
{
  "status": "ok",
  "message": "全量重建完成",
  "imported": 211,
  "updated": 0,
  "skipped": 0
}
```

#### GET /api/recategorize

参数：`id`（文档 ID）、`slug`（新分类 slug）

```json
{
  "status": "ok",
  "message": "文档 #100 已移至分类 'Java与JVM'"
}
```

### 4.2 数据库 Schema

#### categories 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 自增主键 |
| slug | TEXT | UNIQUE NOT NULL | 分类标识 |
| name | TEXT | NOT NULL | 显示名称 |
| icon | TEXT | DEFAULT '📁' | Emoji 图标 |
| sort_order | INTEGER | DEFAULT 0 | 排序权重 |

#### documents 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 自增主键 |
| category_id | INTEGER | NOT NULL, FK → categories.id | 分类关联 |
| filename | TEXT | NOT NULL | 原始文件名 |
| title | TEXT | NOT NULL | 文档标题 |
| filepath | TEXT | UNIQUE NOT NULL | 绝对路径（增量判断主键） |
| content | TEXT | NOT NULL | 纯文本内容（搜索用） |
| html_content | TEXT | NOT NULL | HTML 渲染内容 |
| word_count | INTEGER | DEFAULT 0 | 字数统计 |
| file_mtime | REAL | DEFAULT 0 | 文件修改时间戳 |
| created_at | TEXT | — | 创建日期（YYYY-MM-DD） |
| updated_at | TEXT | — | 更新日期（YYYY-MM-DD） |

#### docs_fts 表（FTS5 虚拟表）

| 字段 | 说明 |
|------|------|
| title | 文档标题 |
| content | 搜索内容 |
| category_name | 分类名称 |
| tokenize | trigram |

### 4.3 前端状态模型

```javascript
App.state = {
  view: 'home' | 'category' | 'search' | 'article',
  categories: Array<Category>,
  documents: Array<Document>,
  currentCategory: string | null,
  currentDoc: Document | null,
  searchQuery: string,
  page: number,
  totalPages: number,
  totalDocs: number,
  stats: { total_documents, total_categories, total_words },
  theme: 'dark' | 'light',
  tocItems: Array<{ id, level, text }>,
  activeTocId: string | null,
};
```

## 5. Acceptance Criteria

- **AC-001**: 给定服务已启动，当访问 `http://localhost:8080/api/stats`，Then 返回包含 `total_documents`、`total_categories`、`total_words` 的 JSON 对象
- **AC-002**: 给定数据库为空，当服务启动，Then 自动执行全量导入，`docs/` 子目录和根目录的 `.md` 文件全部入库
- **AC-003**: 给定数据库已有 N 篇文档，当服务重启，Then 自动执行增量导入，未变更文件全部跳过，耗时 < 1 秒
- **AC-004**: 给定新增一个 `.md` 文件到根目录，当调用 `/api/sync`，Then 返回 `imported: 1`，文档自动分类并入库
- **AC-005**: 给定修改一个已入库的 `.md` 文件，当调用 `/api/sync`，Then 返回 `updated: 1`，文档内容更新
- **AC-006**: 给定搜索词 "Java"，当调用 `/api/search?q=Java`，Then 返回包含 "Java" 关键词的文档列表，按相关度排序
- **AC-007**: 给定搜索词 "黑格尔"（中文），当调用 `/api/search?q=黑格尔`，Then 返回包含 "黑格尔" 的文档（trigram 中文匹配）
- **AC-008**: 给定文档标题包含 "Android"，当导入后，Then 分类为 "移动端开发"
- **AC-009**: 当点击主题切换按钮，Then 页面在暗色/亮色间切换，偏好保存到 `localStorage`
- **AC-010**: 当进入文章页，Then 右侧显示 TOC 导航，滚动时自动高亮当前章节

## 6. Test Automation Strategy

当前项目未引入自动化测试框架。建议的测试策略：

- **测试级别**：集成测试（验证 API 返回值）、端到端测试（验证页面渲染）
- **框架建议**：`pytest`（后端 API）、`Playwright`（前端 E2E）
- **测试数据**：使用独立的测试数据库 `data/test.db`，测试前自动创建，测试后自动清理
- **CI/CD**：GitHub Actions 流水线，推送时自动运行测试
- **覆盖率目标**：后端 API 覆盖率 ≥ 80%

## 7. Rationale & Context

### 7.1 为什么选择 SQLite FTS5？

- 零配置，无需额外部署搜索引擎
- trigram tokenizer 天然支持中文子串匹配，无需中文分词库
- 200 篇文档规模下，搜索性能完全满足需求
- 单文件数据库，便于备份和迁移

### 7.2 为什么使用增量导入？

- 文档库增长后，全量导入耗时线性增长
- 增量导入通过 mtime 判断，跳过未变更文件，启动时间与文档数量无关
- 支持热更新：新增/修改文件后调用 `/api/sync` 即可

### 7.3 为什么前端零框架？

- 项目定位为个人/小团队本地使用，无需复杂状态管理
- 零构建工具，双击即可开发调试
- 长期维护成本低，无依赖升级风险

### 7.4 为什么使用双重分类匹配？

- 文件名匹配覆盖大部分场景，但部分文档文件名不包含明确分类关键词
- 内容匹配作为兜底，提升分类覆盖率
- 双重匹配后，"其他"分类文档数接近 0

## 8. Dependencies & External Integrations

### 技术平台依赖

- **PLT-001**: Python 3.10+ — 需要支持 `http.server`、`sqlite3`、`FTS5 trigram`
- **PLT-002**: 现代浏览器 — 需要支持 CSS 变量、`localStorage`、`fetch` API、ES6+

### 外部系统

- 无外部服务依赖，完全离线运行

### 数据依赖

- **DAT-001**: Markdown 源文件 — 存放在 `DOCS_SRC` 目录及 `DOCS_CATEGORIES_DIR` 子目录中，UTF-8 编码

## 9. Examples & Edge Cases

### 9.1 增量导入流程

```
启动 → init_db() → 检查文档数 > 0 → import_documents(incremental=True)
  → 遍历所有 .md 文件
  → filepath 在 DB 中且 mtime 未变 → skip
  → filepath 不在 DB 中 → 分类 → 插入 → FTS5 索引
  → filepath 在 DB 中但 mtime 变更 → 删除旧记录 → 重新插入 → FTS5 索引
```

### 9.2 搜索降级策略

```
FTS5 MATCH 查询 → 成功 → 返回结果
FTS5 MATCH 查询 → OperationalError（语法错误）→ 降级为 LIKE '%keyword%' 查询
```

### 9.3 分类匹配示例

```
文件名: "《Android开发范例代码大全（第2版）》章节总结.md"
  → 文件名匹配: "android" ∈ "android开发范例..." → slug = "mobile"

文件名: "《Effective Java 中文版》章节总结.md"
  → 文件名匹配: "effective java" ∈ "effective java 中文版" → slug = "java"

文件名: "some-unknown-doc.md"
  → 文件名匹配: 无匹配
  → 内容匹配: 前 500 字包含 "spring boot" → slug = "java"
  → 兜底: slug = "other"
```

### 9.4 Edge Cases

- **空文件**: 跳过导入（`st_size == 0`）
- **重复文件名**: 通过 `filepath` UNIQUE 约束去重
- **特殊字符搜索**: 过滤 FTS5 不支持的字符，降级为 LIKE 查询
- **超大文件**: 内容匹配仅取前 500 字，避免读取大文件影响性能

## 10. Validation Criteria

- 所有 API 接口返回正确的 HTTP 状态码（200 或 404）
- 增量导入后，文档总数 = 源文件数（不含空文件）
- 搜索已知关键词能返回包含该关键词的文档
- 主题切换后刷新页面，主题偏好保持
- 数据库文件在服务重启后数据不丢失
- 并发调用 `/api/sync` 不会导致数据重复

## 11. Related Specifications / Further Reading

- [技术文档](./docs/技术文档.md) — 详细技术实现文档
- [优化方案](./docs/优化方案.md) — 功能优化路线图
- [SQLite FTS5 官方文档](https://www.sqlite.org/fts5.html)
- [Prism.js 官方文档](https://prismjs.com/)
