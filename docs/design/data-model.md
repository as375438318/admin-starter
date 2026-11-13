# 数据模型（as-built v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（竣工现状，2026-11-08） |
| 状态 | 结构与 `admin_starter` 库一致；语义人工核对 |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is**：系统现在“有哪些表、哪些字段”，不是规范（to-be） |
| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |

<!-- AUTO:HEADER:START -->
> 结构由脚本生成于 2026-11-09（`scripts/gen-data-model.ps1`，读 `information_schema`）；语义人工维护。
> **改代码或迁移（新增/修改表、列、索引）后必须重跑脚本**；代码、库、权限码变更时本文件必须在第 8 步同步。
<!-- AUTO:HEADER:END -->

## 1. 表总览

<!-- AUTO:TABLE-LIST:START -->

| # | 表 | 表注释 | 字段数 | 索引数 | 特殊列 |
|---|---|---|---|---|---|
| 1 | `biz_demo_task` | 示例业务任务（数据权限参考实现） | 9 | 4 | 数据权限：`dept_id`（组织范围）、`create_by`（SELF/CUSTOM_USER）、`owner_id`（OWNER）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at` |
| 2 | `demo_customer` | 客户档案（生成器样例） | 6 | 0 | 软删除：`deleted`；审计：`created_at`、`updated_at`；无数据权限（生成器不猜业务，见 `docs/design/module-generator.md` §7） |
| 2 | `role_scope_target` | 角色自定义数据范围目标 | 4 | 2 | — |
| 3 | `sys_config` | 系统参数 | 10 | 2 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 4 | `sys_dept` | 部门 | 13 | 3 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 5 | `sys_dict_item` | 字典项 | 11 | 3 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 6 | `sys_dict_type` | 字典类型 | 8 | 2 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 7 | `sys_file` | 文件记录（受数据权限控制） | 14 | 5 | 数据权限：`dept_id`、`create_by`（**受控表**）；审计：`create_by`、`created_at` |
| 8 | `sys_file_chunk_upload` | 分片上传会话（断点续传） | 14 | 4 | 数据权限：`dept_id`；审计：`created_at`、`updated_at` |
| 9 | `sys_job` | 定时任务定义 | 22 | 4 | 数据权限：`create_by`；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at` |
| 10 | `sys_job_log` | 定时任务执行日志 | 14 | 4 | 审计：`created_at` |
| 11 | `sys_login_log` | 登录日志 | 8 | 2 | 审计：`created_at` |
| 12 | `sys_menu` | 菜单与权限 | 15 | 3 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 13 | `sys_notice` | 通知（站内信） | 13 | 4 | 数据权限：`dept_id`、`create_by`；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at` |
| 14 | `sys_notice_recipient` | 通知应读人快照与已读记录 | 7 | 4 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 15 | `sys_notice_scope` | 通知接收范围 | 7 | 3 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 16 | `sys_oper_log` | 操作日志（受数据权限控制） | 17 | 4 | 数据权限：`dept_id`、`create_by`（**受控表**，实际按 `create_by` 过滤）；审计：`create_by`、`created_at` |
| 17 | `sys_post` | 岗位 | 9 | 3 | 业务归属列：`dept_id`（岗位挂靠部门，**不是**数据权限受控列）；软删除：`deleted`；审计：`created_at`、`updated_at` |
| 18 | `sys_role` | 角色：组织范围 + 归属过滤 | 11 | 2 | 软删除：`deleted`；审计：`created_at`、`updated_at` |
| 19 | `sys_role_menu` | 角色菜单关系 | 2 | 2 | — |
| 20 | `sys_user` | 用户（单部门；如需多部门，扩展 user_dept 关系表，见实现说明） | 16 | 3 | 数据权限列：`dept_id`、`create_by`（本表**豁免**数据权限拦截；`deptId` 仅作普通筛选参数）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at` |
| 21 | `sys_user_role` | 用户角色关系 | 2 | 2 | — |
| 22 | `sys_user_session` | 登录会话记录 | 15 | 5 | 审计：`created_at`、`updated_at` |

<!-- AUTO:TABLE-LIST:END -->

## 2. 表结构（结构自动生成 · 语义人工维护）

> 库内**没有任何物理外键**；下文“表间关系”全部为逻辑关联。结构块由脚本维护，块外文字人工维护。

**按域索引**

| 域 | 表 |
|---|---|
| 认证与审计 | `sys_login_log`、`sys_oper_log` |
| 组织与账号 | `sys_dept`、`sys_user`、`sys_post`、`sys_user_role` |
| 权限与菜单 | `sys_role`、`sys_role_menu`、`role_scope_target`、`sys_menu` |
| 字典与参数 | `sys_dict_type`、`sys_dict_item`、`sys_config` |
| 文件 | `sys_file` |
| 通知 | `sys_notice`、`sys_notice_scope`、`sys_notice_recipient` |
| 示例业务 | `biz_demo_task` |

### `sys_login_log`（登录日志）

**用途**：登录审计；成功与失败登录均记录，供个人中心与运维查看。

**特殊列**：审计：`created_at`

