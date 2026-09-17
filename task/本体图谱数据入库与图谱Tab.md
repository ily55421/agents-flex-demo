# 本体图谱数据入库与「图谱」Tab

- 日期：2026-09-12
- 类型：功能开发（本体图谱数据整合 + 图数据库入库 + 可视化 Tab）
- 资料来源：`D:\workspace\qiz\ylj\cad-to-svg-converter\cad-to-svg-converter\svg`
- 参考页面：`topology_data/本体关系图谱.html`（画布力导向图谱）、`topology_data/电力拓扑知识库.html`（站点档案仪表板）

## 一、需求与决策

1. 把 CAD 拓扑解析工程产出的**本体图谱数据**整合进本项目，并做**数据入库**
2. 新增一个**图谱 Tab**，参考两个既有 HTML 页面做页面渲染集成

与上一阶段「知识库文本解析图谱」的关系：此前 `/api/graph/*` 是从知识库 MD 文档里的
“母线 → 隔离开关 → 断路器”路径链**文本解析**出来的粗粒度图（405 元素/415 连接）。
本次拿到了解析工程的**结构化图谱产出**（693 实体/3598 关系/23 类 TBox），信息量
（类别体系、别名、属性、中文谓词）远超文本解析，故**整体替换**为资源直载方案，
文本解析导入逻辑删除，Neo4j 基础设施（Neo4jConfig/Neo4jProperties）与
`query_topology` Agent 工具保留复用。

## 二、数据整合（资源固化）

`task/bundle_graph_resources.py` 从源工程拷贝并固化到 `src/main/resources/graph/`：

| 资源 | 内容 | 规模 |
| --- | --- | --- |
| `power-topology-graph.json` | 本体图谱（meta 谓词中文词典 / tbox 类层级 / nodes / edges） | 693 实体 · 3598 关系 · 23 类 |
| `power-topology-kb.json` | 站点档案（提取自《电力拓扑知识库》页内嵌 DATA） | 9 站（母线/主变/间隔/设备/直连/质量明细） |
| `power-topology-facts.jsonl` | 知识问答事实 | 1215 条（station/category/text） |

节点类别 16 种（变电站/母线/主变/断路器/隔离开关/接地刀闸/互感器/电容器/避雷器/
出线间隔等），谓词 15 种（has_bus 下辖母线、connects_bus 挂接母线、
directly_connected_to 直接相连等，资源内带中文对照）。

## 三、数据入库（双库）

### Neo4j（实例图 + 版本标记）
- `TopologyGraphService` 重写：启动后台线程比对 `(:GraphMeta {sourceHash})` 与
  资源 SHA-256，不一致（首次/旧文本图/资源更新）则 **整库重建**——693 节点
  （`:Entity:<类别>` 双标签 + uri/label/station/voltage/aliases/attrsJson）+
  3598 关系（**关系类型即英文谓词**，属性带 pred/pred_cn 中文）+ GraphMeta 标记
- 重建前 `MATCH (n) DETACH DELETE n` 清空旧图（含旧文本解析图），幂等可重复
- `ensureSchema` 建 `Entity.uri` 索引；REST 可 `POST /api/graph/import` 手动重建

### DuckDB（站点档案 + 事实，`GraphArchive`）
- `graph_station_doc`：9 站档案（station_name 主键、summary_json、doc_json 文档列）
- `graph_fact`：1215 条事实行（seq/station/category/text，站与类别建索引）
- `graph_meta.fingerprint` 指纹幂等刷新；支持站/类别/关键词 LIKE 组合检索

## 四、REST 接口（`/api/graph/*`）

| 接口 | 说明 |
| --- | --- |
| `GET /summary` | 实体/关系/本体类计数、按类别与谓词分布、入库指纹一致性 |
| `GET /view` | 画布全量视图（predCn 词典 + TBox + 友好字段节点 + 关系，约 820KB） |
| `GET /stations` | 站清单（DuckDB 概要 + 图谱实体计数合并） |
| `GET /station/{name}` | 单站完整档案（母线/主变/间隔/设备/直连/质量） |
| `GET /station/{name}/subgraph` | 单站子图（站内实体 + 相连电压等级/线路） |
| `GET /facts?station=&category=&q=&limit=&offset=` | 事实检索（SQL LIKE + 分页 + 类别清单） |
| `GET /node?id=` | 节点详情（属性/别名 + 出入边按中文谓词分组） |
| `POST /import` | 手动重建入库（指纹比对幂等） |
| `POST /query` | 只读 Cypher（写关键字拦截，实测 `has_bus` 关系可查） |

