<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {IconDatabase, IconDeviceFloppy, IconEye, IconFileUpload, IconPencil, IconRefresh, IconScissors, IconSearch, IconTrash, IconUpload, IconX} from '@tabler/icons-vue'
import {knowledgeApi} from '@/api/agent'
import type {EmbeddingPreset, IngestTask, KnowledgeDocument, KnowledgeHit, KnowledgeSearchMode, KnowledgeStatus, ParserDescriptor} from '@/types/agent'
import ConfigFieldLabel from '@/components/ConfigFieldLabel.vue'

const status = ref<KnowledgeStatus | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

/** 后端支持的入库格式；驱动上传控件 accept 与格式提示，加载失败回退到文本格式。 */
const supportedParsers = ref<ParserDescriptor[]>([])
const fallbackAccept = '.txt,.md,.markdown,.jsonl'
const uploadAccept = computed(() => supportedParsers.value.length
  ? supportedParsers.value.map(item => `.${item.extension}`).join(',')
  : fallbackAccept)
const uploadHint = computed(() => supportedParsers.value.length
  ? `支持 ${supportedParsers.value.map(item => item.extension).join(' / ')}`
  : '支持 .txt / .md / .jsonl')

const newTitle = ref('')
const newContent = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const searchQuery = ref('')
const searchTopK = ref(5)
const searchNamespace = ref('all')
const searchHits = ref<KnowledgeHit[]>([])
const searched = ref(false)
const multiFileInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importProgress = ref('')
const syncFileInput = ref<HTMLInputElement | null>(null)
const syncing = ref(false)

/** 文档预览/编辑弹窗状态。 */
const docDialog = ref<{
  open: boolean
  docId: string
  title: string
  content: string
  contentAvailable: boolean
  chunkCount: number
  source: string
  editing: boolean
  saving: boolean
  error: string | null
}>({open: false, docId: '', title: '', content: '', contentAvailable: true, chunkCount: 0, source: '', editing: false, saving: false, error: null})

/** 打开预览：拉取原始全文并以只读方式展示；旧文档没有保存原文时提示可覆盖保存。 */
async function previewDocument(doc: KnowledgeDocument) {
  docDialog.value = {open: true, docId: doc.docId, title: doc.title, content: '', contentAvailable: true,
    chunkCount: doc.chunkCount, source: doc.source, editing: false, saving: false, error: null}
  try {
    const detail = await knowledgeApi.getDocument(doc.docId)
    docDialog.value.title = detail.title
    docDialog.value.chunkCount = detail.chunkCount
    docDialog.value.contentAvailable = detail.contentAvailable !== false
    if (detail.content != null) {
      docDialog.value.content = detail.content
    } else {
      // 旧版本导入未保存原文：直接进入编辑态，便于粘贴新内容覆盖保存。
      docDialog.value.editing = true
    }
  } catch (cause) {
    docDialog.value.error = cause instanceof Error ? cause.message : '文档读取失败'
  }
}

/** 切换到编辑模式（保留当前文本）。 */
function startEditDocument() {
  docDialog.value.editing = true
  docDialog.value.error = null
}

/** 保存编辑：后端重新切片 + 向量化；成功后刷新清单与状态。 */
async function saveDocumentEdit() {
  const dialog = docDialog.value
  if (!dialog.content.trim()) {
    dialog.error = '文档内容不能为空'
    return
  }
  dialog.saving = true
  dialog.error = null
  try {
    const updated = await knowledgeApi.updateDocument(dialog.docId, dialog.title, dialog.content)
    dialog.editing = false
    dialog.chunkCount = updated.chunkCount
    notice.value = `已保存并重新向量化：${updated.title}（${updated.chunkCount} 片）`
    await Promise.all([loadStatus(), loadDocuments()])
  } catch (cause) {
    dialog.error = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    dialog.saving = false
  }
}

/** 取消编辑：保留文本回到只读预览。 */
function cancelEditDocument() {
  docDialog.value.editing = false
  docDialog.value.error = null
}

function closeDocDialog() {
  docDialog.value.open = false
}

