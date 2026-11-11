package com.adminstarter.system.domain.model;

import java.util.List;

/**
 * 缓存 key 分页结果。{@code truncated=true} 表示达到扫描批次上限仍未扫描完命名空间。
 */
public record CacheKeyPage(List<CacheKeyInfo> list, long total, boolean truncated) {
}
