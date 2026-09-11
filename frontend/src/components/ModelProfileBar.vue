<script setup lang="ts">
import {computed, ref} from 'vue'
import {IconDeviceFloppy, IconSwitchHorizontal, IconTrash, IconX} from '@tabler/icons-vue'
import type {EmbeddingProfile, KnowledgeSearchMode, ModelProfile} from '@/types/agent'
import ConfigHelpIcon from '@/components/ConfigHelpIcon.vue'

const props = defineProps<{
  disabled: boolean
  current: ModelProfile
  currentEmbedding: EmbeddingProfile
}>()
const emit = defineEmits<{
  apply: [profile: ModelProfile]
  applyEmbedding: [profile: EmbeddingProfile]
}>()

const PROFILE_STORAGE_KEY = 'agents-flex-demo.model-profiles.v1'

/** 基于共同字段生成一个预设档案，保持内置预设声明紧凑。 */
function preset(profileName: string, provider: string, endpoint: string, requestPath: string,
                modelName: string, thinkingEnabled = false): ModelProfile {
  return {
    profileName,
    modelProvider: provider,
    modelEndpoint: endpoint,
    modelRequestPath: requestPath,
    modelApiKey: '',
    modelName,
    modelTemperature: 0.2,
    modelThinkingEnabled: thinkingEnabled,
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
  }
}

/** 向量模型预设：bge-m3 常见部署 + 主流云端 embedding 服务；API Key 一律留空由用户填写。 */
function embeddingPreset(profileName: string, endpoint: string, model: string,
                         mode: KnowledgeSearchMode = 'HYBRID'): EmbeddingProfile {
  return {
    profileName,
    embeddingEndpoint: endpoint,
    embeddingApiKey: '',
    embeddingModel: model,
    knowledgeSearchMode: mode,
  }
}

/** 内置预设不可删除；用户档案保存在当前浏览器 localStorage，可随时另存与删除。 */
const builtinPresets: ModelProfile[] = [
  preset('DeepSeek Chat', 'deepseek', 'https://api.deepseek.com', '/chat/completions', 'deepseek-chat'),
  preset('DeepSeek Reasoner（深度思考）', 'deepseek', 'https://api.deepseek.com',
      '/chat/completions', 'deepseek-reasoner', true),
  preset('OpenAI gpt-4o-mini', 'openai', 'https://api.openai.com', '/chat/completions', 'gpt-4o-mini'),
  preset('本地 Ollama（OpenAI 兼容）', 'ollama', 'http://localhost:11434/v1',
      '/chat/completions', 'qwen2.5:7b'),
  preset('SiliconFlow DeepSeek-V3', 'siliconflow', 'https://api.siliconflow.cn',
      '/v1/chat/completions', 'deepseek-ai/DeepSeek-V3'),
]

const builtinEmbeddingPresets: EmbeddingProfile[] = [
  embeddingPreset('BGE-M3（本地网关 18888）', 'http://127.0.0.1:18888/v1', 'bge-m3'),
  embeddingPreset('BGE-M3（Ollama）', 'http://localhost:11434/v1', 'bge-m3'),
  embeddingPreset('BGE-M3（Xinference）', 'http://localhost:9997/v1', 'bge-m3'),
  embeddingPreset('OpenAI text-embedding-3-small', 'https://api.openai.com/v1', 'text-embedding-3-small'),
  embeddingPreset('阿里百炼 text-embedding-v3', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      'text-embedding-v3'),
]

/** 用户档案统一存储结构；profileType 区分聊天与向量，旧数据缺省视为 chat。 */
interface StoredProfile {
  profileType: 'chat' | 'embedding'
  data: ModelProfile | EmbeddingProfile
}

/** 从浏览器恢复用户保存的档案；损坏数据自动回退为空列表。 */
function readUserProfiles(): StoredProfile[] {
  try {
    const raw = window.localStorage.getItem(PROFILE_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) as Array<Partial<StoredProfile> & Partial<ModelProfile> & Partial<EmbeddingProfile>> : []
    if (!Array.isArray(parsed)) return []
    // 兼容 v1 旧格式：数组项直接就是聊天档案字段。
    return parsed.map((item) => item.profileType
        ? {profileType: item.profileType, data: (item as StoredProfile).data}
        : {profileType: 'chat' as const, data: item as unknown as ModelProfile})
        .filter((item) => item.data && (item.data as ModelProfile).profileName)
  } catch {
    return []
  }
}

const userProfiles = ref<StoredProfile[]>(readUserProfiles())
const selectedChatKey = ref('')
const selectedEmbeddingKey = ref('')
const savingChat = ref(false)
const savingEmbedding = ref(false)
const draftChatName = ref('')
const draftEmbeddingName = ref('')

/** 按类型筛选用户档案，下拉项以“类型:下标”作为稳定 key。 */
function userProfilesOfType(type: 'chat' | 'embedding') {
  return userProfiles.value
      .map((item, index) => ({index, item}))
      .filter(({item}) => item.profileType === type)
}

