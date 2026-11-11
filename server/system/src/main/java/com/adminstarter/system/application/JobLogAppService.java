package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.JobLogDtos;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.query.JobLogQuery;
import com.adminstarter.system.domain.repository.JobLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 执行日志查询：只读，日志由 {@link com.adminstarter.system.application.job.JobExecutor} 写入。
 */
@Service
@RequiredArgsConstructor
public class JobLogAppService {

    private final JobLogRepository jobLogRepository;

    public PageResponse<JobLogDtos.Response> page(PageQuery pageQuery, Long jobId, String keyword,
                                                  String result, String triggerType) {
        JobLogQuery query = new JobLogQuery();
        query.setJobId(jobId);
        query.setKeyword(StringUtils.hasText(keyword) ? keyword.trim() : null);
        query.setResult(StringUtils.hasText(result) ? result.trim().toUpperCase() : null);
        query.setTriggerType(StringUtils.hasText(triggerType) ? triggerType.trim().toUpperCase() : null);
        query.setPage(pageQuery.resolvePage());
        query.setPageSize(pageQuery.resolvePageSize());
        PageResult<JobLog> page = jobLogRepository.page(query);
        List<JobLogDtos.Response> list = page.getList().stream().map(this::toResponse).toList();
        return PageResponse.of(query.getPage(), query.getPageSize(), list, page.getTotal());
    }

    public JobLogDtos.DetailResponse detail(Long id) {
        JobLog log = jobLogRepository.findById(id);
        if (log == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "执行日志不存在");
        }
        JobLogDtos.DetailResponse response = new JobLogDtos.DetailResponse();
        response.setId(log.getId());
        response.setJobId(log.getJobId());
        response.setJobName(log.getJobName());
        response.setJobKey(log.getJobKey());
        response.setTriggerType(log.getTriggerType());
        response.setStartedAt(TimeSupport.toInstant(log.getStartedAt()));
        response.setFinishedAt(TimeSupport.toInstant(log.getFinishedAt()));
        response.setElapsedMs(log.getElapsedMs());
        response.setResult(log.getResult());
        response.setAttemptCount(log.getAttemptCount());
        response.setErrorSummary(log.getErrorSummary());
        response.setErrorStack(log.getErrorStack());
        response.setAffectedRows(log.getAffectedRows());
        return response;
    }

    private JobLogDtos.Response toResponse(JobLog log) {
        JobLogDtos.Response response = new JobLogDtos.Response();
        response.setId(log.getId());
        response.setJobId(log.getJobId());
        response.setJobName(log.getJobName());
        response.setJobKey(log.getJobKey());
        response.setTriggerType(log.getTriggerType());
        response.setStartedAt(TimeSupport.toInstant(log.getStartedAt()));
        response.setFinishedAt(TimeSupport.toInstant(log.getFinishedAt()));
        response.setElapsedMs(log.getElapsedMs());
        response.setResult(log.getResult());
        response.setAttemptCount(log.getAttemptCount());
        response.setErrorSummary(log.getErrorSummary());
        response.setAffectedRows(log.getAffectedRows());
        return response;
    }
}
