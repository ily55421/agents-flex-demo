<script setup lang="ts">
/**
 * 对话消息的 Markdown 渲染：markdown-it 生成 HTML（禁用内嵌 HTML，防注入），
 * ```mermaid 代码块渲染为 Mermaid 图表（全局串行渲染器，支持点击灯箱放大），
 * 正文中的“A → B → C”结构化连接链自动转换为流程图（结构化表达优先图示）。
 * 流式场景下未闭合的围栏按普通代码展示，内容补齐后自动重渲染。
 */
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import MarkdownIt from 'markdown-it'
import {renderMermaid} from '@/components/mermaidRenderer'

const props = withDefaults(defineProps<{
  content: string
  /** 流式输出中：未闭合 mermaid 围栏不渲染图表，仅展示代码。 */
  streaming?: boolean
}>(), {streaming: false})

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

let renderToken = 0
const body = ref('')

/** 清理节点标签中会破坏 mermaid 语法的字符。 */
function sanitizeLabel(label: string): string {
  return label.replace(/["`{}|;#]/g, ' ').replace(/\s+/g, ' ').trim()
}

/**
 * 结构化表达优先图示：把连续的“A → B → C”连接链行（列表项或普通段落）转换为
 * 一个 flowchart LR；同名节点自动合并（拓扑链共享母线/设备时呈现为一张连通图）。
 * 代码围栏内、表格行、引用行与含行内代码的行不参与转换。
 */
function transformStructuredChains(source: string): string {
  const lines = source.split('\n')
  const out: string[] = []
  let inFence = false
  let group: string[][] = []
  let seq = 0

  const chainOf = (raw: string): string[] | null => {
    if (inFence) return null
    const trimmed = raw.trim()
    if (!trimmed || trimmed.startsWith('|') || trimmed.startsWith('#') || trimmed.startsWith('>')) return null
    if (trimmed.includes('`')) return null
    const bodyText = trimmed.replace(/^[-*]\s+/, '').replace(/^\d+[.、)]\s+/, '')
    if ((bodyText.match(/→/g) ?? []).length < 2) return null
    if (bodyText.length > 260) return null
    const parts = bodyText.split('→').map(part => part.replace(/[*_]/g, '').trim())
        .filter(part => part.length > 0)
    if (parts.length < 3 || parts.length > 10) return null
    if (parts.some(part => part.length > 24)) return null
    return parts
  }

  const flush = () => {
    if (!group.length) return
    const ids = new Map<string, string>()
    const idOf = (label: string) => {
      if (!ids.has(label)) ids.set(label, `n${++seq}`)
      return ids.get(label)!
    }
    out.push('```mermaid')
    out.push('flowchart LR')
    const seenEdges = new Set<string>()
    for (const parts of group) {
      for (let index = 0; index < parts.length - 1; index++) {
        const from = idOf(parts[index])
        const to = idOf(parts[index + 1])
        const edgeKey = `${from}->${to}`
        if (seenEdges.has(edgeKey)) continue
        seenEdges.add(edgeKey)
        out.push(`    ${from}["${sanitizeLabel(parts[index])}"] --> ${to}["${sanitizeLabel(parts[index + 1])}"]`)
      }
    }
    out.push('```')
    group = []
  }

  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      flush()
      inFence = !inFence
      out.push(line)
      continue
    }
    const parts = chainOf(line)
    if (parts) group.push(parts)
    else {
      flush()
      out.push(line)
    }
  }
  flush()
  return out.join('\n')
}

/** 结构化链转换后再渲染 Markdown（```mermaid 围栏输出为普通代码块，后续替换为图表）。 */
const html = computed(() => md.render(transformStructuredChains(props.content ?? '')))

