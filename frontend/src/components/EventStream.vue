<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {IconActivity, IconChevronDown, IconChevronRight, IconFilter, IconPlayerPlay} from '@tabler/icons-vue'
import type {AgentEvent} from '@/types/agent'

const props = defineProps<{ events: AgentEvent[] }>()
const streamElement = ref<HTMLElement | null>(null)
const filter = ref<'all' | 'model' | 'tool' | 'control'>('all')
const expandedId = ref<string | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(0)
const followingLatest = ref(true)
const ROW_HEIGHT = 62
const OVERSCAN_ROWS = 8
let scrollFrame: number | null = null
let resizeObserver: ResizeObserver | null = null

/**
 * 根据模型、工具或控制类别过滤完整原生事件历史，不做数量截断。
 * 实际 DOM 数量由下方虚拟窗口控制，长对话仍可访问任意一条 Token Delta。
 */
const filteredEvents = computed(() => props.events.filter((event) => {
  if (filter.value === 'all') return true
  if (filter.value === 'model') return event.type.startsWith('MODEL_')
  if (filter.value === 'tool') return event.type.startsWith('TOOL_') || event.type.startsWith('RETRY_')
  return event.type.startsWith('TURN_') || event.type === 'BUDGET_EXCEEDED' || event.type.startsWith('CONTEXT_')
}))

/** 根据滚动位置计算需要挂载到 DOM 的第一条事件索引，并预留少量上方缓冲。 */
const visibleStart = computed(() => Math.max(0,
    Math.floor(scrollTop.value / ROW_HEIGHT) - OVERSCAN_ROWS,
))
/** 根据视口高度计算虚拟窗口末尾索引，并预留少量下方缓冲。 */
const visibleEnd = computed(() => Math.min(filteredEvents.value.length,
    Math.ceil((scrollTop.value + viewportHeight.value) / ROW_HEIGHT) + OVERSCAN_ROWS,
))
/** 返回当前虚拟窗口内真正需要 Vue 渲染的事件。 */
const visibleEvents = computed(() => filteredEvents.value.slice(visibleStart.value, visibleEnd.value))
/** 计算虚拟窗口上方占位高度，使滚动条仍代表完整事件历史。 */
const topSpacerHeight = computed(() => visibleStart.value * ROW_HEIGHT)
/** 计算虚拟窗口下方占位高度，使最后一条事件可滚动到正确位置。 */
const bottomSpacerHeight = computed(() =>
    Math.max(0, (filteredEvents.value.length - visibleEnd.value) * ROW_HEIGHT),
)
/** 查找当前展开事件的完整数据，并在列表下方的详情检查器中展示。 */
const selectedEvent = computed(() =>
    props.events.find((event) => event.eventId === expandedId.value) ?? null,
)

/**
 * 事件数量增加后，在用户仍停留于列表底部时自动跟随最新事件。
 * 用户主动向上查看历史后暂停跟随，避免流式 Token 抢走滚动位置。
 */
watch(() => filteredEvents.value.length, async () => {
  if (!followingLatest.value) return
  await nextTick()
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    if (!streamElement.value) return
    streamElement.value.scrollTop = streamElement.value.scrollHeight
    scrollTop.value = streamElement.value.scrollTop
  })
})

/** 安装 ResizeObserver，持续同步事件列表的可视高度供虚拟窗口计算。 */
onMounted(() => {
  if (!streamElement.value) return
  viewportHeight.value = streamElement.value.clientHeight
  resizeObserver = new ResizeObserver(([entry]) => {
    viewportHeight.value = entry?.contentRect.height ?? streamElement.value?.clientHeight ?? 0
  })
  resizeObserver.observe(streamElement.value)
})

/** 组件卸载时取消事件列表尚未执行的滚动帧和尺寸观察器。 */
onBeforeUnmount(() => {
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
  resizeObserver?.disconnect()
})

/**
 * 同步用户滚动位置，并判断是否需要继续自动跟随最新事件。
 * @param event 事件列表原生 scroll 事件
 */
function handleScroll(event: Event) {
  const element = event.currentTarget as HTMLElement
  scrollTop.value = element.scrollTop
  followingLatest.value = element.scrollHeight - element.scrollTop - element.clientHeight < ROW_HEIGHT * 2
}

/**
 * 切换事件类别时重置为自动跟随模式，并让新过滤结果定位到末尾。
 * @param value 用户选择的事件类别
 */
