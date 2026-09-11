<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {IconDatabase, IconFileUpload, IconRefresh, IconSearch, IconTrash, IconUpload} from '@tabler/icons-vue'
import {knowledgeApi} from '@/api/agent'
import type {KnowledgeDocument, KnowledgeHit, KnowledgeStatus} from '@/types/agent'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'

const status = ref<KnowledgeStatus | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const newTitle = ref('')
const newContent = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const searchQuery = ref('')
const searchTopK = ref(5)
const searchHits = ref<KnowledgeHit[]>([])
const searched = ref(false)

const sourceLabels: Record<string, string> = {MANUAL: '文本录入', FILE: '文件上传', BUILTIN: '内置示例'}
const modeLabels: Record<string, string> = {HYBRID: '混合检索', VECTOR_ONLY: '纯向量', KEYWORD_ONLY: '纯关键词'}

const readyLabel = computed(() => {
  if (!status.value) return '加载中'
  if (!status.value.ready) return '未初始化'
  if (status.value.embeddingConfigured) return `就绪 · ${status.value.embeddingModel ?? ''} · ${status.value.dimension || '?'} 维`
  return '就绪 · 关键词模式（未配置向量模型）'
})

/** 统一执行知识库命令：锁定按钮、展示后端准确错误、完成后刷新状态与清单。 */
async function run(action: () => Promise<unknown>, success?: string) {
  busy.value = true
  error.value = null
  notice.value = null
  try {
    await action()
    if (success) notice.value = success
    await Promise.all([loadStatus(), loadDocuments()])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '知识库操作失败'
  } finally {
    busy.value = false
  }
}

async function loadStatus() {
  status.value = await knowledgeApi.status()
}

async function loadDocuments() {
  documents.value = await knowledgeApi.documents()
}

/** 刷新状态与文档清单。 */
function reload() {
  return run(async () => undefined)
}

/** 供父组件在应用向量配置后主动刷新面板状态。 */
defineExpose({reload})

function addTextDocument() {
  if (!newContent.value.trim()) return
  void run(async () => {
    await knowledgeApi.addDocument(newTitle.value, newContent.value)
    newTitle.value = ''
    newContent.value = ''
  }, '文档已切片并向量化入库').catch(() => undefined)
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  void run(() => knowledgeApi.uploadDocument(file), `已解析并入库：${file.name}`).catch(() => undefined)
  input.value = ''
}

function removeDocument(doc: KnowledgeDocument) {
  void run(() => knowledgeApi.deleteDocument(doc.docId), `已删除：${doc.title}`).catch(() => undefined)
}

function rebuild() {
  if (!window.confirm('重建会清空全部向量与文档元数据，并重新灌入内置示例。继续？')) return
  void run(() => knowledgeApi.rebuild(), '知识库已重建').catch(() => undefined)
}

function testSearch() {
  if (!searchQuery.value.trim()) return
  void run(async () => {
    const result = await knowledgeApi.search(searchQuery.value, searchTopK.value)
    searchHits.value = result.hits
    searched.value = true
  }).catch(() => undefined)
}

function formatTime(millis: number) {
  return new Date(millis).toLocaleString('zh-CN', {hour12: false})
}

onMounted(() => {
  reload().catch(() => undefined)
})
</script>

