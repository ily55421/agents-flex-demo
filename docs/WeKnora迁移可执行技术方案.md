# WeKnora → agents-flex-demo-self 迁移可执行技术方案

> 配套分析文档：《WeKnora功能模块分析与迁移适配方案.md》（模块全景、证据来源、差距矩阵）
> 落地形态：**原地增强** `src/main/java/com/agentsflex/showcase/knowledge/`，保留 RogueMemory（HNSW+BM25、mmap）+ DuckDB + 单进程零中间件架构
> 编码约定：Java 21、Spring Boot 3.5、中文 javadoc、DuckDB `JdbcTemplate` Store 模式（`ensureSchema()` @PostConstruct 幂等建表 + `Map<String,Object>` 视图返回）、Controller 只做校验与格式转换

---

## 目录

- [Phase 0 准备：依赖、配置与约束](#phase-0-准备依赖配置与约束)
- [Phase 1 多格式文档解析（P0-1）](#phase-1-多格式文档解析p0-1)
- [Phase 2 结构化切片升级（P0-2）](#phase-2-结构化切片升级p0-2)
- [Phase 3 异步灌入流水线（P0-3）](#phase-3-异步灌入流水线p0-3)
- [Phase 4 Rerank 与检索增强（P0-4、P0-5）](#phase-4-rerank-与检索增强p0-4p0-5)
- [Phase 5 多知识库（P1-1、P1-2）](#phase-5-多知识库p1-1p1-2)
- [Phase 6 FAQ 与标签（P1-3、P1-4）](#phase-6-faq-与标签p1-3p1-4)
- [Phase 7 Agent 编排增强与轻量多租户（P1-5）](#phase-7-agent-编排增强与轻量多租户p1-5)
- [附录 A 全阶段验证矩阵](#附录-a-全阶段验证矩阵)
- [附录 B 回滚与兼容策略](#附录-b-回滚与兼容策略)
- [附录 C 与 WeKnora 的能力对照口径](#附录-c-与-weknora-的能力对照口径)

---

## 总体实施原则

1. **每个 Phase 独立可交付、独立验证、独立提交**：一次只加一个特性，`mvn test` 通过后再进入下一阶段（避免多特性叠加导致无法定位问题）。
2. **向后兼容优先**：现有测试 `KnowledgeServiceTest` 使用 3 参构造与同步 `addDocument`，前端 `knowledgeApi` 期望 `/documents`、`/documents/upload` 立即返回 `KnowledgeDocument`。**同步路径保持不变**，异步能力以新增入口/新增字段的方式叠加。
3. **能力可降级**：无 embedding 服务 → BM25；无 rerank 服务 → 向量分数排序；无解析依赖 → 回退纯文本读取。任何新增能力缺失都不得阻断启动。
4. **不引入中间件**：不新增 Postgres/Redis/Python 进程；不引入 Java 之外的服务。

---

## Phase 0 准备：依赖、配置与约束

### 目标

引入 Java 原生解析依赖，建立配置项与测试基线，不动任何业务逻辑。

### 涉及文件

| 文件 | 动作 |
| --- | --- |
| `pom.xml` | 新增 4 个依赖（见下） |
| `src/main/resources/application.yml` | `agents-flex.knowledge.*` 新增切片/解析/ingest/rerank 配置 |
| `src/main/java/com/agentsflex/showcase/knowledge/KnowledgeProperties.java` | 新增对应属性字段与 getter/setter |

### 依赖清单（pom.xml）

```xml
<!-- 文档解析：PDF / Office / HTML 三种原生解析器（替代 WeKnora 的 Python docreader） -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.4.1</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-scratchpad</artifactId>
    <version>5.4.1</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.19.1</version>
</dependency>
```

> **不引入 Markdown 解析库**：切片器只需识别标题/代码块/表格的行级结构，用行状态机实现（Phase 2），避免 flexmark-all 约 15MB 的体积与额外依赖树。
> **POI 与 log4j 冲突**：POI 传递依赖 `log4j-api`，本项目使用 logback；Spring Boot 的 `spring-boot-starter-logging` 已在类路径，POI 只用 `log4j-api` 接口（无绑定）即可，不需要排除项。若启动时出现 log4j 绑定告警，在 pom 中排除 `org.apache.logging.log4j:log4j-api` 的 `log4j-core` 传递项。

### 配置项（application.yml 增量）

```yaml
  knowledge:
    # —— 切片（Phase 2）——
    chunk-size: ${KNOWLEDGE_CHUNK_SIZE:512}
    chunk-overlap: ${KNOWLEDGE_CHUNK_OVERLAP:80}
    # —— 解析（Phase 1）——
    max-parse-bytes: ${KNOWLEDGE_MAX_PARSE_BYTES:52428800}   # 单文件解析上限 50MB（对齐 docreader MAX_FILE_SIZE_MB）
    # —— 异步灌入（Phase 3）——
    ingest-concurrency: ${KNOWLEDGE_INGEST_CONCURRENCY:2}
    ingest-queue-capacity: ${KNOWLEDGE_INGEST_QUEUE_CAPACITY:100}
    # —— 重排（Phase 4）——
    rerank-enabled: ${KNOWLEDGE_RERANK_ENABLED:false}
    rerank-endpoint: ${RERANK_ENDPOINT:}
    rerank-api-key: ${RERANK_API_KEY:}
    rerank-model: ${RERANK_MODEL:}
    rerank-top-k: ${KNOWLEDGE_RERANK_TOP_K:20}
```

### 验证

```bash
mvn -q -DskipTests dependency:tree | grep -E "pdfbox|poi|jsoup"   # 依赖进入类路径
mvn test                                                          # 现有 4 个测试仍全绿
```

### 回滚

删除 pom 依赖与 yml 配置即可，无代码副作用。

---

## Phase 1 多格式文档解析（P0-1）

### 目标

把入库输入从「UTF-8 纯文本」扩展为「PDF / Word / Excel / PPT / HTML / Markdown / 文本」统一解析为 **Markdown + 元数据**，对齐 WeKnora docreader 的产物契约（`ReadResponse{markdown_content, metadata, error}`）。

### 设计

**新增包**：`src/main/java/com/agentsflex/showcase/knowledge/parser/`

```java
/**
 * 文档解析器：把任意受支持格式的字节流解析为统一的 Markdown 正文与元数据。
 *
 * <p>对齐 WeKnora docreader 的产物契约：所有格式最终都归一为 Markdown 文本，
 * 使下游切片器只需处理一种输入形态。实现必须是无状态且线程安全的。</p>
 */
public interface DocumentParser {

    /** @return 该解析器负责的文件扩展名（小写、不含点号），如 "pdf"、"docx" */
    List<String> extensions();

    /**
     * 解析文档。
     *
     * @param fileName 原始文件名（用于标题与格式判定）
     * @param bytes    文件字节流
     * @return 解析产物（Markdown 正文 + 元数据）
     * @throws DocumentParseException 解析失败（消息为可操作的中文提示）
     */
    ParsedDocument parse(String fileName, byte[] bytes) throws DocumentParseException;
}

/** 解析产物：Markdown 正文 + 元数据 + 结构统计。 */
public final class ParsedDocument {
    private final String markdown;              // 归一化正文（Markdown）
    private final Map<String, String> metadata; // 如 pageCount / sheetCount / slideCount / parser
    private final int blockCount;               // 结构块数（标题/段落/表格），供切片统计

    // getter + toView()：返回 Map<String,Object> 供 Controller 直接序列化
}
```

**实现清单**（每个文件独立可测）：

| 文件 | 覆盖格式 | 实现要点 |
| --- | --- | --- |
| `TextDocumentParser.java` | txt / md / markdown / jsonl / csv | 直接 UTF-8 读取；md 原样返回（保留标题结构供切片使用）；csv 转 Markdown 表格 |
| `PdfDocumentParser.java` | pdf | PDFBox `PDFTextStripper` 逐页提取，页间插入 `<!-- page:N -->` 标记，元数据记 `pageCount`；文本为空时明确提示"疑似扫描件，当前版本不支持 OCR" |
| `WordDocumentParser.java` | docx | POI `XWPFDocument`：标题样式（Heading 1-6）→ `#` 前缀、正文 → 段落、表格 → Markdown 表格；`XWPFHeaderFooterPolicy` 内容跳过 |
| `SpreadsheetDocumentParser.java` | xlsx / xls | POI `Workbook`：每个 sheet 输出 `## {sheetName}` + Markdown 表格；空行裁剪；单元格换行替换为 `<br>` |
| `PresentationDocumentParser.java` | pptx / ppt | POI `XMLSlideShow`/`HSLFSlideShow`：每页 `## Slide N` + 形状文本（标题优先） |
| `HtmlDocumentParser.java` | html / htm | jsoup：`h1-h6`→`#`、`p`→段落、`table`→Markdown 表格、`pre/code`→围栏代码块、剥离 script/style/nav |
| `DocumentParserRegistry.java` | — | `@Component`，按扩展名路由；未命中时抛出可操作错误："不支持的格式 .xxx，当前支持：pdf/docx/doc/xlsx/xls/pptx/ppt/md/txt/html/jsonl/csv" |

**改造 `KnowledgeController.uploadDocument`**：

```java
// 旧：仅 .txt/.md/.markdown/.jsonl，直接 new String(file.getBytes(), UTF_8)
// 新：交给 registry 解析，成功后走原有 addDocument(title, markdown, "FILE") 同步入库
ParsedDocument parsed = parserRegistry.parse(originalName, file.getBytes());
return knowledgeService.addDocument(resolvedTitle, parsed.getMarkdown(), "FILE");
```

保留：`ALLOWED_EXTENSIONS` 校验改为委派 registry（`parserRegistry.supports(extension)`）；`maxUploadBytes` 保持 2MB 上传限制，另加 `maxParseBytes` 50MB 用于解析保护。

**JSONL 分支保持原样**（走 `addJsonlDocument`，Phase 6 再升级为 FAQ 条目）。

### API 变更

| Method | Path | 变更 |
| --- | --- | --- |
| POST | `/api/knowledge/documents/upload` | 接受格式扩展为 11 种；响应增加 `parser`、`pageCount` 等元数据字段（追加，不破坏现有字段） |
| GET | `/api/knowledge/parsers` | **新增**：返回 `[{extension, parser, description}]`，供前端文件选择器限制与提示 |

### 测试

新增 `src/test/java/com/agentsflex/showcase/knowledge/parser/DocumentParserTest.java`：

- 每个解析器用**内联生成的最小样例**（POI 动态生成 docx/xlsx/pptx 到字节数组、jsoup 解析 HTML 字符串、PDFBox 动态生成单页 PDF）验证 Markdown 产出与元数据，避免二进制测试夹具入库；
- 断言标题层级被保留（`#` 前缀）、表格被转为 Markdown 表格、未知扩展名抛出含支持列表的异常。

### 验证

```bash
mvn test -Dtest="DocumentParserTest,KnowledgeServiceTest"
# 手工：上传 docx/pdf 各一份，GET /api/knowledge/documents/{docId} 预览 Markdown 正文
curl -F "file=@sample.docx" http://localhost:8080/api/knowledge/documents/upload
curl http://localhost:8080/api/knowledge/parsers
```

### 回滚

删除 `parser/` 包与 `/parsers` 端点，Controller 恢复只接受 txt/md/jsonl。

---

## Phase 2 结构化切片升级（P0-2）

### 目标

对齐 WeKnora `internal/infrastructure/chunker/`：**保护结构块不被切断** + **标题层级上下文注入 chunk 元数据** + **参数可配**。

### 设计

把 `TextChunker` 升级为 `MarkdownChunker`（新类，保留 `TextChunker` 不动以避免破坏既有测试与调用点）：

```java
/**
 * 面向 Markdown 的结构感知切片器：按标题层级切分，保护表格/代码块/公式不被截断。
 *
 * <p>对齐 WeKnora chunker 的两条核心规则：① 保护结构块（代码围栏、Markdown 表格、
 * 行内公式）内部不做硬切；② 每个 chunk 携带其所属标题路径（如
 * "知识库设计 > 切片策略"），供检索后上下文补全与引用展示。超长块再按窗口滑切，
 * 重叠长度可配；切片以字符数计量（对中文与 Token 数近似稳定）。</p>
 */
public final class MarkdownChunker {

    public MarkdownChunker() { this(512, 80); }

    public MarkdownChunker(int chunkChars, int overlapChars) { /* 参数校验同 TextChunker */ }

    /**
     * @param markdown 归一化 Markdown 正文
     * @return 切片列表，每片带 heading 路径与序号
     */
    public List<MarkdownChunk> chunk(String markdown);

    /** 切片视图：文本 + 标题路径 + 序号 + 是否被截断。 */
    public static final class MarkdownChunk {
        private final String content;
        private final String headingPath;   // "一级 > 二级"，无标题时为空串
        private final int index;
        // getter
    }

    /** 切片预览：只算不写，供 /chunker/preview 调试。 */
    public List<Map<String, Object>> preview(String markdown, int chunkChars, int overlapChars);
}
```

**行状态机规则**（关键实现点，按行扫描维护状态）：

| 状态 | 触发 | 行为 |
| --- | --- | --- |
| 代码围栏 | 行首 ` ``` ` 或 `~~~` | 进入/退出 code 状态；**code 内部永不切分**，整块作为一个切片单元 |
| Markdown 表格 | 连续 `\|` 开头行 | 整表作为**一个原子单元**（超长时按行组切分，表头重复） |
| 标题 | `^#{1,6}\s` | 更新标题栈（`#` 层级回退时弹出栈），标题行**开启新切片**，并把 headingPath 写入该片及其后续片 |
| 公式块 | `$$` 成对 | 同代码围栏，原子单元 |
| 普通段落 | 其他 | 累积到当前切片；超过 chunkChars 时在**段落边界**切分，跨段时按窗口滑切并保留 overlapChars 重叠 |

**元数据注入**（对齐 WeKnora 的 header_tracker）：chunk 写入 RogueMemory 时 metadata 增加 `headingPath`，检索命中后可在前端与工具输出中展示"来自哪一节"。

**参数可配**：`KnowledgeProperties.chunkSize=512 / chunkOverlap=80`（对齐 WeKnora 默认值；现项目为 500/50）。

**接入点**：`KnowledgeService` 内 `private final TextChunker chunker = new TextChunker();` → 替换为 `MarkdownChunker`，三处调用点（`addDocument` / `updateDocument` / `rebuildFromStore`）改为写入 `chunk.getContent()` 与 `chunk.getHeadingPath()`。

**新增 API**（对齐 WeKnora `/chunker/preview`）：

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/knowledge/chunker/preview` | body：`{content, chunkSize?, overlap?}`；返回 `[{index, headingPath, charCount, content}]`，不落库 |

### 测试

新增 `src/test/java/com/agentsflex/showcase/knowledge/MarkdownChunkerTest.java`：

- 代码块内含 `\n\n` 与超长行 → 不被切断，整块完整出现在同一片；
- Markdown 表格 → 不被从中间截断，且长表切分后每片含表头；
- 标题层级 → `headingPath` 正确（`# A` 下的段落 path="A"；`## B` 后 path="A > B"；同级 `## C` 后 path="A > C"）；
- 超长段落 → 切片数 = ceil((len-overlap)/(chunkSize-overlap))，相邻片重叠字符数 == overlapChars；
- 空输入/纯空白 → 空列表。

### 验证

```bash
mvn test -Dtest="MarkdownChunkerTest,KnowledgeServiceTest"
curl -X POST http://localhost:8080/api/knowledge/chunker/preview \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"# 标题\n\n正文段落...\n\n\`\`\`java\nSystem.out.println();\n\`\`\`\n\n| a | b |\n|---|---|\n| 1 | 2 |\"}"
```

### 回滚

`KnowledgeService` 切回 `TextChunker`；`MarkdownChunker` 与其测试保留不引用。

---

## Phase 3 异步灌入流水线（P0-3）

### 目标

对齐 WeKnora `knowledge_process.go` + task_queue：**上传即返回任务**，解析/切片/向量化在后台执行，状态可查、可取消、可重建，避免大文件上传阻塞请求线程。

### 设计

**新增 DuckDB 任务表**（`IngestTaskStore.java`，沿用 `ensureSchema()` 模式）：

```sql
CREATE TABLE IF NOT EXISTS knowledge_ingest_task (
  task_id VARCHAR PRIMARY KEY,
  doc_id VARCHAR,              -- 成功后回填
  knowledge_base_id VARCHAR,   -- Phase 5 前固定为 'default'
  title VARCHAR NOT NULL,
  source VARCHAR NOT NULL,     -- MANUAL / FILE / URL / REPARSE
  file_name VARCHAR,
  file_type VARCHAR,
  status VARCHAR NOT NULL,     -- PENDING / PROCESSING / FINALIZING / COMPLETED / FAILED / CANCELLED
  stage VARCHAR,               -- PARSING / CHUNKING / EMBEDDING / INDEXING
  progress INTEGER NOT NULL,   -- 0-100
  char_count BIGINT NOT NULL,
  chunk_count INTEGER NOT NULL,
  error VARCHAR,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
```

**状态机**（对齐 WeKnora `parse_status`）：

```text
PENDING → PROCESSING(PARSING → CHUNKING → EMBEDDING → INDEXING) → FINALIZING → COMPLETED
                                                              ↘ FAILED（error 写入原因）
PENDING/PROCESSING → CANCELLED（用户取消，检查点在阶段边界）
```

**新增 `IngestPipeline.java`**：

```java
/**
 * 知识灌入流水线：解析 → 切片 → 向量化 → 建索引，全程异步并落任务表。
 *
 * <p>对齐 WeKnora 的 knowledge_process 编排：任务状态机写入 DuckDB，前端轮询进度；
 * 每个阶段边界检查取消标志，取消后不再继续后续阶段（已写入的切片由调用方回滚）。
 * 线程池并发与队列容量可配，队列满时立即返回失败任务而不是阻塞上传请求。</p>
 */
@Component
public class IngestPipeline {

    @jakarta.annotation.PostConstruct
    void init();   // 创建 ThreadPoolTaskExecutor（coreSize=ingest-concurrency，队列=ingest-queue-capacity，线程名前缀 knowledge-ingest-）

    /** 提交文本入库任务（粘贴路径），立即返回任务视图。 */
    public Map<String, Object> submitText(String title, String content, String source);

    /** 提交文件入库任务（上传路径），立即返回任务视图。 */
    public Map<String, Object> submitFile(String fileName, byte[] bytes, String title);

    /** 提交单篇文档重建任务（对齐 WeKnora reparse）。 */
    public Map<String, Object> submitReparse(String docId);

    /** @return 任务视图；不存在返回 null */
    public Map<String, Object> task(String taskId);

    /** @return 最近任务列表（按创建时间倒序，上限 100） */
    public List<Map<String, Object>> tasks();

    /** 请求取消：置 CANCELLED 标志，工作线程在阶段边界退出。 */
    public boolean cancel(String taskId);

    @jakarta.annotation.PreDestroy
    void shutdown();   // 优雅关闭线程池
}
```

**执行体**（`IngestWorker`，包内类或私有方法）调用既有同步能力：

```java
// 复用 KnowledgeService 的同步核心，不重复实现写索引逻辑：
Map<String, Object> doc = fileName == null
        ? knowledgeService.addDocument(title, content, source)
        : knowledgeService.addDocument(title, parserRegistry.parse(fileName, bytes).getMarkdown(), source);
```

**API 变更**（追加式）：

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/knowledge/documents/upload/async` | **新增**：上传并立即返回任务视图 |
| POST | `/api/knowledge/documents/async` | **新增**：粘贴文本异步入库 |
| POST | `/api/knowledge/documents/{docId}/reparse/async` | **新增**：异步重建单篇索引 |
| GET | `/api/knowledge/tasks` | **新增**：任务列表 |
| GET | `/api/knowledge/tasks/{taskId}` | **新增**：任务详情（含 status/stage/progress/error） |
| POST | `/api/knowledge/tasks/{taskId}/cancel` | **新增**：取消任务 |

> 现有同步端点（`/documents`、`/documents/upload`）**保持不变**，以保证 `KnowledgeServiceTest` 与现有前端行为不变；异步入口为叠加能力。

### 测试

新增 `src/test/java/com/agentsflex/showcase/knowledge/IngestPipelineTest.java`（KEYWORD_ONLY 模式，不依赖外部服务）：

- 提交文本任务 → 轮询到 `COMPLETED`（带超时上限 10s），`doc_id` 回填且文档可检索；
- 提交空白内容 → 任务进入 `FAILED` 且 `error` 含中文原因，不抛异常到调用方；
- `cancel` 一个尚未开始执行的任务（队列容量 1、先塞满）→ 状态变为 `CANCELLED`；
- 队列满 → `submitText` 返回 `FAILED` 任务而不是阻塞。

### 验证

```bash
mvn test -Dtest="IngestPipelineTest,KnowledgeServiceTest"
curl -F "file=@big.pdf" http://localhost:8080/api/knowledge/documents/upload/async
curl http://localhost:8080/api/knowledge/tasks
```

### 回滚

删除 `IngestPipeline`/`IngestTaskStore` 与 6 个新端点；同步端点从未改动，前端可立即回落。

---

## Phase 4 Rerank 与检索增强（P0-4、P0-5）

### 目标

对齐 WeKnora `internal/models/rerank/` + `chat_pipeline/rerank.go`：检索后、TopK 截断前插入重排；并把检索结果升级为**带引用标记**的结构化输出（对齐 `cN`/`dN` 引用）。

### 设计

**新增 `RerankProvider.java`**（OpenAI 兼容 rerank API：Jina / 阿里 DashScope / 硅基流动 均支持 `{model, query, documents[], top_n}` → `{results:[{index, relevance_score}]}`）：

```java
/**
 * 检索结果重排：调用 OpenAI 兼容的 /rerank 接口对候选片段重排序。
 *
 * <p>对齐 WeKnora 的 Reranker 抽象：接口保持最小（Rerank(query, documents) → 分数），
 * 供应商差异由 endpoint 与模型名承担。未配置或调用失败时返回 null，由调用方保持
 * 向量/BM25 原始顺序——重排是增强而非必需依赖。</p>
 */
@Component
public class RerankProvider {

    /**
     * @param query     查询文本
     * @param documents 候选片段正文（长度与返回分数一一对应）
     * @return 每个候选的相关性分数；未启用或失败返回 null
     */
    public float[] rerank(String query, List<String> documents);

    /** @return 是否已配置可用的重排服务 */
    public boolean available();
}
```

**接入 `KnowledgeService.search`**：

```java
// 1. 先按 rerankTopK（默认 20）放大召回
// 2. rerankProvider.available() 时重排，按新分数排序
// 3. 截断到调用方 topK
// 4. 返回视图新增字段：rank（重排后名次）、originalScore、rerankScore、headingPath
```

**引用标记（P0-5）**：`searchForTool` 输出升级——每个命中前加稳定引用号，供模型在回答中引用、前端解析：

```text
知识库检索命中 3 条片段：
[c1] 【切片策略 · 第 2 片 · 小节「切片 > 保护规则」 · 相关度 0.842】生产 RAG 系统...
[c2] ...
```

并新增结构化方法供 Agent 工具直接使用：

```java
/**
 * @return 结构化检索结果：hits（含 citeId/headingPath/score/content）+ 渲染后的引用文本
 */
public Map<String, Object> searchDetailed(String query, int topK, String namespace);
```

**配置**：`rerank-enabled / rerank-endpoint / rerank-api-key / rerank-model / rerank-top-k`（Phase 0 已加）。`status()` 增加 `rerankConfigured`、`rerankModel`。

### 测试

新增 `RerankProviderTest`（不起网络：用 `null` 配置断言 `available()==false` 且 `rerank()` 返回 null）与 `KnowledgeServiceTest` 增补用例：

- 未配置 rerank 时 `search` 结果顺序与分数与升级前一致（**回归保护**）；
- `searchForTool` 输出包含 `[c1]`、`[c2]` 递增引用号与 headingPath；
- `searchDetailed` 返回 hits 强类型化字段（citeId 从 1 递增）。

> 真实 rerank 服务连通性验证放在 Phase 4 的**手工验证**步骤（本机可指向任意 OpenAI 兼容 rerank 服务），CI 不依赖网络。

### 验证

```bash
mvn test -Dtest="KnowledgeServiceTest,RerankProviderTest"
# 手工（配置了 rerank 服务时）：
curl -X POST http://localhost:8080/api/knowledge/search -H "Content-Type: application/json" \
  -d "{\"query\":\"混合检索\",\"topK\":5}"   # 观察 rank/rerankScore 字段与排序变化
```

### 回滚

`rerank-enabled=false`（默认值）即完全等价于升级前行为。

---

## Phase 5 多知识库（P1-1、P1-2）

### 目标

对齐 WeKnora `knowledge_bases` 表：从「单一全局库 + namespace=docId」升级为「多知识库 + 两级 namespace」；并把检索参数从全局配置提升为**按库/按会话可配**。

### 设计

**namespace 布局决策**：RogueMemory 单实例、单 mmap 文件（当前固定 1GB），namespace 由 `docId` 改为 **`{kbId}/{docId}`** 两级字符串。理由：改动最小、无多文件管理成本、检索时用 `namespace prefix` 过滤（RogueMemory `SearchOptions.namespace(...)` 支持精确 namespace；按库检索通过"逐 docId 检索后合并"或"库级 namespace 前缀扫描"实现——见下实现要点）。

```java
// 方案 A（默认，改动最小）：检索按库过滤在应用层完成
//   - 库检索：取出该库全部 docId → 逐个 namespace 检索 → 合并排序截断
// 方案 B（备选，性能更优）：库级 namespace = "{kbId}/"，文档级 = "{kbId}/{docId}"
//   - 若 RogueMemory 的 namespace 过滤支持前缀匹配则直接用；否则回落方案 A
```

**DuckDB 新表**（`KnowledgeBaseStore.java`）：

```sql
CREATE TABLE IF NOT EXISTS knowledge_base (
  kb_id VARCHAR PRIMARY KEY,
  name VARCHAR NOT NULL,
  description VARCHAR,
  type VARCHAR NOT NULL,              -- DOCUMENT / FAQ（Phase 6）
  chunk_size INTEGER NOT NULL,
  chunk_overlap INTEGER NOT NULL,
  top_k INTEGER NOT NULL,             -- 库级检索参数（对齐 WeKnora KB 绑定检索设置）
  rerank_enabled BOOLEAN NOT NULL,
  embedding_signature VARCHAR,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);
-- 文档表加列（幂等 ALTER，沿用 ensureSchema 的 try/catch 模式）
ALTER TABLE knowledge_document ADD COLUMN knowledge_base_id VARCHAR;
-- 任务表已有 knowledge_base_id 列（Phase 3 预留）
```

**默认库迁移**：启动时若 `knowledge_base` 为空，创建 `kb-default`（名称「默认知识库」，chunk 512/80，topK 取配置值），并把 `knowledge_document.knowledge_base_id IS NULL` 的历史文档**原地**归入默认库（`UPDATE ... SET knowledge_base_id='kb-default' WHERE knowledge_base_id IS NULL`）——历史数据无需重灌，只需在索引重建时按两级 namespace 重写。

**API**（对齐 `routes_knowledge.go`）：

| Method | Path | 用途 |
| --- | --- | --- |
| GET/POST | `/api/knowledge/bases` | 库列表 / 建库（name/description/chunkSize/chunkOverlap/topK/rerankEnabled） |
| GET/PUT/DELETE | `/api/knowledge/bases/{kbId}` | 库详情 / 改设置 / 删除（删除需级联清理文档+向量） |
| GET | `/api/knowledge/bases/{kbId}/documents` | 该库文档列表 |
| POST | `/api/knowledge/bases/{kbId}/search` | 库内检索（检索参数：库级默认 + 请求覆盖） |
| PUT | `/api/knowledge/documents/{docId}/move` | 跨库移动（body：`targetKbId`） |

**兼容**：现有 `/api/knowledge/documents`（无 kbId）默认作用于 `kb-default`；`search` 请求体新增可选 `kbId`，缺省 = 全部库（与现行为一致）。

**P1-2 会话级检索参数**：`CreateAgentRequest` 增加 `knowledgeTopK`、`knowledgeRerankEnabled`、`knowledgeKbIds`（可多库路由），`ResearchAgentFactory` 构造知识工具时使用这些参数替代全局默认。

### 测试

新增 `KnowledgeBaseStoreTest`（建库/改设置/软删/默认库自举）与 `KnowledgeServiceTest` 增补：

- 建两个库各入库一篇含唯一标记的文档 → 按库检索只命中本库文档，全库检索命中两篇；
- 库级 topK 生效（库 A topK=1 时只返回 1 条）；
- 历史数据（无 kbId）在启动自举后归入默认库且仍可检索（**迁移回归保护**）；
- 跨库移动后原库搜不到、新库搜得到。

### 验证

```bash
mvn test -Dtest="KnowledgeBaseStoreTest,KnowledgeServiceTest,MarkdownChunkerTest"
curl -X POST http://localhost:8080/api/knowledge/bases -H "Content-Type: application/json" \
  -d "{\"name\":\"电力规程\",\"chunkSize\":512,\"chunkOverlap\":80,\"topK\":5}"
curl -X POST http://localhost:8080/api/knowledge/bases/kb-xxx/search -H "Content-Type: application/json" -d "{\"query\":\"断路器\"}"
cd frontend; $env:NODE_ENV="development"; pnpm build   # 前端知识库选择器
```

### 回滚

库功能以 `kb-default` 单库形态运行（约等于升级前语义）；如需完全回退，恢复 `namespace=docId` 写法与 `/documents` 默认行为，`knowledge_base` 表闲置不影响主链路。

---

## Phase 6 FAQ 与标签（P1-3、P1-4）

### 目标

对齐 WeKnora 的 FAQ 知识库（标准问+相似问+答案，条目粒度检索）与多标签过滤检索。

### 设计

**FAQ 条目模型**（`KnowledgeBaseStore` 同库，新增表）：

```sql
CREATE TABLE IF NOT EXISTS knowledge_faq_entry (
  entry_id VARCHAR PRIMARY KEY,
  knowledge_base_id VARCHAR NOT NULL,
  standard_question VARCHAR NOT NULL,
  similar_questions VARCHAR,   -- JSON 数组文本
  answer VARCHAR NOT NULL,
  tags VARCHAR,                -- 逗号分隔
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
```

**检索口径**（对齐 WeKnora `FAQIndexMode`）：`question_only` = 仅标准问+相似问入索引；`question_answer` = 问+答合并入索引（默认 question_answer，召回更稳）。每条 FAQ 作为**一个切片单元**写入两级 namespace，metadata 带 `faqEntryId`、`questionType`。

**JSONL 导入升级**：现有 `addJsonlDocument`（取 `station/category/text`）扩展为兼容两种输入：

```jsonl
{"text":"...", "station":"...", "category":"..."}                      // 旧格式，向后兼容
{"question":"标准问", "similar":["相似问1","相似问2"], "answer":"答案"}    // 新 FAQ 格式
```

**标签体系**（对齐 `knowledge_multi_tags`）：文档表加列 `tags VARCHAR`（逗号分隔，避免建关联表以维持 Demo 简洁），新增表 `knowledge_tag(tag_id, knowledge_base_id, name, created_at)` 做标签字典。

**API**：

| Method | Path | 用途 |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/knowledge/bases/{kbId}/faq/entries[/{entryId}]` | FAQ 条目 CRUD |
| POST | `/api/knowledge/bases/{kbId}/faq/import` | JSONL/CSV 批量导入（返回任务视图，走 Phase 3 流水线） |
| GET/POST/DELETE | `/api/knowledge/tags[/{tagId}]` | 标签字典 |
| PUT | `/api/knowledge/documents/{docId}/tags` | 文档打标（body：`{tags:[...]}`） |
| POST | `/api/knowledge/bases/{kbId}/search` | 请求体新增可选 `tags:[]`，命中需满足任一标签（对齐 TagIDs OR 过滤） |

### 测试

- FAQ 条目入库后按相似问检索可命中，命中 metadata 含 `faqEntryId`；
- `question_only` 与 `question_answer` 两种索引模式的命中差异（答案文本能否被召回）；
- 旧格式 JSONL（`text/station/category`）导入仍成功（**向后兼容回归**）；
- 标签过滤：打标 A 的文档在 `tags:["B"]` 检索中不命中、`tags:["A"]` 命中。

### 验证

```bash
mvn test -Dtest="KnowledgeFaqTest,KnowledgeServiceTest"
curl -X POST http://localhost:8080/api/knowledge/bases/kb-x/faq/entries -H "Content-Type: application/json" \
  -d "{\"standardQuestion\":\"断路器拒动如何处理\",\"similarQuestions\":[\"开关不动怎么办\"],\"answer\":\"先检查操作电源...\"}"
```

### 回滚

FAQ/标签为独立表与独立端点，停用不影响文档库主链路；JSONL 旧格式分支始终保留。

---

## Phase 7 Agent 编排增强与轻量多租户（P1-5）

### 目标

对齐 WeKnora Agent 的知识工具语义（多 query、按 KB 路由、MMR、引用回传），并落地**降级版多租户**（知识库空间隔离 + 可选只读 API Key）。

### 设计

**7.1 知识工具增强**（`demo/ResearchAgentFactory.java` + `KnowledgeService.searchDetailed`）：

- 工具入参从单一 `query` 扩展为 `queries: string[]`（1-5 个，对齐 WeKnora 的 1-5 语义 query）、`knowledgeBaseIds: string[]`、`tags: string[]`；
- 多 query 各自检索后**按 docId+chunkIndex 去重合并**（同片段取最高分），再全局排序截断；
- 可选 **MMR 去冗余**（λ=0.7）：候选向量已由 RogueMemory 返回分数，MMR 仅需片段间的向量余弦——用 `KnowledgeVectorCache` 已有向量做多样性惩罚，无需额外网络调用；
- 返回结构化结果 + `[cN]` 引用文本，Agent 回答中的引用号可直接映射回片段（对齐 WeKnora `cN/dN`）。

**7.2 会话级检索参数**：Phase 5 已加 `CreateAgentRequest.knowledgeTopK / knowledgeRerankEnabled / knowledgeKbIds`，本阶段在 Runner 层面把会话参数透传到工具执行（每次工具调用读取当前 Run 的会话配置，而非创建时的全局快照）。

**7.3 轻量多租户**（对齐 `tenants` + `tenant_api_keys` 的 capability 位，大幅简化）：

```sql
CREATE TABLE IF NOT EXISTS knowledge_space (
  space_id VARCHAR PRIMARY KEY,
  name VARCHAR NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS knowledge_api_key (
  key_id VARCHAR PRIMARY KEY,
  space_id VARCHAR NOT NULL,
  key_hash VARCHAR NOT NULL,     -- SHA-256，不存明文
  capability VARCHAR NOT NULL,   -- RETRIEVE（只读）/ MANAGE（读写）
  created_at BIGINT NOT NULL
);
ALTER TABLE knowledge_base ADD COLUMN space_id VARCHAR;   -- 库归属空间
```

- Spring `HandlerInterceptor`（`WebConfig` 已存在，追加注册）校验 `/api/knowledge/**`：命中 Key → 仅放行 capability 允许的方法；未带 Key → 视为本机 Demo 模式，全放行（保持现有使用体验）；
- **明确差距**：不实现 users/JWT/RBAC 四角色/审计日志/组织共享，文档中标注为「与 WeKnora 的差距」而非等价替换。

### 测试

- 多 query 合并去重：两个 query 命中同一片段时结果只出现一次；
- 按 kbIds 路由：指定库检索不返回他库片段；
- API Key：`RETRIEVE` 能力 Key 可 GET 检索、POST 入库返回 403；`MANAGE` Key 全部放行；无 Key 全放行；
- 密钥安全：`key_hash` 列非明文，`status()`/列表接口不回显 Key。

### 验证

```bash
mvn test
$env:NODE_ENV="development"; cd frontend; pnpm build
# 手工：配置 Key 后
curl -H "Authorization: Bearer <key>" http://localhost:8080/api/knowledge/status
curl -X POST -H "Authorization: Bearer <key>" http://localhost:8080/api/knowledge/documents -d '{"title":"x","content":"y"}'   # 期望 403（只读 Key）
```

### 回滚

删除 `knowledge_space`/`knowledge_api_key` 相关代码与拦截器注册；空表闲置无副作用。

---

## 附录 A 全阶段验证矩阵

| Phase | 后端测试 | 前端 | 手工验证 | 提交 |
| --- | --- | --- | --- | --- |
| 0 依赖与配置 | `mvn test`（现有 4 测试回归） | — | `dependency:tree` | 独立提交 |
| 1 文档解析 | `DocumentParserTest` | 上传控件 accept 扩展 | 上传 docx/pdf 并预览 | 独立提交 |
| 2 结构化切片 | `MarkdownChunkerTest` | 切片预览面板 | `/chunker/preview` | 独立提交 |
| 3 异步流水线 | `IngestPipelineTest` | 任务进度条轮询 `/tasks` | 上传大文件观察进度 | 独立提交 |
| 4 Rerank 与引用 | `RerankProviderTest` + 回归 | 引用号渲染 | 配置 rerank 后对比排序 | 独立提交 |
| 5 多知识库 | `KnowledgeBaseStoreTest` | 库选择器 + 库管理页 | 双库隔离检索 | 独立提交 |
| 6 FAQ 与标签 | `KnowledgeFaqTest` | FAQ 条目管理 + 标签筛选 | JSONL 双格式导入 | 独立提交 |
| 7 编排与租户 | 工具/Key 测试 | 引用回跳、Key 管理页 | 只读 Key 403 验证 | 独立提交 |

**统一命令**：

```bash
# 后端（Note：本机 NODE_ENV=production 不影响 Maven）
mvn test
mvn -q -DskipTests package

# 前端（必须先重置 NODE_ENV，否则 devDependencies 被跳过、vitest/build 异常）
cd frontend; $env:NODE_ENV="development"; pnpm install; pnpm build
```

## 附录 B 回滚与兼容策略

| 能力 | 兼容设计 | 回滚动作 |
| --- | --- | --- |
| 文档解析 | 未知格式报错提示，不改变已支持格式行为 | 删除 parser 包，Controller 恢复扩展名白名单 |
| 结构化切片 | 新旧切片器共存，`TextChunker` 保留 | `KnowledgeService` 切回 `TextChunker` |
| 异步流水线 | 同步端点零改动，异步为新增入口 | 删除新增 6 端点与线程池 |
| Rerank | 默认关闭；失败返回 null 保持原序 | 配置置 false |
| 多知识库 | 默认库自举 + 历史文档原地归属，无重灌 | 恢复单库 namespace 写法 |
| FAQ/标签 | 独立表与端点 | 停用端点，旧 JSONL 分支保留 |
| 多租户 | 无 Key 时全放行（Demo 行为不变） | 注销拦截器 |

**数据兼容铁律**：所有表变更使用 `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ... ADD COLUMN`（try/catch 忽略"列已存在"），绝不 DROP 既有列；历史数据通过自举迁移而非重建。

## 附录 C 与 WeKnora 的能力对照口径

| 维度 | WeKnora | 本方案 | 口径 |
| --- | --- | --- | --- |
| 解析服务 | Python gRPC 进程（14 格式 + OCR/VLM 外接） | Java 进程内（11 格式，无 OCR/VLM） | ⚠️ 能力等价替换，格式与扫描件处理范围收窄 |
| 切片 | Go chunker（512/80 + 保护正则 + 标题层级 + 父子块） | MarkdownChunker（512/80 + 保护结构块 + 标题路径） | 🔧 核心规则对齐，父子块未实现 |
| 向量与检索 | pgvector/ES/Qdrant… 9 引擎 + BM25 + fanout/fusion | RogueMemory 单引擎 HNSW + BM25 | ✅ 混合检索语义等价，无多引擎与 fanout |
| 重排 | 8 provider | 1 个 OpenAI 兼容 provider 抽象 | 🔧 接口对齐，provider 数量收窄 |
| 灌入 | Redis Asynq + 分布式任务状态机 | Spring 线程池 + DuckDB 任务表（单机） | 🔧 状态机等价，无分布式与断点续跑 |
| 多库 | 91 迁移、完整 RBAC/组织共享/审计 | 多库 + 标签 + 轻量空间/Key | ⚠️ 管理面显著简化 |
| Agent | ReAct + 工具注册表 + 压缩 + 审批 | Agents-Flex 原生 ReAct + 压缩 + 审批 + 知识工具增强 | ✅ 框架能力对等，知识工具语义对齐 |
| 开放能力 | MCP Server / Go SDK / 小程序 / Wiki | 未迁移（REST 已有） | ⏭️ 明确不迁移 |

> **部署口径声明**：本方案产物为「框架能力演示 + 可运行的嵌入式 RAG 增强」，与 WeKnora 生产形态（多中间件、多租户、对象存储）**不是等价替换**；差异集中在扫描件 OCR、分布式任务、RBAC 治理三处。
