package com.adminstarter.common.cache;

import java.util.List;
import java.util.Optional;

/**
 * Redis 缓存命名空间白名单。**前缀的唯一定义处**：新增 Redis 写入点必须在此登记，
 * 否则会落入“未登记键”，缓存管理页只读且不可清理（见 docs/design/cache-management.md）。
 */
public final class CacheNamespaces {

    public static final String CODE_PUBLIC_CONFIG = "public-config";
    public static final String CODE_DATASCOPE = "datascope";
    public static final String CODE_DATASCOPE_DEPT_VERSION = "datascope-deptver";
    public static final String CODE_SESSION_TOUCH = "session-touch";
    public static final String CODE_AUTH_REFRESH = "auth-refresh";
    public static final String CODE_AUTH_DENY = "auth-deny";
    public static final String CODE_AUTH_FAIL = "auth-fail";
    public static final String CODE_IMPORT_DEDUP = "import-dedup";
    public static final String CODE_DICT = "dict";

    public static final String PUBLIC_CONFIG_KEY = "admin:publicconfig:v2";
    public static final String DATASCOPE_PREFIX = "admin:datascope:v1:";
    public static final String DATASCOPE_DEPT_VERSION_KEY = "admin:datascope:deptver";
    public static final String SESSION_TOUCH_PREFIX = "admin:session:touch:";
    public static final String AUTH_REFRESH_PREFIX = "admin:auth:refresh:";
    public static final String AUTH_DENY_PREFIX = "admin:auth:deny:";
    public static final String AUTH_FAIL_PREFIX = "admin:auth:fail:";
    public static final String IMPORT_DEDUP_PREFIX = "admin:import:dedup:v1:";
    public static final String DICT_PREFIX = "admin:dict:v1:";

    private static final List<CacheNamespace> ALL = List.of(
            new CacheNamespace(CODE_PUBLIC_CONFIG, "公开配置缓存", PUBLIC_CONFIG_KEY,
                    CacheNamespace.Mode.EXACT,
                    "免鉴权公开配置（站点名称/标语/版权/ICP/上传上限）",
                    "下一次公开配置请求会回源数据库并重新缓存；用户无感知，仅多一次查询。",
                    false, true),
            new CacheNamespace(CODE_DATASCOPE, "数据权限条件", DATASCOPE_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "按用户缓存数据范围 SQL 条件",
                    "下一次受控查询按数据库重算数据范围；可见范围不变，数据库压力短时上升。",
                    false, true),
            new CacheNamespace(CODE_DATASCOPE_DEPT_VERSION, "组织版本号", DATASCOPE_DEPT_VERSION_KEY,
                    CacheNamespace.Mode.EXACT,
                    "组织树变更版本号，驱动数据权限缓存失效",
                    "版本号重置；已缓存的数据权限条件按 5 分钟 TTL 自然失效，下一次请求重算。不改变授权结果。",
                    false, true),
            new CacheNamespace(CODE_SESSION_TOUCH, "会话活跃节流", SESSION_TOUCH_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "每请求写 last_active_at 的 60 秒节流键",
                    "下一次请求会重新写一次会话活跃时间；不影响登录态，在线状态最多滞后一个节流窗口。",
                    true, true),
            new CacheNamespace(CODE_AUTH_REFRESH, "刷新令牌白名单", AUTH_REFRESH_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "refresh 令牌白名单校验",
                    "所有刷新令牌失效：现有 access 令牌到期（最长 15 分钟）后必须重新登录。",
                    true, true),
            new CacheNamespace(CODE_AUTH_DENY, "Access 令牌黑名单", AUTH_DENY_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "登出后 access 令牌立即失效",
                    "清除会让已登出但未过期的 access 令牌重新可用（最长 15 分钟），属安全降级，因此不提供清理。",
                    true, false),
            new CacheNamespace(CODE_AUTH_FAIL, "登录失败计数", AUTH_FAIL_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "连续失败锁定 15 分钟",
                    "清除会提前解除登录失败锁定，弱化暴力破解防护，因此不提供清理。",
                    true, false),
            new CacheNamespace(CODE_DICT, "字典项缓存", DICT_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "按字典类型缓存的字典项（写操作立即失效，TTL 60 秒兜底）",
                    "下一次字典请求回源数据库；用户可见的字典内容不变，仅多一次查询。",
                    false, true),
            new CacheNamespace(CODE_IMPORT_DEDUP, "导入去重指纹", IMPORT_DEDUP_PREFIX,
                    CacheNamespace.Mode.PREFIX,
                    "同一用户 5 分钟内重复提交同一模块同一文件的短时去重",
                    "删除后同一文件可被立即重传；成功导入的文件可能产生重复数据。",
                    true, false)
    );

    private CacheNamespaces() {
    }

    public static List<CacheNamespace> all() {
        return ALL;
    }

    public static Optional<CacheNamespace> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return ALL.stream().filter(namespace -> namespace.code().equals(code)).findFirst();
    }

    public static CacheNamespace require(String code) {
        return findByCode(code).orElseThrow(() -> new IllegalArgumentException("未登记的缓存命名空间: " + code));
    }
}
