package com.agentsflex.showcase.api;

import com.agentsflex.showcase.runtime.ShowcaseRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Agent 创建 HTTP 端点确实启用 Bean Validation，并保持统一错误响应契约。
 */
@WebMvcTest(AgentDefinitionController.class)
class AgentDefinitionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShowcaseRuntime runtime;

    /**
     * 超出模型温度上限的 JSON 必须返回 400，且不能调用真实 Agent Builder。
     */
    @Test
    void rejectsOutOfRangeModelTemperatureBeforeRuntime() throws Exception {
        mockMvc.perform(post("/api/agent/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelTemperature\":2.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("modelTemperature")));

        verify(runtime, never()).createAgent(any());
    }

    /**
     * 显式 null 不得绕过枚举校验并在 Runtime 中产生难以理解的空指针异常。
     */
    @Test
    void rejectsNullExecutionEnumBeforeRuntime() throws Exception {
        mockMvc.perform(post("/api/agent/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolExecutionMode\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("toolExecutionMode")));

        verify(runtime, never()).createAgent(any());
    }

    /**
     * 框架定义的 0=不限边界应通过 HTTP 校验并进入 Runtime。
     */
    @Test
    void acceptsZeroUnlimitedValuesThroughHttpBoundary() throws Exception {
        when(runtime.createAgent(any())).thenReturn(Collections.<String, Object>singletonMap("agentId", "test-agent"));

        mockMvc.perform(post("/api/agent/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAttachedTokens\":0,\"maxInputTokens\":0,"
                                + "\"maxOutputTokens\":0,\"maxTotalTokens\":0,"
                                + "\"maxToolCalls\":0,\"maxDurationMillis\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("test-agent"));

        verify(runtime).createAgent(any());
    }
}
