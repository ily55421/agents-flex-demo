<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {IconDatabase, IconMessages} from '@tabler/icons-vue'
import ModelConfigDialog from '@/components/ModelConfigDialog.vue'
import {knowledgeApi} from '@/api/agent'
import type {EmbeddingProfile, KnowledgeStatus, ModelProfile, ModelStatus} from '@/types/agent'

const props = defineProps<{
  /** 后端运行时的聊天模型安全状态（不含 API Key）。 */
  modelStatus: ModelStatus | null
}>()
const emit = defineEmits<{
  /** 应用聊天模型配置到运行时（立即生效，不创建 Agent）。 */
  apply: [profile: ModelProfile]
  /** 应用向量模型配置到知识库（持久化并触发重建）。 */
  configureEmbedding: [profile: EmbeddingProfile]
  /** 跳转到知识库页查看向量状态与文档。 */
  goKnowledge: []
}>()

const knowledgeStatus = ref<KnowledgeStatus | null>(null)

/** 聊天模型草稿：以运行时安全状态初始化；API Key 不回显，修改连接时需要重新填写。 */
const modelDraft = computed<ModelProfile>(() => ({
  profileName: props.modelStatus ? `${props.modelStatus.provider} · ${props.modelStatus.model}` : '聊天模型',
  modelProvider: props.modelStatus?.provider ?? 'deepseek',
  modelEndpoint: props.modelStatus?.endpoint ?? 'https://api.deepseek.com',
  modelRequestPath: '/chat/completions',
  modelApiKey: '',
  modelName: props.modelStatus?.model ?? 'deepseek-chat',
  modelTemperature: props.modelStatus?.temperature ?? 0.2,
  modelThinkingEnabled: props.modelStatus?.thinkingEnabled ?? false,
  modelThinkingProtocol: 'none',
  modelSeed: '',
  modelTopP: null,
  modelTopK: null,
  modelMaxTokens: null,
  modelStop: [],
  modelIncludeUsage: true,
  modelResponseFormat: 'NONE',
  modelRetryEnabled: props.modelStatus?.retryEnabled ?? true,
  modelRetryCount: props.modelStatus?.retryCount ?? 2,
  modelRetryInitialDelayMillis: props.modelStatus?.retryInitialDelayMillis ?? 600,
  maxInputTokens: 16000,
  maxOutputTokens: 8192,
  maxTotalTokens: 32000,
  maxAttachedTokens: 0,
}))

/** 向量模型草稿：以后端持久化的最近应用配置初始化（含 Key，仅本机演示回显）。 */
const embeddingDraft = computed<EmbeddingProfile>(() => ({
  profileName: knowledgeStatus.value?.savedEmbedding?.model ?? '向量模型',
  embeddingEndpoint: knowledgeStatus.value?.savedEmbedding?.endpoint ?? '',
  embeddingApiKey: knowledgeStatus.value?.savedEmbedding?.apiKey ?? '',
  embeddingModel: knowledgeStatus.value?.savedEmbedding?.model ?? 'bge-m3',
  knowledgeSearchMode: knowledgeStatus.value?.savedEmbedding?.searchMode ?? 'HYBRID',
}))

/** 聊天模型连接状态摘要。 */
const chatSummary = computed(() => {
  if (!props.modelStatus) return {ready: false, label: '加载中', detail: ''}
  if (!props.modelStatus.configured) {
    return {ready: false, label: '未配置', detail: '在下方填写 OpenAI 兼容服务并点击「应用配置」。'}
  }
  return {
    ready: true,
    label: `已连接 · ${props.modelStatus.provider} / ${props.modelStatus.model}`,
    detail: `${props.modelStatus.endpoint} · 重试 ${props.modelStatus.retryEnabled ? props.modelStatus.retryCount + ' 次' : '关闭'}`,
  }
})

