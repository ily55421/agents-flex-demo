package com.agentsflex.showcase.knowledge.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档解析产物：归一化的 Markdown 正文 + 解析元数据。
 *
 * <p>对齐 WeKnora docreader 的产物契约（{@code ReadResponse{markdown_content, metadata}}）：
 * 无论输入是 PDF、Word、Excel 还是 HTML，解析器一律输出 Markdown，使下游切片器
 * 只需处理一种输入形态。元数据用于状态展示与前端提示（如页数、表数、是否为扫描件）。</p>
 */
public final class ParsedDocument {

    private final String markdown;
    private final Map<String, String> metadata;

    /**
     * @param markdown 归一化后的 Markdown 正文
     * @param metadata 解析元数据（如 parser / pageCount / sheetCount / slideCount）
     */
    public ParsedDocument(String markdown, Map<String, String> metadata) {
        this.markdown = markdown == null ? "" : markdown;
        this.metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /** @return 归一化 Markdown 正文 */
    public String getMarkdown() {
        return markdown;
    }

    /** @return 解析元数据（只读） */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** @return 正文是否为空白（无可入库内容） */
    public boolean isBlank() {
        return markdown.trim().isEmpty();
    }

    /**
     * 转换为前端可直接序列化的视图，供上传响应附加解析信息。
     *
     * @return 含 markdown / charCount / 各项元数据的映射
     */
    public Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("markdown", markdown);
        view.put("charCount", (long) markdown.length());
        view.putAll(metadata);
        return view;
    }

    /**
     * 便捷构造：单一项元数据。
     *
     * @param markdown Markdown 正文
     * @param key      元数据键
     * @param value    元数据值
     * @return 解析产物
     */
    public static ParsedDocument of(String markdown, String key, String value) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(key, value);
        return new ParsedDocument(markdown, metadata);
    }

    /**
     * 便捷构造：仅正文。
     *
     * @param markdown Markdown 正文
     * @return 解析产物（无元数据）
     */
    public static ParsedDocument of(String markdown) {
        return new ParsedDocument(markdown, Collections.emptyMap());
    }

    /**
     * 归一化 Markdown：统一换行符、压缩三个以上连续空行、去掉首尾空白。
     *
     * <p>各解析器产出的空行密度不一致（POI 段落与表格之间常出现多个空行），
     * 归一化后再交给切片器可避免产生大量空切片。</p>
     *
     * @param raw 原始文本
     * @return 归一化文本
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        // 保护 Markdown 代码块内的空行：围栏内原样保留，围栏外连续空行压缩为一个空行
        StringBuilder result = new StringBuilder();
        boolean pendingBlank = false;
        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            boolean fenceLine = isFence(line);
            if (!inFence && line.isBlank()) {
                // 围栏外的空行只记状态：在下一个非空行前补一个空行（即两个换行 = 一段间隔）
                pendingBlank = true;
                continue;
            }
            if (!inFence && pendingBlank) {
                result.append('\n');
                pendingBlank = false;
            }
            result.append(line).append('\n');
            if (fenceLine) inFence = !inFence;
        }
        return result.toString().strip();
    }

    /** @return 该行是否为代码围栏标记行 */
    private static boolean isFence(String line) {
        String stripped = line.stripLeading();
        return stripped.startsWith("```") || stripped.startsWith("~~~");
    }
}