const chatGroups = computed(() => [
  {group: '聊天预设', items: builtinPresets.map((profile, index) => ({key: `builtin:${index}`, profile}))},
  {group: '我的聊天档案', items: userProfilesOfType('chat').map(({index, item}) =>
      ({key: `user:${index}`, profile: item.data as ModelProfile}))},
])

const embeddingGroups = computed(() => [
  {group: '向量预设', items: builtinEmbeddingPresets.map((profile, index) => ({key: `builtin:${index}`, profile}))},
  {group: '我的向量档案', items: userProfilesOfType('embedding').map(({index, item}) =>
      ({key: `user:${index}`, profile: item.data as EmbeddingProfile}))},
])

function pickProfile<T>(groups: Array<{items: Array<{key: string; profile: T}>}>, key: string): T | null {
  if (!key) return null
  for (const group of groups) {
    const found = group.items.find((item) => item.key === key)
    if (found) return found.profile
  }
  return null
}

/** 选中即应用：聊天档案覆盖模型连接字段，向量档案覆盖 embedding 字段。 */
function applyChatSelected() {
  const profile = pickProfile(chatGroups.value, selectedChatKey.value)
  if (profile) emit('apply', profile)
}

function applyEmbeddingSelected() {
  const profile = pickProfile(embeddingGroups.value, selectedEmbeddingKey.value)
  if (profile) emit('applyEmbedding', profile)
}

/** 只有“我的档案”允许删除；内置预设始终保留。 */
const canDeleteChat = computed(() => selectedChatKey.value.startsWith('user:'))
const canDeleteEmbedding = computed(() => selectedEmbeddingKey.value.startsWith('user:'))

/** 把用户档案写回 localStorage；隐私模式等异常下静默降级为仅本次会话可用。 */
function persistUserProfiles() {
  try {
    window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(userProfiles.value))
  } catch {
    // 存储不可用时档案仍保留在内存中，本次会话内可继续切换。
  }
}

/** 另存当前表单快照为指定类型档案；同名覆盖而不是重复堆积。 */
function saveProfile(type: 'chat' | 'embedding', name: string) {
  const trimmed = name.trim()
  if (!trimmed) return
  const data: ModelProfile | EmbeddingProfile = type === 'chat'
      ? {...props.current, profileName: trimmed}
      : {...props.currentEmbedding, profileName: trimmed}
  const existing = userProfiles.value.findIndex((stored) =>
      stored.profileType === type && (stored.data as ModelProfile).profileName === trimmed)
  if (existing >= 0) {
    userProfiles.value[existing] = {profileType: type, data}
    if (type === 'chat') selectedChatKey.value = `user:${existing}`
    else selectedEmbeddingKey.value = `user:${existing}`
  } else {
    userProfiles.value.push({profileType: type, data})
    const index = userProfiles.value.length - 1
    if (type === 'chat') selectedChatKey.value = `user:${index}`
    else selectedEmbeddingKey.value = `user:${index}`
  }
  persistUserProfiles()
}

function confirmSaveChat() {
  saveProfile('chat', draftChatName.value)
  savingChat.value = false
}

function confirmSaveEmbedding() {
  saveProfile('embedding', draftEmbeddingName.value)
  savingEmbedding.value = false
}

/** 删除选中的用户档案；删除后回到占位提示，不改动表单字段。 */
function removeSelected(type: 'chat' | 'embedding') {
  const key = type === 'chat' ? selectedChatKey.value : selectedEmbeddingKey.value
  if (!key.startsWith('user:')) return
  const index = Number(key.slice('user:'.length))
  userProfiles.value.splice(index, 1)
  persistUserProfiles()
  if (type === 'chat') selectedChatKey.value = ''
  else selectedEmbeddingKey.value = ''
}
</script>

