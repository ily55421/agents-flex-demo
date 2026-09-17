package com.agentsflex.showcase.knowledge.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 解析器：jsoup 提取正文并转为 Markdown。
 *
 * <p>对齐 WeKnora 的 web 解析思路，但只处理已下载的 HTML 字节流（不做联网抓取，
 * 避免 SSRF 与网络依赖）：标题标签转 Markdown 标题、表格转 Markdown 表格、
 * 代码块转围栏；script / style / nav / footer 等噪声节点整体移除。</p>
 */
@Component
public class HtmlDocumentParser implements DocumentParser {

    /** 噪声节点选择器：这些内容不承载知识，移除后再提取正文。 */
    private static final String NOISE_SELECTOR =
            "script, style, noscript, iframe, nav, footer, header, aside, form, svg";

    @Override
    public List<String> extensions() {
        return Arrays.asList("html", "htm");
    }

    @Override
    public String name() {
        return "HTML 解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        String html = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html);
        document.select(NOISE_SELECTOR).remove();
        Element body = document.body() == null ? document : document.body();
        StringBuilder markdown = new StringBuilder();
        appendChildren(markdown, body);
        String result = markdown.toString().strip();
        if (result.isEmpty()) {
            // 无 body 结构时退化为整篇纯文本
            result = document.text().strip();
        }
        if (result.isEmpty()) {
            throw new DocumentParseException("HTML 中没有可入库的正文：" + fileName);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", name());
        // 网页标题常承载主题信息，追加为一级标题便于切片建立标题路径
        String title = document.title();
        if (title != null && !title.isBlank()) {
            metadata.put("pageTitle", title.strip());
            result = "# " + title.strip() + "\n\n" + result;
        }
        return new ParsedDocument(ParsedDocument.normalize(result), metadata);
    }

    /**
     * 递归遍历子节点，按标签语义产出 Markdown。
     *
     * @param markdown 输出缓冲
     * @param parent   当前节点
     */
    private static void appendChildren(StringBuilder markdown, Element parent) {
        for (org.jsoup.nodes.Node node : parent.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                String text = textNode.text().strip();
                if (!text.isEmpty()) markdown.append(text).append(' ');
                continue;
            }
            if (!(node instanceof Element element)) continue;
            String tag = element.tagName().toLowerCase(java.util.Locale.ROOT);
            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    int level = Integer.parseInt(tag.substring(1));
                    markdown.append("\n\n").append("#".repeat(level)).append(' ')
                            .append(element.text().strip()).append("\n\n");
                }
                case "p", "div", "section", "article", "li" -> {
                    markdown.append("\n\n");
                    appendChildren(markdown, element);
                    markdown.append("\n\n");
                }
                case "br" -> markdown.append("\n");
                case "pre" -> markdown.append("\n\n```\n").append(element.wholeText().strip())
                        .append("\n```\n\n");
                case "table" -> appendTable(markdown, element);
                case "ul", "ol" -> {
                    markdown.append("\n\n");
                    Elements items = element.children();
                    for (int index = 0; index < items.size(); index++) {
                        Element item = items.get(index);
                        markdown.append("- ").append(item.text().strip()).append('\n');
                    }
                    markdown.append('\n');
                }
                default -> appendChildren(markdown, element);
            }
        }
    }

    /** HTML 表格 → Markdown 表格。 */
    private static void appendTable(StringBuilder markdown, Element table) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return;
        int columnCount = 0;
        for (Element row : rows) {
            columnCount = Math.max(columnCount, row.select("th, td").size());
        }
        if (columnCount == 0) return;
        markdown.append("\n\n");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Elements cells = rows.get(rowIndex).select("th, td");
            markdown.append("| ");
            for (int column = 0; column < columnCount; column++) {
                if (column > 0) markdown.append(" | ");
                String value = column < cells.size() ? cells.get(column).text() : "";
                markdown.append(value.replace("|", "\\|").replaceAll("[\\r\\n]+", " ").strip());
            }
            markdown.append(" |\n");
            if (rowIndex == 0) {
                markdown.append('|');
                for (int column = 0; column < columnCount; column++) {
                    markdown.append(" --- |");
                }
                markdown.append('\n');
            }
        }
        markdown.append('\n');
    }
}