**表间关系**：`user_id` → `sys_user.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_login_log:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `user_id` | bigint | 是 | — | 登录用户 ID（失败登录可能为空） |
| `username` | varchar(32) | 是 | — | 登录账号快照 |
| `ip` | varchar(64) | 是 | — | 客户端 IP |
| `user_agent` | varchar(255) | 是 | — | User-Agent |
| `is_success` | tinyint(1) | 否 | 0 | 是否成功：1 成功 / 0 失败 |
| `message` | varchar(255) | 是 | — | 结果消息（失败原因） |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |

<!-- AUTO:COLS:sys_login_log:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_login_log:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_login_log_user_created` | 普通 | `user_id`、`created_at` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:sys_login_log:END -->

### `sys_oper_log`（操作日志（受数据权限控制））

**用途**：操作审计；由切面写库，**受数据权限控制**（按 `create_by` 隔离）。

**特殊列**：数据权限：`dept_id`、`create_by`（**受控表**，实际按 `create_by` 过滤）；审计：`create_by`、`created_at`

**表间关系**：`user_id`/`create_by` → `sys_user.id`；`dept_id` → `sys_dept.id`（逻辑快照）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_oper_log:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `trace_id` | varchar(64) | 是 | — | 链路追踪 ID |
| `user_id` | bigint | 是 | — | 操作人用户 ID |
| `username` | varchar(32) | 是 | — | 操作人账号快照 |
| `dept_id` | bigint | 是 | — | 操作人部门快照（仅展示，不参与数据权限过滤） |
| `module` | varchar(64) | 是 | — | 模块名 |
| `action` | varchar(64) | 是 | — | 动作名（如 新增 / 修改 / 删除 / 上传） |
| `method` | varchar(255) | 是 | — | Controller 方法签名 |
| `request_uri` | varchar(255) | 是 | — | 请求路径 |
| `request_method` | varchar(16) | 是 | — | HTTP 方法 |
| `ip` | varchar(64) | 是 | — | 客户端 IP |
| `params` | text | 是 | — | 请求参数快照（写入前仅对 `password` 脱敏） |
| `result_code` | int | 否 | 0 | 业务结果码（0 = 成功） |
| `result_message` | varchar(255) | 是 | — | 结果消息 |
| `elapsed_ms` | bigint | 是 | — | 耗时（毫秒） |
| `create_by` | bigint | 是 | — | 操作人用户 ID（数据权限归属列） |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |

<!-- AUTO:COLS:sys_oper_log:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_oper_log:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_oper_log_create_by` | 普通 | `create_by` | 非唯一，用于加速查询 |
| `idx_oper_log_created` | 普通 | `created_at` | 非唯一，用于加速查询 |
| `idx_oper_log_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:sys_oper_log:END -->

### `sys_dept`（部门）

**用途**：组织部门树（单父节点）；`ancestors` 存祖先路径，供“含下级”查询使用。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：自关联：`parent_id` → `sys_dept.id`（逻辑）；`ancestors` 为冗余路径。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_dept:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `parent_id` | bigint | 否 | 0 | 父部门 ID；0 = 根节点 |
| `ancestors` | varchar(255) | 否 | 0 | 祖先路径（逗号分隔）；子部门查询用 `FIND_IN_SET` |
| `name` | varchar(64) | 否 | — | 部门名称 |
| `sort` | int | 否 | 0 | 同级排序（升序） |
| `leader` | varchar(64) | 是 | — | 负责人姓名（自由文本，不指向 `sys_user`） |
| `phone` | varchar(32) | 是 | — | 联系电话（自由文本） |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |
| `uk_parent_id` | bigint | 是 | — | 唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL |
| `uk_name` | varchar(64) | 是 | — | 唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL |

<!-- AUTO:COLS:sys_dept:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_dept:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_dept_parent` | 普通 | `parent_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_dept_parent_name` | 唯一 | `uk_parent_id`、`uk_name` | 唯一约束落在 `uk_parent_id`、`uk_name`（2 列） |

<!-- AUTO:IDX:sys_dept:END -->

### `sys_user`（用户（单部门；如需多部门，扩展 user_dept 关系表，见实现说明））

**用途**：账号主表；单部门归属，密码存 BCrypt 哈希。用户表**豁免**数据权限拦截。

**特殊列**：数据权限列：`dept_id`、`create_by`（物理存在，但本表**豁免**数据权限拦截；`deptId` 仅作普通筛选参数）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`

**表间关系**：`dept_id` → `sys_dept.id`；`create_by` → `sys_user.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_user:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `username` | varchar(32) | 否 | — | 登录账号（全局唯一） |
| `nickname` | varchar(64) | 否 | — | 显示名 |
| `password` | varchar(100) | 否 | — | 密码哈希（BCrypt，非明文） |
| `dept_id` | bigint | 是 | — | 所属部门 ID（单部门）；本表豁免数据权限，该列仅作列表筛选参数 |
| `email` | varchar(128) | 是 | — | 邮箱（可空） |
| `phone` | varchar(32) | 是 | — | 手机号（可空） |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `token_version` | bigint | 否 | 0 | 令牌版本号：本人改密/管理员重置/停用时自增，旧访问与刷新令牌全部失效 |
| `token_revoke_reason` | varchar(32) | 是 | — | 旧令牌失效原因：PASSWORD_RESET / PASSWORD_CHANGED；供 401 分码提示 |
| `create_by` | bigint | 是 | — | 创建人用户 ID；本表豁免数据权限，该列只写不读 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:sys_user:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_user:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_user_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_user_username` | 唯一 | `username` | 唯一约束落在 `username`（1 列） |

<!-- AUTO:IDX:sys_user:END -->

### `sys_post`（岗位）

