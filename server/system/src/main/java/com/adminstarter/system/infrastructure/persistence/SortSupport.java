package com.adminstarter.system.infrastructure.persistence;

import java.util.Map;

/**
 * 排序字段白名单与方向解析：客户端传入的字段必须映射到固定列，禁止直接进 ORDER BY（BE-DB-006）。
 *
 * <p><b>默认方向必须由调用方显式声明</b>（2026-11-12 起）。原因：此前 {@code isAsc(order)} 对 null 返回
 * {@code true}，于是"忘记传参"等价于"升序"，导致同一批列表出现两种默认顺序——用户/参数/字典/定时任务/
 * 操作日志是"最早的在前"，而文件/会话/导出任务是"最新的在前"。默认值靠副作用产生，是这类不一致的根源。</p>
 *
 * <p>全项目排序约定见 {@code CR-20261112-032}：</p>
 * <ul>
 *   <li>流水/事务类列表（created_at 排序）→ 默认 <b>DESC</b>（最新在前）</li>
 *   <li>带显式排序号的参考数据（sort 列）→ 默认 <b>ASC</b>（按人工排序号）</li>
 * </ul>
 */
public final class SortSupport {

    /** 流水/事务类列表的默认方向：最新在前（DESC）。 */
    public static final boolean NEWEST_FIRST = true;

    /** 带人工排序号的参考数据（sort 列）的默认方向：按排序号正序（ASC）。 */
    public static final boolean BY_SORT_COLUMN = false;

    private SortSupport() {
    }

    /**
     * 解析排序字段；传空或不在白名单内时回落到 {@code defaultColumn}。
     * <p>白名单外的字段<b>静默回落</b>而不报错：客户端拿旧字段名调用不应 500，但也不能把任意字段拼进 SQL。</p>
     */
    public static String resolveColumn(Map<String, String> whitelist, String sort, String defaultColumn) {
        if (sort == null || sort.isBlank()) {
            return defaultColumn;
        }
        String column = whitelist.get(sort);
        return column == null ? defaultColumn : column;
    }

    /**
     * 应用排序：主排序列 + <b>同方向的 id 次级键</b>。
     *
     * <p>次级键不是可有可无的装饰：种子数据里多条记录的 {@code created_at} 完全相同，没有次级键时
     * 数据库返回顺序取决于索引/主键，翻页会出现"重复或漏条"。方向跟随主排序，
     * 否则 {@code order=asc} 时会出现"时间正序但 id 倒序"的诡异组合（本 CR 第一版就踩了）。</p>
     */
    public static <T> void applyOrder(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T> wrapper,
                                      String column, boolean ascending) {
        wrapper.orderBy(true, ascending, column);
        if (!"id".equals(column)) {
            wrapper.orderBy(true, ascending, "id");
        }
    }

    /**
     * 是否升序。
     *
     * @param order             客户端传入的方向（{@code asc}/{@code desc}），其余值按默认方向
     * @param defaultDescending 未传方向时的默认值；<b>必须由调用方按列表语义显式给出</b>
     */
    public static boolean isAsc(String order, boolean defaultDescending) {
        if (order == null || order.isBlank()) {
            return !defaultDescending;
        }
        if ("asc".equalsIgnoreCase(order)) {
            return true;
        }
        if ("desc".equalsIgnoreCase(order)) {
            return false;
        }
        // 非法值按默认方向处理，不抛错（与字段白名单回落的策略保持一致）
        return !defaultDescending;
    }
}