-- V14：缓存管理模块（见 docs/design/cache-management.md）
-- 只新增：不改 V1–V13；不新增表（缓存管理读取 Redis 运行态，审计复用 sys_oper_log）。
-- 菜单与授权按业务键（权限码 / 角色 code）匹配，不硬编码 id。

-- 1) 菜单：缓存管理（挂在「系统管理」目录下，按 path 匹配父级）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '缓存管理', 'C', '/system/cache', 'system/cache/index', 'system:cache:list', 'Coin', 13, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:cache:list' AND deleted = 0);

-- 2) 按钮权限码：清理缓存（删除单键 / 清空命名空间 / 清理会话缓存共用）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '清理缓存', 'F', NULL, NULL, 'system:cache:clear', NULL, 1, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:cache:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:cache:clear' AND deleted = 0);

-- 3) 授权：只给 SUPER_ADMIN（清缓存影响全局，不授予部门管理员）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:cache:list', 'system:cache:clear')
               AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
