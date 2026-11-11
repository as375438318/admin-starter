-- 配置治理：删除无效的令牌密钥展示项、补公开配置种子、补文件删除权限
-- 真实签名密钥不在数据库中，见 docs/env/local-dev.md

-- 1. 删除 sys.security.token.secret（该值从未被任何代码消费）
DELETE FROM sys_config WHERE config_key = 'sys.security.token.secret';

-- 2. 补公开配置种子，让参数页可直接编辑，公开接口不再返回 null
INSERT IGNORE INTO sys_config (config_key, config_value, name, remark, is_sensitive, is_enabled, created_at, updated_at, deleted) VALUES
('sys.name.slogan', '通用底座 · 开箱即用', '系统副标题', '显示在登录页品牌下方', 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
('sys.copyright', '© 2026 后台管理系统', '页脚版权', '登录页页脚版权文案', 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
('sys.icp', '京ICP备00000000号', '备案号', '登录页页脚备案号', 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 3. 明确上传上限的字节口径与范围
UPDATE sys_config
SET remark = '单位字节，范围 1MB–200MB（1048576–209715200）'
WHERE config_key = 'sys.upload.max-size';

-- 4. 文件删除权限（菜单类型 F，挂在文件管理下）
INSERT IGNORE INTO sys_menu (id, parent_id, name, menu_type, path, component, permission_code, icon, sort, is_visible, is_enabled, created_at, updated_at, deleted) VALUES
(44, 10, '删除文件', 'F', NULL, NULL, 'system:file:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 管理员角色获得删除权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 44);
