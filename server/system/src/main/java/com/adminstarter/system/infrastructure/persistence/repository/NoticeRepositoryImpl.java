package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Notice;
import com.adminstarter.system.domain.model.NoticeInboxItem;
import com.adminstarter.system.domain.model.NoticeReadStat;
import com.adminstarter.system.domain.model.NoticeReader;
import com.adminstarter.system.domain.model.NoticeRecipient;
import com.adminstarter.system.domain.model.NoticeScope;
import com.adminstarter.system.domain.query.NoticeQuery;
import com.adminstarter.system.domain.repository.NoticeRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.NoticeConverter;
import com.adminstarter.system.infrastructure.persistence.converter.NoticeRecipientConverter;
import com.adminstarter.system.infrastructure.persistence.converter.NoticeScopeConverter;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeDO;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeRecipientDO;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeScopeDO;
import com.adminstarter.system.infrastructure.persistence.mapper.NoticeMapper;
import com.adminstarter.system.infrastructure.persistence.mapper.NoticeRecipientMapper;
import com.adminstarter.system.infrastructure.persistence.mapper.NoticeScopeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "title", "title",
            "id", "id"
    );

    private final NoticeMapper noticeMapper;
    private final NoticeScopeMapper noticeScopeMapper;
    private final NoticeRecipientMapper noticeRecipientMapper;
    private final NoticeConverter converter;
    private final NoticeScopeConverter scopeConverter;
    private final NoticeRecipientConverter recipientConverter;

    @Override
    public void insert(Notice notice) {
        NoticeDO row = converter.toDO(notice);
        noticeMapper.insert(row);
        notice.setId(row.getId());
        notice.setCreatedAt(row.getCreatedAt());
        notice.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Notice notice) {
        noticeMapper.updateById(converter.toDO(notice));
    }

    @Override
    public Notice findById(Long id) {
        return converter.toDomain(noticeMapper.selectById(id));
    }

    @Override
    public PageResult<Notice> page(NoticeQuery query) {
        QueryWrapper<NoticeDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like("title", query.getKeyword());
        }
        if (query.getLevel() != null && !query.getLevel().isBlank()) {
            wrapper.eq("level", query.getLevel());
        }
        if (query.getType() != null && !query.getType().isBlank()) {
            wrapper.eq("type", query.getType());
        }
        if ("PUBLISHED".equals(query.getStatus())) {
            wrapper.isNull("revoked_at");
        } else if ("REVOKED".equals(query.getStatus())) {
            wrapper.isNotNull("revoked_at");
        }
        if (query.getDeptId() != null) {
            wrapper.eq("dept_id", query.getDeptId());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column).orderByDesc("id");
        Page<NoticeDO> page = noticeMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public void deleteCascade(Long noticeId) {
        noticeMapper.deleteScopesByNoticeId(noticeId);
        noticeMapper.deleteRecipientsByNoticeId(noticeId);
        noticeMapper.deleteById(noticeId);
    }

    @Override
    public void insertScopes(List<NoticeScope> scopes) {
        scopes.forEach(scope -> noticeScopeMapper.insert(scopeConverter.toDO(scope)));
    }

    @Override
    public List<NoticeScope> findScopesByNoticeId(Long noticeId) {
        QueryWrapper<NoticeScopeDO> wrapper = new QueryWrapper<NoticeScopeDO>().eq("notice_id", noticeId);
        return noticeScopeMapper.selectList(wrapper).stream().map(scopeConverter::toDomain).toList();
    }

    @Override
    public void insertRecipients(List<NoticeRecipient> recipients) {
        recipients.forEach(recipient -> noticeRecipientMapper.insert(recipientConverter.toDO(recipient)));
    }

    @Override
    public Map<Long, NoticeReadStat> statsByNoticeIds(Collection<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        return noticeMapper.selectStatsByNoticeIds(noticeIds).stream()
                .collect(Collectors.toMap(NoticeReadStat::getNoticeId, Function.identity()));
    }

    @Override
    public PageResult<NoticeReader> pageReaders(Long noticeId, String readStatus, String keyword, int page, int pageSize) {
        IPage<NoticeReader> result = noticeMapper.selectReaders(new Page<>(page, pageSize), noticeId, readStatus, keyword);
        return PageResult.of(result.getRecords(), result.getTotal());
    }

    @Override
    public PageResult<NoticeInboxItem> pageInbox(Long userId, String readStatus, int page, int pageSize) {
        IPage<NoticeInboxItem> result = noticeMapper.selectInbox(new Page<>(page, pageSize), userId, readStatus);
        return PageResult.of(result.getRecords(), result.getTotal());
    }

    @Override
    public NoticeInboxItem findInboxItem(Long noticeId, Long userId) {
        return noticeMapper.selectInboxItem(noticeId, userId);
    }

    @Override
    public long countUnread(Long userId) {
        return noticeMapper.countUnread(userId);
    }

    @Override
    public int markRead(Long noticeId, Long userId, LocalDateTime now) {
        return noticeMapper.markRead(noticeId, userId, now);
    }

    @Override
    public int markAllRead(Long userId, LocalDateTime now) {
        return noticeMapper.markAllRead(userId, now);
    }
}
