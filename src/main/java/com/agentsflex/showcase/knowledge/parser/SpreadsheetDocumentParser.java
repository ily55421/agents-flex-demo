package com.agentsflex.showcase.knowledge.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格解析器：xlsx / xls，每个工作表转为一个 Markdown 表格。
 *
 * <p>工作表名作为二级标题，使切片能建立「文件 > 工作表」的标题路径；空行与空表
 * 跳过，避免产生大量无意义切片。单元格取值统一经 {@link DataFormatter}，
 * 保证日期与数字的展示与 Excel 一致。</p>
 */
@Component
public class SpreadsheetDocumentParser implements DocumentParser {

    /** 单表最大行数上限：防止超大表格撑爆内存与切片数量。 */
    private static final int MAX_ROWS_PER_SHEET = 50_000;
    /** 单表最大列数上限。 */
    private static final int MAX_COLUMNS = 128;

    @Override
    public List<String> extensions() {
        return Arrays.asList("xlsx", "xls");
    }

    @Override
    public String name() {
        return "表格解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        StringBuilder markdown = new StringBuilder();
        int sheetCount = 0;
        try (InputStream in = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            int total = workbook.getNumberOfSheets();
            for (int index = 0; index < total; index++) {
                Sheet sheet = workbook.getSheetAt(index);
                String sheetName = sheet.getSheetName();
                if (sheetName == null || sheetName.isBlank()) {
                    sheetName = "Sheet" + (index + 1);
                }
                String table = renderSheet(sheet, formatter);
                if (table.isEmpty()) continue;
                sheetCount++;
                markdown.append("## ").append(sheetName).append("\n\n").append(table).append('\n');
            }
        } catch (Exception error) {
            throw new DocumentParseException("表格解析失败（文件可能损坏或加密）：" + fileName
                    + "，" + rootMessage(error), error);
        }
        if (markdown.toString().strip().isEmpty()) {
            throw new DocumentParseException("表格中没有可入库的内容：" + fileName);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", name());
        metadata.put("sheetCount", String.valueOf(sheetCount));
        return new ParsedDocument(ParsedDocument.normalize(markdown.toString()), metadata);
    }

    /**
     * 渲染单个工作表为 Markdown 表格。
     *
     * @param sheet     工作表
     * @param formatter 单元格取值格式化器
     * @return Markdown 表格；空表返回空串
     */
    private static String renderSheet(Sheet sheet, DataFormatter formatter) {
        if (sheet == null) return "";
        int firstRow = sheet.getFirstRowNum();
        int lastRow = Math.min(sheet.getLastRowNum(), firstRow + MAX_ROWS_PER_SHEET - 1);
        StringBuilder table = new StringBuilder();
        boolean headerWritten = false;
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            int columnCount = Math.min(row.getLastCellNum(), MAX_COLUMNS);
            if (columnCount <= 0) continue;
            StringBuilder line = new StringBuilder("| ");
            boolean hasValue = false;
            for (int column = 0; column < columnCount; column++) {
                if (column > 0) line.append(" | ");
                Cell cell = row.getCell(column);
                String value = cell == null ? "" : formatter.formatCellValue(cell);
                if (!value.isBlank()) hasValue = true;
                line.append(escapeCell(value));
            }
            line.append(" |\n");
            // 全空行不产出，避免表格出现大量空行
            if (!hasValue) continue;
            table.append(line);
            if (!headerWritten) {
                table.append('|');
                for (int column = 0; column < columnCount; column++) {
                    table.append(" --- |");
                }
                table.append('\n');
                headerWritten = true;
            }
        }
        return table.toString();
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
