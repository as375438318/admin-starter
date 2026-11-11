package com.adminstarter.system.application.job;

import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.repository.JobLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 日志落库器：独立事务（REQUIRES_NEW），任务执行失败或通知失败都不影响日志提交。
 */
@Component
@RequiredArgsConstructor
public class JobLogAppender {

    private final JobLogRepository jobLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(JobLog log) {
        jobLogRepository.insert(log);
    }
}
