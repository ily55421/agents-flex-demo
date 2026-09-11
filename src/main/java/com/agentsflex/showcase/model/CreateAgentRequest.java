package com.agentsflex.showcase.model;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 定义可由 Showcase 页面创建的完整 Agent 配置。
 *
 * <p>模型连接和运行级策略可以由页面提交，也可以从 application.yml/环境变量取得默认值；
 * API Key 仅用于后端构建模型，不会进入 Agent 回显。</p>
 */
public class CreateAgentRequest {

    /**
     * Agent 的显示名称，同时作为 {@code Agent.builder(name)} 的初始名称。
     * 用于在左侧 Agent 列表、运行详情、事件流和 Trace 中区分不同用途的 Agent，
     * 例如“市场研究助手”与“合同审查助手”。
     */
    @NotBlank
    @Size(max = 80)
    private String name = "市场研究助手";

    /**
     * Agent 定义的业务版本号，会随 Agent 元数据和每次运行一起展示。
     * 当提示词、工具集合或执行策略发生不兼容变化时，可递增此值，以便排查某次对话实际使用了哪套配置；
     * 它不是 Maven 版本，也不会自动选择历史 Agent 配置。
     */
    @NotBlank
    @Size(max = 32)
    private String version = "1";

    /**
     * 面向用户的 Agent 能力简介，只用于说明该 Agent 适合处理什么任务。
     * 该字段会展示在 Agent 定义信息中，但不会作为系统提示词发送给模型；
     * 真正约束模型行为的内容应写入 {@link #instructions}。
     */
    @NotBlank
    @Size(max = 300)
    private String description = "演示 Agents-Flex Agent Runtime 的完整研究任务";

    /**
     * 发送给模型的 Agent 系统指令，用于定义角色、工作步骤、工具使用规则和回答边界。
     * 适合放置必须长期生效的业务规则，例如资料不足时先请求表单、发布前必须审批；
     * 它会影响该 Agent 的每一轮对话，不应包含 API Key 等敏感连接信息。
     */
    @NotBlank
    @Size(max = 8000)
    private String instructions = "你是一个中文 AI 市场研究助手。先理解用户问题；涉及市场数据、行业趋势或已有研究结论时，优先调用 search_knowledge 检索本地知识库并引用片段来源；"
            + "信息不足时调用 request_user_input 请求 research_brief 表单；需要补充资料时调用 research_market，随后调用 verify_sources 校验来源；只有用户明确要求发布时才调用 publish_report。"
            + "收到 request_user_input 的 submitted 工具结果后，data 中的字段视为用户已经确认的信息；字段完整且与原任务一致时必须直接继续，"
            + "不得再次以自然语言重复索要相同信息。只有字段缺失或与原任务明确冲突时才向用户说明具体冲突并请求确认。"
            + "不得伪造工具结果，所有最终回答必须使用中文，清楚区分事实、判断与仍需确认的信息。";
    /**
     * OpenAI-compatible 模型服务商标识，例如 {@code deepseek} 或 {@code openai}。
     * 用于构建模型客户端并标注可观测性数据中的 provider；UI 未填写时继承服务端默认配置，
     * 适合在同一 Demo 中切换不同兼容服务商进行验证。
     */
    @Size(max = 80)
    private String modelProvider;

    /**
     * 模型服务的根地址，例如 {@code https://api.deepseek.com}。
     * 创建 Agent 时用于构建真实 HTTP 客户端；接入官方服务、代理网关或本地兼容服务时需要配置，
     * 未填写则继承 application.yml 或环境变量中的默认地址。
     */
    @Size(max = 2048)
    private String modelEndpoint;

    /**
     * 相对于 {@link #modelEndpoint} 的 Chat Completions 请求路径。
     * 当供应商或企业网关不是标准 {@code /v1/chat/completions} 路径时使用；
     * 未填写时沿用服务端默认值，通常无需修改。
     */
    @Size(max = 512)
    private String modelRequestPath;

    /**
     * 调用真实模型服务所需的 API Key。
     * UI 可将其保存在浏览器 localStorage，并在创建新 Agent 时提交；服务端接收后只保留在运行内存中，
     * 不会写入 Agent 定义回显、运行详情或模型公开状态，未填写时尝试使用服务端环境变量。
     */
    @Size(max = 4096)
    private String modelApiKey;

    /**
     * 供应商侧的模型 ID，例如 {@code deepseek-chat}。
     * 用于每次主对话和上下文压缩请求；切换不同能力、价格或上下文窗口的模型时设置，
     * 未填写则继承服务端默认模型。
     */
    @Size(max = 200)
    private String modelName;

    /**
     * 模型生成温度，控制回答的随机性和发散程度。
     * 市场研究、信息抽取等要求稳定的场景通常使用较低值，创意生成可适当提高；
     * {@code null} 表示继承服务端默认值。
     */
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Float modelTemperature;

    /**
     * 是否请求模型启用思考/推理模式。
     * 适用于支持 reasoning_content 等推理能力的模型；不支持该能力的供应商可能忽略或拒绝此参数，
     * {@code null} 表示继承服务端默认开关。
     */
    private Boolean modelThinkingEnabled;

    /**
     * 模型思考内容采用的供应商协议名称，用于正确解析和保留推理字段。
     * 仅在启用思考且供应商要求特定协议时配置，例如 DeepSeek 的对应协议；
     * 未填写时沿用服务端默认协议。
     */
    @Size(max = 80)
    private String modelThinkingProtocol;

    /**
     * 传给模型的随机种子，供应商支持时可提高多次请求结果的可复现性。
     * 适合回归测试或对比参数效果；它通常不能保证完全一致，空值表示不主动发送 seed。
     */
    @Size(max = 200)
    private String modelSeed;

    /**
     * 核采样阈值，只让累计概率位于 Top-P 范围内的候选 Token 参与生成。
     * 用于调节输出多样性，通常与温度择一重点调整；{@code null} 时不覆盖模型服务的默认值。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Float modelTopP;

    /**
     * Top-K 采样候选数量，将每一步生成限制在概率最高的 K 个 Token 中。
     * 仅对支持该参数的供应商生效，适合进一步限制输出发散程度；
     * {@code null} 时不向模型请求写入该参数。
     */
    @Min(1)
    @Max(1000000)
    private Integer modelTopK;

