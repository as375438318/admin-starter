package com.adminstarter;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
}
