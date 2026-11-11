package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.JobDtos;
import com.adminstarter.system.application.job.JobExecutor;
import com.adminstarter.system.application.job.JobRegistry;
import com.adminstarter.system.application.job.JobScheduler;
import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.query.JobQuery;
import com.adminstarter.system.domain.repository.JobRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 定时任务管理：定义、启停、手动执行、注册校验。
 */
@Service
@RequiredArgsConstructor
public class JobAppService {

    private static final Set<String> STATUSES = Set.of(JobDefinition.STATUS_ENABLED, JobDefinition.STATUS_DISABLED);
    private static final Set<String> CONCURRENCY = Set.of(JobExecutor.CONCURRENCY_DISALLOW, JobExecutor.CONCURRENCY_ALLOW);
    private static final Set<String> NOTIFY_POLICIES = Set.of(
            "NEVER", "ON_FAILURE", "ON_CONSECUTIVE");
    private static final String DEFAULT_GROUP = "DEFAULT";

    private final JobRepository jobRepository;
    private final JobScheduler jobScheduler;
    private final JobExecutor jobExecutor;
    private final JobRegistry jobRegistry;
    private final UserRepository userRepository;

    public PageResponse<JobDtos.Response> page(PageQuery pageQuery, String keyword, String groupName, String status) {
        JobQuery query = new JobQuery();
        query.setKeyword(blankToNull(keyword));
        query.setGroupName(blankToNull(groupName));
        query.setStatus(blankToNull(status));
        query.setPage(pageQuery.resolvePage());
        query.setPageSize(pageQuery.resolvePageSize());
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<JobDefinition> result = jobRepository.page(query);
        List<JobDtos.Response> list = result.getList().stream().map(this::toResponse).toList();
        return PageResponse.of(query.getPage(), query.getPageSize(), list, result.getTotal());
    }

    public List<JobDtos.JobKeyOption> keys() {
        return jobRegistry.list().stream()
                .map(job -> new JobDtos.JobKeyOption(job.key(), job.description()))
                .toList();
    }

    @Transactional
    public JobDtos.Response create(JobDtos.Request request) {
        JobDefinition job = new JobDefinition();
        applyRequest(job, request, null);
        CurrentUser current = DataScopeContext.get();
        job.setCreateBy(current == null ? null : current.getUserId());
        jobRepository.insert(job);
        jobScheduler.refresh(job);
        return toResponse(job);
    }

    @Transactional
    public JobDtos.Response update(Long id, JobDtos.Request request) {
        JobDefinition job = require(id);
        applyRequest(job, request, id);
        jobRepository.update(job);
        jobScheduler.refresh(job);
        return toResponse(job);
    }