/** 切片预览弹窗状态：加载文档原文 → 试算切片 → 可调窗口参数重算。 */
const chunkDialog = ref<{
  open: boolean
  title: string
  content: string
  chunkSize: number
  overlap: number
  loading: boolean
  error: string | null
  chunks: { index: number; headingPath: string; charCount: number; content: string }[]
}>({open: false, title: '', content: '', chunkSize: 512, overlap: 80, loading: false, error: null, chunks: []})

/** 打开切片预览：拉取原文并按默认参数试算（对齐后端 /chunker/preview，不落库）。 */
async function openChunkPreview(doc: KnowledgeDocument) {
  chunkDialog.value = {open: true, title: doc.title, content: '', chunkSize: 512, overlap: 80,
    loading: true, error: null, chunks: []}
  try {
    const detail = await knowledgeApi.getDocument(doc.docId)
    if (detail.content == null || !detail.content.trim()) {
      throw new Error('该文档没有保存原始全文（旧版本导入），无法预览切片')
    }
    chunkDialog.value.content = detail.content
    await runChunkPreview()
  } catch (cause) {
    chunkDialog.value.error = cause instanceof Error ? cause.message : '切片预览失败'
  } finally {
    chunkDialog.value.loading = false
  }
}

/** 用当前弹窗参数重新试算切片。 */
async function rerunChunkPreview() {
  if (!chunkDialog.value.content.trim()) return
  await runChunkPreview()
}

/** 按弹窗参数调用后端试算。 */
async function runChunkPreview() {
  chunkDialog.value.loading = true
  chunkDialog.value.error = null
  try {
    const result = await knowledgeApi.previewChunking(chunkDialog.value.content,
      chunkDialog.value.chunkSize, chunkDialog.value.overlap)
    chunkDialog.value.chunks = result.chunks
  } catch (cause) {
    chunkDialog.value.error = cause instanceof Error ? cause.message : '切片试算失败'
  } finally {
    chunkDialog.value.loading = false
  }
}

function closeChunkDialog() {
  chunkDialog.value.open = false
}

const sourceLabels: Record<string, string> = {MANUAL: '文本录入', FILE: '文件上传', BUILTIN: '内置示例'}
const modeLabels: Record<string, string> = {HYBRID: '混合检索', VECTOR_ONLY: '纯向量', KEYWORD_ONLY: '纯关键词'}

/** 内置向量模型预设；用户保存的“我的预设”在后端持久化，重启后仍可复用。 */
const builtinPresets = [
  {name: 'BGE-M3（本地网关 18888）', endpoint: 'http://127.0.0.1:18888/v1', model: 'bge-m3'},
  {name: 'BGE-M3（Ollama）', endpoint: 'http://localhost:11434/v1', model: 'bge-m3'},
  {name: 'BGE-M3（Xinference）', endpoint: 'http://localhost:9997/v1', model: 'bge-m3'},
  {name: 'OpenAI text-embedding-3-small', endpoint: 'https://api.openai.com/v1', model: 'text-embedding-3-small'},
  {name: '阿里百炼 text-embedding-v3', endpoint: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'text-embedding-v3'},
]
/** 面板内手动配置向量模型的表单；presetKey 为 builtin:序号 或 user:名称。 */
const embForm = ref<{presetKey: string; endpoint: string; apiKey: string; model: string; mode: KnowledgeSearchMode}>({
  presetKey: '', endpoint: '', apiKey: '', model: 'bge-m3', mode: 'HYBRID',
})
/** 用户自建预设（服务端持久化）。 */
const userPresets = ref<EmbeddingPreset[]>([])

/** 拉取后端支持的入库格式；失败时保留回退 accept，不阻断面板。 */
async function loadParsers() {
  try {
    supportedParsers.value = await knowledgeApi.parsers()
  } catch {
    supportedParsers.value = []
  }
}

/** 拉取用户自建预设；失败不阻断面板，仅影响快捷填充。 */
async function loadPresets() {
  try {
    userPresets.value = await knowledgeApi.embeddingPresets()
  } catch {
    userPresets.value = []
  }
}

