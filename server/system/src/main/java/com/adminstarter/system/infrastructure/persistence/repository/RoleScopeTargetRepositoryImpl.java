package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.system.domain.model.RoleScopeTarget;
import com.adminstarter.system.domain.repository.RoleScopeTargetRepository;
import com.adminstarter.system.infrastructure.persistence.entity.RoleScopeTargetDO;
import com.adminstarter.system.infrastructure.persistence.mapper.RoleScopeTargetMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RoleScopeTargetRepositoryImpl implements RoleScopeTargetRepository {

    private final RoleScopeTargetMapper mapper;

    @Override
    public List<RoleScopeTarget> findByRoleId(Long roleId) {
        return mapper.selectList(new QueryWrapper<RoleScopeTargetDO>().eq("role_id", roleId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<RoleScopeTarget> findByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new QueryWrapper<RoleScopeTargetDO>().in("role_id", roleIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void replaceRoleTargets(Long roleId, Collection<Long> deptIds, Collection<Long> userIds) {
        deleteByRoleId(roleId);
        if (deptIds != null) {
            deptIds.forEach(id -> insert(roleId, "DEPT", id));
        }
        if (userIds != null) {
            userIds.forEach(id -> insert(roleId, "USER", id));
        }
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        mapper.delete(new QueryWrapper<RoleScopeTargetDO>().eq("role_id", roleId));
    }

    private void insert(Long roleId, String targetType, Long targetId) {
        RoleScopeTargetDO row = new RoleScopeTargetDO();
        row.setRoleId(roleId);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        mapper.insert(row);
    }

    private RoleScopeTarget toDomain(RoleScopeTargetDO row) {
        return new RoleScopeTarget(row.getRoleId(), row.getTargetType(), row.getTargetId());
    }
}
