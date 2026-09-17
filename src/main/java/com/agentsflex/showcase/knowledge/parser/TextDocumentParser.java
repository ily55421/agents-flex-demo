package com.agentsflex.showcase.knowledge.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯文本解析器：txt / md / markdown / jsonl / csv。
 *
 * <p>Markdown 原样返回以保留标题与表格结构（供结构化切片使用）；CSV 转为 Markdown
 * 表格；txt / jsonl 按 UTF-8 直接读取。JSONL 的问答事实库语义由
 * {@code KnowledgeService.addJsonlDocument} 继续承担，本解析器只做文本归一。</p>
 */
@Component
public class TextDocumentParser implements DocumentParser {

    /** CSV 转 Markdown 表格时单行最大列数上限，防止畸形文件生成超宽表格。 */
    private static final int MAX_CSV_COLUMNS = 64;

    @Override
    public List<String> extensions() {
        return Arrays.asList("txt", "text", "md", "markdown", "jsonl", "csv");
    }

    @Override
    public String name() {
        return "文本解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String extension = extensionOf(fileName);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", name());
        if ("csv".equals(extension)) {
            text = csvToMarkdownTable(text);
            metadata.put("format", "csv");
        }
        String markdown = ParsedDocument.normalize(text);
        if (markdown.isEmpty()) {
            throw new DocumentParseException("文件没有可入库的有效文本：" + fileName);
        }
        return new ParsedDocument(markdown, metadata);
    }

    /**
     * 把 CSV 文本转为 Markdown 表格；列数超过上限时按上限截断。
     *
     * @param csv CSV 全文
     * @return Markdown 表格文本
     */
    private static String csvToMarkdownTable(String csv) {
        StringBuilder table = new StringBuilder();
        boolean headerWritten = false;
        for (String line : csv.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            String[] cells = trimmed.split(",", -1);
            if (cells.length > MAX_CSV_COLUMNS) {
                cells = Arrays.copyOf(cells, MAX_CSV_COLUMNS);
            }
            table.append("| ");
            for (int index = 0; index < cells.length; index++) {
                if (index > 0) table.append(" | ");
                // 表格单元格内的竖线与换行必须转义，否则会破坏 Markdown 表格结构
                table.append(cells[index].strip().replace("|", "\\|").replace("\n", " "));
            }
            table.append(" |\n");
            if (!headerWritten) {
                table.append('|');
                for (int index = 0; index < cells.length; index++) {
                    table.append(" --- |");
                }
                table.append('\n');
                headerWritten = true;
            }
        }
        return table.toString();
    }

    /** 提取小写扩展名。 */
    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
