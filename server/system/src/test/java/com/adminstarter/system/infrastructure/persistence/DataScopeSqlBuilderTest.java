package com.adminstarter.system.infrastructure.persistence;

import com.adminstarter.system.domain.model.DataScopeCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据权限 SQL 片段编译（纯函数）。
 * <p>这是"权限放宽"最容易出事的一处：拼错一处就等于把别人的数据开放出去，
 * 或者把应该看的人挡在外面。重点锁三条：</p>
 * <ol>
 *   <li>全都不限制 → 返回 null（不追加条件）；</li>
 *   <li>该限制但没有任何可用条件 → 收敛为 {@code 1 = 0}（fail-closed，不能退化成全量可见）；</li>
 *   <li>组织范围与归属过滤同时存在时用 {@code and}，且各自带外层括号。</li>
 * </ol>
 */
class DataScopeSqlBuilderTest {

    private static DataScopeCondition.DataScopeConditionBuilder base() {
        return DataScopeCondition.builder().currentUserId(5L).deptIds(List.of()).userIds(List.of());
    }

    @Test
    @DisplayName("条件为 null → 不追加（调用方据此跳过改写）")
    void nullConditionProducesNull() {
        assertNull(DataScopeSqlBuilder.build("t", null, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("组织与归属都不限制 → null（全量可见是显式授权的结果）")
    void fullyUnrestrictedProducesNull() {
        DataScopeCondition condition = base().unrestricted(true).ownUnrestricted(true).build();
        assertNull(DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("只有部门范围 → dept_id in (...)，带别名时加前缀")
    void deptScopeOnly() {
        DataScopeCondition condition = base().deptIds(List.of(3L, 4L)).ownUnrestricted(true).build();

        assertEquals("(dept_id in (3,4))", DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
        assertEquals("(t.dept_id in (3,4))", DataScopeSqlBuilder.build("t", condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("SELF 范围按 selfColumn 过滤本人创建")
    void selfScopeUsesSelfColumn() {
        DataScopeCondition condition = base().selfIncluded(true).ownUnrestricted(true).build();
        assertEquals("(create_by = 5)", DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("selfColumn 为空时回落到 create_by（不产生 \"null = 5\" 这种坏 SQL）")
    void blankSelfColumnFallsBack() {
        DataScopeCondition condition = base().selfIncluded(true).ownUnrestricted(true).build();
        assertEquals("(create_by = 5)", DataScopeSqlBuilder.build(null, condition, "owner_id", " "));
    }

    @Test
    @DisplayName("CUSTOM_USER 指定人员 → create_by in (...)")
    void customUsersScope() {
        DataScopeCondition condition = base().userIds(List.of(8L, 9L)).ownUnrestricted(true).build();
        assertEquals("(create_by in (8,9))", DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("组织范围多个来源用 or 连接，整体带括号")
    void multipleOrgPartsAreOred() {
        DataScopeCondition condition = base()
                .deptIds(List.of(3L)).selfIncluded(true).userIds(List.of(8L))
                .ownUnrestricted(true).build();

        String sql = DataScopeSqlBuilder.build("t", condition, "owner_id", "create_by");
        assertEquals("(t.dept_id in (3) or t.create_by = 5 or t.create_by in (8))", sql);
    }

    @Test
    @DisplayName("归属过滤：CREATOR 用 create_by，OWNER 用 ownerColumn，二者 or")
    void ownFilterUsesBothColumns() {
        DataScopeCondition condition = base()
                .unrestricted(true).ownCreator(true).ownOwner(true).build();

        assertEquals("(create_by = 5 or owner_id = 5)",
                DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("组织范围与归属过滤同时存在 → 两段用 and 连接")
    void orgAndOwnAreAnded() {
        DataScopeCondition condition = base()
                .deptIds(List.of(3L)).ownCreator(true).build();

        assertEquals("(dept_id in (3)) and (create_by = 5)",
                DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("要限制组织范围却没有任何来源 → 1 = 0（fail-closed）")
    void emptyOrgScopeFailsClosed() {
        DataScopeCondition condition = base().ownUnrestricted(true).build();
        assertEquals("1 = 0", DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("要限制归属却没有任何过滤项 → 1 = 0（fail-closed）")
    void emptyOwnScopeFailsClosed() {
        DataScopeCondition condition = base().unrestricted(true).build();
        assertEquals("1 = 0", DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by"));
    }

    @Test
    @DisplayName("只拼数值 ID：不出现引号，避免 SQL 注入面")
    void onlyNumericIdsAreInlined() {
        DataScopeCondition condition = base()
                .deptIds(List.of(1L, 2L)).userIds(List.of(3L)).selfIncluded(true).build();

        String sql = DataScopeSqlBuilder.build(null, condition, "owner_id", "create_by");
        assertTrue(sql != null && !sql.contains("'"), "条件里不应出现字符串字面量：" + sql);
        assertFalse(sql.contains("(") && sql.contains("()"), "不应出现空的 in ()：" + sql);
    }
}
