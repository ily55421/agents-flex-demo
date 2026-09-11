<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {IconMessages, IconRefresh, IconRobot} from '@tabler/icons-vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import BudgetPanel from '@/components/BudgetPanel.vue'
import ChatWorkspace from '@/components/ChatWorkspace.vue'
import CompressionPanel from '@/components/CompressionPanel.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import EventStream from '@/components/EventStream.vue'
import RetryPanel from '@/components/RetryPanel.vue'
import RunResult from '@/components/RunResult.vue'
import {useAgentRun} from '@/composables/useAgentRun'
import {agentApi} from '@/api/agent'
import type {AgentDefinition, JsonSchema} from '@/types/agent'

const emit = defineEmits<{ goToWorkspace: [] }>()
const store = useAgentRun()
const {run, agent, trace, modelStatus, streamingText, streamingReasoning, busy} = storeToRefs(store)

const agents = ref<AgentDefinition[]>([])
const selectedAgentId = ref('')
const loading = ref(false)
const loadError = ref<string | null>(null)

/** 当前选中的智能体定义；会话列表打开历史 Run 时以后端 Snapshot 中的 agent 为准。 */
const selectedAgent = computed<AgentDefinition | null>(() =>
    agents.value.find((item) => item.agentId === selectedAgentId.value)
    || agent.value
    || null,
)

/** 供交互面板使用的动态表单 Schema。 */
const formSchema = computed<JsonSchema | null>(() =>
    run.value?.status === 'WAITING_FOR_USER' && !run.value.manualPause
        ? run.value.suspension?.metadata.schema ?? null : null,
)

/** 可对话的智能体（进程内已注册）；归档 Agent 单独列出并说明需重新创建。 */
const runnableAgents = computed(() => agents.value.filter((item) => item.runnable !== false))
const archivedAgents = computed(() => agents.value.filter((item) => item.runnable === false))

/** 加载智能体列表（当前进程 + DuckDB 归档），失败时展示原因。 */
async function loadAgents() {
  loading.value = true
  loadError.value = null
  try {
    agents.value = await agentApi.listAgents()
    if (selectedAgentId.value && !agents.value.some((item) => item.agentId === selectedAgentId.value)) {
      selectedAgentId.value = ''
    }
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '智能体列表加载失败'
  } finally {
    loading.value = false
  }
}

/** 切换智能体时清空当前工作区，避免旧 Run 串台。 */
watch(selectedAgentId, (agentId) => {
  if (!agentId) return
  if (store.run && store.run.agent?.agentId !== agentId) store.reset()
})

/** 发送消息：复用 store 的 create/continueConversation 链路。 */
async function send(message: string) {
  const target = selectedAgent.value
  if (!target) return
  try {
    if (run.value && store.isTerminal) {
      await store.continueConversation(message)
    } else {
      await store.create({agentId: target.agentId, task: message})
      await store.start()
    }
  } catch {
    // Store 已记录后端准确错误。
  }
}

/** 执行交互面板命令。 */
function perform(action: () => Promise<void>) {
  void action().catch(() => undefined)
}

onMounted(() => {
  void loadAgents()
})
</script>