**用途**：岗位（挂在部门下的组织配置，不是数据权限受控表）。

**特殊列**：业务归属列：`dept_id`（岗位挂靠部门，**不是**数据权限受控列）；软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：`dept_id` → `sys_dept.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_post:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `dept_id` | bigint | 否 | — | 所属部门 ID（岗位挂靠，非数据权限列） |
| `code` | varchar(64) | 否 | — | 岗位编码（全局唯一） |
| `name` | varchar(64) | 否 | — | 岗位名称 |
| `sort` | int | 否 | 0 | 排序值 |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:sys_post:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_post:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_post_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_post_code` | 唯一 | `code` | 唯一约束落在 `code`（1 列） |

<!-- AUTO:IDX:sys_post:END -->

### `sys_user_role`（用户角色关系）

**用途**：用户-角色关系；仅用于鉴权装配。

**特殊列**：—

**表间关系**：`user_id` → `sys_user.id`；`role_id` → `sys_role.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_user_role:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `user_id` | bigint | 否 | — | 用户 ID |
| `role_id` | bigint | 否 | — | 角色 ID |

<!-- AUTO:COLS:sys_user_role:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_user_role:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_user_role_role` | 普通 | `role_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `user_id`、`role_id` | 主键约束落在 `user_id`、`role_id` |

<!-- AUTO:IDX:sys_user_role:END -->

### `sys_role`（角色：组织范围 + 归属过滤）

**用途**：角色定义：既是功能权限载体，也承载组织范围（`data_scope`）与归属过滤（`own_filter`）。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：本表不引用其他表；`role_scope_target`、`sys_role_menu`、`sys_user_role` 通过外键列逻辑引用本表。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_role:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `name` | varchar(64) | 否 | — | 角色名称 |
| `code` | varchar(64) | 否 | — | 角色编码（全局唯一） |
| `sort` | int | 否 | 0 | 排序值 |
| `data_scope` | varchar(32) | 否 | SELF | 组织范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM_DEPT / CUSTOM_USER |
| `own_filter` | varchar(32) | 否 | NONE | 归属过滤：NONE / CREATOR / OWNER |
| `remark` | varchar(255) | 是 | — | 备注 |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:sys_role:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_role:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_role_code` | 唯一 | `code` | 唯一约束落在 `code`（1 列） |

<!-- AUTO:IDX:sys_role:END -->

### `sys_role_menu`（角色菜单关系）

**用途**：角色-菜单授权关系；功能权限的实际落点。

**特殊列**：—

**表间关系**：`role_id` → `sys_role.id`；`menu_id` → `sys_menu.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_role_menu:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `role_id` | bigint | 否 | — | 角色 ID |
| `menu_id` | bigint | 否 | — | 菜单 ID |

<!-- AUTO:COLS:sys_role_menu:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_role_menu:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_role_menu_menu` | 普通 | `menu_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `role_id`、`menu_id` | 主键约束落在 `role_id`、`menu_id` |

<!-- AUTO:IDX:sys_role_menu:END -->

### `role_scope_target`（角色自定义数据范围目标）

**用途**：角色自定义数据范围的目标集合；`data_scope=CUSTOM_DEPT/CUSTOM_USER` 时生效。

**特殊列**：—

**表间关系**：`role_id` → `sys_role.id`；`target_id` 按 `target_type` 指向 `sys_dept.id` 或 `sys_user.id`（多态逻辑关联）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:role_scope_target:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `role_id` | bigint | 否 | — | 角色 ID |
| `target_type` | char(4) | 否 | — | 目标类型：DEPT / USER |
| `target_id` | bigint | 否 | — | 目标 ID（按类型指向部门或用户） |

<!-- AUTO:COLS:role_scope_target:END -->

**索引与唯一键**

<!-- AUTO:IDX:role_scope_target:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_role_scope` | 唯一 | `role_id`、`target_type`、`target_id` | 唯一约束落在 `role_id`、`target_type`、`target_id`（3 列） |

<!-- AUTO:IDX:role_scope_target:END -->

### `sys_menu`（菜单与权限）

**用途**：菜单、路由与权限码定义；前端动态路由与按钮权限、后端接口鉴权的权限源。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：自关联：`parent_id` → `sys_menu.id`（逻辑）；`sys_role_menu.menu_id` 反向引用本表。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_menu:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `parent_id` | bigint | 否 | 0 | 父菜单 ID；0 = 根 |
| `name` | varchar(64) | 否 | — | 菜单 / 按钮名称 |
| `menu_type` | char(1) | 否 | — | 类型：M 目录 / C 菜单 / F 按钮 |
| `path` | varchar(255) | 是 | — | 前端路由路径（M/C 使用；V4 放宽到 255） |
| `component` | varchar(255) | 是 | — | 前端组件路径（C 使用；V4 放宽到 255） |
| `permission_code` | varchar(128) | 是 | — | 权限码（F 按钮与接口鉴权使用）；未删除且非空时由 `uk_menu_permission_code` 保证唯一（V7） |
| `icon` | varchar(64) | 是 | — | 图标名（前端图标库键） |
| `sort` | int | 否 | 0 | 同级排序 |
| `is_visible` | tinyint(1) | 否 | 1 | 是否在侧栏显示：1 显示 / 0 隐藏（修改后需刷新或重新登录） |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |
| `uk_permission_code` | varchar(128) | 是 | — | 唯一约束辅助列：仅未删除且非空权限码参与唯一；空码（目录/无码按钮）与已删除行置 NULL |

