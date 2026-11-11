package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.infrastructure.persistence.entity.UserDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter extends TimeConverter {

    User toDomain(UserDO source);

    UserDO toDO(User source);
}
