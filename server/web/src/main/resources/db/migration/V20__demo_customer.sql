-- V20：客户档案模块（由 tools/gen-module 生成，之后按需手改）
-- 三件事：建表 + 菜单/权限码 + 授权。全部按业务键（path / permission_code / 角色 code）匹配，不硬编码 id。

-- 1) 建表
CREATE TABLE IF NOT EXISTS demo_customer (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL                                       COMMENT '客户名称',
    level            VARCHAR(16) NULL                                            COMMENT '客户等级',
    contact          VARCHAR(64) NULL                                            COMMENT '联系人',
    phone            VARCHAR(32) NULL                                            COMMENT '联系电话',
    is_enabled       TINYINT      NOT NULL DEFAULT 1                             COMMENT '状态',
    remark           VARCHAR(255) NULL                                           COMMENT '备注',
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_demo_customer_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户档案';

-- 2) 菜单：页面菜单（parentMenuPath 为空则挂顶层）。NOT EXISTS 保证可重复执行
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 0, '客户档案', 'C', '/demo/customers', 'demo/customer/index', 'demo:customer:list',
       'Avatar', 12, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0);

-- 3) 按钮权限码：挂在刚建的页面菜单下（不要挂到目录上，见断言 #106）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
    SELECT c.id, '查询客户档案', 'F', NULL, NULL, 'demo:customer:list', NULL, 1, 1, 1,
           UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
    FROM (SELECT id FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:customer:list' AND deleted = 0)
    UNION ALL
    SELECT c.id, '新增客户档案', 'F', NULL, NULL, 'demo:customer:create', NULL, 2, 1, 1,
           UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
    FROM (SELECT id FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:customer:create' AND deleted = 0)
    UNION ALL
    SELECT c.id, '修改客户档案', 'F', NULL, NULL, 'demo:customer:update', NULL, 3, 1, 1,
           UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
    FROM (SELECT id FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:customer:update' AND deleted = 0)
    UNION ALL
    SELECT c.id, '删除客户档案', 'F', NULL, NULL, 'demo:customer:delete', NULL, 4, 1, 1,
           UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
    FROM (SELECT id FROM sys_menu WHERE path = '/demo/customers' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:customer:delete' AND deleted = 0);

-- 4) 授权：SUPER_ADMIN 全量；DEPT_MANAGER 只读（按需调整）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (m.path = '/demo/customers' OR m.permission_code LIKE 'demo:customer:%') AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'demo:customer:list' AND m.deleted = 0
WHERE r.code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
