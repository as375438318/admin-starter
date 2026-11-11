package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.ExportTask;
import com.adminstarter.system.infrastructure.persistence.entity.ExportTaskDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExportTaskConverter extends TimeConverter {

    ExportTask toDomain(ExportTaskDO source);

    ExportTaskDO toDO(ExportTask source);
}