/** 围栏是否成对闭合；流式中途（奇数个 ```）不启动 mermaid 渲染。 */
const fencesBalanced = computed(() => {
  const fences = (props.content ?? '').match(/^```/gm)?.length ?? 0
  return fences % 2 === 0
})

watch([html, fencesBalanced], () => void renderMermaidBlocks(), {immediate: true})

onBeforeUnmount(() => {
  renderToken += 1
  closeLightbox()
})

/** 把渲染出的 language-mermaid 代码块替换为 Mermaid SVG；失败回退显示源码。 */
async function renderMermaidBlocks() {
  const token = ++renderToken
  body.value = html.value
  await new Promise(resolve => requestAnimationFrame(resolve))
  if (token !== renderToken) return
  const host = hostRef.value
  if (!host) return
  const codeBlocks = Array.from(host.querySelectorAll('pre > code.language-mermaid'))
  if (!codeBlocks.length) return
  if (!fencesBalanced.value) return // 流式未闭合：保留代码展示，下一帧重试

  for (const codeEl of codeBlocks) {
    const source = codeEl.textContent ?? ''
    const container = document.createElement('div')
    container.className = 'md-mermaid'
    container.title = '点击放大查看'
    codeEl.parentElement?.replaceWith(container)
    if (token !== renderToken) return
    try {
      const svg = await renderMermaid(source)
      if (token !== renderToken) return
      container.innerHTML = svg
    } catch (error) {
      if (token !== renderToken) return
      // 图表语法错误时回退为代码块，同时保留错误提示便于修正
      const fallback = document.createElement('pre')
      fallback.className = 'md-mermaid-fallback'
      const code = document.createElement('code')
      code.textContent = source
      fallback.appendChild(code)
      const tip = document.createElement('small')
      tip.textContent = `Mermaid 渲染失败：${error instanceof Error ? error.message : String(error)}`
      container.replaceWith(fallback, tip)
    }
  }
}

// ------------------------------------------------------------------ 灯箱放大

const lightboxSvg = ref('')
const view = ref({scale: 1, x: 0, y: 0})
let dragging = false
let lastPointer = {x: 0, y: 0}

const canvasStyle = computed(() => ({
  transform: `translate(calc(-50% + ${view.value.x}px), calc(-50% + ${view.value.y}px)) scale(${view.value.scale})`,
}))

function onHostClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const box = target.closest('.md-mermaid')
  if (box) openLightbox(box.innerHTML)
}

function onLightboxKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeLightbox()
  if (event.key === '+' || event.key === '=') zoomBy(1.25)
  if (event.key === '-') zoomBy(0.8)
  if (event.key === '0') resetView()
}

function openLightbox(svg: string) {
  lightboxSvg.value = svg
  view.value = {scale: 1, x: 0, y: 0}
  window.addEventListener('keydown', onLightboxKeydown)
}

function closeLightbox() {
  if (!lightboxSvg.value) return
  lightboxSvg.value = ''
  dragging = false
  window.removeEventListener('keydown', onLightboxKeydown)
}

/** 以指针为中心缩放：保持指针下的图点不动。 */
function zoomAt(cx: number, cy: number, factor: number) {
  const stage = stageRef.value
  if (!stage) return
  const rect = stage.getBoundingClientRect()
  const px = cx - rect.left - rect.width / 2
  const py = cy - rect.top - rect.height / 2
  const next = Math.min(8, Math.max(0.4, view.value.scale * factor))
  const ratio = next / view.value.scale
  view.value = {
    scale: next,
    x: px - (px - view.value.x) * ratio,
    y: py - (py - view.value.y) * ratio,
  }
}

function zoomBy(factor: number) {
  const stage = stageRef.value
  const rect = stage?.getBoundingClientRect()
  zoomAt(rect ? rect.left + rect.width / 2 : 0, rect ? rect.top + rect.height / 2 : 0, factor)
}

function resetView() {
  view.value = {scale: 1, x: 0, y: 0}
}

function onLightboxWheel(event: WheelEvent) {
  zoomAt(event.clientX, event.clientY, event.deltaY < 0 ? 1.15 : 1 / 1.15)
}

function onLightboxPointerDown(event: PointerEvent) {
  dragging = true
  lastPointer = {x: event.clientX, y: event.clientY}
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onLightboxPointerMove(event: PointerEvent) {
  if (!dragging) return
  view.value = {
    ...view.value,
    x: view.value.x + event.clientX - lastPointer.x,
    y: view.value.y + event.clientY - lastPointer.y,
  }
  lastPointer = {x: event.clientX, y: event.clientY}
}

function onLightboxPointerUp() {
  dragging = false
}

const stageRef = ref<HTMLElement | null>(null)
const hostRef = ref<HTMLElement | null>(null)
</script>

<template>
  <div ref="hostRef" class="md-body" v-html="body" @click="onHostClick"/>
  <Teleport to="body">
    <div v-if="lightboxSvg" class="md-lightbox" @click.self="closeLightbox">
      <div class="md-lightbox-tools" @click.stop>
        <button type="button" title="放大（+）" @click="zoomBy(1.25)">＋</button>
        <span class="md-lightbox-scale">{{ Math.round(view.scale * 100) }}%</span>
        <button type="button" title="缩小（-）" @click="zoomBy(0.8)">－</button>
        <button type="button" title="适应窗口（0）" @click="resetView">适应</button>
        <button type="button" class="md-lightbox-close" title="关闭（Esc）" @click="closeLightbox">✕</button>
      </div>
      <div ref="stageRef" class="md-lightbox-stage"
           @click.self="closeLightbox" @wheel.prevent="onLightboxWheel"
           @pointerdown="onLightboxPointerDown" @pointermove="onLightboxPointerMove"
           @pointerup="onLightboxPointerUp" @pointerleave="onLightboxPointerUp">
        <div class="md-lightbox-canvas" :style="canvasStyle" v-html="lightboxSvg"/>
      </div>
      <p class="md-lightbox-hint">滚轮缩放 · 拖拽平移 · Esc 关闭</p>
    </div>
  </Teleport>
</template>

<style scoped>
.md-body { font-size: var(--font-body); line-height: 1.65; min-width: 0; overflow-wrap: break-word; }
.md-body :deep(p) { margin: 0 0 8px; }
.md-body :deep(p:last-child) { margin-bottom: 0; }
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) { margin: 14px 0 8px; line-height: 1.35; font-size: 15.5px; }
.md-body :deep(h1) { font-size: 17.5px; }
.md-body :deep(h2) { font-size: 16.5px; }
.md-body :deep(hr) { border: 0; border-top: 1px solid var(--border); margin: 12px 0; }
.md-body :deep(ul),
.md-body :deep(ol) { margin: 6px 0 10px; padding-left: 22px; }
.md-body :deep(li) { margin: 3px 0; }
.md-body :deep(li > p) { margin: 2px 0; }
.md-body :deep(blockquote) { margin: 8px 0; padding: 6px 12px; border-left: 3px solid var(--accent); background: var(--accent-soft); border-radius: 0 6px 6px 0; color: var(--text-soft); }
.md-body :deep(blockquote p) { margin: 2px 0; }
.md-body :deep(code) { font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 12.5px; background: var(--surface-3); border-radius: 4px; padding: 1.5px 5px; }
.md-body :deep(pre) { background: #12261f; color: #e8f2ee; border-radius: 8px; padding: 11px 13px; overflow-x: auto; margin: 8px 0; }
.md-body :deep(pre code) { background: transparent; color: inherit; padding: 0; font-size: 12.5px; line-height: 1.55; white-space: pre; }
.md-body :deep(table) { border-collapse: collapse; margin: 10px 0; font-size: 13px; display: block; max-width: 100%; overflow-x: auto; white-space: nowrap; }
.md-body :deep(th),
.md-body :deep(td) { border: 1px solid var(--border-strong); padding: 5px 10px; text-align: left; }
.md-body :deep(th) { background: var(--surface-3); font-weight: 600; }
.md-body :deep(a) { color: var(--accent); }
.md-body :deep(.md-mermaid) { margin: 10px 0; padding: 10px; background: #ffffff; border: 1px solid var(--border); border-radius: 8px; overflow: auto; max-height: 380px; text-align: center; cursor: zoom-in; }
.md-body :deep(.md-mermaid:hover) { border-color: var(--accent); }
.md-body :deep(.md-mermaid svg) { max-width: 100%; height: auto; }
.md-body :deep(pre.md-mermaid-fallback) { background: var(--surface-3); color: var(--text); }
.md-body :deep(pre.md-mermaid-fallback + small) { display: block; color: var(--danger); margin: -4px 0 8px; }

/* 图表灯箱：暗色遮罩 + 白底画布，滚轮缩放 / 拖拽平移。 */
.md-lightbox { position: fixed; inset: 0; z-index: 200; background: rgba(6, 10, 20, 0.82); }
.md-lightbox-tools { position: absolute; top: 14px; right: 16px; z-index: 2; display: flex; align-items: center; gap: 6px; background: rgba(19, 28, 46, 0.95); border: 1px solid #24344f; border-radius: 10px; padding: 5px 8px; }
.md-lightbox-tools button { background: #182338; border: 1px solid #24344f; color: #dce6f5; border-radius: 7px; padding: 3px 11px; font-size: 13px; cursor: pointer; }
.md-lightbox-tools button:hover { border-color: #4da3ff; color: #4da3ff; }
.md-lightbox-tools .md-lightbox-scale { color: #8fa3c0; font-size: 12px; min-width: 42px; text-align: center; }
.md-lightbox-tools .md-lightbox-close { background: #b73f47; border-color: #b73f47; color: #fff; }
.md-lightbox-stage { position: absolute; inset: 0; overflow: hidden; cursor: grab; }
.md-lightbox-stage:active { cursor: grabbing; }
.md-lightbox-canvas { position: absolute; left: 50%; top: 50%; background: #ffffff; border-radius: 12px; padding: 18px 22px; box-shadow: 0 18px 60px rgba(0, 0, 0, 0.45); }
/* 显式宽度：mermaid SVG 的 width="100%" 在自适应尺寸的绝对定位画布里会塌缩为 0，
   必须给定具体宽度，高度按 viewBox 比例计算。 */
.md-lightbox-canvas :deep(svg) { width: min(82vw, 980px); max-height: 76vh; height: auto; display: block; }
.md-lightbox-hint { position: absolute; left: 50%; bottom: 16px; transform: translateX(-50%); margin: 0; color: #8fa3c0; font-size: 12px; background: rgba(19, 28, 46, 0.85); border: 1px solid #24344f; border-radius: 999px; padding: 4px 14px; pointer-events: none; }
</style>