<template>
  <div class="model-profile-bar" :data-disabled="props.disabled">
    <div class="profile-row">
      <label class="profile-select-label" for="model-profile-select">
        <IconSwitchHorizontal :size="15" aria-hidden="true"/>
        聊天模型档案
        <ConfigHelpIcon label="聊天模型配置档案"
                        help="选择内置预设或已保存的档案，一键切换下方全部模型连接字段（服务商、地址、密钥、模型与采样参数）。用软盘按钮把当前配置另存为档案；档案保存在当前浏览器，不会写入服务端。"/>
      </label>
      <div class="profile-controls">
        <select id="model-profile-select" v-model="selectedChatKey" :disabled="props.disabled"
                aria-label="聊天模型配置档案" @change="applyChatSelected">
          <option value="" disabled>选择聊天模型配置…</option>
          <optgroup v-for="group in chatGroups" v-show="group.items.length" :key="group.group" :label="group.group">
            <option v-for="item in group.items" :key="item.key" :value="item.key">
              {{ item.profile.profileName }}
            </option>
          </optgroup>
        </select>
        <button type="button" class="profile-action" :disabled="props.disabled"
                title="把当前聊天模型配置另存为档案" aria-label="把当前聊天模型配置另存为档案"
                @click="savingChat = true; draftChatName = `${props.current.modelProvider} · ${props.current.modelName}`">
          <IconDeviceFloppy :size="16"/>
        </button>
        <button v-if="canDeleteChat" type="button" class="profile-action danger" :disabled="props.disabled"
                title="删除选中的聊天档案" aria-label="删除选中的聊天档案" @click="removeSelected('chat')">
          <IconTrash :size="16"/>
        </button>
      </div>
    </div>
    <div v-if="savingChat" class="profile-save-row">
      <input v-model="draftChatName" :disabled="props.disabled" placeholder="聊天档案名称" maxlength="60"
             aria-label="聊天档案名称" @keydown.enter.prevent="confirmSaveChat"/>
      <button class="secondary-button" type="button" :disabled="props.disabled || !draftChatName.trim()"
              @click="confirmSaveChat">保存
      </button>
      <button class="text-button profile-cancel" type="button" :disabled="props.disabled"
              aria-label="取消保存聊天档案" title="取消" @click="savingChat = false">
        <IconX :size="16"/>
      </button>
    </div>

    <div class="profile-row">
      <label class="profile-select-label" for="embedding-profile-select">
        <IconSwitchHorizontal :size="15" aria-hidden="true"/>
        向量模型档案
        <ConfigHelpIcon label="向量模型配置档案"
                        help="RAG 知识库使用的 Embedding 模型。内置 bge-m3 常见部署预设；选择档案后点击“应用到知识库”立即生效，也会随创建 Agent 一并提交。API Key 仅保存在当前浏览器。"/>
      </label>
      <div class="profile-controls">
        <select id="embedding-profile-select" v-model="selectedEmbeddingKey" :disabled="props.disabled"
                aria-label="向量模型配置档案" @change="applyEmbeddingSelected">
          <option value="" disabled>选择向量模型配置…</option>
          <optgroup v-for="group in embeddingGroups" v-show="group.items.length" :key="group.group" :label="group.group">
            <option v-for="item in group.items" :key="item.key" :value="item.key">
              {{ item.profile.profileName }}
            </option>
          </optgroup>
        </select>
        <button type="button" class="profile-action" :disabled="props.disabled"
                title="把当前向量模型配置另存为档案" aria-label="把当前向量模型配置另存为档案"
                @click="savingEmbedding = true; draftEmbeddingName = props.currentEmbedding.embeddingModel">
          <IconDeviceFloppy :size="16"/>
        </button>
        <button v-if="canDeleteEmbedding" type="button" class="profile-action danger" :disabled="props.disabled"
                title="删除选中的向量档案" aria-label="删除选中的向量档案" @click="removeSelected('embedding')">
          <IconTrash :size="16"/>
        </button>
      </div>
    </div>
    <div v-if="savingEmbedding" class="profile-save-row">
      <input v-model="draftEmbeddingName" :disabled="props.disabled" placeholder="向量档案名称" maxlength="60"
             aria-label="向量档案名称" @keydown.enter.prevent="confirmSaveEmbedding"/>
      <button class="secondary-button" type="button" :disabled="props.disabled || !draftEmbeddingName.trim()"
              @click="confirmSaveEmbedding">保存
      </button>
      <button class="text-button profile-cancel" type="button" :disabled="props.disabled"
              aria-label="取消保存向量档案" title="取消" @click="savingEmbedding = false">
        <IconX :size="16"/>
      </button>
    </div>
    <p class="profile-hint">
      档案保存在当前浏览器；切换聊天档案覆盖模型连接字段，切换向量档案覆盖知识库 Embedding 字段。
      已有会话不受影响，重新配置 Agent 并新建会话后按新模型执行。
    </p>
  </div>
</template>

<style scoped>
.profile-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.profile-select-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--ink-soft, #4b5563);
  white-space: nowrap;
}

.profile-controls {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
  justify-content: flex-end;
}

.profile-controls select {
  max-width: 220px;
  min-width: 0;
  font-size: 12.5px;
  padding: 4px 6px;
}

.profile-action {
  display: inline-flex;
  align-items: center;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  padding: 4px;
  cursor: pointer;
  color: #4b5563;
}

.profile-action:hover:not(:disabled) {
  border-color: #10b981;
  color: #047857;
}

.profile-action.danger:hover:not(:disabled) {
  border-color: #ef4444;
  color: #b91c1c;
}

.profile-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.profile-save-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.profile-save-row input {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  padding: 4px 8px;
}

.profile-cancel {
  border: none;
  background: none;
  cursor: pointer;
  color: #6b7280;
  display: inline-flex;
  padding: 4px;
}

.profile-hint {
  font-size: 11.5px;
  color: #6b7280;
  margin: 4px 0 0;
  line-height: 1.5;
}
</style>
