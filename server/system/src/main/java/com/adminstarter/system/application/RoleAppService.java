package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.RoleDtos;
import com.adminstarter.system.domain.enums.DataScope;
import com.adminstarter.system.domain.enums.OwnFilter;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.RoleScopeTarget;
import com.adminstarter.system.domain.query.RoleQuery;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.RoleScopeTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleAppService {

    private final RoleRepository roleRepository;
    private final RoleScopeTargetRepository roleScopeTargetRepository;
    private final DataScopeProvider dataScopeProvider;

    public PageResponse<RoleDtos.Response> page(PageQuery pageQuery, String keyword, Boolean isEnabled) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        RoleQuery query = new RoleQuery();
        query.setKeyword(keyword);
        query.setIsEnabled(isEnabled);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<Role> result = roleRepository.page(query);
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toResponse).toList(), result.getTotal());
    }

    public List<RoleDtos.Response> listAll() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    public RoleDtos.Response getById(Long id) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        RoleDtos.Response response = toResponse(role);
        response.setMenuIds(roleRepository.findMenuIdsByRoleId(id));
        List<RoleScopeTarget> targets = roleScopeTargetRepository.findByRoleId(id);
        response.setDeptIds(targets.stream().filter(t -> "DEPT".equals(t.getTargetType()))
                .map(RoleScopeTarget::getTargetId).toList());
        response.setUserIds(targets.stream().filter(t -> "USER".equals(t.getTargetType()))
                .map(RoleScopeTarget::getTargetId).toList());
        return response;
    }

    @Transactional
    public RoleDtos.Response create(RoleDtos.SaveRequest request) {
        checkCodeUnique(request.getCode(), null);
        Role role = new Role();
        apply(role, request);
        role.setDataScope(DataScope.SELF);
        role.setOwnFilter(OwnFilter.NONE);
        roleRepository.insert(role);
        return toResponse(role);
    }

    @Transactional
    public RoleDtos.Response update(Long id, RoleDtos.SaveRequest request) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        checkCodeUnique(request.getCode(), id);
        apply(role, request);
        roleRepository.update(role);
        evictRoleUsers(id);
        return toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        evictRoleUsers(id);
        roleRepository.replaceRoleMenus(id, List.of());
        roleScopeTargetRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    @Transactional
    public void assignMenus(Long id, List<Long> menuIds) {
        requireRole(id);
        roleRepository.replaceRoleMenus(id, menuIds);
        evictRoleUsers(id);
    }

    @Transactional
    public void assignDataScope(Long id, RoleDtos.DataScopeRequest request) {
        requireRole(id);
        DataScope dataScope = parseDataScope(request.getDataScope());
        OwnFilter ownFilter = parseOwnFilter(request.getOwnFilter());
        Role role = roleRepository.findById(id);
        role.setDataScope(dataScope);
        role.setOwnFilter(ownFilter);
        roleRepository.update(role);

        List<Long> deptIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        if (dataScope == DataScope.CUSTOM_DEPT && request.getDeptIds() != null) {
            deptIds.addAll(request.getDeptIds());
        }
        if (dataScope == DataScope.CUSTOM_USER && request.getUserIds() != null) {
            userIds.addAll(request.getUserIds());
        }
        roleScopeTargetRepository.replaceRoleTargets(id, deptIds, userIds);
        evictRoleUsers(id);
    }

    private void evictRoleUsers(Long roleId) {
        dataScopeProvider.evictUsers(roleRepository.findUserIdsByRoleId(roleId));
    }

    private Role requireRole(Long id) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        roleRepository.findAll().stream()
                .filter(role -> role.getCode().equals(code))
                .filter(role -> excludeId == null || !role.getId().equals(excludeId))
                .findFirst()
                .ifPresent(role -> {
                    throw new BizException(ErrorCode.STATE_CONFLICT, "角色编码已存在");
                });
    }

    private void apply(Role role, RoleDtos.SaveRequest request) {
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setSort(request.getSort() == null ? 0 : request.getSort());
        role.setRemark(request.getRemark());
        role.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private DataScope parseDataScope(String value) {
        try {
            return DataScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 正常情况下已由 DTO 的 @EnumValue 拦截为 422；此处兜底同样按校验失败处理，且不回显原始输入。
            throw new BizException(ErrorCode.VALIDATION_FAILED, "数据范围取值不合法");
        }
    }

    private OwnFilter parseOwnFilter(String value) {
        try {
            return OwnFilter.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "归属过滤取值不合法");
        }
    }

    private RoleDtos.Response toResponse(Role role) {
        RoleDtos.Response response = new RoleDtos.Response();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setCode(role.getCode());
        response.setSort(role.getSort());
        response.setDataScope(role.getDataScope() == null ? null : role.getDataScope().name());
        response.setOwnFilter(role.getOwnFilter() == null ? null : role.getOwnFilter().name());
        response.setRemark(role.getRemark());
        response.setIsEnabled(role.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(role.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(role.getUpdatedAt()));
        return response;
    }
}
