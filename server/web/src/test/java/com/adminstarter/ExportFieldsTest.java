package com.adminstarter;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导出按勾选列裁剪（CR-20261113-038 / P3）。
 *
 * <p>为什么值得集成测试：{@code fields} 是**唯一能缩小导出文件**的入口，它有两条不能破的性质——
 * ① <b>只能减少列</b>，不能增加、不能改变列序（顺序以 schema 为准）；
 * ② <b>不能绕过字段级脱敏</b>：{@code ownerPhone} 这类列先按权限过滤，再与 {@code fields} 取交集。</p>
 *
 * <p>断言落在"真的解析导出文件里的表头"，而不是只看状态码——裁剪错时状态码一样是 200。</p>
 */
class ExportFieldsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    /** 解析导出文件首行表头 */
    private List<String> exportHeaders(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/demo-tasks/export" + query)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn();
        byte[] content = result.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int index = 0; index < header.getLastCellNum(); index++) {
                headers.add(header.getCell(index).getStringCellValue());
            }
            return headers;
        }
    }

    @Test
    @DisplayName("fields 只保留勾选的列，且顺序以 schema 为准（不按参数顺序）")
    void fieldsNarrowColumnsInSchemaOrder() throws Exception {
        List<String> headers = exportHeaders("?fields=status,title");
        assertEquals(List.of("任务标题", "任务状态"), headers,
                "fields=status,title 必须只输出这两列，且顺序按 schema（title 在 status 前），实际：" + headers);
    }

    @Test
    @DisplayName("不传 fields 时导出全部可导出列（向后兼容）")
    void withoutFieldsExportsAllColumns() throws Exception {
        List<String> headers = exportHeaders("");
        assertTrue(headers.size() > 2, "不传 fields 应导出全部列，实际只有：" + headers);
        assertTrue(headers.contains("任务标题") && headers.contains("任务状态"), "缺少基础列：" + headers);
    }

    @Test
    @DisplayName("fields 含 schema 未定义的列 → 400 且提示未知列名")
    void unknownFieldRejected() throws Exception {
        String body = mockMvc.perform(get("/api/v1/demo-tasks/export?fields=title,notAColumn")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("notAColumn"), "错误信息应指出未知列名，实际：" + body);
    }

    @Test
    @DisplayName("fields 不改变脱敏结果：敏感列取值与全量导出一致")
    void fieldsDoNotChangeMasking() throws Exception {
        // 脱敏三态由 MaskResolver 按当前用户权限决定（SUPER_ADMIN=查看，DEPT_MANAGER=打码，
        // 其他=隐藏）。`fields` 只是裁剪，**不得**影响这个判定，否则就成了提权入口。
        String withFields = exportCell("?fields=ownerPhone", "负责人手机号");
        String fullExport = exportCell("", "负责人手机号");
        assertEquals(fullExport, withFields,
                "同一列的脱敏结果不应因 fields 改变：全量=" + fullExport + "，fields=" + withFields);
    }

    /** 取指定表头列的第一个数据单元格值（不存在返回 null） */
    private String exportCell(String query, String headerName) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/demo-tasks/export" + query)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn();
        byte[] content = result.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            int columnIndex = -1;
            for (int index = 0; index < header.getLastCellNum(); index++) {
                if (headerName.equals(header.getCell(index).getStringCellValue())) {
                    columnIndex = index;
                    break;
                }
            }
            if (columnIndex < 0) {
                return null;
            }
            Row firstRow = sheet.getRow(1);
            if (firstRow == null || firstRow.getCell(columnIndex) == null) {
                return "";
            }
            return firstRow.getCell(columnIndex).toString();
        }
    }
}
