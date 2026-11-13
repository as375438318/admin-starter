package com.adminstarter.web.controller;

import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.GenericExportAppService;
import com.adminstarter.system.application.export.ExportModule;
import com.adminstarter.system.application.export.ExportModuleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用导出中心入口（`CR-20261114-043`）。
 *
 * <p>一个 URL 服务所有已注册模块：{@code GET /api/v1/exports/{moduleKey}?fields=…&<模块自己的筛选参数>}。
 * 同步（≤ 2000 行）直接返回 xlsx；超过阈值按既有异步导出基建落任务，返回 JSON（含 taskId），
 * 由前端 `downloadOrJson` 区分。</p>
 *
 * <p>权限按模块查表校验（{@code @exportGuard.allowed}），不因为"走通用入口"就放宽。</p>
 */
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final GenericExportAppService genericExportAppService;
    private final ExportModuleRegistry registry;

    /** 已注册模块清单：前端据此决定哪些列表页显示导出按钮，也方便排障。 */
    @GetMapping("/modules")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> modules() {
        List<Map<String, String>> list = registry.all().values().stream()
                .map(module -> Map.of("key", module.key(), "name", module.name(), "permission", module.permission()))
                .toList();
        return Map.of("code", 0, "message", "ok", "data", list);
    }

    @GetMapping("/{moduleKey}")
    @PreAuthorize("@exportGuard.allowed(#moduleKey)")
    public ResponseEntity<?> export(@PathVariable String moduleKey,
                                    @RequestParam(required = false) Map<String, String> params) {
        ExportModule module = registry.require(moduleKey);
        ExcelExportPlanner.ExportPlan plan = genericExportAppService.plan(moduleKey, new LinkedHashMap<>(params));
        if (!plan.sync()) {
            // 异步受理：返回 JSON（与既有导出接口同形态），前端据此提示"任务已受理"
            return ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", 0, "message", "ok",
                            "data", Map.of("taskId", plan.taskId(), "totalRows", plan.totalRows())));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(plan.fileName(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(plan.content()));
    }
}