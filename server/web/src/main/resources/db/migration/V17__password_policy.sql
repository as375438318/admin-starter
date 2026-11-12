-- 密码策略（等保 P0-1）：新增密码更新时间与"下次登录必须改密"标记。
-- 存量账号一律置为需改密：当前初始口令为弱口令 admin123，且此前无任何复杂度约束。
ALTER TABLE sys_user
    ADD COLUMN password_updated_at DATETIME NULL COMMENT '密码最近修改时间（用于过期提醒）',
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=下次登录必须修改密码';

UPDATE sys_user
SET must_change_password = 1
WHERE deleted = 0;