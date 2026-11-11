package com.adminstarter.system.application;

import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.domain.enums.OwnFilter;
import com.adminstarter.system.domain.model.DataScopeCondition;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.RoleScopeTarget;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.RoleScopeTargetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限条件计算与缓存。任何角色/用户角色变更都必须调用 evict 立即失效；
 * 任何部门树变更（增/改/移/删）必须调用 evictAll，让按组织展开的缓存整体重算。
 */
@Service
@RequiredArgsConstructor
public class DataScopeProvider {

    private static final String CACHE_PREFIX = com.adminstarter.common.cache.CacheNamespaces.DATASCOPE_PREFIX;
    private static final String DEPT_VERSION_KEY = com.adminstarter.common.cache.CacheNamespaces.DATASCOPE_DEPT_VERSION_KEY;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DEPT_VERSION_TTL = Duration.ofDays(365);

    private final RoleRepository roleRepository;
    private final RoleScopeTargetRepository roleScopeTargetRepository;
    private final DeptRepository deptRepository;
    private final CacheService cacheService;

    public DataScopeCondition current() {
        CurrentUser user = DataScopeContext.get();
        if (user == null || user.getUserId() == null) {
            return null;
        }
        String key = cacheKey(user.getUserId());
        DataScopeCondition cached = cacheService.get(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        DataScopeCondition condition = resolve(user);
        cacheService.put(key, condition, CACHE_TTL);
        return condition;
    }

    public void evictUser(Long userId) {
        if (userId != null) {
            cacheService.delete(cacheKey(userId));
        }
    }

    public void evictUsers(Iterable<Long> userIds) {
        userIds.forEach(this::evictUser);
    }

    /**
     * 部门树变更后调用：组织版本号自增，所有按旧树展开的用户缓存前缀整体作废，
     * 旧 key 由 5 分钟 TTL 自然淘汰，无需按模式扫描 Redis。
     */
    public void evictAll() {
        cacheService.increment(DEPT_VERSION_KEY, DEPT_VERSION_TTL);
    }

    private DataScopeCondition resolve(CurrentUser user) {
        List<Role> roles = roleRepository.findByUserId(user.getUserId());
        if (roles.isEmpty()) {
            // 无任何角色：默认只能看本人创建的数据。
            return DataScopeCondition.builder()
                    .currentUserId(user.getUserId())
                    .selfIncluded(true)
                    .ownUnrestricted(true)
                    .deptIds(List.of())
                    .userIds(List.of())
                    .build();
        }

        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<RoleScopeTarget> targets = roleScopeTargetRepository.findByRoleIds(roleIds);

        boolean unrestricted = false;
        boolean selfIncluded = false;
        boolean ownUnrestricted = false;
        boolean ownCreator = false;
        boolean ownOwner = false;
        Set<Long> deptIds = new LinkedHashSet<>();
        Set<Long> userIds = new LinkedHashSet<>();

        for (Role role : roles) {
            if (role.getDataScope() == null) {
                continue;
            }
            switch (role.getDataScope()) {
                case ALL -> unrestricted = true;
                case DEPT -> {
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                    }
                }
                case DEPT_AND_CHILD -> {
                    if (user.getDeptId() != null) {
                        deptIds.addAll(deptRepository.findDescendantIds(user.getDeptId()));
                    }
                }
                case SELF -> selfIncluded = true;
                case CUSTOM_DEPT, CUSTOM_USER -> {
                    // 由下方统一处理，避免重复查询
                }
                default -> {
                    // 未识别的枚举值不追加任何组织范围，保持最小权限（默认拒绝）；
                    // DataScopeSqlBuilder 在无任何条件时输出 1 = 0，不会退化为全量可见。
                }
            }
            OwnFilter ownFilter = role.getOwnFilter();
            if (ownFilter != null) {
                switch (ownFilter) {
                    case NONE -> ownUnrestricted = true;
                    case CREATOR -> ownCreator = true;
                    case OWNER -> ownOwner = true;
                    default -> {
                        // 未识别的枚举值不加任何归属条件，同样由 DataScopeSqlBuilder 收敛为 1 = 0。
                    }
                }
            }
        }

        for (RoleScopeTarget target : targets) {
            if ("DEPT".equals(target.getTargetType())) {
                deptIds.add(target.getTargetId());
            } else if ("USER".equals(target.getTargetType())) {
                userIds.add(target.getTargetId());
            }
        }

        return DataScopeCondition.builder()
                .unrestricted(unrestricted)
                .ownUnrestricted(ownUnrestricted)
                .selfIncluded(selfIncluded)
                .ownCreator(ownCreator)
                .ownOwner(ownOwner)
                .deptIds(new ArrayList<>(deptIds))
                .userIds(new ArrayList<>(userIds))
                .currentUserId(user.getUserId())
                .build();
    }

    private String cacheKey(Long userId) {
        return CACHE_PREFIX + deptVersion() + ":" + userId;
    }

    private long deptVersion() {
        String version = cacheService.getString(DEPT_VERSION_KEY);
        if (version == null) {
            return 0L;
        }
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
