package com.adminstarter.system.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排序解析：默认方向必须显式声明，非法输入回落而不是报错。
 *
 * <p>这套规则是修"同一批列表两种默认顺序"（用户/参数/字典/定时任务/操作日志是最早在前，
 * 文件/会话/导出任务是最新在前）之后固化的，改动前先看 {@code CR-20261112-032}。</p>
 */
class SortSupportTest {

    private static final Map<String, String> WHITELIST = Map.of(
            "createdAt", "created_at",
            "title", "title");

    @Test
    @DisplayName("字段：白名单内映射到固定列，空值/未知名回落到默认列")
    void resolveColumn() {
        assertEquals("created_at", SortSupport.resolveColumn(WHITELIST, null, "created_at"));
        assertEquals("created_at", SortSupport.resolveColumn(WHITELIST, "  ", "created_at"));
        assertEquals("title", SortSupport.resolveColumn(WHITELIST, "title", "created_at"));
        // 未在白名单内：回落，且不得把原值拼进 SQL
        assertEquals("created_at", SortSupport.resolveColumn(WHITELIST, "password; drop table sys_user", "created_at"));
    }

    @Test
    @DisplayName("方向：未传时按调用方声明的默认方向，不是恒为升序")
    void defaultDirection() {
        assertFalse(SortSupport.isAsc(null, SortSupport.NEWEST_FIRST), "流水类未传方向必须是最新在前");
        assertTrue(SortSupport.isAsc(null, SortSupport.BY_SORT_COLUMN), "参考数据未传方向必须按排序号正序");
        assertFalse(SortSupport.isAsc("", SortSupport.NEWEST_FIRST));
    }

    @Test
    @DisplayName("方向：显式 asc/desc 覆盖默认值，大小写不敏感")
    void explicitDirectionWins() {
        assertTrue(SortSupport.isAsc("asc", SortSupport.NEWEST_FIRST));
        assertTrue(SortSupport.isAsc("ASC", SortSupport.NEWEST_FIRST));
        assertFalse(SortSupport.isAsc("desc", SortSupport.BY_SORT_COLUMN));
        assertFalse(SortSupport.isAsc("DESC", SortSupport.BY_SORT_COLUMN));
    }

    @Test
    @DisplayName("方向：非法值按默认方向处理，不抛异常")
    void illegalDirectionFallsBack() {
        assertFalse(SortSupport.isAsc("sideways", SortSupport.NEWEST_FIRST));
        assertTrue(SortSupport.isAsc("up", SortSupport.BY_SORT_COLUMN));
    }
}