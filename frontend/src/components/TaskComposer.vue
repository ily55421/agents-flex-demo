<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {IconAdjustmentsHorizontal, IconCheck, IconRefresh, IconRobot, IconSettings} from '@tabler/icons-vue'
import type {AgentDefinition, CreateAgentPayload, EmbeddingProfile, KnowledgeDocument, ModelProfile} from '@/types/agent'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'
import ConfigHelpIcon from '@/components/ConfigHelpIcon.vue'
import ModelConfigDialog from '@/components/ModelConfigDialog.vue'
import {knowledgeApi} from '@/api/agent'

const props = defineProps<{
  busy: boolean
  disabled: boolean
  agent: AgentDefinition | null
  showReset: boolean
}>()
const emit = defineEmits<{
  create: [configuration: CreateAgentPayload]
  reset: []
  configureEmbedding: [profile: EmbeddingProfile]
  applyModel: [profile: ModelProfile]
}>()
const CONFIG_STORAGE_KEY = 'agents-flex-demo.agent-configuration.v1'

const defaults: CreateAgentPayload = {
  modelProvider: 'deepseek',
  modelEndpoint: 'https://api.deepseek.com',
  modelRequestPath: '/chat/completions',
  modelApiKey: '',
  modelName: 'deepseek-chat',
  modelTemperature: 0.2,
  modelThinkingEnabled: false,
  modelThinkingProtocol: 'none',
  modelSeed: '',
  modelTopP: null,
  modelTopK: null,
  modelMaxTokens: null,
  modelStop: [],
  modelIncludeUsage: true,
  modelResponseFormat: 'NONE',
  modelRetryEnabled: true,
  modelRetryCount: 2,
  modelRetryInitialDelayMillis: 600,
  name: '市场研究助手',
  version: '1',
  description: '演示 Agents-Flex Agent Runtime 的完整研究任务',
  instructions: '你是一个中文 AI 市场研究助手。先理解用户问题；涉及市场数据、行业趋势或已有研究结论时，优先调用 search_knowledge 检索本地知识库并引用片段来源；信息不足时调用 request_user_input 请求 research_brief 表单；需要补充资料时调用 research_market，随后调用 verify_sources 校验来源；只有用户明确要求发布时才调用 publish_report。收到 request_user_input 的 submitted 工具结果后，data 中的字段视为用户已经确认的信息；字段完整且与原任务一致时必须直接继续，不得再次以自然语言重复索要相同信息。只有字段缺失或与原任务明确冲突时才向用户说明具体冲突并请求确认。不得伪造工具结果，所有最终回答必须使用中文，清楚区分事实、判断与仍需确认的信息。',
  maxIterations: 8,
  maxSteps: 32,
  maxAttachedTurns: 6,
  maxAttachedTokens: 0,
  maxAttachedMessages: 40,
  maxInputTokens: 16000,
  maxOutputTokens: 8192,
  maxTotalTokens: 32000,
  maxToolCalls: 8,
  maxDurationMillis: 1800000,
  maxRetries: 2,
  initialDelayMillis: 0,
  maxDelayMillis: 0,
  retryMultiplier: 2,
  compressionDecider: 'PENDING_MESSAGES',
  compressionMessageThreshold: 20,
  compressionTurnThreshold: 4,
  compressionTokenThreshold: 4000,
  compressionMode: 'WHOLE_HISTORY',
  toolErrorStrategy: 'FAIL_RUN',
  interruptedToolMessageTemplate: 'Tool call was not completed: {reason}',
  interruptedTurnMessageTemplate: 'The previous AgentTurn ended before completion: {reason}',
  cancellationReason: 'turn cancelled by caller',
  modelCallTimeoutMillis: 0,
  toolExecutionTimeoutMillis: 0,
  externalToolTimeoutMillis: 0,
  approvalTimeoutMillis: 0,
  userInputTimeoutMillis: 0,
  suspensionExpirationStrategy: 'REJECT_RESUME',
  toolResultMaxCharacters: 0,
  externalToolResultMaxCharacters: 0,
  toolResultOverflowStrategy: 'FAIL',
  toolExecutionMode: 'SEQUENTIAL',
  maxParallelToolCalls: 8,
  parallelFailureStrategy: 'FAIL_FAST',
  compactCompletedToolTurns: true,
  compressionKeepRecentTurns: 2,
  compressionFailureStrategy: 'FAIL',
  compressionInstruction: '保留研究事实、用户约束、审批结果和未完成事项。',
  compressionHistoryHeader: '\n\n历史消息：\n',
  compressionSummaryPrefix: '以下是较早对话的摘要，请将其作为历史事实参考：',
  compressionPerMessageRequest: '\n请逐条摘要以下消息，并仅返回 JSON 数组，每项包含 messageId 和 summary：\n',
  compressionModelCallTimeoutMillis: 0,
  compressionMaxInputCharacters: 0,
  compressionMaxOutputCharacters: 0,
  embeddingEndpoint: 'http://127.0.0.1:18888/v1',
  embeddingApiKey: '',
  embeddingModel: 'bge-m3',
  knowledgeSearchMode: 'HYBRID',
  knowledgeNamespace: 'all',
}

