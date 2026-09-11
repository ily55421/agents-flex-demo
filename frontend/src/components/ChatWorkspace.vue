<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {IconBrain, IconChartBar, IconSearch, IconSend2, IconShieldCheck, IconTimeline, IconTool} from '@tabler/icons-vue'
import type {AgentRun, ModelStatus, TraceView} from '@/types/agent'
import TraceViewer from '@/components/TraceViewer.vue'

const props = defineProps<{
  run: AgentRun | null
  model: ModelStatus | null
  busy: boolean
  streamingText: string
  streamingReasoning: string
  agentReady: boolean
  agentName: string | null
  trace: TraceView | null
}>()
const emit = defineEmits<{ send: [message: string] }>()
const draft = ref('')
const timeline = ref<HTMLElement | null>(null)
let scrollFrame: number | null = null

/** 判断当前 Turn 是否已经结束；结束后输入会创建同一会话的下一轮 Turn。 */
const isTerminal = computed(() => ['COMPLETED', 'FAILED', 'CANCELLED', 'MAX_ITERATIONS_REACHED',
  'MAX_STEPS_REACHED', 'BUDGET_EXCEEDED'].includes(props.run?.status ?? ''))
/** 只有真实模型与已创建 Agent 都就绪，且当前没有运行中的 Turn 时才允许发送。 */
const canSend = computed(() => Boolean(props.model?.configured) && props.agentReady
    && (!props.run || isTerminal.value) && !props.busy)
/** 判断是否应显示模型正在推进的占位消息。 */
const isThinking = computed(() => Boolean(props.run?.processing || props.run?.status === 'RUNNING'))
/** 把供应商标识转换为适合标题栏展示的文本。 */
const providerLabel = computed(() => props.model?.provider?.toUpperCase() || 'MODEL')
/** 头部统计摘要只读取当前 Snapshot，不额外发起请求。 */
const headerStatistics = computed(() => props.run ? [
  {label: 'Tokens', value: props.run.budget.usedTokens.toLocaleString()},
  {label: 'Tools', value: `${props.run.budget.usedToolCalls}/${props.run.budget.toolCallLimit}`},
  {label: 'Events', value: props.run.events.length.toLocaleString()},
] : [])
/** 根据缺失前置条件和 Turn 状态给出准确的输入框提示。 */
const composerPlaceholder = computed(() => {
  if (!props.model?.configured) return '请先点击左侧「⚙ 配置模型」应用模型配置'
  if (!props.agentReady) return '模型已就绪，点击左侧「创建 Agent」后即可输入对话'
  if (props.busy) return '正在处理请求...'
  if (props.run && !isTerminal.value) return '当前 Turn 完成后可以继续追问'
  return props.run ? '继续追问...' : '输入你希望 AI Agent 完成的任务...'
})
/** 在禁用输入时说明当前门控原因，避免用户只能通过按钮样式猜测状态。 */
const composerStatus = computed(() => {
  if (!props.model?.configured) return '真实模型尚未应用'
  if (!props.agentReady) return '创建 Agent 后即可开始对话'
  if (props.busy) return '正在处理当前操作'
  return '当前 Turn 执行或等待人工操作时不能发送新消息'
})

/** 校验并发送输入，发送成功后清空编辑器。 */
function submit() {
  const message = draft.value.trim()
  if (!message || !canSend.value) return
  emit('send', message)
  draft.value = ''
}

/** Enter 发送、Shift+Enter 换行，符合常见 AI 对话编辑器操作。 */
function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  submit()
}

/**
 * 消息或批量流文本变化时最多在下一绘制帧滚动一次。
 * 用户已经向上查看历史时不会强行拉回底部，流式阶段也不启动可堆积的平滑滚动动画。
 */
watch(() => [props.run?.messages.length, props.run?.status, props.streamingText, props.streamingReasoning], async () => {
  const current = timeline.value
  if (!current) return
  const followsLatest = current.scrollHeight - current.scrollTop - current.clientHeight < 120
  if (!followsLatest) return
  await nextTick()
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    if (timeline.value) timeline.value.scrollTop = timeline.value.scrollHeight
  })
})

/** 组件卸载时取消尚未执行的滚动帧，避免回调持有旧 DOM。 */
onBeforeUnmount(() => {
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
})
</script>

