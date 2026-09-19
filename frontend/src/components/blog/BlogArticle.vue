<script setup lang="ts">
/**
 * 文章详情：Markdown 正文渲染 + 代码高亮 + Mermaid 图 + 目录 + 阅读位置记忆 + 相关推荐。
 *
 * 正文用 markdown-it 渲染为 HTML 字符串后交给 v-html（html:false 已在解析层禁用内嵌 HTML，
 * 代码块统一走 prism 转义输出，不存在原始 HTML 注入路径）。
 * v-html 无法直接操作 DOM，因此 mermaid 围栏、标题 id、目录都在渲染完成后的下一帧里
 * 用真实 DOM 查询补齐——与 MarkdownView 的做法保持一致。
 */
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import MarkdownIt from 'markdown-it'
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconBook,
  IconClock,
  IconFileText,
  IconStar,
  IconStarFilled,
  IconTypography,
} from '@tabler/icons-vue'
import {blogApi} from '@/api/blog'
import {renderMermaid} from '@/components/mermaidRenderer'
import BlogGraph from '@/components/blog/BlogGraph.vue'
import type {BlogDocumentDetail, BlogDocumentSummary} from '@/types/blog'
import './prism-tomorrow.css'
// @ts-expect-error prismjs 1.30 未随包发布类型声明（不新增依赖），此处显式抑制 TS7016。
import PrismRaw from 'prismjs'
// 语言包按 prism 依赖顺序引入：typescript 依赖 javascript，scala 依赖 java，markdown 依赖 markup。
import 'prismjs/components/prism-markup'
import 'prismjs/components/prism-css'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-sql'
import 'prismjs/components/prism-json'
import 'prismjs/components/prism-yaml'
import 'prismjs/components/prism-nginx'
import 'prismjs/components/prism-markdown'
import 'prismjs/components/prism-properties'
import 'prismjs/components/prism-docker'
import 'prismjs/components/prism-groovy'
import 'prismjs/components/prism-ini'
import 'prismjs/components/prism-toml'
import 'prismjs/components/prism-go'
import 'prismjs/components/prism-kotlin'
import 'prismjs/components/prism-rust'
import 'prismjs/components/prism-scala'

/** prism 实例的最小接口：避免为无类型声明的包引入 any。 */
interface PrismLike {
  languages: Record<string, unknown>
  highlight: (code: string, grammar: unknown, language: string) => string
}

const prism = PrismRaw as unknown as PrismLike

const props = defineProps<{
  /** 当前文章详情（父组件负责加载，切换文章时整体替换）。 */
  document: BlogDocumentDetail
  /** 是否已收藏；状态由 BlogPanel 统一维护在 localStorage。 */
  favorite: boolean
}>()

const emit = defineEmits<{
  back: []
  open: [id: number]
  category: [slug: string]
  'toggle-favorite': [id: number]
}>()

// ---------------------------------------------------------------- 代码高亮

/** 围栏语言别名 → prism 语言名；未识别时回退转义，保证内容照原样可见。 */
const LANG_ALIASES: Record<string, string> = {
  js: 'javascript',
  jsx: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  py: 'python',
  sh: 'bash',
  shell: 'bash',
  console: 'bash',
  yml: 'yaml',
  html: 'markup',
  xml: 'markup',
  svg: 'markup',
  md: 'markdown',
  kt: 'kotlin',
  dockerfile: 'docker',
  conf: 'nginx',
  cfg: 'ini',
  props: 'properties',
  text: '',
  txt: '',
}

function escapeHtml(text: string): string {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
}

/**
 * markdown-it 的 highlight 钩子：返回转义后的 HTML。
 * mermaid 围栏返回空串，交给 markdown-it 输出转义源码（<pre><code class="language-mermaid">），
 * 随后由 renderMermaidBlocks 替换为 SVG —— 这样渲染失败时仍有源码兜底。
 */
function highlightCode(code: string, lang: string): string {
  if (lang === 'mermaid') return ''
  const name = LANG_ALIASES[lang] ?? lang
  if (!name) return escapeHtml(code)
  const grammar = prism.languages[name]
  if (!grammar) return escapeHtml(code)
  try {
    return prism.highlight(code, grammar, name)
  } catch {
    // 语法包内部异常不该让整篇文章白屏
    return escapeHtml(code)
  }
}

const md = new MarkdownIt({html: false, linkify: true, breaks: false, highlight: highlightCode})

