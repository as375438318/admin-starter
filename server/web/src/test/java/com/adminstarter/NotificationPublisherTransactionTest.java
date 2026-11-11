package com.adminstarter;

import com.adminstarter.system.application.NotificationCommand;
import com.adminstarter.system.application.NotificationPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通知发送的事务语义验证（真实调用方 + 真实数据库，非 mock）。
 * <p>覆盖 {@link NotificationPublisher} 的两种契约：</p>
 * <ul>
 *   <li>{@code publish}：参与调用方事务，调用方回滚则通知不落库；</li>
 *   <li>{@code publishInNewTransaction}：独立事务提交，调用方回滚不影响通知落库
 *       （定时任务失败通知即此场景的真实调用方）。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class NotificationPublisherTransactionTest {

    @Autowired
    private NotificationPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String marker;

    @AfterEach
    void cleanup() {
        if (marker == null) {
            return;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "select id from sys_notice where title = ?", Long.class, marker);
        for (Long id : ids) {
            jdbcTemplate.update("delete from sys_notice_recipient where notice_id = ?", id);
            jdbcTemplate.update("delete from sys_notice_scope where notice_id = ?", id);
            jdbcTemplate.update("delete from sys_notice where id = ?", id);
        }
        marker = null;
    }

    @Test
    void publishRollsBackWithCallerTransaction() {
        marker = "TX-ROLLBACK-" + System.nanoTime();
        String title = marker;

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publish(command(title));
            throw new IllegalStateException("模拟业务失败，验证通知随事务回滚");
        }));

        assertEquals(0, countByTitle(title), "publish 应参与调用方事务，回滚后不落库");
    }

    @Test
    void publishInNewTransactionSurvivesCallerRollback() {
        marker = "TX-NEW-" + System.nanoTime();
        String title = marker;

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publishInNewTransaction(command(title));
            throw new IllegalStateException("模拟业务失败，验证独立事务通知仍然提交");
        }));

        assertEquals(1, countByTitle(title), "publishInNewTransaction 应独立提交，调用方回滚后仍落库");
    }

    private NotificationCommand command(String title) {
        return NotificationCommand.builder()
                .title(title)
                .content("通知事务语义验证（自动化测试生成，结束即清理）")
                .level("NORMAL")
                .type("TASK")
                .revocable(true)
                .scopes(List.of(NotificationCommand.Scope.all()))
                .build();
    }

    private int countByTitle(String title) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_notice where title = ? and deleted = 0", Integer.class, title);
        return count == null ? 0 : count;
    }
}
