package com.adminstarter.system.application.excel.post;

import com.adminstarter.system.application.excel.EnumOption;
import com.adminstarter.system.application.excel.ExcelField;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.application.excel.FieldKind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 岗位字段元数据：模板、导入校验、导出表头同源派生。
 * <p>字段规则来源：</p>
 * <ul>
 *   <li>所属部门：`PostDtos.SaveRequest.deptId` @NotNull；`PostAppService.validateDept` 要求部门存在；
 *       `sys_post.dept_id` NOT NULL</li>
 *   <li>岗位编码：`PostDtos.SaveRequest.code` @NotBlank + @Pattern(^[a-zA-Z0-9_]{2,64}$)；
 *       `PostAppService.checkCodeUnique` 全局唯一；`sys_post.uk_post_code`</li>
 *   <li>岗位名称：`PostDtos.SaveRequest.name` @NotBlank + @Size(max=64)；`sys_post.name` varchar(64)</li>
 *   <li>排序号：`PostDtos.SaveRequest.sort` 可空，`PostAppService.apply` 默认 0；`sys_post.sort` INT</li>
 *   <li>状态：`PostDtos.SaveRequest.isEnabled` 可空，`PostAppService.apply` 默认启用</li>
 * </ul>
 * <p>引用列只声明类别与取值器：部门存 deptId、显示完整路径；状态存 true/false、显示中文。</p>
 */
@Component
public class PostExcelSchema implements ExcelSchema<PostExportRow> {

    public static final String HEADER_DEPT = "所属部门";
    public static final String HEADER_CODE = "岗位编码";
    public static final String HEADER_NAME = "岗位名称";
    public static final String HEADER_SORT = "排序号";
    public static final String HEADER_ENABLED = "状态";

    public static final List<EnumOption> ENABLED_OPTIONS = List.of(
            new EnumOption("true", "启用"),
            new EnumOption("false", "停用"));

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,64}$");

    @Override
    public String key() {
        return "system-post";
    }

    @Override
    public String title() {
        return "岗位";
    }

    @Override
    public String templateFileName() {
        return "岗位导入模板.xlsx";
    }

    @Override
    public String dataSheetName() {
        return "岗位";
    }

    @Override
    public List<ExcelField<PostExportRow>> fields() {
        return List.of(
                ExcelField.<PostExportRow>builder()
                        .key("id")
                        .exportHeader("岗位ID")
                        .exporter(row -> row.getId() == null ? "" : String.valueOf(row.getId()))
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("deptId")
                        .kind(FieldKind.REF_DEPT)
                        .importHeader(HEADER_DEPT)
                        .exportHeader(HEADER_DEPT)
                        .required(true)
                        .refValue(PostExportRow::getDeptId)
                        .formatHint("填部门完整路径，如「总部/研发部」")
                        .example("总部/研发部")
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("code")
                        .importHeader(HEADER_CODE)
                        .exportHeader(HEADER_CODE)
                        .required(true)
                        .maxLength(64)
                        .uniqueHint("全局唯一")
                        .formatHint("2-64 位字母、数字或下划线，且不能与已有岗位重复")
                        .example("dev_lead")
                        .importer(this::parseCode)
                        .exporter(PostExportRow::getCode)
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("name")
                        .importHeader(HEADER_NAME)
                        .exportHeader(HEADER_NAME)
                        .required(true)
                        .maxLength(64)
                        .example("开发组长")
                        .exporter(PostExportRow::getName)
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("sort")
                        .importHeader(HEADER_SORT)
                        .exportHeader(HEADER_SORT)
                        .formatHint("整数，留空默认 0")
                        .example("10")
                        .importer(this::parseSort)
                        .exporter(row -> row.getSort() == null ? "" : String.valueOf(row.getSort()))
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("isEnabled")
                        .kind(FieldKind.ENUM)
                        .importHeader(HEADER_ENABLED)
                        .exportHeader(HEADER_ENABLED)
                        .enums(ENABLED_OPTIONS)
                        .refValue(PostExportRow::getIsEnabled)
                        .formatHint("可填中文或状态值，留空默认「启用」")
                        .example("启用")
                        .build(),
                ExcelField.<PostExportRow>builder()
                        .key("createdAt")
                        .exportHeader("创建时间")
                        .exporter(PostExportRow::getCreatedAt)
                        .build());
    }

    private String parseCode(String text) {
        String code = text == null ? "" : text.trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("岗位编码需为 2-64 位字母、数字或下划线");
        }
        return code;
    }

    private Integer parseSort(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("排序号需为整数");
        }
    }
}
