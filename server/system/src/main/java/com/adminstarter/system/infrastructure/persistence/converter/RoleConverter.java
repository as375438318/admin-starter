package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Role;
import com.adminstarter.system.infrastructure.persistence.entity.RoleDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleConverter extends TimeConverter {

    Role toDomain(RoleDO source);

    RoleDO toDO(Role source);
}
