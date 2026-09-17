/**
 * Mermaid 渲染器：全局串行队列 + 按图源缓存。
 *
 * mermaid 的 render 依赖全局内部状态（临时容器与度量），多个组件并发渲染
 * 会导致部分 SVG 输出为空；因此所有调用进入同一 FIFO 队列逐个执行，
 * 相同图源（流式重渲染、多条相同图表）直接复用缓存的 SVG 字符串。
 */
type MermaidInstance = (typeof import('mermaid'))['default']

let mermaidModulePromise: Promise<MermaidInstance> | null = null
let initialized = false
let queue: Promise<unknown> = Promise.resolve()
const svgCache = new Map<string, string>()
const failed = new Map<string, string>()

async function ensureMermaid(): Promise<MermaidInstance> {
    if (!mermaidModulePromise) {
        // 动态 import 返回模块命名空间，图表 API 在 default 导出上
        mermaidModulePromise = import('mermaid').then(module => module.default)
    }
    const mermaid = await mermaidModulePromise
    if (!initialized) {
        mermaid.initialize({startOnLoad: false, theme: 'default', securityLevel: 'strict'})
        initialized = true
    }
    return mermaid
}

/**
 * 渲染一段 mermaid 图源为 SVG 字符串；语法错误抛出原始错误（调用方回退展示源码）。
 * 相同图源命中缓存立即返回；请求进入全局串行队列，避免并发渲染冲突。
 */
export function renderMermaid(source: string): Promise<string> {
    const cached = svgCache.get(source)
    if (cached) return Promise.resolve(cached)
    const knownFailure = failed.get(source)
    if (knownFailure) return Promise.reject(new Error(knownFailure))

    const task = queue.then(async () => {
        const mermaid = await ensureMermaid()
        const {svg} = await mermaid.render(`md-mermaid-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, source)
        svgCache.set(source, svg)
        return svg
    })
    queue = task.catch(error => {
        failed.set(source, error instanceof Error ? error.message : String(error))
        return '' // 吞掉以保持队列继续；真实错误在 task 上抛给调用方
    })
    return task
}
