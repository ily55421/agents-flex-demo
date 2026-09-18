// AgentStatus 与 Agents-Flex AgentTurnStatus 一一对应，前端不自行发明额外运行状态。
export type AgentStatus =
  | 'READY'
  | 'RUNNING'
  | 'WAITING_FOR_USER'
  | 'WAITING_FOR_APPROVAL'
  | 'RETRY_SCHEDULED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'MAX_ITERATIONS_REACHED'
  | 'MAX_STEPS_REACHED'
  | 'BUDGET_EXCEEDED'

export interface AgentEvent {
  eventId: string
  runId: string
  agentId: string
  agentVersion: string
  sequence: number
  type: string
  occurredAt: number
  data: Record<string, unknown>
  source: 'AGENTS_FLEX_NATIVE'
}

// 动态表单直接消费 Suspension metadata 中的 JSON Schema，避免把业务字段硬编码进组件。
export interface JsonSchemaField {
  type: string
  title: string
  default?: string | number | boolean
  enum?: string[]
  description?: string
}

export interface JsonSchema {
  title: string
  description?: string
  type: 'object'
  properties: Record<string, JsonSchemaField>
  required?: string[]
}

export interface Suspension {
  type: 'USER_INPUT' | 'TOOL_APPROVAL' | 'RETRY'
  correlationId: string | null
  message: string
  resumePhase: string
  metadata: {
    schema?: JsonSchema
    formKey?: string
    toolName?: string
    approvalCode?: string
    approvalReason?: string
    riskLevel?: string
    [key: string]: unknown
  }
}

// 以下 View 类型是后端对原生 Snapshot、压缩状态与 OTel 数据的只读投影。
export interface BudgetView {
  inputTokens: number
  outputTokens: number
  usedTokens: number
  tokenLimit: number
  usedToolCalls: number
  toolCallLimit: number
  usedDurationMs: number
  durationLimitMs: number
  estimatedCost: number
  costSource: 'DEMO_PROJECTION'
  durationSemantics: 'WALL_CLOCK_INCLUDING_HUMAN_WAIT'
}

export interface CompressionView {
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'FAILED'
  startedAt: number
  completedAt: number
  trigger: 'PENDING_MESSAGES' | 'PENDING_TURNS' | 'PENDING_TOKENS' | 'ALWAYS' | 'NEVER'
  condition: string
  beforeMessages: number
  pendingMessages: number
  afterMessages: number
  beforeTokens: number
  afterTokens: number
  originalContext: string[]
  summary: string | null
  source: 'AGENTS_FLEX_NATIVE'
}

// ChatMessageView 直接对应后端 ChatMemory，不在浏览器中伪造模型回答或工具调用。
export interface ChatMessageView {
  id: string | null
  role: 'user' | 'assistant' | 'tool'
  content: string | null
  reasoning: string | null
  toolCalls: ToolCallView[]
}

// ModelStatus 是可公开的连接信息，后端永远不会在该结构中返回 apiKey。
export interface ModelStatus {
  configured: boolean
  provider: string
  model: string
  endpoint: string
  temperature: number
  thinkingEnabled: boolean
  retryEnabled: boolean
  retryCount: number
  retryInitialDelayMillis: number
}

export interface RetryPolicyView {
  maxRetries: number
  initialDelayMillis: number
  maxDelayMillis: number
  multiplier: number
  source: 'AGENTS_FLEX_NATIVE'
}

export interface ToolCallView {
  id: string
  name: string
  arguments: Record<string, unknown>
}

export interface CapabilitySource {
  name: string
  source: string
}