    @Transactional
    public JobDtos.Response changeStatus(Long id, String status) {
        JobDefinition job = require(id);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务状态不合法");
        }
        job.setStatus(normalized);
        jobRepository.update(job);
        jobScheduler.refresh(job);
        return toResponse(job);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        jobScheduler.cancel(id);
        jobRepository.softDelete(id);
    }

    /** 手动执行一次：同步等待结果；并发策略为禁止并发且正在执行时返回 409。 */
    public JobDtos.RunResponse run(Long id) {
        JobDefinition job = require(id);
        if (!jobRegistry.contains(job.getJobKey())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务标识未注册: " + job.getJobKey());
        }
        JobLog log = jobExecutor.executeById(id, JobLog.TRIGGER_MANUAL);
        if (log == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "任务已被删除，无法执行");
        }
        if (JobLog.RESULT_SKIPPED.equals(log.getResult())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "任务正在执行中，禁止并发策略已跳过本次执行");
        }
        JobDtos.RunResponse response = new JobDtos.RunResponse();
        response.setLogId(log.getId());
        response.setResult(log.getResult());
        response.setElapsedMs(log.getElapsedMs());
        response.setAttemptCount(log.getAttemptCount());
        response.setAffectedRows(log.getAffectedRows());
        response.setErrorSummary(log.getErrorSummary());
        response.setStartedAt(TimeSupport.toInstant(log.getStartedAt()));
        response.setFinishedAt(TimeSupport.toInstant(log.getFinishedAt()));
        return response;
    }

    private void applyRequest(JobDefinition job, JobDtos.Request request, Long excludeId) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务名称不能为空");
        }
        String jobKey = request.getJobKey() == null ? "" : request.getJobKey().trim();
        if (!jobRegistry.contains(jobKey)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务标识未注册: " + jobKey);
        }
        if (jobRepository.existsByKey(jobKey, excludeId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务标识已存在: " + jobKey);
        }
        String cron = request.getCronExpression() == null ? "" : request.getCronExpression().trim();
        validateCron(cron);
        String status = request.getStatus() == null ? JobDefinition.STATUS_DISABLED : request.getStatus().trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务状态不合法");
        }
        String concurrency = request.getConcurrencyPolicy() == null
                ? JobExecutor.CONCURRENCY_DISALLOW : request.getConcurrencyPolicy().trim().toUpperCase();
        if (!CONCURRENCY.contains(concurrency)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "并发策略不合法");
        }
        String notifyPolicy = request.getNotifyPolicy() == null ? "NEVER" : request.getNotifyPolicy().trim().toUpperCase();
        if (!NOTIFY_POLICIES.contains(notifyPolicy)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "通知策略不合法");
        }
        int timeout = intOrDefault(request.getTimeoutSeconds(), 60);
        int retryTimes = intOrDefault(request.getRetryTimes(), 0);
        int retryInterval = intOrDefault(request.getRetryIntervalSeconds(), 10);
        int notifyThreshold = intOrDefault(request.getNotifyThreshold(), 3);
        if (timeout < 1 || timeout > 3600) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "超时时间必须在 1–3600 秒");
        }
        if (retryTimes < 0 || retryTimes > 10) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "重试次数必须在 0–10");
        }
        if (retryInterval < 0 || retryInterval > 3600) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "重试间隔必须在 0–3600 秒");
        }
        if (notifyThreshold < 1 || notifyThreshold > 10) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "连续失败阈值必须在 1–10");
        }
        String targets = normalizeTargets(request.getNotifyTargets());

        job.setName(name);
        job.setGroupName(StringUtils.hasText(request.getGroupName()) ? request.getGroupName().trim() : DEFAULT_GROUP);
        job.setJobKey(jobKey);
        job.setCronExpression(cron);
        job.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        job.setStatus(status);
        job.setConcurrencyPolicy(concurrency);
        job.setTimeoutSeconds(timeout);
        job.setRetryTimes(retryTimes);
        job.setRetryIntervalSeconds(retryInterval);
        job.setNotifyPolicy(notifyPolicy);
        job.setNotifyThreshold(notifyThreshold);
        job.setNotifyTargets(targets);
    }

    private void validateCron(String cron) {
        if (cron.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "cron 表达式不能为空");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "cron 表达式不合法，需 Spring 6 段（秒 分 时 日 月 周）: " + e.getMessage());
        }
    }

    /** 接收人：逗号分隔的用户 id；去重并校验用户存在且启用。为空表示任务创建人。 */
    private String normalizeTargets(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "通知接收人必须是用户 id: " + trimmed);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        List<User> users = userRepository.findByIds(ids);
        if (users.size() != ids.size()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "通知接收人不存在或已删除");
        }
        for (User user : users) {
            if (!Boolean.TRUE.equals(user.getIsEnabled())) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "通知接收人已停用: " + user.getId());
            }
        }
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private JobDefinition require(Long id) {
        JobDefinition job = jobRepository.findById(id);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "定时任务不存在");
        }
        return job;
    }

    private JobDtos.Response toResponse(JobDefinition job) {
        JobDtos.Response response = new JobDtos.Response();
        response.setId(job.getId());
        response.setName(job.getName());
        response.setGroupName(job.getGroupName());
        response.setJobKey(job.getJobKey());
        response.setCronExpression(job.getCronExpression());
        response.setDescription(job.getDescription());
        response.setStatus(job.getStatus());
        response.setConcurrencyPolicy(job.getConcurrencyPolicy());
        response.setTimeoutSeconds(job.getTimeoutSeconds());
        response.setRetryTimes(job.getRetryTimes());
        response.setRetryIntervalSeconds(job.getRetryIntervalSeconds());
        response.setNotifyPolicy(job.getNotifyPolicy());
        response.setNotifyThreshold(job.getNotifyThreshold());
        response.setNotifyTargets(job.getNotifyTargets());
        response.setLastRunAt(TimeSupport.toInstant(job.getLastRunAt()));
        response.setLastRunResult(job.getLastRunResult());
        response.setNextRunAt(TimeSupport.toInstant(jobScheduler.nextRunAt(job)));
        response.setRunning(jobScheduler.isRunning(job.getId()));
        response.setRegistered(jobRegistry.contains(job.getJobKey()));
        response.setCreatedBy(job.getCreateBy());
        response.setCreatedAt(TimeSupport.toInstant(job.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(job.getUpdatedAt()));
        return response;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int intOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
