<script setup lang="ts">
/**
 * 文档博客主容器：一个组件内管理 7 个视图（home / category / search / article / tags / trash / favorites）。
 *
 * 刻意不引 vue-router：整个应用是「Tab 切换」而非多页面路由，博客内部视图再叠一层路由
 * 会让 URL、Tab 状态与滚动位置三处互相耦合；用 view ref + 参数 ref 表达状态更直观。
 *
 * 收藏与阅读位置都存在 localStorage（后端没有收藏接口），因此这里维护一份 id 数组，
 * 文章页只负责把点击事件抛回来。
 */
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconBook,
  IconCategory,
  IconChartBar,
  IconClock,
  IconFileText,
  IconRefresh,
  IconSearch,
  IconStar,
  IconTags,
  IconTrash,
  IconX,
} from '@tabler/icons-vue'
import {blogApi} from '@/api/blog'
import BlogArticle from '@/components/blog/BlogArticle.vue'
import BlogPager from '@/components/blog/BlogPager.vue'
import type {
  BlogCategory,
  BlogDocumentDetail,
  BlogDocumentSummary,
  BlogSearchHit,
  BlogStats,
  BlogSuggestion,
  BlogTag,
  BlogTrashItem,
} from '@/types/blog'

/** 视图枚举：article 需要额外加载详情，因此单独走 openArticle。 */
type BlogView = 'home' | 'category' | 'search' | 'article' | 'tags' | 'trash' | 'favorites'

const FAVORITES_KEY = 'blog_favorites_v1'
const PAGE_SIZE = 20

const view = ref<BlogView>('home')
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

// ---------------------------------------------------------------- 收藏

const favorites = ref<number[]>([])

function loadFavorites() {
  try {
    const raw = window.localStorage.getItem(FAVORITES_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    // localStorage 可能被其它版本或手工改坏，这里只接受数字数组
    favorites.value = Array.isArray(parsed)
        ? parsed.filter((item): item is number => typeof item === 'number' && Number.isFinite(item))
        : []
  } catch {
    favorites.value = []
  }
}

function persistFavorites() {
  try {
    window.localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites.value))
  } catch {
    notice.value = '收藏已更新，但浏览器拒绝写入本地存储（可能是隐私模式）'
  }
}

function toggleFavorite(id: number) {
  favorites.value = favorites.value.includes(id)
      ? favorites.value.filter(item => item !== id)
      : [...favorites.value, id]
  persistFavorites()
}

// ---------------------------------------------------------------- 首页

const stats = ref<BlogStats | null>(null)
const categories = ref<BlogCategory[]>([])
const latest = ref<BlogDocumentSummary[]>([])