export interface AgentRun {
  runId: string
  agentId: string
  agentVersion: string
  agent: AgentDefinition
  conversationId: string
  task: string
  status: AgentStatus
  phase: string
  manualPause: boolean
  pauseRequested: boolean
  pauseRequestedAt: number
  processing: boolean
  createdAt: number
  completedAt: number
  iterationCount: number
  maxIterations: number
  stepCount: number
  maxSteps: number
  retryCount: number
  maxRetries: number
  toolCallCount: number
  finalOutput: string | null
  error: string | null
  budgetExceededReason: string | null
  pendingTask: string | null
  pendingToolCalls: ToolCallView[]
  messages: ChatMessageView[]
  suspension: Suspension | null
  budget: BudgetView
  compression: CompressionView
  retryPolicy: RetryPolicyView
  events: AgentEvent[]
  capabilities: CapabilitySource[]
}

export interface TraceView {
  runId: string
  agentId: string
  agentVersion: string
  status: AgentStatus
  durationMs: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  spans: TraceSpan[]
  metrics: TraceMetric[]
  events: AgentEvent[]
  source: 'AGENTS_FLEX_OPENTELEMETRY'
}

export interface TraceSpan {
  traceId: string
  spanId: string
  parentSpanId: string
  name: string
  kind: string
  status: 'UNSET' | 'OK' | 'ERROR'
  statusDescription: string
  startTimeMs: number
  endTimeMs: number
  durationMs: number
  attributes: Record<string, unknown>
  provider?: string
  model?: string
  inputTokens?: number
  outputTokens?: number
  toolName?: string
  arguments?: string
  result?: string
  output?: string
}

export interface TraceMetric {
  name: string
  unit: string
  type: string
  value: number
  count: number
}

export interface CreateAgentPayload {
  modelProvider: string
  modelEndpoint: string
  modelRequestPath: string
  modelApiKey: string
  modelName: string
  modelTemperature: number
  modelThinkingEnabled: boolean
  modelThinkingProtocol: string
  modelSeed: string
  modelTopP: number | null
  modelTopK: number | null
  modelMaxTokens: number | null
  modelStop: string[]
  modelIncludeUsage: boolean
  modelResponseFormat: 'NONE' | 'JSON_OBJECT'
  modelRetryEnabled: boolean
  modelRetryCount: number
  modelRetryInitialDelayMillis: number
  name: string
  version: string
  description: string
  instructions: string
  maxIterations: number
  maxSteps: number
  maxAttachedTurns: number
  maxAttachedTokens: number
  maxAttachedMessages: number
  maxInputTokens: number
  maxOutputTokens: number
  maxTotalTokens: number
  maxToolCalls: number
  maxDurationMillis: number
  maxRetries: number
  initialDelayMillis: number
  maxDelayMillis: number
  retryMultiplier: number
  compressionDecider: 'PENDING_MESSAGES' | 'PENDING_TURNS' | 'PENDING_TOKENS' | 'ALWAYS' | 'NEVER'
  compressionMessageThreshold: number
  compressionTurnThreshold: number
  compressionTokenThreshold: number
  compressionMode: 'WHOLE_HISTORY' | 'PER_MESSAGE'
  toolErrorStrategy: 'FAIL_RUN' | 'RETURN_ERROR_TO_MODEL'
  interruptedToolMessageTemplate: string
  interruptedTurnMessageTemplate: string
  cancellationReason: string
  modelCallTimeoutMillis: number
  toolExecutionTimeoutMillis: number
  externalToolTimeoutMillis: number
  approvalTimeoutMillis: number
  userInputTimeoutMillis: number
  suspensionExpirationStrategy: 'REJECT_RESUME' | 'FAIL_TURN' | 'CANCEL_TURN'
  toolResultMaxCharacters: number
  externalToolResultMaxCharacters: number
  toolResultOverflowStrategy: 'FAIL' | 'TRUNCATE'
  toolExecutionMode: 'SEQUENTIAL' | 'PARALLEL'
  maxParallelToolCalls: number
  parallelFailureStrategy: 'FAIL_FAST' | 'RETURN_ERRORS_TO_MODEL'
  compactCompletedToolTurns: boolean
  compressionKeepRecentTurns: number
  compressionFailureStrategy: 'FAIL' | 'USE_ORIGINAL'
  compressionInstruction: string
  compressionHistoryHeader: string
  compressionSummaryPrefix: string
  compressionPerMessageRequest: string
  compressionModelCallTimeoutMillis: number
  compressionMaxInputCharacters: number
  compressionMaxOutputCharacters: number
  embeddingEndpoint: string
  embeddingApiKey: string
  embeddingModel: string
  knowledgeSearchMode: KnowledgeSearchMode
  knowledgeNamespace: string
}

