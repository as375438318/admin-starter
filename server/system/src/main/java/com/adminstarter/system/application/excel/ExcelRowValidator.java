package com.adminstarter.system.application.excel;

import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用元数据校验：必填、长度、枚举、类型转换、引用字段翻译。所有判断来自 {@link ExcelField}，
 * 业务模块不得再写一份同类校验。
 * <p>引用字段一律整串精确匹配，不解析字符串；匹配不上、多义、越权都进错误清单，由框架负责，不猜。</p>
 */
@Component
public class ExcelRowValidator {

    public Map<String, Object> validate(ExcelSchema<?> schema, RawRow row,
                                        ImportErrorCollector errors, ReferenceContext refs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ExcelField<?> field : schema.importFields()) {
            String raw = row.cells().get(field.getImportHeader());
            String text = raw == null ? "" : raw.trim();
            if (text.isEmpty()) {
                if (field.isRequired()) {
                    errors.add(row.rowNumber(), field.getImportHeader(), "REQUIRED", "不能为空");
                } else {
                    values.put(field.getKey(), null);
                }
                continue;
            }
            if (field.getMaxLength() > 0 && text.length() > field.getMaxLength()) {
                errors.add(row.rowNumber(), field.getImportHeader(), "TOO_LONG",
                        "不能超过 " + field.getMaxLength() + " 字");
                continue;
            }
            if (!field.getEnums().isEmpty()) {
                EnumOption matched = field.getEnums().stream()
                        .filter(option -> option.value().equals(text) || option.label().equals(text))
                        .findFirst().orElse(null);
                if (matched == null) {
                    errors.add(row.rowNumber(), field.getImportHeader(), "INVALID_FORMAT",
                            "只能是 " + field.enumText());
                    continue;
                }
                values.put(field.getKey(), field.getImporter() == null ? matched.value() : field.getImporter().apply(text));
                continue;
            }
            switch (field.getKind()) {
                case REF_DEPT -> resolveDept(field, row, text, values, errors, refs);
                case REF_USER -> resolveUser(field, row, text, values, errors, refs);
                default -> {
                    try {
                        Object value = field.getImporter() == null ? text : field.getImporter().apply(text);
                        values.put(field.getKey(), value);
                    } catch (RuntimeException exception) {
                        errors.add(row.rowNumber(), field.getImportHeader(), "INVALID_FORMAT",
                                field.getFormatHint() == null ? "格式不正确" : field.getFormatHint());
                    }
                }
            }
        }
        return values;
    }

    private void resolveDept(ExcelField<?> field, RawRow row, String text,
                             Map<String, Object> values, ImportErrorCollector errors, ReferenceContext refs) {
        List<Long> ids = refs.depts() == null
                ? List.of()
                : refs.depts().idsByPath().getOrDefault(text, List.of());
        if (ids.isEmpty()) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_NOT_FOUND",
                    "部门不存在，请填部门完整路径（如「总部/研发部/前端组」）");
            return;
        }
        if (ids.size() > 1) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_AMBIGUOUS",
                    "存在多个同名部门（ID " + ids.stream().map(String::valueOf).collect(Collectors.joining("、"))
                            + "），请先清理部门名称");
            return;
        }
        Long deptId = ids.get(0);
        if (!refs.depts().visible(deptId)) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_FORBIDDEN", "无权指定该部门：" + text);
            return;
        }
        values.put(field.getKey(), deptId);
    }

    /**
     * 负责人依次按「姓名（部门）」完整显示文本、账号、姓名匹配；姓名多处重名时拒绝，不猜。
     */
    private void resolveUser(ExcelField<?> field, RawRow row, String text,
                             Map<String, Object> values, ImportErrorCollector errors, ReferenceContext refs) {
        if (refs.users() == null) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_NOT_FOUND", "负责人不存在，请改填账号");
            return;
        }
        List<Long> byDisplay = refs.users().idsByDisplay().getOrDefault(text, List.of());
        if (byDisplay.size() == 1) {
            values.put(field.getKey(), byDisplay.get(0));
            return;
        }
        if (byDisplay.size() > 1) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_AMBIGUOUS", "存在多个同名用户，请改填账号");
            return;
        }
        Long byUsername = refs.users().idByUsername().get(text);
        if (byUsername != null) {
            values.put(field.getKey(), byUsername);
            return;
        }
        List<Long> byNickname = refs.users().idsByNickname().getOrDefault(text, List.of());
        if (byNickname.size() == 1) {
            values.put(field.getKey(), byNickname.get(0));
            return;
        }
        if (byNickname.size() > 1) {
            errors.add(row.rowNumber(), field.getImportHeader(), "REF_AMBIGUOUS", "存在多个同名用户，请改填账号");
            return;
        }
        errors.add(row.rowNumber(), field.getImportHeader(), "REF_NOT_FOUND",
                "负责人不存在，请填「姓名（部门）」或账号");
    }
}
