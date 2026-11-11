package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.query.JobQuery;

import java.time.LocalDateTime;
import java.util.List;

public interface JobRepository {

    void insert(JobDefinition job);

    void update(JobDefinition job);

    JobDefinition findById(Long id);

    PageResult<JobDefinition> page(JobQuery query);

    List<JobDefinition> findEnabled();

    boolean existsByKey(String jobKey, Long excludeId);

    void softDelete(Long id);

    /** 更新下次执行时间；null 表示停用/取消调度。 */
    void updateNextRunAt(Long id, LocalDateTime nextRunAt);

    /** 更新最近一次执行结果。 */
    void updateLastRun(Long id, LocalDateTime lastRunAt, String lastRunResult);
}
