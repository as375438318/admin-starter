package com.adminstarter.system.application;

import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.domain.enums.DataScope;
import com.adminstarter.system.domain.enums.OwnFilter;
import com.adminstarter.system.domain.model.DataScopeCondition;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.RoleScopeTarget;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.RoleScopeTargetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 角色 → 数据权限条件的合成规则（多角色取并集，最宽档胜出）。
 * <p>"权限放宽"最典型的出错方式就在这里：某档位漏判一次，用户就多看见一片数据。
 * 因此每条都锁定"什么情况下会变宽"，以及"无角色 / 未知枚举必须收敛到最小权限"。</p>
 */
class DataScopeProviderTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleScopeTargetRepository scopeTargetRepository = mock(RoleScopeTargetRepository.class);
    private final DeptRepository deptRepository = mock(DeptRepository.class);
    private final CacheService cacheService = mock(CacheService.class);

    private DataScopeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DataScopeProvider(roleRepository, scopeTargetRepository, deptRepository, cacheService);
        when(cacheService.get(anyString(), any())).thenReturn(null);   // 强制走实时计算
        when(scopeTargetRepository.findByRoleIds(any())).thenReturn(List.of());
        DataScopeContext.set(CurrentUser.builder()
                .userId(7L)
                .deptId(20L)
                .permissions(Set.of())
                .build());
    }



    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static Role role(long id, DataScope scope, OwnFilter ownFilter) {
        Role role = new Role();
        role.setId(id);
        role.setDataScope(scope);
        role.setOwnFilter(ownFilter);
        return role;
    }

    @Test
    @DisplayName("未登录 → null（调用方据此不追加条件，且上游已由功能权限拦住）")
    void anonymousReturnsNull() {
        DataScopeContext.clear();
        assertNull(provider.current());
    }

    @Test
    @DisplayName("无任何角色 → 只能看本人创建（默认最小权限，不是全量）")
    void noRolesFallsBackToSelfOnly() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of());

        DataScopeCondition condition = provider.current();

        assertTrue(condition.isSelfIncluded());
        assertTrue(condition.isOwnUnrestricted());
        assertFalse(condition.isUnrestricted(), "无角色不得变成全量可见");
        assertEquals(7L, condition.getCurrentUserId());
    }

    @Test
    @DisplayName("ALL → 组织范围不限制")
    void allScopeIsUnrestricted() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(role(1, DataScope.ALL, OwnFilter.NONE)));

        DataScopeCondition condition = provider.current();

        assertTrue(condition.isUnrestricted());
        assertTrue(condition.isOwnUnrestricted());
    }

    @Test
    @DisplayName("DEPT → 只含本部门；DEPT_AND_CHILD → 追加子孙部门")
    void deptScopesExpandAsDocumented() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(role(1, DataScope.DEPT, OwnFilter.NONE)));
        assertEquals(List.of(20L), provider.current().getDeptIds());

        when(roleRepository.findByUserId(7L)).thenReturn(List.of(role(2, DataScope.DEPT_AND_CHILD, OwnFilter.NONE)));
        when(deptRepository.findDescendantIds(20L)).thenReturn(List.of(20L, 21L, 22L));
        assertEquals(List.of(20L, 21L, 22L), provider.current().getDeptIds());
    }

    @Test
    @DisplayName("多角色：组织范围取并集，ALL 任一命中即整体不限制")
    void multipleRolesUnionAndWidestWins() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(
                role(1, DataScope.SELF, OwnFilter.CREATOR),
                role(2, DataScope.ALL, OwnFilter.OWNER)));

        DataScopeCondition condition = provider.current();

        assertTrue(condition.isUnrestricted());
        assertTrue(condition.isSelfIncluded(), "SELF 档位仍应被记录");
        assertTrue(condition.isOwnCreator());
        assertTrue(condition.isOwnOwner());
        assertFalse(condition.isOwnUnrestricted(), "两个角色都不是 NONE，归属过滤仍生效");
    }

    @Test
    @DisplayName("own_filter=NONE 任一命中 → 归属过滤整体不限制")
    void ownFilterNoneWinsAcrossRoles() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(
                role(1, DataScope.DEPT, OwnFilter.OWNER),
                role(2, DataScope.SELF, OwnFilter.NONE)));

        assertTrue(provider.current().isOwnUnrestricted());
    }

    @Test
    @DisplayName("CUSTOM_DEPT / CUSTOM_USER 目标并入 deptIds 与 userIds")
    void customTargetsAreMerged() {
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(role(1, DataScope.CUSTOM_DEPT, OwnFilter.NONE)));
        when(scopeTargetRepository.findByRoleIds(List.of(1L))).thenReturn(List.of(
                target("DEPT", 30L),
                target("USER", 40L),
                target("USER", 41L)));

        DataScopeCondition condition = provider.current();

        assertEquals(List.of(30L), condition.getDeptIds());
        assertEquals(List.of(40L, 41L), condition.getUserIds());
    }

    @Test
    @DisplayName("角色无 dataScope / 未知枚举 → 不追加范围，收敛为最小权限")
    void unknownOrMissingScopeStaysNarrow() {
        Role noScope = new Role();
        noScope.setId(1L);
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(noScope));

        DataScopeCondition condition = provider.current();

        assertFalse(condition.isUnrestricted());
        assertFalse(condition.isSelfIncluded());
        assertEquals(List.of(), condition.getDeptIds());
        assertEquals(List.of(), condition.getUserIds());
    }

    @Test
    @DisplayName("缓存键带部门版本号：部门树变更后 key 变化，无需扫描 Redis")
    void cacheKeyIncludesDeptVersion() {
        when(cacheService.getString(anyString())).thenReturn("3");
        when(roleRepository.findByUserId(7L)).thenReturn(List.of(role(1, DataScope.ALL, OwnFilter.NONE)));

        provider.current();

        // 断言用常量拼装，避免把 key 格式在测试里再写死一份（改前缀时会同时失效）
        org.mockito.Mockito.verify(cacheService).get(
                org.mockito.ArgumentMatchers.eq(
                        com.adminstarter.common.cache.CacheNamespaces.DATASCOPE_PREFIX + "3:7"), any());
    }

    @Test
    @DisplayName("命中缓存时不再计算（避免每次请求都查角色与部门树）")
    void cachedConditionIsReturnedWithoutResolving() {
        DataScopeCondition cached = DataScopeCondition.builder().unrestricted(true).build();
        when(cacheService.get(anyString(), any())).thenReturn(cached);

        assertEquals(cached, provider.current());
        org.mockito.Mockito.verify(roleRepository, org.mockito.Mockito.never()).findByUserId(anyLong());
    }

    private static RoleScopeTarget target(String type, long id) {
        return new RoleScopeTarget(1L, type, id);
    }
}
