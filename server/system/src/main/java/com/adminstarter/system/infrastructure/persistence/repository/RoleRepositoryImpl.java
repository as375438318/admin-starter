package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.query.RoleQuery;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.RoleConverter;
import com.adminstarter.system.infrastructure.persistence.entity.RoleDO;
import com.adminstarter.system.infrastructure.persistence.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "code", "code",
            "sort", "sort",
            "id", "id"
    );

    private final RoleMapper roleMapper;
    private final RoleConverter converter;

    @Override
    public Role findById(Long id) {
        return converter.toDomain(roleMapper.selectById(id));
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(new QueryWrapper<RoleDO>().orderByAsc("sort", "id"))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void insert(Role role) {
        RoleDO row = converter.toDO(role);
        roleMapper.insert(row);
        role.setId(row.getId());
        role.setCreatedAt(row.getCreatedAt());
        role.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Role role) {
        roleMapper.updateById(converter.toDO(role));
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public PageResult<Role> page(RoleQuery query) {
        QueryWrapper<RoleDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("code", query.getKeyword()));
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq("is_enabled", query.getIsEnabled());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "sort");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.BY_SORT_COLUMN));
        Page<RoleDO> page = roleMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public List<Role> findByUserId(Long userId) {
        return roleMapper.selectRolesByUserId(userId).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<Long> findMenuIdsByRoleId(Long roleId) {
        return roleMapper.selectMenuIds(roleId);
    }

    @Override
    public void replaceRoleMenus(Long roleId, Collection<Long> menuIds) {
        roleMapper.deleteRoleMenus(roleId);
        if (menuIds == null) {
            return;
        }
        menuIds.forEach(menuId -> roleMapper.insertRoleMenu(roleId, menuId));
    }

    @Override
    public List<Long> findUserIdsByRoleId(Long roleId) {
        return roleMapper.selectUserIds(roleId);
    }

    @Override
    public long countByMenuId(Long menuId) {
        return roleMapper.countByMenuId(menuId);
    }
}
