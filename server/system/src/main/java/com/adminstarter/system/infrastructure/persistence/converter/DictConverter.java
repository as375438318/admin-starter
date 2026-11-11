package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.DictItem;
import com.adminstarter.system.domain.model.DictType;
import com.adminstarter.system.infrastructure.persistence.entity.DictItemDO;
import com.adminstarter.system.infrastructure.persistence.entity.DictTypeDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DictConverter extends TimeConverter {

    DictType toDomain(DictTypeDO source);

    DictTypeDO toDO(DictType source);

    DictItem toDomain(DictItemDO source);

    DictItemDO toDO(DictItem source);
}
