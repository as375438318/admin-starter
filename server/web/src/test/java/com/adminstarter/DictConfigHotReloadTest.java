package com.adminstarter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典/参数「热更新」：改完立即生效，不需要重启。
 *
 * <p>字典加了 60 秒 TTL 缓存（读多写少），若写操作忘记失效，用户会看到旧字典最多 1 分钟——
 * 这类问题在手工点测时极难发现（改完刷新可能是"刚好过期"），所以必须用测试钉住。</p>
 */
class DictConfigHotReloadTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

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

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("字典项：新增后立即出现在 items 里（缓存已被写操作失效）")
    void dictItemChangeVisibleImmediately() throws Exception {
        jakarta.servlet.http.Cookie access = adminCookie();
        String typeCode = "hotreload_test_" + System.nanoTime();

        MvcResult created = mockMvc.perform(post("/api/v1/dicts/types")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"热更新测试\",\"type\":\"" + typeCode + "\",\"remark\":\"e2e\",\"isEnabled\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long typeId = MAPPER.readTree(body(created)).path("data").path("id").asLong();
        assertTrue(typeId > 0);

        // 先读一次，把空列表写进缓存
        mockMvc.perform(get("/api/v1/dicts/types/{id}/items", typeId).cookie(access))
                .andExpect(status().isOk());
        // 再写一项：如果缓存没失效，下面读到的仍是空列表
        mockMvc.perform(post("/api/v1/dicts/items")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictTypeId\":" + typeId + ",\"label\":\"值一\",\"value\":\"v1\",\"sort\":1,\"isEnabled\":true}"))
                .andExpect(status().isOk());

        String afterCreate = body(mockMvc.perform(get("/api/v1/dicts/types/{id}/items", typeId).cookie(access))
                .andExpect(status().isOk()).andReturn());
        JsonNode list = MAPPER.readTree(afterCreate).path("data");
        assertEquals(1, list.size(), "新增字典项后必须立即读到，说明写操作失效了缓存：" + afterCreate);
        long itemId = list.get(0).path("id").asLong();

        // 改标签后也必须立即生效
        mockMvc.perform(put("/api/v1/dicts/items/{id}", itemId)
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictTypeId\":" + typeId + ",\"label\":\"值一改名\",\"value\":\"v1\",\"sort\":1,\"isEnabled\":true}"))
                .andExpect(status().isOk());
        String afterUpdate = body(mockMvc.perform(get("/api/v1/dicts/types/{id}/items", typeId).cookie(access))
                .andExpect(status().isOk()).andReturn());
        assertEquals("值一改名", MAPPER.readTree(afterUpdate).path("data").get(0).path("label").asText(),
                "更新字典项后必须立即读到新值：" + afterUpdate);
    }

    @Test
    @DisplayName("参数列表：每行都带生效方式，app.* 需重启、sys.* 立即生效")
    void configRowsCarryEffect() throws Exception {
        String json = body(mockMvc.perform(get("/api/v1/configs").param("page", "1").param("pageSize", "50")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode list = MAPPER.readTree(json).path("data").path("list");
        assertTrue(list.size() > 0, "参数列表为空，无法校验生效方式：" + json);
        for (JsonNode row : list) {
            String key = row.path("configKey").asText();
            String effect = row.path("effect").asText(null);
            assertNotNull(effect, "参数行缺少 effect 字段：" + key);
            assertEquals(key.startsWith("app.") ? "STARTUP" : "HOT", effect,
                    "参数 " + key + " 的生效方式判定不正确");
            assertEquals("HOT".equals(effect), row.path("hotReloadable").asBoolean(),
                    "hotReloadable 必须与 effect 一致：" + key);
        }
    }
}