# WeKnora 功能模块深度分析与迁移适配方案

> 分析对象：`E:\code\source\WeKnora`（腾讯开源企业级 RAG 知识库系统，Go 主服务 + Python 旁路，main 分支 HEAD `6861dbc4`）
> 迁移目标：本项目 `agents-flex-demo-self`（Spring Boot 3.5 + Java 21 + Vue 3，RogueMemory 嵌入式向量库 + DuckDB 元数据）
> 落地形态：**原地增强**现有 `knowledge` 模块，不引入 Postgres/Redis/Python 中间件
> 分析方法：3 路并行源码级深挖（共 ~98 次工具调用），全部结论带文件路径证据；配套实施文档见《WeKnora迁移可执行技术方案.md》

---

## 目录

- [一、WeKnora 定位与架构总览](#一weknora-定位与架构总览)
- [二、核心 RAG 链路源码剖析](#二核心-rag-链路源码剖析)
- [三、知识库多租户管理剖析](#三知识库多租户管理剖析)
- [四、Agent 对话编排剖析](#四agent-对话编排剖析)
- [五、全量模块盘点](#五全量模块盘点)
- [六、当前项目现状盘点](#六当前项目现状盘点)
- [七、差距对比矩阵](#七差距对比矩阵)
- [八、迁移模块清单与优先级](#八迁移模块清单与优先级)
- [九、分门别类总索引](#九分门别类总索引)

---

## 一、WeKnora 定位与架构总览

WeKnora 是腾讯开源的**企业级文档理解与检索增强（RAG）知识库框架**，核心定位：多格式文档灌入 → 结构化切片 → 混合检索 → 重排 → LLM 生成（含引用溯源），并提供多知识库管理、多租户隔离、Agent 对话编排与 MCP 能力开放。

### 1.1 技术栈与部署形态

| 组件 | 技术 | 证据 |
| --- | --- | --- |
| 主服务 app | Go（Gin + GORM），端口 8080 | `cmd/server/main.go`、`internal/router/` |
| 文档解析 docreader | **Python gRPC 服务**，端口 50051 | `docreader/`、`docreader/proto/docreader.proto` |
| MCP Server | Python（stdio/SSE/HTTP 三种传输） | `mcp-server/weknora_mcp_server.py`（1116 行单文件） |
| 管理端 frontend | Vue + nginx（weknora-ui 镜像） | `frontend/`、`scripts/build_frontend_dist.sh` |
| 对话挂件 web | 独立轻量 JS 挂件 | `web/weknora-widget.js`、`web/embed.html` |
| 数据库 | **paradedb/paradedb:v0.22.2-pg17**（pgvector + pg_search 全文） | `docker-compose.yml` |
| 任务队列 | Redis 7 + Asynq | `docker-compose.yml`（`STREAM_MANAGER_TYPE`） |
| 对象存储 | local / MinIO / S3 / COS / OSS / TOS / OBS | `internal/handler/storagebackend.go` |
| 单文件版 | WeKnora-lite.exe（Go 单二进制 + SQLite，免 Docker） | `task/启动Lite版本服务.md`、`task/Lite编译规则.md`、`.env.lite.example` |

### 1.2 服务依赖链（docker-compose）

```text
frontend(nginx:80) ──► app(8080, /health) ──► postgres(paradedb, 向量+全文)
                          │                 ──► redis(asynq 任务队列)
                          └──► docreader(50051, 仅内部 expose)
mcp(8082, profile=full) ──► app healthy 后启动
可选 profile：sandbox / odl-hybrid / searxng / minio / neo4j(GraphRAG) /
             qdrant / milvus / weaviate / doris / dex(OIDC) / langfuse 全家桶
```

### 1.3 对本项目的启示

WeKnora 的"完整版"依赖 Postgres + Redis + Python 三套中间件，与本项目"单进程嵌入式、免中间件"的 Demo 形态冲突；但其 **lite 版（Go 单二进制 + SQLite）证明了同架构可降级运行**。本项目迁移策略与 lite 版思路一致：用 Java 等价物替换外部依赖（PDFBox/POI 替代 docreader，Spring Executor + DuckDB 任务表替代 asynq，RogueMemory 替代 pgvector）。

---

## 二、核心 RAG 链路源码剖析

### 2.1 文档解析（docreader）

**形态**：独立 Python 进程，gRPC 协议（`docreader/proto/docreader.proto`），Go 主服务远程调用。

**proto 核心结构**：

```protobuf
service DocReader {
  rpc Read(ReadRequest) returns (ReadResponse);
  rpc ReadStream(ReadRequest) returns (stream ReadStreamResponse); // 先 meta 后逐帧 ImageRef，规避 gRPC 消息上限
  rpc ListEngines(...);
}
ReadRequest { file_content, file_name, file_type, url, title,
              config{ parser_engine, parser_engine_overrides }, request_id }
ReadResponse { markdown_content, image_refs[], image_dir_path, metadata, error }
ImageRef { filename, original_ref, mime_type, storage_key, image_data }  // 图片 inline bytes 回传 Go 侧持久化
```

**解析引擎注册**（`docreader/parser/registry.py`，`ParserEngineRegistry`）：

| 引擎 | 覆盖格式 | 依赖 |
| --- | --- | --- |
| `builtin` | pdf / docx / doc / md / xlsx / xls / pptx / ppt / epub / html / mhtml / xmind / jpg…webp | `docreader/parser/` 下 14 个解析器文件 |
| `markitdown` | 微软 MarkItDown 库（md/pdf/docx/doc/pptx/ppt/xlsx/xls/csv） | `markitdown_parser.py` |
| `opendataloader` | PDF 版面分析（需 Java 11+） | `opendataloader_parser.py` |
| MinerU | 高级版面解析（由 Go 侧原生集成） | `MINERU_ENDPOINT` 环境变量 |

**关键架构决策**：docreader **自身不内置 OCR/VLM**——扫描 PDF 渲染为 JPEG（`DOCREADER_PDF_RENDER_DPI` 默认 200、`JPEG_QUALITY` 85）后连同图片返回 Go 主服务，由 Go 侧调 OCR/VLM 服务。图片持久化也在 Go 侧（`_resolve_images`）。

**统一产物**：所有格式解析为 **Markdown 文本 + 图片引用 + metadata**，chunk 结构为 `Chunk{content, seq, start, end, images[], metadata{}}`（`docreader/models/document.py`，按 content 判等去重）。

**配置**：`MAX_FILE_SIZE_MB=50`、`DOCREADER_GRPC_MAX_WORKERS=4`、外部代理、MinIO/COS/OSS 凭证（`docreader/config.py`）。

### 2.2 切片/分块策略

**双实现**：Python 侧 `docreader/splitter/splitter.py` 仅供 sidecar 使用；**Go 侧 `internal/infrastructure/chunker/` 是生产路径**（注释明确说明）。

核心机制（Go 侧文件逐一对应）：

| 文件 | 职责 |
| --- | --- |
| `splitter.go` | 主切分逻辑；默认 `chunk_size=512 / chunk_overlap=80`，分隔符 `["\n", "。", " "]` |
| `patterns.go` | **保护正则**：LaTeX 公式 `$$…$$`、图片 `![…](…)`、链接、Markdown 表格表头/行、代码块 ` ``` ` 不被切开 |
| `strategy.go` + `tokens.go` | 分块策略选择（字符/token 级） |
| `heading_splitter.go` + `heading_hierarchy.go` + `header_tracker.go` | **标题层级结构化切片**：chunk 携带标题路径上下文 |
| `heuristic_splitter.go` + `profiler.go` | 启发式切分：先探测文档特征再选策略 |
| `validator.go` | chunk 校验 |
| `internal/application/service/knowledge_process_parent_child_test.go` | **父子块**（parent-child chunk 关联） |

**chunk 后处理**：`internal/application/service/knowledge_index_content.go`（索引内容构建）、`knowledge_post_process.go`（标题追加/元数据）、`knowledge_auto_tag.go`（自动打标签）。

**chunk 类型枚举**（`internal/types/chunk.go`）：`text / parent_text / image_ocr / image_caption / summary / entity / relationship / faq / wiki_page`，chunk 表还带 `pre_chunk_id/next_chunk_id` 双向链表指针与 `parent_chunk_id`，支持上下文扩展检索。

### 2.3 向量化 Embedding

**抽象层**（`internal/models/embedding/embedder.go`）：

```go
type Embedder interface {
    Embed(ctx, text) ([]float32, error)
    BatchEmbed(ctx, texts []string) ([][]float32, error)
    GetModelName() string; GetDimensions() int; GetModelID() string
}
type Config { Source, BaseURL, ModelName, APIKey, TruncatePromptTokens,
              Dimensions, SupportsDimensionOverride, MaxConcurrency, ... }
```

**Provider 覆盖**（`internal/models/embedding/`）：openai / azure_openai / aliyun / zhipu / gemini / jina / nvidia / **ollama（本地）** / volcengine / weknoracloud。

**工程化要点**：`batch.go`（批量组装）、`concurrency_wrapper.go`（`MaxConcurrency` 限流，0 走进程级 `limiter.GateN`）、`dimensions_policy.go`（维度策略，支持维度覆盖）、`langfuse_wrapper.go`（可观测包装）、`ConfigFromModel(*types.Model)` 从 DB 模型构造。

### 2.4 向量存储与检索

**引擎抽象**（`internal/types/vectorstore.go`）：`VectorStore{ID, TenantID, Name, EngineType, ConnectionConfig, IndexConfig}`，表 `vector_stores`，敏感字段 **AES-GCM 加密**。EngineType 合法值 9 种：`postgres(pgvector) / elasticsearch / qdrant / milvus / weaviate / sqlite / doris / tencentvectordb / opensearch`。

**混合检索**：

- 向量表 `embeddings`（`migrations/versioned/000002_embeddings.up.sql`）：`chunk_id + content + embedding halfvec`，带 **BM25 索引**（`embeddings_search_idx`，中文 lindera 分词）+ 按维度拆分的 **HNSW 索引**（`halfvec_cosine_ops`，3584/768/1024 见 `000059`）。
- 混合索引器：`internal/application/service/retriever/keywords_vector_hybrid_indexer.go`（向量+关键词同步建索引）。
- 检索服务：`internal/application/service/knowledgebase_search.go` + `knowledgebase_search_fanout.go`（**多向量库 fan-out**）+ `knowledgebase_search_fusion.go`（结果融合）+ `knowledgebase_search_faq.go` + `knowledgebase_search_budget.go`（检索预算）。
- 检索参数（topK/threshold）在 `knowledgebase_search_shared.go` 与 `chat_pipeline/filter_top_k.go` 处理。
- 检索驱动可由环境变量切换：`RETRIEVE_DRIVER=postgres/sqlite`（KB 未绑定 store 时回落）。

### 2.5 重排 Rerank

**抽象层**（`internal/models/rerank/reranker.go`）：

```go
type Reranker interface {
    Rerank(ctx, query string, documents []string) ([]RankResult, error)
    GetModelName() string; GetModelID() string
}
type RankResult { Index, Document{Text}, RelevanceScore } // 兼容 score/relevance_score 双字段
```

**Provider**：aliyun / jina / lkeap(腾讯) / nvidia / volcengine / zhipu / weknoracloud / 通用 remote_api。

**Pipeline 位置**：`internal/application/service/chat_pipeline/rerank.go`——检索出候选后、拼接上下文前执行；之后 `filter_top_k.go` 按 topK 截断。

### 2.6 检索后处理与引用溯源

**对话管道**（`internal/application/service/chat_pipeline/`，入口 `chat_pipeline.go`）：

```text
query_expansion.go(查询扩展) → memory_affinity.go(记忆亲和) / wiki_boost.go(Wiki 加权)
→ knowledgebase_search(混合检索+fanout+fusion)
→ rerank.go(重排) → filter_top_k.go(截断) → merge.go(多路合并)
→ 拼接 context + 引用 → LLM 生成
```

**引用溯源**：检索结果带 chunk 元数据（来源文档、页码/位置），经 `internal/agent/tools/output_links.go` / `output_budget.go` 转为 `cN`（chunk）/ `dN`（document）引用 ID 回传前端展示。

### 2.7 知识灌入 Pipeline（上传 → 可检索）

主流程在 `internal/application/service/`（Go 侧），异步任务基础设施在 `internal/types/interfaces/`（`task_queue.go / task_handler.go / task_enqueuer.go / task_inspector.go` 四个接口文件，生产实现为 Redis Asynq）：

```text
上传/URL/手动录入 (internal/handler/knowledge*.go)
  → temporary_document.go 落临时文档 → knowledge_create.go 落库(parse_status=pending)
  → 异步入队 task_enqueuer
  → knowledge_process.go 编排:
      ① 文件 → 对象存储(storage://backendID/path)
      ② gRPC → docreader 解析为 Markdown + 图片
      ③ Go chunker 结构化切片(含父子块)
      ④ embedding BatchEmbedWithPool 批量向量化(并发限流)
      ⑤ 写入向量库 + 混合索引
      ⑥ knowledge_post_process: 元数据/自动标签/wiki 入队
  → parse_status: pending → processing → finalizing → completed/failed
```

**操作面**：`knowledge_reparse.go`（重建索引）、`knowledge_batch_reparse.go`（批量）、`knowledgebase_task_cancel_test.go`（任务取消）、`knowledge_faq_create_guard.go`（FAQ 防重复）、Python 侧 chunk 按 content 哈希去重、`knowledge_span_tracker.go`（耗时追踪）。

---

## 三、知识库多租户管理剖析

### 3.1 核心数据模型（`internal/types/*.go` + `migrations/versioned/`，共 91 个迁移）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tenants` | id（序列从 10000 起）、name、api_key、retriever_engines JSONB、storage_quota/used（默认 10GB）、agent_config JSONB | 租户/工作区 |
| `models` | id、tenant_id、type（Embedding/Rerank/KnowledgeQA/VLLM/ASR）、source（18 种 provider）、parameters JSONB、is_default、is_builtin | 模型注册表 |
| `knowledge_bases` | id、name、type（document/faq/wiki）、tenant_id、chunking_config JSONB（默认 512/50）、embedding_model_id/summary_model_id/rerank_model_id、vlm_config、storage_backend_id、vector_store_id（创建后不可改）、faq_config、auto_tag_config | 知识库 |
| `knowledges` | id、tenant_id、knowledge_base_id、type（manual/url/file）、title、parse_status、enable_status、file_name/file_type/file_size/file_path/file_hash、metadata JSONB | 文档 |
| `chunks` | id、tenant_id、knowledge_base_id、knowledge_id、content、chunk_index、chunk_type、parent_chunk_id、pre/next_chunk_id、image_info、relation_chunks JSONB | 切片 |
| `embeddings` | chunk_id、content、embedding halfvec、BM25+HNSW 索引 | 向量表 |
| `sessions` | id、tenant_id、knowledge_base_id、max_rounds、enable_rewrite、fallback_strategy、keyword_threshold/vector_threshold、rerank_model_id、embedding_top_k/rerank_top_k/rerank_threshold、summary_model_id、agent_config、context_config | **检索参数全部挂在会话上** |
| `messages` | id、request_id、session_id、role、content、knowledge_references JSONB、agent_steps JSONB | 消息（含引用） |
| 治理演进表 | organizations(000012)、tenant_rbac(000043)、audit_log(000044)、tenant_invitations(000048)、user_kb_pins(000050)、knowledge_multi_tags(000063)、tenant_api_keys(000065)、storage_backends(000068)、temporary_documents(000070)、chunk_editing_and_custom_metadata(000078) | 91 个版本化迁移 |

### 3.2 多租户隔离机制

- **共享库 + `tenant_id` 行级隔离**：所有业务表带 `tenant_id` 列，repository 层显式 `WHERE tenant_id = ?`（`internal/application/repository/knowledgebase.go` 的 `GetKnowledgeBaseByIDAndTenant` 等）；模型表 `(tenant_id = ? OR is_builtin = true)` 让内置模型全租户可见。
- **用户/角色体系**：users（JWT）+ tenant_members + `tenant_rbac`（Viewer/Contributor/Owner/Admin，`internal/router/rbac.go`）+ organizations（跨租户共享单元）。
- **机器访问**：`tenant_api_keys`（capability：retrieve/ingest/manage_kbs/full-access + KB allow-list）。
- **共享**：`kbshare.go`（KB 共享给组织，ShareCount 计数）+ `agent_share.go`（经共享 Agent 间接可见），路由守卫 `KBAccessRead/Write` 改写请求上下文为"有效租户"。

### 3.3 知识库生命周期 API 全景（`internal/router/routes_knowledge.go`，节选关键 30+ 端点）

| Method | Path | 用途 |
| --- | --- | --- |
| POST/GET/PUT/DELETE | `/knowledge-bases[/:id]` | KB CRUD |
| POST | `/knowledge-bases/copy` | 深拷贝 KB（异步任务+进度查询） |
| POST | `/knowledge-bases/:id/duplicate` | 仅复制设置 |
| POST/GET | `/knowledge-bases/:id/hybrid-search` | **混合检索** |
| POST | `/knowledge-bases/:id/knowledge/file` \| `/url` \| `/manual` | 三种入库方式 |
| POST | `/knowledge/:id/reparse` \| `/cancel-parse` \| `/regenerate-summary` | 重建/取消/摘要 |
| POST | `/knowledge/batch-delete` \| `/batch-reparse` \| `/move` \| `/tags` | 批量操作 |
| GET/PUT/DELETE | `/chunks/:knowledge_id[/:id]` | **chunk 级编辑**（含版本历史 `/revisions`、回滚） |
| 全套 | `/knowledge-bases/:id/faq/entries...` | FAQ 条目 CRUD/搜索/导入 |
| 全套 | `/knowledge-bases/:id/tags` | 标签 CRUD |
| POST | `/chunker/preview` | **切片预览调试** |

### 3.4 模型管理

- `ModelType`：Embedding/Rerank/KnowledgeQA/VLLM/ASR；`ModelSource` 18 种 provider（openai/aliyun/zhipu/volcengine/deepseek/hunyuan/jina/ollama…）。
- **按 KB 绑定**：KB 表直接存 embedding/summary/rerank 模型 ID；`CountByModelID/ListModelUsages` 在删除模型前反查引用（11 种绑定角色：embedding/summary/image_processing/vlm/asr/wiki_synthesis/chat/rerank/query_understand/follow_up/extract）。

### 3.5 FAQ / 标签 / 元数据检索

- **FAQ KB**：`FAQConfig{FAQIndexMode: question_only|question_answer, FAQQuestionIndexMode: combined|separate}`；条目含标准问+相似问+反问+答案（`internal/types/faq.go` 的 `FAQChunkMetadata`），走专用 `/faq/search`。
- **标签**：`knowledge_multi_tags` 多对多；`KnowledgeListFilter.TagIDs` OR 过滤；KB 级 `AutoTagConfig` 解析后异步自动打标（上限 10）。
- **元数据过滤**：`knowledges.metadata JSONB` + external_id 索引，支持 TagIDs/Keyword/FileType/ParseStatus/FolderPath 多维过滤。

### 3.6 存储层

Repository 模式（接口 `internal/types/interfaces/`，实现 `internal/application/repository/`，GORM）；文件存对象存储，路径方案 `storage://{backendID}/{providerPath}`（`BuildStorageBackendPath`），租户级 `storage_quota/storage_used` 计量 + `upload_limit.go` 限流。

---

## 四、Agent 对话编排剖析

### 4.1 ReAct AgentEngine（`internal/agent/`）

**核心结论：WeKnora 的 Agent 不是"每轮必检"的固定 RAG 管道，而是 LLM 在 ReAct 循环中自主决策何时检索、检索几次。**

| 文件 | 职责 |
| --- | --- |
| `engine.go`（841 行） | `AgentEngine`：toolRegistry + chatModel(流式) + eventBus + knowledgeBasesInfo + pinnedMCPServices/Skills(@提及) + compactor + tokenEstimator + modelcontext.Registry（请求级模型句柄，脱敏）。**引擎跨轮无状态**：历史由调用方每轮从 DB 重建（`service.LoadAgentHistory`） |
| `act.go`（602 行） | ReAct 主循环：errgroup 并行工具执行；工具中文名映射（`ToolKnowledgeSearch: "知识搜索"`） |
| `think.go`（628 行） | `streamLLMToEventBus`：LLM 流经 EventBus 逐 chunk 转发，`ReasoningContent`(thinking) 与 `Content` 分开累积，stall watchdog |
| `observe.go` | 工具结果组装回上下文；约束"回答不得暴露内部工具名/chunk_id"（`<communication_instruction>`） |
| `compaction/` | 历史摘要压缩：`cutpoint.go`（按 knowledge_search 大块找裁剪点）、`overflow.go`（溢出后压缩一次重试）、`CompactionKeepRecentTokens/MaxContextTokens` 可配，防死循环标记 |
| `approval/` | 工具人工审批（`WEKNORA_AGENT_TOOL_APPROVAL_TIMEOUT`/`FAIL_OPEN`） |
| `tools/knowledge_search.go`（1396 行） | 知识检索工具：参数 1-5 个语义 query + knowledge_base_ids，内部走 `internal/searchutil` 混合检索 + rerank + **MMR 多样性**，输出含 cN/dN 引用 |
| `tools/grounding_prompt.go` | 按模式（rag/wiki…）把可用工具集注入系统提示（grounding 兜底） |
| `tools/capabilities.go` | KB 能力门控：`"knowledge_search": {AnyOf: [CapVector, CapKeyword]}` |

### 4.2 SSE 对话端到端函数链

```text
gin 路由 → handler/session/qa.go parseQARequest/buildQARequest
→ handler/session/stream.go (text/event-stream)
→ service/agent_service.go CreateAgentEngine（组装 toolRegistry/KB 信息/事件总线/系统提示）
→ service/agent_history.go LoadAgentHistory（DB 重建历史）
→ AgentEngine.Execute → act.go ReAct 循环:
     think.go streamLLMToEventBus(ChatStream)
     → tool_calls? → tools/registry.go 分派(鉴权/参数修复 json_repair/超时)
     → knowledge_search(searchutil 混合检索 + rerank + MMR)
     → observe.go 结果写回上下文 → 循环
→ 引用标注 output_links.go(cN/dN) → EventBus → SSE event:message 逐 chunk 回前端
→ Session/Message 落库(knowledge_references JSONB)
```

### 4.3 MCP Server（`mcp-server/`，Python）

**薄适配层**：`WeKnoraClient` 通过 **REST** 调主服务 `WEKNORA_BASE_URL`（默认 `http://localhost:8080/api/v1`），把知识库全套能力（建租户/建库/上传/hybrid_search/chat SSE 转发）暴露为 MCP 工具；传输 stdio/SSE/HTTP，`MCPAuthMiddleware` 强制 Bearer token；上传目录白名单 `MCP_ALLOWED_UPLOAD_DIRS`。compose 中 `profile=full` 可选启动。

**启示**：对外开放能力不需要新协议栈，REST 薄封装即可。

### 4.4 GraphRAG（可选）

`internal/agent/tools/query_knowledge_graph.go`（510 行）：LLM 在 ReAct 中按需调用的图谱查询工具；需 KB 预先配置实体/关系抽取（Nodes/Relations），未配置回退普通检索。存储为可选 `neo4j` compose profile（`NEO4J_ENABLE`）。另有 Wiki Mode 的交互式知识图谱（`internal/agent/tools/wiki_*.go`）。**成熟度：功能完整但需显式配置，非默认全自动。**

---

## 五、全量模块盘点

| 目录 | 职责 | 核心入口 | 迁移价值 |
| --- | --- | --- | --- |
| `internal/` | Go 主服务全部业务（agent/application/handler/service/router/models/searchutil/event/mcp/sandbox） | `cmd/server/main.go` | **高** |
| `docreader/` | Python 文档解析 gRPC 服务（14 格式+MinerU/ODL） | `docreader/main.py` | **高**（解析生态，需 Java 替代） |
| `migrations/` | 91 个版本化 DB 迁移（PG/MySQL/SQLite/paradedb 四方言） | `migrations/versioned/` | **高**（表结构设计参考） |
| `config/` | config.yaml、prompt_templates、agent_type_presets、builtin_agents/models | `config/config.yaml` | **高**（提示词/预设资产） |
| `frontend/` | 管理端 UI（Vue+nginx） | `frontend/` | 中（本项目已有 Vue 控制台） |
| `client/` | Go SDK（封装全部 REST API） | `client/client.go` | 中（API 契约参考） |
| `mcp-server/` | Python MCP 薄适配层 | `weknora_mcp_server.py` | 中（P2） |
| `web/` | 对话嵌入挂件 | `web/weknora-widget.js` | 低 |
| `miniprogram/` | 微信小程序端 | `miniprogram/app.js` | 低（渠道端） |
| `cli/` | 命令行/技能管理 | `cli/main.go` | 低 |
| `task/` | Lite 版编译/排障笔记 | `task/*.md` | 中（lite 形态参考） |
| `docker/` `deploy/` `helm/` | 部署资产 | `docker-compose.yml` | 中 |
| `packages/` `Formula/` `website-docs/` `examples/` `tests/` `testdata/` `misc/` | 聚合包/Homebrew/文档/示例/测试 | — | 低 |

---

## 六、当前项目现状盘点

### 6.1 knowledge 模块（`src/main/java/com/agentsflex/showcase/knowledge/`）

| 文件 | 现有能力 |
| --- | --- |
| `KnowledgeService.java` | RogueMemory 嵌入式库（HNSW 向量 + BM25，HYBRID/VECTOR_ONLY/KEYWORD_ONLY）；**单一全局库**，namespace=docId；embedding 签名校验防混存；自动灌入 4 篇示例；快照导出/导入（含向量缓存，跨环境免重算）；mmap 崩溃防护（固定 1GB、dirty 标记、启动丢弃重建） |
| `TextChunker.java` | 500 字符滑窗 + 50 重叠，按空行分段；**无结构感知**（不保表格/代码块、无标题层级、无父子块） |
| `KnowledgeController.java` | 入库仅 **txt/md/jsonl**（粘贴或上传）；检索测试（topK/namespace）；文档 CRUD/预览/编辑重切；embedding 配置与预设；快照 import/export |
| `KnowledgeDocumentStore.java` | DuckDB 文档元数据 + 原文 |
| `KnowledgeVectorCache.java` | embedding 向量 DuckDB 缓存（重启重建免网络调用） |
| `KnowledgeSettingsStore.java` | 向量模型配置持久化，重启后台自动恢复 |
| `CachedEmbeddingProvider.java` | embedding 缓存包装 |

### 6.2 Agent 与基础设施

- `demo/ResearchAgentFactory.java` 已把 `searchForTool` 挂为 Agent 工具（LLM 可在 Turn 内调用知识检索）。
- Agents-Flex 框架原生能力：ReAct 式 Turn 循环、SSE 事件流、JSON Schema 动态表单、**人工审批**、**上下文压缩**、挂起/恢复、预算、重试、OpenTelemetry。
- 内嵌 Neo4j 5.26（电力拓扑图谱，`graph/` 目录）——可复用为 GraphRAG 存储。
- 前端：`KnowledgePanel.vue`（知识库管理面板）、`KnowledgeScopeCard.vue`（知识范围卡片）骨架已在。

### 6.3 形态差异关键约束

本项目 = **单进程、嵌入式、零中间件**的框架能力 Showcase；所有运行态在内存，归档在 DuckDB 单文件。迁移必须保持该形态（与 WeKnora-lite 思路同构）。

---

## 七、差距对比矩阵

| WeKnora 能力 | 当前项目状态 | 差距 |
| --- | --- | --- |
| 多格式文档解析（14 格式） | 仅 txt/md/jsonl | ❌ 缺失 |
| 结构化切片（保护正则/标题层级/父子块） | 500 字符滑窗 | ❌ 缺失 |
| 异步灌入流水线（任务状态机/reparse/cancel） | 同步入库 | ❌ 缺失 |
| Rerank 重排（8 provider） | 无 | ❌ 缺失 |
| 引用溯源（cN/dN） | 工具返回纯文本片段 | ❌ 缺失 |
| 多知识库（KB 绑定模型/切片参数） | 单一全局库 | ❌ 缺失 |
| 混合检索（向量+BM25） | ✅ RogueMemory HYBRID 已有 | ✅ 已有 |
| 检索参数化（topK/threshold 会话级） | topK 全局配置 + per-request | 🔧 可增强 |
| embedding 多 provider 抽象 | OpenAI 兼容单配置 | 🔧 可增强（OpenAI 兼容已覆盖多数） |
| 模型管理与删除引用检查 | 配置式档案 | 🔧 可增强 |
| FAQ 知识库（标准问+相似问） | JSONL 导入（雏形） | 🔧 可增强 |
| 标签/元数据过滤检索 | 无 | ❌ 缺失 |
| 多租户/RBAC/审计 | 无（Demo 单用户） | ⏭️ 降级适配 |
| Agent ReAct 编排 + 工具审批 + 压缩 | ✅ Agents-Flex 原生已有 | ✅ 已有 |
| Agent 知识工具（多 query/MMR/按 KB 路由） | 单 query 全库检索 | 🔧 可增强 |
| MCP Server | 无 | ⏭️ P2 |
| GraphRAG | 有内嵌 Neo4j（另用途） | ⏭️ P2 |
| Wiki 模式 / 小程序 / web 挂件 | 无 | ⏭️ 不迁移 |
| 切片预览调试（/chunker/preview） | 无 | ❌ 缺失（低成本高价值） |
| chunk 级编辑/版本历史 | 整篇重切 | ⏭️ P2 |
| 快照导入导出 | ✅ 已有（含向量缓存） | ✅ 独有优势 |

---

## 八、迁移模块清单与优先级

> 优先级定义：**P0** = 核心 RAG 链路增强（检索质量直接受益）；**P1** = 管理面与编排增强；**P2** = 可选能力，评估后决定。

### P0 核心链路（5 项）

| # | 模块 | WeKnora 证据来源 | 本项目适配方式 | 涉及文件 |
| --- | --- | --- | --- | --- |
| P0-1 | 多格式文档解析 | `docreader/parser/`（14 解析器）、`docreader.proto` | Java 原生 `DocumentParser` 接口 + PDFBox/POI/flexmark/jsoup 实现组，统一产出 Markdown | 新建 `knowledge/parser/` 包；改 `KnowledgeController` 上传接口 |
| P0-2 | 结构化切片 | `internal/infrastructure/chunker/`（保护正则、标题层级、512/80） | 升级 `TextChunker`→`MarkdownChunker`：保护表格/代码块/公式、标题路径注入元数据、参数可配 | `TextChunker.java`（重构）、`KnowledgeProperties` |
| P0-3 | 异步灌入流水线 | `knowledge_process.go`、task_queue 接口组 | Spring `ThreadPoolTaskExecutor` + DuckDB `ingest_task` 表（pending→processing→finalizing→completed/failed），进度查询+reparse | 新建 `knowledge/IngestTaskStore.java`、`IngestPipeline.java`；改 Service/Controller |
| P0-4 | Rerank 重排 | `internal/models/rerank/reranker.go`、`chat_pipeline/rerank.go` | `RerankProvider`（OpenAI 兼容 rerank API：Jina/阿里/硅基流动格式），检索后 topK 截断前执行，可开关 | 新建 `knowledge/RerankProvider.java`；改 `KnowledgeService.search` |
| P0-5 | 引用溯源 | `tools/output_links.go`、`messages.knowledge_references` | `searchForTool` 输出带 `[cN]` 标记，结构化 hits 进 Agent 工具结果；前端展示引用列表 | `KnowledgeService.searchForTool`、`demo/ResearchAgentFactory.java`、`frontend` RunResult |

### P1 管理面与编排（5 项）

| # | 模块 | WeKnora 证据来源 | 本项目适配方式 | 涉及文件 |
| --- | --- | --- | --- | --- |
| P1-1 | 多知识库 | `knowledge_bases` 表、`routes_knowledge.go` | DuckDB `knowledge_base` 表（id/name/切片参数/embedding 签名/检索参数）；RogueMemory namespace 升级 `kbId/docId` 两级；KB CRUD API + 前端选择器 | 新建 `KnowledgeBaseStore.java`；改 Service 全部 namespace 调用、`KnowledgePanel.vue` |
| P1-2 | 检索参数会话级 | `sessions` 表（top_k/threshold/rerank 参数全挂会话） | CreateAgentRequest 增加知识检索参数组；Agent 工具调用时按会话参数执行 | `model/CreateAgentRequest.java`、`ResearchAgentFactory.java` |
| P1-3 | FAQ 知识库 | `internal/types/faq.go`、FAQ IndexMode | KB type=faq；JSONL 导入升级为 FAQ 条目（标准问+相似问+答案），检索粒度到条目 | `KnowledgeService.addJsonlDocument` 重构、`KnowledgeBaseStore` |
| P1-4 | 标签/元数据过滤 | `knowledge_multi_tags`、`KnowledgeListFilter` | DuckDB `knowledge_tag` 表 + 检索 tag 过滤参数 + 文档打标 API | 新建 `KnowledgeTagStore.java`；改 search |
| P1-5 | 轻量多租户 | `tenants`、`tenant_api_keys`（capability 位） | **降级适配**：知识库空间隔离（space 字段）+ 可选只读 API Key（检索/管理分离）；文档标注与 WeKnora RBAC 的差距 | `KnowledgeBaseStore`、`WebConfig` 拦截器 |

### P2 可选与明确不迁移（4 项）

| # | 模块 | 结论 |
| --- | --- | --- |
| P2-1 | MCP Server | 延后：本项目 API 已是 REST，可用 Java MCP SDK 薄封装，需求驱动再启动 |
| P2-2 | GraphRAG | 延后：内嵌 Neo4j 已在，可加"实体/关系抽取 + query_knowledge_graph 工具"，需先评估抽取成本 |
| P2-3 | chunk 级编辑/版本历史 | 延后：当前整篇重切已满足 Demo 规模 |
| P2-4 | Wiki 模式 / 小程序 / web 挂件 / Go SDK | 不迁移：渠道端与完整产品形态，超出框架 Showcase 边界 |

---

## 九、分门别类总索引

> 全文知识点按体系归类：✅=当前项目已有/可直接复用，🔧=需增强/适配，❌=缺失待建，⏭️=延后/不迁移。条目数经脚本校验（见文末校验记录）。

### A. 文档解析类（6 条）

1. ❌ docreader 是独立 Python gRPC 服务（:50051），与主服务进程隔离——`docreader/proto/docreader.proto`
2. ❌ 三引擎注册制（builtin/markitdown/opendataloader）+ MinerU 外部集成，按格式路由——`docreader/parser/registry.py`
3. ❌ 覆盖 14 种格式（pdf/docx/doc/xlsx/xls/pptx/ppt/md/epub/html/mhtml/xmind/csv/图片/URL）——`docreader/parser/`
4. ❌ 统一产物为 Markdown+图片引用+metadata；图片 inline bytes 回传主服务持久化——`docreader/models/document.py`
5. ⏭️ OCR/VLM 不在解析层内置：扫描件渲染 JPEG 回传 Go 侧调外部服务——`docreader/README.md`
6. 🔧 迁移替代：Java 原生解析器组（PDFBox/POI/flexmark/jsoup）可覆盖主流格式且零中间件

### B. 切片策略类（6 条）

7. ❌ 默认 chunk_size=512/overlap=80，分隔符 `["\n","。"," "]`——`internal/infrastructure/chunker/splitter.go`
8. ❌ 保护正则保公式/表格/代码块/图片/链接不被切断——`chunker/patterns.go`
9. ❌ 标题层级结构化切片，chunk 携带标题路径上下文——`chunker/heading_splitter.go`、`header_tracker.go`
10. ❌ 启发式切分：先 profiler 探测文档特征再选策略——`chunker/heuristic_splitter.go`
11. ❌ 父子块关联 + pre/next 双向链表支持上下文扩展——`internal/types/chunk.go`
12. 🔧 当前项目为 500/50 滑窗无结构感知——`TextChunker.java`，升级路径为 MarkdownChunker

### C. 向量化与存储类（6 条）

13. ❌ Embedder 接口抽象 + 10 provider（含本地 Ollama）——`internal/models/embedding/embedder.go`
14. ❌ 批量池化 BatchEmbedWithPool + MaxConcurrency 限流——`embedding/batch.go`、`concurrency_wrapper.go`
15. ❌ 向量引擎 9 种可插拔（pgvector/ES/Qdrant/Milvus/Weaviate/TencentVDB/Doris/OpenSearch/SQLite）——`internal/types/vectorstore.go`
16. ❌ embeddings 表 HNSW（halfvec_cosine_ops）+ BM25（lindera 中文分词）双索引——`migrations/versioned/000002_embeddings.up.sql`
17. ✅ 当前项目 RogueMemory 已有 HNSW+BM25 混合检索（HYBRID 模式）——`KnowledgeService.openMemory`
18. ✅ 向量缓存 DuckDB 化 + 快照导入导出免重算为当前项目独有优势——`KnowledgeVectorCache.java`

### D. 检索与重排类（6 条）

19. ❌ 混合检索融合：多向量库 fan-out + fusion——`knowledgebase_search_fanout.go`、`knowledgebase_search_fusion.go`
20. ❌ Reranker 接口 + 8 provider，RankResult 兼容双字段反序列化——`internal/models/rerank/reranker.go`
21. ❌ rerank 位于检索后、topK 截断前——`chat_pipeline/rerank.go`、`filter_top_k.go`
22. ❌ 查询扩展/记忆亲和/Wiki 加权等检索前处理——`chat_pipeline/query_expansion.go`
23. 🔧 检索参数（topK/threshold）在 WeKnora 挂会话级，当前项目为全局配置——`internal/types/session.go` vs `KnowledgeProperties`
24. ❌ 检索预算控制（search_budget）——`knowledgebase_search_budget.go`

### E. 灌入流水线类（6 条）

25. ❌ 异步任务队列接口四件套（task_queue/handler/enqueuer/inspector），生产实现 Redis Asynq——`internal/types/interfaces/`
26. ❌ parse_status 状态机 pending→processing→finalizing→completed/failed——`knowledge_process.go`
27. ❌ 支持 reparse/batch-reparse/cancel 任务级操作——`knowledge_reparse.go`、`knowledge_batch_reparse.go`
28. ❌ chunk 按 content 哈希去重；FAQ 防重复创建 guard——`docreader/models/document.py`、`knowledge_faq_create_guard.go`
29. ❌ 文件先入对象存储（storage://backendID/path），租户级配额计量——`internal/types/knowledgebase.go`
30. 🔧 当前项目同步入库+失败回滚 namespace，需升级为 Executor+DuckDB 任务表——`KnowledgeService.addDocument`

### F. 知识库管理类（6 条）

31. ❌ KB 三类型 document/faq/wiki，绑定 embedding/summary/rerank 模型与切片参数——`internal/types/knowledgebase.go`
32. ❌ 模型注册表 18 种 provider + 11 种绑定角色 + 删除前引用检查——`internal/types/model.go`
33. ❌ KB 深拷贝（异步）/duplicate（仅设置）/move 跨库移动——`routes_knowledge.go`
34. ❌ chunk 级编辑+版本历史+回滚——`/chunks/:knowledge_id/:id/revisions`
35. ❌ 切片预览调试 API——`/chunker/preview`
36. 🔧 当前项目单一全局库 namespace=docId，需升级两级 namespace——`KnowledgeService`

### G. 多租户与治理类（6 条）

37. ❌ 共享库+tenant_id 行级隔离，repository 层显式过滤——`internal/application/repository/knowledgebase.go`
38. ❌ RBAC 四角色（Viewer/Contributor/Owner/Admin）路由守卫——`internal/router/rbac.go`
39. ❌ tenant_api_keys 带 capability 位（retrieve/ingest/manage_kbs/full-access）+ KB allow-list——`migrations/versioned/000065`
40. ❌ KB 组织共享 + Agent 共享间接可见——`repository/kbshare.go`、`agent_share.go`
41. ❌ 审计日志 audit_log + 邀请体系——`migrations/versioned/000044_audit_log.up.sql`、`migrations/versioned/000048_tenant_invitations.up.sql`
42. ⏭️ 当前项目降级适配为空间隔离+只读 API Key，不做完整用户体系

### H. Agent 编排类（7 条）

43. ❌ ReAct AgentEngine 跨轮无状态，历史每轮从 DB 重建——`internal/agent/engine.go`
44. ❌ 知识检索是 LLM 自主决策的工具（1-5 个语义 query + KB 路由 + MMR + rerank）——`internal/agent/tools/knowledge_search.go`
45. ❌ grounding 兜底：无工具时按模式注入可用工具集到系统提示——`tools/grounding_prompt.go`
46. ❌ compaction 历史压缩：按检索结果大块找裁剪点+溢出重试——`internal/agent/compaction/`
47. ✅ 当前项目 Agents-Flex 已有 Turn 循环/审批/压缩/预算/重试/OTel——README 能力边界表
48. 🔧 当前知识工具仅单 query 全库检索纯文本输出——`KnowledgeService.searchForTool`
49. ❌ 引用标注 cN/dN 结构化回传前端——`internal/agent/tools/output_links.go`

### I. 开放接口与部署类（7 条）

50. ❌ MCP Server 为 Python 薄适配层，REST 复用主服务能力——`mcp-server/weknora_mcp_server.py`
51. ❌ client/ 目录为 Go SDK 封装全部 REST API——`client/client.go`
52. ⏭️ web 对话挂件 + 小程序为渠道端，不迁移——`web/`、`miniprogram/`
53. ❌ 完整版依赖 paradedb+redis+docreader 三中间件——`docker-compose.yml`
54. ✅ lite 版证明同架构可 SQLite 单文件运行，与当前项目嵌入式形态同构——`task/启动Lite版本服务.md`
55. ⏭️ GraphRAG 为可选 neo4j profile + KB 显式配置抽取，非默认能力——`internal/agent/tools/query_knowledge_graph.go`
56. 🔧 当前项目内嵌 Neo4j 已在（电力拓扑），GraphRAG 复用设施可行但需评估抽取成本

---

**索引统计**：9 大类共 **56 条**（A6+B6+C6+D6+E6+F6+G6+H7+I7）；性质分布 ✅4 / 🔧7 / ❌41 / ⏭️4（✅+🔧+❌+⏭️ = 56）。

**校验记录**：`python task/verify_weknora_docs.py` 输出 —— A~I 九类条目数与声明逐类一致、合计 56 条、文末声明总数吻合；性质分布 4/7/41/4 与条目总数自洽；两份交付物就绪（37KB / 37KB）。结论 PASS。
