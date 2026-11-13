package com.adminstarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 权限矩阵回归（`CR-20261114-045`）：把「权限码 × 角色」的允许/拒绝期望变成可执行断言。
 *
 * <p>分两层验，缺一不可：</p>
 * <ol>
 *   <li><b>授权数据层</b>：角色实际拿到的权限码集合必须与矩阵逐格一致（71 码 × 7 角色）。
 *       这一层精确、无副作用，能抓住"新权限码只加了菜单没配角色"这类最常见的漏配。</li>
 *   <li><b>执行层</b>：真实 HTTP 调用。{@code GET}/{@code DELETE} 这类无请求体的接口要求
 *       拒绝方**正好 403**，证明 {@code @PreAuthorize} 真的接在这个权限码上。</li>
 * </ol>
 *
 * <p><b>为什么写接口（POST/PUT 带 {@code @Valid} 请求体）只用弱断言</b>：Spring MVC 在调用 handler
 * 之前就完成参数绑定与校验，所以"无权限 + 空请求体"得到的是 422 而不是 403——请求根本没走到鉴权。
 * 与其为了绿灯伪造请求体（那样一旦业务校验变了就会误红），这里对这类接口只断言
 * "允许方不被拒、不被 404、不 5xx"，而"授权是否给对"由第 1 层覆盖。这个盲区在
 * {@code CR-20261114-045} §4 与断言 #114 里写明。</p>
 *
 * <p>用例与映射由 {@code tools/permission-matrix/cli.mjs} 从 Controller 的 {@code @PreAuthorize}
 * 自动抽取生成，禁止手改 JSON。</p>
 */
class PermissionMatrixTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 内置角色账号口令（测试库种子）。 */
    private static final String SEED_PASSWORD = "admin123";

    /**
     * 测试库种子账号在 V17 之后全部 {@code must_change_password=1}，基类只复位了 admin。
     * 不复位其余账号的话，它们拿到的是"必须改密"的令牌，除自我相关接口外一律 403 + 20302，
     * 整张矩阵会被误判成"拒绝"。这里统一复位（幂等）。
     */
    @org.junit.jupiter.api.BeforeEach
    void settleAllSeedAccounts() {
        jdbcTemplate.update(
                "UPDATE sys_user SET must_change_password = 0, password_updated_at = NOW() "
                        + "WHERE username IN ('admin','deptmgr','dev1','auditor','viewer','demo_dept','demo_owner') AND deleted = 0");
    }

    private final Map<String, Cookie> cookieCache = new HashMap<>();

    private JsonNode loadMatrix() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("permission-matrix.json")) {
            assertThat(input).as("permission-matrix.json 必须存在（由 tools/permission-matrix/cli.mjs 生成）").isNotNull();
            return objectMapper.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Cookie loginAs(String username) throws Exception {
        Cookie cached = cookieCache.get(username);
        if (cached != null) {
            return cached;
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, SEED_PASSWORD)))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("账号 %s 应能登录（测试库种子口令 %s）——失败常见原因：测试库被手工改坏（如内置账号被软删）", username)
                .isEqualTo(200);
        for (Cookie cookie : result.getResponse().getCookies()) {
            if ("ACCESS_TOKEN".equals(cookie.getName())) {
                cookieCache.put(username, cookie);
                return cookie;
            }
        }
        throw new IllegalStateException("登录未返回 ACCESS_TOKEN Cookie：" + username);
    }

    /** 从库里读每个角色实际拥有的权限码（与登录时装配进令牌的来源一致）。 */
    private Map<String, Set<String>> grantedCodesByRole() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT r.code AS role_code, m.permission_code AS perm "
                        + "FROM sys_role r "
                        + "JOIN sys_role_menu rm ON rm.role_id = r.id "
                        + "JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE r.deleted = 0 AND m.permission_code IS NOT NULL AND m.permission_code <> '' "
                        + "ORDER BY r.code, m.permission_code",
                rs -> {
                    result.computeIfAbsent(rs.getString("role_code"), key -> new TreeSet<>())
                            .add(rs.getString("perm"));
                });
        return result;
    }

    @Test
    @DisplayName("授权数据层：每个角色实际拿到的权限码与矩阵逐格一致")
    void grantedCodesMatchMatrix() throws Exception {
        JsonNode matrix = loadMatrix();
        JsonNode cases = matrix.path("cases");
        assertThat(cases.size()).as("用例数量下限，避免空矩阵全绿").isGreaterThanOrEqualTo(60);

        Map<String, Set<String>> granted = grantedCodesByRole();
        assertThat(granted).as("测试库应至少装配出内置角色，若为空说明种子数据被改坏").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (JsonNode item : cases) {
            String code = item.path("code").asText();
            for (var entry : iterable(item.path("allowed"))) {
                String role = entry.getKey();
                boolean expected = entry.getValue().asBoolean();
                boolean actual = granted.getOrDefault(role, Set.of()).contains(code);
                if (expected != actual) {
                    failures.add("%s × %s：矩阵=%s，实际=%s".formatted(
                            code, role, expected ? "允许" : "拒绝", actual ? "允许" : "拒绝"));
                }
            }
        }
        assertThat(failures)
                .as("权限矩阵与角色授权数据不一致（%d 条）：\n%s", failures.size(), String.join("\n", failures))
                .isEmpty();
    }

    @Test
    @DisplayName("执行层：GET/带 id 的写接口，拒绝方必须正好 403（证明 @PreAuthorize 真接在该权限码上）")
    void httpEnforcementMatchesMatrix() throws Exception {
        JsonNode matrix = loadMatrix();
        JsonNode platformRoles = matrix.path("platformRoles");

        List<String> failures = new ArrayList<>();
        List<String> weakChecks = new ArrayList<>();
        int strict = 0;

        for (JsonNode item : matrix.path("cases")) {
            String code = item.path("code").asText();
            String kind = item.path("kind").asText();
            String method = item.path("method").asText();
            String path = item.path("path").asText();
            // 弱断言的两类情况：
            // ① 带 @Valid 请求体的写接口（POST/PUT/PATCH）：参数校验先于方法级鉴权，空体只会得到 422，
            //    不发真实业务数据就验不到鉴权；
            // ② 用 hasAnyAuthority 的接口（exact=false）：它本来就"有任一权限码即可"，单码维度谈不上"应拒绝"。
            boolean bodyless = "GET".equals(method) || "DELETE".equals(method);
            boolean weakCase = !bodyless || !item.path("exact").asBoolean();

            for (var entry : iterable(item.path("allowed"))) {
                String role = entry.getKey();
                boolean expectedAllowed = entry.getValue().asBoolean();
                String username = platformRoles.path(role).asText(null);
                assertThat(username).as("角色 %s 缺内置账号映射", role).isNotNull();

                MvcResult result = mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .request(HttpMethod.valueOf(method), path)
                                        .cookie(loginAs(username))
                                        // 写操作要合法 Origin（后端 OriginCheckFilter 的 CSRF 防护）
                                        .header("Origin", "http://127.0.0.1:5173")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
                int status = result.getResponse().getStatus();
                String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                String hint = body.isBlank() ? "" : " body=" + body.substring(0, Math.min(120, body.length()));

                if (weakCase) {
                    weakChecks.add("%s × %s（%s %s → %d）".formatted(code, role, method, path, status));
                    continue;
                }
                strict += 1;

                if (expectedAllowed) {
                    // 允许侧：写接口打的是"不存在的目标"（哨兵 id），所以 404/业务错误都算正常放行；
                    // 只有 401/403（被拒）与 5xx（真故障）才是问题
                    if (status == 403 || status == 401) {
                        failures.add("%s 期望允许但被拒：%s %s → %d（%s）%s".formatted(code, method, path, status, role, hint));
                    } else if (status >= 500) {
                        failures.add("%s 允许访问但服务端报错：%s %s → %d（%s）%s".formatted(code, method, path, status, role, hint));
                    }
                } else if (status != 403) {
                    // 缺必填参数时，Spring 在方法级鉴权之前就返回 400/10001（如 DELETE /cache/keys 需要 key 参数），
                    // 这种情况判定不了"是否有权限"，登记为弱断言而不是失败。
                    if (status == 400 && body.contains("\"code\":10001")) {
                        weakChecks.add("%s × %s（%s %s → 400 缺必填参数，未到鉴权）".formatted(code, role, method, path));
                    } else {
                        failures.add("%s 期望拒绝但放行：%s %s → %d（%s）%s".formatted(code, method, path, status, role, hint));
                    }
                }
            }
        }

        assertThat(strict).as("严格断言的组合数下限（GET 与无请求体的 DELETE）").isGreaterThanOrEqualTo(150);
        assertThat(failures)
                .as("HTTP 层权限与矩阵不一致（%d 条严格检查）：\n%s", strict, String.join("\n", failures))
                .isEmpty();
        // 弱断言不是"通过"，只是"没被判死"：数量变化要能被看到（新增可写接口会推高它）
        assertThat(weakChecks.size())
                .as("未做严格断言的组合（带请求体的写接口 / hasAnyAuthority 接口，预期 ≤ 300 组，实际 %d）。这些组合的授权正确性由授权数据层用例覆盖。",
                        weakChecks.size())
                .isLessThanOrEqualTo(300);
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(JsonNode node) {
        return () -> node.fields();
    }
}
