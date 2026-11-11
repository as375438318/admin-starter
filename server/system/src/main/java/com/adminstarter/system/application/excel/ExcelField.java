package com.adminstarter.system.application.excel;

import com.adminstarter.common.masking.MaskFormat;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 字段元数据：模板、导入校验、导出表头的唯一字段定义。
 * <p>新增字段只改 schema，禁止在模板/校验/导出任一处另写字段清单。</p>
 */
@Getter
@Builder
public class ExcelField<T> {

    private final String key;
    /** 字段类别；决定显示值与存储值的翻译由框架统一完成。 */
    @Builder.Default
    private final FieldKind kind = FieldKind.TEXT;
    /** 导入表头；null 表示不参与导入。 */
    private final String importHeader;
    /** 导出表头；null 表示不参与导出。 */
    private final String exportHeader;
    private final boolean required;
    /** 导入长度上限；0 表示不限。 */
    private final int maxLength;
    /** 补充格式说明：只写额外规则，不重复「必填/选填」「最多 N 字」「可选值」（这三项由模板生成器按元数据自动拼接）。 */
    private final String formatHint;
    private final String example;
    @Builder.Default
    private final List<EnumOption> enums = List.of();
    private final String uniqueHint;
    /** 文本 → 值；为空时保留文本。 */
    private final Function<String, Object> importer;
    /** 实体 → 文本。 */
    private final Function<T, String> exporter;
    /** 引用/枚举字段的取值器：实体 → 原始值（部门 ID、用户 ID、枚举存储值）；TEXT 不使用。 */
    private final Function<T, Object> refValue;
    /** 非空时走 MaskResolver 三态判定。 */
    private final String maskFieldKey;
    private final MaskFormat maskFormat;

    public String importHeaderWithRequiredMark() {
        return importHeader + (required ? "*" : "");
    }

    public String enumText() {
        return enums.stream()
                .map(option -> option.label() + "(" + option.value() + ")")
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }
}
