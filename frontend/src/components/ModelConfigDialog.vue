<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {IconDatabase, IconDeviceFloppy, IconEye, IconEyeOff, IconSettings, IconTrash, IconX} from '@tabler/icons-vue'
import type {EmbeddingProfile, KnowledgeSearchMode, ModelProfile} from '@/types/agent'
import ConfigHelpIcon from '@/components/ConfigHelpIcon.vue'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'

const props = defineProps<{
  open: boolean
  model: ModelProfile
  embedding: EmbeddingProfile
  /** standalone=true 时作为独立页面内嵌渲染：无遮罩、无关闭/取消按钮（关闭由页面导航完成）。 */
  standalone?: boolean
}>()
const emit = defineEmits<{
  close: []
  apply: [model: ModelProfile, embedding: EmbeddingProfile]
  configureEmbedding: [embedding: EmbeddingProfile]
}>()

const PROFILE_STORAGE_KEY = 'agents-flex-demo.model-profiles.v1'

function preset(profileName: string, provider: string, endpoint: string, requestPath: string,
                modelName: string, thinkingEnabled = false): ModelProfile {
  return {
    profileName, modelProvider: provider, modelEndpoint: endpoint, modelRequestPath: requestPath,
    modelApiKey: '', modelName, modelTemperature: 0.2, modelThinkingEnabled: thinkingEnabled,
    modelThinkingProtocol: 'none', modelSeed: '', modelTopP: null, modelTopK: null,
    modelMaxTokens: null, modelStop: [], modelIncludeUsage: true, modelResponseFormat: 'NONE',
    modelRetryEnabled: true, modelRetryCount: 2, modelRetryInitialDelayMillis: 600,
    maxInputTokens: 16000, maxOutputTokens: 8192, maxTotalTokens: 32000, maxAttachedTokens: 0,
  }
}

function embeddingPreset(profileName: string, endpoint: string, model: string,
                         mode: KnowledgeSearchMode = 'HYBRID'): EmbeddingProfile {
  return {profileName, embeddingEndpoint: endpoint, embeddingApiKey: '', embeddingModel: model,
    knowledgeSearchMode: mode}
}

/** 内置预设：聊天模型覆盖主流服务商；向量模型覆盖 bge-m3 常见部署与云端 embedding。 */
const chatPresets: ModelProfile[] = [
  preset('DeepSeek Chat', 'deepseek', 'https://api.deepseek.com', '/chat/completions', 'deepseek-chat'),
  preset('DeepSeek Reasoner（深度思考）', 'deepseek', 'https://api.deepseek.com',
      '/chat/completions', 'deepseek-reasoner', true),
  preset('OpenAI gpt-4o-mini', 'openai', 'https://api.openai.com', '/chat/completions', 'gpt-4o-mini'),
  preset('本地 Ollama（OpenAI 兼容）', 'ollama', 'http://localhost:11434/v1',
      '/chat/completions', 'qwen2.5:7b'),
  preset('SiliconFlow DeepSeek-V3', 'siliconflow', 'https://api.siliconflow.cn',
      '/v1/chat/completions', 'deepseek-ai/DeepSeek-V3'),
]

const embeddingPresets: EmbeddingProfile[] = [
  embeddingPreset('BGE-M3（本地网关 18888）', 'http://127.0.0.1:18888/v1', 'bge-m3'),
  embeddingPreset('BGE-M3（Ollama）', 'http://localhost:11434/v1', 'bge-m3'),
  embeddingPreset('BGE-M3（Xinference）', 'http://localhost:9997/v1', 'bge-m3'),
  embeddingPreset('OpenAI text-embedding-3-small', 'https://api.openai.com/v1', 'text-embedding-3-small'),
  embeddingPreset('阿里百炼 text-embedding-v3', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      'text-embedding-v3'),
]

/** 用户档案统一存储；profileType 区分聊天与向量，旧版纯聊天数据自动兼容。 */
interface StoredProfile {
  profileType: 'chat' | 'embedding'
  data: ModelProfile | EmbeddingProfile
}

