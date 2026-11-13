-- V19：修正「查看仪表盘统计」的挂载层级
--
-- 背景：V18 把权限码 system:dashboard:view 作为 F 型节点**直接挂在了「系统管理」目录（M）下**。
-- 后果（用户 2026-11-13 反馈）：菜单管理里出现一条"按钮挂在目录下"的孤儿节点，且 sort=0 排在最前；
-- 角色授权树里它也像"目录级权限"，容易误读。全库仅此一例（其它 F 节点都挂在 C 型页面菜单下）。
--
-- 做法：为工作台补一个**隐藏的 C 型页面菜单**承载它，再把按钮挪到该菜单下。
-- 三条约束：
--   1) 菜单的 permission_code 必须为 NULL —— 工作台是固定路由（/dashboard），
--      页面本身对所有登录用户开放，只有"统计区"需要 system:dashboard:view；
--      给容器菜单配权限码会让普通用户打开首页就 403。
--   2) is_visible=0 —— 不进侧边栏（首页入口是固定的，且已有面包屑/工作台卡片）。
--   3) 前端动态路由按 path 去重（固定路由优先），因此这个菜单**不会**注册出第二条 /dashboard 路由。
-- 沿用既有约定：按业务键（path / permission_code / 角色 code）匹配，不硬编码 id。

-- 1) 工作台页面菜单（隐藏的容器节点）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '工作台', 'C', '/dashboard', 'dashboard/index', NULL, 'HomeFilled', 0, 0, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dashboard' AND menu_type = 'C' AND deleted = 0);

-- 2) 把统计按钮挪到工作台菜单下（原来挂在目录下）
UPDATE sys_menu
SET parent_id = (SELECT id FROM (SELECT id FROM sys_menu WHERE path = '/dashboard' AND menu_type = 'C' AND deleted = 0 LIMIT 1) c),
    sort = 1,
    updated_at = UTC_TIMESTAMP()
WHERE permission_code = 'system:dashboard:view'
  AND deleted = 0
  AND parent_id <> (SELECT id FROM (SELECT id FROM sys_menu WHERE path = '/dashboard' AND menu_type = 'C' AND deleted = 0 LIMIT 1) c2);

-- 3) 容器菜单授给所有在用角色：它不携带权限码，不构成提权；
--    但不授权会让授权树里出现"子节点已授、父节点未授"的悬空状态。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/dashboard' AND m.menu_type = 'C' AND m.deleted = 0
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
