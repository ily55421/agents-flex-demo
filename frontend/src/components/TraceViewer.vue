<script setup lang="ts">
import {computed, ref} from 'vue'
import {
  IconChevronDown,
  IconChevronRight,
  IconGitBranch,
  IconTimeline,
} from '@tabler/icons-vue'
import type {AgentEvent, TraceSpan, TraceView} from '@/types/agent'

const props = defineProps<{ trace: TraceView | null }>()
const expanded = ref<Set<string>>(new Set())

/** 读取当前 Trace 的全部 OTel Span；尚未导出时返回空数组。 */
const spans = computed(() => props.trace?.spans ?? [])
/** 建立本地 Span ID 集合，用于判断 parentSpanId 是否指向已知父节点。 */
const spanIds = computed(() => new Set(spans.value.map((span) => span.spanId)))
/** 找出无父节点或父节点不在当前快照中的根 Span，作为树形渲染入口。 */
const roots = computed(() => spans.value.filter((span) =>
    !span.parentSpanId || !spanIds.value.has(span.parentSpanId),
))
/** 筛选关键生命周期事件作为补充时间线，避免把 AgentEvent 伪装为 OTel Span。 */
const lifecycleEvents = computed(() => (props.trace?.events ?? []).filter((event) => [
  'TURN_SUSPENDED', 'TURN_RESUMED', 'TOOL_APPROVAL_REQUESTED',
  'CONTEXT_COMPRESSION_COMPLETED', 'RETRY_SCHEDULED', 'TURN_COMPLETED',
  'TURN_FAILED', 'BUDGET_EXCEEDED',
].includes(event.type)))
/** 从 OTel 指标中读取模型请求总数。 */
const modelRequests = computed(() => metricValue('agentsflex.gen_ai.request.count'))
/** 从 OTel 指标中读取工具调用总数。 */
const toolCalls = computed(() => metricValue('agentsflex.gen_ai.tool.call.count'))
/** 从 OTel 指标中读取工具失败总数。 */
const toolErrors = computed(() => metricValue('agentsflex.gen_ai.tool.call.error.count'))

/**
 * 查找指定 Span 的直接子节点，保持后端按开始时间导出的顺序。
 * @param spanId 父 Span ID
 */
function children(spanId: string) {
  return spans.value.filter((span) => span.parentSpanId === spanId)
}

/**
 * 按指标名称读取统一 value 字段，指标尚未导出时返回 0。
 * @param name OTel metric 名称
 */
function metricValue(name: string) {
  return props.trace?.metrics.find((metric) => metric.name === name)?.value ?? 0
}

/**
 * 切换 Span 详情展开状态，并创建新 Set 触发 Vue 响应式更新。
 * @param id Span ID
 */
