# DuckDB数据库与Ehcache缓存集成及启动验证

- 日期：2026-09-11
- 类型：功能开发 + 启动验证 + 推送远端

## 一、功能：数据库（DuckDB）+ 缓存（Ehcache）

### 依赖（pom.xml）
- `spring-boot-starter-cache` + `org.ehcache:ehcache`（Boot 管理版本）+ `javax.cache:cache-api`：Ehcache 3 JCache 缓存
- `org.springframework:spring-jdbc`：JdbcTemplate（不引入 starter-jdbc，避免 Boot 自动数据源干扰）
- `org.duckdb:duckdb_jdbc:0.9.2`：兼容 Java 8 的嵌入式单文件数据库（0.10+ 需要 Java 11）

### 新增文件
- `persistence/DuckDbConfig.java`：DuckDB DataSource（默认 `jdbc:duckdb:./data/showcase.duckdb`，`DUCKDB_URL` 可覆盖，自动创建父目录）+ JdbcTemplate
- `persistence/RunArchive.java`：三张表（`agent_definition`/`run_snapshot`/`run_event`）幂等建表；Agent 定义视图、Run 快照视图、事件审计日志的 JSON 读写；`@Cacheable`/`@CacheEvict` 接入 Ehcache
- `config/CacheConfig.java`：`@EnableCaching` + Ehcache JCache CacheManager（`agentDefinitions` 10 分钟、`modelStatus` 1 分钟、`archivedRunSnapshots` 10 分钟），null 值不进缓存

### 改造文件
- `runtime/ShowcaseRuntime.java`：新增 `@Autowired` 双参构造器（保留单参构造器给测试，archive 为空时全部跳过）；createAgent 归档 Agent 视图并失效 modelStatus 缓存；create/continueConversation/advance 落库快照；onEvent 追加事件日志；get/list 回退 DuckDB 历史；`@PostConstruct` 恢复归档 Agent；`modelStatus()` 加缓存
- `application.yml`：`agents-flex.persistence.duckdb-url` 配置
- `.gitignore`：忽略 `data/`、`*.duckdb*`
- `README.md`：更新实现边界、项目结构与依赖说明

### 行为边界
- 重启后可浏览历史 Agent/Run（DuckDB 快照），但 Runner/ChatMemory/SSE 等运行态对象在内存，旧 Run 不能继续执行
- 热读缓存：Agent 定义不可变；终态 Run 快照写入时逐条失效；模型状态创建 Agent 时主动失效

## 二、验证结果

- `mvn test`：74 个测试全部通过（含新增 `RunArchiveTest` 2 个跨实例持久化用例）
- 后端 `mvn spring-boot:run`：Tomcat 8080 启动成功，Ehcache 三个缓存创建正常
- 前端 `pnpm install` + `pnpm build`：构建通过
- 双服务同启：8080 与 5173 同时监听，`/api/agent/runs/model` 与 `/` 均 200，无端口冲突
- 修复：DuckDB DataSource 的 `destroyMethod="close"` 导致 Spring 5.3 `DriverManagerDataSource` 无 close() 启动失败，已移除

## 三、启动最小依赖（本次分析结论）

| 服务 | 依赖 | 端口 | 状态 |
| --- | --- | --- | --- |
| 后端 | JDK 8+（本机 17 编译 target 1.8）、Maven 3.x、agents-flex 2.2.9（Maven Central 可直接解析，无需本地源码）、DuckDB/Ehcache 自动下载、可写 `./data` | 8080 | 已验证 |
| 前端 | Node 18+（本机 22）、pnpm 10、`pnpm install` | 5173 | 已验证 |

- 端口：后端 8080（application.yml）、前端 5173（vite.config.ts，冲突时 Vite 自动递增）；DuckDB/Ehcache/OTel 均为进程内，不占端口
- 不配 `LLM_API_KEY` 也能启动，但创建 Agent 会被后端明确拒绝

## 四、待办

- 已推送至 `https://gitee.com/lkq55421/agents-flex-demo.git`（新 remote `gitee`，分支 main）
