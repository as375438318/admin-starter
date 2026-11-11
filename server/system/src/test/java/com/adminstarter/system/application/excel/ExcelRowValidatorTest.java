package com.adminstarter.system.application.excel;

import com.adminstarter.system.application.excel.ReferenceDirectoryService.DeptDirectory;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.UserDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入行校验：必填、长度、枚举、类型转换、引用字段反查。
 * <p>导入是最容易把脏数据写进库的入口，规则也最容易和「导出/模板」漂移——所以这里锁的是
 * <b>验证规则全部来自 {@link ExcelField} 元数据</b>，以及「匹配不上/多义/越权一律进错误清单，不猜」。</p>
 */
class ExcelRowValidatorTest {

    private final ExcelRowValidator validator = new ExcelRowValidator();

    // ---------------------------------------------------------------- 测试用 schema（模拟真实模块的字段元数据）

    private static ExcelField<Object> field(String key, String header, FieldKind kind) {
        return ExcelField.builder().key(key).kind(kind).importHeader(header).build();
    }

    private static final ExcelField<Object> TITLE = ExcelField.<Object>builder()
            .key("title").kind(FieldKind.TEXT).importHeader("任务标题")
            .required(true).maxLength(5).build();

    private static final ExcelField<Object> LEVEL = ExcelField.<Object>builder()
            .key("level").kind(FieldKind.ENUM).importHeader("级别")
            .enums(List.of(new EnumOption("NORMAL", "普通"), new EnumOption("URGENT", "紧急")))
            .build();

    private static final ExcelField<Object> AMOUNT = ExcelField.<Object>builder()
            .key("amount").kind(FieldKind.TEXT).importHeader("金额")
            .formatHint("只能是整数").importer(text -> Integer.valueOf(text)).build();

    private static final ExcelField<Object> DEPT = field("deptId", "所属部门", FieldKind.REF_DEPT);
    private static final ExcelField<Object> OWNER = field("ownerId", "负责人", FieldKind.REF_USER);

    private static final ExcelSchema<Object> SCHEMA = new ExcelSchema<>() {
        @Override
        public String key() {
            return "test";
        }

        @Override
        public String title() {
            return "测试导入";
        }

        @Override
        public String templateFileName() {
            return "测试导入模板.xlsx";
        }

        @Override
        public String dataSheetName() {
            return "数据";
        }

        @Override
        public List<ExcelField<Object>> fields() {
            return List.of(TITLE, LEVEL, AMOUNT, DEPT, OWNER);
        }
    };

