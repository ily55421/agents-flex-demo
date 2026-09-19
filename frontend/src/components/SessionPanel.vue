<script setup lang="ts">
import {IconMessagePlus, IconMessages} from '@tabler/icons-vue'
import type {SessionSummary} from '@/types/agent'

const props = defineProps<{
  sessions: SessionSummary[]
  currentConversationId: string | null
  busy: boolean
}>()
const emit = defineEmits<{ newSession: []; open: [runId: string] }>()

/** 运行状态中文标签：会话列表只展示精简映射。 */
const STATUS_LABELS: Record<string, string> = {
  READY: '准备就绪',
  RUNNING: '执行中',
  WAITING_FOR_USER: '等待输入',
  WAITING_FOR_APPROVAL: '等待审批',
  RETRY_SCHEDULED: '等待重试',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消',
  BUDGET_EXCEEDED: '预算终止',
  MAX_ITERATIONS_REACHED: '迭代终止',
  MAX_STEPS_REACHED: '步骤终止',
}

/** 会话徽章语义色：运行中绿色、等待类黄色、失败类红色、其余中性。 */
function toneOf(session: SessionSummary): string {
  if (session.active) return 'running'
  if (session.status === 'COMPLETED') return 'success'
  if (['FAILED', 'CANCELLED', 'BUDGET_EXCEEDED'].includes(session.status)) return 'danger'
  if (['WAITING_FOR_USER', 'WAITING_FOR_APPROVAL'].includes(session.status)) return 'waiting'
  return 'neutral'
}

/** 把时间戳转为紧凑相对时间；跨天时退化为“月-日 时:分”。 */
function formatTime(at: number): string {
  if (!at) return ''
  const delta = Date.now() - at
  if (delta < 60_000) return '刚刚'
  if (delta < 3_600_000) return `${Math.floor(delta / 60_000)} 分钟前`
  if (delta < 86_400_000) return `${Math.floor(delta / 3_600_000)} 小时前`
  const date = new Date(at)
  const clock = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return `${date.getMonth() + 1}-${date.getDate()} ${clock}`
}
</script>

<template>
  <section class="surface session-panel" aria-labelledby="sessions-heading">
    <div class="section-heading">
      <IconMessages :size="18" :stroke-width="1.8" aria-hidden="true"/>
      <h2 id="sessions-heading">会话</h2>
      <button class="primary-button new-session-button" type="button" :disabled="props.busy"
              title="清空当前工作区并开启新会话；已创建的 Agent 继续可用" @click="emit('newSession')">
        <IconMessagePlus :size="16"/>
        新建会话
      </button>
    </div>
    <p v-if="!props.sessions.length" class="session-empty">
      还没有会话；创建 Agent 并发送第一条消息后，这里会出现会话窗口。
    </p>
    <ul v-else class="session-list" aria-label="历史会话窗口">
      <li v-for="session in props.sessions" :key="session.conversationId">
        <button type="button" class="session-item"
                :class="{current: session.conversationId === props.currentConversationId}"
                :title="session.title" @click="emit('open', session.latestRunId)">
          <span class="session-title">{{ session.title }}</span>
          <span class="session-meta">
            <i class="session-status" :data-tone="toneOf(session)">
              {{ STATUS_LABELS[session.status] ?? session.status }}
            </i>
            <span>{{ session.turns }} 轮</span>
            <span>{{ formatTime(session.updatedAt) }}</span>
          </span>
        </button>
      </li>
    </ul>
  </section>
</template>
