-- 初始化数据（Flyway V2）：部门（含上下级）、角色（含数据范围）、用户、菜单与权限、岗位、字典、参数
-- 默认账号 admin / admin123（BCrypt）；其余演示账号同为 admin123，仅用于数据权限自查

INSERT INTO sys_dept (id, parent_id, ancestors, name, sort, leader, phone, is_enabled, created_at, updated_at, deleted) VALUES
(1, 0, '0', '总部', 1, '管理员', '021-00000000', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 1, '0,1', '研发部', 2, '研发负责人', '021-10000000', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 2, '0,1,2', '研发一组', 3, '组长', '021-11000000', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, 1, '0,1', '市场部', 4, '市场负责人', '021-20000000', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(5, 1, '0,1', '财务部', 5, '财务负责人', '021-30000000', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_role (id, name, code, sort, data_scope, own_filter, remark, is_enabled, created_at, updated_at, deleted) VALUES
(1, '超级管理员', 'SUPER_ADMIN', 1, 'ALL', 'NONE', '全部数据', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, '部门管理员', 'DEPT_MANAGER', 2, 'DEPT_AND_CHILD', 'NONE', '本部门及下级', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, '普通员工', 'EMPLOYEE', 3, 'SELF', 'CREATOR', '仅本人创建', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, '跨部门审计', 'CROSS_AUDITOR', 4, 'CUSTOM_DEPT', 'NONE', '指定研发部与市场部', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(5, '指定人员观察者', 'CUSTOM_VIEWER', 5, 'CUSTOM_USER', 'NONE', '仅指定人员 dev2', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 密码均为 admin123
INSERT INTO sys_user (id, username, nickname, password, dept_id, email, phone, is_enabled, create_by, created_at, updated_at, deleted) VALUES
(1, 'admin', '超级管理员', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 1, 'admin@example.com', '13800000001', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 'deptmgr', '部门管理员', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 2, 'deptmgr@example.com', '13800000002', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 'dev1', '开发一号', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 3, 'dev1@example.com', '13800000003', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, 'dev2', '开发二号', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 3, 'dev2@example.com', '13800000004', 1, 3, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(5, 'market1', '市场一号', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 4, 'market1@example.com', '13800000005', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(6, 'auditor', '审计员', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 5, 'auditor@example.com', '13800000006', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(7, 'viewer', '观察者', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 5, 'viewer@example.com', '13800000007', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 3), (5, 3), (6, 4), (7, 5);

INSERT INTO role_scope_target (id, role_id, target_type, target_id) VALUES
(1, 4, 'DEPT', 2),
(2, 4, 'DEPT', 4),
(3, 5, 'USER', 4);

INSERT INTO sys_menu (id, parent_id, name, menu_type, path, component, permission_code, icon, sort, is_visible, is_enabled, created_at, updated_at, deleted) VALUES
(1, 0, '系统管理', 'M', '/system', 'Layout', NULL, 'Setting', 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 1, '用户管理', 'C', '/system/users', 'system/user/index', 'system:user:list', 'User', 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 1, '角色管理', 'C', '/system/roles', 'system/role/index', 'system:role:list', 'Avatar', 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, 1, '菜单管理', 'C', '/system/menus', 'system/menu/index', 'system:menu:list', 'Menu', 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(5, 1, '部门管理', 'C', '/system/depts', 'system/dept/index', 'system:dept:list', 'OfficeBuilding', 4, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(6, 1, '岗位管理', 'C', '/system/posts', 'system/post/index', 'system:post:list', 'Postcard', 5, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(7, 1, '字典管理', 'C', '/system/dicts', 'system/dict/index', 'system:dict:list', 'Collection', 6, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(8, 1, '系统参数', 'C', '/system/configs', 'system/config/index', 'system:config:list', 'Tools', 7, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(9, 1, '操作日志', 'C', '/system/logs', 'system/log/index', 'system:log:list', 'Document', 8, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(10, 1, '文件管理', 'C', '/system/files', 'system/file/index', 'system:file:list', 'Folder', 9, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(11, 0, '个人中心', 'C', '/profile', 'profile/index', NULL, 'UserFilled', 2, 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(12, 2, '新增用户', 'F', NULL, NULL, 'system:user:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(13, 2, '修改用户', 'F', NULL, NULL, 'system:user:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(14, 2, '删除用户', 'F', NULL, NULL, 'system:user:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(15, 2, '重置密码', 'F', NULL, NULL, 'system:user:reset', NULL, 4, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(16, 2, '启停用户', 'F', NULL, NULL, 'system:user:status', NULL, 5, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(17, 2, '分配角色', 'F', NULL, NULL, 'system:user:assign', NULL, 6, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(18, 3, '新增角色', 'F', NULL, NULL, 'system:role:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(19, 3, '修改角色', 'F', NULL, NULL, 'system:role:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(20, 3, '删除角色', 'F', NULL, NULL, 'system:role:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(21, 3, '分配菜单', 'F', NULL, NULL, 'system:role:menu', NULL, 4, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(22, 3, '配置数据范围', 'F', NULL, NULL, 'system:role:scope', NULL, 5, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(23, 4, '新增菜单', 'F', NULL, NULL, 'system:menu:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(24, 4, '修改菜单', 'F', NULL, NULL, 'system:menu:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(25, 4, '删除菜单', 'F', NULL, NULL, 'system:menu:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(26, 5, '新增部门', 'F', NULL, NULL, 'system:dept:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(27, 5, '修改部门', 'F', NULL, NULL, 'system:dept:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(28, 5, '删除部门', 'F', NULL, NULL, 'system:dept:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(29, 6, '新增岗位', 'F', NULL, NULL, 'system:post:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(30, 6, '修改岗位', 'F', NULL, NULL, 'system:post:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(31, 6, '删除岗位', 'F', NULL, NULL, 'system:post:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(32, 7, '新增字典', 'F', NULL, NULL, 'system:dict:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(33, 7, '修改字典', 'F', NULL, NULL, 'system:dict:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(34, 7, '删除字典', 'F', NULL, NULL, 'system:dict:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(35, 8, '新增参数', 'F', NULL, NULL, 'system:config:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(36, 8, '修改参数', 'F', NULL, NULL, 'system:config:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(37, 8, '删除参数', 'F', NULL, NULL, 'system:config:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(38, 10, '上传文件', 'F', NULL, NULL, 'system:file:upload', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(39, 10, '下载文件', 'F', NULL, NULL, 'system:file:download', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 超级管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 部门管理员：用户/角色/部门/岗位
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 1), (2, 2), (2, 3), (2, 5), (2, 6),
(2, 12), (2, 13), (2, 14), (2, 15), (2, 16), (2, 17),
(2, 18), (2, 19), (2, 20), (2, 21), (2, 22),
(2, 26), (2, 27), (2, 28), (2, 29), (2, 30), (2, 31);

-- 普通员工：仅用户列表与角色列表（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (3, 1), (3, 2), (3, 3);

-- 跨部门审计：用户/角色/部门/岗位/操作日志（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 1), (4, 2), (4, 3), (4, 5), (4, 6), (4, 9);

-- 指定人员观察者：用户列表与操作日志（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (5, 1), (5, 2), (5, 9);

INSERT INTO sys_post (id, dept_id, code, name, sort, is_enabled, created_at, updated_at, deleted) VALUES
(1, 1, 'CEO', '总经理', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 2, 'DEV_LEAD', '研发负责人', 2, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 3, 'DEV', '开发工程师', 3, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, 4, 'SALES', '销售专员', 4, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_dict_type (id, name, type, remark, is_enabled, created_at, updated_at, deleted) VALUES
(1, '用户状态', 'user_status', '启用/停用', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, '通用是否', 'yes_no', '是/否', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_dict_item (id, dict_type_id, label, value, sort, is_enabled, remark, created_at, updated_at, deleted) VALUES
(1, 1, '启用', '1', 1, 1, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 1, '停用', '0', 2, 1, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 2, '是', 'Y', 1, 1, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, 2, '否', 'N', 2, 1, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_config (id, config_key, config_value, name, remark, is_sensitive, is_enabled, created_at, updated_at, deleted) VALUES
(1, 'sys.name', '后台管理系统', '系统名称', '显示在登录页与顶栏', 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, 'sys.upload.max-size', '10485760', '文件大小上限', '单位字节', 0, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, 'sys.security.token.secret', 'local-dev-secret', '令牌密钥', '敏感项，列表中掩码显示', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);
