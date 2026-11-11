package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.infrastructure.persistence.entity.FileDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileConverter extends TimeConverter {

    FileRecord toDomain(FileDO source);

    FileDO toDO(FileRecord source);
}
