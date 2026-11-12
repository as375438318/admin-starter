package com.adminstarter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作台统计接口：结构与契约一致、无权限返回 403（而不是空数据）、趋势固定 7 天。
 *
 * <p>为什么专门测 403：统计是<b>系统级聚合</b>（不随数据权限收窄），如果无权限时返回空对象，
 * 前端就无法区分"没权限"和"系统是空的"，会显示一排 0 误导人。</p>
 */
class DashboardOverviewTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 避免被"首登强制改密"挡住：V17 迁移给存量账号置了 must_change_password=1，
     * 此时任何非白名单接口都返回 403+20302，测不到本用例要验证的权限判定。
     */
    @org.junit.jupiter.api.BeforeEach
    void settleDevUserPasswordState() {
        jdbcTemplate.update("UPDATE sys_user SET must_change_password = 0, password_updated_at = UTC_TIMESTAMP()"
                + " WHERE username = 'dev1'");
    }

    // 测试库（*_test）里种子账号口令仍是 admin123；dev 库被 E2E 改过，不要混用
    /** 与 ContractValidationTest 一致：从当前目录逐级向上找契约，兼容从仓库根或模块目录运行。 */
    private static Path locateContract() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve("docs/api/openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 docs/api/openapi.yaml；契约测试必须在仓库内运行");
    }

    private static OpenApiInteractionValidator buildValidator() {
        Path contract = locateContract();
        return OpenApiInteractionValidator.createForSpecificationUrl(contract.toUri().toString())
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request.path.missing", ValidationReport.Level.WARN)
                        .build())
                .build();
    }

    private static final OpenApiInteractionValidator VALIDATOR = buildValidator();

    private jakarta.servlet.http.Cookie accessCookie(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // 数组没有 stream()，用 Arrays.stream；登录接口本身不在契约范围内
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> "ACCESS_TOKEN".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("登录未返回 ACCESS_TOKEN"));
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // 用 Cookie 更贴近真实调用；登录接口本身不在契约范围内
        return accessCookie(username, password).getValue();
    }

    @Test
    @DisplayName("GET /dashboard/overview（超级管理员）：结构符合契约，趋势固定 7 天且升序")
    void overviewMatchesContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/overview")
                        .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN",
                                loginAndGetAccessToken("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.counts.users").isNumber())
                .andExpect(jsonPath("$.data.storage.totalBytes").isNumber())
                .andExpect(jsonPath("$.data.trends").isArray())
                .andExpect(jsonPath("$.data.trends.length()").value(7))
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 趋势必须按时间升序：前端直接按数组顺序画柱状图，顺序错会画反
        int previous = -1;
        for (String day : body.split("\"date\":\"")) {
            if (day.length() >= 10) {
                String date = day.substring(0, 10);
                if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    int value = Integer.parseInt(date.replace("-", ""));
                    assertTrue(value > previous, "趋势日期必须严格升序，出现乱序：" + date);
                    previous = value;
                }
            }
        }
    }

    @Test
    @DisplayName("GET /dashboard/overview（无权限账号）：403 + 20301，不得返回空数据")
    void overviewRequiresPermission() throws Exception {
        // 系统级聚合不随数据权限收窄，必须由权限码把关：默认只授予 SUPER_ADMIN
        mockMvc.perform(get("/api/v1/dashboard/overview")
                        .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN",
                                loginAndGetAccessToken("dev1", "admin123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20301))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /dashboard/overview（未登录）：401，不得泄露统计")
    void overviewRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }
}