/** 选择预设填充表单字段；“我的预设”同时回填保存的 API Key。 */
function applyEmbPreset() {
  const key = embForm.value.presetKey
  if (key.startsWith('builtin:')) {
    const preset = builtinPresets[Number(key.slice(8))]
    if (!preset) return
    embForm.value.endpoint = preset.endpoint
    embForm.value.model = preset.model
  } else if (key.startsWith('user:')) {
    const preset = userPresets.value.find((item) => item.name === key.slice(5))
    if (!preset) return
    embForm.value.endpoint = preset.endpoint
    embForm.value.model = preset.model
    embForm.value.apiKey = preset.apiKey || ''
  }
}

/** 把当前表单另存为“我的预设”（同名覆盖），重启后可继续复用。 */
function saveAsPreset() {
  const endpoint = embForm.value.endpoint.trim()
  const model = embForm.value.model.trim()
  if (!endpoint || !model) {
    error.value = '请先填写服务地址与向量模型，再保存为预设'
    return
  }
  const input = window.prompt('预设名称（同名覆盖）', `我的预设 ${model}`)
  const name = input?.trim()
  if (!name) return
  void run(async () => {
    userPresets.value = await knowledgeApi.saveEmbeddingPreset({
      name, endpoint, model, apiKey: embForm.value.apiKey.trim(),
    })
    embForm.value.presetKey = `user:${name}`
  }, `已保存预设：${name}`).catch(() => undefined)
}

/** 删除选中的“我的预设”。 */
function removePreset() {
  const name = embForm.value.presetKey.slice(5)
  if (!name || !window.confirm(`删除预设「${name}」？`)) return
  void run(async () => {
    userPresets.value = await knowledgeApi.deleteEmbeddingPreset(name)
    embForm.value.presetKey = ''
  }, `已删除预设：${name}`).catch(() => undefined)
}

/** 重启后用后端保存的最近配置回填表单，避免重复手填；已填写时不覆盖。 */
function prefillFromSaved() {
  const saved = status.value?.savedEmbedding
  if (!saved?.endpoint || embForm.value.endpoint) return
  embForm.value.endpoint = saved.endpoint
  embForm.value.model = saved.model || 'bge-m3'
  embForm.value.apiKey = saved.apiKey || ''
  if (saved.searchMode) embForm.value.mode = saved.searchMode
  const matched = userPresets.value.find((item) => item.endpoint === saved.endpoint
    && item.model === (saved.model || 'bge-m3'))
  if (matched) embForm.value.presetKey = `user:${matched.name}`
}

/** 手动应用向量模型配置到知识库；后端持久化并在重启后自动恢复。 */
function applyEmbedding() {
  void run(async () => {
    await knowledgeApi.configureEmbedding({
      embeddingEndpoint: embForm.value.endpoint,
      embeddingApiKey: embForm.value.apiKey,
      embeddingModel: embForm.value.model,
      searchMode: embForm.value.mode,
    })
  }, '向量模型配置已应用并保存，重启后自动恢复').catch(() => undefined)
}

const readyLabel = computed(() => {
  if (!status.value) return '加载中'
  if (!status.value.ready) return '未初始化'
  if (status.value.embeddingConfigured) return `已配置 · ${status.value.embeddingModel ?? ''} · ${status.value.dimension || '?'} 维`
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
  void run(async () => {
    const task = await knowledgeApi.uploadDocumentAsync(file)
    notice.value = `已提交入库任务：${file.name}（${task.status === 'PENDING' ? '排队中' : '处理中'}），进度见任务列表`
    await loadTasks()
  }).catch(() => undefined)
  input.value = ''
}

/** 入库任务面板状态与轮询：有活跃任务时每 1.5s 刷新，全部终态后停止。 */
const tasks = ref<IngestTask[]>([])
let taskTimer: ReturnType<typeof setTimeout> | null = null
const taskStatusLabels: Record<string, string> = {
  PENDING: '排队中', PROCESSING: '处理中', FINALIZING: '收尾中',
  COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消',
}
const taskStageLabels: Record<string, string> = {
  PARSING: '解析中', INDEXING: '写索引中', FINALIZING: '收尾中',
}

async function loadTasks() {
  try {
    tasks.value = await knowledgeApi.tasks()
  } catch {
    tasks.value = []
  }
  const active = tasks.value.some(task => task.status === 'PENDING' || task.status === 'PROCESSING')
  if (active) {
    scheduleTaskPolling()
  } else if (taskTimer) {
    clearTimeout(taskTimer)
    taskTimer = null
    // 刚从活跃转终态：刷新文档清单与状态，让新入库文档立即可见
    await Promise.all([loadStatus(), loadDocuments()])
  }
}

function scheduleTaskPolling() {
  if (taskTimer) return
  taskTimer = setTimeout(async () => {
    taskTimer = null
    await loadTasks()
  }, 1500)
}

async function cancelTask(task: IngestTask) {
  try {
    await knowledgeApi.cancelTask(task.taskId)
    await loadTasks()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '取消失败'
  }
}

onBeforeUnmount(() => {
  if (taskTimer) {
    clearTimeout(taskTimer)
    taskTimer = null
  }
})

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
    const result = await knowledgeApi.search(searchQuery.value, searchTopK.value, searchNamespace.value)
    searchHits.value = result.hits
    searched.value = true
  }).catch(() => undefined)
}