/** 从浏览器恢复上次保存的完整配置；损坏或旧版本数据自动回退到默认值。 */
function readSavedConfiguration(): Partial<CreateAgentPayload> {
  try {
    const raw = window.localStorage.getItem(CONFIG_STORAGE_KEY)
    return raw ? JSON.parse(raw) as Partial<CreateAgentPayload> : {}
  } catch {
    return {}
  }
}

const form = reactive<CreateAgentPayload>({...defaults, ...readSavedConfiguration()})
const editing = ref(true)
const dialogOpen = ref(false)
const stopText = ref(Array.isArray(form.modelStop) ? form.modelStop.join(', ') : '')
if (!Array.isArray(form.modelStop)) form.modelStop = []
/** Agent 创建成功或 Run 运行时锁定字段，确保正在执行的配置不会被界面误改。
 *  模型配置弹窗不受此限制：模型与预算随时可改，改完通过“重新配置 Agent”创建新 Agent 生效。 */
const locked = computed(() => props.busy || props.disabled || !editing.value)
/** 弹窗只在实际请求进行中禁用；已有会话也允许打开并修改模型配置。 */
const dialogLocked = computed(() => props.busy)

/** 配置表单发生变化时持久化到当前浏览器，仅保存用户选择，不写入服务端文件。 */
watch(form, (configuration) => {
  try {
    window.localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(configuration))
  } catch {
    // 隐私模式或存储空间不足时仍允许继续创建 Agent，只是不持久化。
  }
}, {deep: true})

/**
 * 把后端确认过的 Agent 配置复制回表单，使 URL 恢复与重新配置都显示真实生效值。
 * @param agent 后端返回的安全 Agent 定义
 */
function applyAgent(agent: AgentDefinition) {
  const {
    agentId: _agentId,
    tools: _tools,
    approvalPolicy: _approvalPolicy,
    createdAt: _createdAt,
    ...configuration
  } = agent
  Object.assign(form, configuration)
  // 后端回显的有效模型停止序列也要同步到文本输入框，避免表单显示旧的本地值。
  stopText.value = Array.isArray(form.modelStop) ? form.modelStop.join(', ') : ''
}

/** 提交普通对象副本，避免把 Vue 响应式代理传入 API 序列化层。 */
function submit() {
  if (locked.value) return
  const stop = stopText.value.split(',').map((item) => item.trim()).filter(Boolean)
  // 同步回响应式配置，确保停止序列和其他字段一样被 localStorage 持久化。
  form.modelStop = stop
  const configuration = {...form, modelStop: stop}
  emit('create', configuration)
}

/** 进入重新配置模式；保存时会创建新 Agent，已有 Agent 资源和历史 Run 不会被改写。 */
function startEditing() {
  if (props.busy || props.disabled) return
  editing.value = true
}

/** 后端返回新 Agent 或从 URL 恢复 Run 时同步表单，并切换到“已就绪”状态。 */
watch(() => props.agent, (agent) => {
  if (!agent) return
  applyAgent(agent)
  editing.value = false
}, {immediate: true})

