package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.domain.query.UserSessionQuery;
import com.adminstarter.system.domain.repository.UserSessionRepository;
import com.adminstarter.system.infrastructure.persistence.converter.UserSessionConverter;
import com.adminstarter.system.infrastructure.persistence.entity.UserSessionDO;
import com.adminstarter.system.infrastructure.persistence.mapper.UserSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserSessionRepositoryImpl implements UserSessionRepository {

    private final UserSessionMapper sessionMapper;
    private final UserSessionConverter converter;

    @Override
    public void insert(UserSession session) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        UserSessionDO row = converter.toDO(session);
        sessionMapper.insert(row);
        session.setId(row.getId());
    }

    @Override
    public UserSession findById(Long id) {
        return converter.toDomain(sessionMapper.selectById(id));
    }

    @Override
    public UserSession findBySessionId(String sessionId) {
        QueryWrapper<UserSessionDO> wrapper = new QueryWrapper<UserSessionDO>().eq("session_id", sessionId);
        return converter.toDomain(sessionMapper.selectOne(wrapper, false));
    }

    @Override
    public PageResult<UserSession> page(UserSessionQuery query, LocalDateTime onlineSince) {
        IPage<UserSessionDO> page = sessionMapper.selectSessionPage(
                new Page<>(query.getPage(), query.getPageSize()), query, onlineSince);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public List<UserSession> findActiveByUserId(Long userId) {
        QueryWrapper<UserSessionDO> wrapper = new QueryWrapper<UserSessionDO>()
                .eq("user_id", userId)
                .isNull("invalidated_at")
                .orderByDesc("last_active_at")
                .orderByDesc("id");
        return sessionMapper.selectList(wrapper).stream().map(converter::toDomain).toList();
    }

    @Override
    public int touch(String sessionId, LocalDateTime lastActiveAt) {
        return sessionMapper.update(null, new UpdateWrapper<UserSessionDO>()
                .eq("session_id", sessionId)
                .isNull("invalidated_at")
                .set("last_active_at", lastActiveAt)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public int invalidateById(Long id, String reason) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return sessionMapper.update(null, new UpdateWrapper<UserSessionDO>()
                .eq("id", id)
                .isNull("invalidated_at")
                .set("invalidated_at", now)
                .set("invalidate_reason", reason)
                .set("updated_at", now));
    }

    @Override
    public int invalidateByUserId(Long userId, String reason, String excludeSessionId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UpdateWrapper<UserSessionDO> wrapper = new UpdateWrapper<UserSessionDO>()
                .eq("user_id", userId)
                .isNull("invalidated_at")
                .set("invalidated_at", now)
                .set("invalidate_reason", reason)
                .set("updated_at", now);
        if (excludeSessionId != null && !excludeSessionId.isBlank()) {
            wrapper.ne("session_id", excludeSessionId);
        }
        return sessionMapper.update(null, wrapper);
    }

    @Override
    public int deleteInvalidatedBefore(LocalDateTime cutoff) {
        QueryWrapper<UserSessionDO> wrapper = new QueryWrapper<UserSessionDO>()
                .isNotNull("invalidated_at")
                .le("invalidated_at", cutoff);
        return sessionMapper.delete(wrapper);
    }
}
