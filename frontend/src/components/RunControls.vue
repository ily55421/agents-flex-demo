<script setup lang="ts">
import {computed} from 'vue'
import {
  IconPlayerPlay,
  IconPlayerPause,
  IconPlayerTrackNext,
  IconPlus,
  IconRefresh,
  IconSquare,
} from '@tabler/icons-vue'
import type {AgentRun} from '@/types/agent'

const props = defineProps<{ run: AgentRun; busy: boolean }>()
const emit = defineEmits<{
  start: []
  suspend: []
  resume: []
  cancel: []
  refresh: []
  reset: []
}>()

/** 仅 READY Snapshot 允许发送启动命令。 */
const canStart = computed(() => props.run.status === 'READY')
/** RUNNING/READY 且没有暂停状态时允许提出挂起请求。 */
const canSuspend = computed(() =>
    !props.run.manualPause && !props.run.pauseRequested
    && ['READY', 'RUNNING'].includes(props.run.status),
)
/** 只有已落实的手工暂停可以恢复，普通表单 Suspension 不显示恢复命令。 */
const canResume = computed(() => props.run.manualPause)
/** 任何非终态 Run 都允许取消。 */
const canCancel = computed(() => !['COMPLETED', 'FAILED', 'CANCELLED', 'BUDGET_EXCEEDED'].includes(props.run.status))
/** 完整终态集合决定是否显示“新建 Run”入口。 */
const isTerminal = computed(() => ['COMPLETED', 'FAILED', 'CANCELLED', 'MAX_ITERATIONS_REACHED', 'MAX_STEPS_REACHED', 'BUDGET_EXCEEDED'].includes(props.run.status))
</script>

<template>
  <section class="surface run-controls" aria-labelledby="controls-heading">
    <div class="section-heading compact-heading">
      <h2 id="controls-heading">运行控制</h2>
      <span class="native-label">下一个安全检查点</span>
    </div>
    <div class="control-grid">
      <button class="primary-button" type="button" :disabled="busy || !canStart" @click="emit('start')">
        <IconPlayerPlay :size="17" :stroke-width="2"/>
        启动
      </button>
      <button class="secondary-button" type="button" :disabled="busy || !canSuspend" @click="emit('suspend')">
        <IconPlayerPause :size="17" :stroke-width="2"/>
        {{ run.pauseRequested ? '请求中' : run.processing ? '请求挂起' : '挂起' }}
      </button>
      <button class="secondary-button" type="button" :disabled="busy || !canResume" @click="emit('resume')">
        <IconPlayerTrackNext :size="17" :stroke-width="2"/>
        恢复
      </button>
      <button class="danger-button" type="button" :disabled="busy || !canCancel" @click="emit('cancel')">
        <IconSquare :size="16" :stroke-width="2"/>
        取消
      </button>
    </div>
    <div class="control-links">
      <button class="text-button" type="button" :disabled="busy" @click="emit('refresh')">
        <IconRefresh :size="15" :stroke-width="1.8"/>
        从 Snapshot 刷新
      </button>
      <button v-if="isTerminal" class="text-button" type="button" :disabled="busy" @click="emit('reset')">
        <IconPlus :size="15" :stroke-width="1.8"/>
        新建 Run
      </button>
    </div>
  </section>
</template>