<!-- AUTO:COLS:sys_menu:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_menu:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_menu_parent` | 普通 | `parent_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_menu_permission_code` | 唯一 | `uk_permission_code` | 唯一约束落在 `uk_permission_code`（1 列） |

<!-- AUTO:IDX:sys_menu:END -->

### `sys_dict_type`（字典类型）

**用途**：字典类型；以 `type` 作为业务键被前后端引用。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：`sys_dict_item.dict_type_id` 反向引用本表（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_dict_type:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `name` | varchar(64) | 否 | — | 字典名称 |
| `type` | varchar(64) | 否 | — | 业务键（全局唯一，前后端按此引用） |
| `remark` | varchar(255) | 是 | — | 备注 |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:sys_dict_type:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_dict_type:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_dict_type` | 唯一 | `type` | 唯一约束落在 `type`（1 列） |

<!-- AUTO:IDX:sys_dict_type:END -->

### `sys_dict_item`（字典项）

**用途**：字典项；供前端下拉、状态文案等读取。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：`dict_type_id` → `sys_dict_type.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_dict_item:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `dict_type_id` | bigint | 否 | — | 所属字典类型 ID |
| `label` | varchar(64) | 否 | — | 展示文案 |
| `value` | varchar(64) | 否 | — | 存储值；未删除时同一字典下由 `uk_dict_item_type_value` 保证唯一（V7） |
| `sort` | int | 否 | 0 | 排序值 |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用 / 0 停用 |
| `remark` | varchar(255) | 是 | — | 备注 |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |
| `uk_dict_item_value` | varchar(64) | 是 | — | 唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL 释放占位 |

<!-- AUTO:COLS:sys_dict_item:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_dict_item:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_dict_item_type` | 普通 | `dict_type_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_dict_item_type_value` | 唯一 | `dict_type_id`、`uk_dict_item_value` | 唯一约束落在 `dict_type_id`、`uk_dict_item_value`（2 列） |

<!-- AUTO:IDX:sys_dict_item:END -->

### `sys_config`（系统参数）

**用途**：系统参数键值表；是否真正生效以 `docs/design/config-registry.yaml` 的 `consumedBy` 为准。

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：无表间关联。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_config:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `config_key` | varchar(128) | 否 | — | 配置键（全局唯一；代码按此读取） |
| `config_value` | text | 否 | — | 配置值（文本；敏感项在列表掩码） |
| `name` | varchar(64) | 否 | — | 参数名称 |
| `remark` | varchar(255) | 是 | — | 说明文本；**不等于生效承诺**（以配置台账 `consumedBy` 为准） |
| `is_sensitive` | tinyint(1) | 否 | 0 | 敏感标记：1 列表值掩码，公开接口不返回 |
| `is_enabled` | tinyint(1) | 否 | 1 | 启用标记：1 启用（公开配置读取时跳过停用项） |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:sys_config:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_config:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_config_key` | 唯一 | `config_key` | 唯一约束落在 `config_key`（1 列） |

<!-- AUTO:IDX:sys_config:END -->

### `sys_file`（文件记录（受数据权限控制））

**用途**：上传文件元数据；文件内容落本地磁盘，表中只存指针。**受数据权限控制**。

**特殊列**：数据权限：`dept_id`、`create_by`（**受控表**）；审计：`create_by`、`created_at`

**表间关系**：`uploader_id`/`create_by` → `sys_user.id`；`dept_id` → `sys_dept.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_file:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `original_name` | varchar(255) | 否 | — | 原始文件名（含扩展名） |
| `stored_name` | varchar(128) | 否 | — | 落盘文件名（UUID）；只写不读，与 `storage_path` 同值 |
| `storage_path` | varchar(255) | 否 | — | 存储相对路径（当前实现与 `stored_name` 同值；下载/删除按此解析） |
| `content_type` | varchar(128) | 是 | — | MIME 类型 |
| `size` | bigint | 否 | 0 | 文件大小（字节） |
| `sha256` | char(64) | 是 | — | 内容 SHA-256（秒传/物理去重） |
| `uploader_id` | bigint | 是 | — | 上传人用户 ID；与 `create_by` 同值重复 |
| `dept_id` | bigint | 是 | — | 上传人部门快照（数据权限组织范围列） |
| `create_by` | bigint | 是 | — | 上传人用户 ID（数据权限归属列） |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 回收站 |
| `deleted_at` | datetime | 是 | — | 进入回收站时间（UTC） |
| `deleted_by` | bigint | 是 | — | 执行删除的用户 id |

<!-- AUTO:COLS:sys_file:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_file:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_file_create_by` | 普通 | `create_by` | 非唯一，用于加速查询 |
| `idx_file_deleted` | 普通 | `deleted`、`created_at` | 非唯一，用于加速查询 |
| `idx_file_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `idx_file_sha256` | 普通 | `sha256` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:sys_file:END -->

### `biz_demo_task`（示例业务任务（数据权限参考实现））

**用途**：数据权限参考实现：演示 6 档组织范围与 3 档归属过滤，无真实业务含义。

