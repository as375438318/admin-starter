package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.NoticeRecipient;
import com.adminstarter.system.infrastructure.persistence.entity.NoticeRecipientDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoticeRecipientConverter extends TimeConverter {

    NoticeRecipient toDomain(NoticeRecipientDO source);

    NoticeRecipientDO toDO(NoticeRecipient source);
}
