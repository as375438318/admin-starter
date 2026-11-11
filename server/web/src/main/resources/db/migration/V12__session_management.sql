-- V12：会话管理模块（见 docs/design/session-management.md）
-- 只新增：不改 V1–V11；菜单与授权按业务键（权限码 / 角色 code）匹配，不硬编码 id。
-- 会话记录是运行态数据：不做软删除，清理为物理删除（保留 30 天，见设计文档 §8）。

-- 1) 会话记录
CREATE TABLE sys_user_session (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    session_id        VARCHAR(64)  NOT NULL COMMENT '会话标识（写入 access/refresh 令牌的 sid）',
    user_id           BIGINT       NOT NULL COMMENT '用户 id',
    username          VARCHAR(32)  NOT NULL COMMENT '登录账号快照',
    login_at          DATETIME     NOT NULL COMMENT '登录时间（UTC）',
    last_active_at    DATETIME     NOT NULL COMMENT '最近活跃时间（UTC），60 秒节流更新',
    ip                VARCHAR(64)  NULL COMMENT '登录 IP',
    user_agent        VARCHAR(255) NULL COMMENT '原始 User-Agent',
    device_type       VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'PC / MOBILE / TABLET / UNKNOWN',
    browser           VARCHAR(64)  NULL COMMENT '浏览器识别（UA 启发式）',
    os                VARCHAR(64)  NULL COMMENT '操作系统识别（UA 启发式）',
    invalidated_at    DATETIME     NULL COMMENT '失效时间（UTC）；NULL = 未失效',
    invalidate_reason VARCHAR(32)  NULL COMMENT 'LOGOUT / KICKED_BY_ADMIN / PASSWORD_CHANGED / ACCOUNT_DISABLED / USER_DELETED',
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_sid (session_id),
    KEY idx_session_user (user_id, login_at),
    KEY idx_session_active (invalidated_at, last_active_at),
    KEY idx_session_last_active (last_active_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录会话记录';

-- 2) 菜单：会话管理（挂在「系统管理」目录下，按 path 匹配父级）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '会话管理', 'C', '/system/sessions', 'system/session/index', 'system:session:list', 'Monitor', 12, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:session:list' AND deleted = 0);

-- 3) 按钮权限码（挂在会话管理菜单下）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '强制下线', 'F', NULL, NULL, 'system:session:kick', NULL, 1, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:session:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:session:kick' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '清理失效记录', 'F', NULL, NULL, 'system:session:clean', NULL, 2, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:session:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:session:clean' AND deleted = 0);

-- 4) 授权：只给 SUPER_ADMIN（会话列表含全部用户 IP/设备，强制下线为安全敏感操作）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:session:list', 'system:session:kick', 'system:session:clean')
               AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
