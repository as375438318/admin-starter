package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.infrastructure.persistence.entity.JobLogDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobLogConverter extends TimeConverter {

    JobLog toDomain(JobLogDO source);

    JobLogDO toDO(JobLog source);
}
