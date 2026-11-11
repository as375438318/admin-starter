package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.ExportTask;

import java.time.Instant;
import java.util.List;

public interface ExportTaskRepository {

    void insert(ExportTask task);

    ExportTask findById(Long id);

    void update(ExportTask task);

    /** 条件更新 PENDING → RUNNING；返回 false 表示已被取消或领取。 */
    boolean markRunning(Long id);

    void updateProgress(Long id, int progress);

    boolean markCanceledIfPending(Long id);

    void markCancelRequested(Long id);

    PageResult<ExportTask> pageByUser(Long userId, int page, int pageSize);

    List<ExportTask> findExpiredSuccess(Instant now, int limit);

    List<ExportTask> findUnfinishedBefore(Instant threshold);

    void markExpired(Long id);
}