    /**
     * 单次模型响应最多允许生成的 Token 数，对应模型请求级的 {@code max_tokens}。
     * 用于控制单次回答长度和费用，它不同于整轮 Agent 的 {@link #maxOutputTokens} 预算；
     * {@code null} 时使用供应商默认上限。
     */
    @Min(1)
    @Max(1000000)
    private Integer modelMaxTokens;

    /**
     * 模型生成的停止词列表，命中任意字符串后供应商应停止继续生成。
     * 适用于固定格式、协议边界或防止模型生成后续章节；空列表或 {@code null} 表示不设置停止词。
     */
    @Size(max = 20)
    private List<@NotBlank @Size(max = 200) String> modelStop;

    /**
     * 是否要求模型响应携带 Token usage 数据。
     * 开启后可在头部统计、预算和 Trace 中展示更准确的输入/输出 Token；
     * {@code null} 表示继承服务端默认设置。
     */
    private Boolean modelIncludeUsage;

    /**
     * 主对话的响应格式约束。{@code NONE} 表示普通文本，{@code JSON_OBJECT} 要求模型返回 JSON 对象。
     * 适合需要结构化主回答的业务；逐消息压缩另有 JSON 数组协议，因此压缩请求不会继承此约束。
     */
    @NotBlank
    @Pattern(regexp = "NONE|JSON_OBJECT")
    private String modelResponseFormat = "NONE";

    /**
     * 是否启用模型 HTTP 请求层的重试。
     * 用于处理限流、临时网络错误或供应商短暂不可用；它位于 ChatModel 请求层，
     * 与重新推进整个 Agent Turn 的 {@link #maxRetries} 不是同一种重试。
     */
    private Boolean modelRetryEnabled;

    /**
     * 模型 HTTP 请求失败后的最大重试次数，仅在 {@link #modelRetryEnabled} 开启时生效。
     * 适合根据供应商限流策略控制网络请求恢复次数；{@code 0} 表示失败后不追加重试。
     */
    @Min(0)
    @Max(20)
    private Integer modelRetryCount;

    /**
     * 模型 HTTP 请求第一次重试前的等待时间，单位毫秒。
     * 用于避免瞬时故障或限流时立即连续请求；{@code 0} 表示不等待，后续退避由模型客户端实现。
     */
    @Min(0)
    @Max(300000)
    private Integer modelRetryInitialDelayMillis;

    /**
     * 单个 Agent Turn 最多允许调用模型的迭代次数。
     * 一次“模型提出工具调用、执行工具、再调用模型”通常会消耗新的迭代，
     * 用于阻止模型与工具之间无限循环。
     */
    @Min(1)
    @Max(100)
    private int maxIterations = 8;

    /**
     * 单个 Agent Turn 最多允许推进的运行状态 Step 数。
     * Step 覆盖模型调用、工具处理、挂起与恢复等更细粒度阶段，适合作为运行状态机的总保险丝；
     * 它与只统计模型循环的 {@link #maxIterations} 配合生效。
     */
    @Min(1)
    @Max(500)
    private int maxSteps = 32;

    /**
     * 构造新一轮模型上下文时最多附加的最近历史 Turn 数。
     * 用于长对话中优先保留最近几轮，避免无边界地发送全部会话；
     * Agents-Flex 会按完整 Turn 裁剪，避免只留下半个工具交互。
     */
    @Min(1)
    @Max(100)
    private int maxAttachedTurns = 6;

    /**
     * 模型上下文允许附加的历史 Token 上限，0 表示不启用该限制。
     * 适合模型上下文窗口较小或需要严格控制请求体成本的场景；
     * Agents-Flex 会使用 Showcase 的估算器并按完整 Turn 裁剪上下文。
     */
    @Min(0)
    @Max(10000000)
    private long maxAttachedTokens;

    /**
     * 构造模型上下文时最多附加的历史消息条数。
     * 用于限制包含用户消息、AI 消息和工具消息在内的上下文规模；
     * 它会与 Turn、Token 两种挂载上限共同约束，达到任一边界都会减少旧历史。
     */
    @Min(1)
    @Max(500)
    private int maxAttachedMessages = 40;

    /**
     * 单个 Agent Turn 可累计消耗的输入 Token 预算，{@code 0} 表示不启用此项预算。
     * 多次模型迭代的输入会累计计算，适合控制长上下文和反复工具调用造成的输入成本。
     */
    @Min(0)
    @Max(1000000)
    private long maxInputTokens = 4000;

    /**
     * 单个 Agent Turn 可累计生成的输出 Token 预算，{@code 0} 表示不启用此项预算。
     * 它统计整个 Turn 内多次模型调用的输出总量，适合限制答案和中间推理的总体成本。
     */
    @Min(0)
    @Max(1000000)
    private long maxOutputTokens = 4000;

    /**
     * 单个 Agent Turn 输入与输出 Token 的合计预算，{@code 0} 表示不启用此项预算。
     * 当只关心一次任务的总体消耗而不区分输入、输出时使用；会与两项独立预算同时检查。
     */
    @Min(0)
    @Max(1000000)
    private long maxTotalTokens = 4000;

    /**
     * 单个 Agent Turn 允许执行的工具调用总数，{@code 0} 表示不启用此项预算。
     * 用于防止模型重复检索、重复提交或形成工具调用循环；并行调用中的每个工具都会分别计数。
     */
    @Min(0)
    @Max(1000)
    private int maxToolCalls = 8;

    /**
     * 单个 Agent Turn 从创建到结束允许持续的最长时间，单位毫秒，{@code 0} 表示不限制总时长。
     * 这是覆盖模型、工具、审批和用户输入等待的整体预算，不等同于各阶段独立超时。
     */
    @Min(0)
    @Max(86400000)
    private long maxDurationMillis = 1800000;

