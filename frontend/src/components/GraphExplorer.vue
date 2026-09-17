<script setup lang="ts">
/**
 * 图谱知识库页：参考电力本体图谱页面实现的三视图探索器。
 * 实例图谱/本体TBox 用 Canvas 力导向布局渲染（pan/zoom/拖拽/检索/图例过滤/详情面板），
 * 站点档案视图复用 StationBoard 组件（站点列表 + 设备档案 + 事实检索）。
 */
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {IconArrowBackUp, IconFocus2, IconReload, IconSearch, IconX} from '@tabler/icons-vue'
import {graphApi} from '@/api/agent'
import type {GraphNodeDetail, GraphStation, GraphSummary, GraphView} from '@/types/agent'
import StationBoard from '@/components/graph/StationBoard.vue'
import {ForceLayout, type SimNode} from '@/components/graph/forceLayout'

type GraphMode = 'inst' | 'tbox' | 'stations'

/** 类别配色与中文名，沿用本体图谱参考页的视觉方案。 */
const CLASS_COLORS: Record<string, string> = {
  Station: '#e74c3c', Bus: '#f39c12', PowerTransformer: '#9b59b6',
  Breaker: '#2980b9', Disconnector: '#3498db', GroundDisconnector: '#7f8c8d',
  VoltageTransformer: '#2ecc71', CurrentTransformer: '#27ae60', Fuse: '#bdc3c7',
  SurgeArrester: '#d35400', Capacitor: '#1abc9c', StationTransformer: '#16a085',
  FeederBay: '#16a085', IncomingBay: '#1abc9c', BusTieBay: '#e67e22', AuxBay: '#95a5a6',
  PowerLine: '#c0392b', VoltageLevel: '#f1c40f', ConductorNode: '#556677', Class: '#4da3ff',
}
const CLASS_CN: Record<string, string> = {
  Station: '变电站', Bus: '母线', PowerTransformer: '主变压器', Breaker: '断路器',
  Disconnector: '隔离开关', GroundDisconnector: '接地刀闸', VoltageTransformer: '电压互感器',
  CurrentTransformer: '电流互感器', Fuse: '熔断器', SurgeArrester: '避雷器', Capacitor: '电容器',
  StationTransformer: '站用变压器', FeederBay: '出线间隔', IncomingBay: '电源进线间隔',
  BusTieBay: '母联/分段间隔', AuxBay: '辅助间隔', PowerLine: '电力线路', VoltageLevel: '电压等级',
  ConductorNode: '连接点',
}

interface CanvasNode extends SimNode {
  cls: string
  label: string
  station: string | null
  aliases: string[]
  /** 绘制半径。 */
  r: number
}

interface CanvasEdge {
  a: CanvasNode
  b: CanvasNode
  p: string
  pc: string
}

const mode = ref<GraphMode>('inst')
const loading = ref(true)
const error = ref('')
const view = ref<GraphView | null>(null)
const summary = ref<GraphSummary | null>(null)
const stations = ref<GraphStation[]>([])
const stationFilter = ref('')
const offClasses = ref<Set<string>>(new Set())
const query = ref('')
const hits = ref<CanvasNode[]>([])
const selected = ref<CanvasNode | null>(null)
const nodeDetail = ref<GraphNodeDetail | null>(null)
const detailLoading = ref(false)
const hovered = ref<CanvasNode | null>(null)
const tipX = ref(0)
const tipY = ref(0)

const canvasRef = ref<HTMLCanvasElement | null>(null)
const stageRef = ref<HTMLElement | null>(null)

let ctx: CanvasRenderingContext2D | null = null
let rafId = 0
let layout: ForceLayout | null = null
let canvasNodes: CanvasNode[] = []
let canvasEdges: CanvasEdge[] = []
let allNodes: CanvasNode[] = []
let edgeSource: { a: string; b: string; p: string }[] = []
let nodeById = new Map<string, CanvasNode>()
let width = 0
let height = 0
let dpr = 1
let panX = 0
let panY = 0
let scale = 1
let dragNode: CanvasNode | null = null
let panning = false
let lastPointer = {x: 0, y: 0}
let moved = false
let neighborIds = new Set<string>()
let resizeObserver: ResizeObserver | null = null

