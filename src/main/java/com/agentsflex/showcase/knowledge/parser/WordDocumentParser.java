package com.agentsflex.showcase.knowledge.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Word 解析器：docx 保留标题层级与表格结构，doc 退化为纯文本提取。
 *
 * <p>标题样式（Heading 1-6 / 标题 1-6）转为 Markdown 标题，使下游结构化切片能
 * 建立标题路径（对齐 WeKnora 的 header_tracker）；表格转为 Markdown 表格。</p>
 */
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public List<String> extensions() {
        return Arrays.asList("docx", "doc");
    }

    @Override
    public String name() {
        return "Word 解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        boolean legacy = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".doc");
        String markdown = legacy ? parseLegacyDoc(fileName, bytes) : parseDocx(fileName, bytes);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", name());
        metadata.put("format", legacy ? "doc" : "docx");
        if (markdown.strip().isEmpty()) {
            throw new DocumentParseException("文档未提取到有效文本：" + fileName);
        }
        return new ParsedDocument(ParsedDocument.normalize(markdown), metadata);
    }

    /**
     * 解析 docx：按文档体顺序遍历段落与表格，标题样式转 Markdown 标题。
     *
     * @param fileName 文件名（仅用于错误提示）
     * @param bytes    文件字节
     * @return Markdown 文本
     */
    private static String parseDocx(String fileName, byte[] bytes) {
        StringBuilder markdown = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(in)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(markdown, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(markdown, table);
                }
            }
        } catch (Exception error) {
            throw new DocumentParseException("Word 文档解析失败（文件可能损坏或格式不符）："
                    + fileName + "，" + rootMessage(error), error);
        }
        return markdown.toString();
    }

    /** 段落 → Markdown：标题样式加 # 前缀，其余按正文输出。 */
    private static void appendParagraph(StringBuilder markdown, XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.strip().isEmpty()) return;
        String style = paragraph.getStyle();
        int level = headingLevel(style);
        if (level > 0) {
            markdown.append("#".repeat(level)).append(' ').append(text.strip()).append("\n\n");
        } else {
            markdown.append(text.strip()).append("\n\n");
        }
    }

    /** 表格 → Markdown 表格：首行作表头，单元格内换行与竖线转义。 */
    private static void appendTable(StringBuilder markdown, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;
        int columnCount = 0;
        for (XWPFTableRow row : rows) {
            columnCount = Math.max(columnCount, row.getTableCells().size());
        }
        if (columnCount == 0) return;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
            markdown.append("| ");
            for (int column = 0; column < columnCount; column++) {
                if (column > 0) markdown.append(" | ");
                String value = column < cells.size() ? cells.get(column).getText() : "";
                markdown.append(escapeCell(value));
            }
            markdown.append(" |\n");
            if (rowIndex == 0) {
                markdown.append("|");
                for (int column = 0; column < columnCount; column++) {
                    markdown.append(" --- |");
                }
                markdown.append('\n');
            }
        }
        markdown.append('\n');
    }

    /** 解析旧版 .doc：POI scratchpad 只能做纯文本提取。 */
    private static String parseLegacyDoc(String fileName, byte[] bytes) {
        StringBuilder markdown = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            for (String paragraph : extractor.getParagraphText()) {
                String text = paragraph == null ? "" : paragraph.replaceAll("[\\r\\n\\u0007]+", " ").strip();
                if (text.isEmpty()) continue;
                markdown.append(text).append("\n\n");
            }
        } catch (Exception error) {
            throw new DocumentParseException("旧版 Word 文档解析失败（建议另存为 .docx 后重试）："
                    + fileName + "，" + rootMessage(error), error);
        }
        return markdown.toString();
    }

    /**
     * 从样式名推断标题层级。
     *
     * @param style 段落样式名（可能为空）
     * @return 1-6；非标题返回 0
     */
    private static int headingLevel(String style) {
        if (style == null || style.isBlank()) return 0;
        String normalized = style.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("heading ") || normalized.startsWith("heading")) {
            String suffix = normalized.replace("heading", "").replace(" ", "").strip();
            return parseLevel(suffix);
        }
        // 中文版 Word 的标题样式名
        if (normalized.startsWith("标题") || normalized.startsWith("標題")) {
            String suffix = normalized.replace("标题", "").replace("標題", "").strip();
            return parseLevel(suffix);
        }
        return 0;
    }

    /** 把样式名中的数字转成 1-6 的层级。 */
    private static int parseLevel(String suffix) {
        if (suffix.isEmpty()) return 1;
        try {
            int level = Integer.parseInt(suffix.replaceAll("[^0-9]", ""));
            return level >= 1 && level <= 6 ? level : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 转义 Markdown 表格单元格内的竖线与换行。 */
    private static String escapeCell(String raw) {
        if (raw == null) return "";
        return raw.replace("|", "\\|").replaceAll("[\\r\\n]+", "<br>").strip();
    }

    /** 取最底层异常消息，压成单行。 */
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String raw = current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        String text = raw.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