    /**
     * Agent 运行级失败后的最大重试次数。
     * 用于 AgentWorker 根据重试策略重新推进可恢复的 Turn 失败，例如 Demo 中不稳定工具抛出的异常；
     * 它不同于只重发 HTTP 请求的 {@link #modelRetryCount}。
     */
    @Min(0)
    @Max(10)
    private int maxRetries = 2;

    /**
     * Agent 运行级第一次重试前的等待时间，单位毫秒。
     * 适合给临时依赖故障留出恢复窗口；{@code 0} 表示立即进行第一次重试。
     */
    @Min(0)
    @Max(60000)
    private long initialDelayMillis = 0;

    /**
     * Agent 运行级指数退避允许达到的最大等待时间，单位毫秒。
     * 用于限制后续重试间隔，避免指数增长后等待过久；该值必须大于或等于 {@link #initialDelayMillis}，
     * {@code 0} 仅能与初始延迟 0 配合使用，此时每次重试都不等待。
     */
    @Min(0)
    @Max(300000)
    private long maxDelayMillis = 0;

    /**
     * Agent 运行级重试延迟的指数增长倍数。
     * 例如初始延迟 500ms、倍数 2 时，后续间隔可按 500ms、1000ms、2000ms 增长，
     * 并受 {@link #maxDelayMillis} 限制。
     */
    @DecimalMin("1.0")
    @DecimalMax("10.0")
    private double retryMultiplier = 2.0d;

    /**
     * 决定何时触发上下文压缩的策略。
     * {@code PENDING_MESSAGES}/{@code PENDING_TURNS}/{@code PENDING_TOKENS} 分别按待压缩消息、Turn、估算 Token 触发，
     * {@code ALWAYS} 用于演示或测试每轮压缩，{@code NEVER} 用于完全关闭自动压缩。
     */
    @NotBlank
    @Pattern(regexp = "PENDING_MESSAGES|PENDING_TURNS|PENDING_TOKENS|ALWAYS|NEVER")
    private String compressionDecider = "PENDING_MESSAGES";

    /**
     * 待压缩消息数量达到该值时触发压缩，仅在 {@link #compressionDecider} 为 {@code PENDING_MESSAGES} 时生效。
     * 适合消息颗粒度稳定、希望按会话长度控制压缩频率的场景。
     */
    @Min(0)
    @Max(1000000)
    private int compressionMessageThreshold = 20;

    /**
     * 待压缩完整 Turn 数达到该值时触发压缩，仅在 {@link #compressionDecider} 为 {@code PENDING_TURNS} 时生效。
     * 适合工具消息较多但希望按用户对话轮次衡量历史长度的场景。
     */
    @Min(0)
    @Max(1000000)
    private int compressionTurnThreshold = 4;

    /**
     * 待压缩历史的估算 Token 达到该值时触发压缩，仅在 {@link #compressionDecider} 为 {@code PENDING_TOKENS} 时生效。
     * 适合依据模型上下文窗口或调用成本精确控制压缩时机。
     */
    @Min(0)
    @Max(10000000)
    private long compressionTokenThreshold = 4000;

    /**
     * 上下文压缩器的工作方式。
     * {@code WHOLE_HISTORY} 将较早历史整体总结成一段摘要，适合保留跨消息关系；
     * {@code PER_MESSAGE} 逐条生成摘要并保留消息角色和 ID，适合需要追踪单条来源的场景。
     */
    @NotBlank
    @Pattern(regexp = "WHOLE_HISTORY|PER_MESSAGE")
    private String compressionMode = "WHOLE_HISTORY";

    /**
     * 工具执行抛出异常且重试后仍未恢复时的处理策略。
     * {@code FAIL_RUN} 直接让当前运行失败；{@code RETURN_ERROR_TO_MODEL} 将错误作为工具结果交回模型，
     * 适合让模型解释失败、调整参数或选择其他工具继续任务。
     */
    @NotBlank
    @Pattern(regexp = "FAIL_RUN|RETURN_ERROR_TO_MODEL")
    private String toolErrorStrategy = "FAIL_RUN";

    /**
     * 工具调用已开始但未正常完成时，补入模型上下文的工具消息模板。
     * 例如进程恢复或协作式中断后，用它告知模型上次工具没有产出可信结果；
     * 模板中的 {@code {reason}} 会替换为实际中断原因。
     */
    @NotBlank
    @Size(max = 1000)
    private String interruptedToolMessageTemplate = "Tool call was not completed: {reason}";

    /**
     * 上一个 Agent Turn 未正常结束时，注入后续上下文的提示模板。
     * 用于取消、超时或恢复场景，避免模型误认为上一轮已经完成；
     * 模板中的 {@code {reason}} 会替换为实际原因。
     */
    @NotBlank
    @Size(max = 1000)
    private String interruptedTurnMessageTemplate = "The previous AgentTurn ended before completion: {reason}";

    /**
     * 调用方取消当前 Turn 时记录和传播的默认原因文本。
     * 该值会进入取消状态、事件或后续中断说明，适合提供业务可读的取消原因，
     * 但它本身不会主动触发取消。
     */
    @NotBlank
    @Size(max = 1000)
    private String cancellationReason = "turn cancelled by caller";

    /**
     * 单次模型调用的最长执行时间，单位毫秒，{@code 0} 表示不设置该阶段超时。
     * 用于防止供应商连接或流式响应长时间无结果；超时后由运行失败和重试策略继续处理。
     */
    @Min(0)
    @Max(86400000)
    private long modelCallTimeoutMillis;

    /**
     * Runtime 本地工具执行的最长时间，单位毫秒，{@code 0} 表示不设置该阶段超时。
     * 适合限制检索、计算等普通工具长期占用工作线程，不影响等待外部工具回传的独立超时。
     */
    @Min(0)
    @Max(86400000)
    private long toolExecutionTimeoutMillis;

    /**
     * 外部异步工具从发起到等待结果的最长时间，单位毫秒，{@code 0} 表示不设置该阶段超时。
     * 适用于工具由其他进程或人工系统执行、需要稍后通过关联 ID 恢复 Turn 的场景。
     */
    @Min(0)
    @Max(86400000)
    private long externalToolTimeoutMillis;

