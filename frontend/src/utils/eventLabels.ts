/**
 * AgentEventType（agents-flex 2.2.9，共 31 个）的中文标题。
 * 事件流面板与执行链路面板共用，避免两处各写一份转换逻辑。
 */
export const EVENT_LABELS: Record<string, string> = {
  TURN_STARTED: '轮次开始',
  STEP_STARTED: '步骤开始',
  STEP_COMPLETED: '步骤完成',
  MODEL_STARTED: '模型请求开始',
  MODEL_TEXT_DELTA: '模型输出增量',
  MODEL_REASONING_DELTA: '模型思考增量',
  MODEL_TOOL_CALL_DELTA: '工具调用增量',
  MODEL_COMPLETED: '模型请求完成',
  CONTEXT_COMPRESSION_STARTED: '上下文压缩开始',
  CONTEXT_COMPRESSION_COMPLETED: '上下文压缩完成',
  CONTEXT_COMPRESSION_FAILED: '上下文压缩失败',
  TOOL_STARTED: '工具调用开始',
  TOOL_PROGRESS: '工具执行中',
  TOOL_COMPLETED: '工具调用完成',
  TOOL_FAILED: '工具调用失败',
  TOOL_APPROVAL_REQUESTED: '等待工具审批',
  TOOL_INPUT_REQUESTED: '等待用户输入',
  EXTERNAL_TOOL_REQUESTED: '外部工具请求',
  EXTERNAL_TOOL_COMPLETED: '外部工具完成',
  EXTERNAL_TOOL_FAILED: '外部工具失败',
  SNAPSHOT_SAVED: '快照已保存',
  TURN_SUSPENDED: '轮次已挂起',
  TURN_RESUMED: '轮次已恢复',
  CANCELLATION_REQUESTED: '已请求取消',
  RETRY_SCHEDULED: '等待重试',
  MAX_ITERATIONS_REACHED: '迭代次数耗尽',
  MAX_STEPS_REACHED: '步骤数耗尽',
  TURN_COMPLETED: '轮次完成',
  TURN_FAILED: '轮次失败',
  TURN_CANCELLED: '轮次已取消',
  BUDGET_EXCEEDED: '预算耗尽',
}

/**
 * 取事件类型的中文标题；未收录的类型回退为可读的 Title Case 原文，
 * 这样升级 agents-flex 新增枚举时不会出现空白标签。
 * @param type 原生 AgentEventType 名称
 */
export function eventLabel(type: string): string {
  return EVENT_LABELS[type]
      ?? type.split('_').map((word) => word[0] + word.slice(1).toLowerCase()).join(' ')
}
