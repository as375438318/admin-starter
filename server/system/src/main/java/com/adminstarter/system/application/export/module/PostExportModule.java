package com.adminstarter.system.application.export.module;

import com.adminstarter.system.application.excel.post.PostExportAppService;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.ExportModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 岗位导出：委托既有服务，复用同一套列裁剪与任务记录。 */
@Component
@RequiredArgsConstructor
public class PostExportModule implements ExportModule {

    private final PostExportAppService postExportAppService;

    @Override
    public String key() {
        return "post";
    }

    @Override
    public String name() {
        return "岗位";
    }

    @Override
    public String permission() {
        return "system:post:export";
    }

    @Override
    public ExcelExportPlanner.ExportPlan plan(Map<String, String> params) {
        return postExportAppService.plan(params.get("keyword"), parseLong(params.get("deptId")),
                parseBoolean(params.get("isEnabled")), params.get("sort"), params.get("order"),
                params.get("fields"));
    }

    private Long parseLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
    }

    private Boolean parseBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.valueOf(value.trim());
    }
}