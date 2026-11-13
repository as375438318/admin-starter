package com.adminstarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 配置面变更审计（`CR-20261114-042`）。
 *
 * <p>要证明三件事，且只有这三件——多一条都是在替实现做测试：</p>
 * <ol>
 *   <li><b>改配置真的留痕</b>，且带得动 before/after（操作日志没有旧值，这正是补这块的理由）；</li>
 *   <li><b>敏感参数的历史值也打码</b>：只掩新值等于把明文换到旧值里存一遍；</li>
 *   <li><b>审计接口只读且受权限约束</b>：没有写入口；无权限角色 403。</li>
 * </ol>
 */
class ChangeAuditApiTest extends AbstractIntegrationTest {

    /** 写操作必须带合法 Origin（OriginCheckFilter 的 CSRF 防护），否则先撞 20301。 */
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("改参数值 → 审计留痕带 before/after，且按对象键可检索")
    void configUpdateIsAudited() throws Exception {
        Cookie admin = loginAsAdmin();
        Long configId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_config WHERE config_key = 'sys.name' AND deleted = 0", Long.class);
        assertThat(configId).as("内置参数 site.title 必须存在").isNotNull();
        String original = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE id = ?", String.class, configId);

        String newValue = original + "-审计" + System.currentTimeMillis();
        mockMvc.perform(put("/api/v1/configs/" + configId)
                        .cookie(admin)
                        .header("Origin", ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configKey":"sys.name","name":"系统名称","configValue":"%s","remark":"站点名称","isSensitive":false,"isEnabled":true}
                                """.formatted(newValue)))
                .andExpect(status().isOk());

        try {
            MvcResult result = mockMvc.perform(get("/api/v1/change-audits")
                            .cookie(admin)
                            .param("targetType", "CONFIG")
                            .param("targetKey", "sys.name")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.list[0].targetType").value("CONFIG"))
                    .andExpect(jsonPath("$.data.list[0].action").value("UPDATE"))
                    .andExpect(jsonPath("$.data.list[0].operatorName").value("admin"))
                    .andReturn();

            // MockMvc 的 getContentAsString() 默认按 ISO-8859-1 解码，中文会变乱码，必须显式 UTF-8
            JsonNode first = objectMapper.readTree(
                    new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                    .path("data").path("list").path(0);
            String before = first.path("beforeValue").asText();
            String after = first.path("afterValue").asText();
            assertThat(after).contains(newValue).as("变更后应含新值");
            assertThat(before).contains(original).as("变更前必须含旧值——这正是操作日志缺的一半");
            // 只记变化的字段：只改了 configValue，不该把 name/isEnabled 也塞进来
            assertThat(before).doesNotContain("isEnabled");
        } finally {
            // 还原参数，避免污染其它用例（如首页标题断言）
            jdbcTemplate.update("UPDATE sys_config SET config_value = ? WHERE id = ?", original, configId);
        }
    }

    @Test
    @DisplayName("敏感参数的历史值同样打码，不落明文")
    void sensitiveConfigHistoryIsMasked() throws Exception {
        Cookie admin = loginAsAdmin();
        String key = "audit.mask.probe." + System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO sys_config (config_key, config_value, name, remark, is_sensitive, is_enabled, created_at, updated_at, deleted)
                VALUES (?, ?, '审计打码探针', NULL, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0)
                """, key, "SECRET-OLD-1");
        Long configId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_config WHERE config_key = ?", Long.class, key);

        try {
            mockMvc.perform(put("/api/v1/configs/" + configId)
                            .cookie(admin)
                            .header("Origin", ALLOWED_ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"configKey":"%s","name":"审计打码探针","configValue":"SECRET-NEW-2","isSensitive":true,"isEnabled":true}
                                    """.formatted(key)))
                    .andExpect(status().isOk());

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT before_value, after_value, is_sensitive FROM sys_change_audit WHERE target_key = ? ORDER BY id DESC",
                    key);
            assertThat(rows).isNotEmpty();
            Map<String, Object> latest = rows.get(0);
            assertThat(String.valueOf(latest.get("before_value"))).doesNotContain("SECRET-OLD-1");
            assertThat(String.valueOf(latest.get("after_value"))).doesNotContain("SECRET-NEW-2");
            assertThat(String.valueOf(latest.get("after_value"))).contains("******");
        } finally {
            jdbcTemplate.update("DELETE FROM sys_config WHERE config_key = ?", key);
        }
    }

    @Test
    @DisplayName("审计接口只读：没有写入口；无权限角色 403")
    void auditApiIsReadOnlyAndProtected() throws Exception {
        Cookie admin = loginAsAdmin();
        // 读：有权限可读
        mockMvc.perform(get("/api/v1/change-audits").cookie(admin).param("page", "1").param("pageSize", "5"))
                .andExpect(status().isOk());
        // 写：审计没有写入口。这里只断言"任何一个写方法都拿不到 2xx"——
        // 具体是 405 还是 400 由全局异常映射决定，把断言绑死在某个码上等于替实现写测试。
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_change_audit", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/change-audits")
                        .cookie(admin)
                        .header("Origin", ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"CONFIG\"}"))
                .andExpect(status().is4xxClientError());
        long after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_change_audit", Long.class);
        assertThat(after).as("外部调用不能凭空制造审计记录").isEqualTo(before);

        Cookie employee = loginAsEmployee();
        mockMvc.perform(get("/api/v1/change-audits").cookie(employee).param("page", "1").param("pageSize", "5"))
                .andExpect(status().isForbidden());
    }
    private Cookie loginAsAdmin() throws Exception {
        return login("admin", "admin123");
    }

    /** 无 system:audit:list 权限的角色（EMPLOYEE）用于验证 403 */
    private Cookie loginAsEmployee() throws Exception {
        return login("dev1", "admin123");
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if ("ACCESS_TOKEN".equals(cookie.getName())) {
                return cookie;
            }
        }
        throw new IllegalStateException("登录未返回 ACCESS_TOKEN Cookie");
    }
}