    /**
     * 工具审批挂起后等待用户允许或拒绝的最长时间，单位毫秒，{@code 0} 表示不设置审批期限。
     * 适用于发布、删除、付款等有副作用工具，超时后的行为由 {@link #suspensionExpirationStrategy} 决定。
     */
    @Min(0)
    @Max(86400000)
    private long approvalTimeoutMillis;

    /**
     * Agent 请求用户补充表单或文本后等待提交的最长时间，单位毫秒，{@code 0} 表示不设置输入期限。
     * 适用于 research_brief 等人机协作表单，过期处理同样由挂起过期策略控制。
     */
    @Min(0)
    @Max(86400000)
    private long userInputTimeoutMillis;

    /**
     * 审批、用户输入或外部工具等 Suspension 过期后的处理方式。
     * {@code REJECT_RESUME} 按拒绝结果恢复流程，{@code FAIL_TURN} 将本轮标记失败，
     * {@code CANCEL_TURN} 则取消本轮；用于按业务风险决定超时后的默认结果。
     */
    @NotBlank
    @Pattern(regexp = "REJECT_RESUME|FAIL_TURN|CANCEL_TURN")
    private String suspensionExpirationStrategy = "REJECT_RESUME";

    /**
     * 普通本地工具结果允许写回模型上下文的最大字符数，{@code 0} 表示不限制。
     * 适合控制网页正文、检索结果等超长输出，避免单次工具结果挤占模型上下文窗口。
     */
    @Min(0)
    @Max(10000000)
    private long toolResultMaxCharacters;

    /**
     * 外部异步工具结果允许写回模型上下文的最大字符数，{@code 0} 表示不限制。
     * 该限制与本地工具分开，便于对第三方系统返回的大型报告或文件解析结果设置不同边界。
     */
    @Min(0)
    @Max(10000000)
    private long externalToolResultMaxCharacters;

    /**
     * 工具结果超过字符上限时的处理策略。
     * {@code FAIL} 拒绝超限结果并暴露配置问题，适合要求数据完整的任务；
     * {@code TRUNCATE} 截断后继续，适合允许只读取前部摘要的检索场景。
     */
    @NotBlank
    @Pattern(regexp = "FAIL|TRUNCATE")
    private String toolResultOverflowStrategy = "FAIL";

    /**
     * 同一次模型响应包含多个工具调用时的执行方式。
     * {@code SEQUENTIAL} 按顺序执行，适合有依赖或副作用的工具；
     * {@code PARALLEL} 并发执行互不依赖的检索或计算工具，以缩短整体等待时间。
     */
    @NotBlank
    @Pattern(regexp = "SEQUENTIAL|PARALLEL")
    private String toolExecutionMode = "SEQUENTIAL";

    /**
     * 并行工具模式下同一批次最多同时执行的工具数。
     * 用于保护线程池和下游服务，只在 {@link #toolExecutionMode} 为 {@code PARALLEL} 时真正限制并发度。
     */
    @Min(1)
    @Max(128)
    private int maxParallelToolCalls = 8;

    /**
     * 一批并行工具中某个调用失败时的处理策略。
     * {@code FAIL_FAST} 尽快终止该批次并让运行失败；{@code RETURN_ERRORS_TO_MODEL} 汇总成功结果和错误交给模型，
     * 适合允许部分检索源不可用的容错任务。
     */
    @NotBlank
    @Pattern(regexp = "FAIL_FAST|RETURN_ERRORS_TO_MODEL")
    private String parallelFailureStrategy = "FAIL_FAST";

    /**
     * 压缩前是否把已完成的“模型工具调用 + 工具结果”折叠为更紧凑的历史表示。
     * 开启可显著减少工具密集型对话的上下文体积；若业务必须保留每个工具协议细节，可关闭此项。
     */
    private boolean compactCompletedToolTurns = true;

    /**
     * 执行上下文压缩时始终原样保留的最近完整 Turn 数。
     * 用于让模型看到最新问题、工具参数和回答原文，只有更早的历史会交给压缩器；
     * {@code 0} 表示不专门保留最近 Turn。
     */
    @Min(0)
    @Max(100)
    private int compressionKeepRecentTurns = 2;

    /**
     * 压缩模型调用或摘要解析失败时的降级策略。
     * {@code FAIL} 让当前流程失败以避免上下文不确定；{@code USE_ORIGINAL} 放弃本次压缩并继续使用原历史，
     * 适合可用性优先且原上下文仍未超过模型硬限制的场景。
     */
    @NotBlank
    @Pattern(regexp = "FAIL|USE_ORIGINAL")
    private String compressionFailureStrategy = "FAIL";

    /**
     * 发送给压缩模型的摘要要求，说明哪些事实必须保留、哪些内容可以省略。
     * 适合写入领域关键约束，例如保留用户选择、审批结论、引用来源和未完成事项；
     * 同时用于整段历史和逐消息压缩模式。
     */
    @NotBlank
    @Size(max = 4000)
    private String compressionInstruction = "保留研究事实、用户约束、审批结果和未完成事项。";

    /**
     * 整段历史压缩请求中，放在待摘要历史正文之前的分隔标题。
     * 用于清晰区分压缩指令与原始历史，只有 {@link #compressionMode} 为 {@code WHOLE_HISTORY} 时使用。
     */
    @NotBlank
    @Size(max = 1000)
    private String compressionHistoryHeader = "\n\n历史消息：\n";

    /**
     * 将整段摘要重新注入后续模型上下文时添加的前缀。
     * 用于明确告诉主模型这部分是较早会话的压缩事实，而不是当前用户的新输入；
     * 主要服务于 {@code WHOLE_HISTORY} 模式。
     */
    @NotBlank
    @Size(max = 1000)
    private String compressionSummaryPrefix = "以下是较早对话的摘要，请将其作为历史事实参考：";

    /**
     * 逐消息压缩时追加到模型请求中的输出协议说明。
     * 必须要求模型返回包含 {@code messageId} 和 {@code summary} 的 JSON 数组，
     * 以便 Runtime 将摘要准确映射回原消息；仅在 {@link #compressionMode} 为 {@code PER_MESSAGE} 时使用。
     */
    @NotBlank
    @Size(max = 4000)
    private String compressionPerMessageRequest =
            "\n请逐条摘要以下消息，并仅返回 JSON 数组，每项包含 messageId 和 summary：\n";