// AgentDefinition 是后端完成真实 Agents-Flex Builder 校验后返回的安全配置，不包含任何模型密钥字段。
export interface AgentDefinition extends Omit<CreateAgentPayload, 'modelApiKey'> {
  agentId: string
  tools: string[]
  approvalPolicy: string
  createdAt: number
  // runnable=false 表示该 Agent 只存在于 DuckDB 归档中（服务重启后 Runner 未重建），需重新创建才能对话。
  runnable?: boolean
}

export interface CreateRunPayload {
  agentId: string
  task: string
}

// ModelProfile 只覆盖“模型连接 + 生成预算”相关字段；配置档案栏可一键切换并整体应用到表单。
export interface ModelProfile {
  profileName: string
  modelProvider: string
  modelEndpoint: string
  modelRequestPath: string
  modelApiKey: string
  modelName: string
  modelTemperature: number
  modelThinkingEnabled: boolean
  modelThinkingProtocol: string
  modelSeed: string
  modelTopP: number | null
  modelTopK: number | null
  modelMaxTokens: number | null
  modelStop: string[]
  modelIncludeUsage: boolean
  modelResponseFormat: 'NONE' | 'JSON_OBJECT'
  modelRetryEnabled: boolean
  modelRetryCount: number
  modelRetryInitialDelayMillis: number
  // Agent 级 Token 预算与上下文挂载限制；0 表示不限制（挂载 Token）或无预算（输入/输出/总）。
  maxInputTokens: number
  maxOutputTokens: number
  maxTotalTokens: number
  maxAttachedTokens: number
}

// EmbeddingProfile 是 RAG 知识库向量模型档案；与聊天档案共用“内置预设 + 我的档案”机制。
export interface EmbeddingProfile {
  profileName: string
  embeddingEndpoint: string
  embeddingApiKey: string
  embeddingModel: string
  knowledgeSearchMode: KnowledgeSearchMode
}

export type KnowledgeSearchMode = 'HYBRID' | 'VECTOR_ONLY' | 'KEYWORD_ONLY'

// SavedEmbedding 是后端持久化的最近一次应用向量配置；重启后自动恢复并回填表单。
export interface SavedEmbedding {
  endpoint: string
  apiKey: string
  model: string
  searchMode: KnowledgeSearchMode
}

// EmbeddingPreset 是用户自建的向量模型预设，存后端设置文件，重启后仍可复用。
export interface EmbeddingPreset {
  name: string
  endpoint: string
  model: string
  apiKey: string
}

export interface KnowledgeStatus {
  ready: boolean
  searchMode: KnowledgeSearchMode
  topK: number
  embeddingConfigured: boolean
  embeddingSignature: string | null
  embeddingModel: string | null
  dimension: number
  documentCount: number
  chunkCount: number
  signatures: string[]
  seeded: boolean
  lastError: string | null
  mmapPath: string
  savedEmbedding: SavedEmbedding | null
}

export interface KnowledgeDocument {
  docId: string
  title: string
  source: 'MANUAL' | 'FILE' | 'BUILTIN'
  chunkCount: number
  charCount: number
  embeddingSignature: string | null
  createdAt: number
}

export interface KnowledgeHit {
  docId: string
  title: string | null
  source: string | null
  chunkIndex: string | null
  /** 命中片段所属的 Markdown 标题路径（如「切片策略 > 保护规则」）；无结构标题时为 null。 */
  headingPath: string | null
  content: string
  score: number
  mode: KnowledgeSearchMode
}

