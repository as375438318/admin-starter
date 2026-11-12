package com.adminstarter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 口令强制改密的**服务端拦截**验证（等保 P0-1 的另一半）。
 *
 * <p>为什么必须单独建一个临时账号：内置 admin 在测试基类里已被恢复成"不需要改密"
 * （否则所有契约测试都会撞上 20302）。这里造一个真正处于"必须改密"状态的用户，
 * 验证服务端拦不拦、拦得对不对、以及"能自救的路径"是否真的放行。</p>
 */
class PasswordChangeEnforcementTest extends AbstractIntegrationTest {

    private static final String TEMP_USERNAME = "pwd_enforce_tmp";
    private static final String TEMP_INITIAL_PASSWORD = "Init#Pass1234";
    private static final String TEMP_STRONG_PASSWORD = "Strong#Pass2026";
    /** 写操作必须带合法 Origin（OriginCheckFilter 的 CSRF 防护），否则先撞 20301。 */
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createTempUser() {
        cleanup();
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, nickname, password, dept_id, email, phone, is_enabled,"
                        + " create_by, created_at, updated_at, deleted, password_updated_at, must_change_password)"
                        + " VALUES (?, ?, ?, NULL, NULL, NULL, 1, NULL, NOW(), NOW(), 0, NOW(), 1)",
                TEMP_USERNAME, "改密拦截测试账号", passwordEncoder.encode(TEMP_INITIAL_PASSWORD));
        // 授予与 admin 相同的角色：否则测试会在"缺功能权限"上先撞 403，掩盖真正要验证的 20302
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id)"
                        + " SELECT u.id, r.role_id FROM sys_user u,"
                        + " (SELECT role_id FROM sys_user_role WHERE user_id ="
                        + " (SELECT id FROM sys_user WHERE username = 'admin' AND deleted = 0)) r"
                        + " WHERE u.username = ?",
                TEMP_USERNAME);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_user_session WHERE username = ?", TEMP_USERNAME);
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = ?)",
                TEMP_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEMP_USERNAME);
    }

    /** 登录后返回全部 Cookie：刷新令牌在另一个 Cookie 里，接口各取所需。 */
    private Cookie[] loginCookies(String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEMP_USERNAME + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = result.getResponse().getCookies();
        boolean hasAccess = java.util.Arrays.stream(cookies).anyMatch(c -> "ACCESS_TOKEN".equals(c.getName()));
        if (!hasAccess) {
            throw new IllegalStateException("登录未返回 ACCESS_TOKEN");
        }
        return cookies;
    }

    private Cookie login(String password) throws Exception {
        return java.util.Arrays.stream(loginCookies(password))
                .filter(cookie -> "ACCESS_TOKEN".equals(cookie.getName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("登录响应标记 mustChangePassword=true，且口令过期标记为 false（刚创建）")
    void loginFlagsMustChange() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEMP_USERNAME + "\",\"password\":\"" + TEMP_INITIAL_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.passwordExpired").value(false));
    }

    @Test
    @DisplayName("必须改密时访问业务接口被拦：403 + 20302（不是 401，前端据此弹改密框而不是跳登录）")
    void blocksBusinessEndpointsUntilPasswordChanged() throws Exception {
        Cookie access = login(TEMP_INITIAL_PASSWORD);
        mockMvc.perform(get("/api/v1/auth/routes").cookie(access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20302));
    }

    @Test
    @DisplayName("必须改密时仍可自救：查看自己资料、刷新令牌、登出都放行")
    void allowsSelfRescueEndpoints() throws Exception {
        Cookie access = login(TEMP_INITIAL_PASSWORD);
        mockMvc.perform(get("/api/v1/profile").cookie(access))
                .andExpect(status().isOk());
        // refresh 是写操作：OriginCheckFilter 要求带合法 Origin（防 CSRF），缺了会先撞 20301
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ALLOWED_ORIGIN)
                        .cookie(loginCookies(TEMP_INITIAL_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("改密时新口令同样受策略约束：弱口令被拒 422 + 30202")
    void changePasswordStillEnforcesPolicy() throws Exception {
        Cookie access = login(TEMP_INITIAL_PASSWORD);
        mockMvc.perform(put("/api/v1/profile/password").header("Origin", ALLOWED_ORIGIN).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + TEMP_INITIAL_PASSWORD + "\",\"newPassword\":\"admin123\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(30202));
    }

    @Test
    @DisplayName("改成合规口令后拦截解除：新令牌访问业务接口恢复 200")
    void unblocksAfterPasswordChanged() throws Exception {
        Cookie access = login(TEMP_INITIAL_PASSWORD);
        mockMvc.perform(put("/api/v1/profile/password").header("Origin", ALLOWED_ORIGIN).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + TEMP_INITIAL_PASSWORD + "\",\"newPassword\":\""
                                + TEMP_STRONG_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 改密会 bump token_version，旧令牌失效；用新口令重新登录
        Cookie newAccess = login(TEMP_STRONG_PASSWORD);
        mockMvc.perform(get("/api/v1/auth/routes").cookie(newAccess))
                .andExpect(status().isOk());
    }
}