    /**
     * 单次压缩模型调用允许执行的最长时间，单位毫秒，{@code 0} 表示不设置独立超时。
     * 适合让摘要任务使用比主模型调用更短的期限，避免后台压缩长时间阻塞正常对话。
     */
    @Min(0)
    @Max(86400000)
    private long compressionModelCallTimeoutMillis;

    /**
     * 一次压缩请求允许送入压缩模型的最大字符数，{@code 0} 表示不设置字符限制。
     * 用于在 Token 估算之外保护请求体大小；历史超大时可提前阻止不可控的压缩调用。
     */
    @Min(0)
    @Max(10000000)
    private long compressionMaxInputCharacters;

    /**
     * 压缩模型摘要结果允许保留的最大字符数，{@code 0} 表示不设置字符限制。
     * 用于防止“摘要”本身仍然过长而无法有效释放上下文空间，整段和逐消息压缩均会使用该边界。
     */
    @Min(0)
    @Max(10000000)
    private long compressionMaxOutputCharacters;

    /**
     * 知识库 embedding 服务根地址（OpenAI 兼容，含 /v1），例如 http://127.0.0.1:18888/v1。
     * 创建 Agent 时与聊天模型一起提交，用于初始化 RAG 知识库；留空时继承服务端配置。
     */
    @Size(max = 2048)
    private String embeddingEndpoint;

    /**
     * 知识库 embedding 服务密钥；只在后端内存使用，不会进入任何回显视图。
     * 本地 Ollama 等免鉴权服务可留空。
     */
    @Size(max = 4096)
    private String embeddingApiKey;

    /**
     * 知识库 embedding 模型名，例如 bge-m3；留空时继承服务端默认值。
     */
    @Size(max = 200)
    private String embeddingModel;

    /**
     * 知识库检索模式：HYBRID（向量+BM25 混合）、VECTOR_ONLY（纯向量）、KEYWORD_ONLY（纯关键词）。
     */
    @Pattern(regexp = "HYBRID|VECTOR_ONLY|KEYWORD_ONLY")
    private String knowledgeSearchMode;

    /**
     * @return 知识库 embedding 服务根地址
     */
    public String getEmbeddingEndpoint() {
        return embeddingEndpoint;
    }

    /**
     * @param value embedding 服务根地址
     */
    public void setEmbeddingEndpoint(String value) {
        this.embeddingEndpoint = value;
    }

    /**
     * @return 知识库 embedding 服务密钥
     */
    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    /**
     * @param value embedding 服务密钥；不回显
     */
    public void setEmbeddingApiKey(String value) {
        this.embeddingApiKey = value;
    }

    /**
     * @return 知识库 embedding 模型名
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * @param value embedding 模型名
     */
    public void setEmbeddingModel(String value) {
        this.embeddingModel = value;
    }

    /**
     * @return 知识库检索模式
     */
    public String getKnowledgeSearchMode() {
        return knowledgeSearchMode;
    }

    /**
     * @param value 知识库检索模式
     */
    public void setKnowledgeSearchMode(String value) {
        this.knowledgeSearchMode = value;
    }

    /**
     * @return UI 选择的模型服务商；为空时使用服务端默认值
     */
    public String getModelProvider() {
        return modelProvider;
    }

    /**
     * @param modelProvider OpenAI-compatible 服务商标识
     */
    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * @return UI 配置的模型服务根地址
     */
    public String getModelEndpoint() {
        return modelEndpoint;
    }

    /**
     * @param modelEndpoint OpenAI-compatible 服务根地址
     */
    public void setModelEndpoint(String modelEndpoint) {
        this.modelEndpoint = modelEndpoint;
    }

    /**
     * @return UI 配置的 Chat Completions 请求路径
     */
    public String getModelRequestPath() {
        return modelRequestPath;
    }

    /**
     * @param modelRequestPath Chat Completions 请求路径
     */
    public void setModelRequestPath(String modelRequestPath) {
        this.modelRequestPath = modelRequestPath;
    }

    /**
     * @return 模型 API Key，仅用于本次 Agent 创建，不会写入返回视图
     */
    public String getModelApiKey() {
        return modelApiKey;
    }

    /**
     * @param modelApiKey 模型服务密钥
     */
    public void setModelApiKey(String modelApiKey) {
        this.modelApiKey = modelApiKey;
    }

    /**
     * @return UI 配置的模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * @param modelName 服务商支持的模型 ID
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * @return 请求温度
     */
    public Float getModelTemperature() {
        return modelTemperature;
    }

    /**
     * @param modelTemperature 生成温度
     */
    public void setModelTemperature(Float modelTemperature) {
        this.modelTemperature = modelTemperature;
    }

    /**
     * @return 是否启用思考模式
     */
    public Boolean getModelThinkingEnabled() {
        return modelThinkingEnabled;
    }

    /**
     * @param modelThinkingEnabled 是否启用思考模式
     */
    public void setModelThinkingEnabled(Boolean modelThinkingEnabled) {
        this.modelThinkingEnabled = modelThinkingEnabled;
    }

    /**
     * @return 思考协议名称
     */
    public String getModelThinkingProtocol() {
        return modelThinkingProtocol;
    }

    /**
     * @param modelThinkingProtocol 思考协议名称
     */
    public void setModelThinkingProtocol(String modelThinkingProtocol) {
        this.modelThinkingProtocol = modelThinkingProtocol;
    }

    /**
     * @return Agent 在界面、事件与 Trace 中展示的名称
     */
    public String getName() {
        return name;
    }

    /**
     * @param name 长度不超过 80 的 Agent 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return Agent 定义版本，用于区分配置演进
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version 长度不超过 32 的版本标识
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return 面向使用者的 Agent 能力说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description 长度不超过 300 的能力说明
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return 直接传给 Agents-Flex Agent Builder 的系统指令
     */
    public String getInstructions() {
        return instructions;
    }