/** 批量导入：逐个文件提交异步任务（解析/向量化在后台推进），进度见任务列表。 */
async function importFiles(files: FileList | null) {
  if (!files || !files.length) return
  importing.value = true
  busy.value = true
  error.value = null
  notice.value = null
  let submitted = 0
  const failures: string[] = []
  for (let index = 0; index < files.length; index++) {
    const file = files[index]
    importProgress.value = `正在提交 (${index + 1}/${files.length})：${file.name}`
    try {
      await knowledgeApi.uploadDocumentAsync(file)
      submitted++
    } catch (cause) {
      failures.push(`${file.name}: ${cause instanceof Error ? cause.message : '失败'}`)
    }
  }
  importing.value = false
  busy.value = false
  importProgress.value = ''
  notice.value = `已提交 ${submitted} 个入库任务，进度见任务列表`
      + (failures.length ? `；提交失败：${failures.join('；')}` : '')
  await loadTasks()
}

function onMultiFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  void importFiles(files).catch(() => undefined)
  input.value = ''
}

/** 导入跨环境同步快照：源优先合并文档与向量，后端原地重建索引（无需重新向量化）。 */
async function importSnapshotFile(file: File) {
  syncing.value = true
  busy.value = true
  error.value = null
  notice.value = null
  try {
    const result = await knowledgeApi.importSnapshot(file)
    notice.value = `快照导入完成：文档 ${result.documentsUpserted} 篇（新增 ${result.documentsAdded}），`
        + `向量新增 ${result.vectorsAdded} / 跳过 ${result.vectorsSkipped}，`
        + (result.indexRefreshed ? '索引已重建，立即可检索' : '索引重建失败，请重启应用后生效')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '快照导入失败'
  } finally {
    syncing.value = false
    busy.value = false
    await Promise.all([loadStatus(), loadDocuments()])
  }
}

function onSyncFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) void importSnapshotFile(file).catch(() => undefined)
  input.value = ''
}

function formatTime(millis: number) {
  return new Date(millis).toLocaleString('zh-CN', {hour12: false})
}

