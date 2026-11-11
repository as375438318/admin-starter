-- V7：权限越权收口 + 数据模型约束/索引治理
-- 说明：本迁移只做三件事（收回授权、补软删除友好的唯一约束、删无用索引）。
-- 不改历史迁移；唯一约束用 MySQL 8 生成列把「已删除行」置 NULL，避免软删除行永久占用唯一槽位。

-- ---------------------------------------------------------------------------
-- 1) 权限收口
--    DEPT_MANAGER：收回 system:user:assign / system:role:menu / system:role:scope
--    EMPLOYEE    ：收回 system:user:list / system:role:list
--    按业务键（角色编码 + 权限码）删除，不按菜单 id 硬编码（见 assertions #12）
-- ---------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE (r.code = 'DEPT_MANAGER'
       AND m.permission_code IN ('system:user:assign', 'system:role:menu', 'system:role:scope'))
   OR (r.code = 'EMPLOYEE'
       AND m.permission_code IN ('system:user:list', 'system:role:list'));

-- ---------------------------------------------------------------------------
-- 2) 唯一约束（软删除友好）
--    取舍：MySQL 8 无部分索引，用 STORED 生成列承载「仅未删除行参与唯一」。
--    已删除行生成列为 NULL；MySQL 唯一索引允许多个 NULL，因此删除后可复用同名/同码。
--    NULL 处理：
--      sys_dict_item.value 为 NOT NULL，删除行 -> NULL；
--      sys_menu.permission_code 可空，目录/无码按钮为空 -> NULL，多行空码互不冲突；
--      sys_dept(parent_id, name) 均 NOT NULL，删除行两列均 -> NULL。
-- ---------------------------------------------------------------------------
ALTER TABLE sys_dict_item
  ADD COLUMN uk_dict_item_value varchar(64)
    GENERATED ALWAYS AS (IF(deleted = 0, `value`, NULL)) STORED
    COMMENT '唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL 释放占位',
  ADD UNIQUE KEY uk_dict_item_type_value (dict_type_id, uk_dict_item_value);

ALTER TABLE sys_menu
  ADD COLUMN uk_permission_code varchar(128)
    GENERATED ALWAYS AS (
      IF(deleted = 0 AND permission_code IS NOT NULL AND permission_code <> '', permission_code, NULL)
    ) STORED
    COMMENT '唯一约束辅助列：仅未删除且非空权限码参与唯一；空码（目录/无码按钮）与已删除行置 NULL',
  ADD UNIQUE KEY uk_menu_permission_code (uk_permission_code);

ALTER TABLE sys_dept
  ADD COLUMN uk_parent_id bigint
    GENERATED ALWAYS AS (IF(deleted = 0, parent_id, NULL)) STORED
    COMMENT '唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL',
  ADD COLUMN uk_name varchar(64)
    GENERATED ALWAYS AS (IF(deleted = 0, name, NULL)) STORED
    COMMENT '唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL',
  ADD UNIQUE KEY uk_dept_parent_name (uk_parent_id, uk_name);

-- ---------------------------------------------------------------------------
-- 3) 索引治理
--    证据（代码检索）：
--      idx_menu_permission   : 权限码只作 SELECT 输出，菜单查询按 role_id / parent_id / deleted 过滤
--      idx_user_create_by    : sys_user 豁免数据权限；用户列表只按 keyword / deptId / isEnabled 过滤
--      idx_dept_ancestors    : 仅用于 FIND_IN_SET(deptId, ancestors)，该写法无法命中 B-tree 索引
--      idx_login_log_user + idx_login_log_created
--                            : 唯一查询是「按 user_id 等值 + created_at 排序」，合并索引即可覆盖
--    保留：idx_oper_log_dept —— 数据权限 DEPT / DEPT_AND_CHILD / CUSTOM_DEPT 会生成 dept_id IN (...) 条件
--          （DataScopeSqlBuilder），删除会丢掉唯一可命中的索引。
-- ---------------------------------------------------------------------------
ALTER TABLE sys_menu DROP INDEX idx_menu_permission;

ALTER TABLE sys_user DROP INDEX idx_user_create_by;

ALTER TABLE sys_dept DROP INDEX idx_dept_ancestors;

ALTER TABLE sys_login_log
  DROP INDEX idx_login_log_user,
  DROP INDEX idx_login_log_created,
  ADD INDEX idx_login_log_user_created (user_id, created_at);
