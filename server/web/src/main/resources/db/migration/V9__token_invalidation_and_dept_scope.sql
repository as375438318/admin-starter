-- V9：令牌立即失效与数据权限组织版本（见 docs/design/auth-and-datascope.md §2、§3）
-- 只新增列，不修改 V1–V8；不改表结构语义，不回填历史数据（默认 0 表示现有令牌有效直到下一次密码事件）。
ALTER TABLE sys_user
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0
        COMMENT '令牌版本号：本人改密/管理员重置/停用时自增，旧访问与刷新令牌全部失效' AFTER is_enabled,
    ADD COLUMN token_revoke_reason VARCHAR(32) NULL
        COMMENT '旧令牌失效原因：PASSWORD_RESET / PASSWORD_CHANGED；供 401 分码提示' AFTER token_version;
