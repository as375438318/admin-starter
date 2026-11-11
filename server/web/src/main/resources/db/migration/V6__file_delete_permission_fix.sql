-- 修正：部分环境 sys_menu.id=44 已被历史 E2E 测试数据占用（逻辑删除仍占物理行），
-- V5 的 INSERT IGNORE 会静默跳过，导致 system:file:delete 权限未落库。
-- 本迁移按权限码判断补齐：新库（V5 已成功写入 id=44）不会重复插入。

-- 1. 清理 V5 在旧库上误授给历史 e2e 菜单的授权（仅当 44 确为 demo:task:e2e 时）
DELETE FROM sys_role_menu
WHERE role_id = 1
  AND menu_id = 44
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 44 AND m.permission_code = 'demo:task:e2e');

-- 2. 缺失时补建文件删除权限菜单（占用当前空闲 id 46）
INSERT INTO sys_menu (id, parent_id, name, menu_type, path, component, permission_code, icon, sort, is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 46, 10, '删除文件', 'F', NULL, NULL, 'system:file:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:file:delete' AND deleted = 0);

-- 3. 管理员角色获得删除权限（按实际菜单 id 授权，兼容 44 或 46）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.permission_code = 'system:file:delete'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
