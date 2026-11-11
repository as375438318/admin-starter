package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.LoginLog;

public interface LoginLogRepository {

    void insert(LoginLog loginLog);

    PageResult<LoginLog> pageByUser(Long userId, int page, int pageSize);
}
