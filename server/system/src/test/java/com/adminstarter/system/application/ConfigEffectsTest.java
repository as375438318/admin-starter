package com.adminstarter.system.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数生效方式判定：{@code app.*} 需重启、其余改库即生效。
 *
 * <p>第二条用例会解析 {@code docs/design/config-registry.yaml} 做<b>漂移校验</b>：
 * 台账写的 {@code effect} 与代码判定必须一致。否则前端会对着 6 个"需重启"的键显示"立即生效"，
 * 管理员改完口令策略发现没变化，只会认为是 bug。</p>
 */
class ConfigEffectsTest {

    @Test
    @DisplayName("app.* 判为 STARTUP（启动期绑定），sys.* 判为 HOT（运行期读库）")
    void effectByPrefix() {
        assertEquals(ConfigEffects.STARTUP, ConfigEffects.effectOf("app.security.password.min-length"));
        assertEquals(ConfigEffects.STARTUP, ConfigEffects.effectOf("app.auth.cookie-secure"));
        assertEquals(ConfigEffects.HOT, ConfigEffects.effectOf("sys.name"));
        assertEquals(ConfigEffects.HOT, ConfigEffects.effectOf("sys.upload.max-size"));
        // 空值按 HOT 处理：未知键不阻断，前端只会显示"立即生效"（保守但不会谎称需重启）
        assertEquals(ConfigEffects.HOT, ConfigEffects.effectOf(null));
        assertEquals(ConfigEffects.HOT, ConfigEffects.effectOf("  "));
        assertFalse(ConfigEffects.isHotReloadable("app.security.password.expire-days"));
        assertTrue(ConfigEffects.isHotReloadable("sys.copyright"));
    }

    @Test
    @DisplayName("与 config-registry 台账一致：每个登记键的 effect 必须与代码判定相同")
    void matchesConfigRegistry() throws IOException {
        Map<String, String> registry = readRegistry();
        assertFalse(registry.isEmpty(), "config-registry.yaml 未解析到任何键，说明格式变了，需要同步本测试");

        List<String> drift = registry.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(ConfigEffects.effectOf(entry.getKey())))
                .map(entry -> entry.getKey() + "：台账=" + entry.getValue()
                        + " 代码=" + ConfigEffects.effectOf(entry.getKey()))
                .toList();
        assertTrue(drift.isEmpty(), "参数生效方式与台账不一致：" + drift);
    }

    /** 解析台账：`- key: xxx` 之后的第一个 `effect: XXX` 视为该键的生效方式。 */
    private Map<String, String> readRegistry() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        Path registry = null;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve("docs/design/config-registry.yaml");
            if (Files.isRegularFile(candidate)) {
                registry = candidate;
                break;
            }
            current = current.getParent();
        }
        if (registry == null) {
            throw new IllegalStateException("找不到 docs/design/config-registry.yaml");
        }
        Map<String, String> result = new LinkedHashMap<>();
        String key = null;
        for (String line : Files.readAllLines(registry)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- key:")) {
                key = trimmed.substring("- key:".length()).trim();
            } else if (key != null && trimmed.startsWith("effect:")) {
                String effect = trimmed.substring("effect:".length()).split("#")[0].trim();
                result.put(key, effect);
            }
        }
        return result;
    }
}