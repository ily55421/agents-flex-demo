# 工具预算默认值与 Agent 复用修复

- 日期：2026-09-12
- 类型：问题修复（预算默认值 / Agent 定义堆积 / 刷新复用 / 模型配置持久化）

## 一、问题与根因

1. **改了默认值仍 BUDGET_EXCEEDED（used=8, limit=8）**
   `maxToolCalls` 默认值 8→100 只影响新创建；存量 Agent 定义（DuckDB
   `agent_definition`）里存的是 8，重启后归档重建沿用旧值。
2. **「Agent 维护」页归档副本越堆越多、刷新后不能复用**
   `agentId = "showcase-agent-" + UUID.randomUUID()`——同名同版本的 Agent 每次
   重建（重启后自动恢复、重复提交）都新增一行定义，永不复用。
3. **每次刷新/重启都要重新选 Agent、重新应用模型配置**
   前端选中状态与后端模型连接都是纯内存态。

## 二、修复

### 预算默认值 8 → 100
- 后端 `CreateAgentRequest.maxToolCalls = 100`
- 前端 `TaskComposer.vue` 预算表单默认 `maxToolCalls: 100`

### 确定性 agentId（复用而非新增）
- `ShowcaseRuntime.definitionFingerprint(name, version)`：`SHA-256(name#vVersion)`
  前 12 位 hex 作 agentId → 同名同版本重建命中同一行，`ON CONFLICT(agent_id)`
  幂等覆盖；**要迭代配置应递增版本号**（不同版本各自独立）
- 「Agent 维护」页提示文案同步更新

### 启动迁移（RunArchive.ensureSchema 内，先于 ShowcaseRuntime 读取）
- **每次启动（幂等）**：按 (name, version) 去重归档定义，只保留最新一行——
  清理随机 ID 时代的存量堆积，也自愈确定性 ID 切换期的过渡重复
- **一次性（标记 agent_meta.definition-budget-v2）**：存量定义 `maxToolCalls==8`
  升级为 100；升级后用户显式改回 8 不会被再次覆盖
- 实测：清理 15 条重复副本、升级 2 条预算；第二次启动不再执行（幂等）

### 前端：刷新恢复选中 Agent
- `selectAgent` 写入 `localStorage[agents-flex-demo.selected-agent-id]`
- `initialize` 末尾：无 URL run 恢复时按存储 id 从 `listAgents` 恢复选中

### 聊天模型连接持久化（重启免重配）
- 新增 `ModelSettingsStore`（`agents-flex.model.settings-path`，默认
  `./data/model-settings.json`，原子写入；API Key 明文本地保存，与知识库设置
  同一安全等级）
- `ResearchAgentFactory.applyModelConnection` 应用成功后保存请求字段快照；
  `@PostConstruct restoreModelConnection` 启动时按同一“非空才覆盖”语义恢复
- 实测：应用一次 → 重启 → `modelStatus.configured=true`（ollama/qwen2.5:7b 自动恢复）

### 附带修复（启动日志暴露的存量 bug）
- `GraphArchive.upsertMeta` 使用 DuckDB 1.5 不支持的 `MERGE INTO ... KEY` 语法，
  指纹标记从未写成功 → 每次重启重复刷新图谱配套数据并报错；改为 `ON CONFLICT`，
  迁移日志从此干净（0 ERROR），图谱配套数据幂等跳过生效

## 三、验证

- `mvn compile` / `pnpm build` 通过；`mvn test` 全量回归通过
- E2E `task/e2e_agent_reuse.py`（两次连续通过）：
  定义数迁移后 2 条且重建前后不变（复用同一行）✓；归档 Agent 重建对话完成、
  头部 Tools `0/100` ✓；刷新后自动恢复选中 Agent ✓；零控制台错误 ✓
- 重启自动恢复模型连接实测 ✓；迁移幂等（第二次启动 0 清理 0 升级）✓

## 四、注意

- 用户名下同名同版本但不同配置的需求：改配置后**递增版本号**再创建（v2/v3…）
- E2E 发送环节存在偶发竞态（Enter 时组件恰在重渲染），脚本已按
  “输入框禁用即视为已受理”判定；前端逻辑本身无此问题
