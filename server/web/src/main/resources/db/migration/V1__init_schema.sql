-- 后台管理系统通用底座 - 初始化表结构（Flyway V1）
-- 数据库：MySQL 8.0；表名/字段名/索引名统一小写（lower_case_table_names=1）
-- 字符集：utf8mb4；应用侧时间以 UTC 传输，数据库会话时区为 +08:00

CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    ancestors   VARCHAR(255) NOT NULL DEFAULT '0',
    name        VARCHAR(64)  NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    leader      VARCHAR(64)  NULL,
    phone       VARCHAR(32)  NULL,
    is_enabled  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dept_parent (parent_id),
    KEY idx_dept_ancestors (ancestors)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门';

CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(32)  NOT NULL,
    nickname    VARCHAR(64)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    dept_id     BIGINT       NULL,
    email       VARCHAR(128) NULL,
    phone       VARCHAR(32)  NULL,
    is_enabled  TINYINT(1)   NOT NULL DEFAULT 1,
    create_by   BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_dept (dept_id),
    KEY idx_user_create_by (create_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户（单部门；如需多部门，扩展 user_dept 关系表，见实现说明）';

CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    code        VARCHAR(64) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    data_scope  VARCHAR(32) NOT NULL DEFAULT 'SELF',
    own_filter  VARCHAR(32) NOT NULL DEFAULT 'NONE',
    remark      VARCHAR(255) NULL,
    is_enabled  TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色：组织范围 + 归属过滤';

CREATE TABLE sys_menu (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    name            VARCHAR(64)  NOT NULL,
    menu_type       CHAR(1)      NOT NULL COMMENT 'M 目录 / C 菜单 / F 按钮',
    path            VARCHAR(128) NULL,
    component       VARCHAR(128) NULL,
    permission_code VARCHAR(128) NULL,
    icon            VARCHAR(64)  NULL,
    sort            INT          NOT NULL DEFAULT 0,
    is_visible      TINYINT(1)   NOT NULL DEFAULT 1,
    is_enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_menu_parent (parent_id),
    KEY idx_menu_permission (permission_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单与权限';

CREATE TABLE sys_post (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    dept_id     BIGINT      NOT NULL,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    is_enabled  TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_code (code),
    KEY idx_post_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位';

CREATE TABLE sys_dict_type (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    type        VARCHAR(64) NOT NULL,
    remark      VARCHAR(255) NULL,
    is_enabled  TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型';

CREATE TABLE sys_dict_item (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    dict_type_id BIGINT      NOT NULL,
    label        VARCHAR(64) NOT NULL,
    value        VARCHAR(64) NOT NULL,
    sort         INT         NOT NULL DEFAULT 0,
    is_enabled   TINYINT(1)  NOT NULL DEFAULT 1,
    remark       VARCHAR(255) NULL,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dict_item_type (dict_type_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典项';

CREATE TABLE sys_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    config_key    VARCHAR(128) NOT NULL,
    config_value  TEXT         NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    remark        VARCHAR(255) NULL,
    is_sensitive  TINYINT(1)   NOT NULL DEFAULT 0,
    is_enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数';

CREATE TABLE sys_oper_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id       VARCHAR(64)  NULL,
    user_id        BIGINT       NULL,
    username       VARCHAR(32)  NULL,
    dept_id        BIGINT       NULL,
    module         VARCHAR(64)  NULL,
    action         VARCHAR(64)  NULL,
    method         VARCHAR(255) NULL,
    request_uri    VARCHAR(255) NULL,
    request_method VARCHAR(16)  NULL,
    ip             VARCHAR(64)  NULL,
    params         TEXT         NULL,
    result_code    INT          NOT NULL DEFAULT 0,
    result_message VARCHAR(255) NULL,
    elapsed_ms     BIGINT       NULL,
    create_by      BIGINT       NULL,
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_oper_log_dept (dept_id),
    KEY idx_oper_log_create_by (create_by),
    KEY idx_oper_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志（受数据权限控制）';

CREATE TABLE sys_login_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL,
    username   VARCHAR(32)  NULL,
    ip         VARCHAR(64)  NULL,
    user_agent VARCHAR(255) NULL,
    is_success TINYINT(1)   NOT NULL DEFAULT 0,
    message    VARCHAR(255) NULL,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_login_log_user (user_id),
    KEY idx_login_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志';

CREATE TABLE sys_file (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(128) NOT NULL,
    storage_path  VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128) NULL,
    size          BIGINT       NOT NULL DEFAULT 0,
    uploader_id   BIGINT       NULL,
    dept_id       BIGINT       NULL,
    create_by     BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_file_dept (dept_id),
    KEY idx_file_create_by (create_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件记录（受数据权限控制）';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关系';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    KEY idx_role_menu_menu (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色菜单关系';

CREATE TABLE IF NOT EXISTS role_scope_target (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    role_id     BIGINT      NOT NULL,
    target_type CHAR(4)     NOT NULL COMMENT 'DEPT / USER',
    target_id   BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_scope (role_id, target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色自定义数据范围目标';