/** 向量模型状态摘要：直接复用知识库状态里的就绪度与最近错误。 */
const embeddingSummary = computed(() => {
  const status = knowledgeStatus.value
  if (!status) return {ready: false, label: '加载中', detail: '', error: null as string | null}
  if (!status.embeddingConfigured) {
    return {ready: false, label: '未配置 · 关键词模式', detail: '配置后知识库启用向量混合检索。', error: status.lastError}
  }
  return {
    ready: true,
    label: `已配置 · ${status.embeddingModel ?? ''} · ${status.dimension || '?'} 维`,
    detail: status.savedEmbedding?.endpoint ?? '',
    error: status.lastError,
  }
})

/** 草稿重挂载签名：模型状态或已保存向量配置变化时强制刷新弹窗草稿。 */
const draftKey = computed(() =>
    `${props.modelStatus?.provider ?? 'none'}|${props.modelStatus?.model ?? ''}`
    + `|${knowledgeStatus.value?.savedEmbedding?.endpoint ?? ''}`
    + `|${knowledgeStatus.value?.savedEmbedding?.model ?? ''}`)

async function loadKnowledgeStatus() {
  try {
    knowledgeStatus.value = await knowledgeApi.status()
  } catch {
    knowledgeStatus.value = null
  }
}

/** 应用聊天模型后刷新状态摘要。 */
function applyModel(profile: ModelProfile) {
  emit('apply', profile)
}

/** 应用向量模型后刷新摘要，便于立刻看到连接结果。 */
async function applyEmbedding(profile: EmbeddingProfile) {
  emit('configureEmbedding', profile)
  await loadKnowledgeStatus()
}

onMounted(() => {
  void loadKnowledgeStatus()
})
</script>

<template>
  <div class="model-config-page">
    <div class="model-status-cards">
      <section class="surface model-status-card" aria-labelledby="chat-status-heading">
        <div class="status-head">
          <IconMessages :size="17"/>
          <h2 id="chat-status-heading">聊天模型</h2>
          <span :class="['status-badge', {ok: chatSummary.ready}]">{{ chatSummary.label }}</span>
        </div>
        <p class="status-detail">{{ chatSummary.detail }}</p>
      </section>
      <section class="surface model-status-card" aria-labelledby="embedding-status-heading">
        <div class="status-head">
          <IconDatabase :size="17"/>
          <h2 id="embedding-status-heading">向量模型（知识库）</h2>
          <span :class="['status-badge', {ok: embeddingSummary.ready}]">{{ embeddingSummary.label }}</span>
        </div>
        <p class="status-detail">{{ embeddingSummary.detail }}</p>
        <p v-if="embeddingSummary.error" class="status-error">最近错误：{{ embeddingSummary.error }}</p>
      </section>
    </div>

    <!-- key 绑定两端状态签名：状态加载或应用成功后重挂载弹窗，让草稿始终基于最新生效值。 -->
    <ModelConfigDialog :key="draftKey" :open="true" standalone :model="modelDraft" :embedding="embeddingDraft"
                       @apply="(model) => applyModel(model)"
                       @configure-embedding="(profile) => applyEmbedding(profile)"/>

    <div class="model-config-footer">
      <button class="text-button" type="button" @click="emit('goKnowledge')">
        <IconDatabase :size="15"/>前往知识库查看文档与检索状态
      </button>
    </div>
  </div>
</template>

<style scoped>
.model-config-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 12px;
}

.model-status-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.model-status-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
}

.status-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-head h2 {
  margin: 0;
  font-size: 14px;
  color: #111827;
}

.status-badge {
  margin-left: auto;
  font-size: 11.5px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 999px;
  padding: 1px 8px;
}

.status-badge.ok {
  color: #047857;
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.status-detail {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
}

.status-error {
  margin: 0;
  font-size: 11.5px;
  color: #b45309;
}

.model-config-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .model-status-cards {
    grid-template-columns: 1fr;
  }
}
</style>
