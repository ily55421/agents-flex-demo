<script setup lang="ts">
/**
 * 文章关系图谱小图：中心文档 + 一跳邻居（同分类 / 相似）。
 *
 * 力导向计算复用 @/components/graph/forceLayout 的 ForceLayout（与图谱页同一套物理参数），
 * 渲染走 Vue 模板而不是拼 innerHTML：节点坐标是普通对象（shallowRef），每帧只递增一个
 * 计数器触发重渲染，避免为每个坐标建立响应式代理而拖慢 O(n²) 的迭代。
 */
import {computed, onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import {IconAlertTriangle} from '@tabler/icons-vue'
import {blogApi} from '@/api/blog'
import {ForceLayout, type SimEdge, type SimNode} from '@/components/graph/forceLayout'
import type {BlogGraph} from '@/types/blog'

const props = defineProps<{
  /** 中心文档 id；变化时重新拉取图谱。 */
  id: number
}>()

const emit = defineEmits<{ open: [id: number] }>()

/** 关系类型 → 图例文案与线色；未知类型按「相似」处理，后端新增类型时界面不至于空白。 */
const EDGE_STYLES: Record<string, {label: string; color: string}> = {
  same_category: {label: '同分类', color: 'var(--accent)'},
  similar: {label: '相似', color: 'var(--retry)'},
}

/** 可绘制的一条边：同时保留两端节点引用与关系元数据，模板无需回查原始数组下标。 */
interface DrawableEdge {
  a: SimNode
  b: SimNode
  source: number
  target: number
  type: string
}

const graph = ref<BlogGraph | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const hovered = ref<number | null>(null)

const nodes = shallowRef<SimNode[]>([])
const drawableEdges = shallowRef<DrawableEdge[]>([])
const frame = ref(0)
let rafId = 0
let layout: ForceLayout | null = null

/** 悬停节点的邻接节点集合：用于弱化非邻接元素。 */
const neighborIds = computed<Set<string>>(() => {
  const focus = hovered.value
  if (focus == null) return new Set()
  const result = new Set<string>()
  for (const edge of drawableEdges.value) {
    if (edge.source === focus) result.add(String(edge.target))
    if (edge.target === focus) result.add(String(edge.source))
  }
  return result
})

const canRender = computed(() => nodes.value.length >= 2)

/** 自适应视野：按节点包围盒加留白，图谱自动铺满固定高度容器。 */
const viewBox = computed(() => {
  frame.value // 依赖帧计数器：布局推进后重新计算包围盒
  const list = nodes.value
  if (!list.length) return '-160 -160 320 320'
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const node of list) {
    minX = Math.min(minX, node.x)
    minY = Math.min(minY, node.y)
    maxX = Math.max(maxX, node.x)
    maxY = Math.max(maxY, node.y)
  }
  const padding = 60
  return `${minX - padding} ${minY - padding} ${Math.max(200, maxX - minX) + padding * 2} ${Math.max(160, maxY - minY) + padding * 2}`
})

/** 节点半径随连接度增长，中心文档额外放大以便一眼定位。 */
function radiusOf(node: SimNode): number {
  return (node.id === String(graph.value?.center.id ?? '') ? 13 : 8) + Math.min(node.deg, 8) * 1.2
}

function iconOf(id: string): string {
  return graph.value?.nodes.find(item => String(item.id) === id)?.icon ?? '📄'
}

function titleOf(id: string): string {
  return graph.value?.nodes.find(item => String(item.id) === id)?.title ?? `#${id}`
}

function shortTitle(id: string): string {
  const title = titleOf(id)
  return title.length > 16 ? `${title.slice(0, 16)}…` : title
}

function edgeColor(type: string): string {
  return (EDGE_STYLES[type] ?? EDGE_STYLES.similar).color
}

/** 边的高亮判定：悬停节点时只保留其邻接边，其余淡出。 */
function edgeActive(edge: DrawableEdge): boolean {
  if (hovered.value == null) return true
  return edge.source === hovered.value || edge.target === hovered.value
}

function onNodeClick(id: string) {
  const numeric = Number(id)
  if (!Number.isFinite(numeric)) return
  // 中心文档本身不需要「切换」，避免重复加载同一篇
  if (numeric === graph.value?.center.id) return
  emit('open', numeric)
}

