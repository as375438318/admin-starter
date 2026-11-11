package com.adminstarter.common.cache;

/**
 * 缓存命名空间登记项。前缀的唯一定义处是 {@link CacheNamespaces}，本类型只描述元数据。
 *
 * @param code       对外使用的命名空间标识（接口只接受 code，不接受前缀）
 * @param name       中文名称
 * @param prefix     单键命名空间为完整 key；前缀命名空间以冒号结尾
 * @param mode       EXACT（单键）/ PREFIX（前缀）
 * @param purpose    用途说明
 * @param clearEffect 清除后果，前端原样展示
 * @param sensitive  是否含令牌/会话/用户名等敏感标识（只展示 key 名与 TTL，不读 value）
 * @param clearable  是否允许清理；安全降级类命名空间为 false
 */
public record CacheNamespace(String code, String name, String prefix, Mode mode, String purpose,
                             String clearEffect, boolean sensitive, boolean clearable) {

    public enum Mode {
        EXACT,
        PREFIX
    }

    /** key 是否严格属于本命名空间：单键要求完全相等，前缀要求前缀之后还有内容。 */
    public boolean matches(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return mode == Mode.EXACT
                ? prefix.equals(key)
                : key.startsWith(prefix) && key.length() > prefix.length();
    }
}
