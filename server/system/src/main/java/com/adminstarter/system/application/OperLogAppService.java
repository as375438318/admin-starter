package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.LogDtos;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.system.domain.query.OperLogQuery;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.OperLogRepository;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperLogAppService {

    private final OperLogRepository operLogRepository;
    private final DeptRepository deptRepository;

    public void record(OperLog operLog) {
        operLogRepository.insert(operLog);
    }

    public PageResponse<LogDtos.OperLogResponse> page(PageQuery pageQuery, String keyword, Integer resultCode,
                                                     Instant from, Instant to) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        OperLogQuery query = new OperLogQuery();
        query.setKeyword(keyword);
        query.setResultCode(resultCode);
        query.setFrom(TimeSupport.toLocalDateTime(from));
        query.setTo(TimeSupport.toLocalDateTime(to));
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<OperLog> result = DataPermissionContext.controlled(() -> operLogRepository.page(query));
        Map<Long, String> deptNames = new HashMap<>();
        deptRepository.findAll().forEach(dept -> deptNames.put(dept.getId(), dept.getName()));
        List<LogDtos.OperLogResponse> list = result.getList().stream()
                .map(operLog -> toResponse(operLog, deptNames)).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    private LogDtos.OperLogResponse toResponse(OperLog operLog, Map<Long, String> deptNames) {
        LogDtos.OperLogResponse response = new LogDtos.OperLogResponse();
        response.setId(operLog.getId());
        response.setTraceId(operLog.getTraceId());
        response.setUserId(operLog.getUserId());
        response.setUsername(operLog.getUsername());
        response.setDeptId(operLog.getDeptId());
        response.setDeptName(operLog.getDeptId() == null ? null : deptNames.get(operLog.getDeptId()));
        response.setModule(operLog.getModule());
        response.setAction(operLog.getAction());
        response.setMethod(operLog.getMethod());
        response.setRequestUri(operLog.getRequestUri());
        response.setRequestMethod(operLog.getRequestMethod());
        response.setIp(operLog.getIp());
        response.setParams(operLog.getParams());
        response.setResultCode(operLog.getResultCode());
        response.setResultMessage(operLog.getResultMessage());
        response.setElapsedMs(operLog.getElapsedMs());
        response.setCreatedAt(TimeSupport.toInstant(operLog.getCreatedAt()));
        return response;
    }
}
