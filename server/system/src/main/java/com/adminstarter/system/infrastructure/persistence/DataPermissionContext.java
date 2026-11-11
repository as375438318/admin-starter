package com.adminstarter.system.infrastructure.persistence;

import java.util.function.Supplier;

/**
 * 显式开关：只有被数据权限管控的查询才改写 SQL，避免误伤登录、字典等非受控查询。
 */
public final class DataPermissionContext {

    private static final ThreadLocal<Boolean> CONTROLLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DataPermissionContext() {
    }

    public static boolean isControlled() {
        return Boolean.TRUE.equals(CONTROLLED.get());
    }

    public static <T> T controlled(Supplier<T> action) {
        boolean previous = isControlled();
        CONTROLLED.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            CONTROLLED.set(previous);
        }
    }

    public static void controlled(Runnable action) {
        controlled(() -> {
            action.run();
            return null;
        });
    }
}