function readUserProfiles(): StoredProfile[] {
  try {
    const parsed = window.localStorage.getItem(PROFILE_STORAGE_KEY)
        ? JSON.parse(window.localStorage.getItem(PROFILE_STORAGE_KEY)!) : []
    if (!Array.isArray(parsed)) return []
    return parsed.map((item: Partial<StoredProfile> & Partial<ModelProfile>) =>
        item.profileType
            ? {profileType: item.profileType, data: (item as StoredProfile).data}
            : {profileType: 'chat' as const, data: item as unknown as ModelProfile})
        .filter((item) => item.data && (item.data as ModelProfile).profileName)
  } catch {
    return []
  }
}

const userProfiles = ref<StoredProfile[]>(readUserProfiles())

/** 弹窗编辑草稿；每次打开时从父组件快照初始化，关闭不直接改动父组件。 */
const draftModel = reactive<ModelProfile>({...props.model})
const draftEmbedding = reactive<EmbeddingProfile>({...props.embedding})
/** 停止序列以逗号分隔文本编辑，应用时转回数组。 */
const stopText = ref(Array.isArray(props.model.modelStop) ? props.model.modelStop.join(', ') : '')
const activeTab = ref<'chat' | 'embedding'>('chat')
const chatPresetKey = ref('')
const embeddingPresetKey = ref('')
/** 当前下拉选中的“我的档案”名称，供应用与删除使用。 */
const selectedChatProfile = ref('')
const selectedEmbeddingProfile = ref('')
const draftName = ref('')
const showModelKey = ref(false)
const showEmbeddingKey = ref(false)

/** 应用聊天预设：覆盖草稿的模型连接字段（密钥留空由用户填写）。 */
function applyChatPreset() {
  const profile = chatPresets[Number(chatPresetKey.value)]
  if (!profile) return
  const {profileName: _p, modelApiKey: _k, ...fields} = profile
  Object.assign(draftModel, fields, {modelApiKey: draftModel.modelApiKey})
  stopText.value = ''
}

/** 应用向量预设。 */
function applyEmbeddingPreset() {
  const profile = embeddingPresets[Number(embeddingPresetKey.value)]
  if (!profile) return
  const {profileName: _p, embeddingApiKey: _k, ...fields} = profile
  Object.assign(draftEmbedding, fields, {embeddingApiKey: draftEmbedding.embeddingApiKey})
}

/** 打开弹窗时以父组件快照重置草稿，避免上次未应用的编辑残留。 */
watch(() => props.open, (open) => {
  if (!open) return
  Object.assign(draftModel, props.model)
  Object.assign(draftEmbedding, props.embedding)
  stopText.value = Array.isArray(props.model.modelStop) ? props.model.modelStop.join(', ') : ''
  activeTab.value = 'chat'
  chatPresetKey.value = ''
  embeddingPresetKey.value = ''
}, {immediate: true})

/** 只保留模型字段的自定义档案列表，供下拉展示当前用户档案。 */
const userChatProfiles = computed(() => userProfiles.value
    .filter((item) => item.profileType === 'chat')
    .map((item) => item.data as ModelProfile))
const userEmbeddingProfiles = computed(() => userProfiles.value
    .filter((item) => item.profileType === 'embedding')
    .map((item) => item.data as EmbeddingProfile))

/** 从用户档案中选中并覆盖草稿。 */
function pickUserChat(name: string) {
  const profile = userChatProfiles.value.find((item) => item.profileName === name)
  if (!profile) return
  Object.assign(draftModel, {...profile, modelApiKey: draftModel.modelApiKey})
  stopText.value = Array.isArray(profile.modelStop) ? profile.modelStop.join(', ') : ''
}