function toggle(id: string) {
  const next = new Set(expanded.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expanded.value = next
}

/**
 * 将毫秒耗时格式化为 ms 或保留两位小数的秒数。
 * @param value 毫秒耗时
 */
function duration(value: number) {
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`
}

/**
 * 生成 Span 的次要信息：工具名优先，其次是供应商/模型，最后回退 SpanKind。
 * @param span OTel Span 投影
 */
function spanMeta(span: TraceSpan) {
  if (span.toolName) return span.toolName
  if (span.model) return `${span.provider ?? 'model'} / ${span.model}`
  return span.kind
}

/**
 * 将生命周期事件类型中的下划线替换为空格，保持原始大写语义。
 * @param type AgentEventType 名称
 */
function title(type: string) {
  return type.replaceAll('_', ' ')
}

/**
 * 计算事件相对本次 Trace 第一条事件的偏移时间。
 * @param event 当前生命周期事件
 */
function elapsed(event: AgentEvent) {
  const first = props.trace?.events[0]?.occurredAt ?? event.occurredAt
  return Math.max(0, event.occurredAt - first)
}
</script>

<template>
  <section class="surface trace-viewer" aria-labelledby="trace-heading">
    <div class="trace-header">
      <div class="section-heading compact-heading">
        <IconTimeline :size="19" :stroke-width="1.8"/>
        <h2 id="trace-heading">Execution Trace</h2>
      </div>
      <div class="trace-meta">
        <div v-if="trace" class="trace-stats">
          <span><b>{{ trace.totalTokens.toLocaleString() }}</b> tokens</span>
          <span><b>{{ duration(trace.durationMs) }}</b> wall clock</span>
        </div>
        <div class="projection-label">
          <IconGitBranch :size="14"/>
          Agents-Flex OpenTelemetry
        </div>
      </div>
    </div>

    <div v-if="trace" class="otel-metrics" aria-label="OpenTelemetry metrics">
      <div><span>MODEL REQUESTS</span><b>{{ modelRequests }}</b></div>
      <div><span>TOOL CALLS</span><b>{{ toolCalls }}</b></div>
      <div><span>TOOL ERRORS</span><b>{{ toolErrors }}</b></div>
      <div><span>EXPORTED SPANS</span><b>{{ spans.length }}</b></div>
    </div>

    <div class="trace-list span-tree">
      <div v-if="!roots.length" class="trace-empty">等待 Agents-Flex 导出第一个 OpenTelemetry Span</div>
      <template v-for="root in roots" :key="root.spanId">
        <article class="trace-row span-row" :data-status="root.status">
          <button type="button" :aria-expanded="expanded.has(root.spanId)" @click="toggle(root.spanId)">
            <IconChevronDown v-if="expanded.has(root.spanId)" :size="16"/>
            <IconChevronRight v-else :size="16"/>
            <span class="trace-type">{{ root.name }}</span>
            <code>{{ spanMeta(root) }}</code>
            <time>{{ duration(root.durationMs) }}</time>
          </button>
          <div v-if="expanded.has(root.spanId)" class="trace-detail">
            <dl>
              <div>
                <dt>Trace ID</dt>
                <dd>{{ root.traceId }}</dd>
              </div>
              <div>
                <dt>Span ID</dt>
                <dd>{{ root.spanId }}</dd>
              </div>
              <div>
                <dt>Parent</dt>
                <dd>{{ root.parentSpanId || 'ROOT' }}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{{ root.status }}</dd>
              </div>
              <div v-if="root.model">
                <dt>Model</dt>
                <dd>{{ root.model }}</dd>
              </div>
              <div v-if="root.inputTokens !== undefined">
                <dt>Tokens</dt>
                <dd>{{ root.inputTokens }} in / {{ root.outputTokens ?? 0 }} out</dd>
              </div>
              <div v-if="root.arguments">
                <dt>Arguments</dt>
                <dd>{{ root.arguments }}</dd>
              </div>
              <div v-if="root.result">
                <dt>Result</dt>
                <dd>{{ root.result }}</dd>
              </div>
            </dl>
            <pre>{{ JSON.stringify(root.attributes, null, 2) }}</pre>
          </div>
        </article>
        <article v-for="child in children(root.spanId)" :key="child.spanId" class="trace-row span-row child-span"
                 :data-status="child.status">
          <button type="button" :aria-expanded="expanded.has(child.spanId)" @click="toggle(child.spanId)">
            <IconChevronDown v-if="expanded.has(child.spanId)" :size="16"/>
            <IconChevronRight v-else :size="16"/>
            <span class="trace-type">{{ child.name }}</span>
            <code>{{ spanMeta(child) }}</code>
            <time>{{ duration(child.durationMs) }}</time>
          </button>
          <div v-if="expanded.has(child.spanId)" class="trace-detail">
            <dl>
              <div>
                <dt>Trace ID</dt>
                <dd>{{ child.traceId }}</dd>
              </div>
              <div>
                <dt>Span ID</dt>
                <dd>{{ child.spanId }}</dd>
              </div>
              <div>
                <dt>Parent</dt>
                <dd>{{ child.parentSpanId }}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{{ child.status }}</dd>
              </div>
              <div v-if="child.model">
                <dt>Model</dt>
                <dd>{{ child.model }}</dd>
              </div>
              <div v-if="child.inputTokens !== undefined">
                <dt>Tokens</dt>
                <dd>{{ child.inputTokens }} in / {{ child.outputTokens ?? 0 }} out</dd>
              </div>
              <div v-if="child.arguments">
                <dt>Arguments</dt>
                <dd>{{ child.arguments }}</dd>
              </div>
              <div v-if="child.result">
                <dt>Result</dt>
                <dd>{{ child.result }}</dd>
              </div>
              <div v-if="child.output">
                <dt>Output</dt>
                <dd>{{ child.output }}</dd>
              </div>
            </dl>
            <pre>{{ JSON.stringify(child.attributes, null, 2) }}</pre>
          </div>
        </article>
      </template>
    </div>

    <details class="lifecycle-trace">
      <summary>Lifecycle events <span>{{ lifecycleEvents.length }} · AGENTS_FLEX_NATIVE</span></summary>
      <div class="lifecycle-list">
        <div v-for="event in lifecycleEvents" :key="event.eventId">
          <code>{{ title(event.type) }}</code>
          <span>{{ event.data.toolName ?? event.data.phase ?? 'RUNTIME' }}</span>
          <time>+{{ elapsed(event) }} ms</time>
        </div>
      </div>
    </details>
  </section>
</template>
