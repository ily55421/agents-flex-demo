import {computed, onBeforeUnmount, ref} from 'vue'
import {defineStore} from 'pinia'
import {agentApi} from '@/api/agent'
import type {
    AgentDefinition,
    AgentEvent,
    AgentRun,
    CreateAgentPayload,
    CreateRunPayload,
    ModelStatus,
    TraceView,
} from '@/types/agent'

export const useAgentRun = defineStore('agent-run', () => {
    // run 是后端 Snapshot 的本地镜像；trace 是 OTel 导出结果，两者共同驱动全部面板。
    const run = ref<AgentRun | null>(null)
    const agent = ref<AgentDefinition | null>(null)
    const trace = ref<TraceView | null>(null)
    const modelStatus = ref<ModelStatus | null>(null)
    const streamingText = ref('')
    const streamingReasoning = ref('')
    const busy = ref(false)
    const error = ref<string | null>(null)
    const connectionState = ref<'idle' | 'connecting' | 'live' | 'reconnecting'>('idle')
    let eventSource: EventSource | null = null
    let refreshTimer: number | null = null
    let streamFlushTimer: number | null = null
    let streamFlushFrame: number | null = null
    const seenEventIds = new Set<string>()
    const pendingTextChunks: string[] = []
    const pendingReasoningChunks: string[] = []
    const pendingEvents: AgentEvent[] = []

    /** 判断事件是否为模型逐片段输出，供聊天正文批处理和 Trace 精简逻辑复用。 */
    function isStreamingDelta(event: AgentEvent) {
        return event.type === 'MODEL_TEXT_DELTA' || event.type === 'MODEL_REASONING_DELTA'
    }

    /**
     * 安装后端 Snapshot，并合并请求飞行期间已由 SSE 收到的新事件。
     * 右侧事件流保留全部原生事件；组件使用虚拟滚动控制实际 DOM 数量。
     * @param snapshot 后端返回的完整 Run Snapshot
     */
    function installRun(snapshot: AgentRun) {
        const sameRun = run.value?.runId === snapshot.runId
        const snapshotIds = new Set(snapshot.events.map((item) => item.eventId))
        const browserEvents = sameRun ? [...(run.value?.events ?? []), ...pendingEvents] : []
        const mergedIds = new Set(snapshotIds)
        const inFlightEvents: AgentEvent[] = []
        for (const item of browserEvents) {
            if (mergedIds.has(item.eventId)) continue
            mergedIds.add(item.eventId)
            inFlightEvents.push(item)
        }
        pendingEvents.length = 0

        seenEventIds.clear()
        const completeEvents = [...snapshot.events, ...inFlightEvents]
            .sort((left, right) => left.sequence - right.sequence)
        completeEvents.forEach((item) => seenEventIds.add(item.eventId))
        run.value = {
            ...snapshot,
            events: completeEvents,
        }
    }

    /**
     * 移除 Trace 中不会被生命周期面板消费的 Token Delta，降低多轮对话后的响应式内存占用。
     * @param snapshot 后端返回的完整 Trace Snapshot
     */
    function installTrace(snapshot: TraceView) {
        trace.value = {
            ...snapshot,
            events: snapshot.events.filter((item) => !isStreamingDelta(item)),
        }
    }

    /**
     * 把当前批次 Token 和原生事件一次性提交给 Vue；响应式更新最多每 40ms 发生一次。
     * 事件数组保留全部 Delta，但批量追加配合右侧虚拟列表，不会为每个 Token 单独重绘。
     */
    function flushStreamChunks() {
        streamFlushFrame = null
        if (pendingEvents.length) {
            if (run.value) run.value.events.push(...pendingEvents)
            pendingEvents.length = 0
        }
        if (pendingTextChunks.length) {
            streamingText.value += pendingTextChunks.join('')
            pendingTextChunks.length = 0
        }
        if (pendingReasoningChunks.length) {
            streamingReasoning.value += pendingReasoningChunks.join('')
            pendingReasoningChunks.length = 0
        }
    }

    /** 安排下一次 40ms Token 批量刷新；已有定时任务时只继续收集片段。 */
    function scheduleStreamFlush() {
        if (streamFlushTimer !== null || streamFlushFrame !== null) return
        streamFlushTimer = window.setTimeout(() => {
            streamFlushTimer = null
            streamFlushFrame = window.requestAnimationFrame(flushStreamChunks)
        }, 40)
    }

    /** 在模型结束事件到达时立即提交最后一批 Token，防止随后 Snapshot 刷新与未执行动画帧交错。 */
    function flushStreamNow() {
        if (streamFlushTimer !== null) window.clearTimeout(streamFlushTimer)
        if (streamFlushFrame !== null) window.cancelAnimationFrame(streamFlushFrame)
        streamFlushTimer = null
        streamFlushFrame = null
        flushStreamChunks()
    }

    /** 清理尚未提交的 Token、事件、定时器和动画帧，供切换 Run 或离开页面时使用。 */
    function resetStreamBuffer() {
        if (streamFlushTimer !== null) window.clearTimeout(streamFlushTimer)
        if (streamFlushFrame !== null) window.cancelAnimationFrame(streamFlushFrame)
        streamFlushTimer = null
        streamFlushFrame = null
        pendingTextChunks.length = 0
        pendingReasoningChunks.length = 0
        pendingEvents.length = 0
    }

    /** 当前是否已经加载或创建 Run，供需要简化显示逻辑的组件使用。 */
    const hasRun = computed(() => run.value !== null)
    /** 判断当前状态是否为不可再推进的终态，统一控制结果面板和新建入口。 */
    const isTerminal = computed(() =>
        ['COMPLETED', 'FAILED', 'CANCELLED', 'MAX_ITERATIONS_REACHED',
            'MAX_STEPS_REACHED', 'BUDGET_EXCEEDED'].includes(run.value?.status ?? ''),
    )

    /**
     * 提交完整配置并等待后端真实 Agent Builder 成功；只有成功响应才更新当前 Agent。
     * @param configuration 用户在左侧填写的完整 Agent 配置
     */
    async function createAgent(configuration: CreateAgentPayload) {
        busy.value = true
        error.value = null
        try {
            agent.value = await agentApi.createAgent(configuration)
            // 创建 Agent 时后端会按 UI 配置重建模型；重新读取安全状态，让头部立即反映新 Key。
            modelStatus.value = await agentApi.modelStatus()
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : 'Agent 创建失败'
            throw cause
        } finally {
            busy.value = false
        }
    }

    /**
     * 包装所有写命令：设置 busy、清理旧错误、更新 Snapshot/Trace，并安排一次兜底刷新。
     * @param action 返回最新 AgentRun 的 API 命令
     */
    async function execute(action: () => Promise<AgentRun>) {
        // 所有写命令走同一条事务式 UI 流程：锁定按钮、执行命令、刷新 Trace、安排一次兜底刷新。
        busy.value = true
        error.value = null
        try {
            const nextRun = await action()
            installRun(nextRun)
            installTrace(await agentApi.trace(nextRun.runId))
            scheduleRefresh()
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : '操作失败'
            throw cause
        } finally {
            busy.value = false
        }
    }

    /**
     * 创建 Run、把 runId 写入 URL，并建立该 Run 的 SSE 连接。
     * @param payload 用户配置的任务与预算
     */
    async function create(payload: CreateRunPayload) {
        resetStreamBuffer()
        streamingText.value = ''
        streamingReasoning.value = ''
        await execute(() => agentApi.create(payload))
        if (run.value) {
            setLocationRun(run.value.runId)
            connect(run.value.runId)
        }
    }

    /**
     * 在当前终态 Turn 所属会话中追加消息，切换到新 runId、建立 SSE 后再启动模型。
     * @param message 用户的新一轮对话文本
     */
    async function continueConversation(message: string) {
        if (!run.value || !isTerminal.value) return
        const previousRunId = run.value.runId
        resetStreamBuffer()
        streamingText.value = ''
        streamingReasoning.value = ''
        await execute(() => agentApi.continueConversation(previousRunId, message))
        if (run.value) {
            setLocationRun(run.value.runId)
            connect(run.value.runId)
            await start()
        }
    }

    /** 启动当前 READY Run。 */
    const start = () => requireRun((id) => execute(() => agentApi.start(id)))
    /** 请求挂起当前 Run。 */
    const suspend = () => requireRun((id) => execute(() => agentApi.suspend(id)))
    /** 恢复当前手工暂停的 Run。 */
    const resume = () => requireRun((id) => execute(() => agentApi.resume(id)))
    /** 取消当前 Run。 */
    const cancel = () => requireRun((id) => execute(() => agentApi.cancel(id)))
    /** 提交当前动态表单值并恢复 Agent。 */
    const submitForm = (values: Record<string, unknown>) =>
        requireRun((id) => execute(() => agentApi.submitForm(id, values)))
    /** 提交批准或拒绝决定；拒绝时可携带原因。 */
    const approve = (approved: boolean, reason?: string) =>
        requireRun((id) => execute(() => agentApi.approve(id, approved, reason)))

    /**
     * 并行刷新 Run Snapshot 与 Trace；响应返回前若已切换 Run，则丢弃旧响应。
     */
    async function refresh() {
        if (!run.value) return
        try {
            const runId = run.value.runId
            const [nextRun, nextTrace] = await Promise.all([
                agentApi.get(runId),
                agentApi.trace(runId),
            ])
            // 用户可能在请求返回前切换 Run；只允许当前 Run 的响应覆盖界面状态。
            if (run.value?.runId === runId) {
                installRun(nextRun)
                installTrace(nextTrace)
                // 完整助手消息写入 ChatMemory 后移除临时流文本，避免同一回答显示两遍。
                if (nextRun.events.some((item) => item.type === 'MODEL_COMPLETED')) {
                    streamingText.value = ''
                    streamingReasoning.value = ''
                }
            }
            error.value = null
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : '状态刷新失败'
        }
    }

    /**
     * 页面加载时根据 URL 的 run 参数恢复既有 Snapshot、Trace 和 SSE 连接。
     */
    async function initialize() {
        // 模型状态与 Run 恢复并行独立：即使没有历史 Run，聊天框也能准确判断是否可发送。
        try {
            modelStatus.value = await agentApi.modelStatus()
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : '无法读取模型配置'
        }
        if (run.value) return
        const runId = new URL(window.location.href).searchParams.get('run')
        if (!runId) return
        busy.value = true
        try {
            const [restoredRun, restoredTrace] = await Promise.all([
                agentApi.get(runId),
                agentApi.trace(runId),
            ])
            installRun(restoredRun)
            agent.value = restoredRun.agent
            installTrace(restoredTrace)
            connect(runId)
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : '无法恢复当前 Run'
        } finally {
            busy.value = false
        }
    }

    /**
     * 清空本地工作区、定时器和 SSE 连接，但保留后端 Run 供 URL 再次恢复。
     */
    function reset() {
        // Reset 仅清空本地工作区，不删除服务端 Run，便于之后通过 URL 再次恢复。
        eventSource?.close()
        eventSource = null
        if (refreshTimer !== null) window.clearTimeout(refreshTimer)
        refreshTimer = null
        resetStreamBuffer()
        seenEventIds.clear()
        run.value = null
        trace.value = null
        streamingText.value = ''
        streamingReasoning.value = ''
        error.value = null
        connectionState.value = 'idle'
        setLocationRun(null)
    }

    /**
     * 使用 history.replaceState 同步 URL 中的 run 参数，避免新增浏览器历史记录。
     * @param runId 当前 Run ID；传 null 时移除参数
     */
    function setLocationRun(runId: string | null) {
        const url = new URL(window.location.href)
        runId ? url.searchParams.set('run', runId) : url.searchParams.delete('run')
        window.history.replaceState({}, '', url)
    }

    /**
     * 用 90ms 防抖合并密集原生事件，只保留一次最新 Snapshot/Trace 刷新。
     */
    function scheduleRefresh() {
        // 短延迟合并一批连续事件，避免每个 Token/Tool 事件都同时触发两次 HTTP 查询。
        if (refreshTimer !== null) window.clearTimeout(refreshTimer)
        refreshTimer = window.setTimeout(() => {
            refreshTimer = null
            void refresh()
        }, 90)
    }

    /**
     * 建立指定 Run 的 SSE 连接，去重增量事件并在断线时更新连接状态。
     * @param runId 要订阅的 Run ID
     */
    function connect(runId: string) {
        // SSE 提供低延迟事件追加；收到事件后仍刷新 Snapshot，确保预算、状态和 Trace 最终一致。
        eventSource?.close()
        connectionState.value = 'connecting'
        eventSource = new EventSource(agentApi.eventUrl(runId))
        eventSource.addEventListener('connected', () => {
            connectionState.value = 'live'
        })
        eventSource.addEventListener('agent-event', (event) => {
            const nativeEvent = JSON.parse((event as MessageEvent).data) as AgentEvent
            // Set 提供 O(1) 去重；全部事件先进入普通缓冲区，再按绘制周期批量提交给 Vue。
            if (seenEventIds.has(nativeEvent.eventId)) return
            seenEventIds.add(nativeEvent.eventId)

            if (nativeEvent.type === 'MODEL_STARTED') {
                resetStreamBuffer()
                streamingText.value = ''
                streamingReasoning.value = ''
            }

            if (run.value) pendingEvents.push(nativeEvent)
            if (nativeEvent.type === 'MODEL_TEXT_DELTA') {
                pendingTextChunks.push(String(nativeEvent.data.content ?? ''))
            } else if (nativeEvent.type === 'MODEL_REASONING_DELTA') {
                pendingReasoningChunks.push(String(nativeEvent.data.content ?? ''))
            } else {
                if (nativeEvent.type === 'MODEL_COMPLETED') flushStreamNow()
                scheduleRefresh()
            }
            scheduleStreamFlush()
        })
        eventSource.onerror = () => {
            connectionState.value = 'reconnecting'
        }
    }

    /**
     * 仅在存在当前 Run 时执行回调，简化所有 Run 命令的空值保护。
     * @param callback 接收当前 runId 的异步操作
     */
    function requireRun(callback: (runId: string) => Promise<void>) {
        if (!run.value) return Promise.resolve()
        return callback(run.value.runId)
    }

    /** 清除当前界面错误提示，不改变 Run 状态。 */
    function clearError() {
        error.value = null
    }

    onBeforeUnmount(() => {
        eventSource?.close()
        if (refreshTimer !== null) window.clearTimeout(refreshTimer)
        resetStreamBuffer()
    })

    return {
        run,
        agent,
        trace,
        modelStatus,
        streamingText,
        streamingReasoning,
        busy,
        error,
        connectionState,
        hasRun,
        isTerminal,
        createAgent,
        create,
        continueConversation,
        initialize,
        reset,
        start,
        suspend,
        resume,
        cancel,
        submitForm,
        approve,
        refresh,
        clearError,
    }
})