async function loadHome() {
  loading.value = true
  error.value = null
  try {
    const [statsResult, categoryResult, latestResult] = await Promise.all([
      blogApi.stats(),
      blogApi.categories(),
      blogApi.documents({page: 1, limit: 10}),
    ])
    stats.value = statsResult
    categories.value = categoryResult.categories
    latest.value = latestResult.documents
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '首页数据加载失败'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- 列表视图（分类 / 标签 / 搜索共用分页骨架）

const listDocuments = ref<BlogDocumentSummary[]>([])
const listPage = ref(1)
const listPages = ref(1)
const listTotal = ref(0)
const listTitle = ref('')
const activeCategory = ref<string | null>(null)
const activeTag = ref<BlogTag | null>(null)

/** 搜索视图单独一份状态：命中的 snippet 需要高亮，与普通列表行结构不同。 */
const searchInput = ref('')
const searchQuery = ref('')
const searchHits = ref<BlogSearchHit[]>([])
const searchPage = ref(1)
const searchPages = ref(1)
const searchTotal = ref(0)

function resetList() {
  listDocuments.value = []
  listPage.value = 1
  listPages.value = 1
  listTotal.value = 0
}

async function openCategory(category: BlogCategory, page = 1) {
  view.value = 'category'
  activeCategory.value = category.slug
  activeTag.value = null
  listTitle.value = `${category.icon} ${category.name}`
  loading.value = true
  error.value = null
  try {
    const result = await blogApi.documents({category: category.slug, page, limit: PAGE_SIZE})
    listDocuments.value = result.documents
    listPage.value = result.page
    listPages.value = result.pages
    listTotal.value = result.total
  } catch (cause) {
    resetList()
    error.value = cause instanceof Error ? cause.message : '分类文档加载失败'
  } finally {
    loading.value = false
  }
}

async function openTag(tag: BlogTag, page = 1) {
  view.value = 'category'
  activeTag.value = tag
  activeCategory.value = null
  listTitle.value = `${tag.name}`
  loading.value = true
  error.value = null
  try {
    const result = await blogApi.tagDocuments({slug: tag.slug, page, limit: PAGE_SIZE})
    listDocuments.value = result.documents
    listPage.value = result.page
    listPages.value = result.pages
    listTotal.value = result.total
  } catch (cause) {
    resetList()
    error.value = cause instanceof Error ? cause.message : '标签文档加载失败'
  } finally {
    loading.value = false
  }
}

/** 分类 / 标签视图翻页：复用当前筛选条件。 */
function changeListPage(page: number) {
  if (activeTag.value) void openTag(activeTag.value, page)
  else {
    const category = categories.value.find(item => item.slug === activeCategory.value)
    if (category) void openCategory(category, page)
  }
}
/**
 * 按 slug 进分类视图：分类列表可能尚未加载（例如从文章面包屑跳回来），
 * 这里先补一次 categories 请求再反查，保证 activeCategory 能被翻页逻辑复用。
 */
async function openCategoryBySlug(slug: string) {
  if (!slug) return
  if (!categories.value.length) {
    try {
      categories.value = (await blogApi.categories()).categories
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '分类加载失败'
      return
    }
  }
  const category = categories.value.find(item => item.slug === slug)
  if (category) void openCategory(category)
}


async function runSearch(page = 1) {
  const keyword = searchInput.value.trim()
  if (!keyword) return
  view.value = 'search'
  searchQuery.value = keyword
  loading.value = true
  error.value = null
  try {
    const result = await blogApi.search({q: keyword, page, limit: PAGE_SIZE})
    searchHits.value = result.documents
    searchPage.value = result.page
    searchPages.value = result.pages
    searchTotal.value = result.total
  } catch (cause) {
    searchHits.value = []
    searchPages.value = 1
    searchTotal.value = 0
    error.value = cause instanceof Error ? cause.message : '检索失败'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- 搜索建议（防抖）

const suggestions = ref<BlogSuggestion[]>([])
const suggestOpen = ref(false)
const suggestIndex = ref(-1)
let suggestTimer = 0
/** 建议请求序号：慢响应回来时若已不是最新请求就丢弃，避免下拉内容错位。 */
let suggestToken = 0

function onSearchInput() {
  window.clearTimeout(suggestTimer)
  const keyword = searchInput.value.trim()
  if (!keyword) {
    suggestions.value = []
    suggestOpen.value = false
    return
  }
  suggestTimer = window.setTimeout(() => void loadSuggestions(keyword), 150)
}

async function loadSuggestions(keyword: string) {
  const token = ++suggestToken
  try {
    const result = await blogApi.suggest(keyword, 8)
    if (token !== suggestToken) return
    suggestions.value = result.suggestions
    suggestOpen.value = result.suggestions.length > 0
    suggestIndex.value = -1
  } catch {
    // 建议失败不打断输入：主搜索仍可正常提交
    if (token !== suggestToken) return
    suggestions.value = []
    suggestOpen.value = false
  }
}

/**
 * ↑↓ 在「未选中(-1) + 每条建议」之间循环：把 -1..size-1 平移到 0..size 再取模，
 * 这样从「未选中」按 ↓ 会落到第一条、按 ↑ 会落到最后一条。
 */
function moveSuggest(step: number) {
  if (!suggestions.value.length) return
  suggestOpen.value = true
  const size = suggestions.value.length
  suggestIndex.value = (suggestIndex.value + 1 + step + size + 1) % (size + 1) - 1
}

/** 回车：选中建议则直接开文章，否则提交全文检索。 */
function submitSearch() {
  if (suggestOpen.value && suggestIndex.value >= 0 && suggestions.value[suggestIndex.value]) {
    void openArticle(suggestions.value[suggestIndex.value].id)
    closeSuggest()
    return
  }
  closeSuggest()
  void runSearch(1)
}

function closeSuggest() {
  suggestOpen.value = false
  suggestIndex.value = -1
}

/** 失焦后延迟关闭：让建议项的 click 先触发，否则点击会先被 blur 打断。 */
function onSearchBlur() {
  window.setTimeout(closeSuggest, 120)
}

onBeforeUnmount(() => window.clearTimeout(suggestTimer))

// ---------------------------------------------------------------- 文章详情

const article = ref<BlogDocumentDetail | null>(null)

async function openArticle(id: number) {
  view.value = 'article'
  loading.value = true
  error.value = null
  article.value = null
  try {
    const result = await blogApi.document(id)
    article.value = result.document
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '文章加载失败'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- 标签云

const tags = ref<BlogTag[]>([])

async function openTags() {
  view.value = 'tags'
  loading.value = true
  error.value = null
  try {
    const result = await blogApi.tags()
    tags.value = result.tags
  } catch (cause) {
    tags.value = []
    error.value = cause instanceof Error ? cause.message : '标签加载失败'
  } finally {
    loading.value = false
  }
}

/** 标签云字号：按 doc_count 在 0.85rem~1.35rem 之间线性缩放（最少的取小值）。 */
const tagFontScale = computed(() => {
  const counts = tags.value.map(tag => tag.doc_count)
  const max = Math.max(1, ...counts)
  return (count: number) => 0.85 + (Math.max(0, count) / max) * 0.5
})

// ---------------------------------------------------------------- 回收站

const trashItems = ref<BlogTrashItem[]>([])
const trashPage = ref(1)
const trashPages = ref(1)
const trashTotal = ref(0)

async function openTrash(page = 1) {
  view.value = 'trash'
  loading.value = true
  error.value = null
  try {
    const result = await blogApi.trash({page, limit: PAGE_SIZE})
    trashItems.value = result.documents
    trashPage.value = result.page
    trashPages.value = result.pages
    trashTotal.value = result.total
  } catch (cause) {
    trashItems.value = []
    trashPages.value = 1
    trashTotal.value = 0
    error.value = cause instanceof Error ? cause.message : '回收站加载失败'
  } finally {
    loading.value = false
  }
}

async function restoreDocument(item: BlogTrashItem) {
  if (!window.confirm(`恢复《${item.title}》到文档列表？`)) return
  try {
    const result = await blogApi.restore(item.id)
    notice.value = result.message || '已恢复'
    await Promise.all([openTrash(trashPage.value), loadHome()])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '恢复失败'
  }
}

async function purgeDocument(item: BlogTrashItem) {
  if (!window.confirm(`彻底删除《${item.title}》？此操作不可恢复。`)) return
  try {
    const result = await blogApi.purge(item.id)
    notice.value = result.message || '已彻底删除'
    favorites.value = favorites.value.filter(id => id !== item.id)
    persistFavorites()
    await Promise.all([openTrash(trashPage.value), loadHome()])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除失败'
  }
}

/** 把文档移入回收站（列表卡片上的删除按钮）。 */
async function removeDocument(item: BlogDocumentSummary) {
  if (!window.confirm(`把《${item.title}》移入回收站？`)) return
  try {
    const result = await blogApi.remove(item.id)
    notice.value = result.message || '已移入回收站'
    await refreshCurrentView()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '移入回收站失败'
  }
}

// ---------------------------------------------------------------- 收藏视图

const favoriteDocuments = ref<BlogDocumentSummary[]>([])

/**
 * 收藏只存 id，展示时按 id 逐篇取详情。
 * N+1 请求在这里可以接受（收藏量小），但必须并发，否则串行等待会让列表明显迟滞。
 */
async function openFavorites() {
  view.value = 'favorites'
  loading.value = true
  error.value = null
  try {
    const results = await Promise.all(favorites.value.map(id =>
        blogApi.document(id).then(result => result.document).catch(() => null)))
    favoriteDocuments.value = results.filter((item): item is BlogDocumentDetail => item !== null)
  } catch (cause) {
    favoriteDocuments.value = []
    error.value = cause instanceof Error ? cause.message : '收藏加载失败'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- 同步与重建

const syncing = ref(false)
const rebuilding = ref(false)

async function syncDocuments() {
  syncing.value = true
  error.value = null
  try {
    const result = await blogApi.sync()
    notice.value = `同步完成：新增 ${result.data.imported}，更新 ${result.data.updated}，跳过 ${result.data.skipped}，移除 ${result.data.deleted}`
    await loadHome()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '同步失败'
  } finally {
    syncing.value = false
  }
}

async function rebuildGraph() {
  rebuilding.value = true
  error.value = null
  try {
    const result = await blogApi.graphRebuild()
    notice.value = `关系图谱已重建，共 ${result.relations_count} 条关系`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '图谱重建失败'
  } finally {
    rebuilding.value = false
  }
}

// ---------------------------------------------------------------- 视图切换

/** 返回首页：清掉筛选条件，避免下次进入分类视图时沿用旧标题。 */
function goHome() {
  view.value = 'home'
  activeCategory.value = null
  activeTag.value = null
  article.value = null
  closeSuggest()
  void loadHome()
}

/** 删除 / 恢复后刷新当前视图，保持用户所处位置。 */
async function refreshCurrentView() {
  if (view.value === 'category') changeListPage(listPage.value)
  else if (view.value === 'search') await runSearch(searchPage.value)
  else if (view.value === 'trash') await openTrash(trashPage.value)
  else if (view.value === 'favorites') await openFavorites()
  else if (view.value === 'tags') await openTags()
  else await loadHome()
}

const breadcrumb = computed(() => {
  if (view.value === 'home') return '文档博客'
  if (view.value === 'article') return article.value?.title ?? '文章'
  if (view.value === 'tags') return '标签云'
  if (view.value === 'trash') return '回收站'
  if (view.value === 'favorites') return '我的收藏'
  if (view.value === 'search') return `搜索：${searchQuery.value}`
  return listTitle.value
})

/** 搜索结果高亮：先转义再套 <mark>，避免文档正文里的 HTML 被当作标记执行。 */
/**
 * 搜索结果高亮：先整体转义 HTML，再对转义后的文本套 <mark>。
 * 关键词同样要按同一规则转义——否则搜索 `&lt;` 这类含特殊字符的词永远匹配不到片段里已转义的文本。
 */
function highlightSnippet(snippet: string): string {
  const escaped = escapeHtml(snippet)
  const keyword = searchQuery.value.trim()
  if (!keyword) return escaped
  const tokens = escapeHtml(keyword).split(/\s+/).filter(Boolean)
      .map(token => token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  if (!tokens.length) return escaped
  return escaped.replace(new RegExp(`(${tokens.join('|')})`, 'gi'), '<mark>$1</mark>')
}

/** 转义 HTML 元字符：v-html 的内容必须先过这一层，避免文档正文里的标签被当标记执行。 */
function escapeHtml(text: string): string {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
}


function formatWords(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)} 万字`
  return `${count} 字`
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return value.length >= 16 ? value.slice(0, 16).replace('T', ' ') : value
}

onMounted(() => {
  loadFavorites()
  void loadHome()
})
</script>

<template>
  <section class="blog-shell" aria-label="文档博客">
    <!-- 工具条：标题 / 面包屑 + 搜索 + 视图入口 -->
    <header class="blog-toolbar">
      <div class="blog-toolbar-title">
        <IconBook :size="18" :stroke-width="1.8" aria-hidden="true"/>
        <h1>{{ breadcrumb }}</h1>
        <span v-if="view === 'category' || view === 'search'" class="blog-toolbar-count">{{ listTotal || searchTotal }} 篇</span>
      </div>

      <div class="blog-search">
        <IconSearch :size="15" class="blog-search-icon" aria-hidden="true"/>
        <input v-model="searchInput" type="search" placeholder="搜索文档标题或正文…"
               aria-label="搜索文档" autocomplete="off"
               @input="onSearchInput" @focus="onSearchInput"
               @blur="onSearchBlur" @keydown.enter.prevent="submitSearch"
               @keydown.down.prevent="moveSuggest(1)" @keydown.up.prevent="moveSuggest(-1)"
               @keydown.esc="closeSuggest"/>
        <ul v-if="suggestOpen" class="blog-suggest" role="listbox">
          <li v-for="(item, index) in suggestions" :key="item.id">
            <button type="button" role="option" :aria-selected="index === suggestIndex"
                    :class="{active: index === suggestIndex}"
                    @mousedown.prevent="openArticle(item.id); closeSuggest()">
              <span class="blog-suggest-icon">{{ item.icon }}</span>
              <span class="blog-suggest-title">{{ item.title }}</span>
              <span class="blog-suggest-category">{{ item.category_name }}</span>
            </button>
          </li>
        </ul>
      </div>

      <nav class="blog-nav" aria-label="博客视图">
        <button type="button" :class="{active: view === 'home'}" @click="goHome">首页</button>
        <button type="button" :class="{active: view === 'tags'}" @click="openTags()">
          <IconTags :size="15" aria-hidden="true"/>标签云
        </button>
        <button type="button" :class="{active: view === 'favorites'}" @click="openFavorites()">
          <IconStar :size="15" aria-hidden="true"/>收藏
          <span v-if="favorites.length" class="blog-badge">{{ favorites.length }}</span>
        </button>
        <button type="button" :class="{active: view === 'trash'}" @click="openTrash(1)">
          <IconTrash :size="15" aria-hidden="true"/>回收站
          <span v-if="stats?.trashed" class="blog-badge">{{ stats.trashed }}</span>
        </button>
        <button type="button" :disabled="syncing" @click="syncDocuments">
          <IconRefresh :size="15" aria-hidden="true"/>{{ syncing ? '同步中…' : '同步' }}
        </button>
        <button type="button" :disabled="rebuilding" @click="rebuildGraph">
          <IconChartBar :size="15" aria-hidden="true"/>{{ rebuilding ? '重建中…' : '重建图谱' }}
        </button>
      </nav>
    </header>

    <p v-if="error" class="blog-error-bar" role="alert">
      <IconAlertTriangle :size="15" aria-hidden="true"/>{{ error }}
      <button type="button" class="blog-error-close" title="关闭提示" @click="error = null">
        <IconX :size="14" aria-hidden="true"/>
      </button>
    </p>
    <p v-if="notice" class="blog-notice-bar">
      {{ notice }}
      <button type="button" class="blog-error-close" title="关闭提示" @click="notice = null">
        <IconX :size="14" aria-hidden="true"/>
      </button>
    </p>

    <div class="blog-content">
      <p v-if="loading" class="blog-loading">加载中…</p>

      <!-- 首页：统计 + 分类网格 + 最新文档 -->
      <template v-else-if="view === 'home'">
        <div class="blog-stats">
          <article class="blog-stat">
            <IconFileText :size="18" aria-hidden="true"/>
            <span>文档总数</span>
            <strong>{{ stats?.total_documents ?? 0 }}</strong>
          </article>
          <article class="blog-stat">
            <IconCategory :size="18" aria-hidden="true"/>
            <span>分类数</span>
            <strong>{{ stats?.total_categories ?? 0 }}</strong>
          </article>
          <article class="blog-stat">
            <IconChartBar :size="18" aria-hidden="true"/>
            <span>总字数</span>
            <strong>{{ formatWords(stats?.total_words ?? 0) }}</strong>
          </article>
        </div>

        <section class="blog-block" aria-label="文档分类">
          <h2>文档分类</h2>
          <ul class="blog-category-grid">
            <li v-for="category in categories" :key="category.slug">
              <button type="button" @click="openCategory(category)">
                <span class="blog-category-icon">{{ category.icon }}</span>
                <span class="blog-category-name">{{ category.name }}</span>
                <span class="blog-category-count">{{ category.doc_count }} 篇</span>
              </button>
            </li>
          </ul>
        </section>

        <section class="blog-block" aria-label="最新文档">
          <h2>最新文档</h2>
          <p v-if="!latest.length" class="blog-muted">暂无文档。</p>
          <ul v-else class="blog-doc-list">
            <li v-for="item in latest" :key="item.id">
              <button type="button" class="blog-doc-card" @click="openArticle(item.id)">
                <span class="blog-doc-icon">{{ item.icon }}</span>
                <span class="blog-doc-main">
                  <span class="blog-doc-title">{{ item.title }}</span>
                  <span class="blog-doc-meta">
                    <span>{{ item.category_name }}</span>
                    <span>{{ formatWords(item.word_count) }}</span>
                    <span><IconClock :size="13" aria-hidden="true"/>{{ item.read_time }} 分钟</span>
                    <span>{{ formatTime(item.updated_at) }}</span>
                  </span>
                </span>
              </button>
            </li>
          </ul>
        </section>
      </template>

      <!-- 分类 / 标签：同一套卡片列表 + 分页 -->
      <template v-else-if="view === 'category'">
        <h2 class="blog-view-title">{{ listTitle }}</h2>
        <p v-if="!listDocuments.length" class="blog-muted">该范围下暂无文档。</p>
        <ul v-else class="blog-doc-list">
          <li v-for="item in listDocuments" :key="item.id">
            <div class="blog-doc-row">
              <button type="button" class="blog-doc-card" @click="openArticle(item.id)">
                <span class="blog-doc-icon">{{ item.icon }}</span>
                <span class="blog-doc-main">
                  <span class="blog-doc-title">{{ item.title }}</span>
                  <span class="blog-doc-meta">
                    <span>{{ item.category_name }}</span>
                    <span>{{ formatWords(item.word_count) }}</span>
                    <span>{{ item.read_time }} 分钟</span>
                    <span>{{ formatTime(item.updated_at) }}</span>
                  </span>
                </span>
              </button>
              <button type="button" class="blog-icon-button" title="移入回收站"
                      @click="removeDocument(item)">
                <IconTrash :size="15" aria-hidden="true"/>
              </button>
            </div>
          </li>
        </ul>
        <BlogPager :page="listPage" :pages="listPages" @change="changeListPage"/>
      </template>

      <!-- 搜索：snippet 命中高亮 -->
      <template v-else-if="view === 'search'">
        <h2 class="blog-view-title">「{{ searchQuery }}」的检索结果 · {{ searchTotal }} 条</h2>
        <p v-if="!searchHits.length" class="blog-muted">没有匹配的文档，换个关键词试试。</p>
        <ul v-else class="blog-doc-list">
          <li v-for="item in searchHits" :key="item.id">
            <button type="button" class="blog-doc-card" @click="openArticle(item.id)">
              <span class="blog-doc-icon">{{ item.icon }}</span>
              <span class="blog-doc-main">
                <span class="blog-doc-title">{{ item.title }}</span>
                <span class="blog-doc-meta">
                  <span>{{ item.category_name }}</span>
                  <span>{{ formatWords(item.word_count) }}</span>
                  <span>{{ item.read_time }} 分钟</span>
                </span>
                <!-- 片段先转义再高亮（见 highlightSnippet），此处仅注入本组件生成的 mark 标签 -->
                <span class="blog-snippet" v-html="highlightSnippet(item.snippet)"/>
              </span>
            </button>
          </li>
        </ul>
        <BlogPager :page="searchPage" :pages="searchPages" @change="page => runSearch(page)"/>
      </template>

      <!-- 文章详情 -->
      <BlogArticle v-else-if="view === 'article' && article"
                   :document="article" :favorite="favorites.includes(article.id)"
                   @back="goHome" @open="openArticle"
                   @category="slug => openCategoryBySlug(slug)"
                   @toggle-favorite="toggleFavorite"/>

      <!-- 标签云 -->
      <template v-else-if="view === 'tags'">
        <h2 class="blog-view-title">标签云</h2>
        <p v-if="!tags.length" class="blog-muted">暂无标签。</p>
        <ul v-else class="blog-tag-cloud">
          <li v-for="tag in tags" :key="tag.slug">
            <button type="button" :style="{fontSize: `${tagFontScale(tag.doc_count)}rem`}"
                    :title="`${tag.doc_count} 篇文档`"
                    @click="openTag(tag)">
              <span class="blog-tag-dot" :style="{background: tag.color}"/>{{ tag.name }}
              <span class="blog-tag-count">{{ tag.doc_count }}</span>
            </button>
          </li>
        </ul>
      </template>

      <!-- 回收站 -->
      <template v-else-if="view === 'trash'">
        <h2 class="blog-view-title">回收站 · {{ trashTotal }} 篇</h2>
        <p v-if="!trashItems.length" class="blog-muted">回收站是空的。</p>
        <ul v-else class="blog-doc-list">
          <li v-for="item in trashItems" :key="item.id">
            <div class="blog-doc-row">
              <div class="blog-doc-card static">
                <span class="blog-doc-icon">{{ item.icon }}</span>
                <span class="blog-doc-main">
                  <span class="blog-doc-title">{{ item.title }}</span>
                  <span class="blog-doc-meta">
                    <span>{{ item.category_name }}</span>
                    <span>删除于 {{ formatTime(item.deleted_at) }}</span>
                  </span>
                </span>
              </div>
              <div class="blog-doc-actions">
                <button type="button" class="blog-icon-button" title="恢复"
                        @click="restoreDocument(item)">
                  <IconArrowBackUp :size="15" aria-hidden="true"/>
                </button>
                <button type="button" class="blog-icon-button danger" title="彻底删除"
                        @click="purgeDocument(item)">
                  <IconTrash :size="15" aria-hidden="true"/>
                </button>
              </div>
            </div>
          </li>
        </ul>
        <BlogPager :page="trashPage" :pages="trashPages" @change="page => openTrash(page)"/>
      </template>

      <!-- 收藏 -->
      <template v-else-if="view === 'favorites'">
        <h2 class="blog-view-title">我的收藏 · {{ favoriteDocuments.length }} 篇</h2>
        <p v-if="!favorites.length" class="blog-muted">还没有收藏文档，在文章页点「收藏」即可加入。</p>
        <p v-else-if="!favoriteDocuments.length" class="blog-muted">收藏的文档已不存在（可能被彻底删除）。</p>
        <ul v-else class="blog-doc-list">
          <li v-for="item in favoriteDocuments" :key="item.id">
            <div class="blog-doc-row">
              <button type="button" class="blog-doc-card" @click="openArticle(item.id)">
                <span class="blog-doc-icon">{{ item.icon }}</span>
                <span class="blog-doc-main">
                  <span class="blog-doc-title">{{ item.title }}</span>
                  <span class="blog-doc-meta">
                    <span>{{ item.category_name }}</span>
                    <span>{{ formatWords(item.word_count) }}</span>
                    <span>{{ item.read_time }} 分钟</span>
                  </span>
                </span>
              </button>
              <button type="button" class="blog-icon-button" title="取消收藏"
                      @click="toggleFavorite(item.id); openFavorites()">
                <IconStar :size="15" aria-hidden="true"/>
              </button>
            </div>
          </li>
        </ul>
      </template>
    </div>
  </section>
</template>
<style scoped>
.blog-shell {
  width: min(100%, 1260px);
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  overflow-y: auto;
}

/* ---------------- 工具条 ---------------- */

.blog-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.blog-toolbar-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--accent);
}

.blog-toolbar-title h1 {
  margin: 0;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  font-size: var(--font-title);
}

.blog-toolbar-count { color: var(--text-muted); font-size: var(--font-caption); }

.blog-search {
  position: relative;
  flex: 1;
  min-width: 220px;
  max-width: 420px;
}

.blog-search input { padding-left: 30px; }
.blog-search-icon { position: absolute; left: var(--space-2); top: 50%; transform: translateY(-50%); color: var(--text-muted); }

.blog-suggest {
  position: absolute;
  z-index: var(--z-popover);
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  margin: 0;
  padding: var(--space-1) 0;
  list-style: none;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-pop);
}

.blog-suggest button {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 0;
  background: transparent;
  color: var(--text);
  text-align: left;
  font-size: var(--font-small);
}

.blog-suggest button:hover,
.blog-suggest button.active { background: var(--accent-soft); color: var(--accent); }
.blog-suggest-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.blog-suggest-category { color: var(--text-muted); font-size: var(--font-caption); }

.blog-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-wrap: wrap;
  margin-left: auto;
}

.blog-nav button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-soft);
  font-size: var(--font-small);
}

.blog-nav button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.blog-nav button.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); font-weight: 600; }

.blog-badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  color: #ffffff;
  font-size: var(--font-micro);
  text-align: center;
}

/* ---------------- 提示条 ---------------- */

.blog-error-bar,
.blog-notice-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-left: 3px solid var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: var(--font-small);
}

.blog-notice-bar { border-left-color: var(--accent); background: var(--accent-soft); color: var(--accent); }

.blog-error-close {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  display: inline-flex;
  align-items: center;
}

/* ---------------- 内容区 ---------------- */

.blog-content { display: flex; flex-direction: column; gap: var(--space-4); }
.blog-loading { margin: 0; padding: var(--space-6) 0; text-align: center; color: var(--text-muted); font-size: var(--font-small); }
.blog-muted { margin: 0; color: var(--text-muted); font-size: var(--font-small); }

.blog-view-title { margin: 0; font-size: var(--font-heading); }

.blog-block { display: flex; flex-direction: column; gap: var(--space-2); }
.blog-block h2 { margin: 0; font-size: var(--font-title); }

/* 统计卡 */
.blog-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-3); }

.blog-stat {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 3px var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow);
  color: var(--text-muted);
}

.blog-stat svg { color: var(--accent); }
.blog-stat span { font-size: var(--font-caption); }
.blog-stat strong { grid-column: 1 / -1; font-size: var(--font-metric); color: var(--text); }

/* 分类网格 */
.blog-category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.blog-category-grid button {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 2px var(--space-2);
  width: 100%;
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  color: var(--text);
  text-align: left;
}

.blog-category-grid button:hover { border-color: var(--accent); background: var(--accent-soft); }
.blog-category-icon { grid-row: span 2; font-size: 20px; }
.blog-category-name { font-size: var(--font-small); font-weight: 600; }
.blog-category-count { color: var(--text-muted); font-size: var(--font-caption); }

/* 文档列表 */
.blog-doc-list { display: grid; gap: var(--space-2); margin: 0; padding: 0; list-style: none; }
.blog-doc-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--space-2); }
.blog-doc-actions { display: flex; gap: var(--space-1); }

.blog-doc-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  text-align: left;
}

.blog-doc-card:not(.static):hover { border-color: var(--accent); box-shadow: var(--shadow); }
.blog-doc-card.static { cursor: default; }
.blog-doc-icon { font-size: 17px; line-height: 1.5; }
.blog-doc-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.blog-doc-title { font-size: var(--font-body); font-weight: 600; overflow-wrap: anywhere; }

.blog-doc-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.blog-doc-meta span { display: inline-flex; align-items: center; gap: 3px; }

.blog-snippet { color: var(--text-soft); font-size: var(--font-small); line-height: 1.6; overflow-wrap: anywhere; }
/* 命中词高亮：颜色取强调色，和主题一致 */
.blog-snippet :deep(mark) { background: var(--warning-soft); color: var(--warning); font-weight: 600; padding: 0 2px; }

.blog-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-muted);
}

.blog-icon-button:hover { border-color: var(--accent); color: var(--accent); }
.blog-icon-button.danger:hover { border-color: var(--danger); color: var(--danger); background: var(--danger-soft); }

/* 标签云 */
.blog-tag-cloud {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.blog-tag-cloud button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  color: var(--text-soft);
  line-height: 1.5;
}

.blog-tag-cloud button:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }
.blog-tag-dot { width: 8px; height: 8px; border-radius: 50%; }
.blog-tag-count { color: var(--text-muted); font-size: var(--font-micro); }

/* ---------------- 断点 ---------------- */

@media (max-width: 1260px) {
  .blog-shell { padding: var(--space-4); }
}

@media (max-width: 900px) {
  .blog-toolbar { align-items: stretch; }
  .blog-search { max-width: none; }
  .blog-nav { margin-left: 0; }
  .blog-stats { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .blog-shell { padding: var(--space-3); }
  .blog-toolbar-title h1 { max-width: 200px; }
  .blog-doc-card { padding: var(--space-2) var(--space-3); }
  .blog-doc-row { grid-template-columns: minmax(0, 1fr); }
  .blog-doc-actions { justify-content: flex-end; }
}
</style>