/** 知识库快照导入结果：文档与向量的合并计数及索引刷新状态。 */
export interface KnowledgeSyncResult {
  documentsUpserted: number
  documentsAdded: number
  vectorsAdded: number
  vectorsSkipped: number
  indexRefreshed: boolean
  lastError: string | null
}

/** 支持的入库格式与对应解析器（GET /api/knowledge/parsers）。 */
export interface ParserDescriptor {
  extension: string
  parser: string
}

/** 文件上传入库结果：文档元数据 + 解析器附带的格式信息（页数/表数/页演示数）。 */
export interface KnowledgeUploadResult extends KnowledgeDocument {
  parser?: string
  pageCount?: string
  sheetCount?: string
  slideCount?: string
  format?: string
}

/** 切片预览单项：序号、标题路径、字符数与正文（POST /api/knowledge/chunker/preview）。 */
export interface ChunkPreviewItem {
  index: number
  headingPath: string
  charCount: number
  content: string
}

/** 知识灌入任务视图（GET /api/knowledge/tasks）。 */
export interface IngestTask {
  taskId: string
  docId: string | null
  title: string
  source: string
  fileName: string | null
  status: 'PENDING' | 'PROCESSING' | 'FINALIZING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  stage: string | null
  progress: number
  chunkCount: number
  error: string | null
  createdAt: number
  updatedAt: number
}

// SessionSummary 是同一 conversationId 的全部 Turn 在会话列表中的聚合摘要。
export interface SessionSummary {
  conversationId: string
  latestRunId: string
  title: string
  status: AgentStatus
  createdAt: number
  updatedAt: number
  turns: number
  active: boolean
}

// ---------- 电力拓扑本体图谱 ----------

/** 图谱实例节点：URI、本体类别、标签、所属站、电压、别名与属性。 */
export interface GraphNode {
  id: string
  cls: string
  label: string
  station: string | null
  voltage: string | null
  aliases: string[]
  attrs: Record<string, unknown>
}

/** 图谱实例关系：源 URI、谓词、目标 URI。 */
export interface GraphEdge {
  s: string
  p: string
  o: string
}

/** 本体类层级（TBox）节点。 */
export interface TBoxNode {
  id: string
  l: string
  en: string
  parent: string | null
}

/** 本体类层级（TBox）关系：pc 为谓词中文名。 */
export interface TBoxEdge {
  s: string
  p: string
  pc: string
  o: string
}

/** /api/graph/view 返回的画布全量数据。 */
export interface GraphView {
  meta: { ns: string; inst: string; predCn: Record<string, string> }
  tbox: { nodes: TBoxNode[]; edges: TBoxEdge[] }
  nodes: GraphNode[]
  edges: GraphEdge[]
}

/** 图谱统计摘要。 */
export interface GraphSummary {
  source: string
  sourceHash: string
  hashMatch: boolean
  importedAt: string | null
  entities: number
  edges: number
  tboxClasses: number
  tboxEdges: number
  stations: number
  archiveStations: number
  archiveFacts: number
  byClass: Record<string, number>
  byPredicate: Record<string, number>
}

/** 站点清单行：图谱实体计数 + 档案概要。 */
export interface GraphStation {
  name: string
  baseVoltage: string
  summary: Record<string, unknown>
  entities?: number
  classCounts?: Record<string, number>
}

/** 邻接条目（节点详情面板）。 */
export interface GraphNeighbor {
  id: string
  label: string
  cls: string
  clsCn: string
}

/** /api/graph/node 返回的节点详情。 */
export interface GraphNodeDetail {
  node: GraphNode
  outgoing: Record<string, GraphNeighbor[]>
  incoming: Record<string, GraphNeighbor[]>
}

/** 知识问答事实行。 */
export interface GraphFactRow {
  seq: number
  station: string
  category: string
  text: string
}

/** /api/graph/facts 返回。 */
export interface GraphFactsResult {
  total: number
  rows: GraphFactRow[]
  categories: { category: string; count: number }[]
}
