package com.adminstarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 通用导出中心（`CR-20261114-043`）。
 *
 * <p>验三件事：<br>
 * ① 一个入口能服务多个已注册模块（不是"给某个模块写死"）；<br>
 * ② **按模块鉴权**：权限码由模块声明，通用入口不得因为"走统一 URL"就放宽——无权限角色必须 403；<br>
 * ③ 未知模块不泄露"存在性"（同样 403，而不是 500 或 404 让人试探）。</p>
 */
class ExportCenterTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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

    @Test
    @DisplayName("模块清单：登录即可读，且至少包含客户档案/示例任务/岗位")
    void listsRegisteredModules() throws Exception {
        mockMvc.perform(get("/api/v1/exports/modules").cookie(login("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='customer')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='demo-task')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='post')]").exists());
    }

    @Test
    @DisplayName("导出客户档案：返回 xlsx 文件流与 UTF-8 文件名")
    void exportsCustomerAsXlsx() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/exports/customer")
                        .param("fields", "name,phone")
                        .cookie(login("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(1000);
        // xlsx 是 zip 容器，前两字节固定 PK
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("按模块鉴权：无 demo:customer:export 的角色 403；未知模块同样 403")
    void enforcesPerModulePermission() throws Exception {
        Cookie employee = login("dev1", "admin123");
        mockMvc.perform(get("/api/v1/exports/customer").cookie(employee))
                .andExpect(status().isForbidden());

        // 未知模块不区分"不存在"与"无权限"：不给探测面
        mockMvc.perform(get("/api/v1/exports/not-a-module").cookie(login("admin", "admin123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("列白名单透传：未知列 400，与既有导出接口同口径")
    void rejectsUnknownField() throws Exception {
        mockMvc.perform(get("/api/v1/exports/customer")
                        .param("fields", "notAField")
                        .cookie(login("admin", "admin123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));
    }
}
