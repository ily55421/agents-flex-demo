import type {
    AgentDefinition,
    AgentRun,
    CreateAgentPayload,
    CreateRunPayload,
    KnowledgeDocument,
    KnowledgeHit,
    KnowledgeSearchMode,
    KnowledgeStatus,
    ModelProfile,
    ModelStatus,
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
    /** UI 粘贴文本方式添加文档。 */
    addDocument: (title: string, content: string) =>
        request<KnowledgeDocument>(KNOWLEDGE_API_ROOT, '/documents', {
            method: 'POST',
            body: JSON.stringify({title, content}),
        }),
    /** 上传 .txt/.md 文件解析入库；multipart 不能带 JSON Content-Type。 */
    uploadDocument: async (file: File, title?: string): Promise<KnowledgeDocument> => {
        const form = new FormData()
        form.append('file', file)
        if (title) form.append('title', title)
        const response = await fetch(`${KNOWLEDGE_API_ROOT}/documents/upload`, {method: 'POST', body: form})
        if (!response.ok) {
            const body = (await response.json().catch(() => null)) as { message?: string } | null
            throw new Error(body?.message || `上传失败 (${response.status})`)
        }
        return response.json() as Promise<KnowledgeDocument>
    },
    /** 删除文档（RogueMemory namespace + DuckDB 元数据）。 */
    deleteDocument: (docId: string) =>
        request<{ deleted: boolean; docId: string }>(KNOWLEDGE_API_ROOT, `/documents/${docId}`, {method: 'DELETE'}),
    /** 检索测试：返回 TopK 命中片段与生效模式。 */
    search: (query: string, topK: number) =>
        request<{ query: string; hits: KnowledgeHit[] }>(KNOWLEDGE_API_ROOT, '/search', {
            method: 'POST',
            body: JSON.stringify({query, topK}),
        }),
    /** 应用向量模型档案到知识库。 */
    configureEmbedding: (payload: {
        embeddingEndpoint: string; embeddingApiKey: string; embeddingModel: string;
        searchMode: KnowledgeSearchMode
    }) => request<KnowledgeStatus>(KNOWLEDGE_API_ROOT, '/embedding', {
        method: 'POST',
        body: JSON.stringify(payload),
    }),
    /** 重建知识库：清空向量与元数据并重新灌入示例。 */
    rebuild: () => request<KnowledgeStatus>(KNOWLEDGE_API_ROOT, '/rebuild', {method: 'POST'}),
}
