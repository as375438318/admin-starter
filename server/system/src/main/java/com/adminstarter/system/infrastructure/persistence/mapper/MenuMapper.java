package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.entity.MenuDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface MenuMapper extends BaseMapper<MenuDO> {

    @Select("""
            select distinct m.id, m.parent_id, m.name, m.menu_type, m.path, m.component,
                   m.permission_code, m.icon, m.sort, m.is_visible, m.is_enabled,
                   m.created_at, m.updated_at
            from sys_menu m
            join sys_role_menu rm on rm.menu_id = m.id
            join sys_user_role ur on ur.role_id = rm.role_id
            where ur.user_id = #{userId} and m.deleted = 0 and m.is_enabled = 1
            order by m.sort asc, m.id asc
            """)
    List<MenuDO> selectMenusByUserId(@Param("userId") Long userId);

    @Select("""
            select distinct m.permission_code
            from sys_menu m
            join sys_role_menu rm on rm.menu_id = m.id
            join sys_user_role ur on ur.role_id = rm.role_id
            where ur.user_id = #{userId} and m.deleted = 0 and m.is_enabled = 1
              and m.permission_code is not null and m.permission_code <> ''
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            select distinct menu_id from sys_role_menu where role_id in
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">#{roleId}</foreach>
            </script>
            """)
    List<Long> selectMenuIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
