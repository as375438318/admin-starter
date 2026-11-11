package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.JobLogAppService;
import com.adminstarter.system.application.dto.JobLogDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-logs")
@RequiredArgsConstructor
public class JobLogController {

    private final JobLogAppService jobLogAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:job:log')")
    public ApiResponse<PageResponse<JobLogDtos.Response>> page(PageQuery pageQuery,
                                                               @RequestParam(required = false) Long jobId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String result,
                                                               @RequestParam(required = false) String triggerType) {
        return ApiResponse.ok(jobLogAppService.page(pageQuery, jobId, keyword, result, triggerType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:log')")
    public ApiResponse<JobLogDtos.DetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(jobLogAppService.detail(id));
    }
}
