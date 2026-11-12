package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserRoleBinding;
import com.adminstarter.system.domain.query.UserQuery;
import com.adminstarter.system.domain.repository.UserRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.UserConverter;
import com.adminstarter.system.infrastructure.persistence.entity.RoleDO;
import com.adminstarter.system.infrastructure.persistence.entity.UserDO;
import com.adminstarter.system.infrastructure.persistence.mapper.RoleMapper;
import com.adminstarter.system.infrastructure.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "username", "username",
            "nickname", "nickname",
            "id", "id"
    );

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserConverter converter;

    @Override
    public User findById(Long id) {
        return converter.toDomain(userMapper.selectById(id));
    }

    @Override
    public User findByUsername(String username) {
        UserDO row = userMapper.selectOne(new QueryWrapper<UserDO>().eq("username", username).last("limit 1"));
        return converter.toDomain(row);
    }

    @Override
    public List<User> findByUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new QueryWrapper<UserDO>().in("username", usernames)).stream()
                .map(converter::toDomain).toList();
    }

    @Override
    public List<User> findAllForReference() {
        return userMapper.selectList(new QueryWrapper<UserDO>()
                        .select("id", "username", "nickname", "dept_id", "is_enabled")
                        .orderByAsc("id")).stream()
                .map(converter::toDomain).toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new QueryWrapper<UserDO>().eq("username", username)) > 0;
    }

    @Override
    public void insert(User user) {
        UserDO row = converter.toDO(user);
        userMapper.insert(row);
        user.setId(row.getId());
        user.setCreatedAt(row.getCreatedAt());
        user.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(User user) {
        userMapper.updateById(converter.toDO(user));
    }

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void bumpTokenVersion(Long id, String reason) {
        userMapper.bumpTokenVersion(id, reason);
    }

    @Override
    public PageResult<User> page(UserQuery query) {
        QueryWrapper<UserDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("username", query.getKeyword()).or().like("nickname", query.getKeyword()));
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq("is_enabled", query.getIsEnabled());
        }
        if (query.getDeptId() != null) {
            wrapper.eq("dept_id", query.getDeptId());
        }

        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.NEWEST_FIRST));

        Page<UserDO> page = userMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<User> list = page.getRecords().stream().map(converter::toDomain).toList();
        return PageResult.of(list, page.getTotal());
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        return userMapper.selectRoleIds(userId);
    }

    @Override
    public List<UserRoleBinding> findRoleBindings(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectRoleBindings(userIds).stream()
                .map(row -> new UserRoleBinding(
                        ((Number) row.get("userId")).longValue(),
                        ((Number) row.get("roleId")).longValue(),
                        String.valueOf(row.get("roleName"))))
                .toList();
    }

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        List<RoleDO> roles = roleMapper.selectRolesByUserId(userId);
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> codes.add(role.getCode()));
        return List.copyOf(codes);
    }

    @Override
    public void replaceUserRoles(Long userId, Collection<Long> roleIds) {
        userMapper.deleteUserRoles(userId);
        if (roleIds == null) {
            return;
        }
        roleIds.forEach(roleId -> userMapper.insertUserRole(userId, roleId));
    }

    @Override
    public long countByDeptId(Long deptId) {
        return userMapper.selectCount(new QueryWrapper<UserDO>().eq("dept_id", deptId));
    }

    @Override
    public long countByDeptIds(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return 0L;
        }
        return userMapper.selectCount(new QueryWrapper<UserDO>().in("dept_id", deptIds));
    }

    @Override
    public List<Long> findEnabledUserIds() {
        return userMapper.selectEnabledUserIds();
    }

    @Override
    public List<Long> findEnabledUserIdsByDeptIds(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectEnabledUserIdsByDeptIds(deptIds);
    }
}