    /**
     * @param instructions 非空的 System Instructions，最多 8000 字符
     */
    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    /**
     * @return 单个 Turn 允许的最大模型迭代次数
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * @param maxIterations 1 到 100 的迭代上限
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /**
     * @return 单个 Turn 允许推进的最大 Step 数
     */
    public int getMaxSteps() {
        return maxSteps;
    }

    /**
     * @param maxSteps 1 到 500 的 Step 上限
     */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * @return 构造模型上下文时最多挂载的历史 Turn 数
     */
    public int getMaxAttachedTurns() {
        return maxAttachedTurns;
    }

    /**
     * @param maxAttachedTurns 1 到 100 的历史 Turn 上限
     */
    public void setMaxAttachedTurns(int maxAttachedTurns) {
        this.maxAttachedTurns = maxAttachedTurns;
    }

    /**
     * @return 模型上下文历史 Token 上限，0 表示不限制
     */
    public long getMaxAttachedTokens() {
        return maxAttachedTokens;
    }

    /**
     * @param maxAttachedTokens 大于等于 0 的上下文 Token 上限
     */
    public void setMaxAttachedTokens(long maxAttachedTokens) {
        this.maxAttachedTokens = maxAttachedTokens;
    }

    /**
     * @return 构造模型上下文时最多挂载的历史消息数
     */
    public int getMaxAttachedMessages() {
        return maxAttachedMessages;
    }

    /**
     * @param maxAttachedMessages 1 到 500 的历史消息上限
     */
    public void setMaxAttachedMessages(int maxAttachedMessages) {
        this.maxAttachedMessages = maxAttachedMessages;
    }

    /**
     * @return 单个 Turn 允许累计消耗的输入 Token 上限
     */
    public long getMaxInputTokens() {
        return maxInputTokens;
    }