/** 图例：类别 + 数量 + 开关状态。 */
const legendItems = computed(() => {
  if (!view.value) return []
  const counts = new Map<string, number>()
  for (const node of view.value.nodes) counts.set(node.cls, (counts.get(node.cls) ?? 0) + 1)
  return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .map(([cls, count]) => ({
        cls,
        cn: CLASS_CN[cls] ?? cls,
        color: CLASS_COLORS[cls] ?? '#8fa3c0',
        count,
        off: offClasses.value.has(cls),
      }))
})

const predCn = computed(() => view.value?.meta.predCn ?? {})

onMounted(async () => {
  try {
    const [viewData, summaryData, stationData] = await Promise.all([
      graphApi.view(), graphApi.summary(), graphApi.stations(),
    ])
    view.value = viewData
    summary.value = summaryData
    stations.value = stationData
    prepareSource(viewData)
    await nextTick()
    initCanvas()
    buildGraph()
    loading.value = false
    startLoop()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    loading.value = false
  }
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  resizeObserver?.disconnect()
})

watch(mode, value => {
  if (value === 'stations') return
  selected.value = null
  nodeDetail.value = null
  query.value = ''
  hits.value = []
  nextTick(() => {
    initCanvas()
    buildGraph()
  })
})

watch([stationFilter, offClasses], () => {
  if (mode.value !== 'inst') return
  selected.value = null
  nodeDetail.value = null
  buildGraph()
})

// ------------------------------------------------------------------ 数据构建

/** 预置全量画布节点（含展示字段），供过滤与检索复用。 */
function prepareSource(data: GraphView) {
  allNodes = data.nodes.map(node => ({
    id: node.id, cls: node.cls, label: node.label,
    station: node.station, aliases: node.aliases ?? [],
    x: 0, y: 0, vx: 0, vy: 0, fixed: false,
    deg: 0, r: 5,
  }))
  nodeById = new Map(allNodes.map(node => [node.id, node]))
  edgeSource = data.edges.map(edge => ({a: edge.s, b: edge.o, p: edge.p}))
  for (const edge of edgeSource) {
    const a = nodeById.get(edge.a)
    const b = nodeById.get(edge.b)
    if (a) a.deg++
    if (b) b.deg++
  }
}

/** 当前过滤条件下应显示的节点 id 集合（类别开关 + 站点过滤）。 */
function visibleIds(): Set<string> {
  const off = offClasses.value
  const visible = new Set<string>()
  if (!stationFilter.value) {
    for (const node of allNodes) {
      if (!off.has(node.cls)) visible.add(node.id)
    }
    return visible
  }
  const station = stationFilter.value
  const core = allNodes.filter(node => node.station === station && !off.has(node.cls))
  for (const node of core) visible.add(node.id)
  // 补齐与站内实体相连的共享节点（电压等级/线路），与后端子图口径一致
  for (const edge of edgeSource) {
    const inA = visible.has(edge.a)
    const inB = visible.has(edge.b)
    if (inA === inB) continue
    const peerId = inA ? edge.b : edge.a
    const peer = nodeById.get(peerId)
    if (peer && !off.has(peer.cls) && (peer.cls === 'VoltageLevel' || peer.cls === 'PowerLine')) {
      visible.add(peerId)
    }
  }
  return visible
}

