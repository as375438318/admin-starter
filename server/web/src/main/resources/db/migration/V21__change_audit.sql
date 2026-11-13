-- V21：配置面变更审计（CR-20261114-042）
-- 只新增：新表 + 一个只读页面菜单 + 一个权限码；不改 V1–V20，不动既有表。

-- 1) 审计表：只追加，不开放任何 HTTP 写入口（写入只能由业务事务内的 ChangeAuditRecorder 产生）
CREATE TABLE IF NOT EXISTS sys_change_audit (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    target_type    VARCHAR(32)  NOT NULL COMMENT '对象类型：CONFIG/DICT_TYPE/DICT_ITEM/MENU/ROLE_GRANT',
    target_key     VARCHAR(128) NOT NULL COMMENT '对象业务键：参数键/字典编码:值/菜单 path 或权限码/角色 code',
    target_name    VARCHAR(128) NULL     COMMENT '对象可读名',
    action         VARCHAR(16)  NOT NULL COMMENT '动作：CREATE/UPDATE/DELETE/GRANT',
    before_value   TEXT         NULL     COMMENT '变更前（JSON，敏感对象已打码）',
    after_value    TEXT         NULL     COMMENT '变更后（JSON，敏感对象已打码）',
    is_sensitive   TINYINT      NOT NULL DEFAULT 0 COMMENT '敏感对象：快照整体打码',
    operator_id    BIGINT       NULL,
    operator_name  VARCHAR(64)  NULL,
    ip             VARCHAR(64)  NULL,
    trace_id       VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL,
    -- 审计表是"只追加"的：updated_at / deleted 只是为了与 BaseDO 的固定列保持一致，
    -- 实际永远 updated_at = created_at、deleted = 0（没有更新与删除入口）
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_change_audit_target (target_type, target_key, created_at),
    KEY idx_change_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '配置面变更审计（参数/字典/菜单/角色授权）';

-- 2) 只读页面菜单：变更审计（挂在「系统管理」目录下）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '变更审计', 'C', '/system/audits', 'system/audit/index', 'system:audit:list',
       'Tickets', 14, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/audits' AND menu_type = 'C' AND deleted = 0);

-- 3) 授权：SUPER_ADMIN 与 CROSS_AUDITOR（审计员角色本来就该看审计）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/system/audits' AND m.deleted = 0
WHERE r.code IN ('SUPER_ADMIN', 'CROSS_AUDITOR')
  AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