    private static RawRow row(int rowNumber, String title, String level, String amount, String dept, String owner) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("任务标题", title);
        cells.put("级别", level);
        cells.put("金额", amount);
        cells.put("所属部门", dept);
        cells.put("负责人", owner);
        return new RawRow(rowNumber, cells);
    }

    /** 部门目录：总部/研发部/前端组；其中「重复部」有两同名记录；可见范围只含 1、2、3。 */
    private static ReferenceContext fullRefs() {
        Map<Long, String> pathById = Map.of(1L, "总部", 2L, "总部/研发部", 3L, "总部/研发部/前端组");
        Map<String, List<Long>> idsByPath = new LinkedHashMap<>();
        idsByPath.put("总部", List.of(1L));
        idsByPath.put("总部/研发部", List.of(2L));
        idsByPath.put("总部/研发部/前端组", List.of(3L));
        idsByPath.put("重复部", List.of(8L, 9L));
        DeptDirectory depts = new DeptDirectory(pathById, idsByPath, Set.of(1L, 2L, 3L));

        Map<Long, String> displayById = Map.of(5L, "张三（总部/研发部）", 6L, "李四（总部）");
        Map<String, List<Long>> idsByDisplay = new LinkedHashMap<>();
        idsByDisplay.put("张三（总部/研发部）", List.of(5L));
        idsByDisplay.put("李四（总部）", List.of(6L));
        Map<String, Long> idByUsername = Map.of("zhangsan", 5L, "lisi", 6L);
        Map<String, List<Long>> idsByNickname = new LinkedHashMap<>();
        idsByNickname.put("张三", List.of(5L));
        idsByNickname.put("李四", List.of(6L));
        idsByNickname.put("同名", List.of(6L, 7L));
        UserDirectory users = new UserDirectory(displayById, idsByDisplay, idByUsername, idsByNickname, Set.of(5L, 6L));
        return new ReferenceContext(depts, users);
    }

    private List<String> codes(ImportErrorCollector collector) {
        List<String> codes = new ArrayList<>();
        collector.toData(1).getErrors().forEach(error -> codes.add(error.getCode()));
        return codes;
    }

    // ---------------------------------------------------------------- 必填 / 长度 / 裁剪

    @Test
    @DisplayName("必填为空 → REQUIRED，其它字段仍继续校验（一次列出全部问题）")
    void requiredFieldMissing() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "  ", "NORMAL", null, null, null),
                errors, fullRefs());

        assertTrue(codes(errors).contains("REQUIRED"));
        assertNull(values.get("title"));
        assertEquals("NORMAL", values.get("level"), "其余合法字段仍应产出，不因一个字段错就整行丢弃");
    }

    @Test
    @DisplayName("选填为空 → 值为 null 且不报错")
    void optionalBlankProducesNullWithoutError() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "任务", null, null, null, null),
                errors, fullRefs());

        assertFalse(errors.hasErrors());
        assertNull(values.get("level"));
        assertNull(values.get("amount"));
    }

    @Test
    @DisplayName("超过长度上限 → TOO_LONG，且不写入该字段值")
    void tooLongValueIsRejected() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "超过五个字的标题", null, null, null, null),
                errors, fullRefs());

        assertTrue(codes(errors).contains("TOO_LONG"));
        assertNull(values.get("title"));
    }

    @Test
    @DisplayName("单元格首尾空格被裁剪后再校验与落值")
    void valueIsTrimmed() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "  任务  ", " NORMAL ", null, null, null),
                errors, fullRefs());

        assertFalse(errors.hasErrors());
        assertEquals("任务", values.get("title"));
        assertEquals("NORMAL", values.get("level"));
    }

    // ---------------------------------------------------------------- 枚举 / 类型转换

    @Test
    @DisplayName("枚举接受存储值与中文含义")
    void enumAcceptsValueOrLabel() {
        ImportErrorCollector byValue = new ImportErrorCollector();
        assertEquals("NORMAL", validator.validate(SCHEMA, row(2, "任务", "NORMAL", null, null, null),
                byValue, fullRefs()).get("level"));

        ImportErrorCollector byLabel = new ImportErrorCollector();
        assertEquals("URGENT", validator.validate(SCHEMA, row(2, "任务", "紧急", null, null, null),
                byLabel, fullRefs()).get("level"));
    }

    @Test
    @DisplayName("枚举值非法 → INVALID_FORMAT，提示里列出全部可选值")
    void enumRejectsUnknownValue() {
        ImportErrorCollector errors = new ImportErrorCollector();

        validator.validate(SCHEMA, row(2, "任务", "超紧急", null, null, null), errors, fullRefs());

        assertEquals(List.of("INVALID_FORMAT"), codes(errors));
        String message = errors.toData(1).getErrors().get(0).getMessage();
        assertTrue(message.contains("普通(NORMAL)") && message.contains("紧急(URGENT") || message.contains("紧急(URGENT)"),
                "提示须列出可选值，实际：" + message);
    }

    @Test
    @DisplayName("类型转换失败 → INVALID_FORMAT，文案用 formatHint")
    void conversionFailureUsesHint() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "任务", null, "一百", null, null),
                errors, fullRefs());

        assertEquals(List.of("INVALID_FORMAT"), codes(errors));
        assertEquals("只能是整数", errors.toData(1).getErrors().get(0).getMessage());
        assertNull(values.get("amount"));
    }

    @Test
    @DisplayName("类型转换成功 → 落转换后的值（不是原文本）")
    void conversionSuccessStoresConvertedValue() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "任务", null, "42", null, null),
                errors, fullRefs());

        assertFalse(errors.hasErrors());
        assertEquals(42, values.get("amount"));
    }

    // ---------------------------------------------------------------- 部门引用

    @Test
    @DisplayName("部门按完整路径反查为 ID")
    void deptResolvedByFullPath() {
        ImportErrorCollector errors = new ImportErrorCollector();

        Map<String, Object> values = validator.validate(SCHEMA, row(2, "任务", null, null, "总部/研发部/前端组", null),
                errors, fullRefs());

        assertFalse(errors.hasErrors());
        assertEquals(3L, values.get("deptId"));
    }

    @Test
    @DisplayName("部门路径不存在 → REF_NOT_FOUND，提示要填完整路径")
    void deptNotFound() {
        ImportErrorCollector errors = new ImportErrorCollector();

        validator.validate(SCHEMA, row(2, "任务", null, null, "研发部", null), errors, fullRefs());

        assertEquals(List.of("REF_NOT_FOUND"), codes(errors));
        assertTrue(errors.toData(1).getErrors().get(0).getMessage().contains("完整路径"));
    }

    @Test
    @DisplayName("同名部门多处 → REF_AMBIGUOUS（不猜是哪一个），提示带候选 ID")
    void deptAmbiguous() {
        ImportErrorCollector errors = new ImportErrorCollector();

        validator.validate(SCHEMA, row(2, "任务", null, null, "重复部", null), errors, fullRefs());

        assertEquals(List.of("REF_AMBIGUOUS"), codes(errors));
        String message = errors.toData(1).getErrors().get(0).getMessage();
        assertTrue(message.contains("8") && message.contains("9"), "须列出候选 ID，实际：" + message);
    }

    @Test
    @DisplayName("部门存在但不在数据权限范围 → REF_FORBIDDEN（越权写入比不存在更严重）")
    void deptForbiddenOutsideDataScope() {
        ImportErrorCollector errors = new ImportErrorCollector();
        ReferenceContext restricted = new ReferenceContext(
                new DeptDirectory(Map.of(99L, "外部/合作方"), Map.of("外部/合作方", List.of(99L)), Set.of(1L)),
                null);

        validator.validate(SCHEMA, row(2, "任务", null, null, "外部/合作方", null), errors, restricted);

        assertEquals(List.of("REF_FORBIDDEN"), codes(errors));
    }

    // ---------------------------------------------------------------- 用户引用

    @Test
    @DisplayName("负责人依次按「姓名（部门）」、账号、姓名匹配")
    void ownerResolvedByDisplayThenUsernameThenNickname() {
        assertEquals(5L, validateOwner("张三（总部/研发部）"), "完整显示文本");
        assertEquals(5L, validateOwner("zhangsan"), "账号");
        assertEquals(5L, validateOwner("张三"), "姓名唯一");
    }

    private Object validateOwner(String text) {
        ImportErrorCollector errors = new ImportErrorCollector();
        Map<String, Object> values = validator.validate(SCHEMA, row(2, "任务", null, null, null, text),
                errors, fullRefs());
        assertFalse(errors.hasErrors(), "不应报错：" + text);
        return values.get("ownerId");
    }

    @Test
    @DisplayName("姓名重名 → REF_AMBIGUOUS，提示改填账号")
    void ownerNicknameAmbiguous() {
        ImportErrorCollector errors = new ImportErrorCollector();

        validator.validate(SCHEMA, row(2, "任务", null, null, null, "同名"), errors, fullRefs());

        assertEquals(List.of("REF_AMBIGUOUS"), codes(errors));
        assertTrue(errors.toData(1).getErrors().get(0).getMessage().contains("账号"));
    }

    @Test
    @DisplayName("负责人都匹配不上 → REF_NOT_FOUND")
    void ownerNotFound() {
        ImportErrorCollector errors = new ImportErrorCollector();

        validator.validate(SCHEMA, row(2, "任务", null, null, null, "查无此人"), errors, fullRefs());

        assertEquals(List.of("REF_NOT_FOUND"), codes(errors));
    }

    @Test
    @DisplayName("无用户目录（如整份文件没有用户引用字段）→ 仍给出可操作提示")
    void ownerWithoutUserDirectory() {
        ImportErrorCollector errors = new ImportErrorCollector();
        ReferenceContext noUsers = new ReferenceContext(fullRefs().depts(), null);

        validator.validate(SCHEMA, row(2, "任务", null, null, null, "zhangsan"), errors, noUsers);

        assertEquals(List.of("REF_NOT_FOUND"), codes(errors));
        assertTrue(errors.toData(1).getErrors().get(0).getMessage().contains("账号"));
    }
}
