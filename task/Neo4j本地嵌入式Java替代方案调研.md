# Neo4j 本地嵌入式 Java 替代方案调研

- 日期：2026-09-12
- 类型：技术选型调研（纯咨询，无代码修改）

## 一、背景

- 需求：获得图数据库能力，但不单独部署 Neo4j 等中间件（纯 Java 进程内 / 嵌入式）
- 本项目约束：Java 8（pom.xml `java.version=1.8`）、Spring Boot 2.7.18；已集成嵌入式 DuckDB 0.9.2（0.10+ 需 Java 11）+ RogueMemory；存在"电力拓扑知识库"场景（连通性 / 路径 / 影响范围类图分析）

## 二、总体结论

1. **Neo4j 本身就能嵌入式使用**（引 jar、无需部署 server），但 Community 版为 GPLv3，且新版要求 Java 17+；Java 8 只能用已 EOL 的 3.5 —— 本项目现状下不可行
2. 2026 年 Java 生态最值得关注的替代：**ArcadeDB**（Apache 2.0、JVM 进程内嵌、openCypher 25 + SQL + Gremlin、兼容 BOLT 协议可直用官方 Neo4j Java Driver；需 Java 11+）
3. **Kùzu 已停更**：2025-10-10 原仓库归档（团队被 Apple 收购），社区分叉 LadybugDB 最活跃；新项目不建议直接依赖
4. **Java 8 下可用**：JGraphT 1.4.0（图算法库）、TinkerGraph（TinkerPop 3.6.x，Gremlin，内存 + GraphML 持久化）、JanusGraph 0.6.x + BerkeleyDB JE（全嵌入式 ACID 图库）、OrientDB 3.2（维护停滞）

## 三、候选对比

| 方案 | 形态 | 查询语言 | 持久化 | 许可证 | Java 要求 | 状态(2026) |
|---|---|---|---|---|---|---|
| Neo4j Embedded | jar 内嵌 | Cypher | 文件/ACID | GPLv3 | 17+（3.5 支持 8，已 EOL） | 活跃 |
| ArcadeDB | jar 内嵌 | openCypher25/SQL/Gremlin | 文件/ACID | Apache 2.0 | 11+ | 活跃，首选 |
| TinkerGraph (TinkerPop 3.6.x) | 纯内存 | Gremlin | GraphML/Gryo 文件 | Apache 2.0 | Java 8 可用 | 活跃 |
| JanusGraph 0.6.x + BDB JE | jar 内嵌 | Gremlin | 文件/ACID | Apache 2.0 | Java 8 可用 | 活跃但重 |
| JGraphT 1.4.0 | 内存算法库 | Java API | 无（自行落库） | EPL/LGPL | Java 8 可用 | 活跃 |
| Kùzu → LadybugDB | JNI 内嵌 | Cypher | 单文件 | MIT | 需确认 | 原仓库归档，分叉活跃 |
| DuckDB + DuckPGQ | JDBC 内嵌 | SQL/PGQ | 单文件 | MIT | DuckDB 1.2+（需 Java 11+） | 实验性 |
| OrientDB 3.2 | jar 内嵌 | SQL/Gremlin | 文件/ACID | Apache 2.0 | Java 8 可用 | 维护停滞 |

## 四、本项目建议（Java 8 现状）

- **推荐组合：JGraphT 1.4.0（内存图 + 算法）+ DuckDB（边表持久化）**：零新中间件、许可证无忧、Java 8 兼容；适合拓扑连通性 / 最短路径 / 影响分析等固定查询模式
- 需要交互式图查询语言（灵活查询模式）：**TinkerGraph 3.6.x**（Gremlin）；图数据 + RogueMemory 向量检索组合即可支撑 GraphRAG
- 写入频繁且要 ACID 图库：**JanusGraph 0.6.x + BDB JE**（后续可平滑切换 Cassandra/HBase 后端，代码不变）
- 未来升级 Java 17 / Spring Boot 3 后：**ArcadeDB** 综合最优（Cypher 兼容 + BOLT + 原生向量），是 Neo4j 的最佳"平替"
- 轻量场景：直接用现有 DuckDB 0.9.2 的 `WITH RECURSIVE` 递归 CTE 做可达性 / 传播分析，零新增依赖

## 五、参考

- Kùzu 归档与分叉：https://github.com/kuzudb/kuzu （已归档）；LadybugDB：https://github.com/LadybugDB/ladybug
- ArcadeDB 对比页：https://arcadedb.com/neo4j.html
- TinkerPop：https://tinkerpop.apache.org/ ；JanusGraph：https://janusgraph.org/ ；JGraphT：https://jgrapht.org/
