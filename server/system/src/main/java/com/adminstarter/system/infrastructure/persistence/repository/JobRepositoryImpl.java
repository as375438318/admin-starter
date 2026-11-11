package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.query.JobQuery;
import com.adminstarter.system.domain.repository.JobRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.JobConverter;
import com.adminstarter.system.infrastructure.persistence.entity.JobDO;
import com.adminstarter.system.infrastructure.persistence.mapper.JobMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "nextRunAt", "next_run_at",
            "id", "id"
    );

    private final JobMapper jobMapper;
    private final JobConverter converter;

    @Override
    public void insert(JobDefinition job) {
        JobDO row = converter.toDO(job);
        jobMapper.insert(row);
        job.setId(row.getId());
        job.setCreatedAt(row.getCreatedAt());
        job.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(JobDefinition job) {
        jobMapper.updateById(converter.toDO(job));
    }

    @Override
    public JobDefinition findById(Long id) {
        return converter.toDomain(jobMapper.selectById(id));
    }

    @Override
    public PageResult<JobDefinition> page(JobQuery query) {
        QueryWrapper<JobDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("job_key", query.getKeyword()));
        }
        if (query.getGroupName() != null && !query.getGroupName().isBlank()) {
            wrapper.eq("group_name", query.getGroupName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column).orderByDesc("id");
        IPage<JobDO> page = jobMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public List<JobDefinition> findEnabled() {
        QueryWrapper<JobDO> wrapper = new QueryWrapper<JobDO>()
                .eq("status", JobDefinition.STATUS_ENABLED)
                .orderByAsc("id");
        return jobMapper.selectList(wrapper).stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean existsByKey(String jobKey, Long excludeId) {
        QueryWrapper<JobDO> wrapper = new QueryWrapper<JobDO>().eq("job_key", jobKey);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return jobMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void softDelete(Long id) {
        jobMapper.deleteById(id);
    }

    @Override
    public void updateNextRunAt(Long id, LocalDateTime nextRunAt) {
        jobMapper.update(null, new UpdateWrapper<JobDO>()
                .eq("id", id)
                .set("next_run_at", nextRunAt)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public void updateLastRun(Long id, LocalDateTime lastRunAt, String lastRunResult) {
        jobMapper.update(null, new UpdateWrapper<JobDO>()
                .eq("id", id)
                .set("last_run_at", lastRunAt)
                .set("last_run_result", lastRunResult)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }
}
