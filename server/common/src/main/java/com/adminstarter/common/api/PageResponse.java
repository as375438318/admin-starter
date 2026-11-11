package com.adminstarter.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应，字段与 CT-PAGE-001 对齐：page / pageSize / list / total。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private long page;
    private long pageSize;
    private List<T> list;
    private long total;

    public static <T> PageResponse<T> of(long page, long pageSize, List<T> list, long total) {
        return new PageResponse<>(page, pageSize, list, total);
    }
}
