package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.infrastructure.persistence.entity.DemoTaskDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DemoTaskConverter extends TimeConverter {

    DemoTask toDomain(DemoTaskDO source);

    DemoTaskDO toDO(DemoTask source);
}
