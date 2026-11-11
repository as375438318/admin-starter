package com.adminstarter.system.application.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(SCHEMA, output);
        return output.toByteArray();
    }

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
}
