package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Customer;
import com.adminstarter.system.infrastructure.persistence.entity.CustomerDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerConverter extends TimeConverter {

    Customer toDomain(CustomerDO source);

    CustomerDO toDO(Customer source);
}
