package com.agentsflex.showcase.knowledge.parser;

/**
 * 文档解析失败异常：消息必须是可直接展示给用户的可操作中文提示。
 *
 * <p>解析失败属于预期内的用户输入问题（格式不支持、文件损坏、疑似扫描件），
 * 因此与参数校验异常同样映射为 4xx，而不是 5xx 服务端错误。</p>
 */
public class DocumentParseException extends RuntimeException {

    /**
     * @param message 面向用户的中文原因说明
     */
    public DocumentParseException(String message) {
        super(message);
    }

    /**
     * @param message 面向用户的中文原因说明
     * @param cause   底层异常
     */
    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
