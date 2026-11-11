package com.adminstarter.system.domain.query;

import lombok.Data;

/**
 * 管理端会话列表查询条件。online 为 null 表示不过滤，true 只看在线，false 只看已失效/离线。
 */
@Data
public class UserSessionQuery {

    private Long userId;
    private String keyword;
    private Boolean online;
    private int page;
    private int pageSize;
}
