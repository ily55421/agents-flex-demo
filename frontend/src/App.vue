<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {storeToRefs} from 'pinia'
import {IconAdjustmentsHorizontal, IconDatabase, IconMessages, IconRefresh, IconRobot, IconSitemap} from '@tabler/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import AgentPicker from '@/components/AgentPicker.vue'
import AgentWorkshop from '@/components/AgentWorkshop.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import BudgetPanel from '@/components/BudgetPanel.vue'
import CapabilityMap from '@/components/CapabilityMap.vue'
import ChatWorkspace from '@/components/ChatWorkspace.vue'
import CompressionPanel from '@/components/CompressionPanel.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import EventStream from '@/components/EventStream.vue'
import GraphExplorer from '@/components/GraphExplorer.vue'
import KnowledgePanel from '@/components/KnowledgePanel.vue'
import KnowledgeScopeCard from '@/components/KnowledgeScopeCard.vue'
import ModelConfigPage from '@/components/ModelConfigPage.vue'
import RetryPanel from '@/components/RetryPanel.vue'
import RunControls from '@/components/RunControls.vue'
import RunResult from '@/components/RunResult.vue'
import SessionPanel from '@/components/SessionPanel.vue'
import {useAgentRun} from '@/composables/useAgentRun'
import {knowledgeApi} from '@/api/agent'
import type {AgentDefinition, CreateAgentPayload, EmbeddingProfile, JsonSchema, ModelProfile} from '@/types/agent'

const store = useAgentRun()
const {run, agent, trace, modelStatus, streamingText, streamingReasoning, busy, error, connectionState, sessions} = storeToRefs(store)

/** 知识库面板实例；应用向量配置后主动刷新状态，避免用户看到过期的“未配置”。 */
const knowledgePanel = ref<{ reload: () => Promise<void> } | null>(null)

/**
 * 顶部页面 Tab：Agent 对话（默认）/ Agent 维护 / 模型配置 / 知识库 / 图谱。
 * 旧链接的 ?view=chat（纯对话）已并入对话页，回退到默认页。
 */
type PageTab = 'workspace' | 'agents' | 'models' | 'knowledge' | 'graph'
const activeTab = ref<PageTab>(
    (() => {
      const view = new URL(window.location.href).searchParams.get('view')
      return view === 'agents' || view === 'models' || view === 'knowledge' || view === 'graph' ? view : 'workspace'
    })(),
)

/** 切换页面时同步 URL 的 view 参数（replaceState 不产生历史记录）。 */
function switchTab(tab: PageTab) {
  activeTab.value = tab
  const url = new URL(window.location.href)
  tab === 'workspace' ? url.searchParams.delete('view') : url.searchParams.set('view', tab)
  window.history.replaceState({}, '', url)
  // 进入知识库页时刷新状态，保证文档清单与向量状态最新。
  if (tab === 'knowledge') knowledgePanel.value?.reload().catch(() => undefined)
}

/** 从普通 USER_INPUT Suspension 提取动态表单 Schema，手工暂停不会误渲染为业务表单。 */
const formSchema = computed<JsonSchema | null>(() =>
    run.value?.status === 'WAITING_FOR_USER' && !run.value.manualPause
        ? run.value.suspension?.metadata.schema ?? null : null,
)

/** 执行异步命令；错误已由 Store 统一记录，组件层只需避免未处理 Promise。 */
function perform(action: () => Promise<void>) {
  void action().catch(() => undefined)
}

/** 提交 Agent 维护页的完整配置；后端真实 Agent 创建成功后聊天输入才会解锁。 */
function createAgent(configuration: CreateAgentPayload) {
  perform(() => store.createAgent(configuration))
}

/** 立即把向量模型配置应用到知识库，用于不等创建 Agent 的独立调试。 */
function configureEmbedding(profile: EmbeddingProfile) {
  perform(async () => {
    try {
      await knowledgeApi.configureEmbedding({
        embeddingEndpoint: profile.embeddingEndpoint,
        embeddingApiKey: profile.embeddingApiKey,
        embeddingModel: profile.embeddingModel,
        searchMode: profile.knowledgeSearchMode,
      })
    } finally {
      // 失败时后端已记录 lastError，仍刷新面板把原因展示出来，避免静默无反馈。
      knowledgePanel.value?.reload().catch(() => undefined)
    }
  })
}

/** 把聊天模型连接同步到后端运行时，让模型状态与输入框可用性立即生效。 */
function applyModel(profile: ModelProfile) {
  perform(() => store.applyModelConfiguration(profile))
}

