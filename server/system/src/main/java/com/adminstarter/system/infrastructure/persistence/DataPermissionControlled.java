package com.adminstarter.system.infrastructure.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明：该实体对应的表受数据权限控制，并标注负责人字段。
 * <p>必须与 {@link DataPermissionTables#CONTROLLED} 登记一致。业务表如未标注负责人字段，
 * OWNER 归属过滤会回落到 create_by，属于错误配置。</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermissionControlled {

    /** 负责人字段名（数据库列名）。 */
    String ownerColumn() default "create_by";

    /** 数据归属人字段名；SELF / CUSTOM_USER 用它匹配。 */
    String selfColumn() default "create_by";
}
