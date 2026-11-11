package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.PostAppService;
import com.adminstarter.system.application.dto.PostDtos;
import com.adminstarter.system.application.excel.ExcelImportAppService;
import com.adminstarter.system.application.excel.ExcelTemplateAppService;
import com.adminstarter.system.application.excel.ImportOutcome;
import com.adminstarter.system.application.excel.post.PostExcelSchema;
import com.adminstarter.system.application.excel.post.PostExportAppService;
import com.adminstarter.system.application.excel.post.PostImportHandler;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.ExportTaskItem;
import com.adminstarter.system.application.export.ExportTaskService;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PostAppService postAppService;
    private final PostExcelSchema excelSchema;
    private final PostImportHandler importHandler;
    private final ExcelTemplateAppService templateAppService;
    private final ExcelImportAppService importAppService;
    private final PostExportAppService exportAppService;
    private final ExportTaskService exportTaskService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:post:list')")
    public ApiResponse<PageResponse<PostDtos.Response>> page(PageQuery pageQuery,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long deptId,
                                                            @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(postAppService.page(pageQuery, keyword, deptId, isEnabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:post:create')")
    @OperLogRecord(module = "岗位管理", action = "新增岗位")
    public ApiResponse<PostDtos.Response> create(@Valid @RequestBody PostDtos.SaveRequest request) {
        return ApiResponse.ok(postAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:update')")
    @OperLogRecord(module = "岗位管理", action = "修改岗位")
    public ApiResponse<PostDtos.Response> update(@PathVariable Long id,
                                                @Valid @RequestBody PostDtos.SaveRequest request) {
        return ApiResponse.ok(postAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:delete')")
    @OperLogRecord(module = "岗位管理", action = "删除岗位")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postAppService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('system:post:import')")
    @OperLogRecord(module = "岗位管理", action = "下载导入模板")
    public ResponseEntity<byte[]> importTemplate() {
        ExcelTemplateAppService.TemplateFile file = templateAppService.generate(excelSchema);
        return xlsx(file.fileName(), file.content());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('system:post:import')")
    @OperLogRecord(module = "岗位管理", action = "导入岗位")
    public ApiResponse<PostDtos.ImportResult> importFile(@RequestPart("file") MultipartFile file) {
        ImportOutcome outcome = importAppService.importFile(file, importHandler);
        PostDtos.ImportResult result = new PostDtos.ImportResult();
        result.setTotalRows(outcome.totalRows());
        result.setImported(outcome.imported());
        return ApiResponse.ok(result);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:post:export')")
    @OperLogRecord(module = "岗位管理", action = "导出岗位")
    public ResponseEntity<?> export(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long deptId,
                                    @RequestParam(required = false) Boolean isEnabled,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(required = false) String order) {
        ExcelExportPlanner.ExportPlan plan = exportAppService.plan(keyword, deptId, isEnabled, sort, order);
        if (plan.sync()) {
            return xlsx(plan.fileName(), plan.content());
        }
        PostDtos.ExportAccepted accepted = new PostDtos.ExportAccepted();
        accepted.setTaskId(plan.taskId());
        accepted.setTotalRows(plan.totalRows());
        return ResponseEntity.ok(ApiResponse.ok(accepted));
    }

    @GetMapping("/export-tasks")
    @PreAuthorize("hasAuthority('system:post:export')")
    public ApiResponse<PageResponse<ExportTaskItem>> exportTasks(PageQuery pageQuery) {
        CurrentUser current = currentUser();
        PageResult<ExportTaskItem> result = exportTaskService.page(
                current.getUserId(), pageQuery.resolvePage(), pageQuery.resolvePageSize());
        return ApiResponse.ok(PageResponse.of(pageQuery.resolvePage(), pageQuery.resolvePageSize(),
                result.getList(), result.getTotal()));
    }

    @GetMapping("/export-tasks/{id}")
    @PreAuthorize("hasAuthority('system:post:export')")
    public ApiResponse<ExportTaskItem> exportTask(@PathVariable Long id) {
        return ApiResponse.ok(exportTaskService.getOwned(currentUser().getUserId(), id));
    }

    @PostMapping("/export-tasks/{id}/cancel")
    @PreAuthorize("hasAuthority('system:post:export')")
    @OperLogRecord(module = "岗位管理", action = "取消导出任务")
    public ApiResponse<ExportTaskItem> cancelExportTask(@PathVariable Long id) {
        return ApiResponse.ok(exportTaskService.cancel(currentUser().getUserId(), id));
    }

    @GetMapping("/export-tasks/{id}/file")
    @PreAuthorize("hasAuthority('system:post:export')")
    @OperLogRecord(module = "岗位管理", action = "下载导出文件")
    public ResponseEntity<Resource> exportTaskFile(@PathVariable Long id) {
        ExportTaskItem task = exportTaskService.getOwned(currentUser().getUserId(), id);
        Path path = exportTaskService.file(currentUser().getUserId(), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(task.getFileName(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(new FileSystemResource(path), headers, HttpStatus.OK);
    }

    private ResponseEntity<byte[]> xlsx(String fileName, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private CurrentUser currentUser() {
        CurrentUser current = DataScopeContext.get();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return current;
    }
}
