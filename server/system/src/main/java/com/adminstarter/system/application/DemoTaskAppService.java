package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.DemoTaskDtos;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.query.DemoTaskQuery;
import com.adminstarter.system.domain.repository.DemoTaskRepository;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 示例业务应用服务：列表与详情显式进入数据权限上下文，越权详情统一按 404 处理。
 */
@Service
@RequiredArgsConstructor
public class DemoTaskAppService {

    private static final Set<String> STATUSES = Set.of("TODO", "DOING", "DONE");

    private final DemoTaskRepository demoTaskRepository;
    private final DeptRepository deptRepository;
    private final UserRepository userRepository;

    public PageResponse<DemoTaskDtos.Response> page(PageQuery pageQuery, String keyword, String status, Long deptId) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        DemoTaskQuery query = new DemoTaskQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setDeptId(deptId);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());

        PageResult<DemoTask> result = DataPermissionContext.controlled(() -> demoTaskRepository.page(query));
        return PageResponse.of(page, pageSize, assembleList(result.getList()), result.getTotal());
    }

    public DemoTaskDtos.Response getById(Long id) {
        DemoTask task = DataPermissionContext.controlled(() -> demoTaskRepository.findById(id));
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在或无权访问");
        }
        return assembleList(List.of(task)).get(0);
    }

    @Transactional
    public DemoTaskDtos.Response create(DemoTaskDtos.SaveRequest request) {
        validateDept(request.getDeptId());
        CurrentUser current = DataScopeContext.get();
        Long ownerId = request.getOwnerId() == null
                ? (current == null ? null : current.getUserId())
                : request.getOwnerId();
        validateOwner(ownerId);

        DemoTask task = new DemoTask();
        task.setTitle(request.getTitle());
        task.setStatus(normalizeStatus(request.getStatus()));
        task.setDeptId(request.getDeptId());
        task.setOwnerId(ownerId);
        task.setCreateBy(current == null ? null : current.getUserId());
        demoTaskRepository.insert(task);
        return assembleList(List.of(task)).get(0);
    }

    @Transactional
    public DemoTaskDtos.Response update(Long id, DemoTaskDtos.SaveRequest request) {
        DemoTask existing = DataPermissionContext.controlled(() -> demoTaskRepository.findById(id));
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在或无权访问");
        }
        validateDept(request.getDeptId());
        validateOwner(request.getOwnerId());

        existing.setTitle(request.getTitle());
        existing.setStatus(normalizeStatus(request.getStatus()));
        existing.setDeptId(request.getDeptId());
        existing.setOwnerId(request.getOwnerId());
        demoTaskRepository.update(existing);
        return assembleList(List.of(existing)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        DemoTask existing = DataPermissionContext.controlled(() -> demoTaskRepository.findById(id));
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在或无权访问");
        }
        demoTaskRepository.deleteById(id);
    }

    private DemoTaskDtos.Response toResponse(DemoTask task, Map<Long, Dept> depts, Map<Long, User> users) {
        DemoTaskDtos.Response response = new DemoTaskDtos.Response();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setStatus(task.getStatus());
        response.setDeptId(task.getDeptId());
        Dept dept = task.getDeptId() == null ? null : depts.get(task.getDeptId());
        response.setDeptName(dept == null ? null : dept.getName());
        response.setOwnerId(task.getOwnerId());
        User owner = task.getOwnerId() == null ? null : users.get(task.getOwnerId());
        response.setOwnerName(owner == null ? null : owner.getNickname());
        response.setCreateBy(task.getCreateBy());
        response.setCreatedAt(TimeSupport.toInstant(task.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(task.getUpdatedAt()));
        return response;
    }

    private List<DemoTaskDtos.Response> assembleList(List<DemoTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, Dept> depts = new HashMap<>();
        deptRepository.findAll().forEach(dept -> depts.put(dept.getId(), dept));
        List<Long> ownerIds = tasks.stream()
                .map(DemoTask::getOwnerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = userRepository.findByIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return tasks.stream().map(task -> toResponse(task, depts, users)).toList();
    }

    private void validateDept(Long deptId) {
        if (deptRepository.findById(deptId) == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "归属部门不存在");
        }
    }

    private void validateOwner(Long ownerId) {
        if (ownerId != null && userRepository.findById(ownerId) == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "负责人不存在");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "TODO";
        }
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务状态不合法");
        }
        return status;
    }
}
