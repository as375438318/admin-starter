package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.entity.RoleDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RoleMapper extends BaseMapper<RoleDO> {

    @Select("select role_id from sys_role_menu where role_id = #{roleId}")
    List<Long> selectMenuIds(@Param("roleId") Long roleId);

    @Delete("delete from sys_role_menu where role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("insert into sys_role_menu(role_id, menu_id) values(#{roleId}, #{menuId})")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Select("""
            select distinct r.id, r.name, r.code, r.sort, r.data_scope, r.own_filter, r.remark, r.is_enabled,
                   r.created_at, r.updated_at
            from sys_role r
            join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId} and r.deleted = 0 and r.is_enabled = 1
            order by r.sort asc, r.id asc
            """)
    List<RoleDO> selectRolesByUserId(@Param("userId") Long userId);

    @Select("select user_id from sys_user_role where role_id = #{roleId}")
    List<Long> selectUserIds(@Param("roleId") Long roleId);

    @Select("select count(1) from sys_role_menu where menu_id = #{menuId}")
    long countByMenuId(@Param("menuId") Long menuId);
}
