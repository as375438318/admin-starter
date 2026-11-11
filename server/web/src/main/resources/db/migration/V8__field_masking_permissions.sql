-- V8：字段级脱敏权限码与授权（见 docs/design/field-masking.md §4、§10）
-- 只新增：不改 V1–V7，不按菜单 id / 角色 id 硬编码，一律按角色 code + 权限码业务键匹配。
-- 4 枚码挂靠在「用户管理」菜单下（parent_id = 2 为既有种子数据）。

-- 1) 权限码（已存在则跳过，避免重复执行报唯一约束）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 2, '用户手机号-查看', 'F', NULL, NULL, 'system:user:phone:view', NULL, 7, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:user:phone:view' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 2, '用户手机号-掩码', 'F', NULL, NULL, 'system:user:phone:mask', NULL, 8, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:user:phone:mask' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 2, '用户邮箱-查看', 'F', NULL, NULL, 'system:user:email:view', NULL, 9, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:user:email:view' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 2, '用户邮箱-掩码', 'F', NULL, NULL, 'system:user:email:mask', NULL, 10, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:user:email:mask' AND deleted = 0);

-- 2) 授权：SUPER_ADMIN -> view；DEPT_MANAGER -> mask；其他角色不授予
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:user:phone:view' AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:user:email:view' AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:user:phone:mask' AND m.deleted = 0
WHERE r.code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:user:email:mask' AND m.deleted = 0
WHERE r.code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
