package com.adminstarter.system.application;

/**
 * User-Agent 启发式解析：只用于展示“设备/浏览器”，不参与任何鉴权判断。
 * <p>解析失败一律降级为 UNKNOWN / null，禁止抛异常影响登录主流程。</p>
 */
public final class UserAgentParser {

    public static final String DEVICE_PC = "PC";
    public static final String DEVICE_MOBILE = "MOBILE";
    public static final String DEVICE_TABLET = "TABLET";
    public static final String DEVICE_UNKNOWN = "UNKNOWN";

    private UserAgentParser() {
    }

    public static String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DEVICE_UNKNOWN;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return DEVICE_TABLET;
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return DEVICE_MOBILE;
        }
        return DEVICE_PC;
    }

    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("Edg/")) {
            return "Edge " + tokenAfter(userAgent, "Edg/");
        }
        if (userAgent.contains("Chrome/")) {
            return "Chrome " + tokenAfter(userAgent, "Chrome/");
        }
        if (userAgent.contains("Firefox/")) {
            return "Firefox " + tokenAfter(userAgent, "Firefox/");
        }
        if (userAgent.contains("Safari/")) {
            return "Safari " + tokenAfter(userAgent, "Version/");
        }
        if (userAgent.contains("MSIE") || userAgent.contains("Trident/")) {
            return "IE";
        }
        return "Other";
    }

    public static String os(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("Windows NT 10.0")) {
            return "Windows 10";
        }
        if (userAgent.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (userAgent.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("iPhone OS")) {
            return "iOS " + tokenAfter(userAgent, "iPhone OS ");
        }
        if (userAgent.contains("Android")) {
            return "Android " + tokenAfter(userAgent, "Android ");
        }
        if (userAgent.contains("Mac OS X")) {
            return "macOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return "Other";
    }

    private static String tokenAfter(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0) {
            return "";
        }
        String tail = text.substring(index + marker.length());
        StringBuilder value = new StringBuilder();
        for (char ch : tail.toCharArray()) {
            if (Character.isDigit(ch) || ch == '.' || ch == '_') {
                value.append(ch);
            } else {
                break;
            }
        }
        return value.toString().replace('_', '.').replaceAll("\\.$", "");
    }
}
