package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.infrastructure.persistence.entity.FileChunkUploadDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileChunkUploadConverter extends TimeConverter {

    FileChunkUpload toDomain(FileChunkUploadDO source);

    FileChunkUploadDO toDO(FileChunkUpload source);
}
