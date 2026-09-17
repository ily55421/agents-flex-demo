# ArcadeDB 与 Neo4j Embedded 对比选型结论

- 日期：2026-09-12
- 类型：技术选型对比（纯咨询，无代码修改）
- 前置：见《Neo4j本地嵌入式Java替代方案调研.md》

## 一、结论

**默认推荐 ArcadeDB**；仅当"软件不对外分发 + 已在 Java 17/21 + 重度依赖 Neo4j 图科学生态（GDS/APOC）"时，Neo4j Embedded 才是更优解。本项目（Apache 2.0 开源仓库）许可证维度直接排除 Neo4j Community Embedded。

## 二、上轮信息修正（重要）

- **ArcadeDB Java 要求修正**：主线（24.4.1 起，Maven Central）需 **Java 21**；Java 17 走专用 `java17` 分支（GitHub Packages，不在 Maven Central）；Java 11 已不支持。上轮"需 Java 11+"的说法已过时
- **Neo4j Embedded 仍在官方支持**：Java Reference v2026.07 完整覆盖嵌入式用法，`org.neo4j:neo4j:2026.07.1` 在 Maven Central 公开（Community）
- **Neo4j Enterprise 嵌入式已不公开**：官方文档明确 Enterprise 嵌入需联系销售（"get in contact with Neo4j Professional Services"）
- **Neo4j 版本/Java**：2025.x/2026.x 要求 Java 21；5.26 LTS 支持 Java 17；4.4 需 Java 11；3.5 才支持 Java 8（已 EOL）

## 三、核心对比

| 维度 | Neo4j Community Embedded | ArcadeDB Embedded |
|---|---|---|
| 许可证 | **GPLv3（传染性）** | **Apache 2.0** |
| Java 要求 | 21（2026.x）/17（5.26 LTS） | 21（主线）/17（java17 分支） |
| 查询语言 | Cypher 原生（最完整）+ GQL | openCypher 25（TCK 97.8%）+ SQL + Gremlin + GraphQL + Mongo QL |
| 生态 | 20 年积淀：APOC、GDS 图算法库、LangChain/LlamaIndex 集成、可视化 | 社区较小，工具链少 |
| 数据模型 | 图（向量索引 5.13+） | 图+文档+KV+全文+向量+时序 多模型 |
| HA/复制 | Enterprise 付费 | 社区版免费内置 |
| 内存 | ~2-4GB 起步 | low-ram 档 16MB 起步 |
| 资源占用 | 偏重 | 轻量，面向嵌入式优化 |

## 四、GPLv3 边界（Neo4j Embedded 的使用红线）

- 纯内部系统使用：OK（不触发分发义务）
- 自营 SaaS：OK（GPL 无网络使用条款；Neo4j 2018 年正是从 AGPL 改为 GPL）
- **分发二进制（卖软件/交付客户环境/开源仓库发布）：必须整体开源为 GPL 或购买商业授权**
- 本项目为 Apache 2.0 开源仓库 → 嵌入 Neo4j Community 与发布行为直接冲突

## 五、本项目适配

- 当前 Java 8：两者均不可用（Neo4j 仅 EOL 的 3.5 支持 Java 8）
- 短期图需求 → 维持上轮结论：JGraphT 1.4.0 + DuckDB，或 TinkerGraph 3.6.x
- 若升级 JDK 21（连带解锁 Spring Boot 3/DuckDB 新版）：**选 ArcadeDB**（许可证契合、多模型可一站式承载拓扑图 + 向量，替代"图库 + RogueMemory"拆分方案的图那一半；RogueMemory 仍保留向量记忆职责）
- Neo4j Embedded 何时合理：内部工具 + 团队深度 Cypher/GDS 依赖 + 不分发 → 可选

## 六、参考

- Neo4j 嵌入式官方文档（v2026.07）：https://neo4j.com/docs/java-reference/current/java-embedded/setup/
- Neo4j Java Reference 目录：https://neo4j.com/docs/java-reference/current/
- ArcadeDB 系统要求（Java 21/25、17 走分支）：https://docs.arcadedb.com/arcadedb/reference/requirements
- ArcadeDB GitHub（Java 版本策略）：https://github.com/ArcadeData/arcadedb
- ArcadeDB 官方对比页（营销口径，需自证）：https://arcadedb.com/neo4j.html
