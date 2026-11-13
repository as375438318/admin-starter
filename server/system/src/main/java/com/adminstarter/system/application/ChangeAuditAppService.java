package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.ChangeAuditDtos;
import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.domain.query.ChangeAuditQuery;
import com.adminstarter.system.domain.repository.ChangeAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 变更审计只读查询：写入一律由 ChangeAuditRecorder 在业务事务内完成。 */
@Service
@RequiredArgsConstructor
public class ChangeAuditAppService {

    private final ChangeAuditRepository changeAuditRepository;

    public PageResponse<ChangeAuditDtos.Response> page(PageQuery pageQuery, String targetType, String targetKey,
                                                       String operator, String action, String keyword) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        ChangeAuditQuery query = new ChangeAuditQuery();
        query.setTargetType(targetType);
        query.setTargetKey(targetKey);
        query.setOperator(operator);
        query.setAction(action);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<ChangeAudit> result = changeAuditRepository.page(query);
        List<ChangeAuditDtos.Response> list = result.getList().stream().map(this::toResponse).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    private ChangeAuditDtos.Response toResponse(ChangeAudit source) {
        ChangeAuditDtos.Response response = new ChangeAuditDtos.Response();
        response.setId(source.getId());
        response.setTargetType(source.getTargetType());
        response.setTargetKey(source.getTargetKey());
        response.setTargetName(source.getTargetName());
        response.setAction(source.getAction());
        response.setBeforeValue(source.getBeforeValue());
        response.setAfterValue(source.getAfterValue());
        response.setIsSensitive(source.getIsSensitive());
        response.setOperatorId(source.getOperatorId());
        response.setOperatorName(source.getOperatorName());
        response.setIp(source.getIp());
        response.setTraceId(source.getTraceId());
        response.setCreatedAt(TimeSupport.toInstant(source.getCreatedAt()));
        return response;
    }
}