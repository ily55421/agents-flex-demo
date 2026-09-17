# 单 jar 打包部署（含前端）

- 日期：2026-09-12
- 类型：部署能力（单可执行 jar + data 目录，服务器直启）

## 一、部署形态

```
部署目录/
├── agents-flex-demo-1.0.0-SNAPSHOT.jar   # 单可执行 jar（内含前端构建产物 + 图谱资源 + Neo4j）
└── data/                                 # 状态目录（可选：不迁移则为全新空库）
    ├── showcase.duckdb                   # Agent 定义 / 会话归档 / 知识库文档与原文（重要）
    ├── model-settings.json               # 聊天模型连接（重启自动恢复主模型配置）
    ├── knowledge-settings.json           # 向量模型配置（可选）
    ├── neo4j/                            # 图数据库存储（可选，缺省自动从 jar 内资源重建）
    └── knowledge-mem*                    # 知识库索引缓存（可选，启动时自动从 DuckDB 重建）
```

启动：`java -jar agents-flex-demo-1.0.0-SNAPSHOT.jar`（工作目录 = 部署目录，
默认端口 8080；`SERVER_PORT` 可改）。启动后直接访问 `http://服务器:8080/` 即是完整前端，
前后端同源，无需 Nginx / CORS / 单独的 Node 进程。

## 二、打包

- Windows：`deploy\package.bat`；Linux/macOS：`./deploy/package.sh`
- 步骤：前端 `pnpm build` → dist 拷入 `src/main/resources/static` → `mvn -DskipTests package`
- 产物：`target/agents-flex-demo-1.0.0-SNAPSHOT.jar`（约 250MB：依赖 + 前端 + 图谱资源）
- 注意 `src/main/resources/static/` 已加入 .gitignore（是构建产物，不入库）；
  只跑 `mvn package` 而不先拷 dist 会打出不含前端的旧包——务必走打包脚本

## 三、实现要点

- `WebConfig.addResourceHandlers`：`/**` 优先映射 `file:./frontend/dist/`（服务器上
  换前端无需重新打 jar），回退 `classpath:/static/`（jar 内）。前端路由是 URL
  查询参数（?view=...），单入口即可，无需 SPA history 回退
- 前后端同源后 CORS 配置仅对 Vite 开发端口生效，生产同源访问不受影响
- 内嵌 Neo4j / DuckDB / RogueMemory 均以相对路径 `./data/...` 读写，跟随工作目录

## 四、部署演练记录（2026-09-12 实测通过）

隔离目录 `deploy-test/`（jar + data 副本）`java -jar` 启动约 14s 就绪：

| 检查项 | 结果 |
|---|---|
| `GET /` 返回前端 index.html，assets JS 200 | ✅ |
| `GET /api/graph/summary` | ✅ 693 实体 / 3598 关系，hashMatch=true |
| `GET /api/agent/agents` | ✅ 2 条定义，maxToolCalls=100（DuckDB 状态随迁） |
| `GET /api/knowledge/status` | ✅ 11 文档 / 3475 切片（mmap 索引自动重建） |
| `GET /api/agent/runs/model` | ✅ 自动恢复 ollama/qwen2.5:7b（model-settings.json） |

## 五、服务器上须知

1. **JDK 21** 运行（无需 Maven/Node/pnpm——那些只在打包机上）
2. **模型可达性**：主模型 Endpoint 必须从服务器可达；指向本机 Ollama（localhost:11434）
   的配置迁到服务器后需改为服务器可达地址，在「模型配置」页重新应用一次即可（会自动持久化）
3. **知识库为空也能用**：BM25 关键词检索不依赖向量模型；不迁移 data/ 时可在
   知识库页「清空重建」灌入内置示例
4. 强杀进程安全：知识库索引每次启动从 DuckDB 自动重建，Neo4j 事务存储崩溃安全；
   图谱资源指纹不一致时启动后台自动重建
5. 端口被占用：`SERVER_PORT=9090 java -jar ...` 换端口（前端同源，无其他配置）

