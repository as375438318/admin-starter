package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.LoginLog;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.system.infrastructure.persistence.entity.LoginLogDO;
import com.adminstarter.system.infrastructure.persistence.entity.OperLogDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LogConverter extends TimeConverter {

    OperLog toDomain(OperLogDO source);

    OperLogDO toDO(OperLog source);

    LoginLog toDomain(LoginLogDO source);

    LoginLogDO toDO(LoginLog source);
}