/** 依据模式与过滤条件构建力导向模型并初始化布局。 */
function buildGraph() {
  if (!view.value) return
  if (mode.value === 'tbox') {
    const tbox = view.value.tbox
    canvasNodes = tbox.nodes.map(cls => ({
      id: cls.id, cls: 'Class', label: cls.l,
      station: null, aliases: [cls.en ?? ''],
      x: 0, y: 0, vx: 0, vy: 0, fixed: false, deg: 0,
      r: 16,
    }))
    const byId = new Map(canvasNodes.map(node => [node.id, node]))
    canvasEdges = tbox.edges.map(edge => ({
      a: byId.get(edge.s)!, b: byId.get(edge.o)!, p: edge.p, pc: edge.pc ?? predCn.value[edge.p] ?? edge.p,
    })).filter(edge => edge.a && edge.b)
    for (const edge of canvasEdges) {
      edge.a.deg++
      edge.b.deg++
    }
    // 本体层级是树：用层次布局保证可读性（parent 字段存的是父类的英文名）
    const enToId = new Map(tbox.nodes.map(cls => [cls.en ?? cls.id.replace('cls:', ''), cls.id]))
    const parents = new Map(tbox.nodes.map(cls => [cls.id, cls.parent ? enToId.get(cls.parent) ?? null : null]))
    ForceLayout.treeLayout(canvasNodes, parents)
    nodeById = new Map(canvasNodes.map(node => [node.id, node]))
    layout = null
    selected.value = null
    nodeDetail.value = null
    neighborIds = new Set()
    fitView()
    return
  } else {
    const visible = visibleIds()
    canvasNodes = allNodes
        .filter(node => visible.has(node.id))
        .map(node => ({...node, fixed: false}))
    const byId = new Map(canvasNodes.map(node => [node.id, node]))
    canvasEdges = edgeSource
        .map(edge => ({
          a: byId.get(edge.a)!, b: byId.get(edge.b)!,
          p: edge.p, pc: predCn.value[edge.p] ?? edge.p,
        }))
        .filter(edge => edge.a && edge.b)
    for (const node of canvasNodes) {
      node.r = node.cls === 'Station' ? 13 : 4 + Math.min(node.deg, 24) / 2.4
    }
  }
  nodeById = new Map(canvasNodes.map(node => [node.id, node]))
  ForceLayout.initialPositions(canvasNodes, canvasEdges, 380)
  layout = new ForceLayout(canvasNodes, canvasEdges)
  layout.reheat(1)
  selected.value = null
  nodeDetail.value = null
  neighborIds = new Set()
  fitView()
}

// ------------------------------------------------------------------ 渲染循环

function initCanvas() {
  const canvas = canvasRef.value
  const stage = stageRef.value
  if (!canvas || !stage) return
  ctx = canvas.getContext('2d')
  dpr = window.devicePixelRatio || 1
  width = stage.clientWidth
  height = stage.clientHeight
  canvas.width = Math.max(1, Math.floor(width * dpr))
  canvas.height = Math.max(1, Math.floor(height * dpr))
  resizeObserver?.disconnect()
  resizeObserver = new ResizeObserver(() => {
    if (!stage || !canvasRef.value) return
    width = stage.clientWidth
    height = stage.clientHeight
    dpr = window.devicePixelRatio || 1
    canvasRef.value.width = Math.max(1, Math.floor(width * dpr))
    canvasRef.value.height = Math.max(1, Math.floor(height * dpr))
  })
  resizeObserver.observe(stage)
}

function startLoop() {
  const render = () => {
    step()
    draw()
    rafId = requestAnimationFrame(render)
  }
  rafId = requestAnimationFrame(render)
}

function step() {
  if (!layout) return
  if (dragNode) layout.reheat(Math.max(layout.alpha, 0.35))
  layout.tick()
}

