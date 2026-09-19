# 重启前后端服务并定位 ps1 脚本编码解析失败

日期：2026-09-19　范围：仅重启运行中的服务，**未改动任何代码**

## 结果

| 服务 | 端口 | 进程 | 验证 |
| --- | --- | --- | --- |
| 后端 Spring Boot | 18080 | java.exe PID 53588（mvn spring-boot:run） | `GET /api/knowledge/status` → 200，`{"ready":true,"searchMode":"HYBRID",...}` |
| 前端 Vite | 15173 | node.exe PID 11992 | `GET /` → 200；`GET /api/knowledge/status` → 200（代理链路通） |

## 重启过程

1. 旧占用（均为本仓库上一次会话启动的进程，非外部项目）：
   - `18080` → java.exe 24528（`com.agentsflex.showcase.ShowcaseApplication`），链路 40132(cmd) → 3828(maven) → 24528
   - `15173` → node.exe 36992（本项目 `frontend/node_modules/vite`），链路 21772(cmd) → 31608(pnpm) → 40232 → 36992
2. `taskkill /T /F` 终止两棵根进程树（40132 / 21772），`netstat` 确认 `18080`、`15173` 均已无监听后再启动，避免端口冲突。
   其它项目的 dev server（`dg-ui` vite、`room_ui` vite:3100、jdk1.8 java 49836）未触碰。
3. 前端经 `start-frontend.ps1` 启动成功；后端经 `start-backend.ps1` **启动失败**（见下），改用 PowerShell 7 执行同一脚本后启动成功（监听耗时约 80s）。

日志目录：`%TEMP%\agents-flex-showcase-restart\`

## 发现的缺陷：start-backend.ps1 在 Windows PowerShell 5.1 下无法解析

```
start-backend.ps1:29 字符: 1  }  表达式或语句中包含意外的标记“}”
start-backend.ps1:47 / :49 同类错误 → ParserError: UnexpectedToken
```

根因（已用字节验证，非猜测）：三个脚本都是 **UTF-8 无 BOM**（首 3 字节 `23 20 73` = `# s`），
而 Windows PowerShell 5.1 按系统 ANSI（GBK）解码。第 19 行注释中的全角括号 `（` 编码为
`EF BC 88`，后面紧跟 ASCII `"`（`22`）——GBK 把 `88 22` 当成一个双字节汉字，**引号被吞掉**，
该函数内字符串从此不闭合，于是解析错误报在函数收尾的 `}` 上（29/47/49 行）。

影响：`AGENTS.md` 里写的 `powershell -NoProfile -ExecutionPolicy Bypass -File .\start-*.ps1`
在 Windows PowerShell 5.1 下对 `start-backend.ps1` 不可用；`pwsh`（PowerShell 7，默认 UTF-8）正常。
`start-frontend.ps1` 只是运气好——它没有全角标点紧贴引号的组合。

## 建议修复（待确认，尚未执行）

给三个 `.ps1` 加 UTF-8 BOM（文件头补 `EF BB BF`）即可，保留中文注释不动；
或把注释里的全角 `（）；` 换成 ASCII 半角。二者都能让 5.1 与 7 同时可用。
