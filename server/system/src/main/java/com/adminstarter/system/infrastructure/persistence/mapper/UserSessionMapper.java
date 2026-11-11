package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.domain.query.UserSessionQuery;
import com.adminstarter.system.infrastructure.persistence.DataPermissionExempt;
import com.adminstarter.system.infrastructure.persistence.entity.UserSessionDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@DataPermissionExempt(reason = "会话表不受数据权限控制；管理端由 system:session:list 拦截，按 user_id 普通筛选")
public interface UserSessionMapper extends BaseMapper<UserSessionDO> {

    @Select("""
            <script>
            select s.id, s.session_id, s.user_id, s.username, s.login_at, s.last_active_at,
                   s.ip, s.user_agent, s.device_type, s.browser, s.os,
                   s.invalidated_at, s.invalidate_reason, s.created_at, s.updated_at,
                   u.nickname as nickname
            from sys_user_session s
            left join sys_user u on u.id = s.user_id and u.deleted = 0
            where 1 = 1
            <if test="query.userId != null"> and s.user_id = #{query.userId}</if>
            <if test="query.keyword != null and query.keyword != ''">
                and (s.username like concat('%', #{query.keyword}, '%')
                     or u.nickname like concat('%', #{query.keyword}, '%'))
            </if>
            <if test="query.online != null and query.online == true">
                and s.invalidated_at is null and s.last_active_at &gt;= #{onlineSince}
            </if>
            <if test="query.online != null and query.online == false">
                and (s.invalidated_at is not null or s.last_active_at &lt; #{onlineSince})
            </if>
            order by s.last_active_at desc, s.id desc
            </script>
            """)
    IPage<UserSessionDO> selectSessionPage(Page<UserSessionDO> page,
                                           @Param("query") UserSessionQuery query,
                                           @Param("onlineSince") LocalDateTime onlineSince);
}