function pickUserEmbedding(name: string) {
  const profile = userEmbeddingProfiles.value.find((item) => item.profileName === name)
  if (!profile) return
  Object.assign(draftEmbedding, {...profile, embeddingApiKey: draftEmbedding.embeddingApiKey})
}

function persistUserProfiles() {
  try {
    window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(userProfiles.value))
  } catch {
    // 存储不可用时档案仅在本次会话可用。
  }
}

/** 以当前草稿另存为档案；同名覆盖。 */
function saveProfile() {
  const name = draftName.value.trim()
  if (!name) return
  const data = activeTab.value === 'chat'
      ? {...draftModel, profileName: name}
      : {...draftEmbedding, profileName: name}
  const existing = userProfiles.value.findIndex((stored) =>
      stored.profileType === activeTab.value && (stored.data as ModelProfile).profileName === name)
  if (existing >= 0) userProfiles.value[existing] = {profileType: activeTab.value, data}
  else userProfiles.value.push({profileType: activeTab.value, data})
  persistUserProfiles()
  if (activeTab.value === 'chat') selectedChatProfile.value = name
  else selectedEmbeddingProfile.value = name
  draftName.value = ''
}

/** 删除指定类型下名为 name 的用户档案。 */
function removeProfile(type: 'chat' | 'embedding', name: string) {
  const index = userProfiles.value.findIndex((stored) =>
      stored.profileType === type && (stored.data as ModelProfile).profileName === name)
  if (index >= 0) userProfiles.value.splice(index, 1)
  persistUserProfiles()
}

/** 应用草稿到父组件并关闭弹窗。 */
function apply() {
  const stop = stopText.value.split(',').map((item) => item.trim()).filter(Boolean)
  emit('apply', {...draftModel, modelStop: stop}, {...draftEmbedding})
}

/** 立即把当前向量草稿应用到知识库（不等创建 Agent），用于独立调试 embedding。 */
function configureEmbeddingNow() {
  const profile = {...draftEmbedding}
  autoSaveEmbeddingProfile(profile)
  emit('configureEmbedding', profile)
}

/**
 * 应用前自动把当前向量配置纳入“我的档案”，使自定义配置出现在档案下拉中可复用；
 * 用户已输入档案名称时优先使用，否则按模型名自动命名。
 */
function autoSaveEmbeddingProfile(profile: EmbeddingProfile) {
  const name = draftName.value.trim() || `自定义 ${profile.embeddingModel || '向量模型'}`
  const data = {...profile, profileName: name}
  const index = userProfiles.value.findIndex((stored) =>
      stored.profileType === 'embedding' && (stored.data as ModelProfile).profileName === name)
  if (index >= 0) userProfiles.value[index] = {profileType: 'embedding', data}
  else userProfiles.value.push({profileType: 'embedding', data})
  persistUserProfiles()
  selectedEmbeddingProfile.value = name
  draftName.value = ''
}

function close() {
  emit('close')
}
</script>

