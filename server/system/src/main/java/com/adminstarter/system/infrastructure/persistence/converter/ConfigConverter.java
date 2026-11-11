package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Config;
import com.adminstarter.system.infrastructure.persistence.entity.ConfigDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConfigConverter extends TimeConverter {

    Config toDomain(ConfigDO source);

    ConfigDO toDO(Config source);
}
