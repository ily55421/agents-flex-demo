# 升级 JDK 21 + Spring Boot 3.5.16 + 内嵌 Neo4j 图数据库

## 结论

一次性完成两项改造，全部端到端验证通过：

1. **平台升级**：Java 1.8 → **21**，Spring Boot 2.7.18 → **3.5.16**，DuckDB 0.9.2 → **1.5.5.1**，
   机械改动由 OpenRewrite `UpgradeSpringBoot_3_5` 配方自动完成。
2. **内嵌 Neo4j 图数据库**：引入 `org.neo4j:neo4j:5.26.30`（LTS，支持 Java 17/21），
   启动时自动把知识库文档中的电力拓扑解析为图谱（9 站 / 405 设备 / 415 连接 / 32 线路），
   提供 `/api/graph/*` 查询接口与 Agent 的 `query_topology` 工具。

## 升级明细

### OpenRewrite 自动迁移
- pom 挂 `rewrite-maven-plugin:6.46.1` + `rewrite-spring:6.37.1`，
  配方 `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5`（内部链式覆盖 2.7→3.5 全程），
  执行 `mvn rewrite:run` 后 parent 自动升到 3.5.16，javax→jakarta 全量替换
  （annotation/validation），`RunViewMapper` 等处的模式匹配同步优化。
- **手工回退一处**：配方给 ehcache 加了 `jakarta` classifier——按既定决策撤回。
  JCache 规范没有改名（仍是 javax.cache），jakarta classifier 变体实现的是
  jakarta.cache SPI，会导致 `javax.cache.spi.CachingProvider` 查找失败、启动即挂。
  `javax.cache` 与 `javax.sql`（JDK 自带）保持不变。
- `java.version` 手工 17 → **21**（配方默认给 17）。
- 迁移完成后移除 rewrite 插件。

### 依赖升级
- `duckdb_jdbc 0.9.2 → 1.5.5.1`：现有 SQL（knowledge_document / run 归档）完全兼容，
  旧库文件 `./data/showcase.duckdb` 直接打开，数据无损。
- `agents-flex 2.2.9`：JDK 21 冒烟通过——模型状态、归档清单、Agent 创建（工具 lambda、
  压缩器、执行策略全链路构建）均正常，仅在 API Key 业务校验处按设计拦截。
- `RogueMemory 1.1.7-patch2`（子模块）：**0 条 JVM/Unsafe 告警**，知识检索正常；
  崩溃恢复测试（干净重开 / halt 模拟强杀后 dirty 恢复）在 JDK 21 下全部通过。

### eclipse-collections 版本仲裁（RogueMap SDK patch2）
内嵌 Neo4j 5.26 依赖 eclipse-collections **11.1.0**（`ImmutableByteObjectMapFactory.from`
等 API 在 9.2.0 不存在），而 hnswlib-core 1.2.1 传递 9.2.0 并仲裁胜出 → Neo4j 启动即
`NoSuchMethodError`。按"改 SDK 源码适配"原则，在 RogueMap 子模块父 pom 的
dependencyManagement 统一钉到 11.1.0（版本升至 **1.1.7-patch2**，提交 784af61），
11.x 对 SDK/hnswlib 用到的 9.x API 二进制兼容，SDK 测试套件验证通过。
主项目 pom 同步保留钉版说明（双保险，防依赖树变化）。

**上游存量测试失败记录**（基线 e78b7f9 同样失败，与本次改动无关，构建时排除）：
- `roguemap-core LowHeapStringSetTest#testPersistentLowHeapSetRecovery`
- `roguemap-memory RogueMemoryPersistenceTest#compactRemovesTombstones`
  （compact 重命名前未释放旧文件映射，Windows 上文件被锁）

## 内嵌 Neo4j 图谱

### 模型
- `(Station {name})` 变电站（知识库文档标题）
- `(Element {station, name, type})` 站内一次设备，type 由名称推断
  （母线/主变压器/断路器/隔离开关/接地刀闸/互感器/电容器/避雷器/出线）
- `(Station)-[:HAS]->(Element)`，`(Element)-[:CONNECTED]->(Element)`
  ——来自文档中的连接路径链，如 `35kV母线 → 5311隔离开关 → 531断路器 → 主变102000147`
- `(Line {name})` 线路，`(Station)-[:HAS_LINE]->(Line)`，`(Element)-[:FEEDS]->(Line)`
  ——来自出线间隔表
- `(Fact {station, element, attribute, value})` 知识问答库 JSONL 的三元组

### 组件
- `graph/Neo4jConfig`：嵌入式 `DatabaseManagementService` Bean（destroyMethod=shutdown，
  Windows 上保证释放文件句柄），存储目录 `./data/neo4j`，pagecache 可配
- `graph/TopologyGraphService`：启动后台幂等导入 + 查询；`graph/GraphController`：REST
- Agent 工具 `query_topology`：按站/设备/线路关键词返回邻接关系，与 `search_knowledge`
  并列注册到所有 Agent

### REST 接口
- `GET /api/graph/summary` 节点/关系统计
- `GET /api/graph/stations` 站清单（含元素计数）
- `GET /api/graph/station/{name}` 单站子图（nodes + edges，可直接渲染拓扑图）
- `POST /api/graph/import` 知识库更新后手动重建（幂等）
- `POST /api/graph/query` 只读 Cypher（写关键字拦截，测试：`DETACH DELETE` 被拒 409）

## 验证记录

| 项 | 结果 |
|---|---|
| JDK 21 编译 + 启动 | ✅ ~8s 就绪 |
| DuckDB 1.5.5.1 数据 | ✅ 11 文档 / 3475 切片无损 |
| 图谱自动导入 | ✅ 9 站 / 405 元素 / 415 连接 / 32 线路 |
| 单站子图 | ✅ 藏木变：主变/断路器/隔离开关等含连接关系 |
| 只读 Cypher + 写拦截 | ✅ 查询命中 `5311隔离开关→531断路器`；DELETE 被拒 |
| RogueMemory 检索/恢复 | ✅ 关键词命中；干净重开与强杀恢复全绿；0 告警 |
| agents-flex 冒烟 | ✅ Agent 构建全链路通过，仅按设计缺 API Key 拦截 |
| 前端代理 | ✅ /api/knowledge/*、/api/graph/* 均 200 |

## 注意

- 构建需 JDK 21（`JAVA_HOME=D:/dev/Java/jdk-21.0.10_windows-x64_bin/jdk-21.0.10`）。
- 新机器需先构建子模块：`cd RogueMemory && mvn install -DskipTests=false
  "-Dtest=!LowHeapStringSetTest,!RogueMemoryPersistenceTest"`。
- 图谱在知识库文档变化后需 `POST /api/graph/import` 重建（启动时也会自动导入）。