async function load(id: number) {
  loading.value = true
  error.value = null
  stopLoop()
  graph.value = null
  nodes.value = []
  drawableEdges.value = []
  try {
    const data = await blogApi.graph(id, 1)
    graph.value = data
    const simNodes: SimNode[] = data.nodes.map(node => ({
      id: String(node.id),
      x: 0, y: 0, vx: 0, vy: 0, fixed: false, deg: 0,
    }))
    const byId = new Map(simNodes.map(node => [node.id, node]))
    const simEdges: SimEdge[] = []
    const drawable: DrawableEdge[] = []
    for (const edge of data.edges) {
      const a = byId.get(String(edge.source))
      const b = byId.get(String(edge.target))
      if (!a || !b) continue
      a.deg++
      b.deg++
      simEdges.push({a, b})
      drawable.push({a, b, source: edge.source, target: edge.target, type: edge.type})
    }
    ForceLayout.initialPositions(simNodes, simEdges, 180)
    nodes.value = simNodes
    drawableEdges.value = drawable
    startLoop(simNodes, simEdges)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '图谱加载失败'
  } finally {
    loading.value = false
  }
}

/** 推进力导向模拟直到 alpha 归零（ForceLayout.tick 返回 0 即已稳定）。 */
function startLoop(simNodes: SimNode[], simEdges: SimEdge[]) {
  layout = new ForceLayout(simNodes, simEdges)
  layout.reheat(1)
  const step = () => {
    if (!layout) return
    const alpha = layout.tick()
    frame.value++
    if (alpha > 0) rafId = requestAnimationFrame(step)
  }
  rafId = requestAnimationFrame(step)
}

function stopLoop() {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
  layout = null
}

watch(() => props.id, id => void load(id), {immediate: true})

onBeforeUnmount(stopLoop)
</script>

<template>
  <section class="blog-graph" aria-label="文章关系图谱">
    <div class="blog-graph-head">
      <h3>文章关系图谱</h3>
      <ul class="blog-graph-legend">
        <li v-for="(style, type) in EDGE_STYLES" :key="type">
          <span class="blog-graph-dot" :style="{background: style.color}"/>{{ style.label }}
        </li>
      </ul>
    </div>

    <p v-if="loading" class="blog-graph-hint">加载中…</p>
    <p v-else-if="error" class="blog-graph-error">
      <IconAlertTriangle :size="14" aria-hidden="true"/>图谱加载失败：{{ error }}
    </p>
    <p v-else-if="!canRender" class="blog-graph-hint">该文章暂无关联文档，暂不展示图谱。</p>

    <svg v-else class="blog-graph-canvas" :viewBox="viewBox" role="img"
         :aria-label="`与「${graph?.center.title ?? ''}」相关的文档关系图`">
      <line v-for="(edge, index) in drawableEdges" :key="`edge-${index}`"
            :x1="edge.a.x" :y1="edge.a.y" :x2="edge.b.x" :y2="edge.b.y"
            :stroke="edgeColor(edge.type)"
            :stroke-width="edgeActive(edge) ? 1.6 : 0.7"
            :stroke-opacity="edgeActive(edge) ? 0.9 : 0.18"/>
      <g v-for="node in nodes" :key="node.id" class="blog-graph-node"
         :class="{dimmed: hovered != null && hovered !== Number(node.id) && !neighborIds.has(node.id)}"
         @mouseenter="hovered = Number(node.id)" @mouseleave="hovered = null"
         @click="onNodeClick(node.id)">
        <title>{{ titleOf(node.id) }}</title>
        <circle :cx="node.x" :cy="node.y" :r="radiusOf(node)"
                :fill="hovered === Number(node.id) ? 'var(--accent-soft)' : 'var(--surface)'"
                :stroke="hovered === Number(node.id) ? 'var(--accent)' : 'var(--border-strong)'"
                stroke-width="1.2"/>
        <text :x="node.x" :y="node.y + 4" class="blog-graph-icon" text-anchor="middle">{{ iconOf(node.id) }}</text>
        <text :x="node.x" :y="node.y + radiusOf(node) + 12" class="blog-graph-label" text-anchor="middle">
          {{ shortTitle(node.id) }}
        </text>
      </g>
    </svg>
  </section>
</template>

<style scoped>
.blog-graph {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.blog-graph-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.blog-graph-head h3 {
  margin: 0;
  font-size: var(--font-title);
  color: var(--text);
}

.blog-graph-legend {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.blog-graph-legend li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.blog-graph-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.blog-graph-canvas {
  width: 100%;
  height: 350px;
  background: var(--surface-2);
  border-radius: var(--radius-sm);
}

.blog-graph-node { cursor: pointer; }
.blog-graph-node.dimmed { opacity: 0.35; }
.blog-graph-icon { font-size: 11px; }
.blog-graph-label { font-size: 10px; fill: var(--text-soft); pointer-events: none; }

.blog-graph-hint,
.blog-graph-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  margin: 0;
  min-height: 350px;
  color: var(--text-muted);
  font-size: var(--font-small);
}

.blog-graph-error { color: var(--danger); }

@media (max-width: 640px) {
  .blog-graph-canvas { height: 260px; }
  .blog-graph-hint,
  .blog-graph-error { min-height: 260px; }
}
</style>
