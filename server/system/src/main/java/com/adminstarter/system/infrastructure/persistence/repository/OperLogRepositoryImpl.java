package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.system.domain.query.OperLogQuery;
import com.adminstarter.system.domain.repository.OperLogRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.LogConverter;
import com.adminstarter.system.infrastructure.persistence.entity.OperLogDO;
import com.adminstarter.system.infrastructure.persistence.mapper.OperLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class OperLogRepositoryImpl implements OperLogRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "elapsedMs", "elapsed_ms",
            "id", "id"
    );

    private final OperLogMapper operLogMapper;
    private final LogConverter converter;

    @Override
    public void insert(OperLog operLog) {
        OperLogDO row = converter.toDO(operLog);
        operLogMapper.insert(row);
        operLog.setId(row.getId());
        operLog.setCreatedAt(row.getCreatedAt());
    }

    @Override
    public PageResult<OperLog> page(OperLogQuery query) {
        QueryWrapper<OperLogDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("username", query.getKeyword())
                    .or().like("module", query.getKeyword())
                    .or().like("action", query.getKeyword()));
        }
        if (query.getResultCode() != null) {
            wrapper.eq("result_code", query.getResultCode());
        }
        if (query.getFrom() != null) {
            wrapper.ge("created_at", query.getFrom());
        }
        if (query.getTo() != null) {
            wrapper.le("created_at", query.getTo());
        }

        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column);
        Page<OperLogDO> page = operLogMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }
}