## 六、服务端管理脚本 deploy/restart.sh

放在部署目录（与 jar、data/ 同级），`chmod +x restart.sh` 后使用：

| 命令 | 行为 |
|---|---|
| `./restart.sh`（默认 restart） | 优雅停止（SIGTERM，等 30s 释放 Neo4j/DuckDB 句柄，超时 kill -9）→ 后台启动 → 轮询等待就绪 |
| `./restart.sh start` | 仅启动（nohup 后台，写 logs/backend-<时间戳>.log） |
| `./restart.sh stop` | 仅停止 |
| `./restart.sh status` | 查看 PID / 端口 / jar |

- 就绪判定：轮询 `http://127.0.0.1:$SERVER_PORT/`（最长 120s），未就绪自动打印最近 20 行日志
- 日志轮转：保留最近 10 份 backend-*.log
- 环境变量覆盖：`JAR_FILE`（默认部署目录最新 jar）、`SERVER_PORT`（默认 8080）、
  `JAVA_BIN`（服务器 PATH 非 JDK 21 时必设）、`JAVA_OPTS`（默认 -Xms512m -Xmx2g）
- 示例：`JAVA_BIN=/usr/lib/jdk-21/bin/java SERVER_PORT=9090 ./restart.sh`

部署演练实测（2026-09-12）：start/restart/status 全流程通过，restart 优雅停机后
前端 200、API 正常；PATH java 版本不符时用 JAVA_BIN 覆盖即可（脚本会打印类版本错误日志）。
另加 `.gitattributes`（*.sh text eol=lf），保证脚本在 Linux 上无 CRLF 问题。

## 七、捆绑 JRE（服务器免装 JDK）

- `deploy/runtime/OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz`（Eclipse Temurin JRE 21，
  Linux x64，约 50MB；SHA256 与 Adoptium 官方一致：24131497…c5b500；已加入 .gitignore）
- 部署时把它随 jar 上传到部署目录并解压：`tar -xzf OpenJDK21U-jre_*.tar.gz -C runtime/`
  （或直接解压到部署目录顶层），`restart.sh` 会**自动识别** `runtime/jdk*/`、`runtime/jre*/`、
  顶层 `jdk*/`、`jre*/` 下的 java，无需在服务器安装任何 JDK
- `restart.sh` 解析顺序：`JAVA_BIN` → 捆绑 JRE/JDK → `JAVA_HOME` → `PATH`（后两者校验
  主版本 ≥21，不满足时给出中文解决提示并拒绝启动）
- 若偏好完整 JDK：从 https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/linux/ 下载
  同系列 tar.gz 替换即可，识别逻辑相同

## 八、v1.1 加固（2026-09-12 服务器 39090 端口问题）

服务器实测暴露的问题：旧实例（手动启动、无 pid 记录）占用端口，`sh` 运行时探测全失效
（dash 无 /dev/tcp、curl 缺失、netstat 参数不兼容），脚本"检测不到占用 → 又起一个 →
bind 失败"。加固内容：

1. **bash 守卫**：`sh restart.sh` 调用时自动 `exec bash` 重入（dash 兼容问题一次性解决）
2. **停止逻辑三重定位**：pid 文件 + 端口监听者（ss/lsof/fuser）+ `ps -ef` 按 jar 名匹配
   （覆盖无 pid 记录的手动启动实例；Linux 与 Git Bash 通用），优雅 TERM 30s 超时强杀
3. **端口探测三重兜底**：curl → wget → bash 内建 /dev/tcp
4. **交接保护**：stop 后等待端口真正释放（最长 10s）再启动；start 时端口被占则拒绝并
   给出处理指引，不再盲目拉起第二个实例
5. 附带：ps 候选 PID 限定为纯数字列（避免路径串混入 kill 参数）

服务器上遇到"提示未占用但启动失败"时的处理顺序：`./restart.sh stop`（会按 jar 名找到
遗留实例）→ `./restart.sh start`；或手动 `ss -ltnp | grep 端口` 找 PID 后 kill。
