-- 文件能力增强（第二档第 4 项）
-- 只新增：sys_file 增列、分片上传会话表、2 枚权限码、孤儿清理定时任务种子。
-- 禁止修改 V1–V12。

-- 1) sys_file：内容指纹、回收站三列、检索索引
ALTER TABLE sys_file
    ADD COLUMN sha256     CHAR(64) NULL COMMENT '内容 SHA-256（秒传/物理去重）' AFTER size,
    ADD COLUMN deleted    TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 回收站' AFTER created_at,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '进入回收站时间（UTC）' AFTER deleted,
    ADD COLUMN deleted_by BIGINT   NULL COMMENT '执行删除的用户 id' AFTER deleted_at;

ALTER TABLE sys_file
    ADD KEY idx_file_sha256 (sha256),
    ADD KEY idx_file_deleted (deleted, created_at);

-- 2) 分片上传会话（无 deleted 列，不做逻辑删除；EXEMPT 数据权限，靠 uploader_id 归属校验）
CREATE TABLE sys_file_chunk_upload (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    upload_id       CHAR(32)     NOT NULL COMMENT '会话标识（32 位十六进制）',
    uploader_id     BIGINT       NOT NULL COMMENT '上传者（会话唯一归属人）',
    dept_id         BIGINT       NULL COMMENT '上传者部门快照',
    original_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type    VARCHAR(128) NULL,
    size            BIGINT       NOT NULL COMMENT '文件总字节数',
    sha256          CHAR(64)     NOT NULL COMMENT '声明的内容 SHA-256',
    chunk_size      INT          NOT NULL COMMENT '分片字节数（256KB–5MB）',
    total_chunks    INT          NOT NULL COMMENT '分片总数',
    uploaded_chunks TEXT         NULL COMMENT '已上传分片序号，逗号分隔',
    status          VARCHAR(16)  NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING / MERGED / ABORTED / EXPIRED',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chunk_upload_id (upload_id),
    KEY idx_chunk_status_updated (status, updated_at),
    KEY idx_chunk_uploader (uploader_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分片上传会话（断点续传）';

-- 3) 权限码：恢复 / 彻底删除（挂在文件菜单下）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '恢复文件', 'F', NULL, NULL, 'system:file:restore', NULL, 4, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:file:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:file:restore' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT m.id, '彻底删除', 'F', NULL, NULL, 'system:file:purge', NULL, 5, 1, 1,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM sys_menu m
WHERE m.permission_code = 'system:file:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'system:file:purge' AND deleted = 0);

-- 4) 授权：只给 SUPER_ADMIN（彻底删除不可逆，部门管理员不授予）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code IN ('system:file:restore', 'system:file:purge')
               AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 5) 孤儿清理任务种子：挂到既有 sys_job，不新建调度器
INSERT INTO sys_job (name, group_name, job_key, cron_expression, description, status,
                     concurrency_policy, timeout_seconds, retry_times, retry_interval_seconds,
                     notify_policy, notify_threshold, create_by, created_at, updated_at, deleted)
SELECT '文件孤儿清理', 'FILE', 'file-orphan-cleanup', '0 0 2 * * *',
       '清理超过 24 小时的临时分片、临时文件与无主物理文件', 'ENABLED',
       'DISALLOW', 300, 1, 10, 'NEVER', 3, NULL,
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE job_key = 'file-orphan-cleanup' AND deleted = 0);
