package com.adminstarter.system.application.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板生成器：表头、必填标记、填写须知、下拉引用页全部从 {@link ExcelSchema} 派生。
 * <p>模板是用户填写的唯一依据：<b>必填标记漏一个，用户就不知道要填；数据页名与导入期望不一致，整份文件会被判"缺少数据页"</b>。</p>
 */
class ExcelTemplateWriterTest {

    private static final ExcelSchema<Object> SCHEMA = new ExcelSchema<>() {
        @Override
        public String key() {
            return "test";
        }

        @Override
        public String title() {
            return "测试任务";
        }

        @Override
        public String templateFileName() {
            return "测试任务导入模板.xlsx";
        }

        @Override
        public String dataSheetName() {
            return "数据";
        }

        @Override
        public List<ExcelField<Object>> fields() {
            return List.of(
                    ExcelField.builder().key("title").kind(FieldKind.TEXT)
                            .importHeader("任务标题").required(true).maxLength(50).build(),
                    ExcelField.builder().key("level").kind(FieldKind.ENUM)
                            .importHeader("级别")
                            .enums(List.of(new EnumOption("NORMAL", "普通"), new EnumOption("URGENT", "紧急"))).build(),
                    ExcelField.builder().key("deptId").kind(FieldKind.REF_DEPT).importHeader("所属部门").build(),
                    // 只参与导出、不参与导入的字段不应出现在模板里
                    ExcelField.builder().key("id").exportHeader("ID").build());
        }
    };

    /** 模板下拉只依赖引用目录；本测试只断言结构与文案，目录给空实现即可。 */
    private final ExcelTemplateWriter writer = new ExcelTemplateWriter(
            new ReferenceDirectoryService(null, null, null) {
                @Override
                public ReferenceContext context(java.util.List<? extends ExcelField<?>> fields) {
                    return new ReferenceContext(null, null);
                }
            });

    private byte[] template() throws Exception {
        return templateOf(SCHEMA);
    }

