package com.adminstarter.system.infrastructure.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明：该实体对应的表不受数据权限控制。
 * <p>必须与 {@link DataPermissionTables#EXEMPT} 登记一致；运行时拦截器只认登记表，注解用于让“豁免”在模型层可见，
 * 禁止依赖“没配置就默认跳过”的隐式行为。</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermissionExempt {

    /** 豁免理由，必填。 */
    String reason();
}
