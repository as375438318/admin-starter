package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.NoticeScope;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeScopeDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoticeScopeConverter extends TimeConverter {

    NoticeScope toDomain(NoticeScopeDO source);

    NoticeScopeDO toDO(NoticeScope source);
}
