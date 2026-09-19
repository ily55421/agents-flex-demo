这篇文章是 Tw93 关于 **GEO (Generative Engine Optimization，生成式引擎优化)** 的实战指南。核心观点是：**不要为了刷排名而制造垃圾内容，而是通过结构化你的现有内容，让 AI 更准确、更容易地理解和引用你。**

以下是文章核心内容总结：

### 1. 核心理念：AI 搜索 vs 传统 SEO
*   **逻辑不同**：传统 SEO 拼的是进入 Google 前 10；AI Overview 83% 的引用来自排名前 10 **之外** 的页面。AI 看重的是**结构清晰**和**来源可靠**，而非 PageRank。
*   **战略定位**：AI 搜索目前占总引荐流量不到 1%，更像是一种**品牌可见性策略**，而非流量策略。值得花 1 小时优化，但不值得花一周，产品本身才是核心。
*   **增长潜力**：AI 搜索增长极快（2025 年上半年同比涨 527%），且引荐转化率是传统搜索的 5 倍。

### 2. 基础配置：robots.txt 与 llms.txt
#### A. 精细化配置 robots.txt
不要一刀切，需区分爬虫类型：
*   **允许**：搜索/检索爬虫（`OAI-SearchBot`, `Claude-SearchBot`, `PerplexityBot`）和用户触发爬虫（`ChatGPT-User`）。
*   **屏蔽**：训练爬虫（`GPTBot`, `CCBot`）和未声明身份的爬虫（`Bytespider`）。
*   **退出标识**：屏蔽 `Google-Extended` 以声明退出 AI 训练。

#### B. 部署 llms.txt (新标准)
*   **作用**：类似 robots.txt，但是给 AI 看的“站点说明书”。包含站点简介、关键链接、作者信息。
*   **优势**：目前采用率仅 10%，有先发优势。Anthropic、Vercel 等大厂已部署。
*   **网状引用**：在多站点间互相引用 `llms.txt`，形成发现网络，让 AI 能顺藤摸瓜找到所有内容。

### 3. 内容结构化：Markdown 与完整版
*   **llms-full.txt**：提供 30-60KB 的完整版 Markdown 文件，包含 FAQ、场景、竞品对比等。AI 在读取概要后往往需要更多细节。
*   **Markdown 路由**：为每个 HTML 页面提供 `.md` 版本（如 `/page.md`），并在 `<head>` 中声明 `<link rel="alternate" type="text/markdown" href="/page.md" />`。
    *   **优势**：Token 消耗减少 80%，解析更高效。Claude Code 等工具已支持自动请求 Markdown 格式。

### 4. 确保被索引：搜索引擎注册
AI 搜索依赖传统搜索引擎的索引数据：
*   **Bing Webmaster Tools**：必做。Copilot、DuckDuckGo、Yahoo AI 均依赖 Bing。开启 **IndexNow** 协议，实现新内容分钟级收录。
*   **Google Search Console**：提交 Sitemap，监控索引状态。Google AI Overview 会从更广的范围拉取内容。
*   **Perplexity Publisher Program**：提交站点，可获得引用分析和收入分成。

### 5. 高级实践：构建 AI 专属知识入口 (Yobi)
Tw93 构建了一个名为 **Yobi** 的集中式知识网页，作为 AI 的“单一事实来源”：
*   **三层内容**：概览 (`llms.txt`) + 完整版 (`llms-full.txt`) + 独立项目页。
*   **JSON API**：提供 `/api/projects.json` 等结构化数据接口，方便 AI 程序化获取实时数据（如 GitHub Star 数）。
*   **叙事结构**：不仅列出项目，更描述项目间的关系、技术方向和整体定位，帮助 AI 回答“这个人/团队是谁”这类问题。
*   **域名统一**：将子域名的关键数据镜像到主域名，避免 AI 爬虫遗漏。

### 6. 数据驱动的优化建议
基于 Princeton/KDD 2024 论文及 geo-citation-lab 的研究：
*   **具体性 > 泛泛而谈**：包含真实数据、清晰定义、横向对比的内容，影响力高出 50%。
*   **长度适中**：被高频引用的页面平均 2000 词、10+ 标题。**纯 FAQ 格式反而有害**（信息密度低）。
*   **语义相关性**：页面标题/URL 与用户查询的语义相似度是最大预测因子。使用自然语言 URL slug（如 `/projects/pake`）优于 ID（如 `/page?id=47`）。
*   **平台差异**：
    *   **ChatGPT**：引用少但深，单条引用影响力大。策略：把单页写深写透。
    *   **Perplexity**：广撒网，引用量大。策略：覆盖面广。
*   **第三方引用**：品牌被 Reddit/HN 等第三方引用的概率是自引用的 6.5 倍。`llms.txt` 的作用是当第三方提到你时，给 AI 提供一个准确的引用锚点。

### 7. 避坑指南：这些没用
*   **非官方 Meta 标签**：如 `<meta name="ai-content-url">`，无主流 AI 支持。
*   **HTML 注释藏提示**：解析器会在 AI 读取前剥离注释。
*   **User-Agent 嗅探返回不同内容**：属于 Cloaking（伪装），会被 Google 惩罚。
*   **过度依赖 JSON-LD**：除 Bing 外，大多数 LLM 将其视为普通文本，不理解其结构化语义。不要只把数据放在 JSON-LD 里而不显示在页面上。
*   **盲目追求 SEO 分数**：不要为了刷分加注水内容。判断标准：新增内容是否提供了独特信息？

### 总结
GEO 的本质是**给 AI 一个干净的工作环境**。通过 `robots.txt` 放行、`llms.txt` 引导、Markdown 提效、搜索引擎保底，以及构建集中的知识入口，你可以低成本地提升品牌在 AI 时代的可见性。