<template>
  <Teleport to="body" :disabled="props.standalone">
    <div v-if="props.open" :class="props.standalone ? 'standalone-shell' : 'dialog-overlay'"
         @click.self="!props.standalone && close()">
      <section class="model-dialog" role="dialog" aria-modal="true" aria-label="模型配置">
        <header class="dialog-header">
          <h2><IconSettings :size="18"/>模型配置</h2>
          <button v-if="!props.standalone" class="dialog-close" type="button" aria-label="关闭" @click="close"><IconX :size="18"/></button>
        </header>

        <nav class="dialog-tabs" role="tablist">
          <button type="button" :class="{active: activeTab === 'chat'}" role="tab"
                  :aria-selected="activeTab === 'chat'" @click="activeTab = 'chat'">聊天模型</button>
          <button type="button" :class="{active: activeTab === 'embedding'}" role="tab"
                  :aria-selected="activeTab === 'embedding'" @click="activeTab = 'embedding'">向量模型</button>
        </nav>

        <!-- 聊天模型配置 -->
        <div v-if="activeTab === 'chat'" class="dialog-body">
          <div class="preset-row">
            <ConfigFieldLabel for-id="chat-preset" text="预设快捷填充"
                              help="选择预设会覆盖下方聊天模型字段（API Key 留空由你填写）；也可以直接在下方自由配置任意 OpenAI 兼容服务，配置后可另存为个人档案。"/>
            <select id="chat-preset" v-model="chatPresetKey" @change="applyChatPreset">
              <option value="" disabled>选择预设…</option>
              <option v-for="(profile, index) in chatPresets" :key="`p${index}`" :value="String(index)">
                {{ profile.profileName }}
              </option>
            </select>
          </div>
          <div v-if="userChatProfiles.length" class="preset-row">
            <ConfigFieldLabel for-id="chat-user-profile" text="我的档案" help="已另存的聊天模型档案，选中即覆盖下方字段。"/>
            <select v-model="selectedChatProfile" @change="pickUserChat(selectedChatProfile)">
              <option value="" disabled>选择我的档案…</option>
              <option v-for="profile in userChatProfiles" :key="profile.profileName" :value="profile.profileName">
                {{ profile.profileName }}
              </option>
            </select>
            <button v-if="selectedChatProfile" class="dialog-icon-button danger" type="button"
                    title="删除选中的我的档案" aria-label="删除选中的我的档案"
                    @click="removeProfile('chat', selectedChatProfile); selectedChatProfile = ''">
              <IconTrash :size="15"/>
            </button>
          </div>
          <div class="dialog-fields">
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-provider" text="服务商" help="OpenAI 兼容服务商标识，例如 deepseek、openai、ollama 或自定义网关名称。"/>
              <input id="dl-provider" v-model.trim="draftModel.modelProvider" maxlength="80" placeholder="deepseek"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-model" text="模型" help="模型 ID，例如 deepseek-chat 或 qwen2.5:7b。"/>
              <input id="dl-model" v-model.trim="draftModel.modelName" maxlength="200" placeholder="deepseek-chat"/>
            </div>
            <div class="field-block field-span">
              <ConfigFieldLabel for-id="dl-endpoint" text="API 地址" help="OpenAI 兼容服务根地址（含 /v1）。"/>
              <input id="dl-endpoint" v-model.trim="draftModel.modelEndpoint" type="url" maxlength="2048"
                     placeholder="https://api.deepseek.com"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-path" text="请求路径" help="Chat Completions 路径，通常保持 /chat/completions。"/>
              <input id="dl-path" v-model.trim="draftModel.modelRequestPath" maxlength="512" placeholder="/chat/completions"/>
            </div>
            <div class="field-block api-key-field">
              <ConfigFieldLabel for-id="dl-api-key" text="API Key" help="模型服务密钥；只随创建 Agent 发送到后端内存，不会回显。"/>
              <div class="secret-input">
                <input id="dl-api-key" v-model.trim="draftModel.modelApiKey" :type="showModelKey ? 'text' : 'password'"
                       autocomplete="off" maxlength="4096" placeholder="可选"/>
                <button type="button" :aria-label="showModelKey ? '隐藏' : '显示'" @click="showModelKey = !showModelKey">
                  <IconEyeOff v-if="showModelKey" :size="17"/><IconEye v-else :size="17"/>
                </button>
              </div>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-temperature" text="温度" help="0 到 2，控制随机性；研究类任务建议 0.1~0.3。"/>
              <input id="dl-temperature" v-model.number="draftModel.modelTemperature" type="number" min="0" max="2" step="0.1"/>
            </div>
            <label class="checkbox-field field-span" for="dl-thinking">
              <input id="dl-thinking" v-model="draftModel.modelThinkingEnabled" type="checkbox"/>
              <span>启用模型思考模式</span>
              <ConfigHelpIcon label="启用模型思考模式" help="请求支持 reasoning 内容的模型启用思考；不支持的供应商可能忽略。"/>
            </label>
            <div v-if="draftModel.modelThinkingEnabled" class="field-block field-span">
              <ConfigFieldLabel for-id="dl-thinking-protocol" text="思考协议" help="供应商思考协议名称，通常保持服务端默认。"/>
              <input id="dl-thinking-protocol" v-model.trim="draftModel.modelThinkingProtocol" maxlength="80" placeholder="留空使用服务端默认"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-seed" text="随机种子" help="供应商支持时可提高可复现性；留空不发送。"/>
              <input id="dl-seed" v-model.trim="draftModel.modelSeed" maxlength="200" placeholder="可选"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-top-p" text="Top P" help="核采样阈值 0~1；留空使用供应商默认。"/>
              <input id="dl-top-p" v-model.number="draftModel.modelTopP" type="number" min="0" max="1" step="0.01" placeholder="可选"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-top-k" text="Top K" help="候选 Token 上限；留空不发送。"/>
              <input id="dl-top-k" v-model.number="draftModel.modelTopK" type="number" min="1" max="1000000" placeholder="可选"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-max-tokens" text="单次最大输出 Token" help="单次响应上限；留空使用供应商默认。"/>
              <input id="dl-max-tokens" v-model.number="draftModel.modelMaxTokens" type="number" min="1" max="1000000" placeholder="可选"/>
            </div>
            <div class="field-block field-span">
              <ConfigFieldLabel for-id="dl-stop" text="停止序列" help="命中即停止；逗号分隔，最多 20 项。"/>
              <input id="dl-stop" v-model="stopText" maxlength="4019" placeholder="可选"/>
            </div>
            <label class="checkbox-field field-span" for="dl-include-usage">
              <input id="dl-include-usage" v-model="draftModel.modelIncludeUsage" type="checkbox"/>
              <span>流式响应返回 Usage 统计</span>
            </label>
            <label class="checkbox-field field-span" for="dl-retry-enabled">
              <input id="dl-retry-enabled" v-model="draftModel.modelRetryEnabled" type="checkbox"/>
              <span>启用模型 HTTP 请求重试</span>
            </label>
            <div v-if="draftModel.modelRetryEnabled" class="field-block">
              <ConfigFieldLabel for-id="dl-retry-count" text="重试次数" help="0~20。"/>
              <input id="dl-retry-count" v-model.number="draftModel.modelRetryCount" type="number" min="0" max="20" step="1"/>
            </div>
            <div v-if="draftModel.modelRetryEnabled" class="field-block">
              <ConfigFieldLabel for-id="dl-retry-delay" text="重试间隔（ms）" help="首次重试前等待。"/>
              <input id="dl-retry-delay" v-model.number="draftModel.modelRetryInitialDelayMillis" type="number" min="0" max="300000" step="1"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-response-format" text="响应格式" help="模型默认表示不指定；JSON 对象表示强制模型返回可解析的 JSON。"/>
              <select id="dl-response-format" v-model="draftModel.modelResponseFormat">
                <option value="NONE">模型默认</option>
                <option value="JSON_OBJECT">JSON 对象</option>
              </select>
            </div>
            <div class="field-block field-span section-divider">
              <span class="group-title">输出与上下文预算</span>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-budget-output" text="输出 Token 上限" help="整个 Agent Turn 可累计生成的输出上限；回答被截断或出现 BUDGET_EXCEEDED 时调大。0 表示不限制。"/>
              <input id="dl-budget-output" v-model.number="draftModel.maxOutputTokens" type="number" min="0" max="1000000" step="1"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-budget-input" text="输入 Token 上限" help="整个 Agent Turn 可累计消耗的输入上限；长上下文或多次工具调用时调大。0 表示不限制。"/>
              <input id="dl-budget-input" v-model.number="draftModel.maxInputTokens" type="number" min="0" max="1000000" step="1"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-budget-total" text="总 Token 上限" help="输入与输出的合计预算；0 表示不限制。"/>
              <input id="dl-budget-total" v-model.number="draftModel.maxTotalTokens" type="number" min="0" max="1000000" step="1"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-budget-attached" text="上下文挂载 Token" help="每轮发送给模型的历史上下文上限；0 表示不限制（由模型窗口决定）。长对话丢历史时调大。"/>
              <input id="dl-budget-attached" v-model.number="draftModel.maxAttachedTokens" type="number" min="0" max="10000000" step="1"/>
            </div>
          </div>
        </div>

        <!-- 向量模型配置 -->
        <div v-else class="dialog-body">
          <div class="preset-row">
            <ConfigFieldLabel for-id="emb-preset" text="预设快捷填充"
                              help="bge-m3 常见部署与主流云端 embedding 预设；也可以自定义任意 OpenAI 兼容 /embeddings 服务。"/>
            <select id="emb-preset" v-model="embeddingPresetKey" @change="applyEmbeddingPreset">
              <option value="" disabled>选择预设…</option>
              <option v-for="(profile, index) in embeddingPresets" :key="`p${index}`" :value="String(index)">
                {{ profile.profileName }}
              </option>
            </select>
          </div>
          <div v-if="userEmbeddingProfiles.length" class="preset-row">
            <ConfigFieldLabel for-id="emb-user-profile" text="我的档案" help="已另存的向量模型档案。"/>
            <select v-model="selectedEmbeddingProfile" @change="pickUserEmbedding(selectedEmbeddingProfile)">
              <option value="" disabled>选择我的档案…</option>
              <option v-for="profile in userEmbeddingProfiles" :key="profile.profileName" :value="profile.profileName">
                {{ profile.profileName }}
              </option>
            </select>
            <button v-if="selectedEmbeddingProfile" class="dialog-icon-button danger" type="button"
                    title="删除选中的我的档案" aria-label="删除选中的我的档案"
                    @click="removeProfile('embedding', selectedEmbeddingProfile); selectedEmbeddingProfile = ''">
              <IconTrash :size="15"/>
            </button>
          </div>
          <div class="dialog-fields">
            <div class="field-block field-span">
              <ConfigFieldLabel for-id="dl-emb-endpoint" text="服务地址" help="OpenAI 兼容 Embedding 服务根地址（含 /v1）。"/>
              <input id="dl-emb-endpoint" v-model.trim="draftEmbedding.embeddingEndpoint" type="url" maxlength="2048"
                     placeholder="http://127.0.0.1:18888/v1"/>
            </div>
            <div class="field-block">
              <ConfigFieldLabel for-id="dl-emb-model" text="向量模型" help="例如 bge-m3（1024 维，多语言）。维度首次调用自动探测；切换模型需重建知识库。"/>
              <input id="dl-emb-model" v-model.trim="draftEmbedding.embeddingModel" maxlength="200" placeholder="bge-m3"/>
            </div>
            <div class="field-block api-key-field">
              <ConfigFieldLabel for-id="dl-emb-key" text="API Key" help="Embedding 服务密钥；免鉴权服务可留空。"/>
              <div class="secret-input">
                <input id="dl-emb-key" v-model.trim="draftEmbedding.embeddingApiKey" :type="showEmbeddingKey ? 'text' : 'password'"
                       autocomplete="off" maxlength="4096" placeholder="可选"/>
                <button type="button" :aria-label="showEmbeddingKey ? '隐藏' : '显示'" @click="showEmbeddingKey = !showEmbeddingKey">
                  <IconEyeOff v-if="showEmbeddingKey" :size="17"/><IconEye v-else :size="17"/>
                </button>
              </div>
            </div>
            <div class="field-block field-span">
              <ConfigFieldLabel for-id="dl-emb-mode" text="检索模式" help="HYBRID 向量+关键词混合（推荐）；VECTOR_ONLY 纯向量；KEYWORD_ONLY 纯关键词（无需向量服务）。"/>
              <select id="dl-emb-mode" v-model="draftEmbedding.knowledgeSearchMode">
                <option value="HYBRID">混合检索（推荐）</option>
                <option value="VECTOR_ONLY">纯向量</option>
                <option value="KEYWORD_ONLY">纯关键词</option>
              </select>
            </div>
            <div class="field-block field-span">
              <button class="secondary-button full-width" type="button"
                      :disabled="!draftEmbedding.embeddingEndpoint.trim() || !draftEmbedding.embeddingModel.trim()"
                      @click="configureEmbeddingNow">
                <IconDatabase :size="16"/>应用到知识库（立即生效）
              </button>
              <p class="dialog-note">不必等待创建 Agent；应用后可在左侧「RAG 知识库」查看就绪状态、维度与文档数。</p>
            </div>
          </div>
        </div>

        <footer class="dialog-footer">
          <div class="save-profile-row">
            <input v-model="draftName" placeholder="另存为档案名称（可选）" maxlength="60" aria-label="档案名称"/>
            <button class="secondary-button" type="button" :disabled="!draftName.trim()"
                    @click="saveProfile"><IconDeviceFloppy :size="15"/>保存档案</button>
          </div>
          <div class="dialog-actions">
            <button v-if="!props.standalone" class="secondary-button" type="button" @click="close">取消</button>
            <button class="primary-button" type="button" @click="apply">应用配置</button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
  padding: 24px;
}

