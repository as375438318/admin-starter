package com.adminstarter.system.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据权限最终计算条件。由用户全部角色的组织范围并集 + 归属过滤最宽档合成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeCondition {

    /** 任一角色为 ALL 时不再限制组织范围。 */
    private boolean unrestricted;
    /** 任一角色 own_filter=NONE 时不加归属限制。 */
    private boolean ownUnrestricted;
    /** 组织范围包含 SELF：本人创建的数据。 */
    private boolean selfIncluded;
    /** 组织范围命中的部门集合（DEPT / DEPT_AND_CHILD / CUSTOM_DEPT 并集）。 */
    private List<Long> deptIds;
    /** CUSTOM_USER 指定人员（按 create_by 匹配）。 */
    private List<Long> userIds;
    /** 归属过滤：本人创建。 */
    private boolean ownCreator;
    /** 归属过滤：本人负责。 */
    private boolean ownOwner;
    private Long currentUserId;
}