**特殊列**：数据权限：`dept_id`（组织范围）、`create_by`（SELF/CUSTOM_USER）、`owner_id`（OWNER）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`

**表间关系**：`dept_id` → `sys_dept.id`；`owner_id`/`create_by` → `sys_user.id`（逻辑）。无物理外键。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:biz_demo_task:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `title` | varchar(128) | 否 | — | 任务标题 |
| `status` | varchar(16) | 否 | TODO | 状态：TODO / DOING / DONE |
| `dept_id` | bigint | 否 | — | 归属部门（组织范围列） |
| `owner_id` | bigint | 是 | — | 负责人（OWNER 过滤列，可空） |
| `create_by` | bigint | 是 | — | 创建人（SELF / CUSTOM_USER 归属列） |
| `created_at` | datetime | 否 | — | 创建时间（UTC 存储，接口按 ISO-8601 输出） |
| `updated_at` | datetime | 否 | — | 最后更新时间（UTC 存储） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 正常 / 1 已删除 |

<!-- AUTO:COLS:biz_demo_task:END -->

**索引与唯一键**

<!-- AUTO:IDX:biz_demo_task:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_demo_task_create_by` | 普通 | `create_by` | 非唯一，用于加速查询 |
| `idx_demo_task_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `idx_demo_task_owner` | 普通 | `owner_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:biz_demo_task:END -->

## 3. 审查发现（建了没用 / 索引冗余 / 缺约束）

> 本节登记“能从库里看到、但代码或业务上对不上”的项。
> 2026-11-08 已由 `V7__permission_and_index_governance.sql` 落地：§3.2 的索引治理与 §3.3 的前三条唯一约束已实施（下表以“结论”列标注实际状态）；§3.1 的列**保留不删**，删列不可逆，等单独确认。

### 3.1 建了但代码没用上的列

| 表 | 列 | 现象 | 依据 | 结论 |
|---|---|---|---|---|
| `sys_file` | `stored_name` | 上传时写入，但下载/删除只用 `storage_path`；两列当前同值，属于只写不读 | `FileAppService.upload`（`setStoredName` 与 `setStoragePath` 同值）、`resolvePath` 只读 `storage_path` | **保留不删**：删列不可逆，待单独确认 |
| `sys_file` | `uploader_id` | 与 `create_by` 同值重复写入；没有按 `uploader_id` 过滤的查询 | `FileAppService.upload`、`FileAppService.toResponse`（`response.setUploaderId(record.getUploaderId())`）、无 `WHERE uploader_id` | **保留不删**：接口响应仍回显该字段，删列会改契约 |
| `sys_user` | `create_by` | 只写不读；用户表豁免数据权限，列表/详情均不按 `create_by` 过滤 | `DataPermissionTables.EXEMPT`；`UserRepositoryImpl.page` | **保留不删**：删列不可逆，待单独确认 |
| `sys_job` | `create_by` | 不参与数据权限（`sys_job` 豁免），只用于失败通知默认接收人 | `DataPermissionTables.EXEMPT`；`JobFailureNotifier`（`notifyTargets` 为空回退 `create_by`） | **保留**：通知路由需要 |

### 3.2 索引治理结果（V7 已执行）

| 表 | 索引 | 判断 | 依据 | 结论 |
|---|---|---|---|---|
| `sys_menu` | `idx_menu_permission` | 权限码只出现在 SELECT，没有 `WHERE permission_code = ?` 的等值查询 | `MenuMapper`（`select distinct m.permission_code` 无该列过滤） | **已删除** |
| `sys_user` | `idx_user_create_by` | 用户表豁免数据权限且列表无 `create_by` 过滤，索引没有对应查询 | `DataPermissionTables.EXEMPT`；`UserRepositoryImpl.page` | **已删除** |
| `sys_dept` | `idx_dept_ancestors` | 子部门查询用 `FIND_IN_SET(deptId, ancestors)`，B-tree 索引无法命中，实际是全表扫描 | `DeptMapper`（`find_in_set`） | **已删除** |
| `sys_oper_log` | `idx_oper_log_dept` | 数据范围为 DEPT / DEPT_AND_CHILD / CUSTOM_DEPT 时，`DataScopeSqlBuilder` 生成 `dept_id IN (...)`，操作日志查询经 `DataPermissionContext.controlled` 使该条件生效 | `DataScopeSqlBuilder`（`dept_id IN`）、`OperLogAppService.page`、`DataPermissionTables`（`sys_oper_log` 受控） | **保留**：有真实查询命中，删除会退化为全表扫描 |
| `sys_login_log` | `idx_login_log_user`、`idx_login_log_created` | 唯一查询是 `LoginLogRepositoryImpl.pageByUser`：`user_id` 等值 + `created_at` 排序；无按 `created_at` 单独查询、无按时间的清理任务 | `LoginLogRepositoryImpl`、`LoginLogAppService` | **已合并**为 `(user_id, created_at)`，两条旧索引删除 |

### 3.3 唯一约束（V7 已落地）

统一方案：`STORED` 生成列 + 唯一索引；`deleted=1` 时生成列为 `NULL`（MySQL 唯一索引允许多个 `NULL`），软删除行不再永久占位。

| 表 | 约束 | 生成列表达式 | NULL 语义 | 状态 |
|---|---|---|---|---|
| `sys_dict_item` | `uk_dict_item_type_value (dict_type_id, uk_dict_item_value)` | `IF(deleted = 0, value, NULL)` | 已删除行的 `value` 侧为 `NULL`，同字典下不再冲突 | **已建立** |
| `sys_menu` | `uk_menu_permission_code (uk_permission_code)` | `IF(deleted = 0 AND permission_code IS NOT NULL AND permission_code <> '', permission_code, NULL)` | 目录/无码按钮（空或 `NULL`）与已删除菜单均不参与唯一 | **已建立** |
| `sys_dept` | `uk_dept_parent_name (uk_parent_id, uk_name)` | `IF(deleted = 0, parent_id, NULL)` / `IF(deleted = 0, name, NULL)` | 已删除部门整行不参与唯一，同名部门可重建 | **已建立** |

