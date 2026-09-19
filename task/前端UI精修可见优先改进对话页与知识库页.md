# 前端 UI 精修：可见优先的对话页与知识库页改进

日期：2026-09-19　提交：`91dbabf`（承接 `271520f` S1）

## 触发原因

用户反馈「UI 感觉没什么变化，不知道你在改什么」。核对后确认该反馈成立：S1 的产物是令牌、a11y、死代码与窄屏（≤640px）修复，在用户约 2000px 宽屏上**没有可感知视觉差异**。阶段顺序判断失误，本提交起改为「可见优先」，把 S2/S3 中视觉收益最大的部分提前。

## 本提交改了什么（3 文件，+52 / −19）

| 位置 | 改动 | 宽屏可见效果 |
| --- | --- | --- |
| `style.css` `.dashboard-shell` | 上限 `1880px → 1680px`；三栏 `324px / minmax(560px,1fr) / 420px` → `minmax(280px,324px) / minmax(0,1fr) / minmax(360px,420px)` | 2000px 视口下中栏由约 **1080px 收到约 880px**，正文不再摊在巨大空白里 |
| `ChatWorkspace.vue` 空状态 | h2 下新增 `.chat-empty-steps` 三步引导（选 Agent / 确认模型 / 提问看事件流） | 未创建 Agent 时中栏由「头像 + 一行灰字悬空」变为有结构的引导块 |
| `style.css` `.session-list` | `max-height: 236px → min(46vh, 430px)` | 28 条历史会话的可见数从约 5 条提升到约 8–10 条 |
| `style.css` | 对话页 4 处写死的 `820px` 统一为 `--chat-measure` 令牌（取值不变） | 无视觉差异，为后续统一改阅读列宽留单一开关 |
| `KnowledgePanel.vue` `.knowledge-error-line` | 裸橙色文本 → 告警容器（`--warning-soft` 底色 + 3px 左色条 + padding + 可折行） | 知识库页顶部「最近错误」由一行小橙字变为醒目告警块（实测高 72px） |
| `KnowledgePanel.vue` `.knowledge-add > summary` | 折叠块标题做成可点击条（底色 + padding + 字重 + hover 变绿） | 「添加知识 / 跨环境同步 / 向量模型配置 / FAQ」四处不再只是裸 ▶ 文字 |
| `KnowledgePanel.vue` 顶部区 | mode 徽章、状态行、`.danger-text`、分隔线由 hex 回迁令牌（`--accent`/`--radius-pill`/`--text-soft`/`--border`） | 色彩收敛到统一调色板，视觉接近等价 |
| `style.css` 900px 断点 | 新增 `.chat-empty-steps { grid-template-columns: 1fr }` | 窄屏三步条堆叠不压扁 |

## 验证

- `pnpm typecheck`（`vue-tsc -b`）退出 0；`pnpm build` 成功（52.86s）。
- 真实浏览器 `127.0.0.1:15173` 计算样式断言（视口 554×748）：
  - `.chat-empty-steps` 存在、3 个 `li`、高 215px、窄屏为单列，文本为「1 | 选择或创建 Agent | 左侧选已有 Agent，或到「Agent 维护」页创建」；
  - `.session-list` `max-height: 344.08px`（原 236px），实测高 344px，列表内 28 条；
  - `.knowledge-error-line` 计算值 `background rgb(255,241,219)`、`border-left 2.4px solid rgb(152,91,11)`、`padding 8px 10px`、高 72px；
  - `.knowledge-add > summary` `background rgb(243,246,244)`、`padding 7px 9px`、`font-weight 600`；`.knowledge-mode` `border-radius 999px`、`color rgb(8,120,88)`。
- **限制**：in-app 浏览器处于隐藏态（`visibilityState=hidden`），`take_screenshot` 持续报 `NATIVE_BROWSER_VIEWPORT_UNAVAILABLE`，因此本阶段仍**没有截图级观感确认**，以上为计算样式与几何测量；宽屏 1680px 上限的效果是按栅格算术推得（1680−324−420−2×14 gap−2×14 padding≈880），未在真实 2000px 视口截图核对。

## S2 / S3 剩余项

S2 未做：`.trace-row` 在 420px 右栏的横向截断、工具参数 `JSON.stringify` 多行化、事件流英文标题改中文、`.skeleton` 骨架屏基元。
S3 未做：KnowledgePanel 其余约 50 处 hex 回迁、检索行/建库行 grid 化与 900/640 断点、chip/modal 收敛为全局基元、FAQ `v-if="selectedKb !== 'all'"` 常显微调。
