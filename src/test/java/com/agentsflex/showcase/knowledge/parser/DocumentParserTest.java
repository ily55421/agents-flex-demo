package com.agentsflex.showcase.knowledge.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档解析器验证：样例全部在内存中动态生成，不向仓库引入二进制测试夹具。
 *
 * <p>覆盖文本/PDF/Word/表格/演示文稿/HTML 六类解析器的产出契约（Markdown 归一）、
 * 标题层级保留、表格转换，以及未知格式与扫描件的可操作错误提示。</p>
 */
class DocumentParserTest {

    private final TextDocumentParser textParser = new TextDocumentParser();
    private final PdfDocumentParser pdfParser = new PdfDocumentParser();
    private final WordDocumentParser wordParser = new WordDocumentParser();
    private final SpreadsheetDocumentParser spreadsheetParser = new SpreadsheetDocumentParser();
    private final PresentationDocumentParser presentationParser = new PresentationDocumentParser();
    private final HtmlDocumentParser htmlParser = new HtmlDocumentParser();

    /**
     * 注册表按扩展名路由，未支持格式抛出含支持列表的中文提示。
     */
    @Test
    void registryRoutesByExtensionAndRejectsUnknownFormat() {
        DocumentParserRegistry registry = new DocumentParserRegistry(Arrays.asList(
                textParser, pdfParser, wordParser, spreadsheetParser, presentationParser, htmlParser));

        assertThat(registry.supports("报告.pdf")).isTrue();
        assertThat(registry.supports("数据.xlsx")).isTrue();
        assertThat(registry.supports("笔记.unknown")).isFalse();
        assertThat(registry.supportedExtensions()).contains("pdf", "docx", "xlsx", "pptx", "html", "md");

        assertThatThrownBy(() -> registry.parse("笔记.unknown", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("不支持的文件格式")
                .hasMessageContaining("pdf");
    }

    /**
     * 文本解析器原样保留 Markdown 结构，CSV 转 Markdown 表格。
     */
    @Test
    void textParserKeepsMarkdownAndConvertsCsv() {
        ParsedDocument markdown = textParser.parse("说明.md",
                "# 标题\n\n正文段落。\n\n```java\nint a = 1;\n```\n".getBytes(StandardCharsets.UTF_8));
        assertThat(markdown.getMarkdown()).contains("# 标题").contains("```java");

        ParsedDocument csv = textParser.parse("数据.csv",
                "名称,数量\n断路器,3\n变压器,2\n".getBytes(StandardCharsets.UTF_8));
        assertThat(csv.getMarkdown()).contains("| 名称 | 数量 |").contains("| 断路器 | 3 |");
    }

    /**
     * 空文本与空字节拒绝入库，提示为中文原因。
     */
    @Test
    void textParserRejectsEmptyInput() {
        assertThatThrownBy(() -> textParser.parse("空.txt", "   ".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("没有可入库的有效文本");
        assertThatThrownBy(() -> textParser.parse("空.txt", new byte[0]))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("文件内容为空");
    }

    /**
     * PDF 解析器逐页提取文本并写入页码标记与页数元数据。
     */
    @Test
    void pdfParserExtractsTextWithPageMarkers() throws Exception {
        byte[] pdf = createPdf("RAG hybrid retrieval combines vector and BM25.");

        ParsedDocument parsed = pdfParser.parse("样例.pdf", pdf);
        assertThat(parsed.getMarkdown()).contains("<!-- page:1 -->")
                .contains("hybrid retrieval");
        assertThat(parsed.getMetadata()).containsEntry("pageCount", "1")
                .containsEntry("parser", "PDF 解析器");
    }

    /**
     * 无文本层的 PDF 被识别为扫描件并给出可操作提示。
     */
    @Test
    void pdfParserExplainsScannedDocument() throws Exception {
        byte[] emptyPdf;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            emptyPdf = out.toByteArray();
        }
        assertThatThrownBy(() -> pdfParser.parse("扫描件.pdf", emptyPdf))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("疑似扫描件")
                .hasMessageContaining("OCR");
    }

    /**
     * Word 解析器把标题样式转为 Markdown 标题，表格转为 Markdown 表格。
     */
    @Test
    void wordParserKeepsHeadingsAndTables() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("切片策略");

            XWPFParagraph body = document.createParagraph();
            body.createRun().setText("保护表格与代码块不被切断。");

            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("参数");
            table.getRow(0).getCell(1).setText("默认值");
            table.getRow(1).getCell(0).setText("chunk_size");
            table.getRow(1).getCell(1).setText("512");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docx = out.toByteArray();
        }

        ParsedDocument parsed = wordParser.parse("设计.docx", docx);
        assertThat(parsed.getMarkdown()).contains("# 切片策略")
                .contains("保护表格与代码块不被切断。")
                .contains("| 参数 | 默认值 |")
                .contains("| chunk_size | 512 |");
        assertThat(parsed.getMetadata()).containsEntry("format", "docx");
    }

    /**
     * 中文版 Word 的「标题 N」样式同样被识别为标题层级。
     */
    @Test
    void wordParserRecognisesChineseHeadingStyles() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("标题 2");
            heading.createRun().setText("二级标题");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docx = out.toByteArray();
        }
        assertThat(wordParser.parse("中文.docx", docx).getMarkdown()).contains("## 二级标题");
    }

    /**
     * 表格解析器：每个工作表输出为「工作表名标题 + Markdown 表格」，元数据含表数。
     */
    @Test
    void spreadsheetParserRendersSheetsAsMarkdownTables() throws Exception {
        byte[] xlsx;
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("设备清单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("设备");
            header.createCell(1).setCellValue("数量");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("断路器");
            row.createCell(1).setCellValue(3);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        ParsedDocument parsed = spreadsheetParser.parse("清单.xls", xlsx);
        assertThat(parsed.getMarkdown()).contains("## 设备清单")
                .contains("| 设备 | 数量 |")
                .contains("| 断路器 | 3 |");
        assertThat(parsed.getMetadata()).containsEntry("sheetCount", "1");
    }

    /**
     * 演示文稿解析器：每页输出占位标题与页数元数据。
     */
    @Test
    void presentationParserRendersSlides() throws Exception {
        byte[] pptx;
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setText("知识库检索流程");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slideShow.write(out);
            pptx = out.toByteArray();
        }

        ParsedDocument parsed = presentationParser.parse("汇报.pptx", pptx);
        assertThat(parsed.getMarkdown()).contains("## Slide 1").contains("知识库检索流程");
        assertThat(parsed.getMetadata()).containsEntry("slideCount", "1");
    }

    /**
     * HTML 解析器：标题/表格/代码块转 Markdown，噪声节点被移除，网页标题作为一级标题。
     */
    @Test
    void htmlParserConvertsStructureAndDropsNoise() {
        String html = "<html><head><title>切片规范</title></head><body>"
                + "<nav>导航噪声</nav>"
                + "<h2>保护规则</h2>"
                + "<p>表格与代码块不被切断。</p>"
                + "<pre>chunk_size = 512</pre>"
                + "<table><tr><th>参数</th><th>值</th></tr><tr><td>overlap</td><td>80</td></tr></table>"
                + "<script>var noise = 1;</script>"
                + "</body></html>";

        ParsedDocument parsed = htmlParser.parse("规范.html", html.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.getMarkdown())
                .contains("# 切片规范")
                .contains("## 保护规则")
                .contains("```")
                .contains("| 参数 | 值 |")
                .contains("| overlap | 80 |")
                .doesNotContain("导航噪声")
                .doesNotContain("var noise");
        assertThat(parsed.getMetadata()).containsEntry("pageTitle", "切片规范");
    }

    /**
     * Markdown 归一化保留代码块内空行、压缩块外连续空行。
     */
    @Test
    void normalizePreservesFenceBlankLinesAndCompressesOthers() {
        String raw = "段落一\n\n\n\n段落二\n\n```\nline1\n\n\nline2\n```\n";
        String normalized = ParsedDocument.normalize(raw);
        assertThat(normalized).contains("段落一\n\n段落二");
        assertThat(normalized).contains("line1\n\n\nline2");
    }

    /** 动态生成单页 PDF，避免仓库引入二进制测试夹具。 */
    private static byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
