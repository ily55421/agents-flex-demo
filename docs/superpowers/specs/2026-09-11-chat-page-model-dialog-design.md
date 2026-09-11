# 模型配置弹窗 + 左栏优化 + 纯对话页 — 设计文档

日期：2026-09-11 · 状态：已确认（用户批准实现）

## 目标

1. 模型配置支持**自定义**（不全是预设）：预设快捷填充 + 全部字段自由编辑 + 另存个人档案
2. **左侧布局优化**：超长的模型连接字段收进**模型配置弹窗**，左栏只留基本信息 + 配置入口 + 折叠的高级参数
3. 新增**纯对话页面**：顶部 Tab 切换，选择已创建智能体后直接对话

## 决策（已确认）

- 页面切换：**顶部 Tab**（App.vue 内 activeTab 状态，`?view=chat` URL 参数可恢复；不引 vue-router）
- 智能体列表：新增后端 `GET /api/agent/agents`（当前进程 agents + DuckDB 归档 archivedAgents，按 agentId 去重，返回安全视图不含 Key）
- 弹窗范围：**聊天模型 + 向量模型**都进弹窗
- 自定义深度：**现有全部字段自由填写**（不加请求头等高级项）

## 一、后端

- `ShowcaseRuntime.listAgents()`：合并 agents map 与 archivedAgents 的 toView()，按 agentId 去重，按 createdAt 倒序
- `AgentDefinitionController` 新增 `GET /api/agent/agents` → List<Map>

## 二、前端

### ModelConfigDialog.vue（新）
- props：`model`（当前聊天配置快照）、`embedding`（当前向量配置快照）、`open`
- emits：`close`、`apply(chat, embedding)`（写回 TaskComposer 表单）
- 弹窗内 Tab：聊天模型 / 向量模型
  - 顶部预设下拉（现有 5 聊天 + 5 向量预设，含 bge-m3 系列）
  - 中部全部字段自由编辑
  - 底部：另存为档案（localStorage）/ 删除档案 / 取消 / 应用
- 档案存储复用 ModelProfileBar 的 localStorage 结构（profileType chat|embedding）

### ChatHome.vue（新，纯对话页）
- 智能体选择：agentApi.listAgents() 下拉/卡片（agentId、名称、描述、模型、工具数）
- 选择后中央对话区：复用 ChatWorkspace + EventStream + 各交互面板（表单/审批/挂起/结果）
- 未创建 Agent → 引导卡片"去工作台创建"
- 对话走 useAgentRun 现有 create/continueConversation/start

### App.vue 改造
- 顶部 Tab 导航（Agent 工作台 / 纯对话），activeTab ref，`?view=chat` 恢复
- 工作台 = 现有三栏（左栏精简）；纯对话 = ChatHome
- Tab 切换时重置当前 Run 工作区（不删后端 Run）

### TaskComposer.vue 精简
- 移除常驻的"模型连接"与"向量模型"表单组
- 新增"⚙ 配置模型"按钮 → 打开 ModelConfigDialog
- 保留：基本信息、执行与上下文（折叠）、预算（折叠）、重试与压缩（折叠）、高级策略（折叠）、创建/重新配置按钮
- 模型连接信息折叠为一行摘要（服务商 · 模型 · API 地址），点击摘要或按钮打开弹窗

## 三、验证

- mvn test + pnpm build 全绿
- Playwright E2E：Tab 切换、弹窗打开/预设应用/自定义填写/档案保存、纯对话页选智能体对话、左栏精简
- 提交推送 gitee + task 记录
