package com.adminstarter.common.security;

/**
 * 请求级数据权限上下文：认证过滤器写入，MyBatis 数据权限拦截器读取。
 */
public final class DataScopeContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
