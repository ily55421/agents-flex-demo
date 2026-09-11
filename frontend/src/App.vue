<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {storeToRefs} from 'pinia'
import {IconRefresh} from '@tabler/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import BudgetPanel from '@/components/BudgetPanel.vue'
import CapabilityMap from '@/components/CapabilityMap.vue'
import ChatWorkspace from '@/components/ChatWorkspace.vue'
import CompressionPanel from '@/components/CompressionPanel.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import EventStream from '@/components/EventStream.vue'
import KnowledgePanel from '@/components/KnowledgePanel.vue'
import RetryPanel from '@/components/RetryPanel.vue'
import RunControls from '@/components/RunControls.vue'
import RunResult from '@/components/RunResult.vue'
import SessionPanel from '@/components/SessionPanel.vue'
import TaskComposer from '@/components/TaskComposer.vue'
import {useAgentRun} from '@/composables/useAgentRun'
import {knowledgeApi} from '@/api/agent'
import type {CreateAgentPayload, EmbeddingProfile, JsonSchema} from '@/types/agent'

const store = useAgentRun()
const {run, agent, trace, modelStatus, streamingText, streamingReasoning, busy, error, connectionState, sessions} = storeToRefs(store)

/** 从普通 USER_INPUT Suspension 提取动态表单 Schema，手工暂停不会误渲染为业务表单。 */
const formSchema = computed<JsonSchema | null>(() =>
    run.value?.status === 'WAITING_FOR_USER' && !run.value.manualPause
        ? run.value.suspension?.metadata.schema ?? null : null,
)

/** 执行异步命令；错误已由 Store 统一记录，组件层只需避免未处理 Promise。 */
function perform(action: () => Promise<void>) {
  void action().catch(() => undefined)
}

/** 提交左侧完整配置；后端真实 Agent 创建成功后聊天输入才会解锁。 */
function createAgent(configuration: CreateAgentPayload) {
  perform(() => store.createAgent(configuration))
}

/** 立即把向量模型配置应用到知识库，用于不等创建 Agent 的独立调试。 */
function configureEmbedding(profile: EmbeddingProfile) {
  perform(async () => {
    await knowledgeApi.configureEmbedding({
      embeddingEndpoint: profile.embeddingEndpoint,
      embeddingApiKey: profile.embeddingApiKey,
      embeddingModel: profile.embeddingModel,
      searchMode: profile.knowledgeSearchMode,
    })
  })
}

/** 将聊天输入创建为 READY Run 并立即启动，用户不需要理解两阶段 Runtime 命令。 */
async function send(message: string) {
  try {
    if (run.value && store.isTerminal) {
      await store.continueConversation(message)
    } else {
      if (!agent.value) return
      await store.create({agentId: agent.value.agentId, task: message})
      await store.start()
    }
  } catch {
    // Store 已保存后端返回的准确错误，例如缺少 API Key 或模型连接失败。
  }
}

onMounted(() => {
  // 同时读取安全模型状态，并在 URL 带 run 参数时恢复既有 Snapshot、Trace 与 SSE。
  void store.initialize()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader :run="run" :connection-state="connectionState"/>
    <main id="main-content" class="dashboard-shell">
      <aside class="left-rail">
        <SessionPanel :sessions="sessions" :current-conversation-id="run?.conversationId ?? null" :busy="busy"
                      @new-session="store.newSession" @open="(runId) => perform(() => store.openRun(runId))"/>
        <TaskComposer :busy="busy" :disabled="Boolean(run)" :agent="agent" :show-reset="store.isTerminal"
                      @create="createAgent" @reset="store.reset" @configure-embedding="configureEmbedding"/>
        <KnowledgePanel/>
        <RunControls v-if="run" :run="run" :busy="busy" @start="perform(store.start)"
                     @suspend="perform(store.suspend)" @resume="perform(store.resume)"
                     @cancel="perform(store.cancel)" @refresh="perform(store.refresh)" @reset="store.reset"/>
        <CapabilityMap v-if="run" :capabilities="run.capabilities"/>
      </aside>

      <section class="workspace-column">
        <ChatWorkspace :run="run" :model="modelStatus" :trace="trace" :busy="busy" :streaming-text="streamingText"
                       :streaming-reasoning="streamingReasoning" :agent-ready="Boolean(agent)"
                       :agent-name="agent?.name ?? null" @send="send">
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
            <div class="interaction-content"><h2>Agent 已在安全检查点挂起</h2><p>Snapshot 已保存，恢复后继续同一个 Turn。</p>
              <button class="primary-button" type="button" :disabled="busy" @click="perform(store.resume)"><IconRefresh :size="17"/>恢复执行</button>
            </div>
          </section>
          <section v-else-if="run?.pauseRequested" class="interaction-panel pause-panel" aria-live="polite">
            <div class="interaction-icon"><IconRefresh class="spin" :size="22"/></div>
            <div class="interaction-content"><h2>正在等待安全检查点</h2><p>当前原子 Step 完成后会保存 Snapshot 并挂起。</p></div>
          </section>
          <RunResult v-if="store.isTerminal && run" :run="run"/>
        </ChatWorkspace>
      </section>
      <aside class="right-rail"><EventStream :events="run?.events ?? []"/></aside>
    </main>
    <div v-if="error" class="error-toast" role="alert"><span>{{ error }}</span><button type="button" @click="store.clearError">关闭</button></div>
  </div>
</template>
