/**
 * 文档博客 REST 客户端（后端前缀 /api/blog）。
 *
 * 与 agent.ts 保持同一套请求风格：非 2xx 优先采用后端结构化 message 抛 Error，
 * 让界面能展示「文档不存在」「id 非法」等真实原因，而不是笼统的失败提示。
 */
import type {
  BlogActionResult,
  BlogCategory,
  BlogDocumentDetail,
  BlogDocumentSummary,
  BlogGraph,
  BlogGraphRebuildResult,
  BlogPage,
  BlogSearchPage,
  BlogStats,
  BlogSuggestion,
  BlogSyncResult,
  BlogTag,
  BlogTrashItem,
} from '@/types/blog'

const BLOG_API_ROOT = '/api/blog'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BLOG_API_ROOT}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message || `请求失败 (${response.status})`)
  }
  return response.json() as Promise<T>
}

/**
 * 构造查询串：空值（undefined / null / 空串）一律跳过。
 * 后端对 category、slug 这类可选参数用 isBlank 判定，多传空串虽不报错但会让缓存与日志失真。
 */
function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const blogApi = {
  /** 全部分类及在用文档数，后端按 sort_order 排序。 */
  categories: () => request<{ categories: BlogCategory[] }>('/categories'),
  /** 文档列表；category 为空时省略该参数表示全部分类。 */
  documents: (params: { category?: string; page?: number; limit?: number } = {}) =>
    request<BlogPage<BlogDocumentSummary>>('/documents' + query({
      category: params.category,
      page: params.page ?? 1,
      limit: params.limit ?? 20,
    })),
  /** 文档详情，含原始 Markdown 全文。 */
  document: (id: number) =>
    request<{ document: BlogDocumentDetail }>(`/document${query({ id })}`),
  /** 全文检索；category 可选，用于在分类内搜索。 */
  search: (params: { q: string; page?: number; limit?: number; category?: string }) =>
    request<BlogSearchPage>('/search' + query({
      q: params.q,
      page: params.page ?? 1,
      limit: params.limit ?? 20,
      category: params.category,
    })),
  /** 输入框下拉建议（后端默认 8 条，上限 20）。 */
  suggest: (q: string, limit = 8) =>
    request<{ suggestions: BlogSuggestion[] }>('/search-suggest' + query({ q, limit })),
  /** 相关文档推荐，后端默认 5 条。 */
  related: (id: number, limit = 5) =>
    request<{ documents: BlogDocumentSummary[] }>(`/related${query({ id, limit })}`),
  /** 站点统计：文档数 / 分类数 / 总字数 / 回收站数。 */
  stats: () => request<BlogStats>('/stats'),
  /** 全部标签及文档数。 */
  tags: () => request<{ tags: BlogTag[] }>('/tags'),
  /** 标签下的文档列表。 */
  tagDocuments: (params: { slug?: string; page?: number; limit?: number } = {}) =>
    request<BlogPage<BlogDocumentSummary>>('/tag' + query({
      slug: params.slug,
      page: params.page ?? 1,
      limit: params.limit ?? 20,
    })),
  /** 回收站列表，条目额外带 deleted_at。 */
  trash: (params: { page?: number; limit?: number } = {}) =>
    request<BlogPage<BlogTrashItem>>('/trash' + query({
      page: params.page ?? 1,
      limit: params.limit ?? 20,
    })),
  /** 文章关系图谱：中心文档 + 一跳邻居。 */
  graph: (id: number, depth = 1) =>
    request<BlogGraph>(`/graph${query({ id, depth })}`),
  /** 移入回收站（可恢复）。 */
  remove: (id: number) =>
    request<BlogActionResult>('/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  /** 从回收站恢复。 */
  restore: (id: number) =>
    request<BlogActionResult>('/restore', { method: 'POST', body: JSON.stringify({ id }) }),
  /** 彻底删除：不可恢复。 */
  purge: (id: number) =>
    request<BlogActionResult>('/purge', { method: 'POST', body: JSON.stringify({ id }) }),
  /** 增量同步源文档目录，返回导入 / 更新 / 跳过 / 删除计数。 */
  sync: () => request<{ status: string; data: BlogSyncResult }>('/sync', { method: 'POST' }),
  /** 重建文档关系图谱，返回生成的关系条数。 */
  graphRebuild: () =>
    request<BlogGraphRebuildResult>('/graph-rebuild', { method: 'POST' }),
}
