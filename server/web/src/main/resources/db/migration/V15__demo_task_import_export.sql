-- V15：示例任务导入导出（见 docs/design/import-export-framework.md、CR-20261110-002）
-- 只新增：不改既有表；菜单与授权按权限码/角色 code 业务键匹配。

-- 1) 异步导出任务表（临时文件不进入 sys_file）
CREATE TABLE sys_export_task (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL COMMENT '导出人',
    username         VARCHAR(64)  NULL COMMENT '导出人账号快照',
    module           VARCHAR(64)  NOT NULL COMMENT '模块标识，如 demo-task',
    file_name        VARCHAR(255) NOT NULL COMMENT '下载文件名',
    query_params     VARCHAR(2000) NULL COMMENT '导出过滤条件快照（JSON）',
    total_rows       BIGINT       NOT NULL DEFAULT 0 COMMENT '预计导出行数',
    progress         INT          NOT NULL DEFAULT 0 COMMENT '0-100',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED/EXPIRED',
    cancel_requested TINYINT      NOT NULL DEFAULT 0 COMMENT '用户请求取消',
    error_message    VARCHAR(500) NULL COMMENT '失败原因',
    file_path        VARCHAR(500) NULL COMMENT '临时文件绝对路径（服务端专用）',
    file_size        BIGINT       NULL COMMENT '文件字节数',
    expires_at       DATETIME     NULL COMMENT '文件过期时间（UTC）',
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_export_task_user (user_id, created_at),
    KEY idx_export_task_status (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '异步导出任务';

-- 2) 权限码：导入（含模板下载）与导出各一枚，挂在示例业务菜单下
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '导入任务', 'F', NULL, NULL, 'demo:task:import', NULL, 4, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'demo:task:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:task:import' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '导出任务', 'F', NULL, NULL, 'demo:task:export', NULL, 5, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'demo:task:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'demo:task:export' AND deleted = 0);

-- 3) 授权：超级管理员与部门管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'demo:task:import' AND m.deleted = 0
WHERE r.code IN ('SUPER_ADMIN', 'DEPT_MANAGER') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = 'demo:task:export' AND m.deleted = 0
WHERE r.code IN ('SUPER_ADMIN', 'DEPT_MANAGER') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 4) 过期清理任务（每小时执行；job_key 与 ExportFileCleanupJob#key 一致）
INSERT INTO sys_job (name, group_name, job_key, cron_expression, description, status,
                     concurrency_policy, timeout_seconds, retry_times, retry_interval_seconds,
                     notify_policy, notify_threshold, create_by, created_at, updated_at, deleted)
SELECT '导出文件清理', 'DEFAULT', 'export-file-cleanup', '0 0 * * * *',
       '清理超过 24 小时的导出临时文件', 'ENABLED', 'DISALLOW', 120, 0, 10,
       'NEVER', 3, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE job_key = 'export-file-cleanup' AND deleted = 0);
