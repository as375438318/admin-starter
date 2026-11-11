package com.adminstarter;

import com.adminstarter.system.application.NotificationCommand;
import com.adminstarter.system.application.NotificationPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知发送的事务语义验证（真实调用方 + 真实数据库，非 mock）。
 * <p>覆盖 {@link NotificationPublisher} 的两种契约：</p>
 * <ul>
 *   <li>{@code publish}：参与调用方事务，调用方回滚则通知不落库；</li>
 *   <li>{@code publishInNewTransaction}：独立事务提交，调用方回滚不影响通知落库
 *       （定时任务失败通知即此场景的真实调用方）。</li>
 * </ul>
 * <p>运行前置：MySQL 与 Redis 可用；profile {@code test} 指向独立库 {@code admin_starter_test}
 * （Flyway 自动建表）。本地默认 127.0.0.1:3307，CI 通过环境变量注入服务容器地址。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationPublisherTransactionTest {

    @Autowired
    private NotificationPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private String marker;

    /**
     * 安全阀：测试库名必须含 test。
     * <p>这条断言是为了防止 profile/环境变量配错时把测试数据写进开发库或生产库——
     * 断言失败比"静默写脏数据"安全得多。</p>
     */
    @BeforeEach
    void requireTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url != null && url.toLowerCase().contains("test"),
                    "拒绝在非测试库上运行集成测试，当前连接：" + url
                            + "（用 spring.datasource.url / APP_TEST_DB_URL 指向 *_test 库）");
        }
    }

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