Agent 工具 `query_topology` 保持签名接入新模型：命中标签/别名的实体 +
中文谓词邻接文本（如「531断路器 ← 挂接母线 10kV母线」）。

## 五、前端「图谱」Tab（`?view=graph`）

五 Tab 架构：Agent 对话 / Agent 维护 / 模型配置 / 知识库 / **图谱**。图谱页为
自包含暗色岛（沿用参考页视觉方案），三种子视图：

1. **本体图谱**（`GraphExplorer.vue` + `graph/forceLayout.ts`）：Canvas 力导向布局
   （斥力+弹簧+向心，alpha 衰减），693 节点全图默认铺开；滚轮以指针为中心缩放、
   拖拽平移、节点拖拽固定；类别图例 16 项带计数点击过滤；站点下拉过滤（补齐相连
   电压等级/线路，与后端子图同口径）；检索命中列表（标签+别名，回车选首项）；
   选中节点高亮邻接 + 右侧详情面板（/node 接口：属性、别名、出入边按中文谓词
   分组，点击邻居跳转居中）
2. **本体 TBox**：23 类层次树布局（按 parent 深度分行，`ForceLayout.treeLayout`），
   谓词标签默认隐藏、悬停/选中节点时显示关联边标签，避免叠压；侧栏显示实例计数
3. **站点档案**（`graph/StationBoard.vue`）：左侧 9 站列表（电压/母线/主变/实体数
   胶囊），右侧概览统计卡 + 设备计数卡 + 母线（挂接间隔分类统计）+ 主变（电压侧）+
   间隔（连接链）+ 直连关系 + 数据质量，底部知识问答事实区（关键词/类别过滤 +
   分页加载更多）；切换站点自动回滚顶部

## 六、验证（全部通过）

- `mvn test` **91 全绿**（新增 `PowerTopologyModelTest` 5 项：锁资源规模 693/3598/23/9/1215、
  关系端点完整性、检索、子图、谓词/类中文翻译）
- `pnpm build`（vue-tsc 类型检查）通过
- 入库实测：Neo4j `hashMatch: true`、DuckDB 9 档案/1215 事实；只读 Cypher
  `MATCH (s:Station)-[:has_bus]->(b:Bus)` 返回 9 站母线
- E2E `task/e2e_graph_tab.py` 9 项全过：五 Tab / 画布 693·3598 / 图例 16 类开关 /
  站点过滤藏木变 / 检索主变→详情面板邻接 11 条 / TBox 树 / 站点档案 9 站+事实检索
  / URL 恢复 / 零控制台错误
- 视觉验收（judge）三轮：实例图谱画布、节点详情面板、TBox 层次树、站点档案顶部、
  事实区全部 pass（修复过程：/view 节点字段友好化；TBox 力导向→层次树；谓词标签
  改悬停显示；站点切换回滚顶部）

## 七、注意

- 站名 `35kVrkz土布加变电站` 为源数据原文（rkz=日喀则拼音缩写），非乱码
- 图谱资源更新后：重跑 `task/bundle_graph_resources.py` 或手动替换 resources/graph/，
  重启即自动重建 Neo4j/DuckDB（指纹比对）；或调 `POST /api/graph/import`
- `/view` 约 820KB，前端一次加载后本地过滤渲染，不做增量拉取
- 后台入库在启动线程池异步执行（约 10s），期间 `/summary` 的 hashMatch 短暂为 false

## 八、亮色主题重构（2026-09-12 追加）

按全站 design tokens（白面板 / #f4f7f5 浅灰绿底 / #087858 绿强调 / 浅色圆角卡片）
重构图谱三个视图，替换原暗色岛风格：

- **画布渲染**：背景 `#f7faf8`；边线亮色下用主题绿（邻接 `rgba(8,120,88,.42)` /
  弱化灰 `rgba(122,140,130,.32)`）；节点保留分类彩色并加细描边（浅底轮廓感）；
  标签深字浅底衬；选中环改绿
- **GraphExplorer**：顶栏/分段切换（激活绿底白字）/搜索框/站点下拉/命中列表/
  图例（彩色圆点 + 绿计数）/详情面板/悬浮提示/操作提示 全部改用 var(--*) tokens
- **StationBoard**：站列表（选中浅绿底绿边）、概览统计卡（绿色大数字）、
  行卡片、类别胶囊（激活绿底白字）、事实行全部亮色化
- E2E `e2e_graph_tab.py` 9 项通过；judge 视觉验收 5 张截图全部 pass（无残留深色块、
  对比度与布局完好）