// ---------------------------------------------------------------- 正文与目录

interface TocItem {
  id: string
  text: string
  level: number
}

const rootRef = ref<HTMLElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const body = ref('')
const tocItems = ref<TocItem[]>([])
const activeHeading = ref('')
const renderError = ref<string | null>(null)

let renderToken = 0
// 目录条目对应的真实元素单独放在非响应式数组里：DOM 引用进响应式系统只会带来无谓开销
let tocElements: {id: string; el: HTMLElement}[] = []

// 首次渲染由 onMounted 显式触发，这里只负责「同一个组件实例内 props 变化」的场景。
// 之所以不依赖 immediate：BlogPanel 用 v-if 创建本组件，挂载即代表详情已就绪，
// 显式调用比依赖 watch 的调度时机更可靠。
watch([() => props.document.id, () => props.document.content], () => void renderArticle())

/** 上一次渲染的文档 id：切换文章时用它判断需要把内容区滚回顶部。 */
let renderedId: number | null = null

async function renderArticle() {
  const switching = renderedId !== null && renderedId !== props.document.id
  renderedId = props.document.id
  // 换文章必须回到顶部，否则会停在上篇的滚动位置，看起来像「新文章从中间开始」
  if (switching) resetScrollForNewArticle()
  await renderArticleBody()
}

/**
 * 切文章时归零并屏蔽位置记录。
 * 平滑滚动期间（以及内容替换后）会冒出一串属于上一篇位置的 scroll 事件，
 * 若照常写入，会把新文章已保存的阅读位置覆盖成一个大偏移。
 * 屏蔽只在「已经到顶」或超时后解除，避免停在顶部时收不到 scroll 事件导致永久失效。
 */
function resetScrollForNewArticle() {
  resettingScroll = true
  scrollToTop(0)
  window.setTimeout(() => {
    resettingScroll = false
  }, 700)
}

async function renderArticleBody() {
  // 立即执行的 watch 早于 onMounted，这里再解析一次滚动宿主，保证目录高亮用的是真实容器
  resolveScrollTarget()
  const token = ++renderToken
  renderError.value = null
  try {
    body.value = md.render(props.document.content ?? '')
  } catch (cause) {
    body.value = ''
    renderError.value = cause instanceof Error ? cause.message : '正文渲染失败'
    return
  }
  await nextTick()
  await nextFrame()
  if (token !== renderToken) return
  await renderMermaidBlocks(token)
  if (token !== renderToken) return
  buildToc()
  await nextTick()
  if (token !== renderToken) return
  prepareScrollMemory()
  updateActiveHeading()
}

/**
 * 等到浏览器下一帧再操作 DOM。
 *
 * <p>rAF 在页面不可见时（后台标签、被隐藏的内嵌面板）会被完全暂停，
 * 直接 await rAF 会让整条渲染链永久挂起——正文已经写入响应式状态，
 * 但 DOM 查询、Mermaid 替换与目录构建都停在 await 之后再也不执行。
 * 因此这里对隐藏态退化为宏任务，保证后台打开也能完成渲染。</p>
 */
function nextFrame(): Promise<void> {
  if (document.hidden) {
    return new Promise(resolve => window.setTimeout(resolve, 16))
  }
  return new Promise(resolve => requestAnimationFrame(() => resolve()))
}

/** 把 language-mermaid 代码块替换为 Mermaid SVG；失败回退源码 + 错误提示，绝不白屏。 */
async function renderMermaidBlocks(token: number) {
  const host = bodyRef.value
  if (!host) return
  const codeBlocks = Array.from(host.querySelectorAll('pre > code.language-mermaid'))
  for (const codeEl of codeBlocks) {
    const source = codeEl.textContent ?? ''
    const container = document.createElement('div')
    container.className = 'blog-mermaid'
    container.title = '点击放大查看'
    codeEl.parentElement?.replaceWith(container)
    if (token !== renderToken) return
    try {
      const svg = await renderMermaid(source)
      if (token !== renderToken) return
      container.innerHTML = svg
    } catch (cause) {
      if (token !== renderToken) return
      const fallback = document.createElement('pre')
      fallback.className = 'blog-mermaid-fallback'
      const code = document.createElement('code')
      code.textContent = source
      fallback.appendChild(code)
      const tip = document.createElement('small')
      tip.textContent = `Mermaid 渲染失败：${cause instanceof Error ? cause.message : String(cause)}`
      container.replaceWith(fallback, tip)
    }
  }
}

