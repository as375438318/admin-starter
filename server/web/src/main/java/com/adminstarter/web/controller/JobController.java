package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.JobAppService;
import com.adminstarter.system.application.dto.JobDtos;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobAppService jobAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:job:list')")
    public ApiResponse<PageResponse<JobDtos.Response>> page(PageQuery pageQuery,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String groupName,
                                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(jobAppService.page(pageQuery, keyword, groupName, status));
    }

    @GetMapping("/keys")
    @PreAuthorize("hasAuthority('system:job:list')")
    public ApiResponse<List<JobDtos.JobKeyOption>> keys() {
        return ApiResponse.ok(jobAppService.keys());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:job:create')")
    @OperLogRecord(module = "定时任务", action = "新增任务")
    public ApiResponse<JobDtos.Response> create(@Valid @RequestBody JobDtos.Request request) {
        return ApiResponse.ok(jobAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:update')")
    @OperLogRecord(module = "定时任务", action = "修改任务")
    public ApiResponse<JobDtos.Response> update(@PathVariable Long id, @Valid @RequestBody JobDtos.Request request) {
        return ApiResponse.ok(jobAppService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:job:status')")
    @OperLogRecord(module = "定时任务", action = "启停任务")
    public ApiResponse<JobDtos.Response> changeStatus(@PathVariable Long id,
                                                      @Valid @RequestBody JobDtos.StatusRequest request) {
        return ApiResponse.ok(jobAppService.changeStatus(id, request.getStatus()));
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('system:job:run')")
    @OperLogRecord(module = "定时任务", action = "手动执行任务")
    public ApiResponse<JobDtos.RunResponse> run(@PathVariable Long id) {
        return ApiResponse.ok(jobAppService.run(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:delete')")
    @OperLogRecord(module = "定时任务", action = "删除任务")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
