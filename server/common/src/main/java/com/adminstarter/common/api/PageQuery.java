package com.adminstarter.common.api;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import lombok.Data;

/**
 * 分页请求参数。上限按 CT-PAGE-002 的硬上限 100 校验，超限按 CT-PAGE-003 返回 400。
 */
@Data
public class PageQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private Integer page;
    private Integer pageSize;
    private String sort;
    private String order;

    /**
     * 页码：缺省 1，小于 1 直接按非法参数拒绝。
     * <p>不做“静默归零返回首页”：不同仓储对非正页码的兜底不一致（MyBatis-Plus 归零 vs 自定义 offset 为负），
     * 同一参数在不同接口上表现不同会被误判为分页坏掉（`CT-PAGE-001` 要求页码从 1 开始）。</p>
     */
    public int resolvePage() {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须大于 0");
        }
        return page;
    }

    public int resolvePageSize() {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "pageSize 不能超过 " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    public int offset() {
        int p = resolvePage();
        return (p - 1) * resolvePageSize();
    }
}