> 取舍：生成列带来的存储与写入开销极小，换来一致性由库保证，且不必为软删除写应用层补丁。代价是新写入路径必须保证 `deleted` 语义正确（现有仓储均以 `deleted=0` 过滤，符合该前提）。

**仍未加约束的项**（不在本轮范围）：

| 表 | 缺失约束 | 风险 | 建议（未改） |
|---|---|---|---|
| `sys_role` | `data_scope` / `own_filter` 无 CHECK 或外键枚举 | 脏值只能靠代码枚举兜底，非法值会落到默认分支 | 加 CHECK 或枚举表 |
| `sys_menu` | `menu_type` 为 `CHAR(1)`，无 CHECK | 可写入 M/C/F 之外的值，前端路由与鉴权分支漏判 | 加 CHECK |
| `biz_demo_task` | `status` 无 CHECK | 可写入 TODO/DOING/DONE 之外的值 | 示例表，可不改 |
| `sys_file` | `size` 无 `>= 0` 约束 | 负数只可能来自代码缺陷 | 低优先级 |

### 3.4 其他结构事实

- 全部表间关系均为**逻辑关联**，库内无任何物理外键（`information_schema.referential_constraints` 为空）。
- 审计时间列命名是 `created_at` / `updated_at`，**不是** `create_time` / `update_time`；且全库**没有** `update_by` 列。
- `sys_oper_log`、`sys_login_log`、`sys_user_role`、`sys_role_menu`、`role_scope_target` 没有 `deleted` 列（审计与关系数据不做软删除）；`sys_file_chunk_upload` 也没有 `deleted` 列（会话只按 `status` 收口）；`sys_file` 自 V13 起有 `deleted/deleted_at/deleted_by`（回收站）。
- `sys_config.remark` 是说明文本，**不是生效承诺**；键是否被消费以 `docs/design/config-registry.yaml` 的 `consumedBy` 为准。

### `sys_notice`（通知（站内信））

**用途**：—（语义待补）

**特殊列**：数据权限：`dept_id`、`create_by`；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_notice:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | —（语义待补） |
| `title` | varchar(200) | 否 | — | —（语义待补） |
| `content` | text | 否 | — | —（语义待补） |
| `level` | varchar(16) | 否 | NORMAL | NORMAL 普通 / IMPORTANT 重要 / URGENT 紧急 |
| `type` | varchar(32) | 否 | SYSTEM | SYSTEM / BUSINESS / SECURITY / TASK |
| `is_revocable` | tinyint(1) | 否 | 1 | 是否允许撤回 |
| `revoked_at` | datetime | 是 | — | 撤回时间；非空即已撤回 |
| `revoked_by` | bigint | 是 | — | 撤回人 |
| `dept_id` | bigint | 是 | — | 发布人部门（数据权限组织范围） |
| `create_by` | bigint | 是 | — | 发布人；系统调用可为空 |
| `created_at` | datetime | 否 | — | —（语义待补） |
| `updated_at` | datetime | 否 | — | —（语义待补） |
| `deleted` | tinyint | 否 | 0 | —（语义待补） |

<!-- AUTO:COLS:sys_notice:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_notice:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_notice_create_by` | 普通 | `create_by` | 非唯一，用于加速查询 |
| `idx_notice_created` | 普通 | `created_at` | 非唯一，用于加速查询 |
| `idx_notice_dept` | 普通 | `dept_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:sys_notice:END -->

### `sys_notice_recipient`（通知应读人快照与已读记录）

**用途**：—（语义待补）

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_notice_recipient:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | —（语义待补） |
| `notice_id` | bigint | 否 | — | —（语义待补） |
| `user_id` | bigint | 否 | — | —（语义待补） |
| `read_at` | datetime | 是 | — | 阅读时间；NULL=未读 |
| `created_at` | datetime | 否 | — | —（语义待补） |
| `updated_at` | datetime | 否 | — | —（语义待补） |
| `deleted` | tinyint | 否 | 0 | —（语义待补） |

<!-- AUTO:COLS:sys_notice_recipient:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_notice_recipient:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_recipient_notice_read` | 普通 | `notice_id`、`read_at` | 非唯一，用于加速查询 |
| `idx_recipient_user_read` | 普通 | `user_id`、`read_at` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_notice_recipient` | 唯一 | `notice_id`、`user_id` | 唯一约束落在 `notice_id`、`user_id`（2 列） |

<!-- AUTO:IDX:sys_notice_recipient:END -->

### `sys_notice_scope`（通知接收范围）

**用途**：—（语义待补）

**特殊列**：软删除：`deleted`；审计：`created_at`、`updated_at`

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_notice_scope:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | —（语义待补） |
| `notice_id` | bigint | 否 | — | —（语义待补） |
| `scope_type` | varchar(16) | 否 | — | ALL / ROLE / DEPT / USER |
| `target_id` | bigint | 是 | — | ROLE=角色id / DEPT=部门id / USER=用户id / ALL=NULL |
| `created_at` | datetime | 否 | — | —（语义待补） |
| `updated_at` | datetime | 否 | — | —（语义待补） |
| `deleted` | tinyint | 否 | 0 | —（语义待补） |

