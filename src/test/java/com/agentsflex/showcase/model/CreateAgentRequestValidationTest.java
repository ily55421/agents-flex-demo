package com.agentsflex.showcase.model;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 Agent 创建请求的 HTTP 输入边界，确保浏览器约束不能被直接调用 API 绕过。
 */
class CreateAgentRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    /**
     * 创建与 Spring MVC 相同规则的 Bean Validation 校验器。
     */
    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    /**
     * 释放校验器工厂持有的缓存资源。
     */
    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /**
     * 默认配置必须始终是可直接创建 Agent 的有效配置。
     */
    @Test
    void acceptsTheCompleteDefaultConfiguration() {
        assertThat(validator.validate(new CreateAgentRequest())).isEmpty();
    }

    /**
     * 验证 Agents-Flex 定义为“0 表示不限”的边界不会被 Demo 的请求校验错误拒绝。
     */
    @Test
    void acceptsZeroForEveryUnlimitedBoundary() {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setMaxAttachedTokens(0);
        request.setMaxInputTokens(0);
        request.setMaxOutputTokens(0);
        request.setMaxTotalTokens(0);
        request.setMaxToolCalls(0);
        request.setMaxDurationMillis(0);
        request.setMaxRetries(0);
        request.setInitialDelayMillis(0);
        request.setMaxDelayMillis(0);
        request.setModelCallTimeoutMillis(0);
        request.setToolExecutionTimeoutMillis(0);
        request.setExternalToolTimeoutMillis(0);
        request.setApprovalTimeoutMillis(0);
        request.setUserInputTimeoutMillis(0);
        request.setToolResultMaxCharacters(0);
        request.setExternalToolResultMaxCharacters(0);
        request.setCompressionModelCallTimeoutMillis(0);
        request.setCompressionMaxInputCharacters(0);
        request.setCompressionMaxOutputCharacters(0);
        request.setModelRetryCount(0);
        request.setModelRetryInitialDelayMillis(0);
        request.setCompressionMessageThreshold(0);
        request.setCompressionTurnThreshold(0);
        request.setCompressionTokenThreshold(0);

        assertThat(validator.validate(request)).isEmpty();
    }

    /**
     * 每一种非法数值、枚举、必填文本和集合边界都必须定位到准确字段。
     */
    @ParameterizedTest(name = "拒绝非法字段 {0}")
    @MethodSource("invalidConfigurations")
    void rejectsEveryInvalidConfigurationBoundary(String expectedField,
                                                  Consumer<CreateAgentRequest> mutation) {
        CreateAgentRequest request = new CreateAgentRequest();
        mutation.accept(request);

        Set<ConstraintViolation<CreateAgentRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .anySatisfy(path -> assertThat(path).startsWith(expectedField));
    }

    /**
     * 构造全部代表性越界输入。每个 Builder 标量至少覆盖一个下界、上界或非法枚举值。
     */
    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                invalid("name", value -> value.setName(" ")),
                invalid("modelTemperature", value -> value.setModelTemperature(2.01f)),
                invalid("modelTopP", value -> value.setModelTopP(-0.01f)),
                invalid("modelTopK", value -> value.setModelTopK(0)),
                invalid("modelMaxTokens", value -> value.setModelMaxTokens(0)),
                invalid("modelStop", value -> value.setModelStop(strings(21, "stop"))),
                invalid("modelStop", value -> value.setModelStop(Collections.singletonList(" "))),
                invalid("modelResponseFormat", value -> value.setModelResponseFormat(null)),
                invalid("modelRetryCount", value -> value.setModelRetryCount(21)),
                invalid("modelRetryInitialDelayMillis", value -> value.setModelRetryInitialDelayMillis(300001)),
                invalid("maxIterations", value -> value.setMaxIterations(0)),
                invalid("maxSteps", value -> value.setMaxSteps(501)),
                invalid("maxAttachedTurns", value -> value.setMaxAttachedTurns(0)),
                invalid("maxAttachedTokens", value -> value.setMaxAttachedTokens(10000001)),
                invalid("maxAttachedMessages", value -> value.setMaxAttachedMessages(501)),
                invalid("maxInputTokens", value -> value.setMaxInputTokens(-1)),
                invalid("maxOutputTokens", value -> value.setMaxOutputTokens(1000001)),
                invalid("maxTotalTokens", value -> value.setMaxTotalTokens(-1)),
                invalid("maxToolCalls", value -> value.setMaxToolCalls(1001)),
                invalid("maxDurationMillis", value -> value.setMaxDurationMillis(86400001)),
                invalid("maxRetries", value -> value.setMaxRetries(11)),
                invalid("initialDelayMillis", value -> value.setInitialDelayMillis(60001)),
                invalid("maxDelayMillis", value -> value.setMaxDelayMillis(300001)),
                invalid("retryMultiplier", value -> value.setRetryMultiplier(0.99d)),
                invalid("compressionDecider", value -> value.setCompressionDecider("SIZE")),
                invalid("compressionMessageThreshold", value -> value.setCompressionMessageThreshold(-1)),
                invalid("compressionTurnThreshold", value -> value.setCompressionTurnThreshold(1000001)),
                invalid("compressionTokenThreshold", value -> value.setCompressionTokenThreshold(-1)),
                invalid("compressionMode", value -> value.setCompressionMode("EXCERPT")),
                invalid("toolErrorStrategy", value -> value.setToolErrorStrategy(null)),
                invalid("interruptedToolMessageTemplate", value -> value.setInterruptedToolMessageTemplate(" ")),
                invalid("interruptedTurnMessageTemplate", value -> value.setInterruptedTurnMessageTemplate(" ")),
                invalid("cancellationReason", value -> value.setCancellationReason(" ")),
                invalid("modelCallTimeoutMillis", value -> value.setModelCallTimeoutMillis(86400001)),
                invalid("toolExecutionTimeoutMillis", value -> value.setToolExecutionTimeoutMillis(-1)),
                invalid("externalToolTimeoutMillis", value -> value.setExternalToolTimeoutMillis(86400001)),
                invalid("approvalTimeoutMillis", value -> value.setApprovalTimeoutMillis(-1)),
                invalid("userInputTimeoutMillis", value -> value.setUserInputTimeoutMillis(86400001)),
                invalid("suspensionExpirationStrategy", value -> value.setSuspensionExpirationStrategy("UNKNOWN")),
                invalid("toolResultMaxCharacters", value -> value.setToolResultMaxCharacters(10000001)),
                invalid("externalToolResultMaxCharacters", value -> value.setExternalToolResultMaxCharacters(-1)),
                invalid("toolResultOverflowStrategy", value -> value.setToolResultOverflowStrategy(null)),
                invalid("toolExecutionMode", value -> value.setToolExecutionMode("ASYNC")),
                invalid("maxParallelToolCalls", value -> value.setMaxParallelToolCalls(0)),
                invalid("parallelFailureStrategy", value -> value.setParallelFailureStrategy(null)),
                invalid("compressionKeepRecentTurns", value -> value.setCompressionKeepRecentTurns(101)),
                invalid("compressionFailureStrategy", value -> value.setCompressionFailureStrategy("IGNORE")),
                invalid("compressionInstruction", value -> value.setCompressionInstruction(" ")),
                invalid("compressionHistoryHeader", value -> value.setCompressionHistoryHeader(" ")),
                invalid("compressionSummaryPrefix", value -> value.setCompressionSummaryPrefix(" ")),
                invalid("compressionPerMessageRequest", value -> value.setCompressionPerMessageRequest(" ")),
                invalid("compressionModelCallTimeoutMillis",
                        value -> value.setCompressionModelCallTimeoutMillis(86400001)),
                invalid("compressionMaxInputCharacters",
                        value -> value.setCompressionMaxInputCharacters(10000001)),
                invalid("compressionMaxOutputCharacters",
                        value -> value.setCompressionMaxOutputCharacters(-1))
        );
    }

    /**
     * 将字段名和修改动作封装为参数化测试参数。
     */
    private static Arguments invalid(String field, Consumer<CreateAgentRequest> mutation) {
        return Arguments.of(field, mutation);
    }

    /**
     * 创建固定数量的字符串，用于验证停止序列集合的数量上限。
     */
    private static List<String> strings(int count, String value) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) values.add(value + index);
        return values;
    }
}