/** 对话页选择 Agent：切换到不同 Agent 时清空当前对话窗口（历史会话仍保留）。 */
function pickAgent(definition: AgentDefinition) {
  if (run.value && run.value.agent?.agentId !== definition.agentId) {
    if (!window.confirm('切换 Agent 会清空当前对话窗口（历史会话仍可从左侧重新打开）。继续？')) return
    store.reset()
  }
  store.selectAgent(definition)
}

/** 从维护页跳回对话页：可选携带要对话的 Agent。 */
function goChat(definition: AgentDefinition | null) {
  if (definition && definition.agentId !== agent.value?.agentId) pickAgent(definition)
  switchTab('workspace')
}

/** 对话页左下角切换检索范围：以当前 Agent 配置 + 新范围重建运行对象。 */
function changeKnowledgeScope(docId: string) {
  const current = agent.value
  if (!current) return
  perform(async () => {
    await store.recreateAgent({...current, knowledgeNamespace: docId})
  })
}

/** 将聊天输入创建为 READY Run 并立即启动，用户不需要理解两阶段 Runtime 命令。 */
async function send(message: string) {
  try {
    if (run.value && store.isTerminal) {
      await store.continueConversation(message)
    } else {
      if (!agent.value) return
      // 历史归档 Agent（服务重启后运行对象未重建）在发送时自动重建：
      // 免密钥服务全程无感；需要密钥的服务由后端报错引导到「Agent 维护」页。
      if (agent.value.runnable === false) await store.recreateAgent(agent.value)
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
    <nav class="page-tabs" aria-label="页面切换">
      <button type="button" :class="{active: activeTab === 'workspace'}" @click="switchTab('workspace')">
        <IconMessages :size="16"/>Agent 对话
      </button>
      <button type="button" :class="{active: activeTab === 'agents'}" @click="switchTab('agents')">
        <IconRobot :size="16"/>Agent 维护
      </button>
      <button type="button" :class="{active: activeTab === 'models'}" @click="switchTab('models')">
        <IconAdjustmentsHorizontal :size="16"/>模型配置
      </button>
      <button type="button" :class="{active: activeTab === 'knowledge'}" @click="switchTab('knowledge')">
        <IconDatabase :size="16"/>知识库
      </button>
      <button type="button" :class="{active: activeTab === 'graph'}" @click="switchTab('graph')">
        <IconSitemap :size="16"/>图谱
      </button>
    </nav>

    <!-- Agent 对话页：左侧会话历史 + Agent 选择，中间对话区，右侧事件流。 -->
    <main v-if="activeTab === 'workspace'" id="main-content" class="dashboard-shell">
      <aside class="left-rail">
        <AgentPicker :agent="agent" :busy="busy" @select="pickAgent"
                     @go-workshop="switchTab('agents')" @go-models="switchTab('models')"/>
        <SessionPanel :sessions="sessions" :current-conversation-id="run?.conversationId ?? null" :busy="busy"
                      @new-session="store.newSession" @open="(runId) => perform(() => store.openRun(runId))"/>
        <RunControls v-if="run" :run="run" :busy="busy" @start="perform(store.start)"
                     @suspend="perform(store.suspend)" @resume="perform(store.resume)"
                     @cancel="perform(store.cancel)" @refresh="perform(store.refresh)" @reset="store.reset"/>
        <CapabilityMap v-if="run" :capabilities="run.capabilities"/>
        <KnowledgeScopeCard :agent="agent" :busy="busy" @change="changeKnowledgeScope"/>
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

    <!-- Agent 维护页：创建/重新配置 Agent，查看已创建清单并跳转对话。 -->
    <main v-else-if="activeTab === 'agents'" id="main-content" class="workshop-shell">
      <AgentWorkshop :agent="agent" :busy="busy" @create="createAgent" @go-chat="goChat"
                     @configure-embedding="configureEmbedding" @apply-model="applyModel"/>
    </main>

    <!-- 模型配置页：聊天模型与向量模型的独立维护入口。 -->
    <main v-else-if="activeTab === 'models'" id="main-content" class="models-shell">
      <ModelConfigPage :model-status="modelStatus" @apply="applyModel"
                       @configure-embedding="configureEmbedding" @go-knowledge="switchTab('knowledge')"/>
    </main>

    <main v-else-if="activeTab === 'graph'" class="graph-tab-shell">
      <GraphExplorer/>
    </main>

    <main v-else class="knowledge-shell">
      <KnowledgePanel ref="knowledgePanel"/>
    </main>

    <div v-if="error" class="error-toast" role="alert"><span>{{ error }}</span><button type="button" @click="store.clearError">关闭</button></div>
  </div>
</template>
