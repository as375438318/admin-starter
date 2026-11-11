-- 数据权限参考实现：示例业务表 biz_demo_task（Flyway V3）
-- 同时提供 6 档部门范围与 3 档归属过滤的演示账号，用于脚手架自查。

CREATE TABLE biz_demo_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(128) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'TODO' COMMENT 'TODO / DOING / DONE',
    dept_id     BIGINT       NOT NULL COMMENT '归属部门，受控列',
    owner_id    BIGINT       NULL COMMENT '负责人，OWNER 归属过滤列（业务表显式标注）',
    create_by   BIGINT       NULL COMMENT '创建人，SELF / CUSTOM_USER 归属列',
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_demo_task_dept (dept_id),
    KEY idx_demo_task_owner (owner_id),
    KEY idx_demo_task_create_by (create_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '示例业务任务（数据权限参考实现）';

-- 演示账号（密码均为 admin123）
INSERT INTO sys_user (id, username, nickname, password, dept_id, email, phone, is_enabled, create_by, created_at, updated_at, deleted) VALUES
(8, 'demo_dept', '部门范围演示', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 2, 'demo_dept@example.com', '13800000008', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(9, 'demo_owner', '负责人过滤演示', '$2a$10$K/5DB4WGO/n8RPIaoX1d.eqA1TM9WlHFVB4vOq0K1/ZcK1K.XKKNC', 4, 'demo_owner@example.com', '13800000009', 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 演示角色：DEPT（本部门）；ALL + OWNER（负责人仅本人）
-- 说明：id 6/7 可能被历史软删除角色占用（主键仍存在），此处使用 8/9。
INSERT INTO sys_role (id, name, code, sort, data_scope, own_filter, remark, is_enabled, created_at, updated_at, deleted) VALUES
(8, '部门范围示例', 'DEMO_DEPT', 8, 'DEPT', 'NONE', '仅本部门数据', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(9, '负责人过滤示例', 'DEMO_OWNER', 9, 'ALL', 'OWNER', '全部部门，但只看自己负责的数据', 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (8, 8), (9, 9);

-- 示例菜单与权限码
INSERT INTO sys_menu (id, parent_id, name, menu_type, path, component, permission_code, icon, sort, is_visible, is_enabled, created_at, updated_at, deleted) VALUES
(40, 0, '示例业务', 'C', '/demo/tasks', 'demo/task/index', 'demo:task:list', 'List', 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(41, 40, '新增任务', 'F', NULL, NULL, 'demo:task:create', NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(42, 40, '修改任务', 'F', NULL, NULL, 'demo:task:update', NULL, 2, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(43, 40, '删除任务', 'F', NULL, NULL, 'demo:task:delete', NULL, 3, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);

-- 菜单授权：管理员与部门管理员可读写，其余演示角色只读
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 40), (1, 41), (1, 42), (1, 43),
(2, 40), (2, 41), (2, 42), (2, 43),
(3, 40), (4, 40), (5, 40), (8, 40), (9, 40);

-- 示例数据：覆盖总部/研发部/研发一组/市场部/财务部，创建人与负责人交叉
INSERT INTO biz_demo_task (id, title, status, dept_id, owner_id, create_by, created_at, updated_at, deleted) VALUES
(1, '研发-需求评审', 'TODO', 2, 3, 3, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(2, '研发-接口联调', 'DOING', 2, 4, 4, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(3, '研发一组-单元测试', 'TODO', 3, 3, 3, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(4, '研发一组-代码评审', 'DONE', 3, 4, 4, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(5, '市场-季度活动', 'DOING', 4, 5, 5, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(6, '市场-渠道对接', 'TODO', 4, 9, 5, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(7, '财务-预算编制', 'DOING', 5, 6, 6, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(8, '总部-年度规划', 'TODO', 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(9, '总部-制度修订', 'DONE', 1, 2, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(10, '研发-技术分享', 'TODO', 2, 2, 2, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(11, '市场-客户回访', 'DOING', 4, 9, 5, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0),
(12, '财务-审计配合', 'DONE', 5, 6, 6, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);