function draw() {
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#f7faf8'
  ctx.fillRect(0, 0, width, height)
  ctx.save()
  ctx.translate(width / 2 + panX, height / 2 + panY)
  ctx.scale(scale, scale)

  const showLabels = mode.value === 'tbox' || scale > 1.15
  const highlight = neighborIds.size > 0
  // TBox 谓词标签只在关联边（悬停/选中节点的边）上显示，避免 24 条边的标签挤成一团
  const labeledEdgeIds = new Set<string>()
  if (mode.value === 'tbox') {
    const focusId = hovered.value?.id ?? selected.value?.id
    if (focusId) {
      for (const edge of canvasEdges) {
        if (edge.a.id === focusId || edge.b.id === focusId) labeledEdgeIds.add(edge.p + edge.a.id + edge.b.id)
      }
    }
  }

  // 边：非邻接边弱化，邻接边按谓词高亮（亮色画布用主题绿 + 中性灰）
  for (const edge of canvasEdges) {
    const active = !highlight
        || neighborIds.has(edge.a.id) && neighborIds.has(edge.b.id)
    ctx.strokeStyle = active ? 'rgba(8, 120, 88, 0.42)' : 'rgba(122, 140, 130, 0.32)'
    ctx.lineWidth = active ? 1.4 / scale : 1 / scale
    ctx.beginPath()
    ctx.moveTo(edge.a.x, edge.a.y)
    ctx.lineTo(edge.b.x, edge.b.y)
    ctx.stroke()
    if (mode.value === 'tbox' && edge.pc && labeledEdgeIds.has(edge.p + edge.a.id + edge.b.id)) {
      ctx.fillStyle = 'rgba(68, 84, 74, 0.95)'
      ctx.font = `${11 / scale}px "Microsoft YaHei", sans-serif`
      ctx.textAlign = 'center'
      ctx.fillText(edge.pc, (edge.a.x + edge.b.x) / 2, (edge.a.y + edge.b.y) / 2 - 3 / scale)
    }
  }

  // 节点：高亮集合外压暗；细描边保证浅色节点在亮底上仍有轮廓
  for (const node of canvasNodes) {
    const dim = highlight && !neighborIds.has(node.id) && selected.value?.id !== node.id
    ctx.globalAlpha = dim ? 0.22 : 1
    ctx.fillStyle = CLASS_COLORS[node.cls] ?? '#7a8c82'
    ctx.beginPath()
    ctx.arc(node.x, node.y, node.r, 0, Math.PI * 2)
    ctx.fill()
    ctx.strokeStyle = 'rgba(21, 32, 25, 0.28)'
    ctx.lineWidth = 1 / scale
    ctx.stroke()
    if (selected.value?.id === node.id) {
      ctx.strokeStyle = '#087858'
      ctx.lineWidth = 2.4 / scale
      ctx.beginPath()
      ctx.arc(node.x, node.y, node.r + 3 / scale, 0, Math.PI * 2)
      ctx.stroke()
    }
    const labeled = showLabels
        || hovered.value?.id === node.id
        || selected.value?.id === node.id
        || (highlight && neighborIds.has(node.id))
    if (labeled) {
      ctx.font = `${(mode.value === 'tbox' ? 13 : 12) / scale}px "Microsoft YaHei", sans-serif`
      ctx.textAlign = 'center'
      ctx.fillStyle = 'rgba(247, 250, 248, 0.82)'
      const metrics = ctx.measureText(node.label)
      const pad = 3 / scale
      ctx.fillRect(node.x - metrics.width / 2 - pad, node.y + node.r + 2 / scale,
          metrics.width + pad * 2, 14 / scale)
      ctx.fillStyle = dim ? 'rgba(68, 84, 74, 0.5)' : '#152019'
      ctx.fillText(node.label, node.x, node.y + node.r + 12.5 / scale)
    }
    ctx.globalAlpha = 1
  }
  ctx.restore()
}

/** 把可见内容适配到画布中央。 */
function fitView() {
  if (!canvasNodes.length) {
    scale = 1
    panX = 0
    panY = 0
    return
  }
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const node of canvasNodes) {
    minX = Math.min(minX, node.x)
    maxX = Math.max(maxX, node.x)
    minY = Math.min(minY, node.y)
    maxY = Math.max(maxY, node.y)
  }
  const boxWidth = Math.max(maxX - minX, 120) + 140
  const boxHeight = Math.max(maxY - minY, 120) + 140
  scale = Math.min(width / boxWidth, height / boxHeight, mode.value === 'tbox' ? 1.6 : 1.5)
  panX = -(minX + maxX) / 2 * scale
  panY = -(minY + maxY) / 2 * scale
}

// ------------------------------------------------------------------ 指针交互

function toWorld(px: number, py: number): { x: number; y: number } {
  return {x: (px - width / 2 - panX) / scale, y: (py - height / 2 - panY) / scale}
}

function pickNode(px: number, py: number): CanvasNode | null {
  const point = toWorld(px, py)
  let best: CanvasNode | null = null
  let bestDist = Infinity
  for (let index = canvasNodes.length - 1; index >= 0; index--) {
    const node = canvasNodes[index]
    const dx = node.x - point.x
    const dy = node.y - point.y
    const dist = dx * dx + dy * dy
    const reach = node.r + 6 / scale
    if (dist < reach * reach && dist < bestDist) {
      best = node
      bestDist = dist
    }
  }
  return best
}

function onPointerDown(event: PointerEvent) {
  if (!canvasRef.value) return
  canvasRef.value.setPointerCapture(event.pointerId)
  const rect = canvasRef.value.getBoundingClientRect()
  const px = event.clientX - rect.left
  const py = event.clientY - rect.top
  moved = false
  lastPointer = {x: px, y: py}
  const node = pickNode(px, py)
  if (node) {
    dragNode = node
    node.fixed = true
  } else {
    panning = true
  }
}