/* standalone 页面模式：无遮罩，卡片随页面容器自适应铺开。 */
.standalone-shell {
  display: block;
}

.standalone-shell .model-dialog {
  width: 100%;
  max-width: none;
  max-height: none;
  box-shadow: none;
  border: 1px solid #e5e7eb;
}

.model-dialog {
  background: #fff;
  border-radius: 14px;
  width: min(680px, 100%);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.25);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
}

.dialog-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.dialog-close {
  border: none;
  background: none;
  cursor: pointer;
  color: #6b7280;
  display: inline-flex;
  padding: 4px;
  border-radius: 6px;
}

.dialog-close:hover {
  background: #f3f4f6;
}

.dialog-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 18px 0;
  border-bottom: 1px solid #e5e7eb;
}

.dialog-tabs button {
  border: none;
  background: none;
  padding: 8px 14px;
  font-size: 13.5px;
  color: #6b7280;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.dialog-tabs button.active {
  color: #047857;
  border-bottom-color: #059669;
  font-weight: 600;
}

.dialog-body {
  padding: 14px 18px;
  overflow-y: auto;
  flex: 1;
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.preset-row select {
  min-width: 220px;
}

.dialog-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
}

.section-divider {
  border-top: 1px dashed #d1d5db;
  padding-top: 10px;
  margin-top: 4px;
}

.group-title {
  font-size: 12.5px;
  font-weight: 650;
  color: #374151;
}

.dialog-fields .field-span {
  grid-column: 1 / -1;
}

.dialog-fields input[type=text],
.dialog-fields input:not([type]),
.dialog-fields input[type=url],
.dialog-fields input[type=password],
.dialog-fields input[type=number],
.dialog-fields select,
.dialog-fields textarea {
  width: 100%;
}

.secret-input {
  display: flex;
  align-items: center;
  gap: 4px;
}

.secret-input input {
  flex: 1;
}

.secret-input button {
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 6px;
  padding: 4px;
  cursor: pointer;
  color: #6b7280;
  display: inline-flex;
}

.dialog-icon-button {
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 6px;
  padding: 4px;
  cursor: pointer;
  color: #6b7280;
  display: inline-flex;
}

.dialog-icon-button.danger:hover {
  border-color: #ef4444;
  color: #b91c1c;
}

.checkbox-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #374151;
}

.dialog-note {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: #6b7280;
  line-height: 1.5;
}

.dialog-footer {
  border-top: 1px solid #e5e7eb;
  padding: 12px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.save-profile-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.save-profile-row input {
  flex: 1;
  min-width: 0;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
