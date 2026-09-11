# RAG知识库与向量模型档案集成（bge-m3 + RogueMemory）

- 日期：2026-09-11
- 类型：功能开发（多模型档案 + 完整 RAG 演示）

## 一、功能总览

1. **模型配置档案双类型**：聊天模型档案 + 向量模型档案（EmbeddingProfile），内置 5 个聊天预设与 5 个向量预设（含 BGE-M3 本地网关 18888 / Ollama / Xinference、OpenAI、百炼）；用户档案存浏览器 localStorage（带 v1 旧格式兼容迁移），另存/删除与聊天档案同机制。
2. **RAG 知识库**：RogueMemory（mmap 持久化、HNSW 向量 + BM25 混合检索、RRF 融合）+ DuckDB 文档元数据；三种入库方式（UI 粘贴文本 / 上传 txt·md / 首次配置向量模型自动灌入 4 篇内置示例）；检索测试面板（TopK + 分数 + 模式展示）。
3. **Agent 工具接入**：第 5 个工具 `search_knowledge(query)`，模型自主检索知识库并在回答中引用来源；事件流与 OTel Trace 零改动可观测。

## 二、后端实现（com.agentsflex.showcase.knowledge 包）

- `TextChunker`：段落边界 + 500 字滑窗（50 重叠）切片
- `KnowledgeService`：RogueMemory 生命周期（@PostConstruct 初始化）、`configure` 模型签名守卫（endpoint|model，签名变更需重建）、入库回滚、`searchForTool` 文本化输出、`rebuild` 按 `path.mem` 前缀清理 mmap 文件
- `KnowledgeDocumentStore`：DuckDB `knowledge_document` 表（清单/删除/统计）
- `KnowledgeController`：`/api/knowledge` 8 个端点（status/documents/upload/delete/search/embedding/rebuild）
- `ResearchAgentFactory`：注册 knowledgeTool、应用 embedding 配置、instructions 默认规则更新
- 依赖：`com.yomahub:roguemap-memory/roguemap-embedding:1.1.5`（Java 8、零依赖）+ HikariCP
- `CreateAgentRequest` 新增 4 字段：embeddingEndpoint/ApiKey/Model + knowledgeSearchMode

## 三、前端实现

- `ModelProfileBar.vue` 重写：双档案分组下拉（聊天预设/向量预设/我的档案×2），applyEmbedding 事件
- `TaskComposer.vue`：新增"向量模型（Embedding · RAG 知识库）"表单组（地址/模型/Key/检索模式）+"应用到知识库"按钮
- `KnowledgePanel.vue`（新）：状态、添加（文本+文件上传）、检索测试、文档清单、清空重建
- `api/agent.ts` + `knowledgeApi`；`types/agent.ts` + 6 个知识库类型

## 四、验证结果

- `mvn test`：**81 个测试全绿**（含 TextChunkerTest 3、KnowledgeServiceTest 4：KEYWORD_ONLY 全链路、删除一致性、重建、空输入拒绝）
- `pnpm build`：vue-tsc + vite 构建通过
- Playwright E2E 7 项全过：面板渲染/文本入库/检索命中/向量档案应用/应用按钮/聊天档案回归/控制台零错误
- 并发压测：8 路并行 status 全 200

## 五、验证中发现并修复的问题

1. **RogueMemory rebuild 残留**：mmap 落盘为 `path.mem` 单文件（256MB 预分配），按父目录前缀清理而非删目录
2. **DuckDB 并发竞态**（重要）：DriverManagerDataSource 每请求新建连接，前端并行请求触发 DuckDB 0.9.2 文件锁竞态 → "Cannot open file" 500。改用 **HikariCP（maxPoolSize=2）** 持有连接复用，8 并发验证通过
3. **CORS 白名单**：仅允许 5173，Vite 自动递增到 5174 后 POST 403 → 补 5174/5175 + DELETE 方法
4. KnowledgeService 增加 @PostConstruct 启动初始化（面板打开即可用，无需等首次读写）

## 六、使用说明

- 左侧配置"向量模型"后点"应用到知识库"，或创建 Agent 时随配置提交；有向量配置时自动灌入 4 篇内置示例文档（含 bge-m3 评测、RAG 混合检索实践等）
- 未配置向量模型时知识库以 KEYWORD_ONLY 模式运行（纯 BM25，仍可演示检索链路）
- 切换 embedding 模型需"清空重建"（不同模型向量空间互不兼容，面板有明确提示）
