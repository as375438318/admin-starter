package com.adminstarter.system.domain.repository;

import com.adminstarter.system.domain.model.RoleScopeTarget;

import java.util.Collection;
import java.util.List;

public interface RoleScopeTargetRepository {

    List<RoleScopeTarget> findByRoleId(Long roleId);

    List<RoleScopeTarget> findByRoleIds(Collection<Long> roleIds);

    void replaceRoleTargets(Long roleId, Collection<Long> deptIds, Collection<Long> userIds);

    void deleteByRoleId(Long roleId);
}
