# JDK 升级目标版本选型结论（21 还是 17）

- 日期：2026-09-12
- 类型：技术选型结论（纯咨询，无代码修改）
- 前置：见《ArcadeDB与Neo4jEmbedded对比选型结论.md》

## 一、结论

**直接升 JDK 21（LTS），跳过 17；暂不上 25。** 一步到位（JDK 8→21 + Spring Boot 2.7→3.5 一次完成），不要把 17 当中转站。

## 二、为什么跳过 17

1. **图库主线要求 21**：ArcadeDB 主线（Maven Central）需 Java 21，17 只能走 `java17` 旁支（GitHub Packages）；Neo4j 2025.x/2026.x 嵌入式同样要求 21。升 17 = 图库选型自废主线
2. **17 的支持窗口正在关闭**：Oracle Premier Support 2026-09-30 到期（本月），之后仅扩展支持（安全补丁）至 2029-09；免费 OpenJDK/Temurin 17 安全支持约 2027-10 结束。现在落地 17 = 接盘刚出主流支持期的版本
3. **迁移成本 8→17 ≈ 8→21**：Java 8 升级最痛的部分（EE 模块移除 JAXB/activation、JDK 内部强封装、内部 API 清理）在 17 就全部要过，17→21 只是增量。两跳没有收益
4. **21 独有收益**：虚拟线程（JEP 444）对多 Agent 并发运行、事件流 IO 有实际价值；Spring Boot 3.2+ 开启 `spring.threads.virtual.enabled=true` 即用
5. **RogueMap 甜点位**：roguemap-core 大量使用 sun.misc.Unsafe（UnsafeOps）；JDK 23 起 JEP 471 弃用告警、24 起 JEP 498 每次调用告警。21 上完全干净

## 三、为什么暂不上 25

- JDK 25 是新 LTS（2025-09），ArcadeDB 官方兼容（要求页写明 21/25）
- 但 RogueMap 的 Unsafe 用法在 23+ 会触发告警（JEP 471/498），且生态验证面还在补齐（业界同期案例：StarRocks 2026-06 基线从 17→21，明确"21 first, 25 as follow-up"）
- 计划：RogueMap 将 UnsafeOps 迁移到 FFM（java.lang.foreign）后，再评估 25/29

## 四、支持时间线（Oracle 商业支持 / 免费生态）

| LTS | GA | Premier 到 | Extended 到 | 免费 Temurin 安全支持到 |
|---|---|---|---|---|
| 17 | 2021-09 | **2026-09-30** | 2029-09-30 | ~2027-10 |
| 21 | 2023-09 | 2028-09-30 | 2031-09-30 | ~2029-12 |
| 25 | 2025-09 | 2030-09 | 2033-09 | ~2031-09 |

许可提示：Oracle JDK 21 的 NFTC 免费窗口到 2026-09，之后的 Oracle build 更新转 OTN（商用收费）——**用 Eclipse Temurin / Amazon Corretto 21 等开源构建**（GPLv2+CE 永久免费）即可规避。

## 五、本项目迁移清单（一次到位）

1. `pom.xml`：`java.version` 1.8 → 21；Spring Boot 2.7.18 → 3.5.x（parent 升级）
2. javax → jakarta 批量迁移：`javax.validation`→`jakarta.validation`、servlet、`javax.annotation`→`jakarta.annotation`。**例外：`javax.cache` 保持不变**（JCache 规范未改名，Ehcache 3 JCache 继续用）
3. `duckdb_jdbc` 0.9.2 → 1.x（顺带解锁 DuckPGQ 等新能力）
4. agents-flex 2.2.9 在 JDK 21 下冒烟验证（纯 Java 8 库，大概率兼容；留意反射/动态代理）
5. RogueMemory 1.1.5 / roguemap-core 直接可用（21 无 Unsafe 告警）
6. 工具：OpenRewrite `UpgradeSpringBoot_3_5` 配方可自动化大部分机械改动
7. Boot 2.7 的 OSS 支持已于 2023-11 结束，本次升级同时消掉这笔三年旧债

## 六、参考

- Oracle Java SE Support Roadmap：https://www.oracle.com/java/technologies/java-se-support-roadmap.html
- ArcadeDB 系统要求（Java 21/25）：https://docs.arcadedb.com/arcadedb/reference/requirements
- StarRocks JDK 17→21 基线升级 issue（业界同期决策案例）：https://github.com/StarRocks/starrocks/issues/74519
