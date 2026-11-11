package com.adminstarter.system.application.excel;

import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.DeptDirectory;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.UserDirectory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 导出写表器与模板生成器：列集合与单元格内容都从 {@link ExcelSchema} 派生。
 * <p>这里锁三件事：① 表头就是 schema 的导出表头（不多不少、顺序一致，否则用户拿到的文件与文档不符）；
 * ② 枚举/部门/用户按「显示值」导出而不是原始 ID；③ <b>无权限的掩码字段整列不输出</b>（导出是数据外流的最大出口）。</p>
 */
class ExcelExportWriterTest {

    /** 测试实体。 */
    private record RowData(String name, String level, Long deptId, Long ownerId, String phone) {
    }

    private static final ExcelSchema<RowData> SCHEMA = new ExcelSchema<>() {
        @Override
        public String key() {
            return "test";
        }

        @Override
        public String title() {
            return "测试导出";
        }

        @Override
        public String templateFileName() {
            return "测试.xlsx";
        }

        @Override
        public String dataSheetName() {
            return "数据";
        }

        @Override
        public List<ExcelField<RowData>> fields() {
            return List.of(
                    ExcelField.<RowData>builder().key("name").importHeader("任务标题").exportHeader("任务标题")
                            .exporter(RowData::name).build(),
                    ExcelField.<RowData>builder().key("level").importHeader("级别").exportHeader("级别")
                            .kind(FieldKind.ENUM)
                            .enums(List.of(new EnumOption("NORMAL", "普通"), new EnumOption("URGENT", "紧急")))
                            .refValue(RowData::level).build(),
                    ExcelField.<RowData>builder().key("deptId").importHeader("所属部门").exportHeader("所属部门")
                            .kind(FieldKind.REF_DEPT).refValue(RowData::deptId).build(),
                    ExcelField.<RowData>builder().key("ownerId").importHeader("负责人").exportHeader("负责人")
                            .kind(FieldKind.REF_USER).refValue(RowData::ownerId).build(),
                    ExcelField.<RowData>builder().key("phone").importHeader("手机号").exportHeader("手机号")
                            .exporter(RowData::phone).maskFieldKey("user.phone").maskFormat(MaskFormat.PHONE).build());
        }
    };

    private final ReferenceDirectoryService referenceDirectoryService = mock(ReferenceDirectoryService.class);
    private final ExcelExportWriter writer = new ExcelExportWriter(referenceDirectoryService);

    @BeforeEach
    void setUp() {
        DeptDirectory depts = new DeptDirectory(Map.of(2L, "总部/研发部"), Map.of(), Set.of(2L));
        UserDirectory users = new UserDirectory(Map.of(5L, "张三（总部/研发部）"), Map.of(), Map.of(), Map.of(), Set.of(5L));
        when(referenceDirectoryService.context(any())).thenReturn(new ReferenceContext(depts, users));
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static void loginWith(String... permissions) {
        DataScopeContext.set(CurrentUser.builder().userId(7L).username("tester")
                .permissions(Set.of(permissions)).build());
    }

    private static List<List<String>> readSheet(byte[] bytes, String sheetName) throws IOException {
        List<List<String>> table = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(sheetName);
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        cells.add(row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue());
                    }
                }
                table.add(cells);
            }
        }
        return table;
    }

    private byte[] writeSync(List<RowData> rows) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeSync(SCHEMA, rows, output);
        return output.toByteArray();
    }

    @Test
    @DisplayName("表头与 schema 的导出表头完全一致（不多不少、顺序一致）")
    void headerMatchesSchema() throws Exception {
        loginWith("system:user.phone:view");

        List<List<String>> table = readSheet(writeSync(List.of()), "数据");

        assertEquals(List.of("任务标题", "级别", "所属部门", "负责人", "手机号"), table.get(0));
    }

    @Test
    @DisplayName("枚举导出中文含义，未知存储值原样输出（不丢数据）")
    void enumExportsLabel() throws Exception {
        loginWith("system:user.phone:view");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", "URGENT", 2L, 5L, "13800138000"),
                new RowData("任务B", "SOMETHING_NEW", 2L, 5L, "13800138001"))), "数据");

        assertEquals("紧急", table.get(1).get(1));
        assertEquals("SOMETHING_NEW", table.get(2).get(1), "未知枚举值不得被吞掉");
    }

    @Test
    @DisplayName("部门导出完整路径、负责人导出「姓名（部门）」而不是原始 ID")
    void referencesExportDisplayValues() throws Exception {
        loginWith("system:user.phone:view");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", "NORMAL", 2L, 5L, "13800138000"))), "数据");

        assertEquals("总部/研发部", table.get(1).get(2));
        assertEquals("张三（总部/研发部）", table.get(1).get(3));
    }

    @Test
    @DisplayName("有 view 权限 → 掩码字段输出明文")
    void maskedFieldExportsPlainWhenViewAllowed() throws Exception {
        loginWith("system:user.phone:view");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", "NORMAL", 2L, 5L, "13800138000"))), "数据");

        assertEquals("13800138000", table.get(1).get(4));
    }

    @Test
    @DisplayName("只有 mask 权限 → 输出掩码值（列仍存在，但值不可还原）")
    void maskedFieldExportsMaskedWhenOnlyMaskAllowed() throws Exception {
        loginWith("system:user.phone:mask");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", "NORMAL", 2L, 5L, "13800138000"))), "数据");

        assertEquals("138****8000", table.get(1).get(4));
    }

    @Test
    @DisplayName("无任何相关权限 → 该列整列不输出（导出是数据外流的最大出口）")
    void maskedFieldColumnIsOmittedWithoutPermission() throws Exception {
        loginWith("system:user:list");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", "NORMAL", 2L, 5L, "13800138000"))), "数据");

        assertEquals(List.of("任务标题", "级别", "所属部门", "负责人"), table.get(0));
        assertEquals(4, table.get(1).size());
        assertFalse(table.get(0).contains("手机号"));
    }

    @Test
    @DisplayName("空值写空串而不是 \"null\"（下游再导入不会报格式错）")
    void nullValuesBecomeEmptyCells() throws Exception {
        loginWith("system:user.phone:view");

        List<List<String>> table = readSheet(writeSync(List.of(
                new RowData("任务A", null, null, null, null))), "数据");

        assertEquals("", table.get(1).get(1));
        assertFalse(String.valueOf(table.get(1)).contains("null"));
    }

    @Test
    @DisplayName("流式导出：分页取数直到 null，返回总写入行数并回调进度")
    void streamingWritesAllPagesAndReportsProgress() throws Exception {
        loginWith("system:user.phone:view");
        List<List<RowData>> pages = List.of(
                List.of(new RowData("任务A", "NORMAL", 2L, 5L, "13800138000")),
                List.of(new RowData("任务B", "URGENT", 2L, 5L, "13800138001")),
                List.of());
        int[] cursor = {0};
        List<Long> progress = new ArrayList<>();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = writer.writeStreaming(SCHEMA, () -> cursor[0] < pages.size() ? pages.get(cursor[0]++) : null,
                output, progress::add);

        assertEquals(2, written);
        // 一直取到 null 为止：3 次拿到页（含一个空页），第 4 次拿到 null 后停止
        assertEquals(pages.size(), cursor[0], "应一直取到 null 为止，且空页不会被当成结束");
        assertTrue(progress.size() >= 1);
        assertEquals(2L, progress.get(progress.size() - 1));

        List<List<String>> table = readSheet(output.toByteArray(), "数据");
        assertEquals("任务A", table.get(1).get(0));
        assertEquals("任务B", table.get(2).get(0));
    }
}
