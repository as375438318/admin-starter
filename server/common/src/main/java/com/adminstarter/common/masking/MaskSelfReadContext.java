package com.adminstarter.common.masking;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * "读自己"豁免：仅由个人中心读取接口显式开启，请求级生效（裁决 3，限读自己）。
 * 未开启、或无请求上下文时一律按权限判定，fail-closed。
 */
public final class MaskSelfReadContext {

    private static final String ATTRIBUTE = "com.adminstarter.masking.selfRead";

    private MaskSelfReadContext() {
    }

    public static void enable() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static boolean isEnabled() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes != null && Boolean.TRUE.equals(attributes.getAttribute(ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST));
    }
}
