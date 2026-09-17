# RogueMemory 子模块补丁：修复 mmap 原生崩溃（各种 500 报错的根源）

## 结论

知识库页面反复出现 `请求失败 (500)`，根源不是接口代码，而是后端 JVM 被 RogueMemory 1.1.5 的
mmap 解析缺陷反复原生崩溃（`EXCEPTION_ACCESS_VIOLATION`）。今天共留下 7 份 `hs_err_pid*.log`
崩溃日志，全部指向 `RogueMemory.parseRecordHeader` / `Unsafe.getInt` 越界读。

修复方式：按用户要求**保留 RogueMemory**，把上游源码（https://github.com/bryan31/RogueMap.git）
作为 git 子模块引入本项目 `RogueMemory/` 目录，在补丁分支
`patch/windows-mmap-crash-fix` 上修改源码，本地构建为 `1.1.7-patch1` 安装到本地 Maven 仓库，
主项目 pom 显式依赖该补丁版本。

## 崩溃根因（源码级）

1. **恢复扫描把索引 blob 当记录解析（启动 3 秒即崩的根因）**
   `checkpoint()` 把 OrdinalRegistry / BM25 序列化块通过 `allocator.allocate()` 追加进
   记录分配流；而打开已有文件的恢复扫描（`rebuildOffsetTables` / `rebuildFromScan`）从
   HEADER_SIZE 扫到 `usedMemory()`——包含 blob 区。扫描把 blob 的序列化字节误当记录头，
   解析出垃圾长度字段后按其跳跃，`Unsafe` 读到未映射内存 → JVM 崩溃。
2. **解析全程零越界校验（运行 12 分钟崩溃的根因之一）**
   `parseRecordHeader` 里 `catch (Exception)` 拦不住原生段错误；长度字段一旦是垃圾值
   （半成品写入、并发撕裂），`pos` 可跳到映射区之外。
3. **读写完全无互斥**
   崩溃日志显示两个线程同时在 `parseRecordHeader` / `parseRecordFull`：写入是多步
   Unsafe 拷贝，非原子；检索线程并发解析会读到半成品记录。

## 补丁内容（子模块提交 f054982）

- `parseRecordHeader(addr, limit)` / `parseRecordFull(addr, rh, limit)` /
  `computeDeletedByteOffset(addr, limit)`：全部改为**段感知越界校验**，任何字段读取前先与
  所在映射段结束地址（新增 `MmapAllocator.getAddressUpperBound`）比对；垃圾长度 → 返回
  null/-1 跳过，绝不再越界读。
- 文件头新增**记录区结束偏移**（`MmapFileHeader.MEMORY_DATA_END_OFFSET_POS = 136`）：
  `saveIndexes()` 在分配 blob 之前采集 `usedMemory()` 并写入文件头；恢复扫描据此止步，
  旧格式文件（该字段为 0）回退原行为。
- 恢复扫描遇到不可解析位置时按“4 字节长度 + 数据”布局调用 `skipIndexBlobAt` 跳过 blob，
  兼容旧格式文件，也覆盖崩溃发生在 checkpoint 中途的场景。
- `loadOrdinalRegistry` / `loadBm25`：size 字段先校验（0 ≤ size ≤ 512MB 且不越界）再分配读取。
- `RogueMemory` 全部读写路径增加 `ReentrantReadWriteLock`（写：add/update/delete/
  deleteByNamespace/checkpoint/close；读：get/exists/search），杜绝并发解析撕裂数据。
- 版本号 1.1.7 → 1.1.7-patch1（上游 master 即 1.1.7，同样存在上述缺陷）。

## 构建与依赖

```bash
# 子模块初始化（新机器首次）
git submodule update --init
cd RogueMemory
# 补丁构建 + 安装到本地 Maven 仓库（本地仓库目录由 settings.xml 的 localRepository 决定）
mvn install -DskipTests
```

主项目 `pom.xml` 依赖 `com.yomahub:roguemap-memory:1.1.7-patch1` 与
`com.yomahub:roguemap-embedding:1.1.7-patch1`。**必须先执行上述子模块构建**，
否则依赖解析失败——这是刻意设计：防止误用回 Maven 中央仓库的崩溃版本。

## 验证

1. **压测**：180 次并发检索 + 25 轮“入库→checkpoint→删除”（每轮都触发此前崩溃路径），
   后端存活，无新增 hs_err。
2. **独立恢复测试**（`/tmp/rmtest/TestRecovery.java`，三步全部通过）：
   - 创建 200 记录 + checkpoint + 50 追加记录 → 干净关闭 → **重开检索正常**
     （旧版此路径启动即崩）；
   - `Runtime.halt(1)` 模拟 JVM 强杀（dirty=1）→ **dirty 恢复扫描正常**，检索命中；
   - 注：最后一段未 checkpoint 的记录在崩溃后不承诺保留，属库自身 checkpoint 语义
     （旧代码恢复扫描终点相同），非回归；本项目知识库启动时从 DuckDB 全量重建索引，不受影响。
3. **端到端**：前端代理 `/api/knowledge/*` 状态、检索（“35kV 召回”命中电力文档）、
   文档清单全部 200；知识库页面显示“就绪 · 关键词模式”，11 文档 · 3475 切片，无报错。

## 顺带修复

- **去掉“模型配置弹窗应用时自动把向量模型倒灌进知识库”**（TaskComposer.applyModelDialog）。
  此前聊天模型弹窗会把 18888 网关（不支持 /embeddings）静默写入知识库，产生
  “已配置但持续报错”的幽灵状态；现在知识库向量模型只在知识库页手动配置、手动应用
  （弹窗内的“应用到知识库”按钮保留）。
- 重启后端后知识库回到干净状态：`embeddingConfigured=false`，不再有幽灵配置。
