package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.infrastructure.persistence.entity.DeptDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeptConverter extends TimeConverter {

    Dept toDomain(DeptDO source);

    DeptDO toDO(Dept source);
}
