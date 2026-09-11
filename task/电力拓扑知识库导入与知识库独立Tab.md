# 电力拓扑知识库导入与知识库独立Tab

- 日期：2026-09-12
- 类型：功能开发（知识库批量导入 + Tab 架构 + 检索范围绑定）
- 资料来源：`D:\workspace\qiz\ylj\cad-to-svg-converter\cad-to-svg-converter\svg\topology_data`

## 一、需求与决策

1. 把 topology_data 目录的资料导入本项目知识库
2. 知识库从工作台左栏独立成 Tab
3. 对话中可选择知识库检索范围

用户决策：拓扑 JSON（885KB 汇总 + 9 站 JSON）**跳过**（结构化数据向量化检索效果差）；jsonl 问答事实（1215 条）**逐条入库**（检索粒度最细）。

## 二、实际导入结果（11/11 全部成功）

| 文件 | 切片数 |
| --- | --- |
| 电力拓扑知识库总览.md | 14 |
| 9 篇站点文档（35kV 藏木变/帮辛变/波弄贡/八盖/巴达/布塔/巴河/宾格/rkz土布加） | 164~340 片/篇 |
| 电力拓扑知识问答.jsonl | **1215**（每条 text 独立片段） |
| **合计** | **21 文档 · 3388+ 切片** |

检索实测：`藏木变有几台主变压器` → 命中"35kV藏木变中主变压器102000147与531断路器直接相连"（BM25 score 8.99）✓

## 三、功能实现

### 后端
- `KnowledgeService.search(query, topK, namespace)`：namespace 过滤检索（`SearchOptions.namespace`），all/空为全库；`searchForTool` 同步支持
- `KnowledgeService.addJsonlDocument()`：逐行解析 JSONL（station/category/text），每条 text 独立片段入库，解析失败行跳过，中途失败回滚
- `KnowledgeController`：上传扩展名支持 `.jsonl`（按扩展名分流到 addJsonlDocument）；`/search` 接受 `namespace` 参数
- **崩溃保护（重要）**：验证中因强杀后端导致 RogueMemory mmap 索引损坏，启动时 `rebuildFromScan` 越界 → JVM EXCEPTION_ACCESS_VIOLATION（Java 层无法捕获）。新增 dirty 标记机制：`initialize()` 写 `<path>.dirty`，正常 `@PreDestroy` 清除；启动时发现标记残留则把数据文件隔离为 `.corrupted-<ts>` 并从空库启动，面板提示重新导入——**保证数据损坏不再导致应用无法启动**
- `CreateAgentRequest.knowledgeNamespace`：Agent 绑定知识库检索范围（docId / all），`search_knowledge` 工具按此过滤；DemoAgent 视图回显

### 前端
- **三 Tab 架构**：Agent 工作台 / 知识库 / 纯对话（`?view=knowledge|chat` URL 同步与刷新恢复）
- KnowledgePanel 移入独立 Tab 全宽展示（从工作台左栏移除）
- 知识库页新增：**批量导入**（多选 .md/.txt/.jsonl，逐个上传显示进度，失败不中断）、检索范围下拉（全部文档 / 各文档）
- 工作台新增"知识库范围"折叠组：创建 Agent 时绑定检索范围（列出各文档与切片数）
- 纯对话页智能体信息条显示其绑定的知识库范围（docId 翻译为文档标题）

## 四、验证（全部通过）

- `mvn test` 81 全绿；`pnpm build` 通过
- 导入脚本 `task/import_topology_knowledge.py`：11/11 成功
- 检索与 namespace 过滤实测通过（全库 + 限定藏木变）
- E2E：知识库 Tab 7 项（三 Tab/独立页/范围下拉/批量导入按钮/工作台范围选择/URL 恢复/零控制台错误）；纯对话页、知识库旧用例已适配三 Tab 架构并全部通过
- 附带：修复了导入过程中发现的 RogueMemory 崩溃问题（见上）

## 五、注意

- 当前知识库为 KEYWORD_ONLY/BM25 模式（未配置可用 embedding）；配置 bge-m3 后新写入的向量才生效，已有切片如需向量检索需"清空重建"后重导入（文档清单在 DuckDB 中已持久化，重建后需重跑导入脚本）
- 重启后端请用正常流程（脚本/Ctrl+C），强杀会触发下次启动的知识库隔离重建
