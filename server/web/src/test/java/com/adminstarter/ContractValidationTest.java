package com.adminstarter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约验证：真实响应 vs {@code docs/api/openapi.yaml}（to-be 契约）。
 * <p>与 {@code docs/api/examples/backend/ContractValidationTest.java} 模板的差异：**不复制契约副本**，
 * 直接读仓库里的契约文件——复制一份到 {@code src/test/resources} 必然漂移，
 * 且会让"改了契约但测试还过"这种最危险的情况成为可能。</p>
 * <p>覆盖两类偏离：</p>
 * <ul>
 *   <li>成功响应：信封字段、分页结构、响应头 X-Trace-Id 必须与契约一致；</li>
 *   <li>未登录访问受保护接口：必须返回契约里声明的 401 结构（而不是空 body 或 302）。</li>
 * </ul>
 */
class ContractValidationTest extends AbstractIntegrationTest {

    private static final OpenApiInteractionValidator VALIDATOR = buildValidator();

    @Autowired
    private MockMvc mockMvc;

    /** 从工作目录向上找仓库根的契约文件，兼容 IDEA（工作目录=模块）与 Maven（工作目录=模块或仓库根）。 */
    private static Path locateContract() {
        // 允许用 -Dcontract.file=... 指定契约文件（便于验证"契约改动是否会让测试变绿/变红"）
        String override = System.getProperty("contract.file");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
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
        // 2.44.9 的工厂方法只接受「位置字符串」：用 file: URI 指向仓库内的契约文件
        String location = locateContract().toUri().toString();
        return OpenApiInteractionValidator.createForSpecificationUrl(location)
                .withLevelResolver(LevelResolver.create()
                        // 契约按 §4.4 只写"被前端消费/需交付"的接口，未文档化的路径不在本测试范围内，
                        // 因此把"路径未定义"降级为 WARN，避免把"契约未覆盖"误报成"实现违约"。
                        .withLevel("validation.request.path.missing", ValidationReport.Level.WARN)

                        .build())
                .build();
    }

    @Test
    @DisplayName("GET /public/configs：未登录也应 200，且响应结构与契约一致")
    void publicConfigsMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/public/configs"))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /public/configs：只返回白名单参数，敏感配置不得出现")
    void publicConfigsExposeOnlyWhitelistedKeys() throws Exception {
        String body = mockMvc.perform(get("/api/v1/public/configs"))
                .andReturn().getResponse().getContentAsString();

        // 契约明确：白名单外的参数（尤其令牌密钥）不得返回
        assertTrue(!body.contains("token.secret"), "公开配置不得返回令牌密钥：" + body);
        assertTrue(!body.toLowerCase().contains("password"), "公开配置不得返回任何口令字段：" + body);
    }

    @Test
    @DisplayName("GET /files 未登录：返回契约声明的 401 结构（不是 302 或空 body）")
    void unauthenticatedFilesMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/files").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.code").isNumber());
    }

    // ------------------------------------------------------------------
    // 受保护接口：先真实登录换 Cookie（登录接口本身不在契约交付范围内），再校验业务响应
    // ------------------------------------------------------------------

    private Cookie loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if ("ACCESS_TOKEN".equals(cookie.getName())) {
                return cookie;
            }
        }
        throw new IllegalStateException("登录未返回 ACCESS_TOKEN Cookie，无法进行受保护接口的契约校验");
    }

    @Test
    @DisplayName("GET /files（已登录）：列表响应符合契约（分页四字段 + 文件项必填字段）")
    void fileListMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/files").param("page", "1").param("pageSize", "10").cookie(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("GET /notices（已登录）：通知列表响应符合契约")
    void noticeListMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("page", "1").param("pageSize", "10").cookie(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("GET /files/recycle（已登录）：回收站列表响应符合契约")
    void fileRecycleListMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/files/recycle").param("page", "1").param("pageSize", "10")
                        .cookie(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR));
    }

    @Test
    @DisplayName("GET /files/{id}/preview 不存在：契约声明的 404（与越权统一，不泄露存在性）")
    void previewMissingFileMatchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/files/{id}/preview", 999999L).cookie(loginAsAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR));
    }
}