<template>
  <section class="chat-home">
    <header class="chat-home-header">
      <div class="chat-home-title">
        <IconMessages :size="20"/>
        <h2>纯对话</h2>
      </div>
      <div class="agent-selector">
        <label for="chat-agent-select">选择智能体</label>
        <select id="chat-agent-select" v-model="selectedAgentId" :disabled="loading || Boolean(run?.processing)">
          <option value="" disabled>{{ loading ? '加载智能体…' : '选择要对话的智能体…' }}</option>
          <option v-for="item in runnableAgents" :key="item.agentId" :value="item.agentId">
            {{ item.name }} · {{ item.modelProvider }} / {{ item.modelName }} · {{ item.tools.length }} 工具
          </option>
          <optgroup v-if="archivedAgents.length" label="历史归档（需在工作台重新创建后才能对话）">
            <option v-for="item in archivedAgents" :key="item.agentId" :value="item.agentId" disabled>
              {{ item.name }} · {{ item.modelProvider }} / {{ item.modelName }}
            </option>
          </optgroup>
        </select>
        <button class="text-button" type="button" :disabled="loading" title="刷新智能体列表"
                aria-label="刷新智能体列表" @click="loadAgents">
          <IconRefresh :size="16"/>
        </button>
      </div>
    </header>

    <p v-if="loadError" class="chat-home-error" role="alert">智能体列表加载失败：{{ loadError }}</p>

    <div v-if="!runnableAgents.length && !loading" class="chat-home-empty">
      <IconRobot :size="42" class="chat-home-empty-icon"/>
      <h3>{{ archivedAgents.length ? '历史智能体需要重新创建' : '还没有可用智能体' }}</h3>
      <p v-if="archivedAgents.length">
        检测到 {{ archivedAgents.length }} 个归档智能体配置，但服务重启后运行对象未重建（API Key 不持久化）。
        请到「Agent 工作台」确认模型配置并重新创建，即可在此直接对话。
      </p>
      <p v-else>请先到「Agent 工作台」配置模型并创建 Agent，之后可以在这里直接选择智能体开始对话。</p>
      <button class="primary-button" type="button" @click="emit('goToWorkspace')">去工作台创建</button>
    </div>

    <div v-else-if="!selectedAgent && !run" class="chat-home-empty">
      <IconMessages :size="42" class="chat-home-empty-icon"/>
      <h3>选择一个智能体开始对话</h3>
      <p>选择后即可发送消息；Agent 会按自身工具与执行策略运行，事件流与 Trace 实时可见。</p>
    </div>

    <div v-else class="chat-home-workspace">
      <div v-if="selectedAgent" class="agent-brief">
        <span class="agent-brief-name">{{ selectedAgent.name }}</span>
        <span class="agent-brief-model">{{ selectedAgent.modelProvider }} · {{ selectedAgent.modelName }}</span>
        <span v-if="selectedAgent.description" class="agent-brief-desc">{{ selectedAgent.description }}</span>
      </div>
      <div class="chat-home-main">
        <ChatWorkspace :run="run" :model="modelStatus" :trace="trace" :busy="busy"
                       :streaming-text="streamingText" :streaming-reasoning="streamingReasoning"
                       :agent-ready="Boolean(selectedAgent)" :agent-name="selectedAgent?.name ?? null"
                       @send="send">
          <template #header-statistics>
            <div v-if="run" class="metrics-layout">
              <BudgetPanel :budget="run.budget" :exceeded-reason="run.budgetExceededReason"/>
              <CompressionPanel :compression="run.compression"/>
              <RetryPanel :events="run.events" :retry-count="run.retryCount" :max-retries="run.maxRetries" :policy="run.retryPolicy"/>
            </div>
          </template>
          <DynamicForm v-if="formSchema" :schema="formSchema" :busy="busy"
                       @submit="(values) => perform(() => store.submitForm(values))"/>
          <ApprovalPanel v-if="run?.status === 'WAITING_FOR_APPROVAL' && run.suspension"
                         :suspension="run.suspension" :tool-call="run.pendingToolCalls[0]" :busy="busy"
                         @decide="(approved, reason) => perform(() => store.approve(approved, reason))"/>
          <section v-if="run?.manualPause" class="interaction-panel pause-panel" aria-live="polite">
            <div class="interaction-icon"><IconRefresh :size="22"/></div>
            <div class="interaction-content"><h2>Agent 已在安全检查点挂起</h2>
              <p>Snapshot 已保存，恢复后继续同一个 Turn。</p>
              <button class="primary-button" type="button" :disabled="busy" @click="perform(store.resume)">
                <IconRefresh :size="17"/>恢复执行
              </button>
            </div>
          </section>
          <section v-else-if="run?.pauseRequested" class="interaction-panel pause-panel" aria-live="polite">
            <div class="interaction-icon"><IconRefresh class="spin" :size="22"/></div>
            <div class="interaction-content"><h2>正在等待安全检查点</h2>
              <p>当前原子 Step 完成后会保存 Snapshot 并挂起。</p>
            </div>
          </section>
          <RunResult v-if="store.isTerminal && run" :run="run"/>
        </ChatWorkspace>
        <EventStream :events="run?.events ?? []"/>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-home {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.chat-home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 0 2px;
}

.chat-home-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #047857;
}

.chat-home-title h2 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.agent-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-selector label {
  font-size: 13px;
  color: #4b5563;
}

.agent-selector select {
  min-width: 320px;
  max-width: 480px;
}

.chat-home-error {
  margin: 0;
  font-size: 12.5px;
  color: #b91c1c;
}

.chat-home-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  padding: 40px;
}

.chat-home-empty-icon {
  color: #d1d5db;
}

.chat-home-empty h3 {
  margin: 4px 0 0;
  font-size: 16px;
  color: #374151;
}

.chat-home-empty p {
  margin: 0;
  max-width: 440px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.chat-home-workspace {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
}

.agent-brief {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 12.5px;
}

.agent-brief-name {
  font-weight: 600;
  color: #065f46;
}

.agent-brief-model {
  color: #047857;
}

.agent-brief-desc {
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}

.chat-home-main {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

@media (max-width: 1200px) {
  .chat-home-main {
    grid-template-columns: 1fr;
  }
}
</style>
