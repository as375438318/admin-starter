package com.adminstarter.system.application.excel;

import com.adminstarter.common.error.BizException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xlsx 解析（SAX 流式）：表头归一化、空行跳过、行号保留、行数上限、坏文件兜底。
 * <p>这几条直接决定用户看到的错误提示好不好用：行号错了用户找不到问题行，
 * 空行没跳过会报一堆「第 N 行为空」，坏文件不兜底会抛 500。</p>
 */
class ExcelStreamParserTest {

    private static final String SHEET = "数据";

    private final ExcelStreamParser parser = new ExcelStreamParser();

    private static ExcelSchema<Object> schema(String sheetName) {
        return new ExcelSchema<>() {
            @Override
            public String key() {
                return "test";
            }

            @Override
            public String title() {
                return "测试";
            }

            @Override
            public String templateFileName() {
                return "测试.xlsx";
            }

            @Override
            public String dataSheetName() {
                return sheetName;
            }

            @Override
            public List<ExcelField<Object>> fields() {
                return List.of();
            }
        };
    }

    /** 生成 xlsx 字节；rows 中每个 String[] 一行，null 表示整行为空（跳过不写单元格）。 */
    private static byte[] workbook(String sheetName, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            for (int index = 0; index < rows.length; index++) {
                if (rows[index] == null) {
                    sheet.createRow(index);   // 空行：建行不写单元格
                    continue;
                }
                Row row = sheet.createRow(index);
                for (int column = 0; column < rows[index].length; column++) {
                    if (rows[index][column] != null) {
                        row.createCell(column).setCellValue(rows[index][column]);
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ExcelStreamParser.ParseResult parse(byte[] bytes, int maxRows) {
        return parser.parse(new ByteArrayInputStream(bytes), schema(SHEET), maxRows);
    }

    @Test
    @DisplayName("正常解析：表头去掉必填星号，数据行按表头映射，并保留 Excel 真实行号")
    void parsesHeaderAndRows() throws IOException {
        byte[] bytes = workbook(SHEET, new String[][]{
                {"任务标题*", "级别"},
                {"消息中心改造", "普通"},
                {"报表导出", "紧急"}});

        ExcelStreamParser.ParseResult result = parse(bytes, 100);

        assertEquals(List.of("任务标题", "级别"), result.header(), "表头尾部的必填星号应被去掉");
        assertEquals(2, result.rows().size());
        assertEquals(2, result.rows().get(0).rowNumber(), "首条数据在 Excel 第 2 行");
        assertEquals(3, result.rows().get(1).rowNumber());
        assertEquals("消息中心改造", result.rows().get(0).cells().get("任务标题"));
        assertEquals("紧急", result.rows().get(1).cells().get("级别"));
    }

    @Test
    @DisplayName("空行被跳过，但后续行号仍是真实行号（否则用户按提示找不到行）")
    void skipsBlankRowsButKeepsRealRowNumbers() throws IOException {
        byte[] bytes = workbook(SHEET, new String[][]{
                {"任务标题"},
                null,
                {"真实数据"}});

        ExcelStreamParser.ParseResult result = parse(bytes, 100);

        assertEquals(1, result.rows().size(), "空行不应进入结果");
        assertEquals(3, result.rows().get(0).rowNumber(), "数据虽然在结果里排第一，行号仍是 3");
    }

    @Test
    @DisplayName("整行只有空白字符也视为空行")
    void rowWithOnlyWhitespaceIsSkipped() throws IOException {
        byte[] bytes = workbook(SHEET, new String[][]{
                {"任务标题"},
                {"   "},
                {"有内容"}});

        assertEquals(1, parse(bytes, 100).rows().size());
    }

    @Test
    @DisplayName("超出表头范围的列被忽略（用户多填的列不至于让整行失败）")
    void extraColumnsBeyondHeaderAreIgnored() throws IOException {
        byte[] bytes = workbook(SHEET, new String[][]{
                {"任务标题"},
                {"有内容", "多出来的一列"}});

        ExcelStreamParser.ParseResult result = parse(bytes, 100);

        assertEquals(1, result.rows().size());
        assertEquals(1, result.rows().get(0).cells().size());
        assertFalse(result.rows().get(0).cells().containsKey(""));
    }

    @Test
    @DisplayName("数据页名不匹配 → 明确提示缺少哪个数据页（不是笼统的解析失败）")
    void missingSheetIsReported() throws IOException {
        byte[] bytes = workbook("别的页", new String[][]{{"任务标题"}, {"x"}});

        BizException error = assertThrows(BizException.class, () -> parse(bytes, 100));

        assertTrue(error.getMessage().contains(SHEET), "提示里要出现期望的页名，实际：" + error.getMessage());
    }

    @Test
    @DisplayName("行数超过上限 → 拒绝并说明上限（避免把几十万行读进内存）")
    void rowLimitIsEnforced() throws IOException {
        byte[] bytes = workbook(SHEET, new String[][]{
                {"任务标题"}, {"1"}, {"2"}, {"3"}});

        BizException error = assertThrows(BizException.class, () -> parse(bytes, 2));

        assertTrue(error.getMessage().contains("上限"), "实际：" + error.getMessage());
    }

    @Test
    @DisplayName("非 xlsx 内容 → 统一提示「请确认是有效的 .xlsx 模板」，不抛 500")
    void brokenFileIsRejectedGracefully() {
        byte[] garbage = "this is not a zip file".getBytes(StandardCharsets.UTF_8);

        BizException error = assertThrows(BizException.class,
                () -> parser.parse(new ByteArrayInputStream(garbage), schema(SHEET), 100));

        assertTrue(error.getMessage().contains(".xlsx"), "实际：" + error.getMessage());
    }
}
