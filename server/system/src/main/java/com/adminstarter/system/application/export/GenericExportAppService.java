package com.adminstarter.system.application.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通用导出入口：按模块键分发。
 *
 * <p>这一层刻意很薄——它只做"找模块、交给模块规划"，公共的同步/异步、列裁剪、任务记录仍在
 * {@link ExcelExportPlanner} 与既有导出基建里。加一个可导出模块＝注册一个 {@link ExportModule}，
 * 不改这里。</p>
 */
@Service
@RequiredArgsConstructor
public class GenericExportAppService {

    private final ExportModuleRegistry registry;

    public ExcelExportPlanner.ExportPlan plan(String moduleKey, Map<String, String> params) {
        return registry.require(moduleKey).plan(params);
    }

    public ExportModule module(String moduleKey) {
        return registry.require(moduleKey);
    }
}