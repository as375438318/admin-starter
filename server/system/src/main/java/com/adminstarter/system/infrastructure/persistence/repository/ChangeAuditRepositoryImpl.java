package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.domain.query.ChangeAuditQuery;
import com.adminstarter.system.domain.repository.ChangeAuditRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.ChangeAuditConverter;
import com.adminstarter.system.infrastructure.persistence.entity.ChangeAuditDO;
import com.adminstarter.system.infrastructure.persistence.mapper.ChangeAuditMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ChangeAuditRepositoryImpl implements ChangeAuditRepository {

    /** 白名单解析统一走 SortSupport（禁止裸 get(sort)：Map.of 的 get(null) 会 NPE，断言 #103）。 */
    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "targetType", "target_type",
            "targetKey", "target_key",
            "operatorName", "operator_name",
            "id", "id"
    );

    private final ChangeAuditMapper changeAuditMapper;
    private final ChangeAuditConverter converter;

    @Override
    public void insert(ChangeAudit audit) {
        ChangeAuditDO row = converter.toDO(audit);
        changeAuditMapper.insert(row);
        audit.setId(row.getId());
        audit.setCreatedAt(row.getCreatedAt());
    }

    @Override
    public PageResult<ChangeAudit> page(ChangeAuditQuery query) {
        QueryWrapper<ChangeAuditDO> wrapper = new QueryWrapper<>();
        if (query.getTargetType() != null && !query.getTargetType().isBlank()) {
            wrapper.eq("target_type", query.getTargetType());
        }
        if (query.getTargetKey() != null && !query.getTargetKey().isBlank()) {
            wrapper.eq("target_key", query.getTargetKey());
        }
        if (query.getAction() != null && !query.getAction().isBlank()) {
            wrapper.eq("action", query.getAction());
        }
        if (query.getOperator() != null && !query.getOperator().isBlank()) {
            wrapper.like("operator_name", query.getOperator());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like("target_name", kw).or().like("target_key", kw));
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.NEWEST_FIRST));
        Page<ChangeAuditDO> page = changeAuditMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }
}