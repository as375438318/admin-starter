package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.query.RoleQuery;

import java.util.Collection;
import java.util.List;

public interface RoleRepository {

    Role findById(Long id);

    List<Role> findAll();

    void insert(Role role);

    void update(Role role);

    void deleteById(Long id);

    PageResult<Role> page(RoleQuery query);

    List<Role> findByUserId(Long userId);

    List<Long> findMenuIdsByRoleId(Long roleId);

    void replaceRoleMenus(Long roleId, Collection<Long> menuIds);

    List<Long> findUserIdsByRoleId(Long roleId);

    long countByMenuId(Long menuId);
}
