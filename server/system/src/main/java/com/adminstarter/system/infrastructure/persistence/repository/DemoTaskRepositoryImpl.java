package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.query.DemoTaskQuery;
import com.adminstarter.system.domain.repository.DemoTaskRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.DemoTaskConverter;
import com.adminstarter.system.infrastructure.persistence.entity.DemoTaskDO;
import com.adminstarter.system.infrastructure.persistence.mapper.DemoTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DemoTaskRepositoryImpl implements DemoTaskRepository {

    /**
     * 单条多行 INSERT 的行数上限。取值依据见 docs/audit/perf-2w-2026-11-10.md 的批次对比。
     */
    private static final int BATCH_SIZE = 1000;

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "title", "title",
            "status", "status",
            "id", "id"
    );

    private final DemoTaskMapper demoTaskMapper;
    private final DemoTaskConverter converter;

    @Override
    public DemoTask findById(Long id) {
        return converter.toDomain(demoTaskMapper.selectById(id));
    }

    @Override
    public void insert(DemoTask task) {
        DemoTaskDO row = converter.toDO(task);
        demoTaskMapper.insert(row);
        task.setId(row.getId());
        task.setCreatedAt(row.getCreatedAt());
        task.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void insertBatch(List<DemoTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        // 多行 INSERT 不经过 MetaObjectHandler，公共时间字段在这里显式填充（与单条插入同口径：UTC）。
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<DemoTaskDO> rows = new ArrayList<>(tasks.size());
        for (DemoTask task : tasks) {
            DemoTaskDO row = converter.toDO(task);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            demoTaskMapper.insertBatch(rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
    }

    @Override
    public void update(DemoTask task) {
        demoTaskMapper.updateById(converter.toDO(task));
    }

    @Override
    public void deleteById(Long id) {
        demoTaskMapper.deleteById(id);
    }

    @Override
    public PageResult<DemoTask> page(DemoTaskQuery query) {
        QueryWrapper<DemoTaskDO> wrapper = wrapper(query);
        Page<DemoTaskDO> page = demoTaskMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public long count(DemoTaskQuery query) {
        return demoTaskMapper.selectCount(wrapper(query));
    }

    @Override
    public List<DemoTask> findForExport(DemoTaskQuery query, int offset, int limit) {
        QueryWrapper<DemoTaskDO> wrapper = wrapper(query);
        wrapper.last("limit " + limit + " offset " + offset);
        return demoTaskMapper.selectList(wrapper).stream().map(converter::toDomain).toList();
    }

    private QueryWrapper<DemoTaskDO> wrapper(DemoTaskQuery query) {
        QueryWrapper<DemoTaskDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like("title", query.getKeyword());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.eq("dept_id", query.getDeptId());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column);
        return wrapper;
    }
}