<template>
  <section class="chat-workspace" aria-labelledby="chat-heading">
    <header class="chat-header">
      <div>
        <h1 id="chat-heading">AI Agent 对话</h1>
        <p>{{
            agentReady ? `${agentName} 已就绪，真实模型与 Runtime 已连接`
                : model?.configured ? '真实模型已连接，等待创建 Agent' : '等待配置真实模型'
          }}</p>
      </div>
      <div class="chat-header-tools">
        <div class="model-chip" :data-configured="model?.configured">
          <span class="model-dot"/>
          <b>{{ providerLabel }}</b>
          <span>{{ model?.model || '读取配置中' }}</span>
        </div>
        <div v-if="run" class="chat-header-stats" aria-label="运行统计摘要">
          <span v-for="item in headerStatistics" :key="item.label"><small>{{ item.label }}</small><b>{{
              item.value
            }}</b></span>
        </div>
        <details v-if="run" class="chat-statistics">
          <summary>
            <IconChartBar :size="15"/>
            查看更多
          </summary>
          <div class="chat-statistics-content">
            <slot name="header-statistics"/>
          </div>
        </details>
        <details v-if="run" class="chat-trace-menu">
          <summary title="查看完整执行 Trace">
            <IconTimeline :size="15"/>
            Trace
          </summary>
          <div class="chat-trace-content">
            <TraceViewer :trace="trace"/>
          </div>
        </details>
      </div>
    </header>

    <div ref="timeline" class="chat-timeline">
      <div v-if="!run" class="chat-empty">
        <div class="assistant-avatar">
          <IconBrain :size="25"/>
        </div>
        <h2>{{ agentReady ? '开始一次真实的 Agent 对话' : '请先创建 Agent' }}</h2>
        <p v-if="model?.configured && agentReady">输入研究问题后，系统会调用 {{
            model.model
          }}，并在需要时使用表单、工具和人工审批。</p>
        <div v-if="model?.configured && agentReady" class="chat-example-actions" aria-label="对话示例">
          <span>快速开始</span>
          <button class="secondary-button" type="button"
                  @click="emit('send', '帮我研究一下新能源市场')">
            <IconSearch :size="16"/>
            研究新能源市场
          </button>
          <button class="secondary-button approval-example-button" type="button"
                  @click="emit('send', '帮我研究一下新能源市场，并在报告完成后申请发布到管理层简报频道')">
            <IconShieldCheck :size="16"/>
            研究并申请发布（触发审批）
          </button>
        </div>
        <p v-else-if="model?.configured">在左侧配置 Agent 的指令、执行策略、预算、重试与压缩参数，点击「创建 Agent」后即可对话。</p>
        <p v-else>请点击左侧「⚙ 配置模型」填写模型连接（本地 Ollama / 内网网关无需 API Key），应用后点击「创建 Agent」即可开始对话；配置会从当前浏览器自动恢复。</p>
      </div>

      <template v-for="(message, index) in run?.messages ?? []" :key="message.id || index">
        <article class="chat-message" :class="`message-${message.role}`">
          <div class="message-avatar">
            <IconTool v-if="message.role === 'tool'" :size="17"/>
            <IconBrain v-else-if="message.role === 'assistant'" :size="18"/>
            <span v-else>你</span>
          </div>
          <div class="message-content">
            <span class="message-author">{{
                message.role === 'user' ? '你' : message.role === 'tool' ? '工具结果' : model?.model || 'AI'
              }}</span>
            <details v-if="message.reasoning" class="reasoning-block">
              <summary>查看模型思考过程</summary>
              <p>{{ message.reasoning }}</p>
            </details>
            <p v-if="message.content">{{ message.content }}</p>
            <div v-for="tool in message.toolCalls" :key="tool.id" class="tool-call-row">
              <IconTool :size="15"/>
              <span>调用 {{ tool.name }}</span><code>{{ JSON.stringify(tool.arguments) }}</code>
            </div>
          </div>
        </article>
      </template>

      <article v-if="isThinking || streamingText || streamingReasoning"
               class="chat-message message-assistant thinking-message">
        <div class="message-avatar">
          <IconBrain :size="18"/>
        </div>
        <div class="message-content"><span class="message-author">{{ model?.model }}</span>
          <details v-if="streamingReasoning" class="reasoning-block" open>
            <summary>模型正在思考</summary>
            <p>{{ streamingReasoning }}</p></details>
          <p v-if="streamingText" class="streaming-content">{{ streamingText }}<span class="stream-caret"/></p>
          <div v-else class="typing-dots"><i/><i/><i/></div>
        </div>
      </article>

      <div v-if="run" class="chat-interactions">
        <slot/>
      </div>
    </div>

    <footer class="chat-composer-wrap">
      <div class="composer-actions">
        <div class="chat-composer" :data-disabled="!canSend">
          <textarea v-model="draft" rows="3" :disabled="!canSend" aria-label="输入对话内容"
                    :placeholder="composerPlaceholder" @keydown="handleKeydown"/>
          <button type="button" :disabled="!canSend || !draft.trim()" title="发送消息" aria-label="发送消息"
                  @click="submit">
            <IconSend2 :size="20"/>
          </button>
        </div>
      </div>
      <p v-if="!canSend" class="composer-status">{{ composerStatus }}</p>
    </footer>
  </section>
</template>
