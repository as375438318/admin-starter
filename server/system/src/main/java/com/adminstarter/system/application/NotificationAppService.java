package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.NoticeDtos;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.Notice;
import com.adminstarter.system.domain.model.NoticeInboxItem;
import com.adminstarter.system.domain.model.NoticeReadStat;
import com.adminstarter.system.domain.model.NoticeReader;
import com.adminstarter.system.domain.model.NoticeRecipient;
import com.adminstarter.system.domain.model.NoticeScope;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.query.NoticeQuery;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.NoticeRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知应用服务：站内信渠道的发布、管理端列表/撤回/删除/统计，以及用户端收件箱。
 * <p>发送能力通过 {@link NotificationPublisher} 对模块内其他模块开放，HTTP 发布接口复用同一条路径。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationAppService implements NotificationPublisher {

    public static final Set<String> LEVELS = Set.of("NORMAL", "IMPORTANT", "URGENT");
    public static final Set<String> TYPES = Set.of("SYSTEM", "BUSINESS", "SECURITY", "TASK");
    private static final Set<String> SCOPE_TYPES = Set.of("ALL", "ROLE", "DEPT", "USER");
    private static final Set<String> READ_STATUSES = Set.of("ALL", "UNREAD", "READ");

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DeptRepository deptRepository;

    // ---------------------------------------------------------------- 发送接口

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Long publish(NotificationCommand command) {
        return doPublish(command);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long publishInNewTransaction(NotificationCommand command) {
        return doPublish(command);
    }

    @Transactional
    public NoticeDtos.Response publishAsCurrentUser(NoticeDtos.PublishRequest request) {
        CurrentUser current = DataScopeContext.get();
        NotificationCommand command = NotificationCommand.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .level(request.getLevel())
                .type(request.getType())
                .revocable(request.getRevocable())
                .scopes(request.getScopes() == null ? List.of() : request.getScopes().stream()
                        .map(scope -> NotificationCommand.Scope.builder()
                                .scopeType(scope.getScopeType())
                                .targetId(scope.getTargetId())
                                .build())
                        .toList())
                .publisherId(current == null ? null : current.getUserId())
                .publisherDeptId(current == null ? null : current.getDeptId())
                .build();
        Long id = doPublish(command);
        return getById(id);
    }

    private Long doPublish(NotificationCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "通知内容不能为空");
        }
        String title = command.getTitle() == null ? "" : command.getTitle().trim();
        String content = command.getContent() == null ? "" : command.getContent().trim();
        if (title.isEmpty() || title.length() > 200) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "标题不能为空且不超过 200 字");
        }
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "通知内容不能为空");
        }
        String level = normalizeEnum(command.getLevel(), "NORMAL", LEVELS, "通知级别");
        String type = normalizeEnum(command.getType(), "SYSTEM", TYPES, "通知类型");
        boolean revocable = command.getRevocable() == null || command.getRevocable();

        List<NotificationCommand.Scope> scopes = normalizeScopes(command.getScopes());
        Set<Long> recipients = resolveRecipients(scopes);
        if (recipients.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围内没有可用的启用用户");
        }

        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setLevel(level);
        notice.setType(type);
        notice.setRevocable(revocable);
        notice.setDeptId(command.getPublisherDeptId());
        notice.setCreateBy(command.getPublisherId());
        noticeRepository.insert(notice);

        List<NoticeScope> scopeRows = new ArrayList<>();
        for (NotificationCommand.Scope scope : scopes) {
            NoticeScope row = new NoticeScope();
            row.setNoticeId(notice.getId());
            row.setScopeType(scope.getScopeType());
            row.setTargetId(scope.getTargetId());
            scopeRows.add(row);
        }
        noticeRepository.insertScopes(scopeRows);

        List<NoticeRecipient> recipientRows = recipients.stream().map(userId -> {
            NoticeRecipient row = new NoticeRecipient();
            row.setNoticeId(notice.getId());
            row.setUserId(userId);
            return row;
        }).toList();
        noticeRepository.insertRecipients(recipientRows);

        return notice.getId();
    }

    private List<NotificationCommand.Scope> normalizeScopes(List<NotificationCommand.Scope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围不能为空");
        }
        Map<String, NotificationCommand.Scope> deduped = new LinkedHashMap<>();
        for (NotificationCommand.Scope scope : scopes) {
            if (scope == null || scope.getScopeType() == null) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围类型不能为空");
            }
            String scopeType = scope.getScopeType().trim().toUpperCase();
            if (!SCOPE_TYPES.contains(scopeType)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围类型不合法: " + scope.getScopeType());
            }
            if (scopeType.equals("ALL")) {
                deduped.put("ALL", NotificationCommand.Scope.all());
                continue;
            }
            if (scope.getTargetId() == null) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "指定范围必须选择目标");
            }
            validateTarget(scopeType, scope.getTargetId());
            deduped.put(scopeType + ":" + scope.getTargetId(), NotificationCommand.Scope.builder()
                    .scopeType(scopeType)
                    .targetId(scope.getTargetId())
                    .build());
        }
        return List.copyOf(deduped.values());
    }

    private void validateTarget(String scopeType, Long targetId) {
        switch (scopeType) {
            case "ROLE" -> {
                if (roleRepository.findById(targetId) == null) {
                    throw new BizException(ErrorCode.INVALID_PARAMETER, "指定角色不存在");
                }
            }
            case "DEPT" -> {
                if (deptRepository.findById(targetId) == null) {
                    throw new BizException(ErrorCode.INVALID_PARAMETER, "指定部门不存在");
                }
            }
            case "USER" -> {
                User user = userRepository.findById(targetId);
                if (user == null || Boolean.FALSE.equals(user.getIsEnabled())) {
                    throw new BizException(ErrorCode.INVALID_PARAMETER, "指定用户不存在或已停用");
                }
            }
            default -> throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围类型不合法");
        }
    }

    private Set<Long> resolveRecipients(List<NotificationCommand.Scope> scopes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (NotificationCommand.Scope scope : scopes) {
            switch (scope.getScopeType()) {
                case "ALL" -> ids.addAll(userRepository.findEnabledUserIds());
                case "ROLE" -> ids.addAll(enabledUsers(roleRepository.findUserIdsByRoleId(scope.getTargetId())));
                case "DEPT" -> ids.addAll(userRepository.findEnabledUserIdsByDeptIds(
                        deptRepository.findDescendantIds(scope.getTargetId())));
                case "USER" -> ids.add(scope.getTargetId());
                default -> throw new BizException(ErrorCode.INVALID_PARAMETER, "接收范围类型不合法");
            }
        }
        return ids;
    }

    private List<Long> enabledUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findByIds(userIds).stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsEnabled()))
                .map(User::getId)
                .toList();
    }

    // ---------------------------------------------------------------- 管理端

    public PageResponse<NoticeDtos.Response> page(PageQuery pageQuery, String keyword, String level, String type,
                                                  String status, Long deptId) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        NoticeQuery query = new NoticeQuery();
        query.setKeyword(keyword);
        query.setLevel(blankToNull(level));
        query.setType(blankToNull(type));
        query.setStatus(blankToNull(status));
        query.setDeptId(deptId);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<Notice> result = DataPermissionContext.controlled(() -> noticeRepository.page(query));
        return PageResponse.of(page, pageSize, assemble(result.getList(), false), result.getTotal());
    }

    public NoticeDtos.Response getById(Long id) {
        Notice notice = DataPermissionContext.controlled(() -> noticeRepository.findById(id));
        if (notice == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在或无权访问");
        }
        return assemble(List.of(notice), true).get(0);
    }

    @Transactional
    public NoticeDtos.Response revoke(Long id) {
        Notice notice = DataPermissionContext.controlled(() -> noticeRepository.findById(id));
        if (notice == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在或无权访问");
        }
        if (notice.getRevokedAt() != null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "通知已撤回");
        }
        if (Boolean.FALSE.equals(notice.getRevocable())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该通知不允许撤回");
        }
        CurrentUser current = DataScopeContext.get();
        notice.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        notice.setRevokedBy(current == null ? null : current.getUserId());
        noticeRepository.update(notice);
        return assemble(List.of(notice), true).get(0);
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = DataPermissionContext.controlled(() -> noticeRepository.findById(id));
        if (notice == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在或无权访问");
        }
        noticeRepository.deleteCascade(id);
    }

    public PageResponse<NoticeDtos.ReaderResponse> readers(Long id, PageQuery pageQuery, String readStatus,
                                                          String keyword) {
        Notice notice = DataPermissionContext.controlled(() -> noticeRepository.findById(id));
        if (notice == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在或无权访问");
        }
        String status = blankToNull(readStatus) == null ? "ALL" : readStatus.trim().toUpperCase();
        if (!READ_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "已读状态筛选不合法");
        }
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        PageResult<NoticeReader> result = noticeRepository.pageReaders(
                id, status.equals("ALL") ? null : status, blankToNull(keyword), page, pageSize);
        List<NoticeDtos.ReaderResponse> list = result.getList().stream().map(reader -> {
            NoticeDtos.ReaderResponse response = new NoticeDtos.ReaderResponse();
            response.setUserId(reader.getUserId());
            response.setUsername(reader.getUsername());
            response.setNickname(reader.getNickname());
            response.setDeptName(reader.getDeptName());
            response.setRead(reader.getReadAt() != null);
            response.setReadAt(TimeSupport.toInstant(reader.getReadAt()));
            return response;
        }).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    // ---------------------------------------------------------------- 用户端

    public PageResponse<NoticeDtos.InboxResponse> inbox(Long userId, PageQuery pageQuery, String readStatus) {
        String status = blankToNull(readStatus) == null ? "ALL" : readStatus.trim().toUpperCase();
        if (!READ_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "已读状态筛选不合法");
        }
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        PageResult<NoticeInboxItem> result = noticeRepository.pageInbox(
                userId, status.equals("ALL") ? null : status, page, pageSize);
        List<NoticeDtos.InboxResponse> list = result.getList().stream().map(item -> {
            NoticeDtos.InboxResponse response = new NoticeDtos.InboxResponse();
            response.setId(item.getNoticeId());
            response.setTitle(item.getTitle());
            response.setLevel(item.getLevel());
            response.setType(item.getType());
            response.setPublishedAt(TimeSupport.toInstant(item.getPublishedAt()));
            response.setPublisherName(publisherName(item));
            response.setRead(item.getReadAt() != null);
            response.setReadAt(TimeSupport.toInstant(item.getReadAt()));
            return response;
        }).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    @Transactional
    public NoticeDtos.InboxDetailResponse inboxDetail(Long noticeId, Long userId) {
        NoticeInboxItem item = noticeRepository.findInboxItem(noticeId, userId);
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在、已撤回或无权访问");
        }
        if (item.getReadAt() == null) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            noticeRepository.markRead(noticeId, userId, now);
            item.setReadAt(now);
        }
        NoticeDtos.InboxDetailResponse response = new NoticeDtos.InboxDetailResponse();
        response.setId(item.getNoticeId());
        response.setTitle(item.getTitle());
        response.setContent(item.getContent());
        response.setLevel(item.getLevel());
        response.setType(item.getType());
        response.setPublishedAt(TimeSupport.toInstant(item.getPublishedAt()));
        response.setPublisherName(publisherName(item));
        response.setRead(true);
        response.setReadAt(TimeSupport.toInstant(item.getReadAt()));
        return response;
    }

    public NoticeDtos.UnreadCountResponse unreadCount(Long userId) {
        NoticeDtos.UnreadCountResponse response = new NoticeDtos.UnreadCountResponse();
        response.setCount(noticeRepository.countUnread(userId));
        return response;
    }

    @Transactional
    public NoticeDtos.ReadAllResponse readAll(Long userId) {
        NoticeDtos.ReadAllResponse response = new NoticeDtos.ReadAllResponse();
        response.setMarked(noticeRepository.markAllRead(userId, LocalDateTime.now(ZoneOffset.UTC)));
        return response;
    }

    // ---------------------------------------------------------------- 组装

    private List<NoticeDtos.Response> assemble(List<Notice> notices, boolean includeContent) {
        if (notices.isEmpty()) {
            return List.of();
        }
        List<Long> noticeIds = notices.stream().map(Notice::getId).toList();
        Map<Long, NoticeReadStat> stats = noticeRepository.statsByNoticeIds(noticeIds);

        Map<Long, String> deptNames = new HashMap<>();
        deptRepository.findAll().forEach(dept -> deptNames.put(dept.getId(), dept.getName()));
        Map<Long, Role> roles = new HashMap<>();
        roleRepository.findAll().forEach(role -> roles.put(role.getId(), role));

        Set<Long> userIds = new LinkedHashSet<>();
        notices.stream().map(Notice::getCreateBy).filter(Objects::nonNull).forEach(userIds::add);
        notices.stream().map(Notice::getRevokedBy).filter(Objects::nonNull).forEach(userIds::add);
        List<NoticeScope> allScopes = new ArrayList<>();
        for (Notice notice : notices) {
            List<NoticeScope> scopes = noticeRepository.findScopesByNoticeId(notice.getId());
            allScopes.addAll(scopes);
        }
        allScopes.stream()
                .filter(scope -> "USER".equals(scope.getScopeType()) && scope.getTargetId() != null)
                .map(NoticeScope::getTargetId)
                .forEach(userIds::add);
        Map<Long, User> users = userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Map<Long, List<NoticeScope>> scopesByNotice = allScopes.stream()
                .collect(Collectors.groupingBy(NoticeScope::getNoticeId));

        List<NoticeDtos.Response> list = new ArrayList<>();
        for (Notice notice : notices) {
            NoticeDtos.Response response = new NoticeDtos.Response();
            response.setId(notice.getId());
            response.setTitle(notice.getTitle());
            response.setContent(includeContent ? notice.getContent() : null);
            response.setLevel(notice.getLevel());
            response.setType(notice.getType());
            response.setRevocable(notice.getRevocable());
            response.setRevoked(notice.getRevokedAt() != null);
            response.setPublishedAt(TimeSupport.toInstant(notice.getCreatedAt()));
            response.setCreatedAt(TimeSupport.toInstant(notice.getCreatedAt()));
            response.setPublisherId(notice.getCreateBy());
            User publisher = notice.getCreateBy() == null ? null : users.get(notice.getCreateBy());
            response.setPublisherName(publisher == null ? "系统" : displayName(publisher));
            response.setDeptId(notice.getDeptId());
            response.setDeptName(notice.getDeptId() == null ? null : deptNames.get(notice.getDeptId()));
            response.setRevokedAt(TimeSupport.toInstant(notice.getRevokedAt()));
            response.setRevokedBy(notice.getRevokedBy());
            User revoker = notice.getRevokedBy() == null ? null : users.get(notice.getRevokedBy());
            response.setRevokedByName(revoker == null ? null : displayName(revoker));

            List<NoticeDtos.ScopeResponse> scopeResponses = scopesByNotice
                    .getOrDefault(notice.getId(), List.of()).stream()
                    .map(scope -> {
                        NoticeDtos.ScopeResponse item = new NoticeDtos.ScopeResponse();
                        item.setScopeType(scope.getScopeType());
                        item.setTargetId(scope.getTargetId());
                        item.setTargetName(scopeTargetName(scope, roles, deptNames, users));
                        return item;
                    }).toList();
            response.setScopes(scopeResponses);

            NoticeReadStat stat = stats.get(notice.getId());
            NoticeDtos.ReadStats readStats = new NoticeDtos.ReadStats();
            readStats.setTotalRecipients(stat == null ? 0 : stat.getTotalRecipients());
            readStats.setReadCount(stat == null ? 0 : stat.getReadCount());
            readStats.setUnreadCount(stat == null ? 0 : stat.getTotalRecipients() - stat.getReadCount());
            response.setStats(readStats);
            list.add(response);
        }
        return list;
    }

    private String scopeTargetName(NoticeScope scope, Map<Long, Role> roles, Map<Long, String> deptNames,
                                   Map<Long, User> users) {
        return switch (scope.getScopeType()) {
            case "ALL" -> "全员";
            case "ROLE" -> {
                Role role = roles.get(scope.getTargetId());
                yield role == null ? "角色已删除" : role.getName();
            }
            case "DEPT" -> {
                String name = deptNames.get(scope.getTargetId());
                yield name == null ? "部门已删除" : name + "（含下级）";
            }
            case "USER" -> {
                User user = users.get(scope.getTargetId());
                yield user == null ? "用户已删除" : displayName(user);
            }
            default -> null;
        };
    }

    private String publisherName(NoticeInboxItem item) {
        return item.getPublisherName() == null ? "系统" : item.getPublisherName();
    }

    private String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname();
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String label) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, label + "不合法: " + value);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
