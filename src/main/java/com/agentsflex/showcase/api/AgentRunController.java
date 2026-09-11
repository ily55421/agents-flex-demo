package com.agentsflex.showcase.api;

import com.agentsflex.showcase.model.ApprovalRequest;
import com.agentsflex.showcase.model.CreateRunRequest;
import com.agentsflex.showcase.model.ContinueConversationRequest;
import com.agentsflex.showcase.model.FormSubmissionRequest;
import com.agentsflex.showcase.model.UpdateModelRequest;
import com.agentsflex.showcase.runtime.ShowcaseRuntime;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Demo 的 HTTP 边界层。
 *
 * <p>控制器只负责参数校验和路由：写接口向 {@link ShowcaseRuntime} 提交命令，读接口返回
 * 当前 Snapshot 或 Trace 投影，SSE 接口持续推送原生 AgentEvent。所有状态转换都留在
 * Runtime 中，避免 Controller 与前端复制 Agents-Flex 的状态机。</p>
 */
@Validated
@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {

    private final ShowcaseRuntime runtime;

    /**
     * 注入唯一的 Runtime 控制面服务。
     *
     * @param runtime 负责 Run 生命周期与状态投影的服务
     */
    public AgentRunController(ShowcaseRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 创建 READY Run，但不立即执行 Step。
     *
     * @param request 已通过 Bean Validation 的 Agent ID 与首条用户消息
     * @return 新 Run Snapshot
     */
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateRunRequest request) {
        return runtime.create(request);
    }

    /**
     * @return 当前进程内全部 Run 的最新 Snapshot 列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return runtime.list();
    }

    /**
     * 返回聊天界面需要的真实模型连接状态，响应中刻意排除敏感 API Key。
     *
     * @return 安全的供应商、模型、端点与配置状态
     */
    @GetMapping("/model")
    public Map<String, Object> modelStatus() {
        return runtime.modelStatus();
    }

    /**
     * 仅应用模型连接配置到服务端内存，使页面“应用配置”立即生效而不必创建 Agent。
     *
     * @param request 已通过 Bean Validation 的模型连接与采样参数
     * @return 应用后的不含 API Key 模型状态
     */
    @PostMapping("/model")
    public Map<String, Object> updateModel(@Valid @RequestBody UpdateModelRequest request) {
        return runtime.updateModel(request);
    }

    /**
     * @param runId Turn ID
     * @return 指定 Run 的最新 Snapshot
     */
    @GetMapping("/{runId}")
    public Map<String, Object> get(@PathVariable String runId) {
        return runtime.get(runId);
    }

    /**
     * @param runId READY Turn ID
     * @return 提交异步启动命令后的 Snapshot
     */
    @PostMapping("/{runId}/start")
    public Map<String, Object> start(@PathVariable String runId) {
        return runtime.start(runId);
    }

    /**
     * 在已有会话的终态 Turn 后追加用户消息，并创建共享 ChatMemory 的新 READY Turn。
     *
     * @param runId   当前会话最后一个 Turn ID
     * @param request 新一轮用户消息
     * @return 新 Turn 的 READY Snapshot，调用方随后建立 SSE 并启动它
     */
    @PostMapping("/{runId}/messages")
    public Map<String, Object> continueConversation(@PathVariable String runId,
                                                    @Valid @RequestBody ContinueConversationRequest request) {
        return runtime.continueConversation(runId, request.getMessage());
    }

    /**
     * 提交 Runtime JSON Schema 表单并恢复对应 USER_INPUT Suspension。
     *
     * @param runId   等待用户输入的 Turn ID
     * @param request 动态字段值
     * @return 恢复后的 Snapshot
     */
    @PostMapping("/{runId}/form")
    public Map<String, Object> submitForm(@PathVariable String runId,
                                          @Valid @RequestBody FormSubmissionRequest request) {
        return runtime.submitForm(runId, request.getValues());
    }

    /**
     * @param runId   等待工具审批的 Turn ID
     * @param request 批准或拒绝决策
     * @return 提交审批命令后的 Snapshot
     */
    @PostMapping("/{runId}/approval")
    public Map<String, Object> approve(@PathVariable String runId,
                                       @RequestBody ApprovalRequest request) {
        return runtime.approve(runId, request);
    }

    /**
     * @param runId 要在安全检查点挂起的 Turn ID
     * @return 即时挂起或记录暂停请求后的 Snapshot
     */
    @PostMapping("/{runId}/suspend")
    public Map<String, Object> suspend(@PathVariable String runId) {
        return runtime.suspend(runId);
    }

    /**
     * @param runId 已手工挂起的 Turn ID
     * @return 恢复同一 Turn 后的 Snapshot
     */
    @PostMapping("/{runId}/resume")
    public Map<String, Object> resume(@PathVariable String runId) {
        return runtime.resume(runId);
    }

    /**
     * @param runId 要取消的 Turn ID
     * @return CANCELLED Snapshot
     */
    @PostMapping("/{runId}/cancel")
    public Map<String, Object> cancel(@PathVariable String runId) {
        return runtime.cancel(runId);
    }

    /**
     * @param runId Turn ID
     * @return 强制刷新后的 OTel Trace 与 Metric 投影
     */
    @GetMapping("/{runId}/trace")
    public Map<String, Object> trace(@PathVariable String runId) {
        return runtime.trace(runId);
    }

    /**
     * @param runId 要实时订阅的 Turn ID
     * @return 不设服务端超时的 SSE 连接
     */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String runId) {
        // 连接建立时先发送完整 Snapshot，之后只增量发送带 eventId 的原生事件。
        return runtime.subscribe(runId);
    }
}
