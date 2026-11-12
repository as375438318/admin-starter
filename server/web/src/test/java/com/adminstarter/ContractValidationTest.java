package com.adminstarter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /**
     * 只校验响应的校验器：给"故意发非法请求体"的负例用。
     *
     * <p>负例的整个目的就是发一个违反契约的请求体（缺必填字段），若用 {@link #VALIDATOR}，
     * 校验器会先把**请求**标红，导致我们验证不到"服务端的 422 响应结构是否符合契约"。</p>
     */
    private static final OpenApiInteractionValidator VALIDATOR_RESPONSE_ONLY = buildResponseOnlyValidator();

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

    private static OpenApiInteractionValidator buildResponseOnlyValidator() {
        String location = locateContract().toUri().toString();
        return OpenApiInteractionValidator.createForSpecificationUrl(location)
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request.path.missing", ValidationReport.Level.WARN)
                        .withLevel("validation.request.body.schema", ValidationReport.Level.WARN)
                        .withLevel("validation.request.body.unexpected", ValidationReport.Level.WARN)
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
    // ==================================================================
    // 写接口：请求体夹具 + 响应双校验（swagger-request-validator 同时校验请求与响应）
    //
    // 为什么写接口更难测：读接口只要参数对就行；写接口必须先满足"业务上合法的请求体"，
    // 否则拿到的是 422/404 而不是要验证的成功分支。夹具因此从**真实接口返回值**里取
    // （GET /jobs、GET /jobs/{id}），而不是在测试里手写一份可能过期的常量。
    // ==================================================================

    /** 写操作必须带合法 Origin（OriginCheckFilter 的 CSRF 防护），否则先撞 20301 而不是被测分支。 */
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 以管理员身份取 JSON（用于构造夹具：任务列表 / 单条任务）。
     *
     * <p>必须显式按 UTF-8 解码：`MockHttpServletResponse.getContentAsString()` 默认用响应里声明的 charset，
     * 而本项目响应头是 `application/json`（不带 charset）→ 默认 ISO-8859-1。中文任务名会被解成乱码，
     * 每个汉字膨胀成 3 个字符，回写成请求体时直接超契约的 `maxLength: 64`——第一版就是这样"假报"了一个数据问题。</p>
     */
    private JsonNode fetchJson(Cookie access, String path) throws Exception {
        String body = mockMvc.perform(get(path).cookie(access))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return MAPPER.readTree(body);
    }

    /** 取一条已存在的任务作为"改任务"夹具；没有任务时直接失败，而不是跳过用例。 */
    private JsonNode firstJob(Cookie access) throws Exception {
        JsonNode list = fetchJson(access, "/api/v1/jobs").path("data").path("list");
        assertTrue(list.isArray() && !list.isEmpty(),
                "定时任务列表为空：PUT /jobs/{id} 的契约用例需要至少一条已存在任务作夹具");
        return list.get(0);
    }

    /** 用任务当前值构造 JobRequest（原样回写，避免测试改动真实数据）。 */
    private String jobRequestFrom(JsonNode job) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("name", job.path("name").asText());
        body.put("groupName", job.path("groupName").asText("DEFAULT"));
        body.put("jobKey", job.path("jobKey").asText());
        body.put("cronExpression", job.path("cronExpression").asText());
        if (!job.path("description").isMissingNode()) {
            body.put("description", job.path("description").asText());
        }
        return MAPPER.writeValueAsString(body);
    }

    @Test
    @DisplayName("POST /my-notices/read-all（写接口）：响应符合契约，data.marked 为整数")
    void readAllMyNoticesMatchesContract() throws Exception {
        mockMvc.perform(post("/api/v1/my-notices/read-all")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.marked").isNumber());
    }

    @Test
    @DisplayName("PUT /jobs/{id}（写接口）：夹具取自真实任务，请求体与响应都要符合契约")
    void updateJobMatchesContract() throws Exception {
        Cookie access = loginAsAdmin();
        JsonNode job = firstJob(access);

        mockMvc.perform(put("/api/v1/jobs/{id}", job.path("id").asLong())
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequestFrom(job)))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.id").value(job.path("id").asLong()));
    }

    @Test
    @DisplayName("PUT /jobs/{id}/status（写接口）：状态原样回写，响应符合契约")
    void updateJobStatusMatchesContract() throws Exception {
        Cookie access = loginAsAdmin();
        JsonNode job = firstJob(access);
        String currentStatus = job.path("status").asText("DISABLED");

        mockMvc.perform(put("/api/v1/jobs/{id}/status", job.path("id").asLong())
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + currentStatus + "\"}"))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.status").value(currentStatus));
    }

    @Test
    @DisplayName("PUT /jobs/{id} 缺必填 cronExpression：契约声明的 422 结构（错误信封也要对得上）")
    void updateJobMissingRequiredFieldMatchesContract() throws Exception {
        Cookie access = loginAsAdmin();
        JsonNode job = firstJob(access);
        ObjectNode body = (ObjectNode) MAPPER.readTree(jobRequestFrom(job));
        body.remove("cronExpression");

        mockMvc.perform(put("/api/v1/jobs/{id}", job.path("id").asLong())
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                // 用"只校验响应"的校验器：请求体是我故意写坏的，验证目标是 422 的响应结构
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR_RESPONSE_ONLY))
                .andExpect(jsonPath("$.code").value(30201));
    }
}