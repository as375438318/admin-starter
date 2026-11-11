package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.system.domain.query.OperLogQuery;

public interface OperLogRepository {

    void insert(OperLog operLog);

    PageResult<OperLog> page(OperLogQuery query);
}
