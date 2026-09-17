<script setup lang="ts">
/**
 * 站点档案视图：参考《电力拓扑知识库》页面，把 DuckDB 入库的站点档案与
 * 知识问答事实呈现为：站点列表 + 概览统计 + 母线/主变/间隔/连接/质量明细 + 事实检索。
 */
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {IconSearch} from '@tabler/icons-vue'
import {graphApi} from '@/api/agent'
import type {GraphFactRow, GraphStation} from '@/types/agent'

const props = defineProps<{ stations: GraphStation[] }>()
const mainRef = ref<HTMLElement | null>(null)

interface BayAttachment { kind?: string; bay_no?: string | null; line_name?: string | null }
interface BusRow { id?: string; voltage?: string; attached_bays?: BayAttachment[] }
interface TransformerSide { terminal?: number; connects?: string; voltage?: string; bus_group?: string }
interface TransformerRow { id?: string; name?: string; model?: string | null; windings?: number | null; sides?: TransformerSide[] }
interface ChainDevice { type_cn?: string; number?: string | null; labels?: string[] }
interface BayRow { kind?: string; voltage?: string; line_name?: string | null; chain?: ChainDevice[] }
interface ConnectionRow { a_type?: string; a_number?: string | null; b_type?: string; b_number?: string | null; group?: string }
interface StationDoc {
  station_name?: string
  base_voltage?: string
  voltage_levels?: string[]
  summary?: Record<string, unknown>
  buses?: BusRow[]
  transformers?: TransformerRow[]
  bays?: BayRow[]
  direct_connections?: ConnectionRow[]
  quality?: Record<string, unknown>
}

const DEVICE_CN: Record<string, string> = {
  pt: '电压互感器', ct: '电流互感器', breaker: '断路器', disconnector: '隔离开关',
  ground_disconnector: '接地刀闸', capacitor: '电容器', power_transformer: '主变压器',
  fuse: '熔断器', surge_arrester: '避雷器', station_transformer: '站用变压器',
}
const KIND_CN: Record<string, string> = {
  feeder: '出线间隔', incoming: '电源进线', bus_tie: '母联/分段', aux: '辅助间隔',
}
const FACT_CN: Record<string, string> = {
  station_overview: '站点概览', bus: '母线', transformer: '主变', feeder_bay: '出线间隔',
  incoming_bay: '进线间隔', bus_tie: '母联/分段', aux_bay: '辅助间隔',
  connection: '连接关系', device: '设备',
}

const activeStation = ref('')
const doc = ref<StationDoc | null>(null)
const docError = ref('')
const docLoading = ref(false)

const factQuery = ref('')
const factCategory = ref('')
const factRows = ref<GraphFactRow[]>([])
const factTotal = ref(0)
const factOffset = ref(0)
const factCategories = ref<{ category: string; count: number }[]>([])
const factLoading = ref(false)
const FACT_PAGE = 40

const summaryStats = computed(() => {
  const summary = doc.value?.summary ?? {}
  const entries: [string, string][] = [
    ['bus_count', '母线'],
    ['transformer_count', '主变压器'],
    ['feeder_count', '出线间隔'],
    ['incoming_count', '电源进线'],
    ['bus_tie_count', '母联/分段'],
    ['aux_bay_count', '辅助间隔'],
  ]
  return entries.map(([key, label]) => ({label, value: Number(summary[key] ?? 0)}))
})

const deviceCounts = computed(() => {
  const counts = doc.value?.summary?.device_counts
  if (!counts || typeof counts !== 'object') return []
  return Object.entries(counts as Record<string, unknown>)
      .filter(([, value]) => Number(value) > 0)
      .map(([key, value]) => ({
        label: DEVICE_CN[key] ?? key,
        value: Number(value),
      }))
})

