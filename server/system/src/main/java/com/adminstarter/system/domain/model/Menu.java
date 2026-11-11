package com.adminstarter.system.domain.model;

import com.adminstarter.system.domain.enums.MenuType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Menu extends BaseModel {

    private Long parentId;
    private String name;
    private MenuType menuType;
    private String path;
    private String component;
    private String permissionCode;
    private String icon;
    private Integer sort;
    private Boolean isVisible;
    private Boolean isEnabled;
}
