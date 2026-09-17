# 知识库页面卡加载与JVM崩溃修复

## 现象
知识库 Tab 显示"加载中 / 0 文档 / 请求失败 (500)"，页面卡住；后端进程崩溃退出
（EXCEPTION_ACCESS_VIOLATION，`RogueMemory.parseRecordHeader` / `Unsafe.getInt` 原生越界）。

## 根因（三层问题）
1. **dirty 标记顺序 Bug（致命）**：`initialize()` 先 `markDirty()` 再打开索引（内部才执行
   损坏预检 `quarantineIfCorrupted()`），导致预检把自己刚创建的 `.dirty` 当成"上次异常退出"
   证据，**每次重启都把完好 mem 文件隔离成空库**，随后在空文件上 mmap 越界崩溃。
   data 目录因此堆积了 4+ 个 `knowledge-mem.corrupted-*`（每个 256MB）。
2. **RogueMemory 1.1.5 在 Windows 下无法重新打开已持久化文件**：即使正常关闭后重启，
   `parseRecordHeader` 加载旧文件仍触发 JVM 原生崩溃（Java 异常无法捕获）。
3. **无优雅停机手段**：Windows 上强杀进程不触发 JVM shutdown hook，`.dirty` 必然残留。

## 修复方案：DuckDB 作为唯一事实源，mmap 每次启动重建
- `KnowledgeService.initialize()`：
  - 先 `discardStaleFiles()`（删除上次遗留的 `.mem/.dirty/.wal`，强制新建，规避加载旧文件崩溃）
  - 再 `markDirty()`，打开索引（环境变量 embedding 可用则用向量模式，否则 BM25）
  - 最后 `rebuildFromStore()`：遍历 DuckDB 全部文档，读原文重新切片写入 RogueMemory，
    再 `store.update` 回写切片数；旧格式（无原文）文档跳过。
- 移除 `quarantineIfCorrupted()`（不再隔离恢复旧文件，DuckDB 才是数据源）。
- `configure()`（UI 配置 embedding）成功后同样触发 `rebuildFromStore()`，
  使切换向量模型后索引立即重建。
- 新增 `SystemController`：`POST /api/system/shutdown` 优雅停机
  （延迟 `Runtime.exit(0)`，触发 JVM shutdown hook → `@PreDestroy` → 释放 mmap + 清除 dirty）。

## 数据恢复
- 损坏的 mem 缓存全部不可复用，已隔离（corrupted-* / crashed-backup，可手动清理释放 ~2GB）。
- 重新导入电力拓扑资料（`import_topology_knowledge.py`，11 文件 / 3378 切片），
  再通过"GET 原文 → PUT 回写"重建索引（因 jsonl 重新切片粒度差异，最终 3475 片）。
- 之后每次启动由 `rebuildFromStore` 自动重建，无需手动干预。

## 验证
- 连续三次重启后端：无崩溃、无 dirty 残留、无新增 corrupted 文件。
- 启动后自动重建：11 文档 / 3475 切片，检索命中（BM25）、预览原文正常。
- 优雅停机闭环：`POST /api/system/shutdown` → 8080 释放 + dirty 清除 → 重启 → 数据自动恢复。
- 前端回归 `e2e_knowledge_tab.py` 全通过；知识库页不再卡加载（11 文档 · 3475 切片）。
- 端口：8080 后端 / 5173 前端，无冲突。

## 遗留说明
- 当前无可用 OpenAI 兼容 embedding 服务（127.0.0.1:18888 无 `/v1/embeddings`，Ollama 未启动），
  知识库以 BM25 关键词检索运行；配置可用向量服务后可获得语义检索。
- 重启后端请使用 `POST http://localhost:8080/api/system/shutdown` 优雅停机后再启动，
  可避免强杀带来的索引重建等待。
