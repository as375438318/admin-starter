package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.domain.model.NoticeInboxItem;
import com.adminstarter.system.domain.model.NoticeReadStat;
import com.adminstarter.system.domain.model.NoticeReader;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NoticeMapper extends BaseMapper<NoticeDO> {

    @Select("""
            <script>
            select n.id as noticeId, n.title, n.level, n.type, n.created_at as publishedAt,
                   n.create_by as publisherId, u.nickname as publisherName, r.read_at as readAt
            from sys_notice_recipient r
            join sys_notice n on n.id = r.notice_id
            left join sys_user u on u.id = n.create_by
            where r.user_id = #{userId} and r.deleted = 0 and n.deleted = 0 and n.revoked_at is null
            <if test="readStatus == 'UNREAD'"> and r.read_at is null</if>
            <if test="readStatus == 'READ'"> and r.read_at is not null</if>
            order by n.created_at desc, n.id desc
            </script>
            """)
    IPage<NoticeInboxItem> selectInbox(Page<NoticeInboxItem> page,
                                       @Param("userId") Long userId,
                                       @Param("readStatus") String readStatus);

    @Select("""
            select n.id as noticeId, n.title, n.content, n.level, n.type, n.created_at as publishedAt,
                   n.create_by as publisherId, u.nickname as publisherName, r.read_at as readAt
            from sys_notice_recipient r
            join sys_notice n on n.id = r.notice_id
            left join sys_user u on u.id = n.create_by
            where r.notice_id = #{noticeId} and r.user_id = #{userId}
              and r.deleted = 0 and n.deleted = 0 and n.revoked_at is null
            limit 1
            """)
    NoticeInboxItem selectInboxItem(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Select("""
            select count(*)
            from sys_notice_recipient r
            join sys_notice n on n.id = r.notice_id
            where r.user_id = #{userId} and r.deleted = 0 and r.read_at is null
              and n.deleted = 0 and n.revoked_at is null
            """)
    long countUnread(@Param("userId") Long userId);

    @Update("""
            update sys_notice_recipient
            set read_at = #{now}, updated_at = #{now}
            where notice_id = #{noticeId} and user_id = #{userId} and deleted = 0 and read_at is null
            """)
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update sys_notice_recipient r
            join sys_notice n on n.id = r.notice_id
            set r.read_at = #{now}, r.updated_at = #{now}
            where r.user_id = #{userId} and r.deleted = 0 and r.read_at is null
              and n.deleted = 0 and n.revoked_at is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            <script>
            select notice_id as noticeId, count(*) as totalRecipients,
                   cast(sum(case when read_at is not null then 1 else 0 end) as signed) as readCount
            from sys_notice_recipient
            where deleted = 0 and notice_id in
            <foreach collection="noticeIds" item="noticeId" open="(" separator="," close=")">#{noticeId}</foreach>
            group by notice_id
            </script>
            """)
    List<NoticeReadStat> selectStatsByNoticeIds(@Param("noticeIds") Collection<Long> noticeIds);

    @Select("""
            <script>
            select r.user_id as userId, u.username, u.nickname, d.name as deptName, r.read_at as readAt
            from sys_notice_recipient r
            join sys_user u on u.id = r.user_id
            left join sys_dept d on d.id = u.dept_id
            where r.notice_id = #{noticeId} and r.deleted = 0
            <if test="readStatus == 'READ'"> and r.read_at is not null</if>
            <if test="readStatus == 'UNREAD'"> and r.read_at is null</if>
            <if test="keyword != null and keyword != ''">
              and (u.username like concat('%', #{keyword}, '%') or u.nickname like concat('%', #{keyword}, '%'))
            </if>
            order by r.read_at is null, r.read_at desc, r.id
            </script>
            """)
    IPage<NoticeReader> selectReaders(Page<NoticeReader> page,
                                      @Param("noticeId") Long noticeId,
                                      @Param("readStatus") String readStatus,
                                      @Param("keyword") String keyword);

    @Update("update sys_notice_scope set deleted = 1, updated_at = UTC_TIMESTAMP() where notice_id = #{noticeId} and deleted = 0")
    int deleteScopesByNoticeId(@Param("noticeId") Long noticeId);

    @Update("update sys_notice_recipient set deleted = 1, updated_at = UTC_TIMESTAMP() where notice_id = #{noticeId} and deleted = 0")
    int deleteRecipientsByNoticeId(@Param("noticeId") Long noticeId);
}