<template>
  <section class="surface knowledge-panel" aria-labelledby="knowledge-heading">
    <div class="section-heading">
      <IconDatabase :size="18" :stroke-width="1.8" aria-hidden="true"/>
      <h2 id="knowledge-heading">RAG 知识库</h2>
      <span class="knowledge-mode" v-if="status">{{ modeLabels[status.searchMode] ?? status.searchMode }}</span>
    </div>

    <div class="knowledge-status" aria-live="polite">
      <span class="knowledge-ready">{{ readyLabel }}</span>
      <span>{{ status?.documentCount ?? 0 }} 文档 · {{ status?.chunkCount ?? 0 }} 切片</span>
    </div>
    <p v-if="status?.lastError" class="knowledge-error-line">最近错误：{{ status.lastError }}</p>

    <div class="knowledge-actions">
      <button class="text-button" type="button" :disabled="busy" @click="reload().catch(() => undefined)">
        <IconRefresh :size="15"/>刷新
      </button>
      <button class="text-button danger-text" type="button" :disabled="busy" @click="rebuild">
        <IconTrash :size="15"/>清空重建
      </button>
    </div>

    <details class="knowledge-add">
      <summary>添加知识</summary>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-title" text="标题" help="用于检索结果引用展示；留空时显示“未命名文档”。"/>
        <input id="kb-title" v-model.trim="newTitle" maxlength="120" placeholder="例如：2026 行业白皮书要点"/>
      </div>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-content" text="正文" help="粘贴后由后端按段落切片并调用向量模型入库；超长段落自动滑窗切分并保留重叠。"/>
        <textarea id="kb-content" v-model="newContent" rows="4" placeholder="粘贴研究资料、结论或笔记…"/>
      </div>
      <div class="knowledge-add-row">
        <button class="primary-button" type="button" :disabled="busy || !newContent.trim()" @click="addTextDocument">
          <IconUpload :size="16"/>文本入库
        </button>
        <button class="secondary-button" type="button" :disabled="busy" @click="fileInput?.click()">
          <IconFileUpload :size="16"/>上传 .txt / .md
        </button>
        <input ref="fileInput" type="file" accept=".txt,.md,.markdown,text/markdown,text/plain"
               class="knowledge-file-input" aria-label="上传知识文件" @change="onFileChange"/>
      </div>
    </details>

    <div v-if="error" class="knowledge-error" role="alert">{{ error }}</div>
    <div v-else-if="notice" class="knowledge-notice">{{ notice }}</div>

    <div class="knowledge-search">
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-query" text="检索测试" help="直接查询 RogueMemory 混合索引（向量 ANN + BM25），验证切片与向量化效果；Agent 的 search_knowledge 工具走同一链路。"/>
        <div class="knowledge-search-row">
          <input id="kb-query" v-model.trim="searchQuery" maxlength="500" placeholder="例如：混合检索怎么做？"
                 @keydown.enter.prevent="testSearch"/>
          <input v-model.number="searchTopK" class="knowledge-topk" type="number" min="1" max="20" aria-label="TopK"/>
          <button class="primary-button" type="button" :disabled="busy || !searchQuery" @click="testSearch">
            <IconSearch :size="16"/>检索
          </button>
        </div>
      </div>
      <ol v-if="searched" class="knowledge-hits">
        <li v-for="(hit, index) in searchHits" :key="`${hit.docId}-${hit.chunkIndex}-${index}`">
          <div class="knowledge-hit-head">
            <span>{{ hit.title }}</span>
            <span class="knowledge-hit-score">片段{{ hit.chunkIndex }} · {{ hit.score.toFixed(4) }}</span>
          </div>
          <p>{{ hit.content }}</p>
        </li>
        <li v-if="!searchHits.length" class="knowledge-hit-empty">没有命中片段。可先添加知识或切换检索模式。</li>
      </ol>
    </div>

    <div class="knowledge-docs">
      <h3>文档清单 <span v-if="documents.length">（{{ documents.length }}）</span></h3>
      <ul>
        <li v-for="doc in documents" :key="doc.docId">
          <div class="knowledge-doc-main">
            <span class="knowledge-doc-title" :title="doc.title">{{ doc.title }}</span>
            <span class="knowledge-doc-meta">
              {{ sourceLabels[doc.source] ?? doc.source }} · {{ doc.chunkCount }} 片 · {{ doc.charCount }} 字 ·
              {{ formatTime(doc.createdAt) }}
            </span>
          </div>
          <button class="text-button danger-text" type="button" :disabled="busy"
                  :aria-label="`删除文档 ${doc.title}`" @click="removeDocument(doc)">
            <IconTrash :size="15"/>
          </button>
        </li>
        <li v-if="!documents.length" class="knowledge-hit-empty">还没有文档。添加知识或配置向量模型后会自动灌入内置示例。</li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.knowledge-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.knowledge-mode {
  margin-left: auto;
  font-size: 11.5px;
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 999px;
  padding: 1px 8px;
}

.knowledge-status {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #4b5563;
  flex-wrap: wrap;
}

.knowledge-ready {
  font-weight: 600;
}

.knowledge-error-line {
  margin: 0;
  font-size: 11.5px;
  color: #b45309;
}

.knowledge-actions {
  display: flex;
  gap: 10px;
}

.danger-text {
  color: #b91c1c;
}

.knowledge-add,
.knowledge-search,
.knowledge-docs {
  border-top: 1px solid #e5e7eb;
  padding-top: 10px;
}

.knowledge-add summary,
.knowledge-docs h3 {
  font-size: 13px;
  color: #1f2937;
  margin: 0 0 8px;
  cursor: pointer;
}

.knowledge-add .field-block {
  margin-bottom: 8px;
}

.knowledge-add-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.knowledge-file-input {
  display: none;
}

.knowledge-error {
  font-size: 12px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 6px 10px;
}

.knowledge-notice {
  font-size: 12px;
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  padding: 6px 10px;
}

.knowledge-search-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.knowledge-search-row input[type=text],
.knowledge-search-row input:not([type]) {
  flex: 1;
  min-width: 0;
}

.knowledge-topk {
  width: 52px;
  flex: none;
}

.knowledge-hits {
  margin: 8px 0 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow: auto;
}

.knowledge-hit-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
}

.knowledge-hit-score {
  font-weight: 400;
  color: #6b7280;
  white-space: nowrap;
}

.knowledge-hits p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #4b5563;
  line-height: 1.55;
}

.knowledge-docs ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow: auto;
}

.knowledge-docs li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 6px 8px;
}

.knowledge-doc-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.knowledge-doc-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-doc-meta {
  font-size: 11.5px;
  color: #6b7280;
}

.knowledge-hit-empty {
  border: none !important;
  color: #6b7280;
  font-size: 12px;
}
</style>
