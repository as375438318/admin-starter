package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.masking.MaskedValueGuard;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.UserDtos;
import com.adminstarter.system.application.service.PasswordPolicy;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserRoleBinding;
import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.domain.query.UserQuery;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAppService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DeptRepository deptRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final DataScopeProvider dataScopeProvider;
    private final UserSessionAppService sessionAppService;

    public PageResponse<UserDtos.Response> page(PageQuery pageQuery, String keyword, Boolean isEnabled, Long deptId) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        UserQuery query = new UserQuery();
        query.setKeyword(keyword);
        query.setIsEnabled(isEnabled);
        query.setDeptId(deptId);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());

        PageResult<User> result = userRepository.page(query);
        return PageResponse.of(page, pageSize, assembleList(result.getList()), result.getTotal());
    }

    public UserDtos.Response getById(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        return assembleList(List.of(user)).get(0);
    }

    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "账号已存在");
        }
        validateDept(request.getDeptId());
        validateRoles(request.getRoleIds());
        checkRoleChangeAllowed(request.getRoleIds(), List.of());
        MaskedValueGuard.rejectMaskedValue("email", request.getEmail());
        MaskedValueGuard.rejectMaskedValue("phone", request.getPhone());

        // 等保 P0-1：口令复杂度前置校验（失败 422 + 30202，含具体原因）
        passwordPolicy.validate(request.getPassword(), request.getUsername());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        user.setDeptId(request.getDeptId());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setIsEnabled(true);
        CurrentUser current = DataScopeContext.get();
        user.setCreateBy(current == null ? null : current.getUserId());
        userRepository.insert(user);
        userRepository.replaceUserRoles(user.getId(), request.getRoleIds());
        return assembleList(List.of(user)).get(0);
    }

    @Transactional
    public UserDtos.Response update(Long id, UserDtos.UpdateRequest request) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        validateDept(request.getDeptId());
        validateRoles(request.getRoleIds());
        List<Long> existingRoleIds = userRepository.findRoleBindings(List.of(id)).stream()
                .map(UserRoleBinding::getRoleId).toList();
        checkRoleChangeAllowed(request.getRoleIds(), existingRoleIds);

        existing.setNickname(request.getNickname());
        existing.setDeptId(request.getDeptId());
        existing.setEmail(MaskedValueGuard.apply("email", "user:email", MaskFormat.EMAIL,
                existing.getEmail(), request.getEmail(), request.isEmailSpecified(), false));
        existing.setPhone(MaskedValueGuard.apply("phone", "user:phone", MaskFormat.PHONE,
                existing.getPhone(), request.getPhone(), request.isPhoneSpecified(), false));
        userRepository.update(existing);
        if (request.getRoleIds() != null) {
            userRepository.replaceUserRoles(id, request.getRoleIds());
        }
        dataScopeProvider.evictUser(id);
        return assembleList(List.of(existing)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        CurrentUser current = DataScopeContext.get();
        if (current != null && current.getUserId().equals(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能删除当前登录账号");
        }
        userRepository.replaceUserRoles(id, List.of());
        userRepository.deleteById(id);
        // 账号已删除：会话表标记 USER_DELETED，避免设备列表仍显示在线
        sessionAppService.invalidateUser(id, UserSession.REASON_USER_DELETED);
        dataScopeProvider.evictUser(id);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        passwordPolicy.validate(password, existing.getUsername());
        existing.setPassword(passwordEncoder.encode(password));
        existing.setPasswordUpdatedAt(LocalDateTime.now());
        // 管理员重置的口令只有管理员知道，用户必须自己再改一次
        existing.setMustChangePassword(true);
        userRepository.update(existing);
        userRepository.bumpTokenVersion(id, "PASSWORD_RESET");
        // 账号级失效由 token_version 负责；会话表同批标记 PASSWORD_CHANGED
        sessionAppService.invalidateUser(id, UserSession.REASON_PASSWORD_CHANGED);
    }

    @Transactional
    public void updateStatus(Long id, Boolean isEnabled) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        CurrentUser current = DataScopeContext.get();
        if (current != null && current.getUserId().equals(id) && Boolean.FALSE.equals(isEnabled)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能停用当前登录账号");
        }
        existing.setIsEnabled(isEnabled);
        userRepository.update(existing);
        if (Boolean.FALSE.equals(isEnabled)) {
            userRepository.bumpTokenVersion(id, "ACCOUNT_DISABLED");
            // 账号级失效由 token_version 负责；会话表同批标记 ACCOUNT_DISABLED
            sessionAppService.invalidateUser(id, UserSession.REASON_ACCOUNT_DISABLED);
        }
        dataScopeProvider.evictUser(id);
    }

    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在或无权访问");
        }
        validateRoles(roleIds);
        userRepository.replaceUserRoles(id, roleIds);
        dataScopeProvider.evictUser(id);
    }

    public List<UserDtos.Option> options(String keyword) {
        UserQuery query = new UserQuery();
        query.setKeyword(keyword);
        query.setPage(1);
        query.setPageSize(50);
        PageResult<User> result = userRepository.page(query);
        Map<Long, Dept> depts = deptMap();
        return result.getList().stream().map(user -> {
            UserDtos.Option option = new UserDtos.Option();
            option.setId(user.getId());
            option.setUsername(user.getUsername());
            option.setNickname(user.getNickname());
            option.setDeptId(user.getDeptId());
            Dept dept = user.getDeptId() == null ? null : depts.get(user.getDeptId());
            option.setDeptName(dept == null ? null : dept.getName());
            return option;
        }).toList();
    }

    private List<UserDtos.Response> assembleList(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, Dept> depts = deptMap();
        List<Long> userIds = users.stream().map(User::getId).toList();
        List<UserRoleBinding> bindings = userRepository.findRoleBindings(userIds);
        Map<Long, List<Long>> roleIds = new HashMap<>();
        Map<Long, List<String>> roleNames = new HashMap<>();
        for (UserRoleBinding binding : bindings) {
            roleIds.computeIfAbsent(binding.getUserId(), k -> new ArrayList<>()).add(binding.getRoleId());
            roleNames.computeIfAbsent(binding.getUserId(), k -> new ArrayList<>()).add(binding.getRoleName());
        }
        return users.stream().map(user -> {
            UserDtos.Response response = new UserDtos.Response();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setNickname(user.getNickname());
            response.setDeptId(user.getDeptId());
            Dept dept = user.getDeptId() == null ? null : depts.get(user.getDeptId());
            response.setDeptName(dept == null ? null : dept.getName());
            response.setEmail(user.getEmail());
            response.setPhone(user.getPhone());
            response.setIsEnabled(user.getIsEnabled());
            response.setRoleIds(roleIds.getOrDefault(user.getId(), List.of()));
            response.setRoleNames(roleNames.getOrDefault(user.getId(), List.of()));
            response.setCreatedAt(TimeSupport.toInstant(user.getCreatedAt()));
            response.setUpdatedAt(TimeSupport.toInstant(user.getUpdatedAt()));
            return response;
        }).toList();
    }

    private Map<Long, Dept> deptMap() {
        Map<Long, Dept> map = new HashMap<>();
        deptRepository.findAll().forEach(dept -> map.put(dept.getId(), dept));
        return map;
    }

    private void validateDept(Long deptId) {
        if (deptId != null && deptRepository.findById(deptId) == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "所属部门不存在");
        }
    }

    private void validateRoles(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> known = new HashSet<>();
        roleRepository.findAll().forEach(role -> known.add(role.getId()));
        for (Long roleId : roleIds) {
            if (!known.contains(roleId)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "角色不存在: " + roleId);
            }
        }
    }

    /**
     * 角色绑定只能由持有 system:user:assign 的人改动：
     * 新建/编辑用户都接受 roleIds，若不校验，撤掉 assign 权限仍可通过表单改角色，收权无效。
     * 未改变现有角色集合时不拦截，保证仅有 user:update 的人可正常编辑资料。
     */
    private void checkRoleChangeAllowed(Collection<Long> requested, Collection<Long> existing) {
        if (requested == null || requested.isEmpty()) {
            return;
        }
        if (existing != null && new HashSet<>(requested).equals(new HashSet<>(existing))) {
            return;
        }
        CurrentUser current = DataScopeContext.get();
        if (current == null || !current.hasPermission("system:user:assign")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权分配角色，请由超级管理员操作");
        }
    }
}