<!-- AUTO:COLS:sys_notice_scope:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_notice_scope:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_notice_scope_notice` | 普通 | `notice_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_notice_scope` | 唯一 | `notice_id`、`scope_type`、`target_id` | 唯一约束落在 `notice_id`、`scope_type`、`target_id`（3 列） |

<!-- AUTO:IDX:sys_notice_scope:END -->

### `sys_job`（定时任务定义）

**用途**：存任务标识（`job_key`）与调度配置（cron、并发策略、超时、重试、失败通知）；**不存**脚本、Shell 命令、SQL 字符串或类名，任务实现来自代码中注册的 `ScheduledJob` Bean。

**特殊列**：**豁免**数据权限（仅 `SUPER_ADMIN` 持有 `system:job:*`，调度需全局一致定义；虽然有 `create_by` 列，但不参与数据权限过滤）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`；`uk_job_key` 为软删除唯一辅助列。

**表间关系**：`create_by` → `sys_user.id`（逻辑）；`sys_job_log.job_id` 反向逻辑引用本表（不建物理外键，删除任务不删日志）。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_job:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `name` | varchar(64) | 否 | — | 任务名称 |
| `group_name` | varchar(64) | 否 | DEFAULT | 分组（自由文本） |
| `job_key` | varchar(128) | 否 | — | 已注册任务标识（ScheduledJob.key） |
| `cron_expression` | varchar(64) | 否 | — | Spring 6 段 cron：秒 分 时 日 月 周 |
| `description` | varchar(255) | 是 | — | 描述 |
| `status` | varchar(16) | 否 | DISABLED | ENABLED 启用 / DISABLED 停用 |
| `concurrency_policy` | varchar(16) | 否 | DISALLOW | DISALLOW 禁止并发 / ALLOW 允许并发 |
| `timeout_seconds` | int | 否 | 60 | 单次执行超时（秒） |
| `retry_times` | int | 否 | 0 | 失败重试次数（不含首次） |
| `retry_interval_seconds` | int | 否 | 10 | 重试间隔（秒） |
| `notify_policy` | varchar(32) | 否 | NEVER | NEVER / ON_FAILURE / ON_CONSECUTIVE |
| `notify_threshold` | int | 否 | 3 | 连续失败阈值（ON_CONSECUTIVE 生效） |
| `notify_targets` | varchar(255) | 是 | — | 指定接收人 id，逗号分隔；空=任务创建人 |
| `last_run_at` | datetime | 是 | — | 最近一次执行开始时间（UTC） |
| `last_run_result` | varchar(16) | 是 | — | SUCCESS / FAIL / TIMEOUT / SKIPPED |
| `next_run_at` | datetime | 是 | — | 下次自动执行时间（UTC）；自动执行后回写快照，手动执行不改 |
| `create_by` | bigint | 是 | — | 创建人（失败通知默认接收人） |
| `created_at` | datetime | 否 | — | 创建时间（UTC） |
| `updated_at` | datetime | 否 | — | 更新时间（UTC） |
| `deleted` | tinyint | 否 | 0 | 逻辑删除：0 未删 / 1 已删 |
| `uk_job_key` | varchar(128) | 是 | — | 唯一约束辅助列：仅未删除行参与唯一，删除后置 NULL |

<!-- AUTO:COLS:sys_job:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_job:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_job_group` | 普通 | `group_name` | 非唯一，用于加速查询 |
| `idx_job_status` | 普通 | `status` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_job_job_key` | 唯一 | `uk_job_key` | 唯一约束落在 `uk_job_key`（1 列） |

<!-- AUTO:IDX:sys_job:END -->

### `sys_job_log`（定时任务执行日志）

**用途**：一次触发一行的执行审计；自动/手动均记，含异常摘要与堆栈、实际尝试次数、影响条数。只增不删，删除任务不清日志。

**特殊列**：**豁免**数据权限（仅 `SUPER_ADMIN` 可读）；无软删除（审计数据）；审计：`created_at`。

**表间关系**：`job_id` → `sys_job.id`（逻辑）；`job_name`、`job_key` 为执行时快照，任务改名/删除后日志仍可读。

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_job_log:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | 主键 |
| `job_id` | bigint | 否 | — | 任务 id |
| `job_name` | varchar(64) | 否 | — | 任务名快照 |
| `job_key` | varchar(128) | 否 | — | 任务标识快照 |
| `trigger_type` | varchar(16) | 否 | — | AUTO 自动 / MANUAL 手动 |
| `started_at` | datetime | 否 | — | 开始时间（UTC） |
| `finished_at` | datetime | 否 | — | 结束时间（UTC） |
| `elapsed_ms` | bigint | 否 | 0 | 耗时（毫秒，含重试间隔） |
| `result` | varchar(16) | 否 | — | SUCCESS / FAIL / TIMEOUT / SKIPPED |
| `attempt_count` | int | 否 | 1 | 实际执行次数（首次+重试） |
| `error_summary` | varchar(255) | 是 | — | 异常摘要 |
| `error_stack` | text | 是 | — | 异常堆栈（截断） |
| `affected_rows` | bigint | 是 | — | 影响条数 |
| `created_at` | datetime | 否 | — | 日志写入时间（UTC） |