const qualityItems = computed(() => {
  const quality = doc.value?.quality ?? {}
  const entries: [string, string, boolean][] = [
    ['total_devices', '设备总数', false],
    ['bound_devices', '已绑定图元', false],
    ['devices_in_bays', '归入间隔', false],
    ['conductor_groups', '连接点组', false],
    ['total_polylines', '图元连线', false],
    ['dangling_refs', '悬挂引用', true],
  ]
  return entries.map(([key, label, warn]) => ({label, value: Number(quality[key] ?? 0), warn}))
})

function bayKinds(bus: BusRow) {
  const counts = new Map<string, number>()
  for (const attachment of bus.attached_bays ?? []) {
    const kind = KIND_CN[String(attachment.kind ?? '')] ?? String(attachment.kind ?? '其他')
    counts.set(kind, (counts.get(kind) ?? 0) + 1)
  }
  return [...counts.entries()].map(([kind, count]) => `${kind}×${count}`).join('、') || '无挂接间隔'
}

function chainText(bay: BayRow) {
  return (bay.chain ?? [])
      .map(device => [device.type_cn, device.number ?? (device.labels ?? [])[0]].filter(Boolean).join(' '))
      .filter(Boolean)
      .join(' → ') || '（空链）'
}

async function loadStation(name: string) {
  activeStation.value = name
  doc.value = null
  docError.value = ''
  await nextTick()
  mainRef.value?.scrollTo({top: 0})
  if (!name) return
  docLoading.value = true
  try {
    doc.value = await graphApi.stationDocument(name) as StationDoc
  } catch (cause) {
    docError.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    docLoading.value = false
  }
}

async function loadFacts(append: boolean) {
  factLoading.value = true
  try {
    const result = await graphApi.facts({
      station: activeStation.value || undefined,
      category: factCategory.value || undefined,
      q: factQuery.value.trim() || undefined,
      limit: FACT_PAGE,
      offset: append ? factOffset.value : 0,
    })
    factRows.value = append ? [...factRows.value, ...result.rows] : result.rows
    factTotal.value = result.total
    factOffset.value = factRows.value.length
    factCategories.value = result.categories
  } finally {
    factLoading.value = false
  }
}

function searchFacts() {
  factOffset.value = 0
  void loadFacts(false)
}

function toggleCategory(category: string) {
  factCategory.value = factCategory.value === category ? '' : category
  searchFacts()
}

watch(() => props.stations, stations => {
  if (!activeStation.value && stations.length) {
    void loadStation(stations[0].name)
  }
}, {immediate: true})

onMounted(() => {
  void loadFacts(false)
})
</script>