onMounted(async () => {
  await Promise.all([loadPresets(), loadParsers()])
  await reload().catch(() => undefined)
  await loadTasks()
  prefillFromSaved()
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
          <IconFileUpload :size="16"/>上传单文件
        </button>
        <button class="secondary-button" type="button" :disabled="busy || importing" @click="multiFileInput?.click()">
          <IconFileUpload :size="16"/>批量导入（多格式）
        </button>
        <input ref="fileInput" type="file" :accept="uploadAccept"
               class="knowledge-file-input" aria-label="上传知识文件" @change="onFileChange"/>
        <input ref="multiFileInput" type="file" :accept="uploadAccept"
               class="knowledge-file-input" multiple aria-label="批量导入知识文件" @change="onMultiFileChange"/>
      </div>
      <p class="knowledge-upload-hint">解析支持：{{ uploadHint }}。PDF / Word / 表格 / 幻灯片 / HTML 会先归一为 Markdown 再切片入库。</p>
      <p v-if="importProgress" class="knowledge-import-progress">{{ importProgress }}</p>
    </details>

    <details class="knowledge-add">
      <summary>跨环境同步（导出 / 导入快照）</summary>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-sync" text="知识库快照"
                          help="导出包含全部文档（含原文）与向量缓存；目标环境导入后用缓存原地重建索引，无需重新向量化。导入为源优先合并，可重复执行。"/>
        <div class="kb-preset-row">
          <a class="secondary-button kb-sync-export" :href="knowledgeApi.exportSnapshotUrl"
             :class="{disabled: busy}" download
             :aria-disabled="busy">
            <IconDeviceFloppy :size="16"/>导出快照（.json）
          </a>
          <button class="secondary-button" type="button" :disabled="busy || syncing"
                  @click="syncFileInput?.click()">
            <IconFileUpload :size="16"/>导入快照（.json）
          </button>
          <input ref="syncFileInput" type="file" accept=".json,application/json"
                 class="knowledge-file-input" aria-label="导入知识库快照" @change="onSyncFileChange"/>
        </div>
      </div>
    </details>

    <details class="knowledge-add">
      <summary>向量模型配置（手动）</summary>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-emb-preset" text="预设快捷填充"
                          help="内置预设与「我的预设」选中后自动填充地址、模型与 Key；也可以完全手动填写任意 OpenAI 兼容 /embeddings 服务。"/>
        <div class="kb-preset-row">
          <select id="kb-emb-preset" v-model="embForm.presetKey" @change="applyEmbPreset">
            <option value="" disabled>选择预设…</option>
            <optgroup label="内置预设">
              <option v-for="(preset, index) in builtinPresets" :key="`emb-b${index}`"
                      :value="`builtin:${index}`">{{ preset.name }}</option>
            </optgroup>
            <optgroup v-if="userPresets.length" label="我的预设">
              <option v-for="preset in userPresets" :key="`emb-u${preset.name}`"
                      :value="`user:${preset.name}`">{{ preset.name }}</option>
            </optgroup>
          </select>
          <button class="text-button" type="button" title="把当前表单保存为我的预设" :disabled="busy"
                  @click="saveAsPreset">
            <IconDeviceFloppy :size="15"/>存为预设
          </button>
          <button v-if="embForm.presetKey.startsWith('user:')" class="text-button danger-text" type="button"
                  title="删除选中的我的预设" :disabled="busy" @click="removePreset">
            <IconTrash :size="15"/>
          </button>
        </div>
      </div>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-emb-endpoint" text="服务地址" help="OpenAI 兼容 Embedding 服务根地址（含 /v1），例如 http://127.0.0.1:18888/v1。"/>
        <input id="kb-emb-endpoint" v-model.trim="embForm.endpoint" type="url" maxlength="2048"
               placeholder="http://127.0.0.1:18888/v1"/>
      </div>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-emb-model" text="向量模型" help="模型 ID，例如 bge-m3。"/>
        <input id="kb-emb-model" v-model.trim="embForm.model" maxlength="200" placeholder="bge-m3"/>
      </div>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-emb-key" text="API Key（可选）" help="免鉴权服务可留空。"/>
        <input id="kb-emb-key" v-model.trim="embForm.apiKey" type="password" autocomplete="off" maxlength="4096" placeholder="可选"/>
      </div>
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-emb-mode" text="检索模式" help="HYBRID 向量+关键词混合（推荐）；VECTOR_ONLY 纯向量；KEYWORD_ONLY 纯关键词（无需向量服务）。"/>
        <select id="kb-emb-mode" v-model="embForm.mode">
          <option value="HYBRID">混合检索（推荐）</option>
          <option value="VECTOR_ONLY">纯向量</option>
          <option value="KEYWORD_ONLY">纯关键词</option>
        </select>
      </div>
      <div class="knowledge-add-row">
        <button class="primary-button" type="button" :disabled="busy || !embForm.endpoint.trim() || !embForm.model.trim()"
                @click="applyEmbedding">
          <IconDatabase :size="16"/>应用向量模型
        </button>
      </div>
      <p class="dialog-note">应用后立即生效并持久化保存，重启自动恢复；连接失败会保留配置并显示原因，检索自动使用关键词模式，不影响使用。</p>
    </details>

    <div v-if="error" class="knowledge-error" role="alert">{{ error }}</div>
    <div v-else-if="notice" class="knowledge-notice">{{ notice }}</div>

    <div class="knowledge-search">
      <div class="field-block">
        <ConfigFieldLabel for-id="kb-query" text="检索测试" help="直接查询 RogueMemory 混合索引（向量 ANN + BM25）；可限定单个文档范围，Agent 的 search_knowledge 工具走同一链路。"/>
        <div class="knowledge-search-row">
          <select v-model="searchNamespace" class="knowledge-namespace" aria-label="检索范围">
            <option value="all">全部文档</option>
            <option v-for="doc in documents" :key="doc.docId" :value="doc.docId">{{ doc.title }}</option>
          </select>
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
          <p v-if="hit.headingPath" class="knowledge-hit-heading" :title="`来自小节：${hit.headingPath}`">
            {{ hit.headingPath }}
          </p>
          <p>{{ hit.content }}</p>
        </li>
        <li v-if="!searchHits.length" class="knowledge-hit-empty">没有命中片段。可先添加知识或切换检索模式。</li>
      </ol>
    </div>

    <div class="knowledge-tasks" v-if="tasks.length">
      <h3>入库任务 <span>（{{ tasks.length }}）</span></h3>
      <ul>
        <li v-for="task in tasks" :key="task.taskId" class="knowledge-task-item">
          <div class="knowledge-task-main">
            <span class="knowledge-doc-title" :title="task.title">{{ task.title }}</span>
            <span class="knowledge-task-meta">
              {{ taskStatusLabels[task.status] ?? task.status }}
              <template v-if="task.stage && (task.status === 'PROCESSING')"> · {{ taskStageLabels[task.stage] ?? task.stage }}</template>
              <template v-if="task.status === 'COMPLETED'"> · {{ task.chunkCount }} 片</template>
              · {{ formatTime(task.createdAt) }}
            </span>
          </div>
          <div class="knowledge-task-progress" role="progressbar"
               :aria-valuenow="task.progress" aria-valuemin="0" aria-valuemax="100">
            <div class="knowledge-task-bar" :class="`task-${task.status.toLowerCase()}`"
                 :style="{width: task.progress + '%'}"></div>
          </div>
          <div class="knowledge-task-side">
            <span class="knowledge-task-pct">{{ task.progress }}%</span>
            <button v-if="task.status === 'PENDING' || task.status === 'PROCESSING'"
                    class="text-button danger-text" type="button" :aria-label="`取消任务 ${task.title}`"
                    @click="cancelTask(task)">
              <IconX :size="14"/>
            </button>
          </div>
          <p v-if="task.error" class="knowledge-task-error">{{ task.error }}</p>
        </li>
      </ul>
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
          <div class="knowledge-doc-ops">
            <button class="text-button" type="button" :disabled="busy"
                    :aria-label="`预览文档 ${doc.title}`" @click="previewDocument(doc)">
              <IconEye :size="15"/>
            </button>
            <button class="text-button" type="button" :disabled="busy"
                    :aria-label="`切片预览 ${doc.title}`" title="切片预览（不落库）" @click="openChunkPreview(doc)">
              <IconScissors :size="15"/>
            </button>
            <button class="text-button danger-text" type="button" :disabled="busy"
                    :aria-label="`删除文档 ${doc.title}`" @click="removeDocument(doc)">
              <IconTrash :size="15"/>
            </button>
          </div>
        </li>
        <li v-if="!documents.length" class="knowledge-hit-empty">还没有文档。添加知识或配置向量模型后会自动灌入内置示例。</li>
      </ul>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="docDialog.open" class="doc-dialog-backdrop" @click.self="closeDocDialog">
      <div class="doc-dialog" role="dialog" aria-modal="true" :aria-label="`文档${docDialog.editing ? '编辑' : '预览'}`">
        <div class="doc-dialog-head">
          <h3>{{ docDialog.editing ? '编辑文档' : '文档预览' }}</h3>
          <button class="text-button" type="button" :disabled="docDialog.saving" @click="closeDocDialog"
                  aria-label="关闭">
            <IconX :size="16"/>
          </button>
        </div>
        <div class="field-block">
          <ConfigFieldLabel for-id="doc-edit-title" text="标题" help="编辑后保存会按新标题重建切片元数据。"/>
          <input id="doc-edit-title" v-model.trim="docDialog.title" maxlength="120"
                 :readonly="!docDialog.editing" placeholder="文档标题"/>
        </div>
        <div class="doc-dialog-meta">
          <span>{{ sourceLabels[docDialog.source] ?? docDialog.source }}</span>
          <span>{{ docDialog.chunkCount }} 片</span>
          <span>{{ docDialog.content.length }} 字</span>
        </div>
        <p v-if="!docDialog.contentAvailable" class="doc-dialog-missing" role="note">
          该文档为旧版本导入，未保存原始全文，无法预览原文。可在下方粘贴新内容覆盖保存（保存后重新切片并向量化），或关闭后删除并重新导入。
        </p>
        <div class="field-block">
          <ConfigFieldLabel for-id="doc-edit-content" text="正文"
                            help="保存后后端会删除旧向量切片、按新文本重新切片并向量化，检索立即使用新内容。"/>
          <textarea id="doc-edit-content" v-model="docDialog.content" rows="14" spellcheck="false"
                    :readonly="!docDialog.editing" placeholder="文档正文…"/>
        </div>
        <p v-if="docDialog.error" class="doc-dialog-error" role="alert">{{ docDialog.error }}</p>
        <div class="doc-dialog-foot">
          <button v-if="!docDialog.editing" class="secondary-button" type="button" @click="startEditDocument">
            <IconPencil :size="16"/>编辑
          </button>
          <template v-else>
            <button class="secondary-button" type="button" :disabled="docDialog.saving"
                    @click="cancelEditDocument">取消</button>
            <button class="primary-button" type="button" :disabled="docDialog.saving || !docDialog.content.trim()"
                    @click="saveDocumentEdit">
              <IconUpload :size="16"/>{{ docDialog.saving ? '保存中…' : '保存并重新向量化' }}
            </button>
          </template>
          <button class="text-button" type="button" :disabled="docDialog.saving" @click="closeDocDialog">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="chunkDialog.open" class="doc-dialog-backdrop" @click.self="closeChunkDialog">
      <div class="doc-dialog" role="dialog" aria-modal="true" aria-label="切片预览">
        <div class="doc-dialog-head">
          <h3>切片预览：{{ chunkDialog.title }}</h3>
          <button class="text-button" type="button" :disabled="chunkDialog.loading" @click="closeChunkDialog"
                  aria-label="关闭">
            <IconX :size="16"/>
          </button>
        </div>
        <div class="chunk-param-row">
          <label>窗口
            <input v-model.number="chunkDialog.chunkSize" type="number" min="64" max="4096" step="32"/>
          </label>
          <label>重叠
            <input v-model.number="chunkDialog.overlap" type="number" min="0" max="512" step="8"/>
          </label>
          <button class="secondary-button" type="button" :disabled="chunkDialog.loading"
                  @click="rerunChunkPreview">
            <IconRefresh :size="15"/>重新试算
          </button>
        </div>
        <p class="dialog-note">预览只试算不落库；实际入库使用同样的结构化切片（保护代码块/表格，标题路径写入元数据）。修改窗口后需重建或编辑保存才会生效。</p>
        <p v-if="chunkDialog.error" class="doc-dialog-error" role="alert">{{ chunkDialog.error }}</p>
        <p v-else-if="chunkDialog.loading" class="dialog-note">试算中…</p>
        <p v-else-if="chunkDialog.chunks.length" class="dialog-note">
          共 {{ chunkDialog.chunks.length }} 片 ·
          平均 {{ Math.round(chunkDialog.chunks.reduce((sum, chunk) => sum + chunk.charCount, 0) / chunkDialog.chunks.length) }} 字/片
        </p>
        <ol v-else class="chunk-list">
          <li class="knowledge-hit-empty">没有可用切片。</li>
        </ol>
        <ol class="chunk-list">
          <li v-for="chunk in chunkDialog.chunks" :key="chunk.index" class="chunk-item">
            <div class="chunk-head">
              <span>#{{ chunk.index }}</span>
              <span v-if="chunk.headingPath" class="chunk-heading">{{ chunk.headingPath }}</span>
              <span class="chunk-size">{{ chunk.charCount }} 字</span>
            </div>
            <pre>{{ chunk.content }}</pre>
          </li>
        </ol>
      </div>
    </div>
  </Teleport>
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

/* 预设选择行：下拉占满剩余宽度，“存为预设/删除”按钮内联其右。 */
.kb-preset-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.kb-preset-row select {
  flex: 1;
  min-width: 0;
}

.kb-preset-row .text-button {
  flex-shrink: 0;
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

.knowledge-import-progress {
  margin: 6px 0 0;
  font-size: 12px;
  color: #047857;
}

.dialog-note {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: #6b7280;
  line-height: 1.5;
}

.knowledge-namespace {
  max-width: 180px;
  flex: none;
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

.knowledge-hit-heading {
  margin: 2px 0 0;
  font-size: 11px;
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 1px 8px;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-upload-hint {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: #6b7280;
}

.knowledge-tasks h3,
.knowledge-docs h3 {
  margin: 4px 0 8px;
  font-size: 13px;
  color: #1f2937;
}

.knowledge-tasks ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
}

.knowledge-task-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 10px;
}

.knowledge-task-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.knowledge-task-meta {
  font-size: 11.5px;
  color: #6b7280;
}

.knowledge-task-progress {
  align-self: center;
  width: 120px;
  height: 6px;
  background: #e5e7eb;
  border-radius: 999px;
  overflow: hidden;
}

.knowledge-task-bar {
  height: 100%;
  border-radius: 999px;
  background: #3b82f6;
  transition: width 0.4s ease;
}

.knowledge-task-bar.task-completed {
  background: #10b981;
}

.knowledge-task-bar.task-failed {
  background: #ef4444;
}

.knowledge-task-bar.task-cancelled {
  background: #9ca3af;
}

.knowledge-task-side {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.knowledge-task-pct {
  font-size: 11.5px;
  color: #4b5563;
  white-space: nowrap;
}

.knowledge-task-error {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 11px;
  color: #b91c1c;
}

.chunk-param-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #4b5563;
}

.chunk-param-row label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.chunk-param-row input {
  width: 84px;
}

.chunk-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow: auto;
}

.chunk-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
}

.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: #6b7280;
}

