package com.agentsflex.showcase.api;

import com.agentsflex.showcase.model.CreateAgentRequest;
import com.agentsflex.showcase.runtime.ShowcaseRuntime;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 暴露独立于 Run 生命周期的 Agent 创建边界。
 * 只有该接口成功返回的 agentId 才能用于创建新对话，避免浏览器绕过完整配置步骤。
 */
@Validated
@RestController
@RequestMapping("/api/agent/agents")
public class AgentDefinitionController {

    private final ShowcaseRuntime runtime;

    /**
     * @param runtime 负责构建、注册和查找真实 Agent 的统一控制面
     */
    public AgentDefinitionController(ShowcaseRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 校验完整配置并立即调用 Agents-Flex Builder 创建 Agent。
     *
     * @param request 已通过 Bean Validation 的 Agent 配置
     * @return 包含服务端 agentId 的安全配置视图
     */
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateAgentRequest request) {
        return runtime.createAgent(request);
    }

    /**
     * 列出当前进程已创建 + DuckDB 归档的全部 Agent 安全视图，供纯对话页选择智能体。
     *
     * @return 不含 API Key 的 Agent 定义列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return runtime.listAgents();
    }
}
