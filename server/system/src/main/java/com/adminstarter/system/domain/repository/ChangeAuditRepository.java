package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.domain.query.ChangeAuditQuery;

public interface ChangeAuditRepository {

    void insert(ChangeAudit audit);

    PageResult<ChangeAudit> page(ChangeAuditQuery query);
}