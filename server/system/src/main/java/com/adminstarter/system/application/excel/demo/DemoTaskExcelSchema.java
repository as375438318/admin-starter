package com.adminstarter.system.application.excel.demo;

import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.system.application.excel.EnumOption;
import com.adminstarter.system.application.excel.ExcelField;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.application.excel.FieldKind;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 示例任务字段元数据：模板、导入校验、导出表头同源派生。
 * <p>字段规则来源：</p>
 * <ul>
 *   <li>任务标题：DemoTaskDtos.SaveRequest.title（@NotBlank、@Size(max=128)）；V3 建表 title varchar(128)</li>
 *   <li>任务状态：DemoTaskDtos.SaveRequest.status（@Pattern TODO|DOING|DONE）；DemoTaskAppService.normalizeStatus 留空→TODO</li>
 *   <li>归属部门：DemoTaskDtos.SaveRequest.deptId（@NotNull）；DemoTaskAppService.validateDept</li>
 *   <li>负责人：DemoTaskDtos.SaveRequest.ownerId（可空）；DemoTaskAppService.create 留空默认当前登录人</li>
 *   <li>唯一性：V3 仅主键与普通索引，无业务唯一约束</li>
 * </ul>
 * <p>引用列只声明类别与取值器，显示值与存储值的翻译由框架统一完成：
 * 部门存 deptId、显示完整路径；负责人存 ownerId、显示「姓名（部门）」；状态存 TODO/DOING/DONE、显示中文。</p>
 */
@Component
public class DemoTaskExcelSchema implements ExcelSchema<DemoTaskExportRow> {

    public static final String HEADER_TITLE = "任务标题";
    public static final String HEADER_STATUS = "任务状态";
    public static final String HEADER_DEPT = "归属部门";
    public static final String HEADER_OWNER = "负责人";

    public static final List<EnumOption> STATUS_OPTIONS = List.of(
            new EnumOption("TODO", "待处理"),
            new EnumOption("DOING", "进行中"),
            new EnumOption("DONE", "已完成"));

    @Override
    public String key() {
        return "demo-task";
    }

    @Override
    public String title() {
        return "示例任务";
    }

    @Override
    public String templateFileName() {
        return "示例任务导入模板.xlsx";
    }

    @Override
    public String dataSheetName() {
        return "示例任务";
    }

    @Override
    public List<ExcelField<DemoTaskExportRow>> fields() {
        return List.of(
                ExcelField.<DemoTaskExportRow>builder()
                        .key("id")
                        .exportHeader("任务ID")
                        .exporter(row -> row.getId() == null ? "" : String.valueOf(row.getId()))
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("title")
                        .importHeader(HEADER_TITLE)
                        .exportHeader(HEADER_TITLE)
                        .required(true)
                        .maxLength(128)
                        .example("整理需求文档")
                        .exporter(DemoTaskExportRow::getTitle)
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("status")
                        .kind(FieldKind.ENUM)
                        .importHeader(HEADER_STATUS)
                        .exportHeader(HEADER_STATUS)
                        .enums(STATUS_OPTIONS)
                        .refValue(DemoTaskExportRow::getStatus)
                        .formatHint("可填中文或状态值，留空默认「待处理」")
                        .example("待处理")
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("deptId")
                        .kind(FieldKind.REF_DEPT)
                        .importHeader(HEADER_DEPT)
                        .exportHeader(HEADER_DEPT)
                        .required(true)
                        .refValue(DemoTaskExportRow::getDeptId)
                        .formatHint("填部门完整路径，如「总部/研发部/前端组」")
                        .example("总部/研发部")
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("ownerId")
                        .kind(FieldKind.REF_USER)
                        .importHeader(HEADER_OWNER)
                        .exportHeader(HEADER_OWNER)
                        .refValue(DemoTaskExportRow::getOwnerId)
                        .formatHint("填「姓名（部门）」或账号，留空默认当前登录人")
                        .example("张三（总部/研发部）")
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("ownerPhone")
                        .exportHeader("负责人手机号")
                        .maskFieldKey("user:phone")
                        .maskFormat(MaskFormat.PHONE)
                        .exporter(DemoTaskExportRow::getOwnerPhone)
                        .build(),
                ExcelField.<DemoTaskExportRow>builder()
                        .key("createdAt")
                        .exportHeader("创建时间")
                        .exporter(DemoTaskExportRow::getCreatedAt)
                        .build());
    }
}
