-- V16：岗位管理接入导入导出（见 docs/design/import-export-framework.md、CR-20261110-003）
-- 只新增：不改既有表；菜单与授权按权限码/角色 code 业务键匹配。

-- 1) 权限码：导入（含模板下载）与导出各一枚，挂在岗位管理菜单下
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '导入岗位', 'F', NULL, NULL, 'system:post:import', NULL, 4, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:post:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:post:import' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '导出岗位', 'F', NULL, NULL, 'system:post:export', NULL, 5, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:post:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:post:export' AND deleted = 0);

-- 2) 授权：超级管理员与部门管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:post:import' AND m.deleted = 0
WHERE r.code IN ('SUPER_ADMIN', 'DEPT_MANAGER') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:post:export' AND m.deleted = 0
WHERE r.code IN ('SUPER_ADMIN', 'DEPT_MANAGER') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
