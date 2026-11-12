package com.adminstarter.system.application;

/**
 * 系统参数的「生效方式」判定。
 *
 * <p><b>单一判定处</b>：{@code app.*} 前缀的键由 Spring 的 {@code @ConfigurationProperties} 在启动时绑定
 * （口令策略、令牌、文件上传上限等），改库不会改变运行中的 Bean —— 必须重启；
 * 其余键（{@code sys.*}）由业务在运行期从 {@code sys_config} 读取，改完立即生效（配合缓存失效）。</p>
 *
 * <p>该规则必须与 {@code docs/design/config-registry.yaml} 的 {@code effect} 字段一致，
 * 由 {@code ConfigEffectsTest} 解析台账文件做漂移校验。</p>
 */
public final class ConfigEffects {

    /** 改库即生效（配合缓存失效）。 */
    public static final String HOT = "HOT";
    /** 需重启进程才生效（启动期绑定）。 */
    public static final String STARTUP = "STARTUP";

    /** 启动期绑定的键前缀：Spring 配置，不经过 sys_config 运行期读取。 */
    public static final String STARTUP_PREFIX = "app.";

    private ConfigEffects() {
    }

    public static String effectOf(String key) {
        if (key == null || key.isBlank()) {
            return HOT;
        }
        return key.startsWith(STARTUP_PREFIX) ? STARTUP : HOT;
    }

    /** 该键改完是否立即生效（前端据此提示"已生效/需重启"）。 */
    public static boolean isHotReloadable(String key) {
        return HOT.equals(effectOf(key));
    }
}