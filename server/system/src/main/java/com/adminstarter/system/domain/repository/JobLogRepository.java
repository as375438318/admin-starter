package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.query.JobLogQuery;

import java.util.List;

public interface JobLogRepository {

    void insert(JobLog log);

    JobLog findById(Long id);

    PageResult<JobLog> page(JobLogQuery query);

    /** 最近若干条执行结果（倒序），用于统计连续失败次数。 */
    List<String> recentResults(Long jobId, int limit);
}
