package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.Notice;
import com.adminstarter.system.domain.model.NoticeRecipient;
import com.adminstarter.system.domain.model.NoticeScope;
import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.NoticeRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知投递：接收范围归一化 + 收件人解析。
 * <p>"谁收到通知"错了就是数据泄露或漏发，且发出去后不可撤回，所以这里锁三件事：</p>
 * <ol>
 *   <li>范围必须真的被校验（不存在的角色/部门、停用用户一律拒绝，不能静默降级成"发给所有人"）；</li>
 *   <li>收件人解析正确（DEPT 含子孙部门、ROLE 过滤停用用户、多范围取并集去重）；</li>
 *   <li>范围内无人时必须报错，禁止落一条没人收得到的通知。</li>
 * </ol>
 */
class NotificationPublishTest {

    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final DeptRepository deptRepository = mock(DeptRepository.class);

    private final NotificationAppService service = new NotificationAppService(
            noticeRepository, userRepository, roleRepository, deptRepository);

    private static User user(long id, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setIsEnabled(enabled);
        return user;
    }

    private static NotificationCommand.NotificationCommandBuilder validCommand() {
        return NotificationCommand.builder()
                .title("系统维护通知")
                .content("今晚 22:00 停机维护")
                .scopes(List.of(NotificationCommand.Scope.all()));
    }

    private static String messageOf(BizException exception) {
        return exception.getMessage();
    }

    // ---------------------------------------------------------------- 入参校验

    @Test
    @DisplayName("标题为空或超过 200 字 → 拒绝")
    void titleIsRequiredAndBounded() {
        BizException empty = assertThrows(BizException.class, () -> service.publish(
                validCommand().title("   ").build()));
        assertEquals(ErrorCode.INVALID_PARAMETER, empty.getErrorCode());

        BizException tooLong = assertThrows(BizException.class, () -> service.publish(
                validCommand().title("x".repeat(201)).build()));
        assertEquals(ErrorCode.INVALID_PARAMETER, tooLong.getErrorCode());

        verify(noticeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("内容为空 → 拒绝")
    void contentIsRequired() {
        BizException error = assertThrows(BizException.class, () -> service.publish(
                validCommand().content("  ").build()));
        assertTrue(messageOf(error).contains("内容"));
        verify(noticeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("接收范围为空 → 拒绝（绝不允许「默认发给所有人」）")
    void emptyScopesAreRejected() {
        BizException error = assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of()).build()));
        assertTrue(messageOf(error).contains("接收范围不能为空"));
        verify(noticeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("范围类型非法（如 TEAM）→ 拒绝")
    void unknownScopeTypeIsRejected() {
        BizException error = assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(NotificationCommand.Scope.builder()
                        .scopeType("TEAM").targetId(1L).build())).build()));
        assertTrue(messageOf(error).contains("不合法"));
    }

