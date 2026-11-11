package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserRoleBinding;
import com.adminstarter.system.domain.query.UserQuery;

import java.util.Collection;
import java.util.List;

public interface UserRepository {

    User findById(Long id);

    User findByUsername(String username);

    /** 批量按账号查询，供导入一次性预加载，禁止逐行查库。 */
    List<User> findByUsernames(Collection<String> usernames);

    /** 引用翻译用轻量查询：只取 id/username/nickname/dept_id/is_enabled，不加载密码。 */
    List<User> findAllForReference();

    boolean existsByUsername(String username);

    void insert(User user);

    void update(User user);

    void deleteById(Long id);

    /**
     * 自增令牌版本并使旧访问/刷新令牌失效；reason 写入 401 分码提示依据。
     */
    void bumpTokenVersion(Long id, String reason);

    PageResult<User> page(UserQuery query);

    List<User> findByIds(Collection<Long> ids);

    List<Long> findRoleIdsByUserId(Long userId);

    List<UserRoleBinding> findRoleBindings(Collection<Long> userIds);

    List<String> findRoleCodesByUserId(Long userId);

    void replaceUserRoles(Long userId, Collection<Long> roleIds);

    long countByDeptId(Long deptId);

    long countByDeptIds(Collection<Long> deptIds);

    /** 全部启用且未删除的用户 id（通知“全员”范围展开用）。 */
    List<Long> findEnabledUserIds();

    /** 指定部门集合内启用且未删除的用户 id（通知“指定部门含下级”展开用）。 */
    List<Long> findEnabledUserIdsByDeptIds(Collection<Long> deptIds);
}
