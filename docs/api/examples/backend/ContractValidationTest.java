package com.example.contract;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约验证测试示例（骨架，复制到项目后即可运行）。
 *
 * 作用：用 swagger-request-validator 把“真实响应”和 docs/api/openapi.yaml 做比对，
 *      偏离契约（少字段、多字段、类型错、状态码错）会在测试里直接失败。
 *
 * 复制到项目后需要改的 4 处（其余保持）：
 *   1. package 声明：改成项目的测试包名；
 *   2. @SpringBootTest(classes = ...)：改成项目自己的启动类；
 *   3. 两个接口路径与请求体：改成项目真实接口；
 *   4. 契约文件位置：把 docs/api/openapi.yaml 复制到 src/test/resources/openapi.yaml。
 *
 * 依赖坐标见同目录 pom-snippet.xml（swagger-request-validator-mockmvc，版本 2.44.9）。
 */
@SpringBootTest(classes = ContractValidationTest.TestApplicationPlaceholder.class)
@AutoConfigureMockMvc
class ContractValidationTest {

    /** 复制到项目后删除这个占位类，改用项目真实启动类。 */
    @org.springframework.boot.autoconfigure.SpringBootApplication
    static class TestApplicationPlaceholder { }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET 列表：响应必须符合契约（信封、分页四字段、X-Trace-Id）")
    void listResources_matchesContract() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .accept(MediaType.APPLICATION_JSON))
                // 1) 契约校验：响应体、状态码、头部全部比对 openapi.yaml 中的定义
                .andExpect(OpenApiValidationMatchers.openApi().isValid("classpath:openapi.yaml"))
                // 2) 关键语义再断言一次：信封字段、分页四字段、traceId 与 X-Trace-Id 一致
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.pageSize").isNumber())
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Trace-Id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    @DisplayName("POST 创建：201 + 信封 + 幂等键必填")
    void createResource_matchesContract() throws Exception {
        String body = "{\"name\":\"契约测试示例\"}";
        mockMvc.perform(post("/api/v1/resources")
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(OpenApiValidationMatchers.openApi().isValid("classpath:openapi.yaml"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    @DisplayName("校验失败：422 + data.errors[{field,code,message}]")
    void createResource_validationError_matchesContract() throws Exception {
        String body = "{\"name\":\"\"}";
        mockMvc.perform(post("/api/v1/resources")
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(OpenApiValidationMatchers.openApi().isValid("classpath:openapi.yaml"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(30201))
                .andExpect(jsonPath("$.data.errors[0].field").isString())
                .andExpect(jsonPath("$.data.errors[0].code").isString())
                .andExpect(jsonPath("$.data.errors[0].message").isString());
    }
}
