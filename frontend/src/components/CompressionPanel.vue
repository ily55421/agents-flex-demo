<script setup lang="ts">
import {computed} from 'vue'
import {IconArrowsMinimize, IconChevronRight} from '@tabler/icons-vue'
import type {CompressionView} from '@/types/agent'

const props = defineProps<{ compression: CompressionView }>()
/** 将压缩内部状态翻译为中文生命周期标签。 */
const statusLabel = computed(() => ({
  PENDING: '等待阈值',
  RUNNING: '压缩中',
  COMPLETED: '已完成',
  SKIPPED: '未触发',
  FAILED: '失败',
})[props.compression.status])
/** 把 Agents-Flex 压缩 Decider 枚举转换为紧凑的中文触发说明。 */
const triggerLabel = computed(() => ({
  PENDING_MESSAGES: '消息阈值',
  PENDING_TURNS: 'Turn 阈值',
  PENDING_TOKENS: 'Token 阈值',
  ALWAYS: '始终触发',
  NEVER: '从不触发',
})[props.compression.trigger])
/** 未生成摘要时根据 Decider 给出准确状态，NEVER 不再误写成“等待阈值”。 */
const emptySummary = computed(() => props.compression.trigger === 'NEVER'
  ? '当前 Agent 已配置为不触发语义压缩'
  : '等待上下文达到压缩条件')

/**
 * 将毫秒时间戳格式化为 24 小时制本地时间，未发生的节点显示占位符。
 * @param value Unix 毫秒时间戳
 */
function clock(value: number) {
  return value ? new Date(value).toLocaleTimeString('zh-CN', {hour12: false}) : '--'
}
</script>

<template>
  <section class="surface metric-panel" aria-labelledby="compression-heading">
    <div class="section-heading compact-heading">
      <IconArrowsMinimize :size="18" :stroke-width="1.9"/>
      <h2 id="compression-heading">Message Compression</h2>
      <span class="native-label">{{ statusLabel }}</span>
    </div>
    <div class="compression-lifecycle" :data-status="compression.status">
      <b>{{ triggerLabel }}</b>
      <span>{{ compression.condition }}</span>
      <time>{{ clock(compression.startedAt) }} → {{ clock(compression.completedAt) }}</time>
    </div>
    <div class="compression-flow">
      <div><span>Before</span><strong>{{ compression.beforeMessages }}</strong><small>{{ compression.beforeTokens }}
        estimated tokens</small></div>
      <IconChevronRight :size="20" :stroke-width="1.6" aria-hidden="true"/>
      <div><span>Model Context</span><strong>{{ compression.afterMessages }}</strong><small>{{
          compression.afterTokens
        }} estimated tokens</small></div>
    </div>
    <div class="summary-block">
      <span>Agents-Flex 语义摘要</span>
      <p>{{ compression.summary || emptySummary }}</p>
    </div>
    <details v-if="compression.originalContext.length" class="compression-context">
      <summary>查看压缩前上下文 <span>{{ compression.originalContext.length }} 条</span></summary>
      <ol>
        <li v-for="(message, index) in compression.originalContext" :key="index">{{ message }}</li>
      </ol>
    </details>
  </section>
</template>