.chunk-head span:first-child {
  font-weight: 700;
  color: #1f2937;
}

.chunk-heading {
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 1px 8px;
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-size {
  margin-left: auto;
  white-space: nowrap;
}

.chunk-item pre {
  margin: 6px 0 0;
  font-size: 11.5px;
  line-height: 1.55;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
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

.knowledge-doc-ops {
  display: flex;
  gap: 2px;
  flex: none;
}

.doc-dialog-backdrop {
  position: fixed;
  inset: 0;
  background: rgb(15 23 42 / 45%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 60;
}

.doc-dialog {
  width: min(760px, 100%);
  max-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgb(0 0 0 / 25%);
  padding: 14px 16px;
}

.doc-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.doc-dialog-head h3 {
  margin: 0;
  font-size: 14px;
  color: #1f2937;
}

.doc-dialog-meta {
  display: flex;
  gap: 10px;
  font-size: 11.5px;
  color: #6b7280;
}

.doc-dialog textarea {
  min-height: 220px;
  resize: vertical;
  font-family: inherit;
  line-height: 1.6;
}

.doc-dialog textarea[readonly],
.doc-dialog input[readonly] {
  background: #f9fafb;
  color: #374151;
  cursor: default;
}

.doc-dialog-error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 6px 10px;
}

.doc-dialog-missing {
  margin: 0;
  font-size: 12px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 6px 10px;
  line-height: 1.55;
}

.doc-dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
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
