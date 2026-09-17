package com.agentsflex.showcase.knowledge.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 解析器：基于 PDFBox 逐页提取文本，页边界保留为注释标记。
 *
 * <p>对齐 WeKnora「解析层不含 OCR」的架构决策：本解析器只做文本层提取，
 * 扫描件（无文本层）会被明确识别并给出可操作提示，而不是静默产出空文档。
 * 页标记 {@code <!-- page:N -->} 使前端预览与引用可定位到具体页码。</p>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    /** 扫描件判定阈值：提取文本少于该字符数时认为该页无文本层。 */
    private static final int SCANNED_TEXT_THRESHOLD = 10;

    @Override
    public List<String> extensions() {
        return Collections.singletonList("pdf");
    }

    @Override
    public String name() {
        return "PDF 解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        StringBuilder markdown = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            int textLength = 0;
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                // 逐页提取：页内文本保留原始换行，页边界用注释标记，便于引用定位
                String pageText = stripper.getText(document);
                if (pageText == null || pageText.strip().length() < SCANNED_TEXT_THRESHOLD) {
                    continue;
                }
                textLength += pageText.length();
                markdown.append("<!-- page:").append(page).append(" -->\n");
                markdown.append(pageText.strip()).append("\n\n");
            }
            if (textLength == 0) {
                throw new DocumentParseException("PDF 未提取到文本层（共 " + pageCount
                        + " 页，疑似扫描件）。当前版本不内置 OCR，请改用带文本层的 PDF 或先做 OCR 转换。");
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("parser", name());
            metadata.put("pageCount", String.valueOf(pageCount));
            return new ParsedDocument(ParsedDocument.normalize(markdown.toString()), metadata);
        } catch (DocumentParseException error) {
            throw error;
        } catch (IOException error) {
            throw new DocumentParseException("PDF 解析失败（文件可能损坏或受密码保护）："
                    + friendlyMessage(error), error);
        }
    }

    /** 把底层异常压成单行可读文本。 */
    private static String friendlyMessage(Exception error) {
        String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String text = raw.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
