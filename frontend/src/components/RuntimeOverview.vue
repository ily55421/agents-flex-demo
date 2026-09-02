<script setup lang="ts">
import {computed} from 'vue'
import {
  IconBrain,
  IconCheck,
  IconCircleDashed,
  IconForms,
  IconRoute,
  IconShieldCheck,
  IconTool,
} from '@tabler/icons-vue'
import type {AgentRun} from '@/types/agent'

const props = defineProps<{ run: AgentRun }>()

/** 将框架状态翻译为中文标签；tone 只负责语义色，不改变业务状态。 */
const statusMeta = computed(() => {
  const values: Record<string, { label: string; tone: string }> = {
    READY: {label: '准备就绪', tone: 'neutral'},
    RUNNING: {label: '执行中', tone: 'running'},
    WAITING_FOR_USER: {label: props.run.manualPause ? '已挂起' : '等待表单', tone: 'waiting'},
    WAITING_FOR_APPROVAL: {label: '等待审批', tone: 'warning'},
    RETRY_SCHEDULED: {label: '等待重试', tone: 'retry'},
    COMPLETED: {label: '已完成', tone: 'success'},
    FAILED: {label: '执行失败', tone: 'danger'},
    CANCELLED: {label: '已取消', tone: 'danger'},
    BUDGET_EXCEEDED: {label: '预算终止', tone: 'danger'},
  }
  return values[props.run.status] ?? {label: props.run.status, tone: 'neutral'}
})

/** 由原生事件推导生命周期节点，使浏览器刷新后仍能恢复已完成步骤。 */
const stages = computed(() => [
  {key: 'created', label: '创建', icon: IconRoute, done: true, active: props.run.status === 'READY'},
  {
    key: 'input',
    label: '补充信息',
    icon: IconForms,
    done: props.run.events.some((e) => e.type === 'TURN_RESUMED'),
    active: props.run.status === 'WAITING_FOR_USER' && !props.run.manualPause
  },
  {
    key: 'research',
    label: '研究执行',
    icon: IconTool,
    done: props.run.events.some((e) => e.type === 'TOOL_COMPLETED' && e.data.toolName === 'research_market'),
    active: props.run.status === 'RUNNING' || props.run.status === 'RETRY_SCHEDULED'
  },
  {
    key: 'approval',
    label: '发布审批',
    icon: IconShieldCheck,
    done: props.run.events.some((e) => e.type === 'TURN_RESUMED' && e.data.commandType === 'APPROVE_TOOL'),
    active: props.run.status === 'WAITING_FOR_APPROVAL'
  },
  {
    key: 'done',
    label: '完成',
    icon: IconCheck,
    done: props.run.status === 'COMPLETED',
    active: props.run.status === 'COMPLETED'
  },
])
</script>

<template>
  <section class="surface runtime-overview" aria-labelledby="runtime-heading">
    <div class="runtime-titlebar">
      <div>
        <div class="section-heading compact-heading">
          <IconBrain :size="19" :stroke-width="1.8" aria-hidden="true"/>
          <h2 id="runtime-heading">Agent Execution</h2>
        </div>
        <p class="run-task">{{ run.task }}</p>
      </div>
      <div class="status-block" :data-tone="statusMeta.tone" role="status" aria-live="polite">
        <IconCircleDashed v-if="run.processing" class="spin" :size="17" :stroke-width="2"/>
        <span>{{ statusMeta.label }}</span>
      </div>
    </div>

    <div class="runtime-identifiers">
      <div><span>PHASE</span><code>{{ run.phase }}</code></div>
      <div><span>AGENT</span><code>{{ run.agentId }}</code></div>
      <div><span>VERSION</span><code>{{ run.agentVersion }}</code></div>
      <div><span>STEP</span><code>{{ run.stepCount }} / {{ run.maxSteps }}</code></div>
    </div>

    <ol class="execution-rail" aria-label="执行生命周期">
      <li v-for="stage in stages" :key="stage.key" :class="{ active: stage.active, done: stage.done }">
        <div class="stage-icon">
          <component :is="stage.icon" :size="17" :stroke-width="1.9"/>
        </div>
        <span>{{ stage.label }}</span>
      </li>
    </ol>

    <div class="pending-strip" :class="{ empty: !run.pendingTask }">
      <span>当前任务</span>
      <strong>{{ run.pendingTask || run.finalOutput || '没有待处理任务' }}</strong>
    </div>
  </section>
</template>
