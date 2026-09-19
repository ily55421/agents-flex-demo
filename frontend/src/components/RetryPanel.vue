<script setup lang="ts">
import {computed} from 'vue'
import {IconCircleCheck, IconCircleX, IconRefreshDot} from '@tabler/icons-vue'
import type {AgentEvent, RetryPolicyView} from '@/types/agent'

const props = defineProps<{ events: AgentEvent[]; retryCount: number; maxRetries: number; policy: RetryPolicyView }>()
/** 以 verify_sources 的原生工具事件还原每次尝试，不使用计时器模拟进度。 */
const attempts = computed(() => {
  const failed = props.events.filter((event) => event.type === 'TOOL_FAILED' && event.data.toolName === 'verify_sources')
  const completed = props.events.find((event) => event.type === 'TOOL_COMPLETED' && event.data.toolName === 'verify_sources')
  return [
    ...failed.map((event, index) => ({
      attempt: index + 1,
      ok: false,
      message: String(event.data.error ?? '工具调用失败')
    })),
    ...(completed ? [{attempt: failed.length + 1, ok: true, message: '来源验证完成'}] : []),
  ]
})
/** 提取最近一次 verify_sources 失败原因，供面板底部展示诊断信息。 */
const lastError = computed(() => {
  const failures = props.events.filter((event) => event.type === 'TOOL_FAILED' && event.data.toolName === 'verify_sources')
  return failures.length ? String(failures[failures.length - 1]?.data.error ?? '') : null
})
</script>

<template>
  <section class="surface retry-panel" aria-labelledby="retry-heading">
    <div class="section-heading compact-heading">
      <IconRefreshDot :size="18" :stroke-width="1.9"/>
      <h2 id="retry-heading">持久重试</h2>
      <span class="native-label">{{ retryCount }} / {{ maxRetries }}</span>
    </div>
    <div class="retry-policy">
      <span>首次间隔 <b>{{ policy.initialDelayMillis }} ms</b></span>
      <span>最大间隔 <b>{{ policy.maxDelayMillis }} ms</b></span>
      <span>退避倍率 <b>{{ policy.multiplier }}x</b></span>
    </div>
    <div v-if="attempts.length" class="attempt-list">
      <div v-for="item in attempts" :key="item.attempt" :class="item.ok ? 'ok' : 'failed'">
        <IconCircleCheck v-if="item.ok" :size="17" :stroke-width="2"/>
        <IconCircleX v-else :size="17" :stroke-width="2"/>
        <span>第 {{ item.attempt }} 次</span>
        <small>{{ item.message }}</small>
      </div>
    </div>
    <div v-else class="metric-empty">工具执行后将在这里显示持久化重试</div>
    <p v-if="lastError" class="retry-last-error"><span>最近错误</span>{{ lastError }}</p>
  </section>
</template>
