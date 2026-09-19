# AGENTS.md — 环境与构建说明

本文件记录本仓库的**环境要求**（重点是 **JDK 21 路径**）与常用命令，供人和 AI 代理共用。
文中路径为当前开发机实测值。

## 一、JDK：构建必须使用 JDK 21

`pom.xml` 声明 `<java.version>21</java.version>`，Spring Boot `3.5.16`。
**用 JDK 8 或 17 构建会直接失败**，报错：

```
Fatal error compiling: 错误: 不支持发行版本 21
```

### 开发机 JDK 21 路径（构建用，完整 JDK）

```
D:\dev\Java\jdk-21.0.10_windows-x64_bin\jdk-21.0.10
```

- 这是**完整 JDK**，含 `javac`（`javac 21.0.10`），用于 `mvn spring-boot:run`、`mvn package`。
- ⚠️ 路径有**两层嵌套**：外层 `jdk-21.0.10_windows-x64_bin` 只是解压目录，真正的 JDK 根目录在其下的 `jdk-21.0.10`。指到外层会找不到 `bin\java.exe`。
- 使用方式：

```powershell
$env:JAVA_HOME = "D:\dev\Java\jdk-21.0.10_windows-x64_bin\jdk-21.0.10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version    # 期望输出 21.0.10
javac -version   # 期望输出 javac 21.0.10
```

### 本机默认环境为什么不能用

| 来源 | 实际值 | 后果 |
| --- | --- | --- |
| `PATH` 中的 `java` | `D:\GitBack\soft\JEnv\java.bat`（jenv 包装脚本） | 解析到 **JDK 1.8.0_152**，编译失败 |
| `JAVA_HOME` | `D:\dev\Java\jdk-17.0.3.1` | 17 < 21，编译失败 |
| Maven 运行 JVM | `mvn -v` 显示 `Java version: 17.0.3.1` | `maven-compiler-plugin` 用 Maven 所在 JVM 的 `javac` 编译，所以**运行 Maven 的 JDK 也必须是 21** |

**结论：不要依赖全局环境变量，显式指定上面的 JDK 21 路径。**

`start-backend.ps1` 已内置 JDK 解析逻辑，按
`JAVA_HOME`(≥21) → `PATH`(≥21) → 常见安装目录扫描 的顺序自动定位 JDK 21，
并兼容「解压后多一层同名目录」的情况，因此**直接运行启动脚本无需手动设置环境变量**。

### 本机其它 JDK（不要用于构建）

```
D:\dev\Java\jdk1.8.0_152               # JDK 8，jenv 当前默认
D:\dev\Java\jdk-17.0.3.1               # JDK 17，当前 JAVA_HOME
D:\dev\Java\jdk-18.0.1.1               # JDK 18
D:\dev\Java\graalvm-ce-java17-22.3.0   # GraalVM CE 17
```

### 部署端运行时（Linux，非开发机构建）

部署端不构建、只运行 jar，相关运行时如下：

| 位置 | 说明 |
| --- | --- |
| `deploy/runtime/OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz` | Eclipse Temurin **JRE 21**（Linux x64，约 52MB），随 jar 上传到部署目录后解压 |
| `runtime/jdk-21.0.12+1-jre/` | ⚠️ **不是真的 JRE**。其 `bin/java` 只有 98 字节，是只 `echo` 版本号的 shell 桩脚本（占位用）。**不要指向它构建或运行** |

部署端 `deploy/restart.sh` 的 JDK 解析顺序：
`JAVA_BIN` → 捆绑 JRE/JDK → `JAVA_HOME` → `PATH`（后两者校验版本 ≥21）。

## 二、其它工具链

| 工具 | 版本 / 路径 |
| --- | --- |
| Maven | `3.8.6`，`D:\dev\apache-maven-3.8.6` |
| Node.js | `C:\nvm4w\nodejs\node.exe`（需 ≥18） |
| pnpm | `C:\nvm4w\nodejs\pnpm.ps1` |

## 三、常用命令

```powershell
# 一键启动前后端（自动解析 JDK 21）
.\start-all.ps1

# 单独启动
.\start-backend.ps1     # 后端 http://localhost:18080
.\start-frontend.ps1    # 前端 http://localhost:15173

# 手动构建（必须先切到 JDK 21）
$env:JAVA_HOME = "D:\dev\Java\jdk-21.0.10_windows-x64_bin\jdk-21.0.10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -B clean package -DskipTests
```

### 首次克隆需先构建子模块

`RogueMemory` 是 git 子模块（`https://github.com/ily55421/RogueMap.git`，分支 `patch/windows-mmap-crash-fix`），
主工程依赖其 `1.1.7-patch2` 产物，构建前需先安装到本地仓库：

```powershell
cd RogueMemory
mvn install -DskipTests=false "-Dtest=!LowHeapStringSetTest,!RogueMemoryPersistenceTest"
```

## 四、端口约定

| 服务 | 默认端口 | 覆盖变量 |
| --- | --- | --- |
| 后端 Spring Boot | `18080` | `SERVER_PORT`（`start-backend.ps1` 也接受 `BACKEND_PORT`） |
| 前端 Vite | `15173` | `FRONTEND_PORT`（代理目标由 `BACKEND_PORT` 决定） |

默认值刻意避开 `8080` / `5173` —— 这两个端口在开发机上常被其它项目占用。
后端 CORS 白名单已包含 `15173`–`15175` 与历史端口 `5173`–`5175`。

## 五、相关文档

- `README.md` — 项目介绍与演示流程
- `task/升级JDK21与Boot3.5并接入内嵌Neo4j.md` — JDK 8→21 + Boot 2.7→3.5 升级记录
- `task/JDK升级目标版本选型结论.md` — 为什么选 21（而非 17 / 25）
- `task/启动脚本生成与前后端启动.md` — 启动脚本说明
- `task/单jar打包部署.md` — 单 jar 打包与部署
