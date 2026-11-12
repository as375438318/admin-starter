package com.adminstarter;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：统一 {@code @SpringBootTest} + {@code @AutoConfigureMockMvc} + profile {@code test}。
 * <p>Spring Test 的上下文缓存按**注解组合**做 key：子类注解不一致就会各起一个上下文。
 * 之前两个测试类注释不同（一个带 @AutoConfigureMockMvc、一个不带），实测各起一次上下文、
 * 每次约 8–10s；统一继承本类后共用一个上下文，新增测试类不再重复付出启动成本。</p>
 * <p>新增集成测试请继承本类，不要各自重复写这三个注解。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 把内置 admin 恢复成"口令已就位、不需要改密"的状态。
     *
     * <p>为什么必须在测试里做：`V17__password_policy.sql` 会把**所有存量账号**置为
     * `must_change_password = 1`（它们的口令都产生于无策略时代）。这会让 admin 登录后拿到的
     * access token 带上 `pwd` 声明，于是除"改密/登出/刷新/看自己"以外的接口全部 403 + 20302——
     * 契约测试要验证的是**正常已登录用户**的响应，必须先把这面旗降下来。</p>
     *
     * <p>测试库跨次运行持久化，所以这里是幂等 UPDATE，不依赖执行顺序。
     * 真正"必须改密"的拦截行为由 {@link PasswordChangeEnforcementTest} 用临时账号验证。</p>
     */
    @BeforeEach
    void settleAdminPasswordState() {
        jdbcTemplate.update(
                "UPDATE sys_user SET must_change_password = 0, password_updated_at = NOW() "
                        + "WHERE username = 'admin' AND deleted = 0");
    }
}