/** 扫描 h2/h3/h4 生成目录；无 id 的标题补 blog-heading-N，作为锚点与高亮标识。 */
function buildToc() {
  const host = bodyRef.value
  if (!host) return
  const headings = Array.from(host.querySelectorAll<HTMLElement>('h2, h3, h4'))
  const items: TocItem[] = []
  tocElements = []
  headings.forEach((heading, index) => {
    const id = heading.id || `blog-heading-${index}`
    heading.id = id
    items.push({id, text: (heading.textContent ?? '').trim() || `第 ${index + 1} 节`, level: Number(heading.tagName.slice(1))})
    tocElements.push({id, el: heading})
  })
  tocItems.value = items
}

// ---------------------------------------------------------------- 滚动：目录高亮 + 阅读位置

/** 滚动宿主：优先最近的可滚动祖先（面板内容区），没有则回退 window。 */
let scrollTarget: HTMLElement | Window = window

function resolveScrollTarget() {
  let element = rootRef.value?.parentElement ?? null
  while (element) {
    const overflowY = getComputedStyle(element).overflowY
    if (overflowY === 'auto' || overflowY === 'scroll') {
      scrollTarget = element
      return
    }
    element = element.parentElement
  }
  scrollTarget = window
}

function currentScrollTop(): number {
  return scrollTarget instanceof Window ? window.scrollY : scrollTarget.scrollTop
}

function scrollToTop(top: number) {
  if (scrollTarget instanceof Window) window.scrollTo({top, behavior: 'smooth'})
  else scrollTarget.scrollTo({top, behavior: 'smooth'})
}

/** 高亮当前章节：取最后一个已经越过内容区顶部（含 90px 缓冲）的标题。 */
function updateActiveHeading() {
  if (!tocElements.length) return
  const containerTop = scrollTarget instanceof Window ? 0 : scrollTarget.getBoundingClientRect().top
  const threshold = containerTop + 90
  let active = tocElements[0].id
  for (const entry of tocElements) {
    if (entry.el.getBoundingClientRect().top <= threshold) active = entry.id
    else break
  }
  activeHeading.value = active
}

const SCROLL_KEY_PREFIX = 'blog_scroll_'
const savedTop = ref(0)
const showContinue = ref(false)
/** 本次会话内是否向下滚过：避免「打开文章时容器归零」把已存位置覆盖成 0。 */
let scrolledDown = false
let persistScheduled = false
/**
 * 正在执行「切文章回到顶部」的平滑滚动：期间产生的 scroll 事件带的是上一篇的位置，
 * 必须丢弃，否则会把新文章已保存的阅读位置覆盖成一个大偏移。
 */
let resettingScroll = false

function scrollKey(id: number): string {
  return `${SCROLL_KEY_PREFIX}${id}`
}

/** 读取上一篇的阅读位置；>100px 才提示「继续阅读」，轻微滚动不值得打断。 */
function prepareScrollMemory() {
  scrolledDown = false
  let stored = 0
  try {
    stored = Number(window.localStorage.getItem(scrollKey(props.document.id)) ?? '0')
  } catch {
    stored = 0
  }
  savedTop.value = Number.isFinite(stored) ? stored : 0
  showContinue.value = savedTop.value > 100
}

function onScroll() {
  updateActiveHeading()
  if (persistScheduled) return
  persistScheduled = true
  requestAnimationFrame(() => {
    persistScheduled = false
    if (resettingScroll) {
      // 归零动画结束（到达顶部）后解除屏蔽，后续滚动才重新记录
      if (currentScrollTop() <= 0) resettingScroll = false
      return
    }
    const top = currentScrollTop()
    if (top > 0) scrolledDown = true
    // 未向下滚动过时不写 0，否则「继续阅读」位置会在文章刚打开时被清掉
    if (top <= 0 && !scrolledDown) return
    try {
      window.localStorage.setItem(scrollKey(props.document.id), String(Math.round(top)))
    } catch {
      // 隐私模式下 localStorage 可能不可写，阅读位置只是增强功能，失败不影响正文
    }
  })
}

function continueReading() {
  showContinue.value = false
  scrollToTop(savedTop.value)
}

function jumpToHeading(id: string) {
  const entry = tocElements.find(item => item.id === id)
  if (!entry) return
  const containerTop = scrollTarget instanceof Window ? 0 : scrollTarget.getBoundingClientRect().top
  const offset = currentScrollTop() + entry.el.getBoundingClientRect().top - containerTop - 76
  scrollToTop(Math.max(0, offset))
}

