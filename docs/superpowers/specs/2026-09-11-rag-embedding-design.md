# 多模型档案 + bge-m3 向量化 + 完整 RAG 演示 — 设计文档

日期：2026-09-11 · 状态：已确认（用户批准执行）

## 目标

1. 模型配置档案支持多类型：聊天模型档案 ×N（已有）+ 向量模型档案 ×N（新增，内置 bge-m3 预设）
2. 完整 RAG 演示：知识入库（UI 粘贴文本 / 上传 txt·md 文件 / 预置示例自动灌入）→ 切片 → bge-m3 向量化 → RogueMemory 混合检索 → Agent 工具 `search_knowledge` 消费，事件流/Trace 可见

## 选型（已验证）

- `com.yomahub:roguemap-memory:1.1.5` + `roguemap-embedding:1.1.5`：Java 8+、零第三方依赖、Apache-2.0；HNSW 向量 ANN + BM25 混合检索（RRF 融合），mmap 持久化（`data/knowledge-mem`），内置 namespace 隔离
- `UniversalEmbeddingProvider(baseUrl, apiKey, model)`：OpenAI 兼容 `/v1/embeddings`，维度自动探测（bge-m3=1024）；适配本地网关 18888、Ollama 11434、OpenAI、百炼等
- 文档元数据存 DuckDB（复用已集成的 showcaseJdbcTemplate），向量存 RogueMemory，各司其职

## 决策与边界

- 档案继续存浏览器 localStorage（Key 不落库、多浏览器独立），后端仅接收生效配置
- 文件解析仅 .txt/.md（UTF-8，≤2MB）；PDF/Word 留扩展点
- 知识库全局单例，绑定"当前生效 embedding 配置"；模型签名（baseUrl+model）变化时拒绝写入/检索并提示重建（清空重建按钮）
- 预置示例：首次获得可用 embedding 配置且知识库为空时自动灌入 4 篇内置示例；embedding 不可用不阻断启动
- search_knowledge 为第 5 个工具，instructions 默认规则引导优先检索知识库并标注来源

## REST（/api/knowledge）

- `GET /status` 状态（文档数、chunk 数、模型签名、就绪度）
- `GET /documents` 列表 · `POST /documents`（JSON 文本或 multipart 文件）· `DELETE /documents/{docId}`
- `POST /search`（query+topK 检索测试，返回片段+分数+模式）
- `POST /rebuild`（清空知识库与元数据，等待重新灌入）

## 前端

- ModelProfile：+`profileType: 'chat'|'embedding'`；档案下拉三组（聊天预设/向量预设/我的档案）；向量预设 4 个（BGE-M3 本地网关、BGE-M3 Ollama、OpenAI small、百炼 v3）
- TaskComposer：+“向量模型（Embedding）”表单组（服务商/地址/路径/Key/模型/检索模式/TopK），随创建 Agent 提交
- KnowledgePanel：列表、粘贴文本、上传文件、检索测试、状态与重建；挂载于左栏
- API Key 不回显原则不变

## 测试

- 单测：TextChunker（段落/滑窗/重叠）、KnowledgeService KEYWORD_ONLY 链路（无需外部 embedding）、txt/md 解析与大小/编码校验、DuckDB 元数据与删除一致性
- 端到端：内置浏览器走“添加知识→检索测试→对话触发 search_knowledge→事件流验证”
- `mvn test` + `pnpm build` 全绿后推送 gitee
