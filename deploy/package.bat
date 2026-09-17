@echo off
REM 一键打包：前端构建产物拷入后端资源目录，产出可直接部署的单可执行 jar。
REM 产物: target\agents-flex-demo-1.0.0-SNAPSHOT.jar（部署时与 data\ 目录同级放置）
REM 用法: deploy\package.bat   （需 JDK 21 + Maven + Node/pnpm）
setlocal
cd /d "%~dp0.."

echo [1/3] 构建前端 ...
if not exist frontend\node_modules (
  pushd frontend && call pnpm install || exit /b 1 && popd
)
pushd frontend && call pnpm build || exit /b 1 && popd

echo [2/3] 前端产物拷入 jar 静态资源目录 ...
if exist src\main\resources\static rmdir /s /q src\main\resources\static
mkdir src\main\resources\static
xcopy frontend\dist src\main\resources\static /e /i /q >nul || exit /b 1

echo [3/3] 打包后端 fat jar ...
call mvn -B -DskipTests package || exit /b 1

echo.
echo 打包完成: target\agents-flex-demo-1.0.0-SNAPSHOT.jar
echo 部署方式: 将 jar 与 data\ 目录放在同一目录，执行 java -jar agents-flex-demo-1.0.0-SNAPSHOT.jar
echo   - data\showcase.duckdb        Agent 定义 / 会话归档 / 知识库文档（必须迁移，否则为空库）
echo   - data\model-settings.json    聊天模型连接（免重新配置主模型）
echo   - data\knowledge-settings.json 向量模型配置（可选）
echo   - data\neo4j\                 图数据库存储（可选，缺省自动重建）
echo   - data\knowledge-mem*         知识库索引缓存（可选，启动时自动从 DuckDB 重建）
endlocal
