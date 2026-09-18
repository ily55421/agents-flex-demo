import type {
    AgentDefinition,
    AgentRun,
    CreateAgentPayload,
    CreateRunPayload,
    EmbeddingPreset,
    GraphFactsResult,
    GraphNodeDetail,
    GraphStation,
    GraphSummary,
    GraphView,
    ChunkPreviewItem,
    IngestTask,
    KnowledgeBase,
    KnowledgeDocument,
    KnowledgeHit,
    KnowledgeSearchMode,
    KnowledgeStatus,
    KnowledgeSyncResult,
    KnowledgeUploadResult,
    ModelProfile,
    ModelStatus,
    ParserDescriptor,
    TraceView,
} from '@/types/agent'

const RUN_API_ROOT = '/api/agent/runs'
const AGENT_API_ROOT = '/api/agent/agents'
const KNOWLEDGE_API_ROOT = '/api/knowledge'

/**
 * 所有 REST 调用共用的请求入口。
 * 开发环境由 Vite 将 /api 代理到 Spring Boot，因此前端不绑定具体主机或端口。
 * 非 2xx 响应优先采用后端的结构化 message，保证界面能展示准确的状态约束错误。
 */
async function request<T>(root: string, path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${root}${path}`, {
        ...init,
        headers: {
            'Content-Type': 'application/json',
            ...init?.headers,
        },
    })
    if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { message?: string } | null
        throw new Error(body?.message || `请求失败 (${response.status})`)
    }
    return response.json() as Promise<T>
}

export const agentApi = {
    /** 查询不包含 API Key 的真实模型配置状态。 */
    modelStatus: () => request<ModelStatus>(RUN_API_ROOT, '/model'),
    /** 仅应用模型连接配置到后端内存，使配置立即生效而不必创建 Agent。 */
    updateModel: (payload: ModelProfile) =>
        request<ModelStatus>(RUN_API_ROOT, '/model', {method: 'POST', body: JSON.stringify(payload)}),
    /** 列出后端当前全部 Run Snapshot；前端按 conversationId 聚合为会话窗口。 */
    list: () => request<AgentRun[]>(RUN_API_ROOT, ''),
    /** 使用完整配置在后端构建并注册真实 Agents-Flex Agent。 */
    createAgent: (payload: CreateAgentPayload) =>
        request<AgentDefinition>(AGENT_API_ROOT, '', {method: 'POST', body: JSON.stringify(payload)}),
    /** 列出当前进程已创建 + DuckDB 归档的全部 Agent 安全视图，供纯对话页选择智能体。 */
    listAgents: () => request<AgentDefinition[]>(AGENT_API_ROOT, ''),
    /** 使用已创建 Agent 创建 READY Run，返回初始 Snapshot。 */
    create: (payload: CreateRunPayload) =>
        request<AgentRun>(RUN_API_ROOT, '', {method: 'POST', body: JSON.stringify(payload)}),
    /** 在同一 ChatMemory 会话下创建下一轮 READY Turn。 */
    continueConversation: (runId: string, message: string) =>
        request<AgentRun>(RUN_API_ROOT, `/${runId}/messages`, {method: 'POST', body: JSON.stringify({message})}),
    /** 查询指定 Run 的最新 Snapshot，不改变 Runtime 状态。 */
    get: (runId: string) => request<AgentRun>(RUN_API_ROOT, `/${runId}`),
    /** 启动 READY Run 的异步推进。 */
    start: (runId: string) => request<AgentRun>(RUN_API_ROOT, `/${runId}/start`, {method: 'POST'}),
    /** 请求立即或在下一个安全检查点挂起 Run。 */
    suspend: (runId: string) => request<AgentRun>(RUN_API_ROOT, `/${runId}/suspend`, {method: 'POST'}),
    /** 恢复由操作员手工挂起的原 Turn。 */
    resume: (runId: string) => request<AgentRun>(RUN_API_ROOT, `/${runId}/resume`, {method: 'POST'}),
    /** 将 Run 转为 CANCELLED 终态。 */
    cancel: (runId: string) => request<AgentRun>(RUN_API_ROOT, `/${runId}/cancel`, {method: 'POST'}),
    /** 提交 JSON Schema 动态表单并恢复 USER_INPUT Suspension。 */
    submitForm: (runId: string, values: Record<string, unknown>) =>
        request<AgentRun>(RUN_API_ROOT, `/${runId}/form`, {
            method: 'POST',
            body: JSON.stringify({values}),
        }),
    /** 提交人工审批结果并恢复 TOOL_APPROVAL Suspension。 */
    approve: (runId: string, approved: boolean, reason?: string) =>
        request<AgentRun>(RUN_API_ROOT, `/${runId}/approval`, {
            method: 'POST',
            body: JSON.stringify({approved, reason, approver: 'showcase-user'}),
        }),
    /** 查询强制刷新后的 OTel Span、Metric 和生命周期事件。 */
    trace: (runId: string) => request<TraceView>(RUN_API_ROOT, `/${runId}/trace`),
    /** 生成同源 SSE 地址；连接与自动重连由 EventSource 自己管理。 */
    eventUrl: (runId: string) => `${RUN_API_ROOT}/${runId}/events`,
}

export const knowledgeApi = {
    /** 知识库状态：就绪度、生效 embedding 签名、文档与切片计数。 */
    status: () => request<KnowledgeStatus>(KNOWLEDGE_API_ROOT, '/status'),
    /** 已入库文档清单。 */
    documents: () => request<KnowledgeDocument[]>(KNOWLEDGE_API_ROOT, '/documents'),
    /** 读取文档原始全文与元数据，供预览查看；旧文档可能没有保存原文（contentAvailable=false）。 */
    getDocument: (docId: string) =>
        request<KnowledgeDocument & { content: string | null; contentAvailable: boolean }>(
            KNOWLEDGE_API_ROOT, `/documents/${docId}`),
    /** 编辑文档全文：后端重新切片并向量化，检索立即生效。 */
    updateDocument: (docId: string, title: string, content: string) =>
        request<KnowledgeDocument>(KNOWLEDGE_API_ROOT, `/documents/${docId}`, {
            method: 'PUT',
            body: JSON.stringify({title, content}),
        }),
    /** UI 粘贴文本方式添加文档。 */
    addDocument: (title: string, content: string) =>
        request<KnowledgeDocument>(KNOWLEDGE_API_ROOT, '/documents', {
            method: 'POST',
            body: JSON.stringify({title, content}),
        }),
    /** 上传文件解析入库（支持 pdf/docx/xlsx/pptx/html/md/txt 等 11 种格式，由 /parsers 驱动）。 */
    uploadDocument: async (file: File, title?: string): Promise<KnowledgeUploadResult> => {
        const form = new FormData()
        form.append('file', file)
        if (title) form.append('title', title)
        const response = await fetch(`${KNOWLEDGE_API_ROOT}/documents/upload`, {method: 'POST', body: form})
        if (!response.ok) {
            const body = (await response.json().catch(() => null)) as { message?: string } | null
            throw new Error(body?.message || `上传失败 (${response.status})`)
        }
        return response.json() as Promise<KnowledgeUploadResult>
    },
    /** 支持的入库格式与对应解析器；驱动上传控件 accept 与格式提示。 */
    parsers: () => request<ParserDescriptor[]>(KNOWLEDGE_API_ROOT, '/parsers'),
    /** 切片预览：按窗口参数试算切片，不落库；用于调整切片参数前先看效果。 */
    previewChunking: (content: string, chunkSize?: number, overlap?: number) =>
        request<{ chunkSize: number; overlap: number; chunks: ChunkPreviewItem[] }>(
            KNOWLEDGE_API_ROOT, '/chunker/preview', {
                method: 'POST',
                body: JSON.stringify({content, chunkSize, overlap}),
            }),
    /** 上传文件并异步入库：立即返回任务视图，进度经 tasks() 轮询；kbId 指定目标库。 */
    uploadDocumentAsync: async (file: File, title?: string, kbId?: string): Promise<IngestTask> => {
        const form = new FormData()
        form.append('file', file)
        if (title) form.append('title', title)
        if (kbId && kbId !== 'all') form.append('kbId', kbId)
        const response = await fetch(`${KNOWLEDGE_API_ROOT}/documents/upload/async`,
            {method: 'POST', body: form})
        if (!response.ok) {
            const body = (await response.json().catch(() => null)) as { message?: string } | null
            throw new Error(body?.message || `任务提交失败 (${response.status})`)
        }
        return response.json() as Promise<IngestTask>
    },
    /** 粘贴文本异步入库（大文本避免请求线程被向量化阻塞）。 */
    addDocumentAsync: (title: string, content: string) =>
        request<IngestTask>(KNOWLEDGE_API_ROOT, '/documents/async', {
            method: 'POST',
            body: JSON.stringify({title, content}),
        }),
    /** 最近灌入任务列表（上限 50），供任务面板轮询。 */
    tasks: () => request<IngestTask[]>(KNOWLEDGE_API_ROOT, '/tasks'),
    /** 取消任务：INDEXING（原子写索引）开始前生效。 */
    cancelTask: (taskId: string) =>
        request<{ accepted: boolean; taskId: string }>(KNOWLEDGE_API_ROOT, `/tasks/${taskId}/cancel`,
            {method: 'POST'}),
    /** 删除文档（RogueMemory namespace + DuckDB 元数据）。 */
    deleteDocument: (docId: string) =>
        request<{ deleted: boolean; docId: string }>(KNOWLEDGE_API_ROOT, `/documents/${docId}`, {method: 'DELETE'}),
    /** 检索测试：kbId 优先于 namespace（文档范围）；两者都缺省为全库检索。 */
    search: (query: string, topK: number, namespace?: string, kbId?: string) =>
        request<{ query: string; kbId: string; namespace: string; hits: KnowledgeHit[] }>(
            KNOWLEDGE_API_ROOT, '/search', {
            method: 'POST',
            body: JSON.stringify({query, topK, namespace, kbId}),
        }),
    /** 知识库清单（含默认库）。 */
    bases: () => request<KnowledgeBase[]>(KNOWLEDGE_API_ROOT, '/bases'),
    /** 创建知识库（name 必填，切片/检索参数可选）。 */
    createBase: (name: string, description?: string) =>
        request<KnowledgeBase>(KNOWLEDGE_API_ROOT, '/bases', {
            method: 'POST',
            body: JSON.stringify({name, description}),
        }),
    /** 删除知识库：库内须无文档；默认库不可删。 */
    deleteBase: (kbId: string) =>
        request<{ deleted: boolean; kbId: string }>(KNOWLEDGE_API_ROOT, `/bases/${kbId}`,
            {method: 'DELETE'}),
    /** 应用向量模型档案到知识库；后端会持久化，重启后自动恢复。 */
    configureEmbedding: (payload: {
        embeddingEndpoint: string; embeddingApiKey: string; embeddingModel: string;
        searchMode: KnowledgeSearchMode
    }) => request<KnowledgeStatus>(KNOWLEDGE_API_ROOT, '/embedding', {
        method: 'POST',
        body: JSON.stringify(payload),
    }),
    /** 用户自建向量预设清单（服务端持久化，重启后仍可复用）。 */
    embeddingPresets: () => request<EmbeddingPreset[]>(KNOWLEDGE_API_ROOT, '/embedding-presets'),
    /** 新增或同名覆盖向量预设；返回更新后的清单。 */
    saveEmbeddingPreset: (preset: EmbeddingPreset) =>
        request<EmbeddingPreset[]>(KNOWLEDGE_API_ROOT, '/embedding-presets', {
            method: 'POST',
            body: JSON.stringify(preset),
        }),
    /** 删除指定名称的向量预设；返回更新后的清单。 */
    deleteEmbeddingPreset: (name: string) =>
        request<EmbeddingPreset[]>(KNOWLEDGE_API_ROOT, `/embedding-presets/${encodeURIComponent(name)}`,
            {method: 'DELETE'}),
    /** 重建知识库：清空向量与元数据并重新灌入示例。 */
    rebuild: () => request<KnowledgeStatus>(KNOWLEDGE_API_ROOT, '/rebuild', {method: 'POST'}),
    /** 全量快照导出地址（文档含原文 + 全部向量缓存），浏览器直接下载。 */
    exportSnapshotUrl: `${KNOWLEDGE_API_ROOT}/export`,
    /** 导入全量快照：源优先 upsert 合并，随后后端用缓存原地重建索引（零重新向量化）。 */
    importSnapshot: async (file: File): Promise<KnowledgeSyncResult> => {
        const form = new FormData()
        form.append('file', file)
        const response = await fetch(`${KNOWLEDGE_API_ROOT}/import`, {method: 'POST', body: form})
        if (!response.ok) {
            const body = (await response.json().catch(() => null)) as { message?: string } | null
            throw new Error(body?.message || `快照导入失败 (${response.status})`)
        }
        return response.json() as Promise<KnowledgeSyncResult>
    },
}

const GRAPH_API_ROOT = '/api/graph'

export const graphApi = {
    /** 图谱画布全量视图：谓词词典 + TBox + 实例节点 + 关系。 */
    view: () => request<GraphView>(GRAPH_API_ROOT, '/view'),
    /** 图谱统计：实体/关系/本体类计数、分布与入库状态。 */
    summary: () => request<GraphSummary>(GRAPH_API_ROOT, '/summary'),
    /** 站点清单：图谱实体计数 + 档案概要统计。 */
    stations: () => request<GraphStation[]>(GRAPH_API_ROOT, '/stations'),
    /** 单站完整档案：母线/主变/间隔/设备/连接/质量明细。 */
    stationDocument: (name: string) =>
        request<Record<string, unknown>>(GRAPH_API_ROOT, `/station/${encodeURIComponent(name)}`),
    /** 知识问答事实检索：站点/类别/关键词过滤 + 分页。 */
    facts: (params: { station?: string; category?: string; q?: string; limit?: number; offset?: number }) => {
        const query = new URLSearchParams()
        if (params.station) query.set('station', params.station)
        if (params.category) query.set('category', params.category)
        if (params.q) query.set('q', params.q)
        query.set('limit', String(params.limit ?? 50))
        query.set('offset', String(params.offset ?? 0))
        return request<GraphFactsResult>(GRAPH_API_ROOT, `/facts?${query.toString()}`)
    },
    /** 节点详情：属性/别名 + 按中文谓词分组的邻接关系。 */
    nodeDetail: (id: string) =>
        request<GraphNodeDetail>(GRAPH_API_ROOT, `/node?id=${encodeURIComponent(id)}`),
    /** 手动重建图谱入库（资源指纹比对，幂等）。 */
    reimport: () => request<Record<string, unknown>>(GRAPH_API_ROOT, '/import', {method: 'POST'}),
}
