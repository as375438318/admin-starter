package com.adminstarter.system.infrastructure.persistence;

import java.util.Map;
import java.util.Set;

/**
 * 数据权限表登记表：每张业务表必须显式登记为“受控”或“豁免”，未登记表在受控查询中直接报错。
 * <p>禁止依赖“没配置就默认跳过”的隐式行为；本类是与实体注解（{@link DataPermissionControlled} /
 * {@link DataPermissionExempt}）配套的运行时唯一事实源。</p>
 */
public final class DataPermissionTables {

    public enum TableKind {
        CONTROLLED,
        EXEMPT,
        UNKNOWN
    }

    /** 受控业务表 → 负责人字段（OWNER 归属过滤使用）。 */
    private static final Map<String, String> CONTROLLED_OWNER_COLUMNS = Map.of(
            "sys_oper_log", "create_by",
            "sys_file", "create_by",
            "sys_notice", "create_by",
            "biz_demo_task", "owner_id"
    );

    /** 受控业务表 → 数据归属人字段（SELF / CUSTOM_USER 使用）。 */
    private static final Map<String, String> CONTROLLED_SELF_COLUMNS = Map.of(
            "sys_oper_log", "create_by",
            "sys_file", "create_by",
            "sys_notice", "create_by",
            "biz_demo_task", "create_by"
    );

    public static final Set<String> CONTROLLED = CONTROLLED_OWNER_COLUMNS.keySet();

    /**
     * 显式不受数据权限控制：系统配置类、关系表、登录日志，以及用户表。
     * <p>用户表豁免理由：能进入用户管理页的人已由功能权限（system:user:list）拦截，
     * 叠加数据权限会出现“管理员看不到自己要管的人”。按部门查看用户使用列表的普通筛选参数 deptId。</p>
     * <p>历史说明：早期实现曾把 sys_user 当作受控表，并用 id 作为 SELF 列（见 impl-notes DS-001-H），该映射已删除。</p>
     */
    public static final Set<String> EXEMPT = Set.of(
            "sys_user",
            "sys_user_role",
            "sys_role",
            "sys_role_menu",
            "role_scope_target",
            "sys_menu",
            "sys_dept",
            "sys_post",
            "sys_dict_type",
            "sys_dict_item",
            "sys_config",
            "sys_notice_scope",
            "sys_notice_recipient",
            "sys_login_log",
            "sys_job",
            "sys_job_log",
            "sys_user_session",
            "sys_file_chunk_upload"
    );

    /**
     * 受控表的“公共行”例外：满足条件的行对任何通过功能权限进入查询的人都可见（只影响 SELECT 可见性）。
     * <p>当前只有通知：范围含 ALL（全员）的通知对所有持有 system:notice:list 的管理员可见；
     * 撤回/删除授权不变（仍只给 SUPER_ADMIN）。%s 为表别名前缀（无别名时为空串）。</p>
     */
    private static final Map<String, String> PUBLIC_ROW_CONDITIONS = Map.of(
            "sys_notice",
            "exists (select 1 from sys_notice_scope s where s.notice_id = %sid and s.scope_type = 'ALL' and s.deleted = 0)"
    );

    private DataPermissionTables() {
    }

    public static TableKind kindOf(String tableName) {
        String name = normalize(tableName);
        if (name == null) {
            return TableKind.UNKNOWN;
        }
        if (CONTROLLED.contains(name)) {
            return TableKind.CONTROLLED;
        }
        if (EXEMPT.contains(name)) {
            return TableKind.EXEMPT;
        }
        return TableKind.UNKNOWN;
    }

    public static boolean isControlled(String tableName) {
        return kindOf(tableName) == TableKind.CONTROLLED;
    }

    public static String ownerColumn(String tableName) {
        return isControlled(tableName) ? CONTROLLED_OWNER_COLUMNS.get(normalize(tableName)) : null;
    }

    public static String selfColumn(String tableName) {
        return CONTROLLED_SELF_COLUMNS.getOrDefault(normalize(tableName), "create_by");
    }

    /** 公共行例外条件；无例外返回 null。alias 为空时用表名限定，避免子查询内层同名列遮蔽外层列。 */
    public static String publicRowCondition(String tableName, String alias) {
        String template = PUBLIC_ROW_CONDITIONS.get(normalize(tableName));
        if (template == null) {
            return null;
        }
        String prefix = alias == null || alias.isBlank() ? normalize(tableName) + "." : alias + ".";
        return template.formatted(prefix);
    }

    private static String normalize(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String name = tableName.toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
