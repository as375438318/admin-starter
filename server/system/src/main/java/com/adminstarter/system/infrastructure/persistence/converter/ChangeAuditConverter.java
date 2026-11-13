package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.infrastructure.persistence.entity.ChangeAuditDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChangeAuditConverter extends TimeConverter {

    ChangeAudit toDomain(ChangeAuditDO source);

    ChangeAuditDO toDO(ChangeAudit source);
}