-- V18：仪表盘统计权限码
-- 只新增：不改 V1–V17；不新增表（统计全部取自既有表，聚合口径见 CR-20261112-031）。
-- 菜单与授权按业务键（权限码 / 角色 code）匹配，不硬编码 id。

-- 1) 按钮权限码：查看仪表盘统计
--    兜底策略：只有显式授予才能看到全局统计，避免"任意登录用户都能看到全系统用户数/会话数/日志量"。
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '查看仪表盘统计', 'F', NULL, NULL, 'system:dashboard:view', NULL, 0, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:dashboard:view' AND deleted = 0);

-- 2) 授权：只给 SUPER_ADMIN（统计是全局口径，不随数据权限收窄，见 CR）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'system:dashboard:view' AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);