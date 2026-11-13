package com.adminstarter.system.application.export.module;

import com.adminstarter.system.application.DemoTaskExportAppService;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.ExportModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 示例任务导出：委托既有服务。 */
@Component
@RequiredArgsConstructor
public class DemoTaskExportModule implements ExportModule {

    private final DemoTaskExportAppService demoTaskExportAppService;

    @Override
    public String key() {
        return "demo-task";
    }

    @Override
    public String name() {
        return "示例任务";
    }

    @Override
    public String permission() {
        return "demo:task:export";
    }

    @Override
    public ExcelExportPlanner.ExportPlan plan(Map<String, String> params) {
        String deptId = params.get("deptId");
        return demoTaskExportAppService.plan(params.get("keyword"), params.get("status"),
                deptId == null || deptId.isBlank() ? null : Long.valueOf(deptId.trim()),
                params.get("sort"), params.get("order"), params.get("fields"));
    }
}