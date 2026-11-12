package com.adminstarter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 列表默认顺序（CR-20261112-032）：流水/事务类列表默认<b>最新在前</b>。
 *
 * <p>为什么值得集成测试：默认方向是"接口行为"，只测 {@code SortSupport} 单元测不到"仓储是否真的传对了常量"。
 * 这类问题此前真实发生过——操作日志页默认"最早的在前"，看起来像 bug。</p>
 */
class ListOrderingTest extends AbstractIntegrationTest {

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

    private long[] firstAndLastId(String path) throws Exception {
        String body = mockMvc.perform(get(path)
                        .param("page", "1").param("pageSize", "10")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
        java.util.List<Long> ids = new java.util.ArrayList<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        assertTrue(ids.size() >= 2, "列表数据不足以判断顺序：" + path);
        return new long[]{ids.get(0), ids.get(ids.size() - 1)};
    }

    @Test
    @DisplayName("用户列表：默认最新在前（id 递减），且 order=asc 可覆盖")
    void usersDefaultNewestFirst() throws Exception {
        long[] ids = firstAndLastId("/api/v1/users");
        assertTrue(ids[0] > ids[1], "用户列表默认应按创建时间倒序，实际首条 id=" + ids[0] + " 末条 id=" + ids[1]);

        long[] asc = firstAndLastId("/api/v1/users?order=asc");
        assertTrue(asc[0] < asc[1], "order=asc 必须能覆盖默认方向，实际首条 id=" + asc[0] + " 末条 id=" + asc[1]);
    }

    @Test
    @DisplayName("操作日志列表：默认最新在前（这是最反直觉的一处）")
    void operLogsDefaultNewestFirst() throws Exception {
        long[] ids = firstAndLastId("/api/v1/oper-logs");
        assertTrue(ids[0] > ids[1], "操作日志默认应最新在前，实际首条 id=" + ids[0] + " 末条 id=" + ids[1]);
    }

    @Test
    @DisplayName("角色列表：参考数据按人工排序号正序（不被流水类规则带跑）")
    void rolesKeepSortColumnAscending() throws Exception {
        String body = mockMvc.perform(get("/api/v1/roles").param("page", "1").param("pageSize", "10")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"sort\":(\\d+)").matcher(body);
        java.util.List<Integer> sorts = new java.util.ArrayList<>();
        while (matcher.find()) {
            sorts.add(Integer.parseInt(matcher.group(1)));
        }
        assertNotNull(sorts);
        assertTrue(sorts.size() >= 2, "角色数据不足以判断顺序");
        for (int i = 1; i < sorts.size(); i++) {
            assertTrue(sorts.get(i) >= sorts.get(i - 1), "角色默认应按 sort 升序，出现乱序：" + sorts);
        }
    }
}