    /**
     * @param maxInputTokens 100 到 1000000 的输入 Token 上限
     */
    public void setMaxInputTokens(long maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    /**
     * @return 单个 Turn 允许累计生成的输出 Token 上限
     */
    public long getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * @param maxOutputTokens 100 到 1000000 的输出 Token 上限
     */
    public void setMaxOutputTokens(long maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * @return 输入与输出合计 Token 上限
     */
    public long getMaxTotalTokens() {
        return maxTotalTokens;
    }

    /**
     * @param maxTotalTokens 500 到 1000000 的总 Token 上限
     */
    public void setMaxTotalTokens(long maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    /**
     * @return 单个 Turn 允许执行的最大工具调用次数
     */
    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    /**
     * @param maxToolCalls 1 到 1000 的工具调用上限
     */
    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    /**
     * @return 包含人工等待时间在内的墙钟执行时长上限
     */
    public long getMaxDurationMillis() {
        return maxDurationMillis;
    }

    /**
     * @param maxDurationMillis 1000 到 86400000 毫秒的执行时长上限
     */
    public void setMaxDurationMillis(long maxDurationMillis) {
        this.maxDurationMillis = maxDurationMillis;
    }

    /**
     * @return 单个可重试操作的最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * @param maxRetries 0 到 10 的最大重试次数，0 表示不重试
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * @return 第一次重试前的等待毫秒数
     */
    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    /**
     * @param initialDelayMillis 0 到 60000 毫秒的初始重试间隔
     */
    public void setInitialDelayMillis(long initialDelayMillis) {
        this.initialDelayMillis = initialDelayMillis;
    }

    /**
     * @return 指数退避允许达到的最大等待毫秒数
     */
    public long getMaxDelayMillis() {
        return maxDelayMillis;
    }

    /**
     * @param maxDelayMillis 0 到 300000 毫秒的最大重试间隔
     */
    public void setMaxDelayMillis(long maxDelayMillis) {
        this.maxDelayMillis = maxDelayMillis;
    }

    /**
     * @return 每次重试间隔使用的指数退避倍率
     */
    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    /**
     * @param retryMultiplier 1.0 到 10.0 的退避倍率
     */
    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    /**
     * @return 触发增量上下文压缩所需的待处理消息数
     */
    public int getCompressionMessageThreshold() {
        return compressionMessageThreshold;
    }

    /**
     * @param compressionMessageThreshold 2 到 500 的消息阈值
     */
    public void setCompressionMessageThreshold(int compressionMessageThreshold) {
        this.compressionMessageThreshold = compressionMessageThreshold;
    }

    /**
     * @return 工具最终失败时采用的 Agents-Flex 原生处理策略
     */
    public String getToolErrorStrategy() {
        return toolErrorStrategy;
    }

    /**
     * @param toolErrorStrategy FAIL_RUN 或 RETURN_ERROR_TO_MODEL
     */
    public void setToolErrorStrategy(String toolErrorStrategy) {
        this.toolErrorStrategy = toolErrorStrategy;
    }

    /**
     * @return 模型采样随机种子；为空时使用供应商默认行为
     */
    public String getModelSeed() {
        return modelSeed;
    }

    /**
     * @param modelSeed 发送给模型的可选随机种子
     */
    public void setModelSeed(String modelSeed) {
        this.modelSeed = modelSeed;
    }

    /**
     * @return 核采样累计概率上限
     */
    public Float getModelTopP() {
        return modelTopP;
    }

    /**
     * @param modelTopP 0 到 1 的 Top P 参数
     */
    public void setModelTopP(Float modelTopP) {
        this.modelTopP = modelTopP;
    }

    /**
     * @return 候选 Token 数量上限
     */
    public Integer getModelTopK() {
        return modelTopK;
    }

    /**
     * @param modelTopK 大于 0 的 Top K 参数
     */
    public void setModelTopK(Integer modelTopK) {
        this.modelTopK = modelTopK;
    }

    /**
     * @return 单次模型响应的最大输出 Token 数
     */
    public Integer getModelMaxTokens() {
        return modelMaxTokens;
    }

    /**
     * @param modelMaxTokens 大于 0 的单次输出 Token 上限
     */
    public void setModelMaxTokens(Integer modelMaxTokens) {
        this.modelMaxTokens = modelMaxTokens;
    }

    /**
     * @return 命中任一值时停止生成的序列
     */
    public List<String> getModelStop() {
        return modelStop;
    }

    /**
     * @param modelStop 发送给模型的停止序列列表
     */
    public void setModelStop(List<String> modelStop) {
        this.modelStop = modelStop;
    }

    /**
     * @return 流式响应是否要求返回 Usage 统计
     */
    public Boolean getModelIncludeUsage() {
        return modelIncludeUsage;
    }

    /**
     * @param modelIncludeUsage 是否在流式请求中附带 Usage
     */
    public void setModelIncludeUsage(Boolean modelIncludeUsage) {
        this.modelIncludeUsage = modelIncludeUsage;
    }

    /**
     * @return NONE 或 JSON_OBJECT 响应格式策略
     */
    public String getModelResponseFormat() {
        return modelResponseFormat;
    }

    /**
     * @param modelResponseFormat 模型默认格式或 JSON Object 格式
     */
    public void setModelResponseFormat(String modelResponseFormat) {
        this.modelResponseFormat = modelResponseFormat;
    }

    /**
     * @return 未完成工具调用写入模型上下文时使用的消息模板
     */
    public String getInterruptedToolMessageTemplate() {
        return interruptedToolMessageTemplate;
    }

    /**
     * @param value 支持 reason、turnId、toolCallId 和 toolName 占位符的模板
     */
    public void setInterruptedToolMessageTemplate(String value) {
        this.interruptedToolMessageTemplate = value;
    }

    /**
     * @return Turn 异常结束时追加的助手消息模板
     */
    public String getInterruptedTurnMessageTemplate() {
        return interruptedTurnMessageTemplate;
    }

    /**
     * @param value 支持 reason 和 turnId 占位符的 Turn 中断模板
     */
    public void setInterruptedTurnMessageTemplate(String value) {
        this.interruptedTurnMessageTemplate = value;
    }

    /**
     * @return 主动取消 Turn 时写入收束消息的原因
     */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /**
     * @param value 注入中断消息模板 reason 占位符的取消原因
     */
    public void setCancellationReason(String value) {
        this.cancellationReason = value;
    }

    /**
     * @return 单次模型调用超时毫秒数，0 表示不限制
     */
    public long getModelCallTimeoutMillis() {
        return modelCallTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的模型调用超时毫秒数
     */
    public void setModelCallTimeoutMillis(long value) {
        this.modelCallTimeoutMillis = value;
    }

    /**
     * @return 单次本地工具执行超时毫秒数，0 表示不限制
     */
    public long getToolExecutionTimeoutMillis() {
        return toolExecutionTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的本地工具超时毫秒数
     */
    public void setToolExecutionTimeoutMillis(long value) {
        this.toolExecutionTimeoutMillis = value;
    }

    /**
     * @return 外部工具结果等待超时毫秒数，0 表示不限制
     */
    public long getExternalToolTimeoutMillis() {
        return externalToolTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的外部工具等待超时
     */
    public void setExternalToolTimeoutMillis(long value) {
        this.externalToolTimeoutMillis = value;
    }

    /**
     * @return 人工审批最长等待毫秒数，0 表示不限制
     */
    public long getApprovalTimeoutMillis() {
        return approvalTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的审批等待超时
     */
    public void setApprovalTimeoutMillis(long value) {
        this.approvalTimeoutMillis = value;
    }

    /**
     * @return 用户表单输入最长等待毫秒数，0 表示不限制
     */
    public long getUserInputTimeoutMillis() {
        return userInputTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的用户输入等待超时
     */
    public void setUserInputTimeoutMillis(long value) {
        this.userInputTimeoutMillis = value;
    }

    /**
     * @return 挂起过期后的恢复处理策略
     */
    public String getSuspensionExpirationStrategy() {
        return suspensionExpirationStrategy;
    }

    /**
     * @param value REJECT_RESUME、FAIL_TURN 或 CANCEL_TURN
     */
    public void setSuspensionExpirationStrategy(String value) {
        this.suspensionExpirationStrategy = value;
    }

    /**
     * @return 本地工具结果允许写入上下文的字符上限
     */
    public long getToolResultMaxCharacters() {
        return toolResultMaxCharacters;
    }

    /**
     * @param value 大于等于 0 的本地工具结果字符上限，0 表示不限
     */
    public void setToolResultMaxCharacters(long value) {
        this.toolResultMaxCharacters = value;
    }

    /**
     * @return 外部工具结果允许写入上下文的字符上限
     */
    public long getExternalToolResultMaxCharacters() {
        return externalToolResultMaxCharacters;
    }

    /**
     * @param value 大于等于 0 的外部工具结果字符上限，0 表示不限
     */
    public void setExternalToolResultMaxCharacters(long value) {
        this.externalToolResultMaxCharacters = value;
    }

    /**
     * @return 工具结果超过字符上限后的处理策略
     */
    public String getToolResultOverflowStrategy() {
        return toolResultOverflowStrategy;
    }

    /**
     * @param value FAIL 表示失败，TRUNCATE 表示带标记截断
     */
    public void setToolResultOverflowStrategy(String value) {
        this.toolResultOverflowStrategy = value;
    }

    /**
     * @return 同一模型回合内本地工具调用的执行模式
     */
    public String getToolExecutionMode() {
        return toolExecutionMode;
    }

    /**
     * @param value SEQUENTIAL 顺序执行或 PARALLEL 并行执行
     */
    public void setToolExecutionMode(String value) {
        this.toolExecutionMode = value;
    }

    /**
     * @return 并行工具批次允许同时启动的最大数量
     */
    public int getMaxParallelToolCalls() {
        return maxParallelToolCalls;
    }

    /**
     * @param value 1 到 128 的并行工具调用上限
     */
    public void setMaxParallelToolCalls(int value) {
        this.maxParallelToolCalls = value;
    }

    /**
     * @return 并行批次中任一工具失败后的处理策略
     */
    public String getParallelFailureStrategy() {
        return parallelFailureStrategy;
    }

    /**
     * @param value FAIL_FAST 或 RETURN_ERRORS_TO_MODEL
     */
    public void setParallelFailureStrategy(String value) {
        this.parallelFailureStrategy = value;
    }

    /**
     * @return 压缩模型上下文前是否归并已完成的工具 Turn
     */
    public boolean isCompactCompletedToolTurns() {
        return compactCompletedToolTurns;
    }

    /**
     * @param value 是否归并已完成的工具调用与结果消息
     */
    public void setCompactCompletedToolTurns(boolean value) {
        this.compactCompletedToolTurns = value;
    }

    /**
     * @return 压缩后仍完整保留的最近 Turn 数量
     */
    public int getCompressionKeepRecentTurns() {
        return compressionKeepRecentTurns;
    }

    /**
     * @param value 大于等于 0 的最近 Turn 保留数量
     */
    public void setCompressionKeepRecentTurns(int value) {
        this.compressionKeepRecentTurns = value;
    }

    /**
     * @return 压缩器执行失败后的上下文处理策略
     */
    public String getCompressionFailureStrategy() {
        return compressionFailureStrategy;
    }

    /**
     * @param value FAIL 表示终止，USE_ORIGINAL 表示继续使用原始上下文
     */
    public void setCompressionFailureStrategy(String value) {
        this.compressionFailureStrategy = value;
    }

    /**
     * @return 指导压缩模型保留关键信息的系统化摘要要求
     */
    public String getCompressionInstruction() {
        return compressionInstruction;
    }

    /**
     * @param value 长度不超过 4000 的压缩模型摘要要求
     */
    public void setCompressionInstruction(String value) {
        this.compressionInstruction = value;
    }

    /**
     * @return 拼接在摘要要求与历史消息之间的标题文本
     */
    public String getCompressionHistoryHeader() {
        return compressionHistoryHeader;
    }

    /**
     * @param value 长度不超过 1000 的历史消息标题文本
     */
    public void setCompressionHistoryHeader(String value) {
        this.compressionHistoryHeader = value;
    }

    /**
     * @return 压缩摘要写回业务模型上下文时使用的提示前缀
     */
    public String getCompressionSummaryPrefix() {
        return compressionSummaryPrefix;
    }

    /**
     * @param value 长度不超过 1000 的摘要上下文提示前缀
     */
    public void setCompressionSummaryPrefix(String value) {
        this.compressionSummaryPrefix = value;
    }

    /**
     * @return 摘要模型单次调用超时毫秒数，0 表示不限制
     */
    public long getCompressionModelCallTimeoutMillis() {
        return compressionModelCallTimeoutMillis;
    }

    /**
     * @param value 大于等于 0 的摘要模型调用超时
     */
    public void setCompressionModelCallTimeoutMillis(long value) {
        this.compressionModelCallTimeoutMillis = value;
    }

    /**
     * @return 发送给摘要模型的请求字符上限，0 表示不限制
     */
    public long getCompressionMaxInputCharacters() {
        return compressionMaxInputCharacters;
    }

    /**
     * @param value 大于等于 0 的压缩输入字符上限
     */
    public void setCompressionMaxInputCharacters(long value) {
        this.compressionMaxInputCharacters = value;
    }

    /**
     * @return 摘要模型返回正文的字符上限，0 表示不限制
     */
    public long getCompressionMaxOutputCharacters() {
        return compressionMaxOutputCharacters;
    }

    /**
     * @param value 大于等于 0 的压缩输出字符上限
     */
    public void setCompressionMaxOutputCharacters(long value) {
        this.compressionMaxOutputCharacters = value;
    }

    /**
     * @return 是否启用 ChatOptions/OpenAI 客户端请求级重试
     */
    public Boolean getModelRetryEnabled() {
        return modelRetryEnabled;
    }

    /**
     * @param value 是否在一次模型调用内部重试网络类错误
     */
    public void setModelRetryEnabled(Boolean value) {
        this.modelRetryEnabled = value;
    }

    /**
     * @return 单次模型请求内部允许的重试次数
     */
    public Integer getModelRetryCount() {
        return modelRetryCount;
    }

    /**
     * @param value 0 到 20 的模型请求重试次数
     */
    public void setModelRetryCount(Integer value) {
        this.modelRetryCount = value;
    }

    /**
     * @return 模型请求内部首次重试前的等待毫秒数
     */
    public Integer getModelRetryInitialDelayMillis() {
        return modelRetryInitialDelayMillis;
    }

    /**
     * @param value 0 到 300000 毫秒的模型请求重试初始间隔
     */
    public void setModelRetryInitialDelayMillis(Integer value) {
        this.modelRetryInitialDelayMillis = value;
    }

    /**
     * @return 增量压缩触发决策器类型
     */
    public String getCompressionDecider() {
        return compressionDecider;
    }

    /**
     * @param value 消息、Turn、Token 阈值或始终/从不触发策略
     */
    public void setCompressionDecider(String value) {
        this.compressionDecider = value;
    }

    /**
     * @return 待压缩 Turn 数触发阈值
     */
    public int getCompressionTurnThreshold() {
        return compressionTurnThreshold;
    }

    /**
     * @param value 大于等于 0 的待压缩 Turn 数阈值
     */
    public void setCompressionTurnThreshold(int value) {
        this.compressionTurnThreshold = value;
    }

    /**
     * @return 待压缩估算 Token 数触发阈值
     */
    public long getCompressionTokenThreshold() {
        return compressionTokenThreshold;
    }

    /**
     * @param value 大于等于 0 的待压缩 Token 阈值
     */
    public void setCompressionTokenThreshold(long value) {
        this.compressionTokenThreshold = value;
    }

    /**
     * @return 整段历史或逐消息摘要压缩模式
     */
    public String getCompressionMode() {
        return compressionMode;
    }

    /**
     * @param value WHOLE_HISTORY 或 PER_MESSAGE
     */
    public void setCompressionMode(String value) {
        this.compressionMode = value;
    }

    /**
     * @return 逐消息压缩时要求模型返回 messageId/summary 数组的请求文本
     */
    public String getCompressionPerMessageRequest() {
        return compressionPerMessageRequest;
    }

    /**
     * @param value 长度不超过 4000 的逐消息压缩请求文本
     */
    public void setCompressionPerMessageRequest(String value) {
        this.compressionPerMessageRequest = value;
    }
}
