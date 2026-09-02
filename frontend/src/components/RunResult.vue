<script setup lang="ts">
import {computed} from 'vue'
import { IconCircleCheck, IconExclamationCircle } from '@tabler/icons-vue'
import type { AgentRun } from '@/types/agent'

const props = defineProps<{ run: AgentRun }>()

/** 将终态中的自动重试次数翻译为诊断提示，帮助区分未重试与重试耗尽。 */
const retrySummary = computed(() => {
  if (props.run.status === 'COMPLETED' || props.run.retryCount <= 0) return ''
  return `已自动重试 ${props.run.retryCount} 次（上限 ${props.run.maxRetries} 次）后仍未恢复`
})
</script>

<template>
  <section class="result-panel" :class="run.status === 'COMPLETED' ? 'success' : 'failed'" aria-live="polite">
    <IconCircleCheck v-if="run.status === 'COMPLETED'" :size="25" :stroke-width="1.8" />
    <IconExclamationCircle v-else :size="25" :stroke-width="1.8" />
    <div>
      <strong>{{ run.status === 'COMPLETED' ? 'Agent Run 已完成' : run.status }}</strong>
      <p>{{ run.finalOutput || run.error || run.budgetExceededReason }}</p>
      <small v-if="retrySummary" class="result-retry-summary">{{ retrySummary }}</small>
    </div>
  </section>
</template>
