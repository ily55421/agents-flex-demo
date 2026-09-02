import type {
    AgentDefinition,
    AgentRun,
    CreateAgentPayload,
    CreateRunPayload,
    ModelStatus,
    TraceView,
} from '@/types/agent'

const RUN_API_ROOT = '/api/agent/runs'
const AGENT_API_ROOT = '/api/agent/agents'

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
    /** 使用完整配置在后端构建并注册真实 Agents-Flex Agent。 */
    createAgent: (payload: CreateAgentPayload) =>
        request<AgentDefinition>(AGENT_API_ROOT, '', {method: 'POST', body: JSON.stringify(payload)}),
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
