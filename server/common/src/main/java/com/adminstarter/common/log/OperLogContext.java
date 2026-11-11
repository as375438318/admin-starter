package com.adminstarter.common.log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志补充上下文：业务方法把无法从入参序列化得到的审计字段（文件名、条数、通道等）放进来，
 * 由 web 层 {@code OperLogAspect} 读取并合并到 params 后清理。禁止跨请求复用。
 */
public final class OperLogContext {

    private static final ThreadLocal<Map<String, Object>> HOLDER = ThreadLocal.withInitial(LinkedHashMap::new);

    private OperLogContext() {
    }

    public static void put(String key, Object value) {
        HOLDER.get().put(key, value);
    }

    public static Map<String, Object> snapshot() {
        return new LinkedHashMap<>(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
