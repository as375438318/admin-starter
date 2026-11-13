package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.ExportTask;
import com.adminstarter.system.domain.model.ExportTaskStatus;
import com.adminstarter.system.domain.repository.ExportTaskRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.ExportTaskConverter;
import com.adminstarter.system.infrastructure.persistence.entity.ExportTaskDO;
import com.adminstarter.system.infrastructure.persistence.mapper.ExportTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ExportTaskRepositoryImpl implements ExportTaskRepository {
    /** 可排序字段白名单（camelCase → 列名）：我的导出任务 */
    private static final java.util.Map<String, String> SORTS = java.util.Map.of(
            "createdAt", "created_at",
            "status", "status",
            "id", "id");

    private final ExportTaskMapper exportTaskMapper;
    private final ExportTaskConverter converter;

    @Override
    public void insert(ExportTask task) {
        ExportTaskDO row = converter.toDO(task);
        exportTaskMapper.insert(row);
        task.setId(row.getId());
        task.setCreatedAt(row.getCreatedAt());
        task.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public ExportTask findById(Long id) {
        return converter.toDomain(exportTaskMapper.selectById(id));
    }

    @Override
    public void update(ExportTask task) {
        exportTaskMapper.updateById(converter.toDO(task));
    }

    @Override
    public boolean markRunning(Long id) {
        return exportTaskMapper.update(null, new UpdateWrapper<ExportTaskDO>()
                .eq("id", id)
                .eq("status", ExportTaskStatus.PENDING.name())
                .set("status", ExportTaskStatus.RUNNING.name())
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC))) > 0;
    }

    @Override
    public void updateProgress(Long id, int progress) {
        exportTaskMapper.update(null, new UpdateWrapper<ExportTaskDO>()
                .eq("id", id)
                .eq("status", ExportTaskStatus.RUNNING.name())
                .set("progress", progress)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public boolean markCanceledIfPending(Long id) {
        return exportTaskMapper.update(null, new UpdateWrapper<ExportTaskDO>()
                .eq("id", id)
                .eq("status", ExportTaskStatus.PENDING.name())
                .set("status", ExportTaskStatus.CANCELED.name())
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC))) > 0;
    }

    @Override
    public void markCancelRequested(Long id) {
        exportTaskMapper.update(null, new UpdateWrapper<ExportTaskDO>()
                .eq("id", id)
                .in("status", ExportTaskStatus.PENDING.name(), ExportTaskStatus.RUNNING.name())
                .set("cancel_requested", true)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public PageResult<ExportTask> pageByUser(Long userId, int page, int pageSize, String sort, String order) {
        QueryWrapper<ExportTaskDO> wrapper = new QueryWrapper<ExportTaskDO>().eq("user_id", userId);
        // 可排序字段白名单（见 sort-whitelist.ts 的 exportTask 段）；白名单外回落默认排序
        // 注意：Map.of 是可变 Map 的不可变实现，**get(null) 会抛 NPE**，必须先判空（见断言 #103）
        String column = sort == null ? null : SORTS.get(sort);
        if (column == null) {
            wrapper.orderByDesc("created_at", "id");
        } else {
            SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(order, SortSupport.NEWEST_FIRST));
        }
        Page<ExportTaskDO> result = exportTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(converter::toDomain).toList(), result.getTotal());
    }

    @Override
    public List<ExportTask> findExpiredSuccess(Instant now, int limit) {
        return exportTaskMapper.selectList(new QueryWrapper<ExportTaskDO>()
                        .eq("status", ExportTaskStatus.SUCCESS.name())
                        .le("expires_at", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                        .last("limit " + limit))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public List<ExportTask> findUnfinishedBefore(Instant threshold) {
        return exportTaskMapper.selectList(new QueryWrapper<ExportTaskDO>()
                        .in("status", ExportTaskStatus.PENDING.name(), ExportTaskStatus.RUNNING.name())
                        .le("updated_at", LocalDateTime.ofInstant(threshold, ZoneOffset.UTC)))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void markExpired(Long id) {
        exportTaskMapper.update(null, new UpdateWrapper<ExportTaskDO>()
                .eq("id", id)
                .eq("status", ExportTaskStatus.SUCCESS.name())
                .set("status", ExportTaskStatus.EXPIRED.name())
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }
}
