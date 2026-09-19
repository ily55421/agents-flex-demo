<script setup lang="ts">
import {computed} from 'vue'
import {IconBolt, IconClockHour4, IconCurrencyDollar, IconTool} from '@tabler/icons-vue'
import type {BudgetView} from '@/types/agent'

const props = defineProps<{ budget: BudgetView; exceededReason: string | null }>()
/** 计算 Token 预算使用率，并限制为进度条支持的 0-100 区间。 */
const tokenPercent = computed(() => Math.min(100, Math.round(props.budget.usedTokens / props.budget.tokenLimit * 100)))
/** 计算工具调用预算使用率，并防止超额时撑破进度语义。 */
const toolPercent = computed(() => Math.min(100, Math.round(props.budget.usedToolCalls / props.budget.toolCallLimit * 100)))
/** 计算墙钟时长预算使用率；未配置时长上限时返回 0。 */
const durationPercent = computed(() => props.budget.durationLimitMs
    ? Math.min(100, Math.round(props.budget.usedDurationMs / props.budget.durationLimitMs * 100))
    : 0)

/**
 * 将毫秒值格式化为紧凑的 ms 或 s 文本。
 * @param value 毫秒时长
 */
function duration(value: number) {
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(value < 10000 ? 1 : 0)} s`
}
</script>

<template>
  <section class="surface metric-panel" aria-labelledby="budget-heading">
    <div class="section-heading compact-heading">
      <IconBolt :size="18" :stroke-width="1.9"/>
      <h2 id="budget-heading">预算控制</h2>
      <span class="native-label">原生</span>
    </div>
    <div class="budget-main">
      <div>
        <span>Token 使用</span>
        <strong>{{ budget.usedTokens.toLocaleString() }} <small>/ {{
            budget.tokenLimit.toLocaleString()
          }}</small></strong>
      </div>
      <b>{{ tokenPercent }}%</b>
    </div>
    <div class="meter" role="progressbar" aria-label="Token 预算使用" :aria-valuenow="tokenPercent" aria-valuemin="0"
         aria-valuemax="100">
      <span :style="{ width: `${tokenPercent}%` }"/>
    </div>
    <div class="metric-grid">
      <div>
        <IconTool :size="16"/>
        <span>工具调用</span><strong>{{ budget.usedToolCalls }} / {{
          budget.toolCallLimit
        }}</strong><small>{{ toolPercent }}%</small></div>
      <div>
        <IconCurrencyDollar :size="16"/>
        <span>估算成本</span><strong>${{ budget.estimatedCost.toFixed(4) }}</strong><small>演示估算</small></div>
    </div>
    <div class="budget-duration">
      <IconClockHour4 :size="15"/>
      <span>执行时间</span>
      <strong>{{ duration(budget.usedDurationMs) }} / {{ duration(budget.durationLimitMs) }}</strong>
      <small>{{ durationPercent }}% · 墙钟时间，包含人工等待</small>
    </div>
    <p v-if="exceededReason" class="inline-error">{{ exceededReason }}</p>
  </section>
</template>
