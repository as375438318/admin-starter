package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.query.JobLogQuery;
import com.adminstarter.system.domain.repository.JobLogRepository;
import com.adminstarter.system.infrastructure.persistence.converter.JobLogConverter;
import com.adminstarter.system.infrastructure.persistence.entity.JobLogDO;
import com.adminstarter.system.infrastructure.persistence.mapper.JobLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JobLogRepositoryImpl implements JobLogRepository {

    private final JobLogMapper jobLogMapper;
    private final JobLogConverter converter;

    @Override
    public void insert(JobLog log) {
        JobLogDO row = converter.toDO(log);
        jobLogMapper.insert(row);
        log.setId(row.getId());
        log.setCreatedAt(row.getCreatedAt());
    }

    @Override
    public JobLog findById(Long id) {
        return converter.toDomain(jobLogMapper.selectById(id));
    }

    @Override
    public PageResult<JobLog> page(JobLogQuery query) {
        QueryWrapper<JobLogDO> wrapper = new QueryWrapper<>();
        if (query.getJobId() != null) {
            wrapper.eq("job_id", query.getJobId());
        }
        if (query.getResult() != null && !query.getResult().isBlank()) {
            wrapper.eq("result", query.getResult());
        }
        if (query.getTriggerType() != null && !query.getTriggerType().isBlank()) {
            wrapper.eq("trigger_type", query.getTriggerType());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("job_name", query.getKeyword()).or().like("job_key", query.getKeyword()));
        }
        wrapper.orderByDesc("started_at").orderByDesc("id");
        IPage<JobLogDO> page = jobLogMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public List<String> recentResults(Long jobId, int limit) {
        return jobLogMapper.selectRecentResults(jobId, limit);
    }
}