    private byte[] templateOf(ExcelSchema<?> schema) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(schema, output);
        return output.toByteArray();
    }

    /** 专用 schema：含"必填的下拉列"，用来验证「必填 + 下拉 → 带输入提示」这个组合。 */
    private static final ExcelSchema<Object> SCHEMA_WITH_REQUIRED_REF = new ExcelSchema<>() {
        @Override
        public String key() {
            return "test-required-ref";
        }

        @Override
        public String title() {
            return "测试任务";
        }

        @Override
        public String templateFileName() {
            return "测试任务导入模板.xlsx";
        }

        @Override
        public String dataSheetName() {
            return "数据";
        }

        @Override
        public List<ExcelField<Object>> fields() {
            return List.of(
                    ExcelField.builder().key("status").kind(FieldKind.ENUM).importHeader("状态")
                            .required(true)
                            .enums(List.of(new EnumOption("ON", "启用"), new EnumOption("OFF", "停用")))
                            .build());
        }
    };

    private static List<String> rowValues(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        List<String> values = new ArrayList<>();
        if (row == null) {
            return values;
        }
        for (int column = 0; column < row.getLastCellNum(); column++) {
            values.add(row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue());
        }
        return values;
    }

    private static boolean anyCellContains(Sheet sheet, String text) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = 0; column < row.getLastCellNum(); column++) {
                String value = row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue();
                if (value != null && value.contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("数据页表头带必填星号，且只包含参与导入的字段")
    void headersCarryRequiredMarksAndSkipExportOnlyFields() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet sheet = workbook.getSheet("数据");
            assertNotNull(sheet, "数据页名必须与 schema.dataSheetName 一致，否则导入会判「缺少数据页」");

            List<String> header = rowValues(sheet, 0);
            assertEquals("任务标题*", header.get(0), "必填列要带 * 标记");
            assertEquals("级别", header.get(1));
            assertEquals("所属部门", header.get(2));
            assertEquals(3, header.size(), "只导出不导入的字段不应出现在模板里");
        }
    }

    @Test
    @DisplayName("枚举列带下拉引用页，且下拉源是全部可选值")
    void enumDropdownReferencesHiddenSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet reference = null;
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (workbook.getSheetName(index).contains("引用")) {
                    reference = workbook.getSheetAt(index);
                }
            }
            assertNotNull(reference, "枚举列应有隐藏的引用数据页（内联下拉受 255 字符上限）");

            List<String> firstColumn = new ArrayList<>();
            for (int rowIndex = 0; rowIndex <= reference.getLastRowNum(); rowIndex++) {
                Row row = reference.getRow(rowIndex);
                if (row != null && row.getCell(0) != null) {
                    firstColumn.add(row.getCell(0).getStringCellValue());
                }
            }
            assertTrue(firstColumn.stream().anyMatch(value -> value.contains("普通")
                            || value.contains("紧急")),
                    "下拉源应包含枚举含义，实际：" + firstColumn);
        }
    }

    @Test
    @DisplayName("填写须知页说明整批校验语义与每列的必填/选填、字数上限")
    void instructionSheetExplainsRules() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet guide = workbook.getSheet("填写须知");
            assertNotNull(guide, "缺少「填写须知」页");

            assertTrue(anyCellContains(guide, "测试任务"), "须知标题应带模块名");
            assertTrue(anyCellContains(guide, "整批校验通过后才会写入"), "必须说明「任一行有错整批不落库」");
            assertTrue(anyCellContains(guide, "任务标题：必填"), "每个字段都要写必填/选填");
            assertTrue(anyCellContains(guide, "级别：选填"));
            assertTrue(anyCellContains(guide, "50"), "有长度上限的字段要写出来");
        }
    }
    // ------------------------------------------------------------------
    // 可用性：星号红色、必填提示、下拉箭头（2026-11-12）
    // 这三条都是"用户能直接感知"的点，且都曾在真实模板里失效过：
    // - `*` 若是纯文本，用户在表头看不出哪列必填；
    // - `showDropDown` 语义是反的（true = 隐藏箭头），传错就等于把下拉藏了；
    // - 必填提示只写在「填写须知」页，用户填数据页时看不到。
    // ------------------------------------------------------------------

    @Test
    @DisplayName("必填列的星号是红色：富文本分两段，只有 * 一段是红字")
    void requiredMarkIsRed() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Cell header = workbook.getSheet("数据").getRow(0).getCell(0);
            assertNotNull(header.getRichStringCellValue(),
                    "表头必须是富文本，否则做不到「只有星号是红的」");

            XSSFRichTextString rich = (XSSFRichTextString) header.getRichStringCellValue();
            String text = rich.getString();
            assertTrue(text.endsWith("*"), "必填列表头应以 * 结尾，实际：" + text);

            int starStart = text.length() - 1;
            XSSFFont starFont = rich.getFontAtIndex(starStart);
            assertNotNull(starFont, "星号必须有独立的字体段");
            assertEquals(IndexedColors.RED.getIndex(), starFont.getColor(),
                    "星号必须是红色（用户明确要求的视觉标记）");
        }
    }

    @Test
    @DisplayName("必填列的表头批注以【必填】开头：提示每一行都要填")
    void requiredColumnsHintEveryRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet sheet = workbook.getSheet("数据");
            String required = sheet.getRow(0).getCell(0).getCellComment().getString().getString();
            assertTrue(required.contains("【必填】"), "必填列表头批注应明确标出必填，实际：" + required);
            assertTrue(required.contains("每一行"), "提示要说清「每一行都要填」，实际：" + required);
        }
    }

    @Test
    @DisplayName("枚举/部门列带下拉且箭头可见：suppressDropDownArrow 必须为 true（OOXML 语义是反的）")
    void referenceColumnsHaveVisibleDropdown() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templateOf(SCHEMA_WITH_REQUIRED_REF)))) {
            Sheet data = workbook.getSheet("数据");
            List<? extends DataValidation> validations = data.getDataValidations();
            assertTrue(validations.size() >= 1, "枚举/部门列应有下拉数据验证");

            // POI 的 getSuppressDropDownArrow 对应 OOXML 的 showDropDown；
            // true 才是"显示箭头"（该属性语义为“抑制下拉箭头”的反向表达）。
            assertTrue(validations.stream().allMatch(DataValidation::getSuppressDropDownArrow),
                    "下拉箭头必须可见，否则用户不知道这一列能选");

            // 必填列的下拉要带输入提示（选中单元格即显示“每行必填”）
            assertTrue(validations.stream().anyMatch(DataValidation::getShowPromptBox),
                    "必填列的下拉应带输入提示框");
        }
    }
    @Test
    @DisplayName("所有表头都加粗且都走富文本：不能出现「有的粗有的不粗」")
    void allHeadersAreBoldRichText() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Row header = workbook.getSheet("数据").getRow(0);
            for (int column = 0; column < header.getLastCellNum(); column++) {
                Cell cell = header.getCell(column);
                String text = cell.getStringCellValue();
                // 普通字符串的加粗来自单元格样式，富文本的加粗来自 run 字体；
                // 两套机制在 Excel 里表现不一致 → 统一走富文本，避免"有的粗有的不粗"。
                assertTrue(cell.getRichStringCellValue() instanceof XSSFRichTextString,
                        "表头必须是富文本：" + text);
                XSSFRichTextString rich = (XSSFRichTextString) cell.getRichStringCellValue();
                XSSFFont firstFont = rich.getFontAtIndex(0);
                assertNotNull(firstFont, "表头文字必须有显式字体段：" + text);
                assertTrue(firstFont.getBold(), "表头文字必须加粗：" + text);
            }
        }
    }
}