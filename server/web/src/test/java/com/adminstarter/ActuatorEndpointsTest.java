package com.adminstarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 可运维性探针（`CR-20261114-044`）。
 *
 * <p>验两件部署时真的会用到的事：<br>
 * ① <b>readiness / liveness 分开暴露</b>——容器编排按不同策略"摘流量"与"重启"；<br>
 * ② <b>health 详情不对外泄露</b>——未授权只给 status，避免暴露 DB/Redis 拓扑与错误信息。</p>
 *
 * <p><b>为什么这里没有断言 /actuator/prometheus</b>：在测试上下文里它不会注册
 * （诊断确认：`webEndpointDiscoverer -> [health, info]`，且没有 `PrometheusMeterRegistry` bean），
 * 而**真实进程是正常的**（打成 jar 运行后 curl 返回 200、约 41KB 指标）。
 * 与其写一个"为了绿而 mock 掉"的假测试，不如把这条放到部署冒烟里：
 * `scripts/smoke-deployment.ps1` 会打真实实例的 readiness + prometheus，
 * 断言脚本本身在无指标时失败。</p>
 */
class ActuatorEndpointsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("readiness/liveness 探针可用（编排靠它们决定是否接流量）")
    void exposesProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("health 详情不对外泄露（未授权只给 status）")
    void healthDoesNotLeakDetails() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // show-details: when-authorized → 匿名只看到 status
        assertThat(body).doesNotContain("\"db\"");
        assertThat(body).doesNotContain("\"redis\"");
    }
}
