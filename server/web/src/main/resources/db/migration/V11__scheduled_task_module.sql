-- V11：定时任务模块（见 docs/design/scheduled-task.md）
-- 只新增：不改 V1–V10；菜单与授权按业务键（权限码 / 角色 code）匹配，不硬编码 id。
-- 安全红线：本表只存“已注册任务标识 + 调度配置”，不存脚本、Shell 命令、SQL 字符串。

-- 1) 任务定义
CREATE TABLE sys_job (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(64)  NOT NULL COMMENT '任务名称',
    group_name             VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT' COMMENT '分组（自由文本）',
    job_key                VARCHAR(128) NOT NULL COMMENT '已注册任务标识（ScheduledJob.key）',
    cron_expression        VARCHAR(64)  NOT NULL COMMENT 'Spring 6 段 cron：秒 分 时 日 月 周',
    description            VARCHAR(255) NULL COMMENT '描述',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED 启用 / DISABLED 停用',
    concurrency_policy     VARCHAR(16)  NOT NULL DEFAULT 'DISALLOW' COMMENT 'DISALLOW 禁止并发 / ALLOW 允许并发',
    timeout_seconds        INT          NOT NULL DEFAULT 60 COMMENT '单次执行超时（秒）',
    retry_times            INT          NOT NULL DEFAULT 0 COMMENT '失败重试次数（不含首次）',
    retry_interval_seconds INT          NOT NULL DEFAULT 10 COMMENT '重试间隔（秒）',
    notify_policy          VARCHAR(32)  NOT NULL DEFAULT 'NEVER' COMMENT 'NEVER / ON_FAILURE / ON_CONSECUTIVE',
    notify_threshold       INT          NOT NULL DEFAULT 3 COMMENT '连续失败阈值（ON_CONSECUTIVE 生效）',
    notify_targets         VARCHAR(255) NULL COMMENT '指定接收人 id，逗号分隔；空=任务创建人',
    last_run_at            DATETIME     NULL COMMENT '最近一次执行开始时间（UTC）',
    last_run_result        VARCHAR(16)  NULL COMMENT 'SUCCESS / FAIL / TIMEOUT / SKIPPED',
    next_run_at            DATETIME     NULL COMMENT '下次自动执行时间（UTC）',
    create_by              BIGINT       NULL COMMENT '创建人（失败通知默认接收人）',
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    uk_job_key             VARCHAR(128) GENERATED ALWAYS AS (IF(deleted = 0, job_key, NULL)) STORED
        COMMENT '唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_job_key (uk_job_key),
    KEY idx_job_status (status),
    KEY idx_job_group (group_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务定义';

-- 2) 执行日志（审计数据，不做软删除）
CREATE TABLE sys_job_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    job_id        BIGINT       NOT NULL COMMENT '任务 id',
    job_name      VARCHAR(64)  NOT NULL COMMENT '任务名快照',
    job_key       VARCHAR(128) NOT NULL COMMENT '任务标识快照',
    trigger_type  VARCHAR(16)  NOT NULL COMMENT 'AUTO 自动 / MANUAL 手动',
    started_at    DATETIME     NOT NULL COMMENT '开始时间（UTC）',
    finished_at   DATETIME     NOT NULL COMMENT '结束时间（UTC）',
    elapsed_ms    BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒，含重试间隔）',
    result        VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / FAIL / TIMEOUT / SKIPPED',
    attempt_count INT          NOT NULL DEFAULT 1 COMMENT '实际执行次数（首次+重试）',
    error_summary VARCHAR(255) NULL COMMENT '异常摘要',
    error_stack   TEXT         NULL COMMENT '异常堆栈（截断）',
    affected_rows BIGINT       NULL COMMENT '影响条数',
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_log_job_started (job_id, started_at),
    KEY idx_job_log_started (started_at),
    KEY idx_job_log_result (result)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务执行日志';

-- 3) 菜单：定时任务（挂在「系统管理」目录下，按 path 匹配父级）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '定时任务', 'C', '/system/jobs', 'system/job/index', 'system:job:list', 'Timer', 11, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '/system' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:list' AND deleted = 0);

-- 4) 按钮权限码（挂在定时任务菜单下）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '新增任务', 'F', NULL, NULL, 'system:job:create', NULL, 1, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:create' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '修改任务', 'F', NULL, NULL, 'system:job:update', NULL, 2, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:update' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '删除任务', 'F', NULL, NULL, 'system:job:delete', NULL, 3, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:delete' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '启停任务', 'F', NULL, NULL, 'system:job:status', NULL, 4, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:status' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '手动执行', 'F', NULL, NULL, 'system:job:run', NULL, 5, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:run' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '执行日志', 'F', NULL, NULL, 'system:job:log', NULL, 6, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:job:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:job:log' AND deleted = 0);

-- 5) 授权：只给 SUPER_ADMIN（调度高危，部门管理员不授予）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:job:list', 'system:job:create', 'system:job:update',
                                          'system:job:delete', 'system:job:status', 'system:job:run',
                                          'system:job:log')
               AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
