package com.adminstarter.system.infrastructure.persistence;

import com.adminstarter.system.infrastructure.persistence.DataPermissionTables.TableKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据权限登记表的纯函数验证（不启动 Spring、不连库）。
 * <p>这是 fail-closed 的唯一事实源：未登记表必须报 UNKNOWN，由调用方转成"直接报错"，
 * 禁止退化成"没配置就默认跳过"。</p>
 */
class DataPermissionTablesTest {

    @Test
    @DisplayName("受控表：4 张业务表，按归属人字段区分 owner_id 与 create_by")
    void controlledTablesAreRegistered() {
        for (String table : new String[]{"sys_oper_log", "sys_file", "sys_notice", "biz_demo_task"}) {
            assertEquals(TableKind.CONTROLLED, DataPermissionTables.kindOf(table), table + " 应为受控表");
            assertTrue(DataPermissionTables.isControlled(table));
        }
        assertEquals("owner_id", DataPermissionTables.ownerColumn("biz_demo_task"));
        assertEquals("create_by", DataPermissionTables.ownerColumn("sys_file"));
        assertEquals("create_by", DataPermissionTables.selfColumn("biz_demo_task"));
    }

    @Test
    @DisplayName("豁免表：用户表必须豁免，否则管理员看不到自己要管的人")
    void exemptTablesIncludeUserTable() {
        assertEquals(TableKind.EXEMPT, DataPermissionTables.kindOf("sys_user"));
        assertEquals(TableKind.EXEMPT, DataPermissionTables.kindOf("sys_notice_recipient"));
        assertFalse(DataPermissionTables.isControlled("sys_user"));
        assertNull(DataPermissionTables.ownerColumn("sys_user"), "豁免表没有归属人字段");
    }

    @Test
    @DisplayName("未登记表返回 UNKNOWN（fail-closed，不能被当成豁免）")
    void unknownTableIsNotSilentlyExempt() {
        assertEquals(TableKind.UNKNOWN, DataPermissionTables.kindOf("t_future_business"));
        assertEquals(TableKind.UNKNOWN, DataPermissionTables.kindOf(null));
        assertEquals(TableKind.UNKNOWN, DataPermissionTables.kindOf("   "));
    }

    @Test
    @DisplayName("表名归一化：忽略大小写与 schema 前缀")
    void tableNameIsNormalized() {
        assertEquals(TableKind.CONTROLLED, DataPermissionTables.kindOf("SYS_FILE"));
        assertEquals(TableKind.CONTROLLED, DataPermissionTables.kindOf("admin_starter.sys_file"));
        assertEquals("create_by", DataPermissionTables.ownerColumn("ADMIN_STARTER.SYS_OPER_LOG"));
    }

    @Test
    @DisplayName("受控表与豁免表不得重叠（防止同一张表被登记两次）")
    void controlledAndExemptAreDisjoint() {
        Set<String> overlap = new HashSet<>(DataPermissionTables.CONTROLLED);
        overlap.retainAll(DataPermissionTables.EXEMPT);
        assertTrue(overlap.isEmpty(), "以下表同时登记为受控与豁免：" + overlap);
    }

    @Test
    @DisplayName("通知的公共行例外：带别名与不带别名的前缀不同，其余表没有例外")
    void publicRowConditionUsesAliasPrefix() {
        String withAlias = DataPermissionTables.publicRowCondition("sys_notice", "n");
        assertTrue(withAlias.contains("s.notice_id = n.id"), "带别名时应使用别名限定外层列：" + withAlias);

        String withoutAlias = DataPermissionTables.publicRowCondition("sys_notice", null);
        assertTrue(withoutAlias.contains("s.notice_id = sys_notice.id"), "无别名时应使用表名限定：" + withoutAlias);

        assertNull(DataPermissionTables.publicRowCondition("sys_file", "f"), "文件表没有公共行例外");
    }
}
