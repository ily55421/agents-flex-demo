/**
 * 文档博客（/api/blog）的数据契约。
 *
 * 后端直接返回裸 JSON（无 status/data 包装），字段名与 SQLite 查询列名一一对应，
 * 因此这里刻意保留 snake_case：前端不再做一层命名映射，接口字段变化时只需改这一处。
 */

/** 分类（GET /api/blog/categories）。icon 是后端给出的 emoji，前端原样展示。 */
export interface BlogCategory {
  id: number
  slug: string
  name: string
  icon: string
  /** 该分类下未删除的文档数。 */
  doc_count: number
}

/**
 * 列表行：分类列表、最新文档、相关推荐、收藏等场景共用同一结构。
 * word_count 是字符数，read_time 是后端按每分钟 300 字估算好的分钟数（前端不再重复计算）。
 */
export interface BlogDocumentSummary {
  id: number
  title: string
  filename: string
  word_count: number
  created_at: string
  updated_at: string
  category_name: string
  category_slug: string
  icon: string
  read_time: number
}

/** 检索命中行：在摘要基础上附带命中位置附近的纯文本片段（可能含 "..."）。 */
export interface BlogSearchHit extends BlogDocumentSummary {
  snippet: string
}

/** 回收站行：比列表行多一个删除时间，用于展示「何时被删」。 */
export interface BlogTrashItem extends BlogDocumentSummary {
  deleted_at: string | null
}

/** 文档详情（GET /api/blog/document）：列表行 + 定位信息与原始 Markdown 全文。 */
export interface BlogDocumentDetail extends BlogDocumentSummary {
  category_id: number
  filepath: string
  /** 原始 Markdown 全文，由前端负责渲染。 */
  content: string
  deleted_at: string | null
}

/** 分页信封：后端所有列表接口统一返回这五个字段。 */
export interface BlogPage<T> {
  documents: T[]
  total: number
  page: number
  limit: number
  /** 后端已按 total/limit 向上取整，且最小为 1（空结果也有 1 页）。 */
  pages: number
}

/** 检索结果分页：额外回显清洗后的查询串，便于高亮时对齐后端实际使用的关键词。 */
export interface BlogSearchPage extends BlogPage<BlogSearchHit> {
  query: string
}

/** 站点统计（GET /api/blog/stats）。 */
export interface BlogStats {
  total_documents: number
  total_categories: number
  /** 全部在用文档的字符数之和。 */
  total_words: number
  /** 回收站中的文档数。 */
  trashed: number
}

/** 标签（GET /api/blog/tags）；color 是后端存储的十六进制色值。 */
export interface BlogTag {
  id: number
  name: string
  slug: string
  color: string
  doc_count: number
}

/** 搜索建议项（GET /api/blog/search-suggest）；title_match 为 0 表示标题命中。 */
export interface BlogSuggestion {
  id: number
  title: string
  category_name: string
  category_slug: string
  icon: string
  title_match: number
}

/** 图谱中心文档：比普通节点多一个分类显示名。 */
export interface BlogGraphCenter {
  id: number
  title: string
  category_name: string
  category_slug: string
  icon: string
}

/** 图谱节点：中心文档与一跳邻居共用，邻居没有分类显示名。 */
export interface BlogGraphNode {
  id: number
  title: string
  icon: string | null
  category_slug: string
}

/** 图谱关系：type 取 same_category（同分类）或 similar（标题关键词重叠）。 */
export interface BlogGraphEdge {
  source: number
  target: number
  type: string
  weight: number
}

/** 文章关系图谱（GET /api/blog/graph）。 */
export interface BlogGraph {
  center: BlogGraphCenter
  nodes: BlogGraphNode[]
  edges: BlogGraphEdge[]
  /** 后端回显的跳数；当前实现固定一跳，保留字段以兼容后续扩展。 */
  depth?: number
}

/** 管理操作的统一返回（delete / restore / purge）。 */
export interface BlogActionResult {
  status: string
  message: string
}

/** 源目录增量同步计数（POST /api/blog/sync 的 data 字段）。 */
export interface BlogSyncResult {
  imported: number
  updated: number
  skipped: number
  deleted: number
}

/** 图谱重建结果（POST /api/blog/graph-rebuild）。 */
export interface BlogGraphRebuildResult {
  status: string
  relations_count: number
}
