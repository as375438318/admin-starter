package com.adminstarter.system.domain.enums;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import lombok.Getter;

/**
 * 菜单三级类型：目录 / 菜单 / 按钮。
 */
@Getter
public enum MenuType {

    DIRECTORY("M"),
    MENU("C"),
    BUTTON("F");

    private final String code;

    MenuType(String code) {
        this.code = code;
    }

    public static MenuType fromCode(String code) {
        for (MenuType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BizException(ErrorCode.INVALID_PARAMETER, "未知菜单类型: " + code);
    }
}
