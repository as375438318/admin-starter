-- V22：通用导出中心（CR-20261114-043）
-- 只新增：客户档案的导出权限码 + 授权。不改既有表与既有权限码。

-- 1) 按钮权限码：导出客户档案（挂在「客户档案」页面菜单下，断言 #106）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT c.id, '导出客户档案', 'F', NULL, NULL, 'demo:customer:export', NULL, 5, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:customer:export' AND deleted = 0);

-- 2) 授权：SUPER_ADMIN（导出是批量外带数据，默认不给普通角色）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'demo:customer:export' AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);