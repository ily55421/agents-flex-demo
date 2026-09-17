package com.agentsflex.showcase.api;

import com.agentsflex.showcase.knowledge.parser.DocumentParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将参数校验错误和 Runtime 状态错误映射为一致的 JSON 响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理 Run 不存在、当前状态不允许执行命令等业务冲突。
     *
     * @param error Runtime 抛出的参数或状态异常
     * @return HTTP 409 及前端可直接展示的错误信息
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
    }

    /**
     * 处理文档解析失败：格式不支持、文件损坏、PDF 无文本层（疑似扫描件）等。
     *
     * <p>这类问题由用户输入决定而非服务端故障，因此与参数校验错误一样映射为 4xx，
     * 避免前端把「这个文件解析不了」误判为服务异常。</p>
     *
     * @param error 解析器抛出的异常，消息已整理为可操作的中文提示
     * @return HTTP 409 及可直接展示的原因
     */
    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentParse(DocumentParseException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
    }

    /**
     * 提取 Bean Validation 的第一条字段错误，避免向前端暴露冗长的框架异常结构。
     *
     * @param error Spring MVC 请求体校验异常
     * @return HTTP 400 及“字段名: 原因”格式的错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().isEmpty()
                ? "Request validation failed"
                : error.getBindingResult().getFieldErrors().get(0).getField() + ": "
                + error.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 组装所有异常处理器共用的错误响应格式。
     *
     * @param status  HTTP 状态码
     * @param message 面向调用方的错误原因
     * @return 包含时间、状态、错误类型和消息的响应实体
     */
    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