/** 当前表单“模型连接”字段的只读快照，供模型配置弹窗初始化与编辑。 */
const modelSnapshot = computed<ModelProfile>(() => ({
  profileName: `${form.modelProvider} · ${form.modelName}`,
  modelProvider: form.modelProvider,
  modelEndpoint: form.modelEndpoint,
  modelRequestPath: form.modelRequestPath,
  modelApiKey: form.modelApiKey,
  modelName: form.modelName,
  modelTemperature: form.modelTemperature,
  modelThinkingEnabled: form.modelThinkingEnabled,
  modelThinkingProtocol: form.modelThinkingProtocol,
  modelSeed: form.modelSeed,
  modelTopP: form.modelTopP,
  modelTopK: form.modelTopK,
  modelMaxTokens: form.modelMaxTokens,
  modelStop: Array.isArray(form.modelStop) ? [...form.modelStop] : [],
  modelIncludeUsage: form.modelIncludeUsage,
  modelResponseFormat: form.modelResponseFormat,
  modelRetryEnabled: form.modelRetryEnabled,
  modelRetryCount: form.modelRetryCount,
  modelRetryInitialDelayMillis: form.modelRetryInitialDelayMillis,
  maxInputTokens: form.maxInputTokens,
  maxOutputTokens: form.maxOutputTokens,
  maxTotalTokens: form.maxTotalTokens,
  maxAttachedTokens: form.maxAttachedTokens,
}))

/** 当前表单“向量模型”字段的只读快照，供模型配置弹窗初始化与编辑。 */
const embeddingSnapshot = computed<EmbeddingProfile>(() => ({
  profileName: form.embeddingModel || '向量模型',
  embeddingEndpoint: form.embeddingEndpoint,
  embeddingApiKey: form.embeddingApiKey,
  embeddingModel: form.embeddingModel,
  knowledgeSearchMode: form.knowledgeSearchMode,
}))

/** 模型配置摘要：左栏一行展示当前生效的聊天模型与向量模型。 */
const modelSummary = computed(() => {
  const chat = `${form.modelProvider} / ${form.modelName}`
  const embedding = form.embeddingEndpoint ? `${form.embeddingModel} @ ${form.embeddingEndpoint}` : '未配置'
  return {chat, embedding}
})

/** 知识库文档清单：供“知识库范围”下拉选择检索范围。 */
const knowledgeDocs = ref<KnowledgeDocument[]>([])

/** 刷新知识库文档清单，用于范围选择；失败时静默保留空列表。 */
async function loadKnowledgeDocs() {
  try {
    knowledgeDocs.value = await knowledgeApi.documents()
  } catch {
    knowledgeDocs.value = []
  }
}

onMounted(() => {
  void loadKnowledgeDocs()
})

/** 弹窗“应用配置”：写回表单、持久化到浏览器，并同步到后端使模型状态立即生效。 */
function applyModelDialog(model: ModelProfile, embedding: EmbeddingProfile) {
  if (dialogLocked.value) return
  const {profileName: _chatName, ...chatFields} = model
  const {profileName: _embedName, ...embedFields} = embedding
  Object.assign(form, chatFields, embedFields)
  stopText.value = Array.isArray(form.modelStop) ? form.modelStop.join(', ') : ''
  dialogOpen.value = false
  emit('applyModel', {...model, modelStop: [...(Array.isArray(model.modelStop) ? model.modelStop : [])]})
  // 向量模型同时同步给知识库，避免“配置了但没生效”的割裂体验；未填写时跳过。
  if (embedding.embeddingEndpoint.trim() && embedding.embeddingModel.trim()) {
    emit('configureEmbedding', {...embedding})
  }
}
</script>

