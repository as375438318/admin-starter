-- V10：通知 / 站内信模块（见 docs/design/notification.md）
-- 只新增：不改 V1–V9；菜单与授权按业务键（权限码 / 角色 code）匹配，不硬编码 id。

-- 1) 通知本体（站内信渠道；保留渠道无关字段，未来可复用到邮件/短信）
CREATE TABLE sys_notice (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    level        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 普通 / IMPORTANT 重要 / URGENT 紧急',
    type         VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM / BUSINESS / SECURITY / TASK',
    is_revocable TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否允许撤回',
    revoked_at   DATETIME     NULL COMMENT '撤回时间；非空即已撤回',
    revoked_by   BIGINT       NULL COMMENT '撤回人',
    dept_id      BIGINT       NULL COMMENT '发布人部门（数据权限组织范围）',
    create_by    BIGINT       NULL COMMENT '发布人；系统调用可为空',
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_notice_created (created_at),
    KEY idx_notice_dept (dept_id),
    KEY idx_notice_create_by (create_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知（站内信）';

-- 2) 接收范围规则（多行并集；DEPT 固定含全部下级部门）
CREATE TABLE sys_notice_scope (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    notice_id  BIGINT      NOT NULL,
    scope_type VARCHAR(16) NOT NULL COMMENT 'ALL / ROLE / DEPT / USER',
    target_id  BIGINT      NULL COMMENT 'ROLE=角色id / DEPT=部门id / USER=用户id / ALL=NULL',
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_scope (notice_id, scope_type, target_id),
    KEY idx_notice_scope_notice (notice_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知接收范围';

-- 3) 应读人快照 + 按人已读
CREATE TABLE sys_notice_recipient (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    notice_id  BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    read_at    DATETIME NULL COMMENT '阅读时间；NULL=未读',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted    TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_recipient (notice_id, user_id),
    KEY idx_recipient_notice_read (notice_id, read_at),
    KEY idx_recipient_user_read (user_id, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知应读人快照与已读记录';

-- 4) 菜单：通知公告（挂在「系统管理」目录下，按 path 匹配父级）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '通知公告', 'C', '/system/notices', 'system/notice/index', 'system:notice:list', 'Bell', 10, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:notice:list' AND deleted = 0);

-- 5) 按钮权限码（挂在通知公告菜单下）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '发布通知', 'F', NULL, NULL, 'system:notice:create', NULL, 1, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:notice:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:notice:create' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '撤回通知', 'F', NULL, NULL, 'system:notice:revoke', NULL, 2, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:notice:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:notice:revoke' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '删除通知', 'F', NULL, NULL, 'system:notice:delete', NULL, 3, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:notice:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:notice:delete' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '已读统计', 'F', NULL, NULL, 'system:notice:read-stats', NULL, 4, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:notice:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:notice:read-stats' AND deleted = 0);

-- 6) 授权：SUPER_ADMIN 全部；DEPT_MANAGER 列表 + 发布 + 统计（撤回/删除只给超管）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:notice:list', 'system:notice:create', 'system:notice:revoke',
                                          'system:notice:delete', 'system:notice:read-stats')
               AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:notice:list', 'system:notice:create', 'system:notice:read-stats')
               AND m.deleted = 0
WHERE r.code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