function selectFilter(value: 'all' | 'model' | 'tool' | 'control') {
  filter.value = value
  followingLatest.value = true
  scrollTop.value = 0
  void nextTick(() => {
    if (streamElement.value) streamElement.value.scrollTop = streamElement.value.scrollHeight
  })
}

/**
 * 将大写下划线事件类型转换为便于阅读的标题格式。
 * @param type 原生 AgentEventType 名称
 */
function label(type: string) {
  return type.split('_').map((word) => word[0] + word.slice(1).toLowerCase()).join(' ')
}

/**
 * 根据事件语义选择成功、警告、失败、重试或中性色。
 * @param type 原生 AgentEventType 名称
 */
function tone(type: string) {
  if (type.includes('FAILED') || type.includes('CANCELLED') || type === 'BUDGET_EXCEEDED') return 'danger'
  if (type.includes('APPROVAL') || type.includes('SUSPENDED')) return 'warning'
  if (type.includes('COMPLETED')) return 'success'
  if (type.includes('RETRY')) return 'retry'
  return 'neutral'
}

/**
 * 展开指定事件详情；再次点击同一事件时折叠。
 * @param eventId 全局唯一事件 ID
 */
function toggle(eventId: string) {
  expandedId.value = expandedId.value === eventId ? null : eventId
}

/**
 * 从事件 data 中按工具名、消息、内容、阶段的优先级生成单行摘要。
 * @param event 原生事件投影
 */
function preview(event: AgentEvent) {
  if (event.data.toolName) return String(event.data.toolName)
  if (event.data.message) return String(event.data.message)
  if (event.data.content) return String(event.data.content)
  return `phase=${String(event.data.phase ?? 'n/a')}`
}
</script>

<template>
  <section class="surface event-stream" aria-labelledby="events-heading">
    <div class="event-stream-header">
      <div class="section-heading compact-heading">
        <IconActivity :size="18" :stroke-width="1.9"/>
        <h2 id="events-heading">Agent Event Stream</h2>
      </div>
      <div class="event-count">
        <IconPlayerPlay :size="13" fill="currentColor"/>
        {{ filteredEvents.length }}<span v-if="filter !== 'all'">/{{ events.length }}</span>
      </div>
    </div>
    <div class="filter-row" aria-label="事件过滤器">
      <IconFilter :size="15" :stroke-width="1.8" aria-hidden="true"/>
      <button v-for="item in ['all', 'model', 'tool', 'control'] as const" :key="item" type="button"
              :class="{ active: filter === item }" @click="selectFilter(item)">
        {{ {all: '全部', model: '模型', tool: '工具', control: '控制'}[item] }}
      </button>
    </div>
    <div ref="streamElement" class="event-list" @scroll="handleScroll">
      <div v-if="!filteredEvents.length" class="event-empty">启动 Agent 后显示原生 Runtime 事件</div>
      <div v-if="topSpacerHeight" class="event-spacer" :style="{height: `${topSpacerHeight}px`}" aria-hidden="true"></div>
      <article v-for="event in visibleEvents" :key="event.eventId" class="event-row"
               :class="{ selected: expandedId === event.eventId }" :data-tone="tone(event.type)">
        <div class="event-sequence">{{ String(event.sequence).padStart(2, '0') }}</div>
        <div class="event-body">
          <button class="event-summary" type="button" :aria-expanded="expandedId === event.eventId"
                  :aria-label="`${label(event.type)} 事件详情`" @click="toggle(event.eventId)">
            <IconChevronDown v-if="expandedId === event.eventId" :size="13"/>
            <IconChevronRight v-else :size="13"/>
            <span><strong>{{ label(event.type) }}</strong><small>{{ preview(event) }}</small></span>
            <time>{{ new Date(event.occurredAt).toLocaleTimeString('zh-CN', {hour12: false}) }}</time>
          </button>
        </div>
      </article>
      <div v-if="bottomSpacerHeight" class="event-spacer" :style="{height: `${bottomSpacerHeight}px`}" aria-hidden="true"></div>
    </div>
    <div v-if="selectedEvent" class="event-detail event-inspector">
      <dl>
        <div><dt>Agent</dt><dd>{{ selectedEvent.agentId }}@{{ selectedEvent.agentVersion }}</dd></div>
        <div><dt>Run</dt><dd>{{ selectedEvent.runId }}</dd></div>
        <div><dt>Event</dt><dd>{{ selectedEvent.eventId }}</dd></div>
        <div><dt>Source</dt><dd>{{ selectedEvent.source }}</dd></div>
      </dl>
      <pre>{{ JSON.stringify(selectedEvent.data, null, 2) }}</pre>
    </div>
  </section>
</template>
