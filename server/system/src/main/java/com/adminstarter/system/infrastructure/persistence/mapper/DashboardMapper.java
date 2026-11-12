package com.adminstarter.system.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合查询。
 *
 * <p><b>为什么不复用各业务 Mapper 的 selectCount</b>：那样要注入 12 个 Mapper 且会触发数据权限拦截器，
 * 让"系统级聚合"这个口径变得含糊。这里集中写显式 SQL，口径一眼可查；代价是不随数据权限收窄（已文档化）。</p>
 *
 * <p>所有计数都只取 COUNT/SUM，不返回明细。</p>
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countUsers();

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND is_enabled = 1")
    long countEnabledUsers();

    @Select("SELECT COUNT(*) FROM sys_role WHERE deleted = 0")
    long countRoles();

    @Select("SELECT COUNT(*) FROM sys_dept WHERE deleted = 0")
    long countDepts();

    @Select("SELECT COUNT(*) FROM sys_post WHERE deleted = 0")
    long countPosts();

    @Select("SELECT COUNT(*) FROM sys_dict_type WHERE deleted = 0")
    long countDictTypes();

    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE deleted = 0")
    long countDictItems();

    @Select("SELECT COUNT(*) FROM sys_config WHERE deleted = 0")
    long countConfigs();

    /** 生效中的通知：未删除且未撤回（与列表页 PUBLISHED 口径一致） */
    @Select("SELECT COUNT(*) FROM sys_notice WHERE deleted = 0 AND revoked_at IS NULL")
    long countNotices();

    @Select("SELECT COUNT(*) FROM sys_job WHERE deleted = 0")
    long countJobs();

    @Select("SELECT COUNT(*) FROM sys_job WHERE deleted = 0 AND status = 'ENABLED'")
    long countEnabledJobs();

    /** 在线的会话：未失效且最近 30 分钟内有活跃（与会话页"在线"口径一致） */
    @Select("SELECT COUNT(*) FROM sys_user_session"
            + " WHERE invalidated_at IS NULL AND last_active_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE)")
    long countActiveSessions();

    @Select("SELECT COUNT(*) FROM sys_file WHERE deleted = 0")
    long countFiles();

    @Select("SELECT COALESCE(SUM(size), 0) FROM sys_file WHERE deleted = 0")
    long sumFileBytes();

    @Select("SELECT COUNT(*) FROM sys_login_log WHERE is_success = 1 AND created_at >= UTC_DATE()")
    long countTodayLoginSuccess();

    @Select("SELECT COUNT(*) FROM sys_login_log WHERE is_success = 0 AND created_at >= UTC_DATE()")
    long countTodayLoginFailure();

    @Select("SELECT COUNT(*) FROM sys_oper_log WHERE created_at >= UTC_DATE()")
    long countTodayOperations();

    /** 近 N 天登录趋势（按自然日，缺日不补——补零由应用层做，SQL 保持简单） */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS total"
            + " FROM sys_login_log WHERE created_at >= DATE_SUB(UTC_DATE(), INTERVAL #{days} DAY)"
            + " GROUP BY day")
    List<Map<String, Object>> countLoginsByDay(@Param("days") int days);

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS total"
            + " FROM sys_oper_log WHERE created_at >= DATE_SUB(UTC_DATE(), INTERVAL #{days} DAY)"
            + " GROUP BY day")
    List<Map<String, Object>> countOperationsByDay(@Param("days") int days);

    /** 示例任务按状态分组（演示数据权限的业务表，聚合仍是全局口径） */
    @Select("SELECT status, COUNT(*) AS total FROM biz_demo_task WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countDemoTasksByStatus();
}