    @Test
    @DisplayName("指定范围未给目标 ID → 拒绝")
    void targetIsRequiredForNonAllScopes() {
        BizException error = assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(NotificationCommand.Scope.builder()
                        .scopeType("DEPT").build())).build()));
        assertTrue(messageOf(error).contains("必须选择目标"));
    }

    @Test
    @DisplayName("角色/部门不存在、用户停用 → 拒绝（不能把不存在目标当成「空范围」静默通过）")
    void missingOrDisabledTargetsAreRejected() {
        when(roleRepository.findById(11L)).thenReturn(null);
        when(deptRepository.findById(22L)).thenReturn(null);
        when(userRepository.findById(33L)).thenReturn(user(33L, false));

        assertTrue(messageOf(assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(NotificationCommand.Scope.ofRole(11L))).build())))
                .contains("角色不存在"));

        assertTrue(messageOf(assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(NotificationCommand.Scope.ofDept(22L))).build())))
                .contains("部门不存在"));

        assertTrue(messageOf(assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(NotificationCommand.Scope.ofUser(33L))).build())))
                .contains("不存在或已停用"));
    }

    @Test
    @DisplayName("范围内没有可用的启用用户 → 拒绝（禁止落一条没人收得到的通知）")
    void emptyRecipientsAreRejected() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of());

        BizException error = assertThrows(BizException.class, () -> service.publish(validCommand().build()));

        assertTrue(messageOf(error).contains("没有可用的启用用户"));
        verify(noticeRepository, never()).insert(any());
    }

    // ---------------------------------------------------------------- 收件人解析

    @Test
    @DisplayName("ALL → 展开为全部启用用户")
    void allScopeResolvesEnabledUsers() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of(1L, 2L, 3L));

        service.publish(validCommand().build());

        assertEquals(List.of(1L, 2L, 3L), capturedRecipientIds());
    }

    @Test
    @DisplayName("ROLE → 用角色下用户，且过滤掉停用用户")
    void roleScopeFiltersDisabledUsers() {
        Role role = new Role();
        role.setId(11L);
        when(roleRepository.findById(11L)).thenReturn(role);
        when(roleRepository.findUserIdsByRoleId(11L)).thenReturn(List.of(4L, 5L));
        when(userRepository.findByIds(List.of(4L, 5L))).thenReturn(List.of(user(4L, true), user(5L, false)));

        service.publish(validCommand().scopes(List.of(NotificationCommand.Scope.ofRole(11L))).build());

        assertEquals(List.of(4L), capturedRecipientIds(), "停用用户不得收到通知");
    }

    @Test
    @DisplayName("DEPT → 展开为子孙部门下的启用用户（含本部门）")
    void deptScopeExpandsDescendants() {
        Dept dept = new Dept();
        dept.setId(22L);
        when(deptRepository.findById(22L)).thenReturn(dept);
        when(deptRepository.findDescendantIds(22L)).thenReturn(List.of(22L, 23L));
        when(userRepository.findEnabledUserIdsByDeptIds(List.of(22L, 23L))).thenReturn(List.of(6L, 7L));

        service.publish(validCommand().scopes(List.of(NotificationCommand.Scope.ofDept(22L))).build());

        assertEquals(List.of(6L, 7L), capturedRecipientIds());
    }

    @Test
    @DisplayName("USER → 直接作为收件人")
    void userScopeAddsTarget() {
        when(userRepository.findById(33L)).thenReturn(user(33L, true));

        service.publish(validCommand().scopes(List.of(NotificationCommand.Scope.ofUser(33L))).build());

        assertEquals(List.of(33L), capturedRecipientIds());
    }

    @Test
    @DisplayName("多范围取并集并去重：同一用户既在部门里又在指定人员里，只落一条收件记录")
    void multipleScopesUnionAndDedupe() {
        when(userRepository.findEnabledUserIdsByDeptIds(anyList())).thenReturn(List.of(8L, 9L));
        when(userRepository.findById(9L)).thenReturn(user(9L, true));
        Dept dept = new Dept();
        dept.setId(22L);
        when(deptRepository.findById(22L)).thenReturn(dept);
        when(deptRepository.findDescendantIds(22L)).thenReturn(List.of(22L));

        service.publish(validCommand().scopes(List.of(
                NotificationCommand.Scope.ofDept(22L),
                NotificationCommand.Scope.ofUser(9L))).build());

        assertEquals(List.of(8L, 9L), capturedRecipientIds(), "重复用户只保留一条");
    }

    @Test
    @DisplayName("ALL 与其它范围混用 → 取并集（ALL 不吞掉其它范围，收件人仍含指定人员）")
    void allScopeUnionsWithOtherScopes() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of(1L, 2L));
        when(userRepository.findById(99L)).thenReturn(user(99L, true));

        service.publish(validCommand().scopes(List.of(
                NotificationCommand.Scope.ofUser(99L),
                NotificationCommand.Scope.all())).build());

        assertEquals(List.of(99L, 1L, 2L), capturedRecipientIds());
    }

    @Test
    @DisplayName("即使带了 ALL，其它范围的目标仍必须存在（fail-closed，不因 ALL 放过脏目标）")
    void invalidTargetIsRejectedEvenWhenAllScopePresent() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of(1L));
        when(userRepository.findById(99L)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.publish(
                validCommand().scopes(List.of(
                        NotificationCommand.Scope.all(),
                        NotificationCommand.Scope.ofUser(99L))).build()));

        assertTrue(messageOf(error).contains("不存在或已停用"));
        verify(noticeRepository, never()).insert(any());
    }

    // ---------------------------------------------------------------- 落库形态

    @Test
    @DisplayName("默认值：级别 NORMAL、类型 SYSTEM、可撤回；发布人写入 notice")
    void defaultsAndPublisherAreApplied() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of(1L));

        service.publish(validCommand().publisherId(77L).publisherDeptId(88L).build());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).insert(captor.capture());
        Notice notice = captor.getValue();
        assertEquals("NORMAL", notice.getLevel());
        assertEquals("SYSTEM", notice.getType());
        assertTrue(notice.getRevocable());
        assertEquals(77L, notice.getCreateBy());
        assertEquals(88L, notice.getDeptId());
    }

    @Test
    @DisplayName("范围与收件人各自落库：scope 行数 = 去重后的范围数，recipient 行数 = 收件人数")
    void scopesAndRecipientsArePersisted() {
        when(userRepository.findEnabledUserIds()).thenReturn(List.of(1L, 2L));

        service.publish(validCommand().scopes(List.of(
                NotificationCommand.Scope.all(),
                NotificationCommand.Scope.all())).build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoticeScope>> scopes = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoticeRecipient>> recipients = ArgumentCaptor.forClass(List.class);
        verify(noticeRepository).insertScopes(scopes.capture());
        verify(noticeRepository).insertRecipients(recipients.capture());

        assertEquals(1, scopes.getValue().size(), "重复的 ALL 范围应去重");
        assertEquals(2, recipients.getValue().size());
        assertTrue(recipients.getValue().stream().allMatch(row -> row.getReadAt() == null),
                "新通知的收件记录不得带已读时间");
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedRecipientIds() {
        ArgumentCaptor<List<NoticeRecipient>> captor = ArgumentCaptor.forClass(List.class);
        verify(noticeRepository).insertRecipients(captor.capture());
        return captor.getValue().stream().map(NoticeRecipient::getUserId).toList();
    }
}
