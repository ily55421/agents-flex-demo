# 对话消息 Markdown 与 Mermaid 渲染

- 日期：2026-09-12
- 类型：功能优化（Agent 对话消息渲染升级 + 图表灯箱放大 + 结构化表达优先图示）

## 一、问题

Agent 对话页的助手消息此前是纯文本插值（`{{ message.content }}`），模型输出的
Markdown 源码（`**`、`##`、表格竖线、` ``` ` 围栏）全部原样显示，图表无法可视化，
也无法放大查看。

## 二、实现

### 依赖
- `markdown-it`（+`@types/markdown-it`）：GFM 式渲染，`html:false` 禁用内嵌 HTML 防注入，
  `breaks:true` 适配聊天单换行习惯，`linkify` 自动链接
- `mermaid`（v12）：动态 `import()` 自动分包（cynefin/elk 等懒加载 chunk），
  仅在首次出现 ```mermaid 块时加载

### 组件
- `MarkdownView.vue`：markdown → HTML 渲染 + 渲染后处理——把
  `pre > code.language-mermaid` 替换为 Mermaid SVG；失败回退为代码块 + 错误提示行。
  **流式安全**：围栏未闭合（奇数个 ```）时不渲染图表，保留代码展示，内容补齐自动重渲染
- `mermaidRenderer.ts`：**全局串行渲染队列 + 按图源缓存 + 失败缓存**。
  关键修复：mermaid 的 render 依赖全局内部状态，对话消息与 RunResult 两个组件
  并发渲染同一图表时会产生空白 SVG；串行化后消除。v12 API 在 default 导出上
  （`import('mermaid').then(m => m.default)`）
- 接入点：
  - `ChatWorkspace.vue`：助手消息 + 流式输出区（光标经 CSS 附着到最后一个块元素末尾）；
    用户/工具消息保持纯文本
  - `RunResult.vue`：终态 `finalOutput` 走 Markdown；错误/预算提示保持纯文本

### 结构化表达优先图示（两层保障）
1. **模型侧**：`ResearchAgentFactory.appendRenderGuideline` 给所有 Agent 的指令追加
   全局约定——内容存在结构化关系（流程/层级/拓扑连接路径/状态流转/时序）时优先用
   ```mermaid 图示，图后再补简短表格/文字；对重建的归档 Agent 同样生效
2. **前端兜底**：`transformStructuredChains` 把正文中连续的“A → B → C”连接链行
   （≥2 个 →、每段 ≤24 字、列表项或普通段落）自动转为 `flowchart LR`，
   **同名节点合并**（两条拓扑链共享“10kV母线”呈现为 7 节点连通图）；
   代码围栏内、表格行、引用行、含行内代码的行不参与转换，避免误判

### 图表灯箱放大
- 点击任意 `.md-mermaid` 打开全屏灯箱（Teleport 到 body）：暗色遮罩 + 白底圆角画布，
  右上工具条（＋/百分比/－/适应/✕），底部操作提示
- 交互：滚轮以指针为中心缩放（0.4x~8x）、拖拽平移、键盘 +/－/0/Esc、点击遮罩关闭
- **坑**：mermaid SVG 的 `width="100%"` 在自适应尺寸的绝对定位画布中宽度塌缩为 0
  （画布只剩内边距、SVG 不可见），必须显式给 `width: min(82vw, 980px)`，
  高度按 viewBox 比例计算

### 样式
- `.md-body` 全套 Markdown 样式：标题/表格（横向滚动）/代码块（深底）/引用/列表/链接
- `.md-mermaid` 容器：白底圆角边框、`max-height: 380px` 内部滚动、`cursor: zoom-in`，
  SVG `max-width: 100%`

## 三、验证

- `pnpm build`（vue-tsc）通过
- E2E `task/e2e_chat_markdown.py`（真实 ollama/qwen2.5:7b 对话）：
  表格/二级标题/行内代码渲染 ✓；mermaid SVG=2（168×240、3 节点、20 图形元素）✓；
  **空白 SVG 防回归断言**（尺寸 + 图形元素计数）✓；RunResult Markdown ✓；零控制台错误 ✓
- E2E `task/e2e_chat_mermaid_zoom.py`：连接链自动图示（两条链共享母线合并为 7 节点）✓；
  灯箱打开 SVG 744×152 非空（防塌缩断言）✓；滚轮缩放 100%→132%（SVG 高 152→201，
  防只变百分比回归）✓；Esc 关闭 ✓；零控制台错误 ✓
- judge 视觉验收：第一轮发现空白 mermaid 容器（并发冲突）→ 串行队列修复后 pass；
  灯箱第一轮发现画布塌缩（width:100% 塌缩为 0）→ 显式宽度修复后两张截图均 pass
- 图谱 Tab E2E 回归通过

## 四、注意

- mermaid v12 相比 v11 的 API 导出方式有变化，升级 mermaid 时注意 `default` 导出
- 模型侧渲染约定在 Agent 创建/重建时注入，后端重启后首次发送（自动重建归档 Agent）即生效
- 事件流（EventStream）与工具消息仍为纯文本展示（工具输出为 JSON，Markdown 化无收益）
