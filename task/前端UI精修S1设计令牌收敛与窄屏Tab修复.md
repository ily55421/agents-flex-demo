# 前端 UI 精修 S1：设计令牌收敛与窄屏 Tab 修复

日期：2026-09-19　提交：`271520f`　阶段：4 阶段计划中的第 1 阶段（S2 对话页 / S3 知识库页 / S4 共享基元待做）

## 背景

用户要求优化前端页面与 UI。已确认边界：**零新增依赖、不换视觉风格**，本批只做「设计系统层 + 对话页 + 知识库页 + 窄屏响应式」；不做暗色模式、不启用 Tailwind 工具类、不引入组件库、不拆分大组件。完整计划见 `C:\Users\84168\.qoder-cn\plans\fond-inlet-wagtail.md`。

## 改了什么（4 文件，+69 / −164）

| 文件 | 改动 |
| --- | --- |
| `frontend/src/style.css` | 新增令牌：`--radius-sm/lg/pill`、`--space-1..6`、`--z-header/popover/toast/dialog/lightbox/tooltip`、`--shadow-pop/dialog`；文件顶补三条纪律注释（含断点阶梯 1260/900/640） |
| 同上 | 修真实缺陷：`var(--text-strong)` 从未定义 → 改引用 `--text`（2 处） |
| 同上 | 新增统一键盘焦点样式（`:where(a,button,summary,[tabindex]):focus-visible` + checkbox/radio/range 单独补）；删 `.field-help` 上抹轮廓的 `outline:none` |
| 同上 | `.page-tabs` 改 `nowrap` + `flex:none` + 容器横向滚动并隐藏滚动条；`≤640px` 隐藏 `.tab-label` 退为图标 |
| 同上 | `.knowledge-shell`/`.models-shell` 补 `overflow-y:auto`（`html,body` 仍 `overflow:hidden`，滚动收进内容区） |
| 同上 | 裸 `z-index` 收敛为令牌（值不变）：`.app-header`20、`.field-help-popover`1000、`.chat-statistics-content`/`.chat-trace-content`30、`.error-toast`50 |
| 同上 | 删除无主死样式：`.chat-shell`（全仓仅自身定义）与 RuntimeOverview 独占的 24 行样式 + 6 行 640 断点规则 |
| `frontend/src/App.vue` | 5 个 Tab 文案包进 `.tab-label` 并补 `aria-label`/`title`；图谱页与知识库页 `<main>` 补 `id="main-content"`；5 个 `<main>` 统一 `tabindex="-1"` |
| `RuntimeOverview.vue` | **删除**（103 行，无任何 importer） |
| `SessionPanel.vue` | 注释不再指向已删除组件 |

## 为什么这些是真问题（可复核）

- `--text-strong` 未定义 → 该声明在计算值阶段无效 → `color` 回退为**继承值**。`.model-config-summary` 本身是 `--text-muted`，所以其中的 `<strong>` 一直渲染成灰色而非深色。修复后实测计算值为 `rgb(21, 32, 25)`。
- `.app-shell` 把 Tab 行高度锁死 `68px 46px`，而 `.page-tabs button` 无 `white-space` 约束 → 窄屏文案竖排换行并被裁（此前 738px 截图可见「Agent 对/话」「知识/库」）。
- `#main-content` 只挂在 3 个 Tab，且所有 `<main>` 都无 `tabindex` → skip-link 在图谱页/知识库页跳到空目标，且 Chrome 下锚点跳转不带 `tabindex` 不会真正移焦。

## 验证

- `pnpm typecheck`（`vue-tsc -b`）退出码 0；`pnpm build` 成功（1m32s，产物仅 chunk 体积告警，与本次改动无关）。
- 真实浏览器 `127.0.0.1:15173`，视口 **554×748**（in-app 浏览器当前宽度）计算样式断言：
  - `.page-tabs button` `white-space: nowrap`，5 个按钮高度均 **32px**（单行），导航条高 45px；
  - `.tab-label` 计算为 `display: none`（≤640 生效）；临时显示全部文案后实测 5 个 Tab 合计需 **578px** → 738/900/1440 三档均不溢出，`>640px` 档位无需换行；
  - 令牌全部解析：`--z-header=20`、`--z-toast=50`、`--z-popover=30`、`--radius-sm=4px`、`--space-4=14px`、`--shadow-pop=...`；
  - `.model-config-label` 与 `.model-config-summary strong` 计算色 `rgb(21, 32, 25)`，`.model-config-hint` `rgb(107, 123, 113)`；
  - `document.body.scrollHeight (748) === innerHeight (748)` → 无双滚动条；`main#main-content` 存在且 `tabindex="-1"`；
  - console 无 error/warning（仅 vite connected）。
- **未完成的一项视觉确认**：in-app 浏览器处于隐藏状态（`visibilityState=hidden`），`take_screenshot` 报 `NATIVE_BROWSER_VIEWPORT_UNAVAILABLE`，因此本阶段**没有截图级的主观观感确认**，只有上面的计算样式与几何断言。请你在真实浏览器里过一眼顶部 Tab 与焦点框观感。

## 下一步

S2 对话页（三栏宽度与消息行宽、trace 右栏截断、工具参数多行、事件流中文标签与行高令牌、骨架屏基元）。等你复测 S1 通过后再动。