function onPointerMove(event: PointerEvent) {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const px = event.clientX - rect.left
  const py = event.clientY - rect.top
  if (dragNode) {
    const point = toWorld(px, py)
    dragNode.x = point.x
    dragNode.y = point.y
    moved = true
  } else if (panning) {
    panX += px - lastPointer.x
    panY += py - lastPointer.y
    moved = true
  } else {
    hovered.value = pickNode(px, py)
    tipX.value = px
    tipY.value = py
  }
  lastPointer = {x: px, y: py}
}

function onPointerUp() {
  dragNode = null
  panning = false
}

function onClick(event: MouseEvent) {
  if (moved) {
    moved = false
    return
  }
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const node = pickNode(event.clientX - rect.left, event.clientY - rect.top)
  if (node) selectNode(node)
  else {
    selected.value = null
    nodeDetail.value = null
    neighborIds = new Set()
  }
}

function onWheel(event: WheelEvent) {
  event.preventDefault()
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const px = event.clientX - rect.left
  const py = event.clientY - rect.top
  const factor = event.deltaY < 0 ? 1.12 : 1 / 1.12
  const next = Math.min(6, Math.max(0.08, scale * factor))
  // 以指针为缩放中心：保持指针下的世界坐标不动
  const worldX = (px - width / 2 - panX) / scale
  const worldY = (py - height / 2 - panY) / scale
  scale = next
  panX = px - width / 2 - worldX * scale
  panY = py - height / 2 - worldY * scale
}

// ------------------------------------------------------------------ 选择与检索

async function selectNode(node: CanvasNode) {
  selected.value = node
  neighborIds = new Set([node.id])
  for (const edge of canvasEdges) {
    if (edge.a.id === node.id) neighborIds.add(edge.b.id)
    if (edge.b.id === node.id) neighborIds.add(edge.a.id)
  }
  if (mode.value === 'tbox') {
    nodeDetail.value = null
    return
  }
  detailLoading.value = true
  try {
    nodeDetail.value = await graphApi.nodeDetail(node.id)
  } catch {
    nodeDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** 检索命中后聚焦：居中 + 选中 + 展开邻接。 */
function focusHit(node: CanvasNode) {
  hits.value = []
  query.value = node.label
  panX = -node.x * scale
  panY = -node.y * scale
  void selectNode(node)
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    const keyword = query.value.trim().toLowerCase()
    if (!keyword) {
      hits.value = []
      return
    }
    hits.value = canvasNodes
        .filter(node => node.label.toLowerCase().includes(keyword)
            || node.aliases.some(alias => alias.toLowerCase().includes(keyword)))
        .slice(0, 12)
  }, 160)
}

function toggleClass(cls: string) {
  const next = new Set(offClasses.value)
  next.has(cls) ? next.delete(cls) : next.add(cls)
  offClasses.value = next
}

function resetLayout() {
  buildGraph()
}

function classCn(cls: string) {
  return CLASS_CN[cls] ?? cls
}

function attrEntries(detail: GraphNodeDetail | null) {
  if (!detail) return []
  return Object.entries(detail.node.attrs ?? {})
}

/** 详情面板邻接组（出边/入边按中文谓词分组）。 */
interface AdjacencyGroup {
  predicate: string
  neighbors: GraphNodeDetail['outgoing'][string]
  direction: string
}

function adjacencyGroups(detail: GraphNodeDetail | null): AdjacencyGroup[] {
  if (!detail) return []
  const groups: AdjacencyGroup[] = []
  for (const [predicate, neighbors] of Object.entries(detail.outgoing ?? {})) {
    groups.push({predicate, neighbors, direction: '→'})
  }
  for (const [predicate, neighbors] of Object.entries(detail.incoming ?? {})) {
    groups.push({predicate, neighbors, direction: '←'})
  }
  return groups
}

/** TBox 模式下选中类的关系列表（selected 变化时重算）。 */
const selectedTboxRelations = computed(() => {
  const current = selected.value
  if (!current) return []
  return canvasEdges
      .filter(edge => edge.a.id === current.id || edge.b.id === current.id)
      .map(edge => ({
        key: edge.p + (edge.a.id === current.id ? `->${edge.b.id}` : `<-${edge.a.id}`),
        outgoing: edge.a.id === current.id,
        pc: edge.pc,
        peerLabel: edge.a.id === current.id ? edge.b.label : edge.a.label,
        peerId: edge.a.id === current.id ? edge.b.id : edge.a.id,
      }))
})