<!-- AUTO:COLS:sys_job_log:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_job_log:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_job_log_job_started` | 普通 | `job_id`、`started_at` | 非唯一，用于加速查询 |
| `idx_job_log_result` | 普通 | `result` | 非唯一，用于加速查询 |
| `idx_job_log_started` | 普通 | `started_at` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |

<!-- AUTO:IDX:sys_job_log:END -->

### `sys_user_session`（登录会话记录）

**用途**：—（语义待补）

**特殊列**：审计：`created_at`、`updated_at`

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_user_session:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | —（语义待补） |
| `session_id` | varchar(64) | 否 | — | 会话标识（写入 access/refresh 令牌的 sid） |
| `user_id` | bigint | 否 | — | 用户 id |
| `username` | varchar(32) | 否 | — | 登录账号快照 |
| `login_at` | datetime | 否 | — | 登录时间（UTC） |
| `last_active_at` | datetime | 否 | — | 最近活跃时间（UTC），60 秒节流更新 |
| `ip` | varchar(64) | 是 | — | 登录 IP |
| `user_agent` | varchar(255) | 是 | — | 原始 User-Agent |
| `device_type` | varchar(32) | 否 | UNKNOWN | PC / MOBILE / TABLET / UNKNOWN |
| `browser` | varchar(64) | 是 | — | 浏览器识别（UA 启发式） |
| `os` | varchar(64) | 是 | — | 操作系统识别（UA 启发式） |
| `invalidated_at` | datetime | 是 | — | 失效时间（UTC）；NULL = 未失效 |
| `invalidate_reason` | varchar(32) | 是 | — | LOGOUT / KICKED_BY_ADMIN / PASSWORD_CHANGED / ACCOUNT_DISABLED / USER_DELETED |
| `created_at` | datetime | 否 | — | —（语义待补） |
| `updated_at` | datetime | 否 | — | —（语义待补） |

<!-- AUTO:COLS:sys_user_session:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_user_session:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_session_active` | 普通 | `invalidated_at`、`last_active_at` | 非唯一，用于加速查询 |
| `idx_session_last_active` | 普通 | `last_active_at` | 非唯一，用于加速查询 |
| `idx_session_user` | 普通 | `user_id`、`login_at` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_session_sid` | 唯一 | `session_id` | 唯一约束落在 `session_id`（1 列） |

<!-- AUTO:IDX:sys_user_session:END -->

### `sys_file_chunk_upload`（分片上传会话（断点续传））

**用途**：—（语义待补）

**特殊列**：数据权限：`dept_id`；审计：`created_at`、`updated_at`

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

<!-- AUTO:COLS:sys_file_chunk_upload:START -->

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | bigint | 否 | 自增 | —（语义待补） |
| `upload_id` | char(32) | 否 | — | 会话标识（32 位十六进制） |
| `uploader_id` | bigint | 否 | — | 上传者（会话唯一归属人） |
| `dept_id` | bigint | 是 | — | 上传者部门快照 |
| `original_name` | varchar(255) | 否 | — | 原始文件名 |
| `content_type` | varchar(128) | 是 | — | —（语义待补） |
| `size` | bigint | 否 | — | 文件总字节数 |
| `sha256` | char(64) | 否 | — | 声明的内容 SHA-256 |
| `chunk_size` | int | 否 | — | 分片字节数（256KB–5MB） |
| `total_chunks` | int | 否 | — | 分片总数 |
| `uploaded_chunks` | text | 是 | — | 已上传分片序号，逗号分隔 |
| `status` | varchar(16) | 否 | UPLOADING | UPLOADING / MERGED / ABORTED / EXPIRED |
| `created_at` | datetime | 否 | — | —（语义待补） |
| `updated_at` | datetime | 否 | — | —（语义待补） |

<!-- AUTO:COLS:sys_file_chunk_upload:END -->

**索引与唯一键**

<!-- AUTO:IDX:sys_file_chunk_upload:START -->

| 索引名 | 类型 | 列（按顺序） | 约束说明 |
|---|---|---|---|
| `idx_chunk_status_updated` | 普通 | `status`、`updated_at` | 非唯一，用于加速查询 |
| `idx_chunk_uploader` | 普通 | `uploader_id` | 非唯一，用于加速查询 |
| `PRIMARY` | 主键 | `id` | 主键约束落在 `id` |
| `uk_chunk_upload_id` | 唯一 | `upload_id` | 唯一约束落在 `upload_id`（1 列） |

<!-- AUTO:IDX:sys_file_chunk_upload:END -->

## 口令策略相关字段（`sys_user`，2026-11-12 新增）

| 字段 | 类型 | 说明 |
|---|---|---|
| `password_updated_at` | DATETIME NULL | 口令最近修改时间。**为 NULL 时按"已过期"处理**（历史数据不能假设刚改过，fail-closed） |
| `must_change_password` | TINYINT(1) NOT NULL DEFAULT 0 | 1 = 下次登录必须改密。置位场景：管理员重置口令、存量弱口令迁移（`V17__password_policy.sql` 把存量行全部置 1） |

置位/清位规则：

- 创建用户：`must_change_password = 0`（口令由创建者按策略输入）
- 管理员重置口令：置 `1`（新口令只有管理员知道，用户必须自己再改）
- 用户自助改密成功：置 `0`，同时刷新 `password_updated_at`
- 口令超过 `app.security.password.expire-days`：登录时 `passwordExpired = true`，效果等同必须改密