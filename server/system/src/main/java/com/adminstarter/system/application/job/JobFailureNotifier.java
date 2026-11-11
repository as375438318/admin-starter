package com.adminstarter.system.application.job;

import com.adminstarter.system.application.NotificationCommand;
import com.adminstarter.system.application.NotificationPublisher;
import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.repository.JobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务失败通知：使用 {@link NotificationPublisher#publishInNewTransaction}（REQUIRES_NEW），
 * 任务是独立执行、无外层事务，通知落库不依赖任务结果。发送失败只记日志，不影响任务日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobFailureNotifier {

    public static final String POLICY_NEVER = "NEVER";
    public static final String POLICY_ON_FAILURE = "ON_FAILURE";
    public static final String POLICY_ON_CONSECUTIVE = "ON_CONSECUTIVE";

    private final NotificationPublisher notificationPublisher;
    private final JobLogRepository jobLogRepository;

    public void notifyIfNeeded(JobDefinition job, JobLog jobLog) {
        if (!JobLog.RESULT_FAIL.equals(jobLog.getResult()) && !JobLog.RESULT_TIMEOUT.equals(jobLog.getResult())) {
            return;
        }
        String policy = job.getNotifyPolicy() == null ? POLICY_NEVER : job.getNotifyPolicy();
        int consecutive = consecutiveFailures(job.getId());
        boolean shouldNotify = switch (policy) {
            case POLICY_ON_FAILURE -> true;
            case POLICY_ON_CONSECUTIVE -> consecutive >= (job.getNotifyThreshold() == null ? 3 : job.getNotifyThreshold());
            case POLICY_NEVER -> false;
            default -> {
                log.warn("任务 {} 的通知策略不识别: {}，本次不发送", job.getId(), policy);
                yield false;
            }
        };
        if (!shouldNotify) {
            return;
        }
        List<Long> targets = resolveTargets(job);
        if (targets.isEmpty()) {
            log.warn("任务 {} 失败但无可用通知接收人（notifyTargets 为空且无创建人），跳过通知", job.getId());
            return;
        }
        try {
            String title = ("定时任务执行失败：" + job.getName());
            if (title.length() > 200) {
                title = title.substring(0, 200);
            }
            String content = """
                    任务：%s（%s）
                    触发方式：%s
                    执行结果：%s
                    执行次数：%d
                    异常摘要：%s
                    连续失败：%d 次
                    """.formatted(job.getName(), job.getJobKey(), jobLog.getTriggerType(), jobLog.getResult(),
                    jobLog.getAttemptCount(), jobLog.getErrorSummary(), consecutive);
            NotificationCommand command = NotificationCommand.builder()
                    .title(title)
                    .content(content)
                    .level(POLICY_ON_CONSECUTIVE.equals(policy) ? "URGENT" : "IMPORTANT")
                    .type("TASK")
                    .revocable(false)
                    .scopes(targets.stream().map(NotificationCommand.Scope::ofUser).toList())
                    .build();
            Long noticeId = notificationPublisher.publishInNewTransaction(command);
            log.info("任务 {} 失败通知已发送，noticeId={}", job.getId(), noticeId);
        } catch (Throwable e) {
            log.error("任务 {} 失败通知发送失败: {}", job.getId(), e.getMessage(), e);
        }
    }

    /** 从最近日志倒序统计连续失败次数（含本次）；SUCCESS / SKIPPED 均视为中断。 */
    private int consecutiveFailures(Long jobId) {
        List<String> results = jobLogRepository.recentResults(jobId, 20);
        int count = 0;
        for (String result : results) {
            if (JobLog.RESULT_FAIL.equals(result) || JobLog.RESULT_TIMEOUT.equals(result)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private List<Long> resolveTargets(JobDefinition job) {
        List<Long> targets = new ArrayList<>();
        if (StringUtils.hasText(job.getNotifyTargets())) {
            for (String part : job.getNotifyTargets().split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    targets.add(Long.valueOf(trimmed));
                } catch (NumberFormatException e) {
                    log.warn("任务 {} 的通知接收人不是合法用户 id: {}", job.getId(), trimmed);
                }
            }
        }
        if (targets.isEmpty() && job.getCreateBy() != null) {
            targets.add(job.getCreateBy());
        }
        return targets;
    }
}