<template>
  <section class="surface task-composer" aria-labelledby="agent-config-heading">
    <div class="section-heading agent-config-heading">
      <IconAdjustmentsHorizontal :size="18" :stroke-width="1.8" aria-hidden="true"/>
      <h2 id="agent-config-heading">Agent 配置</h2>
      <div v-if="agent && !editing" class="ready-actions">
        <button v-if="showReset" class="ready-reset-button" type="button" title="清空当前对话并开始新对话"
                aria-label="清空当前对话并开始新对话" @click="emit('reset')">
          <IconRefresh :size="15"/>
        </button>
        <span class="ready-badge"><IconCheck :size="14"/>已就绪</span>
      </div>
    </div>

    <form class="composer-form agent-form" @submit.prevent="submit">
      <div class="model-config-card">
        <div class="model-config-head">
          <span class="model-config-label"><IconSettings :size="15"/>模型配置</span>
          <button class="text-button" type="button" :disabled="dialogLocked" @click="dialogOpen = true">
            ⚙ 配置模型
          </button>
        </div>
        <div class="model-config-summary">
          <span>聊天：<strong>{{ modelSummary.chat }}</strong></span>
          <span>向量：<strong>{{ modelSummary.embedding }}</strong></span>
          <span>预算：<strong>输出 {{ form.maxOutputTokens || '不限' }} / 总 {{ form.maxTotalTokens || '不限' }} Token</strong></span>
        </div>
        <p class="model-config-hint">预设快捷填充 + 自由自定义（含输出与上下文预算）；已有会话也能修改，改后点「重新配置 Agent」生效。</p>
      </div>

      <fieldset :disabled="locked">
        <legend>基本信息</legend>
        <div class="field-block">
          <ConfigFieldLabel for-id="agent-name" text="Agent 名称" help="Agent 在左侧列表、运行详情、事件流和 Trace 中显示的名称，用于区分不同用途的智能体，例如市场研究助手。"/>
          <input id="agent-name" v-model.trim="form.name" required maxlength="80"/>
        </div>
        <div class="field-block">
          <ConfigFieldLabel for-id="agent-version" text="版本" help="Agent 定义的业务版本标识。修改提示词、工具或执行策略后递增，便于通过运行记录确认实际使用的配置；它不是 Maven 版本。"/>
          <input id="agent-version" v-model.trim="form.version" required maxlength="32"/>
        </div>
        <div class="field-block">
          <ConfigFieldLabel for-id="agent-description" text="描述" help="面向使用者的能力简介，会展示在 Agent 定义信息中。它用于说明适用任务，不会作为系统指令发送给模型。"/>
          <textarea id="agent-description" v-model.trim="form.description" rows="2" required maxlength="300"/>
        </div>
        <div class="field-block">
          <ConfigFieldLabel for-id="agent-instructions" text="System Instructions" help="发送给模型的系统指令，用于定义角色、工作步骤、工具使用规则和回答边界。适合放置每轮对话都必须遵守的业务规则，不要写入 API Key。"/>
          <textarea id="agent-instructions" v-model.trim="form.instructions" rows="7" required maxlength="8000"/>
        </div>
      </fieldset>

      <details class="config-group" open>
        <summary>知识库范围</summary>
        <fieldset :disabled="locked" class="compact-fields">
          <div class="field-block field-span">
            <ConfigFieldLabel for-id="knowledge-namespace" text="检索范围" help="限定 Agent 的 search_knowledge 工具只检索哪个文档；选「全部文档」检索整个知识库。导入新知识后可回到这里切换范围。"/>
            <select id="knowledge-namespace" v-model="form.knowledgeNamespace">
              <option value="all">全部文档</option>
              <option v-for="doc in knowledgeDocs" :key="doc.docId" :value="doc.docId">
                {{ doc.title }}（{{ doc.chunkCount }} 片）
              </option>
            </select>
          </div>
          <div class="field-block field-span knowledge-range-hint">
            <span>还没有需要的知识？前往顶部「知识库」页批量导入后回到这里选择。</span>
          </div>
        </fieldset>
      </details>

      <details class="config-group">
        <summary>执行与上下文</summary>
        <fieldset :disabled="locked" class="compact-fields">
          <div class="field-block"><ConfigFieldLabel for-id="max-iterations" text="最大迭代" help="单个 Agent Turn 最多允许调用模型的迭代次数，用于阻止模型与工具之间无限循环。一次模型调用后执行工具再调用模型，通常会消耗新的迭代。"/><input id="max-iterations"
                                                                                      v-model.number="form.maxIterations"
                                                                                      type="number" min="1" max="100"
                                                                                      required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="max-steps" text="最大步骤" help="单个 Agent Turn 最多推进的运行状态 Step 数，覆盖模型、工具、挂起和恢复等阶段，是运行状态机的总保险丝。"/><input id="max-steps"
                                                                                 v-model.number="form.maxSteps"
                                                                                 type="number" min="1" max="500"
                                                                                 required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="attached-turns" text="挂载 Turn" help="构造模型上下文时最多附加的最近完整对话轮数。长对话中会优先保留最近 Turn，避免发送全部历史。"/><input id="attached-turns"
                                                                                       v-model.number="form.maxAttachedTurns"
                                                                                       type="number" min="1" max="100"
                                                                                       required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="attached-tokens" text="挂载 Token 上限" help="附加历史允许占用的最大估算 Token 数，0 表示不启用该限制。适合模型上下文窗口较小或需要严格控制请求成本的场景。"/><input id="attached-tokens"
                                                                                              v-model.number="form.maxAttachedTokens"
                                                                                              type="number" min="0" max="10000000"
                                                                                              step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="attached-messages" text="挂载消息" help="构造模型上下文时最多附加的历史消息条数，包含用户、AI 和工具消息。它会与 Turn、Token 上限共同约束历史大小。"/><input id="attached-messages"
                                                                                         v-model.number="form.maxAttachedMessages"
                                                                                         type="number" min="1" max="500"
                                                                                         required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="tool-error-strategy" text="工具最终失败" help="工具异常在重试后仍未恢复时的处理方式。终止 Run 适合要求严格完整性的任务；返回模型处理适合让模型调整参数或选择其他工具继续。"/><select
              id="tool-error-strategy" v-model="form.toolErrorStrategy">
            <option value="FAIL_RUN">终止 Run</option>
            <option value="RETURN_ERROR_TO_MODEL">返回模型处理</option>
          </select></div>
        </fieldset>
      </details>

      <details class="config-group">
        <summary>预算控制</summary>
        <fieldset :disabled="locked" class="compact-fields">
          <div class="field-block"><ConfigFieldLabel for-id="input-budget" text="输入 Token" help="单个 Agent Turn 可累计消耗的输入 Token 预算，0 表示不限制。多次模型迭代的输入会合计计算，用于控制长上下文成本。"/><input id="input-budget"
                                                                                      v-model.number="form.maxInputTokens"
                                                                                      type="number" min="0"
                                                                                      max="1000000" step="1"
                                                                                      required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="output-budget" text="输出 Token" help="单个 Agent Turn 可累计生成的输出 Token 预算，0 表示不限制。统计整个 Turn 内多次模型调用的输出总量。"/><input id="output-budget"
                                                                                       v-model.number="form.maxOutputTokens"
                                                                                       type="number" min="0"
                                                                                       max="1000000" step="1"
                                                                                       required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="total-budget" text="总 Token" help="单个 Agent Turn 输入与输出 Token 的合计预算，0 表示不限制。适合只关心任务总消耗的场景，并会与输入、输出独立预算同时检查。"/><input id="total-budget"
                                                                                    v-model.number="form.maxTotalTokens"
                                                                                    type="number" min="0"
                                                                                    max="1000000" step="1" required/>
          </div>
          <div class="field-block"><ConfigFieldLabel for-id="tool-budget" text="工具调用" help="单个 Agent Turn 允许实际执行的工具调用总数，0 表示不限制。并行调用中的每个工具都会分别计数，可防止重复调用循环。"/><input id="tool-budget"
                                                                                   v-model.number="form.maxToolCalls"
                                                                                   type="number" min="0" max="1000"
                                                                                   required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="duration-budget" text="执行时长（毫秒）" help="单个 Agent Turn 从创建到结束的整体最长时长，0 表示不限制。覆盖模型、工具、审批和用户输入等待，不等同于各阶段独立超时。"/><input
              id="duration-budget" v-model.number="form.maxDurationMillis" type="number" min="0" max="86400000"
              step="1" required/></div>
        </fieldset>
      </details>

      <details class="config-group">
        <summary>重试与压缩</summary>
        <fieldset :disabled="locked" class="compact-fields">
          <div class="field-block"><ConfigFieldLabel for-id="max-retries" text="最大重试" help="Agent 运行级失败后的最大重试次数，用于恢复可恢复的 Turn 失败，例如不稳定工具异常；它不同于只重发 HTTP 请求的模型重试。"/><input id="max-retries"
                                                                                   v-model.number="form.maxRetries"
                                                                                   type="number" min="0" max="10"
                                                                                   required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="retry-multiplier" text="退避倍率" help="Agent 重试等待时间的指数增长倍数。例如初始间隔 500ms、倍率 2 时，后续间隔按 500ms、1000ms、2000ms 增长，并受最大间隔限制。"/><input id="retry-multiplier"
                                                                                        v-model.number="form.retryMultiplier"
                                                                                        type="number" min="1" max="10"
                                                                                        step="0.1" required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="initial-delay" text="初始间隔（ms）" help="Agent 第一次重试前等待的毫秒数。给临时依赖故障留出恢复窗口；0 表示立即进行第一次重试。"/><input id="initial-delay"
                                                                                         v-model.number="form.initialDelayMillis"
                                                                                         type="number" min="0"
                                                                                         max="60000" step="1"
                                                                                         required/></div>
          <div class="field-block"><ConfigFieldLabel for-id="max-delay" text="最大间隔（ms）" help="Agent 单次重试等待的最大毫秒数，必须大于或等于初始间隔，用于限制指数退避增长；与初始间隔都为 0 时表示立即重试。"/><input id="max-delay"
                                                                                     v-model.number="form.maxDelayMillis"
                                                                                     type="number" min="0" max="300000"
                                                                                     step="1" required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="compression-decider" text="压缩触发条件" help="选择上下文何时触发摘要：按待压缩消息数、Turn 数或 Token 数触发，也可始终触发或从不触发。阈值字段会随选择动态显示。"/><select
              id="compression-decider" v-model="form.compressionDecider">
            <option value="PENDING_MESSAGES">待压缩消息数</option><option value="PENDING_TURNS">待压缩 Turn 数</option>
            <option value="PENDING_TOKENS">待压缩 Token 数</option><option value="ALWAYS">始终触发</option>
            <option value="NEVER">从不触发</option>
          </select></div>
          <div v-if="form.compressionDecider === 'PENDING_MESSAGES'" class="field-block field-span">
            <ConfigFieldLabel for-id="compression-message-threshold" text="压缩消息阈值" help="待压缩消息达到此数量时触发摘要，仅在压缩触发条件选择“待压缩消息数”时生效。适合消息颗粒度稳定的会话。"/><input id="compression-message-threshold"
              v-model.number="form.compressionMessageThreshold" type="number" min="0" max="1000000" step="1" required/>
          </div>
          <div v-if="form.compressionDecider === 'PENDING_TURNS'" class="field-block field-span">
            <ConfigFieldLabel for-id="compression-turn-threshold" text="压缩 Turn 阈值" help="待压缩完整 Turn 达到此数量时触发摘要，仅在选择“待压缩 Turn 数”时生效。适合工具消息较多但按对话轮次管理历史的场景。"/><input id="compression-turn-threshold"
              v-model.number="form.compressionTurnThreshold" type="number" min="0" max="1000000" step="1" required/>
          </div>
          <div v-if="form.compressionDecider === 'PENDING_TOKENS'" class="field-block field-span">
            <ConfigFieldLabel for-id="compression-token-threshold" text="压缩 Token 阈值" help="待压缩历史的估算 Token 达到此数量时触发摘要，仅在选择“待压缩 Token 数”时生效。适合依据上下文窗口或成本控制压缩时机。"/><input id="compression-token-threshold"
              v-model.number="form.compressionTokenThreshold" type="number" min="0" max="10000000" step="1" required/>
          </div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="compression-mode" text="摘要压缩模式" help="整段历史摘要会把旧历史总结成一段内容；逐消息摘要会保留消息角色和 ID 并逐条总结，适合需要追踪来源的场景。"/><select
              id="compression-mode" v-model="form.compressionMode">
            <option value="WHOLE_HISTORY">整段历史摘要</option><option value="PER_MESSAGE">逐消息摘要</option>
          </select></div>
        </fieldset>
      </details>

      <details class="config-group">
        <summary>高级执行策略</summary>
        <fieldset :disabled="locked" class="compact-fields">
          <div class="field-block"><ConfigFieldLabel for-id="model-timeout" text="模型调用超时（ms）" help="单次模型调用的最长执行时间，0 表示不设置阶段超时。用于防止连接或流式响应长时间无结果，超时后进入统一失败或重试流程。"/><input id="model-timeout"
              v-model.number="form.modelCallTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="tool-timeout" text="本地工具超时（ms）" help="单次本地工具调用的最长执行时间，0 表示不设置限制。适合限制检索、计算等工具长期占用工作线程。"/><input id="tool-timeout"
              v-model.number="form.toolExecutionTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="external-tool-timeout" text="外部工具超时（ms）" help="外部异步工具从发起到等待回传的最长时间，0 表示不设置期限。适用于工具由其他进程或人工系统执行、稍后按关联 ID 恢复的场景。"/><input id="external-tool-timeout"
              v-model.number="form.externalToolTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="approval-timeout" text="审批等待超时（ms）" help="工具进入审批挂起后等待用户允许或拒绝的最长时间，0 表示不设置期限。超时后的迟到恢复行为由挂起过期策略决定。"/><input id="approval-timeout"
              v-model.number="form.approvalTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="user-input-timeout" text="表单等待超时（ms）" help="Agent 请求用户补充表单后等待提交的最长时间，0 表示不设置期限。适用于 research_brief 等人机协作表单。"/><input id="user-input-timeout"
              v-model.number="form.userInputTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="suspension-expiration" text="挂起过期策略" help="审批、表单或外部工具等待期限已过后收到迟到恢复命令时的处理方式：拒绝恢复、失败当前 Turn 或取消当前 Turn。"/><select id="suspension-expiration" v-model="form.suspensionExpirationStrategy">
            <option value="REJECT_RESUME">拒绝迟到恢复</option><option value="FAIL_TURN">失败当前 Turn</option><option value="CANCEL_TURN">取消当前 Turn</option>
          </select></div>
          <div class="field-block"><ConfigFieldLabel for-id="tool-result-limit" text="本地工具结果上限（字符）" help="本地工具结果写回模型上下文的最大字符数，0 表示不限制。适合控制网页正文、检索结果等超长输出。"/><input id="tool-result-limit"
              v-model.number="form.toolResultMaxCharacters" type="number" min="0" max="10000000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="external-result-limit" text="外部工具结果上限（字符）" help="外部异步工具结果写回模型上下文的最大字符数，0 表示不限制。可针对第三方系统或大型报告设置独立边界。"/><input id="external-result-limit"
              v-model.number="form.externalToolResultMaxCharacters" type="number" min="0" max="10000000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="result-overflow" text="结果超限策略" help="工具结果超过字符上限时的处理方式：严格失败可保证数据完整，截断并继续适合只需要前部内容的检索任务。"/><select id="result-overflow" v-model="form.toolResultOverflowStrategy">
            <option value="FAIL">严格失败</option><option value="TRUNCATE">截断并继续</option>
          </select></div>
          <div class="field-block"><ConfigFieldLabel for-id="tool-execution-mode" text="工具执行模式" help="同一模型响应包含多个工具调用时，顺序执行适合有依赖或副作用的工具，并行执行适合互不依赖的检索或计算任务。"/><select id="tool-execution-mode" v-model="form.toolExecutionMode">
            <option value="SEQUENTIAL">顺序执行</option><option value="PARALLEL">并行执行</option>
          </select></div>
          <div class="field-block"><ConfigFieldLabel for-id="parallel-limit" text="并行工具上限" help="并行模式下同一批次最多同时执行的工具数，用于保护线程池和下游服务；顺序执行模式下不会产生并发。"/><input id="parallel-limit"
              v-model.number="form.maxParallelToolCalls" type="number" min="1" max="128"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="parallel-failure" text="并行失败策略" help="一批并行工具出现失败时的处理方式：快速失败并进入统一重试，或把成功结果与错误一起交回模型决定后续动作。"/><select id="parallel-failure" v-model="form.parallelFailureStrategy">
            <option value="FAIL_FAST">快速失败并重试</option><option value="RETURN_ERRORS_TO_MODEL">交回模型处理</option>
          </select></div>
          <label class="checkbox-field field-span" for="compact-tool-turns"><input id="compact-tool-turns"
              v-model="form.compactCompletedToolTurns" type="checkbox"/><span>压缩时归并已完成工具 Turn</span><ConfigHelpIcon label="压缩时归并已完成工具 Turn" help="将已完成的模型工具调用和工具结果折叠为更紧凑的历史表示，以减少工具密集型对话的上下文体积。"/></label>
          <div class="field-block"><ConfigFieldLabel for-id="keep-recent-turns" text="压缩保留最近 Turn 数" help="压缩时始终原样保留的最近完整 Turn 数，较早历史才会交给压缩器。0 表示不专门保留最近 Turn。"/><input id="keep-recent-turns"
              v-model.number="form.compressionKeepRecentTurns" type="number" min="0" max="100"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="compression-failure" text="压缩失败策略" help="压缩模型调用或摘要解析失败时的降级方式：严格失败可避免上下文不确定，使用原始上下文优先保证对话可用性。"/><select id="compression-failure" v-model="form.compressionFailureStrategy">
            <option value="FAIL">严格失败</option><option value="USE_ORIGINAL">使用原始上下文</option>
          </select></div>
          <div class="field-block"><ConfigFieldLabel for-id="compression-model-timeout" text="摘要模型超时（ms）" help="单次压缩模型调用的最长时间，0 表示不设置独立超时。可比主模型设置更短期限，避免后台摘要阻塞正常对话。"/><input id="compression-model-timeout"
              v-model.number="form.compressionModelCallTimeoutMillis" type="number" min="0" max="86400000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="compression-input-limit" text="压缩输入上限（字符）" help="一次压缩请求送入摘要模型的最大字符数，0 表示不限制。用于在 Token 预算之外保护压缩请求体大小。"/><input id="compression-input-limit"
              v-model.number="form.compressionMaxInputCharacters" type="number" min="0" max="10000000" step="1"/></div>
          <div class="field-block"><ConfigFieldLabel for-id="compression-output-limit" text="压缩输出上限（字符）" help="摘要模型返回内容的最大字符数，0 表示不限制。防止摘要本身过长而无法释放上下文空间。"/><input id="compression-output-limit"
              v-model.number="form.compressionMaxOutputCharacters" type="number" min="0" max="10000000" step="1"/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="compression-instruction" text="摘要要求" help="发送给压缩模型的保留规则，例如事实、用户约束、审批结果、来源和未完成事项。整段与逐消息压缩模式都会使用。"/><textarea id="compression-instruction"
              v-model.trim="form.compressionInstruction" rows="3" maxlength="4000" required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="compression-history-header" text="历史消息标题" help="整段历史压缩请求中放在原始历史正文前的分隔标题，用于让摘要模型清楚区分指令和待摘要内容。"/><textarea id="compression-history-header"
              v-model="form.compressionHistoryHeader" rows="2" maxlength="1000" required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="compression-summary-prefix" text="摘要上下文前缀" help="压缩摘要重新注入主模型上下文时使用的前缀，用于明确这部分是较早会话的历史事实，而不是当前用户的新输入。"/><input id="compression-summary-prefix"
              v-model.trim="form.compressionSummaryPrefix" maxlength="1000" required/></div>
          <div v-if="form.compressionMode === 'PER_MESSAGE'" class="field-block field-span">
            <ConfigFieldLabel for-id="compression-per-message-request" text="逐消息摘要返回要求" help="逐消息压缩时要求模型仅返回包含 messageId 和 summary 的 JSON 数组，Runtime 依靠 messageId 把摘要映射回原消息。"/><textarea
              id="compression-per-message-request" v-model="form.compressionPerMessageRequest"
              rows="3" maxlength="4000" required/>
          </div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="interrupted-tool-template" text="工具中断消息模板" help="工具未正常完成时注入模型上下文的提示模板，用于告知模型上次工具没有可信结果。支持 {reason} 占位符。"/><input id="interrupted-tool-template"
              v-model.trim="form.interruptedToolMessageTemplate" maxlength="1000" required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="interrupted-turn-template" text="Turn 中断消息模板" help="上一轮因取消、超时或异常未完成时追加的提示模板，避免模型误认为上一轮已经结束。支持 {reason} 占位符。"/><input id="interrupted-turn-template"
              v-model.trim="form.interruptedTurnMessageTemplate" maxlength="1000" required/></div>
          <div class="field-block field-span"><ConfigFieldLabel for-id="cancellation-reason" text="取消原因" help="调用方主动取消 Turn 时记录并传播的默认原因文本，会进入取消状态、事件和中断说明；它本身不会主动触发取消。"/><input id="cancellation-reason"
              v-model.trim="form.cancellationReason" maxlength="1000" required/></div>
        </fieldset>
      </details>

      <div class="fixed-capabilities">
        <span>固定 Showcase 能力</span>
        <p>动态表单、市场检索、来源重试、发布审批</p>
      </div>

      <button v-if="!agent || editing" class="primary-button full-width" type="submit" :disabled="locked">
        <IconRobot :size="18"/>
        {{ busy ? '正在创建...' : '创建 Agent' }}
      </button>
      <button v-else class="secondary-button full-width" type="button" :disabled="busy || disabled"
              @click="startEditing">
        <IconSettings :size="18"/>
        重新配置 Agent
      </button>

      <ModelConfigDialog :open="dialogOpen" :model="modelSnapshot" :embedding="embeddingSnapshot"
                         @close="dialogOpen = false" @apply="applyModelDialog"
                         @configure-embedding="(profile) => emit('configureEmbedding', profile)"/>
    </form>
  </section>
</template>