<template>
  <div class="board">
    <aside class="board-side">
      <div class="side-head">变电站（{{ stations.length }}）</div>
      <button v-for="station in stations" :key="station.name" type="button"
              class="st-item" :class="{active: station.name === activeStation}"
              @click="loadStation(station.name)">
        <span class="nm">{{ station.name }}</span>
        <span class="chips">
          <i v-if="station.baseVoltage">{{ station.baseVoltage }}</i>
          <i v-if="station.summary?.bus_count != null">母线{{ station.summary.bus_count }}</i>
          <i v-if="station.summary?.transformer_count != null">主变{{ station.summary.transformer_count }}</i>
          <i v-if="station.entities != null">实体{{ station.entities }}</i>
        </span>
      </button>
    </aside>

    <main ref="mainRef" class="board-main">
      <div v-if="docLoading" class="board-tip">站点档案加载中…</div>
      <div v-else-if="docError" class="board-tip warn">{{ docError }}</div>
      <div v-else-if="doc" class="board-body">
        <div class="h2">
          {{ doc.station_name }}
          <i v-for="level in doc.voltage_levels ?? []" :key="level">{{ level }}</i>
        </div>

        <div class="statgrid">
          <div v-for="stat in summaryStats" :key="stat.label" class="stat">
            <b>{{ stat.value }}</b><span>{{ stat.label }}</span>
          </div>
        </div>

        <div v-if="deviceCounts.length" class="cardgrid">
          <div v-for="item in deviceCounts" :key="item.label" class="card">
            <b>{{ item.value }}</b><span>{{ item.label }}</span>
          </div>
        </div>

        <div class="h3">母线（{{ doc.buses?.length ?? 0 }}）</div>
        <div class="rows">
          <div v-for="bus in doc.buses ?? []" :key="bus.id" class="row">
            <b>{{ bus.voltage }}母线</b><span class="muted">图元 {{ bus.id }}</span>
            <span class="muted">{{ bayKinds(bus) }}</span>
          </div>
        </div>

        <div class="h3">主变压器（{{ doc.transformers?.length ?? 0 }}）</div>
        <div class="rows">
          <div v-for="transformer in doc.transformers ?? []" :key="transformer.id" class="row">
            <b>{{ transformer.name || `主变 ${transformer.id}` }}</b>
            <span class="muted">{{ transformer.model || '型号未知' }}</span>
            <span class="muted">{{ (transformer.sides ?? []).map(side => side.voltage).filter(Boolean).join(' / ') }}</span>
          </div>
        </div>

        <div class="h3">间隔（{{ doc.bays?.length ?? 0 }}）</div>
        <div class="rows">
          <div v-for="(bay, index) in doc.bays ?? []" :key="index" class="row">
            <b>{{ KIND_CN[bay.kind ?? ''] ?? bay.kind }}<template v-if="bay.line_name"> · {{ bay.line_name }}</template></b>
            <span class="muted">{{ bay.voltage }}</span>
            <span class="chain">{{ chainText(bay) }}</span>
          </div>
        </div>

        <div class="h3">直连关系（{{ doc.direct_connections?.length ?? 0 }}）</div>
        <div class="rows">
          <div v-for="(connection, index) in doc.direct_connections ?? []" :key="index" class="row">
            <b>{{ connection.a_type }}{{ connection.a_number ? ' ' + connection.a_number : '' }}
              <em>—</em> {{ connection.b_type }}{{ connection.b_number ? ' ' + connection.b_number : '' }}</b>
            <span class="muted">组 {{ connection.group }}</span>
          </div>
        </div>

        <div class="h3">数据质量</div>
        <div class="cardgrid">
          <div v-for="item in qualityItems" :key="item.label" class="card" :class="{warn: item.warn && item.value > 0}">
            <b>{{ item.value }}</b><span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <section class="facts">
        <div class="h3 facts-head">
          知识问答事实
          <span class="muted">共 {{ factTotal }} 条</span>
        </div>
        <div class="fact-bar">
          <div class="fact-search">
            <IconSearch :size="15"/>
            <input v-model="factQuery" type="search" placeholder="检索事实文本（如：藏木变 主变）"
                   @keydown.enter.prevent="searchFacts" @input="searchFacts"/>
          </div>
          <div class="fact-cats">
            <button v-for="item in factCategories" :key="item.category" type="button"
                    class="cat" :class="{on: factCategory === item.category}"
                    @click="toggleCategory(item.category)">
              {{ FACT_CN[item.category] ?? item.category }} {{ item.count }}
            </button>
          </div>
        </div>
        <div class="fact-list">
          <div v-for="row in factRows" :key="row.seq" class="fact">
            <span class="fact-station">{{ row.station }}</span>
            <span class="fact-cat">{{ FACT_CN[row.category] ?? row.category }}</span>
            <span class="fact-text">{{ row.text }}</span>
          </div>
          <div v-if="!factRows.length && !factLoading" class="board-tip">没有匹配的事实，请调整关键词或筛选。</div>
        </div>
        <button v-if="factRows.length < factTotal" type="button" class="more" :disabled="factLoading"
                @click="loadFacts(true)">
          {{ factLoading ? '加载中…' : `加载更多（${factTotal - factRows.length} 条未显示）` }}
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* 站点档案视图：与全站一致的亮色主题（白面板 / 绿色强调 / 浅色卡片）。 */
.board { display: grid; grid-template-columns: 264px minmax(0, 1fr); height: 100%; min-height: 0; }
.board-side { border-right: 1px solid var(--border); background: var(--surface-2); overflow-y: auto; padding: 10px 8px; }
.side-head { font-size: 12px; color: var(--text-muted); padding: 4px 8px 8px; }
.st-item { display: flex; flex-direction: column; gap: 4px; width: 100%; text-align: left; background: transparent; border: 1px solid transparent; border-radius: 8px; color: var(--text); padding: 8px 10px; cursor: pointer; }
.st-item:hover { background: var(--surface-3); }
.st-item.active { background: var(--accent-soft); border-color: var(--accent); }
.st-item .nm { font-size: 13px; font-weight: 600; }
.st-item .chips { display: flex; flex-wrap: wrap; gap: 4px; }
.st-item .chips i { font-style: normal; font-size: 11px; color: var(--text-muted); background: var(--surface); border: 1px solid var(--border); border-radius: 4px; padding: 1px 6px; }
.board-main { overflow-y: auto; padding: 16px 18px 26px; min-height: 0; }
.board-tip { color: var(--text-muted); padding: 24px 4px; }
.board-tip.warn { color: var(--warning); }
.h2 { font-size: 17px; font-weight: 700; display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.h2 i { font-style: normal; font-size: 12px; font-weight: 500; color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent); border-radius: 5px; padding: 1px 7px; }
.h3 { font-size: 13.5px; color: var(--accent); margin: 18px 0 8px; border-bottom: 1px solid var(--border); padding-bottom: 5px; }
.statgrid { display: grid; grid-template-columns: repeat(auto-fit, minmax(108px, 1fr)); gap: 8px; }
.stat { background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.stat b { font-size: 21px; color: var(--accent); }
.stat span { font-size: 12px; color: var(--text-muted); }
.cardgrid { display: grid; grid-template-columns: repeat(auto-fit, minmax(128px, 1fr)); gap: 8px; margin-top: 8px; }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 9px 12px; display: flex; align-items: baseline; gap: 7px; }
.card b { font-size: 16px; color: var(--accent); }
.card span { font-size: 12px; color: var(--text-muted); }
.card.warn b { color: var(--danger); }
.rows { display: flex; flex-direction: column; gap: 6px; }
.row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; padding: 7px 12px; font-size: 12.5px; }
.row b { font-size: 12.5px; font-weight: 600; color: var(--text); }
.row b em { font-style: normal; color: var(--accent); padding: 0 4px; }
.muted { color: var(--text-muted); }
.chain { color: var(--text-soft); overflow-wrap: anywhere; }
.facts { margin-top: 6px; }
.facts-head { display: flex; align-items: baseline; gap: 8px; }
.fact-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.fact-search { display: flex; align-items: center; gap: 6px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; padding: 5px 10px; color: var(--text-muted); }
.fact-search input { background: transparent; border: 0; outline: none; color: var(--text); width: 240px; font-size: 13px; }
.fact-search:focus-within { border-color: var(--accent); }
.fact-cats { display: flex; flex-wrap: wrap; gap: 5px; }
.cat { background: var(--surface-2); border: 1px solid var(--border); color: var(--text-soft); border-radius: 999px; padding: 3px 11px; font-size: 12px; cursor: pointer; }
.cat:hover { color: var(--accent); }
.cat.on { background: var(--accent); border-color: var(--accent); color: #fff; }
.fact-list { display: flex; flex-direction: column; gap: 5px; }
.fact { display: flex; gap: 8px; align-items: baseline; background: var(--surface-2); border: 1px solid var(--border); border-radius: 7px; padding: 6px 10px; font-size: 12.5px; }
.fact-station { flex: none; color: var(--accent); font-size: 11.5px; }
.fact-cat { flex: none; color: var(--text-soft); font-size: 11.5px; background: var(--surface); border: 1px solid var(--border); border-radius: 4px; padding: 0 5px; }
.fact-text { color: var(--text); overflow-wrap: anywhere; }
.more { margin-top: 10px; width: 100%; background: var(--surface-2); border: 1px solid var(--border); color: var(--text-soft); border-radius: 8px; padding: 8px; font-size: 12.5px; cursor: pointer; }
.more:hover:not(:disabled) { color: var(--accent); border-color: var(--accent); }
</style>
