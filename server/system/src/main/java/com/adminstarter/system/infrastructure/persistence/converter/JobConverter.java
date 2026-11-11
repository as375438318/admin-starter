package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.infrastructure.persistence.entity.JobDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobConverter extends TimeConverter {

    JobDefinition toDomain(JobDO source);

    JobDO toDO(JobDefinition source);
}