onMounted(() => {
  resolveScrollTarget()
  scrollTarget.addEventListener('scroll', onScroll, {passive: true})
  // 挂载后立即渲染一次：本组件由 BlogPanel 用 v-if 创建，挂载即代表详情已就绪
  void renderArticle()
})

onBeforeUnmount(() => {
  renderToken += 1
  scrollTarget.removeEventListener('scroll', onScroll)
  closeLightbox()
})

// ---------------------------------------------------------------- Mermaid 灯箱

const lightboxSvg = ref('')
const lightboxView = ref({scale: 1, x: 0, y: 0})
const stageRef = ref<HTMLElement | null>(null)
let dragging = false
let lastPointer = {x: 0, y: 0}

const canvasStyle = computed(() => ({
  transform: `translate(calc(-50% + ${lightboxView.value.x}px), calc(-50% + ${lightboxView.value.y}px)) scale(${lightboxView.value.scale})`,
}))

function onBodyClick(event: MouseEvent) {
  const box = (event.target as HTMLElement).closest('.blog-mermaid')
  if (box) openLightbox(box.innerHTML)
}

function openLightbox(svg: string) {
  lightboxSvg.value = svg
  lightboxView.value = {scale: 1, x: 0, y: 0}
  window.addEventListener('keydown', onLightboxKeydown)
}

function closeLightbox() {
  if (!lightboxSvg.value) return
  lightboxSvg.value = ''
  dragging = false
  window.removeEventListener('keydown', onLightboxKeydown)
}

function onLightboxKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeLightbox()
  if (event.key === '+' || event.key === '=') zoomBy(1.25)
  if (event.key === '-') zoomBy(0.8)
  if (event.key === '0') resetLightbox()
}

function zoomBy(factor: number) {
  const rect = stageRef.value?.getBoundingClientRect()
  zoomAt(rect ? rect.left + rect.width / 2 : 0, rect ? rect.top + rect.height / 2 : 0, factor)
}

/** 以指针为中心缩放：指针下的图点保持不动。 */
function zoomAt(clientX: number, clientY: number, factor: number) {
  const rect = stageRef.value?.getBoundingClientRect()
  if (!rect) return
  const px = clientX - rect.left - rect.width / 2
  const py = clientY - rect.top - rect.height / 2
  const next = Math.min(8, Math.max(0.4, lightboxView.value.scale * factor))
  const ratio = next / lightboxView.value.scale
  lightboxView.value = {
    scale: next,
    x: px - (px - lightboxView.value.x) * ratio,
    y: py - (py - lightboxView.value.y) * ratio,
  }
}

function resetLightbox() {
  lightboxView.value = {scale: 1, x: 0, y: 0}
}

function onLightboxWheel(event: WheelEvent) {
  zoomAt(event.clientX, event.clientY, event.deltaY < 0 ? 1.15 : 1 / 1.15)
}

function onPointerDown(event: PointerEvent) {
  dragging = true
  lastPointer = {x: event.clientX, y: event.clientY}
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging) return
  lightboxView.value = {
    ...lightboxView.value,
    x: lightboxView.value.x + event.clientX - lastPointer.x,
    y: lightboxView.value.y + event.clientY - lastPointer.y,
  }
  lastPointer = {x: event.clientX, y: event.clientY}
}

function onPointerUp() {
  dragging = false
}

// ---------------------------------------------------------------- 字号与相关推荐

/** 正文字号倍率：以 --font-body 为基准缩放，避免在组件里另造一套字号阶梯。 */
const fontScale = ref(1)
const bodyStyle = computed(() => ({fontSize: `calc(var(--font-body) * ${fontScale.value})`}))

function zoomText(delta: number) {
  fontScale.value = Math.min(1.8, Math.max(0.9, Math.round((fontScale.value + delta) * 10) / 10))
}

const related = ref<BlogDocumentSummary[]>([])
const relatedError = ref<string | null>(null)
const relatedLoading = ref(false)

watch(() => props.document.id, id => void loadRelated(id), {immediate: true})

async function loadRelated(id: number) {
  relatedLoading.value = true
  relatedError.value = null
  try {
    const result = await blogApi.related(id, 5)
    related.value = result.documents
  } catch (cause) {
    related.value = []
    relatedError.value = cause instanceof Error ? cause.message : '相关文档加载失败'
  } finally {
    relatedLoading.value = false
  }
}

