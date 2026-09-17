package com.agentsflex.showcase.knowledge.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析器注册表：按文件扩展名路由到具体 {@link DocumentParser}。
 *
 * <p>对齐 WeKnora docreader 的 {@code ParserEngineRegistry} 思路：解析器自注册，
 * 调用方只提交文件名与字节流。未命中扩展名时抛出可操作的错误，并在消息中列出
 * 全部已支持格式，避免用户拿到无提示的失败。</p>
 */
@Component
public class DocumentParserRegistry {

    private final Map<String, DocumentParser> parsersByExtension;

    /**
     * @param parsers Spring 容器中全部解析器实现；扩展名冲突时以先注册者为准
     */
    public DocumentParserRegistry(List<DocumentParser> parsers) {
        Map<String, DocumentParser> mapping = new LinkedHashMap<>();
        for (DocumentParser parser : parsers) {
            for (String extension : parser.extensions()) {
                mapping.putIfAbsent(extension.toLowerCase(Locale.ROOT), parser);
            }
        }
        this.parsersByExtension = Collections.unmodifiableMap(mapping);
    }

    /**
     * @param fileName 文件名（可含路径）
     * @return 是否支持该文件
     */
    public boolean supports(String fileName) {
        return parsersByExtension.containsKey(extensionOf(fileName));
    }

    /**
     * 解析文档。
     *
     * @param fileName 原始文件名
     * @param bytes    文件字节流
     * @return 解析产物
     * @throws DocumentParseException 格式不支持或解析失败
     */
    public ParsedDocument parse(String fileName, byte[] bytes) {
        String extension = extensionOf(fileName);
        DocumentParser parser = parsersByExtension.get(extension);
        if (parser == null) {
            throw new DocumentParseException("不支持的文件格式 ." + extension
                    + "，当前支持：" + String.join(" / ", supportedExtensions()));
        }
        return parser.parse(fileName, bytes);
    }

    /**
     * @return 全部支持的扩展名（按注册顺序，供前端文件选择器与提示使用）
     */
    public List<String> supportedExtensions() {
        return new ArrayList<>(parsersByExtension.keySet());
    }

    /**
     * @return 扩展名到解析器展示名的映射（供 /parsers 端点展示）
     */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Map.Entry<String, DocumentParser> entry : parsersByExtension.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("extension", entry.getKey());
            row.put("parser", entry.getValue().name());
            values.add(row);
        }
        return values;
    }

    /**
     * 提取小写扩展名；无扩展名时返回空串。
     *
     * @param name 文件名
     * @return 不含点号的扩展名
     */
    private static String extensionOf(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String baseName = slash < 0 ? name : name.substring(slash + 1);
        int dot = baseName.lastIndexOf('.');
        return dot < 0 ? "" : baseName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
