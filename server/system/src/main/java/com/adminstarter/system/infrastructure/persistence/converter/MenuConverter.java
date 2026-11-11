package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.enums.MenuType;
import com.adminstarter.system.domain.model.Menu;
import com.adminstarter.system.infrastructure.persistence.entity.MenuDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MenuConverter extends TimeConverter {

    Menu toDomain(MenuDO source);

    MenuDO toDO(Menu source);

    default MenuType toMenuType(String code) {
        return code == null ? null : MenuType.fromCode(code);
    }

    default String fromMenuType(MenuType type) {
        return type == null ? null : type.getCode();
    }
}
