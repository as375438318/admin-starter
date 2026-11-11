package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.entity.DeptDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DeptMapper extends BaseMapper<DeptDO> {

    /**
     * 数据权限“本部门及下级”的 SQL 条件：祖先链包含目标部门。
     */
    @Select("select id from sys_dept where deleted = 0 and (id = #{deptId} or find_in_set(#{deptId}, ancestors))")
    List<Long> selectDescendantIds(@Param("deptId") Long deptId);
}
