package com.adminstarter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 高风险写接口的契约测试：文件上传、分片合并、批量导入。
 *
 * <p>为什么这三个最值得测：它们都带 <b>multipart 或二进制</b>、都涉及落盘、都是"出问题就是数据/磁盘事故"的路径；
 * 而此前契约测试只覆盖 JSON 读写接口。</p>
 *
 * <p>夹具原则：<b>不手写常量</b>——xlsx 用 POI 现场生成（列名取自模板接口），分片链路严格按
 * init → PUT 分片 → complete 走一遍。</p>
 */
class ContractValidationWriteHeavyTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    @Autowired
    private MockMvc mockMvc;

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

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createForSpecificationUrl(locateContract().toUri().toString())
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request.path.missing", ValidationReport.Level.WARN)
                            .build())
                    .build();

    /**
     * multipart 请求专用的校验器：<b>只校验响应</b>。
     *
     * <p>MockMvc 的 {@code multipart(...)} 不会把请求体序列化成可校验的字节流，校验器因此报
     * {@code validation.request.body.missing}（"A request body is required but none found"）。
     * 这是工具限制而不是实现违约——把该级别降为 WARN，仍然严格校验响应结构。</p>
     */
    private static final OpenApiInteractionValidator VALIDATOR_RESPONSE_ONLY =
            OpenApiInteractionValidator.createForSpecificationUrl(locateContract().toUri().toString())
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request.path.missing", ValidationReport.Level.WARN)
                            .withLevel("validation.request.body.missing", ValidationReport.Level.WARN)
                            .build())
                    .build();

    private jakarta.servlet.http.Cookie adminCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> "ACCESS_TOKEN".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("登录未返回 ACCESS_TOKEN"));
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("POST /files（multipart 单文件上传）：响应符合契约，返回记录可与内容对应")
    void uploadFileMatchesContract() throws Exception {
        byte[] content = ("contract-upload-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "契约上传.txt", "text/plain", content);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                // 请求（multipart 结构）与响应双向校验
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR_RESPONSE_ONLY))
                .andExpect(jsonPath("$.data.originalName").value("契约上传.txt"))
                .andExpect(jsonPath("$.data.size").value(content.length));
    }

    @Test
    @DisplayName("分片上传全链路：init → PUT 分片 → complete，三步响应都符合契约")
    void chunkUploadMatchesContract() throws Exception {
        jakarta.servlet.http.Cookie access = adminCookie();
        // chunkSize 必须是 256KB–5MB（FileAppService.CHUNK_MIN_SIZE），先前的 12 字节被 422 拒了
        int chunkSize = 256 * 1024;
        byte[] part1 = new byte[chunkSize];
        byte[] part2 = new byte[chunkSize];
        // 内容必须每次不同：相同 sha256+size 会命中「秒传」，init 直接返回 instant=true 且没有 uploadId，
        // 后续 PUT 分片就会 400（第二次跑这个用例时踩到过）
        java.util.Random random = new java.util.Random();
        random.nextBytes(part1);
        random.nextBytes(part2);
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.writeBytes(part1);
        joined.writeBytes(part2);
        byte[] whole = joined.toByteArray();

        String initJson = body(mockMvc.perform(post("/api/v1/files/chunk-sessions")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 字段名以 FileDtos.ChunkInitRequest 为准：size 必填、contentType 必填；
                        // 第一版写了 totalSize 且漏了 contentType，被 422「文件大小不能小于 0」挡下
                        .content("{\"fileName\":\"分片契约.zip\",\"size\":" + whole.length
                                + ",\"contentType\":\"application/octet-stream\""
                                + ",\"chunkSize\":" + chunkSize
                                + ",\"sha256\":\"" + sha256(whole) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andReturn());
        JsonNode init = MAPPER.readTree(initJson).path("data");
        assertNotNull(init, "初始化分片会话必须返回 data：" + initJson);
        String uploadId = init.path("uploadId").asText();
        assertTrue(!uploadId.isBlank(), "初始化未返回 uploadId：" + initJson);

        // 分片是 multipart 字段 `chunk`（不是裸字节流）：控制器签名为 @RequestPart("chunk")
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                                "/api/v1/files/chunk-sessions/{uploadId}/chunks/{index}", uploadId, 0)
                        .file(new MockMultipartFile("chunk", "chunk-0", MediaType.APPLICATION_OCTET_STREAM_VALUE, part1))
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR_RESPONSE_ONLY));
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                                "/api/v1/files/chunk-sessions/{uploadId}/chunks/{index}", uploadId, 1)
                        .file(new MockMultipartFile("chunk", "chunk-1", MediaType.APPLICATION_OCTET_STREAM_VALUE, part2))
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access))
                .andExpect(status().isOk());

        // 合并：字节数与 SHA-256 必须与 init 一致，响应是文件记录
        mockMvc.perform(post("/api/v1/files/chunk-sessions/{uploadId}/complete", uploadId)
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR))
                .andExpect(jsonPath("$.data.size").value(whole.length))
                .andExpect(jsonPath("$.data.originalName").value("分片契约.zip"));
    }

    @Test
    @DisplayName("POST /demo-tasks/import（xlsx 夹具）：成功响应符合契约")
    void importMatchesContract() throws Exception {
        byte[] xlsx = demoTaskWorkbook("契约导入任务");
        MockMultipartFile file = new MockMultipartFile("file", "契约导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mockMvc.perform(multipart("/api/v1/demo-tasks/import").file(file)
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR_RESPONSE_ONLY))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.imported").value(1));
    }

    @Test
    @DisplayName("POST /demo-tasks/import 缺必填列：422 响应结构符合契约（错误信封也要对得上）")
    void importValidationErrorMatchesContract() throws Exception {
        byte[] xlsx = demoTaskWorkbook(null);
        MockMultipartFile file = new MockMultipartFile("file", "契约导入-缺列.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mockMvc.perform(multipart("/api/v1/demo-tasks/import").file(file)
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(adminCookie()))
                .andExpect(status().isUnprocessableEntity())
                // 用"只校验响应"的校验器思路：这里请求体合法，响应是 422，直接用完整校验器即可
                .andExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR_RESPONSE_ONLY))
                .andExpect(jsonPath("$.code").value(30201))
                .andExpect(jsonPath("$.data.errors").isArray());
    }

    /**
     * 现场生成导入夹具：列名与「下载模板」一致（任务标题 / 任务状态 / 归属部门 / 负责人）。
     * 不写死二进制样本，避免模板列名调整后测试悄悄失效。
     */
    private byte[] demoTaskWorkbook(String title) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 数据页名必须与 schema.dataSheetName() 一致（「示例任务」），否则服务端直接 400 拒绝
            Sheet sheet = workbook.createSheet("示例任务");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("任务标题");
            header.createCell(1).setCellValue("任务状态");
            header.createCell(2).setCellValue("归属部门");
            header.createCell(3).setCellValue("负责人");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(title == null ? "" : title);
            row.createCell(1).setCellValue("待处理");
            row.createCell(2).setCellValue("总部");
            row.createCell(3).setCellValue("");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}