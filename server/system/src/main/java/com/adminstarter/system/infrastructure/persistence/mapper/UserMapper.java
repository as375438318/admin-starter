package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.DataPermissionExempt;
import com.adminstarter.system.infrastructure.persistence.entity.UserDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@DataPermissionExempt(reason = "用户表不受数据权限控制；按部门查看用户使用列表筛选参数 deptId")
public interface UserMapper extends BaseMapper<UserDO> {

    @Update("update sys_user set token_version = token_version + 1, token_revoke_reason = #{reason}, "
            + "updated_at = now() where id = #{userId} and deleted = 0")
    int bumpTokenVersion(@Param("userId") Long userId, @Param("reason") String reason);

    @Select("select role_id from sys_user_role where user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Delete("delete from sys_user_role where user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("insert into sys_user_role(user_id, role_id) values(#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("""
            <script>
            select ur.user_id as userId, r.id as roleId, r.name as roleName
            from sys_user_role ur
            join sys_role r on r.id = ur.role_id
            where r.deleted = 0 and ur.user_id in
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">#{userId}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectRoleBindings(@Param("userIds") Collection<Long> userIds);

    @Select("select id from sys_user where deleted = 0 and is_enabled = 1")
    List<Long> selectEnabledUserIds();

    @Select("""
            <script>
            select id from sys_user
            where deleted = 0 and is_enabled = 1 and dept_id in
            <foreach collection="deptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
            </script>
            """)
    List<Long> selectEnabledUserIdsByDeptIds(@Param("deptIds") Collection<Long> deptIds);
}
