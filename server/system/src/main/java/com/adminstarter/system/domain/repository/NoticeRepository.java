package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Notice;
import com.adminstarter.system.domain.model.NoticeInboxItem;
import com.adminstarter.system.domain.model.NoticeReadStat;
import com.adminstarter.system.domain.model.NoticeReader;
import com.adminstarter.system.domain.model.NoticeRecipient;
import com.adminstarter.system.domain.model.NoticeScope;
import com.adminstarter.system.domain.query.NoticeQuery;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NoticeRepository {

    void insert(Notice notice);

    void update(Notice notice);

    Notice findById(Long id);

    PageResult<Notice> page(NoticeQuery query);

    /** 逻辑删除通知，并级联逻辑删除范围与快照。 */
    void deleteCascade(Long noticeId);

    void insertScopes(List<NoticeScope> scopes);

    List<NoticeScope> findScopesByNoticeId(Long noticeId);

    void insertRecipients(List<NoticeRecipient> recipients);

    /** 按通知 id 汇总应读/已读人数；分页列表一次取回，避免逐行查询。 */
    Map<Long, NoticeReadStat> statsByNoticeIds(Collection<Long> noticeIds);

    PageResult<NoticeReader> pageReaders(Long noticeId, String readStatus, String keyword, int page, int pageSize);

    PageResult<NoticeInboxItem> pageInbox(Long userId, String readStatus, int page, int pageSize);

    NoticeInboxItem findInboxItem(Long noticeId, Long userId);

    long countUnread(Long userId);

    /** 幂等标记单条已读；返回本次更新行数。 */
    int markRead(Long noticeId, Long userId, LocalDateTime now);

    /** 标记本人全部未读（排除已撤回/已删除）；返回本次更新行数。 */
    int markAllRead(Long userId, LocalDateTime now);
}