/** 后端返回的是 SQLite 文本时间（YYYY-MM-DD HH:MM:SS），截到分钟即可，不做时区换算。 */
function formatTime(value: string | null): string {
  if (!value) return '—'
  return value.length >= 16 ? value.slice(0, 16).replace('T', ' ') : value
}

/** 字数用「万」单位压缩，避免统计条上出现 7 位数。 */
function formatWords(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)} 万字`
  return `${count} 字`
}
</script>

<template>
  <article ref="rootRef" class="blog-article">
    <header class="blog-article-head">
      <nav class="blog-crumb" aria-label="面包屑">
        <button type="button" class="blog-crumb-link" @click="emit('category', document.category_slug)">
          {{ document.icon }} {{ document.category_name }}
        </button>
        <span aria-hidden="true">/</span>
        <span class="blog-crumb-current">{{ document.title }}</span>
      </nav>

      <div class="blog-article-actions">
        <button v-if="showContinue" type="button" class="blog-action blog-continue" @click="continueReading">
          <IconBook :size="15" aria-hidden="true"/>继续阅读
        </button>
        <button type="button" class="blog-action" :class="{active: favorite}"
                :title="favorite ? '取消收藏' : '收藏这篇文档'"
                @click="emit('toggle-favorite', document.id)">
          <IconStarFilled v-if="favorite" :size="15" aria-hidden="true"/>
          <IconStar v-else :size="15" aria-hidden="true"/>
          {{ favorite ? '已收藏' : '收藏' }}
        </button>
        <span class="blog-zoom" role="group" aria-label="正文字号">
          <IconTypography :size="15" aria-hidden="true"/>
          <button type="button" title="缩小字号" @click="zoomText(-0.1)">A-</button>
          <button type="button" title="放大字号" @click="zoomText(0.1)">A+</button>
        </span>
        <button type="button" class="blog-action" @click="emit('back')">
          <IconArrowLeft :size="15" aria-hidden="true"/>返回
        </button>
      </div>
    </header>

    <h1 class="blog-article-title">{{ document.title }}</h1>

    <div class="blog-article-meta">
      <span><IconFileText :size="14" aria-hidden="true"/>{{ formatWords(document.word_count) }}</span>
      <span><IconClock :size="14" aria-hidden="true"/>约 {{ document.read_time }} 分钟</span>
      <span>更新于 {{ formatTime(document.updated_at) }}</span>
      <span class="blog-article-file">{{ document.filename }}</span>
    </div>

    <p v-if="renderError" class="blog-error-bar">
      <IconAlertTriangle :size="15" aria-hidden="true"/>正文渲染失败：{{ renderError }}
    </p>

    <div class="blog-article-layout">
      <div ref="bodyRef" class="blog-article-body" :style="bodyStyle"
           v-html="body" @click="onBodyClick"/>

      <aside v-if="tocItems.length" class="blog-toc" aria-label="文章目录">
        <h2>目录</h2>
        <ol>
          <li v-for="item in tocItems" :key="item.id" :class="`level-${item.level}`">
            <button type="button" :class="{active: item.id === activeHeading}"
                    @click="jumpToHeading(item.id)">{{ item.text }}</button>
          </li>
        </ol>
      </aside>
    </div>

    <BlogGraph :id="document.id" @open="id => emit('open', id)"/>

    <section class="blog-related" aria-label="相关文档">
      <h2>相关文档</h2>
      <p v-if="relatedLoading" class="blog-muted">加载中…</p>
      <p v-else-if="relatedError" class="blog-error-bar">
        <IconAlertTriangle :size="15" aria-hidden="true"/>相关文档加载失败：{{ relatedError }}
      </p>
      <p v-else-if="!related.length" class="blog-muted">暂无相关文档。</p>
      <ul v-else>
        <li v-for="item in related" :key="item.id">
          <button type="button" @click="emit('open', item.id)">
            <span class="blog-related-icon">{{ item.icon }}</span>
            <span class="blog-related-title">{{ item.title }}</span>
            <span class="blog-related-meta">{{ formatWords(item.word_count) }} · {{ item.category_name }}</span>
          </button>
        </li>
      </ul>
    </section>

    <Teleport to="body">
      <div v-if="lightboxSvg" class="blog-lightbox" @click.self="closeLightbox">
        <div class="blog-lightbox-tools" @click.stop>
          <button type="button" title="放大（+）" @click="zoomBy(1.25)">＋</button>
          <span class="blog-lightbox-scale">{{ Math.round(lightboxView.scale * 100) }}%</span>
          <button type="button" title="缩小（-）" @click="zoomBy(0.8)">－</button>
          <button type="button" title="适应窗口（0）" @click="resetLightbox">适应</button>
          <button type="button" class="blog-lightbox-close" title="关闭（Esc）" @click="closeLightbox">✕</button>
        </div>
        <div ref="stageRef" class="blog-lightbox-stage"
             @click.self="closeLightbox" @wheel.prevent="onLightboxWheel"
             @pointerdown="onPointerDown" @pointermove="onPointerMove"
             @pointerup="onPointerUp" @pointerleave="onPointerUp">
          <div class="blog-lightbox-canvas" :style="canvasStyle" v-html="lightboxSvg"/>
        </div>
        <p class="blog-lightbox-hint">滚轮缩放 · 拖拽平移 · Esc 关闭</p>
      </div>
    </Teleport>
  </article>
</template>

<style scoped>
.blog-article {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.blog-article-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.blog-crumb {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.blog-crumb-link {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--accent);
  font-size: var(--font-caption);
}

.blog-crumb-current {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-article-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.blog-action,
.blog-zoom button,
.blog-continue {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-soft);
  font-size: var(--font-small);
}

.blog-action:hover,
.blog-zoom button:hover { border-color: var(--accent); color: var(--accent); }

.blog-action.active { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }
.blog-continue { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }

.blog-zoom {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-muted);
}

.blog-zoom button { padding: 0 var(--space-2); font-weight: 700; }

.blog-article-title {
  margin: 0;
  font-size: var(--font-heading);
  line-height: 1.35;
  color: var(--text);
}

.blog-article-meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--border);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.blog-article-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.blog-article-file { overflow-wrap: anywhere; }

.blog-article-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: var(--space-5);
  align-items: start;
}

.blog-error-bar {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-left: 3px solid var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: var(--font-caption);
}

.blog-muted { margin: 0; color: var(--text-muted); font-size: var(--font-small); }

/* ---------------- 正文排版（v-html 内容必须用 :deep） ---------------- */

.blog-article-body { min-width: 0; line-height: 1.75; color: var(--text); overflow-wrap: break-word; }
.blog-article-body :deep(p) { margin: 0 0 var(--space-3); }
.blog-article-body :deep(h1),
.blog-article-body :deep(h2),
.blog-article-body :deep(h3),
.blog-article-body :deep(h4) { margin: var(--space-5) 0 var(--space-2); line-height: 1.4; }
.blog-article-body :deep(h2) { font-size: 1.25em; }
.blog-article-body :deep(h3) { font-size: 1.12em; }
.blog-article-body :deep(h4) { font-size: 1em; }
.blog-article-body :deep(ul),
.blog-article-body :deep(ol) { margin: 0 0 var(--space-3); padding-left: var(--space-6); }
.blog-article-body :deep(li) { margin: 2px 0; }
.blog-article-body :deep(hr) { border: 0; border-top: 1px solid var(--border); margin: var(--space-5) 0; }
.blog-article-body :deep(a) { color: var(--accent); }
.blog-article-body :deep(blockquote) {
  margin: var(--space-3) 0;
  padding: var(--space-2) var(--space-3);
  border-left: 3px solid var(--accent);
  background: var(--accent-soft);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  color: var(--text-soft);
}
.blog-article-body :deep(blockquote p) { margin: 2px 0; }
.blog-article-body :deep(code) {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 0.9em;
  background: var(--surface-3);
  border-radius: var(--radius-sm);
  padding: 1px 5px;
}
.blog-article-body :deep(pre) {
  margin: var(--space-3) 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius);
  background: var(--code);
  overflow-x: auto;
}
.blog-article-body :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: 0.88em;
  line-height: 1.6;
  white-space: pre;
}
.blog-article-body :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: var(--space-3) 0;
  font-size: 0.94em;
}
.blog-article-body :deep(th),
.blog-article-body :deep(td) { border: 1px solid var(--border-strong); padding: 5px 10px; text-align: left; }
.blog-article-body :deep(th) { background: var(--surface-3); font-weight: 600; }
.blog-article-body :deep(img) { max-width: 100%; height: auto; border-radius: var(--radius-sm); }

/* Mermaid 图：白底保证浅色主题下的线条对比度，点击进灯箱 */
.blog-article-body :deep(.blog-mermaid) {
  margin: var(--space-3) 0;
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #ffffff;
  overflow: auto;
  max-height: 420px;
  text-align: center;
  cursor: zoom-in;
}
.blog-article-body :deep(.blog-mermaid:hover) { border-color: var(--accent); }
.blog-article-body :deep(.blog-mermaid svg) { max-width: 100%; height: auto; }
.blog-article-body :deep(pre.blog-mermaid-fallback) { background: var(--surface-3); color: var(--text); }
.blog-article-body :deep(pre.blog-mermaid-fallback + small) { display: block; color: var(--danger); margin-top: calc(-1 * var(--space-2)); }

/* ---------------- 目录 ---------------- */

.blog-toc {
  position: sticky;
  top: var(--space-3);
  max-height: calc(100dvh - 160px);
  overflow-y: auto;
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
}

.blog-toc h2 {
  margin: 0 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: 700;
}

.blog-toc ol { margin: 0; padding: 0; list-style: none; }
.blog-toc li.level-3 { padding-left: var(--space-3); }
.blog-toc li.level-4 { padding-left: var(--space-5); }

.blog-toc button {
  display: block;
  width: 100%;
  padding: 3px var(--space-2);
  border: 0;
  border-left: 2px solid transparent;
  background: transparent;
  color: var(--text-soft);
  font-size: var(--font-caption);
  line-height: 1.5;
  text-align: left;
}

.blog-toc button:hover { color: var(--accent); }
.blog-toc button.active { border-left-color: var(--accent); color: var(--accent); font-weight: 600; }

/* ---------------- 相关文档 ---------------- */

.blog-related {
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.blog-related h2 {
  margin: 0 0 var(--space-2);
  font-size: var(--font-title);
}

.blog-related ul { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-1); }

.blog-related button {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text);
  text-align: left;
}

.blog-related button:hover { border-color: var(--accent); }
.blog-related-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--font-small); }
.blog-related-meta { color: var(--text-muted); font-size: var(--font-caption); }

/* ---------------- 灯箱 ---------------- */

.blog-lightbox { position: fixed; inset: 0; z-index: var(--z-lightbox); background: color-mix(in srgb, var(--text) 82%, transparent); }
.blog-lightbox-tools {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: 5px var(--space-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-pop);
}
.blog-lightbox-tools button {
  padding: 3px var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text);
  font-size: var(--font-small);
}
.blog-lightbox-tools button:hover { border-color: var(--accent); color: var(--accent); }
.blog-lightbox-scale { min-width: 44px; text-align: center; color: var(--text-muted); font-size: var(--font-caption); }
.blog-lightbox-close { background: var(--danger) !important; border-color: var(--danger) !important; color: #ffffff !important; }
.blog-lightbox-stage { position: absolute; inset: 0; overflow: hidden; cursor: grab; }
.blog-lightbox-stage:active { cursor: grabbing; }
.blog-lightbox-canvas {
  position: absolute;
  left: 50%;
  top: 50%;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: #ffffff;
  box-shadow: var(--shadow-dialog);
}
/* 显式宽度：mermaid SVG 的 width="100%" 在自适应尺寸的绝对定位画布里会塌缩为 0。 */
.blog-lightbox-canvas :deep(svg) { display: block; width: min(82vw, 980px); max-height: 76vh; height: auto; }
.blog-lightbox-hint {
  position: absolute;
  left: 50%;
  bottom: var(--space-4);
  transform: translateX(-50%);
  margin: 0;
  padding: 4px var(--space-4);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  font-size: var(--font-caption);
  pointer-events: none;
}

/* 目录占一列；窄屏（<1260px）隐藏，正文独占整行 */
@media (max-width: 1260px) {
  .blog-article-layout { grid-template-columns: minmax(0, 1fr); }
  .blog-toc { display: none; }
}

@media (max-width: 900px) {
  .blog-article { padding: var(--space-4); }
  .blog-crumb-current { max-width: 200px; }
}

@media (max-width: 640px) {
  .blog-article-head { align-items: flex-start; flex-direction: column; }
  .blog-related button { grid-template-columns: auto minmax(0, 1fr); }
  .blog-related-meta { grid-column: 1 / -1; }
}
</style>