function jumpToNeighbor(neighbor: { id: string }) {
  const node = nodeById.get(neighbor.id)
  if (node) {
    focusHit(node)
    void selectNode(node)
  }
}
</script>

<template>
  <section class="graph-shell">
    <header class="graph-bar">
      <div class="seg" role="tablist">
        <button type="button" :class="{on: mode === 'inst'}" @click="mode = 'inst'">本体图谱</button>
        <button type="button" :class="{on: mode === 'tbox'}" @click="mode = 'tbox'">本体 TBox</button>
        <button type="button" :class="{on: mode === 'stations'}" @click="mode = 'stations'">站点档案</button>
      </div>
      <template v-if="mode !== 'stations'">
        <div class="search-box">
          <IconSearch :size="15"/>
          <input v-model="query" type="search" placeholder="检索实体名称 / 别名，回车看命中"
                 @input="onSearchInput" @keydown.enter.prevent="hits.length && focusHit(hits[0])"/>
          <button v-if="query" type="button" class="clear" @click="query = ''; hits = []"><IconX :size="13"/></button>
        </div>
        <select v-if="mode === 'inst'" v-model="stationFilter" aria-label="站点过滤">
          <option value="">全部站点</option>
          <option v-for="station in stations" :key="station.name" :value="station.name">
            {{ station.name }}{{ station.entities ? `（${station.entities}）` : '' }}
          </option>
        </select>
        <button v-if="selected || neighborIds.size" type="button" class="ghost-btn"
                title="清除选择与高亮" @click="selected = null; nodeDetail = null; neighborIds = new Set()">
          <IconArrowBackUp :size="15"/>
        </button>
        <button type="button" class="ghost-btn" title="重置视图" @click="resetLayout">
          <IconReload :size="15"/>
        </button>
        <span v-if="summary" class="stat-chip">
          {{ summary.entities }} 实体 · {{ summary.edges }} 关系
          <template v-if="mode === 'tbox'"> · {{ summary.tboxClasses }} 类</template>
        </span>
      </template>
      <span v-else-if="summary" class="stat-chip">
        {{ summary.archiveStations }} 站档案 · {{ summary.archiveFacts }} 条事实
      </span>
    </header>

    <div v-if="error" class="graph-error">图谱加载失败：{{ error }}</div>
    <div v-else-if="loading" class="graph-loading">图谱数据加载中…</div>

    <StationBoard v-else-if="mode === 'stations'" :stations="stations"/>

    <div v-show="mode !== 'stations'" ref="stageRef" class="graph-stage">
      <canvas ref="canvasRef" class="graph-canvas"
              @pointerdown="onPointerDown" @pointermove="onPointerMove" @pointerup="onPointerUp"
              @pointerleave="hovered = null" @click="onClick" @wheel="onWheel"/>
      <div v-if="hits.length" class="hit-list">
        <div class="hit-head">命中 {{ hits.length }} 项（回车选第一项）</div>
        <button v-for="hit in hits" :key="hit.id" type="button" class="hit-item" @click="focusHit(hit)">
          <span class="dot" :style="{background: CLASS_COLORS[hit.cls] ?? '#8fa3c0'}"/>
          <span class="hit-label">{{ hit.label }}</span>
          <span class="hit-cls">{{ classCn(hit.cls) }}</span>
        </button>
      </div>

      <div v-if="mode === 'inst'" class="legend">
        <div class="legend-title">类别图例（点击过滤）</div>
        <div class="legend-row">
          <button v-for="item in legendItems" :key="item.cls" type="button"
                  class="legend-item" :class="{off: item.off}" @click="toggleClass(item.cls)">
            <span class="dot" :style="{background: item.color}"/>
            {{ item.cn }} <b>{{ item.count }}</b>
          </button>
        </div>
      </div>

      <div v-if="hovered && !dragNode" class="tip" :style="{left: tipX + 14 + 'px', top: tipY + 14 + 'px'}">
        <b>{{ hovered.label }}</b>
        <span>{{ classCn(hovered.cls) }}{{ hovered.station ? ' · ' + hovered.station : '' }}</span>
      </div>

      <aside v-if="selected" class="side">
        <button type="button" class="side-close" @click="selected = null; nodeDetail = null; neighborIds = new Set()">
          <IconX :size="15"/>
        </button>
        <h2>{{ selected.label }}</h2>
        <div class="sub">
          <span class="tag" :style="{borderColor: CLASS_COLORS[selected.cls] ?? '#8fa3c0'}">
            {{ classCn(selected.cls) }}
          </span>
          <span v-if="selected.station" class="tag">{{ selected.station }}</span>
          <span v-if="mode === 'tbox' && summary?.byClass[selected.aliases[0]]"
                class="tag">实例 {{ summary.byClass[selected.aliases[0]] }}</span>
        </div>
        <div v-if="selected.aliases.length" class="grp">
          <h3>别名</h3>
          <div class="tag-wrap"><span v-for="alias in selected.aliases" :key="alias" class="tag">{{ alias }}</span></div>
        </div>
        <template v-if="mode === 'inst'">
          <div v-if="detailLoading" class="grp">详情加载中…</div>
          <template v-else-if="nodeDetail">
            <div v-if="attrEntries(nodeDetail).length" class="grp">
              <h3>属性</h3>
              <div v-for="[key, value] in attrEntries(nodeDetail)" :key="key" class="kv">
                <span>{{ key }}</span><b>{{ value }}</b>
              </div>
            </div>
            <div v-for="group in adjacencyGroups(nodeDetail)" :key="group.direction + group.predicate" class="grp">
              <h3>{{ group.direction }} {{ group.predicate }}（{{ group.neighbors.length }}）</h3>
              <button v-for="neighbor in group.neighbors" :key="neighbor.id" type="button" class="li"
                      @click="jumpToNeighbor(neighbor)">
                <span class="dot" :style="{background: CLASS_COLORS[neighbor.cls] ?? '#8fa3c0'}"/>
                {{ neighbor.label }} <i>{{ neighbor.clsCn }}</i>
              </button>
            </div>
          </template>
        </template>
        <template v-else>
          <div class="grp">
            <h3>类关系（TBox）</h3>
            <button v-for="relation in selectedTboxRelations" :key="relation.key" type="button" class="li"
                    @click="jumpToNeighbor({id: relation.peerId})">
              <span class="dot" style="background:#4da3ff"/>
              {{ relation.outgoing ? '→' : '←' }} {{ relation.pc }} {{ relation.peerLabel }}
            </button>
          </div>
        </template>
      </aside>

      <div v-if="!loading && !canvasNodes.length" class="graph-empty">
        当前过滤条件下没有可见节点，请调整类别图例或站点过滤。
      </div>
      <div class="zoom-hint">
        <IconFocus2 :size="14"/> 滚轮缩放 · 拖拽平移 · 点击节点看详情
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 图谱页与全站统一的亮色主题：白面板 + 绿色强调（与知识库/对话页同一套 design tokens）。 */
.graph-shell { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--surface); color: var(--text); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; box-shadow: var(--shadow); }
.graph-bar { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--border); background: var(--surface); flex-wrap: wrap; }
.seg { display: flex; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; background: var(--surface-2); }
.seg button { background: transparent; color: var(--text-muted); border: 0; padding: 6px 14px; font-size: 13px; cursor: pointer; }
.seg button:hover { color: var(--accent); }
.seg button.on { background: var(--accent); color: #fff; }
.search-box { display: flex; align-items: center; gap: 6px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; padding: 5px 10px; color: var(--text-muted); }
.search-box input { background: transparent; border: 0; outline: none; color: var(--text); width: 260px; font-size: 13px; }
.search-box input:focus { outline: none; }
.search-box .clear { background: transparent; border: 0; color: var(--text-muted); cursor: pointer; display: flex; }
.search-box:focus-within { border-color: var(--accent); }
.graph-bar select { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; color: var(--text); padding: 6px 8px; font-size: 13px; outline: none; max-width: 240px; }
.ghost-btn { display: inline-flex; align-items: center; gap: 4px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; color: var(--text-muted); padding: 6px 9px; cursor: pointer; }
.ghost-btn:hover { color: var(--accent); border-color: var(--accent); }
.stat-chip { margin-left: auto; font-size: 12px; color: var(--text-muted); white-space: nowrap; }
.graph-stage { position: relative; flex: 1; min-height: 0; overflow: hidden; background: #f7faf8; }
.graph-canvas { position: absolute; inset: 0; width: 100%; height: 100%; cursor: grab; }
.graph-canvas:active { cursor: grabbing; }
.graph-loading, .graph-error, .graph-empty { display: grid; place-items: center; flex: 1; color: var(--text-muted); font-size: 14px; padding: 40px; text-align: center; }
.graph-error { color: var(--danger); }
.hit-list { position: absolute; left: 12px; top: 12px; width: 300px; max-height: 55%; overflow-y: auto; background: rgba(255, 255, 255, 0.97); border: 1px solid var(--border); border-radius: 10px; box-shadow: var(--shadow); z-index: 14; padding: 6px; }
.hit-head { font-size: 12px; color: var(--text-muted); padding: 4px 8px; }
.hit-item { display: flex; align-items: center; gap: 8px; width: 100%; background: transparent; border: 0; color: var(--text); font-size: 13px; padding: 6px 8px; border-radius: 6px; cursor: pointer; text-align: left; }
.hit-item:hover { background: var(--surface-2); }
.hit-item .hit-cls { margin-left: auto; color: var(--text-muted); font-size: 11px; }
.dot { width: 9px; height: 9px; border-radius: 50%; flex: none; display: inline-block; }
.legend { position: absolute; left: 12px; bottom: 12px; background: rgba(255, 255, 255, 0.95); border: 1px solid var(--border); border-radius: 10px; box-shadow: var(--shadow); padding: 10px 12px; max-width: min(430px, 60%); font-size: 12px; z-index: 12; }
.legend-title { color: var(--text-muted); margin-bottom: 6px; }
.legend-row { display: flex; flex-wrap: wrap; gap: 4px 10px; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; color: var(--text-soft); background: transparent; border: 0; padding: 2px 4px; cursor: pointer; font-size: 12px; border-radius: 4px; }
.legend-item:hover { color: var(--accent); }
.legend-item.off { opacity: 0.35; }
.legend-item b { color: var(--accent); font-weight: 600; }
.tip { position: absolute; z-index: 16; pointer-events: none; background: #ffffff; border: 1px solid var(--border); border-radius: 8px; box-shadow: var(--shadow); padding: 6px 10px; font-size: 12px; display: flex; flex-direction: column; gap: 1px; max-width: 280px; }
.tip span { color: var(--text-muted); }
.side { position: absolute; right: 0; top: 0; bottom: 0; width: 360px; max-width: 45%; background: var(--surface); border-left: 1px solid var(--border); z-index: 15; overflow-y: auto; padding: 16px; }
.side-close { position: absolute; right: 10px; top: 10px; background: transparent; border: 0; color: var(--text-muted); cursor: pointer; }
.side-close:hover { color: var(--danger); }
.side h2 { font-size: 16px; margin: 0 24px 6px 0; overflow-wrap: anywhere; }
.side .sub { color: var(--text-muted); font-size: 12px; margin-bottom: 10px; display: flex; flex-wrap: wrap; gap: 4px; }
.tag { display: inline-block; background: var(--surface-3); border: 1px solid var(--border); border-radius: 6px; padding: 2px 8px; font-size: 12px; color: var(--text); }
.tag-wrap { display: flex; flex-wrap: wrap; gap: 4px; }
.grp { margin-top: 12px; }
.grp h3 { font-size: 13px; color: var(--accent); border-bottom: 1px solid var(--border); padding-bottom: 4px; margin: 0 0 6px; }
.li { display: flex; align-items: center; gap: 7px; width: 100%; background: transparent; border: 0; color: var(--text); font-size: 12.5px; padding: 5px 4px; border-radius: 5px; cursor: pointer; text-align: left; }
.li:hover { background: var(--surface-2); }
.li i { color: var(--text-muted); font-style: normal; margin-left: auto; font-size: 11px; }
.kv { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; padding: 3px 0; color: var(--text-muted); }
.kv b { color: var(--text); font-weight: 500; overflow-wrap: anywhere; text-align: right; }
.zoom-hint { position: absolute; right: 12px; bottom: 12px; display: inline-flex; align-items: center; gap: 6px; background: rgba(255, 255, 255, 0.92); border: 1px solid var(--border); border-radius: 8px; padding: 5px 10px; font-size: 11.5px; color: var(--text-muted); z-index: 11; }
</style>
