package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Notice;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoticeConverter extends TimeConverter {

    Notice toDomain(NoticeDO source);

    NoticeDO toDO(Notice source);
}
