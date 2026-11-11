# 权限矩阵（as-built v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（竣工现状，2026-11-10） |
| 状态 | 由脚本从 `admin_starter` 库导出；人工备注单独标记 |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is**：当前库里“谁有哪些权限码、数据范围是什么”，不是规范（to-be） |
| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |

> **库变了要重跑**：菜单、角色、`sys_role_menu`、`sys_role` 的 `data_scope` / `own_filter`、`role_scope_target` 任一变化，都必须重跑
> `pwsh scripts/export-permission-matrix.ps1`；代码/库/权限码变更时本文件必须在第 8 步同步。
> 权限码事实源是 `sys_menu.permission_code`；接口鉴权口径以 `docs/api/openapi.generated.yaml` 与后端注解为准。

<!-- AUTO:HEADER:START -->
> 本文件由脚本导出于 2026-11-10，除「人工备注」外请勿手工编辑。
<!-- AUTO:HEADER:END -->

## 1. 权限码清单（来源 `sys_menu`，`deleted=0`）

<!-- AUTO:PERM-CODES:START -->

| 模块 | 类型 | 名称 | 权限码 | 菜单 id |
|---|---|---|---|---|
| 系统管理 | 菜单 | 用户管理 | `system:user:list` | 2 |
| 系统管理 | 菜单 | 角色管理 | `system:role:list` | 3 |
| 系统管理 | 菜单 | 菜单管理 | `system:menu:list` | 4 |
| 系统管理 | 菜单 | 部门管理 | `system:dept:list` | 5 |
| 系统管理 | 菜单 | 岗位管理 | `system:post:list` | 6 |
| 系统管理 | 菜单 | 字典管理 | `system:dict:list` | 7 |
| 系统管理 | 菜单 | 系统参数 | `system:config:list` | 8 |
| 系统管理 | 菜单 | 操作日志 | `system:log:list` | 9 |
| 系统管理 | 菜单 | 文件管理 | `system:file:list` | 10 |
| 系统管理 | 按钮/接口 | 新增用户 | `system:user:create` | 12 |
| 系统管理 | 按钮/接口 | 修改用户 | `system:user:update` | 13 |
| 系统管理 | 按钮/接口 | 删除用户 | `system:user:delete` | 14 |
| 系统管理 | 按钮/接口 | 重置密码 | `system:user:reset` | 15 |
| 系统管理 | 按钮/接口 | 启停用户 | `system:user:status` | 16 |
| 系统管理 | 按钮/接口 | 分配角色 | `system:user:assign` | 17 |
| 系统管理 | 按钮/接口 | 新增角色 | `system:role:create` | 18 |
| 系统管理 | 按钮/接口 | 修改角色 | `system:role:update` | 19 |
| 系统管理 | 按钮/接口 | 删除角色 | `system:role:delete` | 20 |
| 系统管理 | 按钮/接口 | 分配菜单 | `system:role:menu` | 21 |
| 系统管理 | 按钮/接口 | 配置数据范围 | `system:role:scope` | 22 |
| 系统管理 | 按钮/接口 | 新增菜单 | `system:menu:create` | 23 |
| 系统管理 | 按钮/接口 | 修改菜单 | `system:menu:update` | 24 |
| 系统管理 | 按钮/接口 | 删除菜单 | `system:menu:delete` | 25 |
| 系统管理 | 按钮/接口 | 新增部门 | `system:dept:create` | 26 |
| 系统管理 | 按钮/接口 | 修改部门 | `system:dept:update` | 27 |
| 系统管理 | 按钮/接口 | 删除部门 | `system:dept:delete` | 28 |
| 系统管理 | 按钮/接口 | 新增岗位 | `system:post:create` | 29 |
| 系统管理 | 按钮/接口 | 修改岗位 | `system:post:update` | 30 |
| 系统管理 | 按钮/接口 | 删除岗位 | `system:post:delete` | 31 |
| 系统管理 | 按钮/接口 | 新增字典 | `system:dict:create` | 32 |
| 系统管理 | 按钮/接口 | 修改字典 | `system:dict:update` | 33 |
| 系统管理 | 按钮/接口 | 删除字典 | `system:dict:delete` | 34 |
| 系统管理 | 按钮/接口 | 新增参数 | `system:config:create` | 35 |
| 系统管理 | 按钮/接口 | 修改参数 | `system:config:update` | 36 |
| 系统管理 | 按钮/接口 | 删除参数 | `system:config:delete` | 37 |
| 系统管理 | 按钮/接口 | 上传文件 | `system:file:upload` | 38 |
| 系统管理 | 按钮/接口 | 下载文件 | `system:file:download` | 39 |
| 系统管理 | 按钮/接口 | 删除文件 | `system:file:delete` | 46 |
| 系统管理 | 按钮/接口 | 用户手机号-查看 | `system:user:phone:view` | 47 |
| 系统管理 | 按钮/接口 | 用户手机号-掩码 | `system:user:phone:mask` | 48 |
| 系统管理 | 按钮/接口 | 用户邮箱-查看 | `system:user:email:view` | 49 |
| 系统管理 | 按钮/接口 | 用户邮箱-掩码 | `system:user:email:mask` | 50 |
| 系统管理 | 菜单 | 通知公告 | `system:notice:list` | 51 |
| 系统管理 | 按钮/接口 | 发布通知 | `system:notice:create` | 52 |
| 系统管理 | 按钮/接口 | 撤回通知 | `system:notice:revoke` | 53 |
| 系统管理 | 按钮/接口 | 删除通知 | `system:notice:delete` | 54 |
| 系统管理 | 按钮/接口 | 已读统计 | `system:notice:read-stats` | 55 |
| 系统管理 | 菜单 | 定时任务 | `system:job:list` | 56 |
| 系统管理 | 按钮/接口 | 新增任务 | `system:job:create` | 57 |
| 系统管理 | 按钮/接口 | 修改任务 | `system:job:update` | 58 |
| 系统管理 | 按钮/接口 | 删除任务 | `system:job:delete` | 59 |
| 系统管理 | 按钮/接口 | 启停任务 | `system:job:status` | 60 |
| 系统管理 | 按钮/接口 | 手动执行 | `system:job:run` | 61 |
| 系统管理 | 按钮/接口 | 执行日志 | `system:job:log` | 62 |
| 系统管理 | 菜单 | 会话管理 | `system:session:list` | 63 |
| 系统管理 | 按钮/接口 | 强制下线 | `system:session:kick` | 64 |
| 系统管理 | 按钮/接口 | 清理失效记录 | `system:session:clean` | 65 |
| 系统管理 | 按钮/接口 | 恢复文件 | `system:file:restore` | 66 |
| 系统管理 | 按钮/接口 | 彻底删除 | `system:file:purge` | 67 |
| 系统管理 | 菜单 | 缓存管理 | `system:cache:list` | 68 |
| 系统管理 | 按钮/接口 | 清理缓存 | `system:cache:clear` | 69 |
| 系统管理 | 按钮/接口 | 导入岗位 | `system:post:import` | 72 |
| 系统管理 | 按钮/接口 | 导出岗位 | `system:post:export` | 73 |
| 示例业务 | 菜单 | 示例业务 | `demo:task:list` | 40 |
| 示例业务 | 按钮/接口 | 新增任务 | `demo:task:create` | 41 |
| 示例业务 | 按钮/接口 | 修改任务 | `demo:task:update` | 42 |
| 示例业务 | 按钮/接口 | 删除任务 | `demo:task:delete` | 43 |
| 示例业务 | 按钮/接口 | 导入任务 | `demo:task:import` | 70 |
| 示例业务 | 按钮/接口 | 导出任务 | `demo:task:export` | 71 |

共 **69** 个权限码（菜单级 14 个 / 按钮接口级 55 个）。

<!-- AUTO:PERM-CODES:END -->

## 2. 角色 × 权限码矩阵（来源 `sys_role_menu`）

> `✓` = 该角色被授予该菜单/按钮；空白 = 未授予。列标题格式为 `角色编码（角色 id）`。

<!-- AUTO:ROLE-MATRIX:START -->

| 权限码 | 模块 | 类型 | SUPER_ADMIN（1） | DEPT_MANAGER（2） | EMPLOYEE（3） | CROSS_AUDITOR（4） | CUSTOM_VIEWER（5） | DEMO_DEPT（8） | DEMO_OWNER（9） |
|---|---|---|---|---|---|---|---|---|---|
| `system:user:list` | 系统管理 | 菜单 | ✓ | ✓ |  | ✓ | ✓ |  |  |
| `system:role:list` | 系统管理 | 菜单 | ✓ | ✓ |  | ✓ |  |  |  |
| `system:menu:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:dept:list` | 系统管理 | 菜单 | ✓ | ✓ |  | ✓ |  |  |  |
| `system:post:list` | 系统管理 | 菜单 | ✓ | ✓ |  | ✓ |  |  |  |
| `system:dict:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:config:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:log:list` | 系统管理 | 菜单 | ✓ |  |  | ✓ | ✓ |  |  |
| `system:file:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:user:create` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:user:update` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:user:delete` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:user:reset` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:user:status` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:user:assign` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:role:create` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:role:update` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:role:delete` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:role:menu` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:role:scope` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:menu:create` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:menu:update` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:menu:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:dept:create` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:dept:update` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:dept:delete` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:post:create` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:post:update` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:post:delete` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:dict:create` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:dict:update` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:dict:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:config:create` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:config:update` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:config:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:file:upload` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:file:download` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:file:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:user:phone:view` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:user:phone:mask` | 系统管理 | 按钮/接口 |  | ✓ |  |  |  |  |  |
| `system:user:email:view` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:user:email:mask` | 系统管理 | 按钮/接口 |  | ✓ |  |  |  |  |  |
| `system:notice:list` | 系统管理 | 菜单 | ✓ | ✓ |  |  |  |  |  |
| `system:notice:create` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:notice:revoke` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:notice:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:notice:read-stats` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:job:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:job:create` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:job:update` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:job:delete` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:job:status` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:job:run` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:job:log` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:session:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:session:kick` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:session:clean` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:file:restore` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:file:purge` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:cache:list` | 系统管理 | 菜单 | ✓ |  |  |  |  |  |  |
| `system:cache:clear` | 系统管理 | 按钮/接口 | ✓ |  |  |  |  |  |  |
| `system:post:import` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `system:post:export` | 系统管理 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `demo:task:list` | 示例业务 | 菜单 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `demo:task:create` | 示例业务 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `demo:task:update` | 示例业务 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `demo:task:delete` | 示例业务 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `demo:task:import` | 示例业务 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| `demo:task:export` | 示例业务 | 按钮/接口 | ✓ | ✓ |  |  |  |  |  |
| **合计（权限码）** |  |  | **67** | **31** | **1** | **6** | **3** | **1** | **1** |

<!-- AUTO:ROLE-MATRIX:END -->

## 3. 角色数据权限（来源 `sys_role` / `role_scope_target` / `sys_user_role`）

<!-- AUTO:ROLE-SCOPE:START -->

| 角色 id | 编码 | 名称 | 组织范围（6 档） | 归属过滤（3 档） | 自定义目标 | 关联用户数 | 授权菜单数 | 授权权限码数 |
|---|---|---|---|---|---|---|---|---|
| 1 | `SUPER_ADMIN` | 超级管理员 | `ALL` | `NONE` | — | 1 | 69 | 67 |
| 2 | `DEPT_MANAGER` | 部门管理员 | `DEPT_AND_CHILD` | `NONE` | — | 1 | 32 | 31 |
| 3 | `EMPLOYEE` | 普通员工 | `SELF` | `CREATOR` | — | 3 | 2 | 1 |
| 4 | `CROSS_AUDITOR` | 跨部门审计 | `CUSTOM_DEPT` | `NONE` | DEPT×2 | 1 | 7 | 6 |
| 5 | `CUSTOM_VIEWER` | 指定人员观察者 | `CUSTOM_USER` | `NONE` | USER×1 | 1 | 4 | 3 |
| 8 | `DEMO_DEPT` | 部门范围示例 | `DEPT` | `NONE` | — | 1 | 1 | 1 |
| 9 | `DEMO_OWNER` | 负责人过滤示例 | `ALL` | `OWNER` | — | 1 | 1 | 1 |

<!-- AUTO:ROLE-SCOPE:END -->

**规则（实现口径，不是规范）**

- 组织范围 6 档：`ALL` 全部 / `DEPT` 本部门 / `DEPT_AND_CHILD` 本部门及下级 / `SELF` 仅本人创建 / `CUSTOM_DEPT` 指定部门 / `CUSTOM_USER` 指定人员。
- 归属过滤 3 档：`NONE` 不限 / `CREATOR` 仅本人创建 / `OWNER` 仅本人负责（`ownerColumn`，如 `biz_demo_task.owner_id`）。
- **多角色取并集**：组织范围按 `deptIds` / `userIds` 合并；任一角色 `ALL` 即放行全部。
- **归属取最宽松档**：任一角色 `own_filter=NONE` 即不加归属限制；否则 `CREATOR` 与 `OWNER` 条件按 `OR` 合并。
- **无角色**：默认只能看本人创建的数据（`create_by = 当前用户`）。
- 两个维度叠加为 `组织条件 AND 归属条件`；某一维度启用但条件为空时输出 `1 = 0`（拒绝，不退化为全量）。
- 条件按用户缓存：`admin:datascope:v1:{userId}`，TTL 5 分钟；角色/用户角色/菜单授权变更后调用 `evictUser` 立即失效。

## 4. 例外单列

### 4.1 `sys_user` 豁免数据权限

- `sys_user` 在 `DataPermissionTables.EXEMPT` 中显式登记，实体/Mapper 标注 `@DataPermissionExempt`。
- 按部门查看用户使用列表普通筛选参数 `deptId`（`GET /api/v1/users?deptId=`），不经过数据权限层。
- 理由：能进入用户管理页的人已由功能权限 `system:user:list` 拦截；叠加数据权限会出现“管理员看不到自己要管的人”。

### 4.2 公开接口免鉴权字段（5 项）

`GET /api/v1/public/configs` 免鉴权，返回且仅返回：

| 字段 | 对应配置键 |
|---|---|
| `name` | `sys.name` |
| `slogan` | `sys.name.slogan` |
| `copyright` | `sys.copyright` |
| `icp` | `sys.icp` |
| `uploadMaxSize` | `sys.upload.max-size` |

白名单在 `PublicConfigAppService.PUBLIC_KEYS`；敏感项（`is_sensitive=1`）与停用项双重兜底不返回。新增字段必须同时改白名单与本文件。

### 4.3 `DataPermissionTables` 受控 / 豁免分类（15 张业务表）

| 分类 | 表 | 数量 |
|---|---|---|
| 受控 | `sys_oper_log`、`sys_file`、`biz_demo_task` | 3 |
| 豁免 | `sys_user`、`sys_user_role`、`sys_role`、`sys_role_menu`、`role_scope_target`、`sys_menu`、`sys_dept`、`sys_post`、`sys_dict_type`、`sys_dict_item`、`sys_config`、`sys_login_log` | 12 |
| 未登记 | 新增表若未显式登记，受控查询**直接报错**（不是静默跳过） | — |

受控表的归属列：

| 表 | 组织范围列 | 归属列（SELF/CUSTOM_USER） | 负责人列（OWNER） |
|---|---|---|---|
| `sys_oper_log` | `dept_id` | `create_by` | `create_by` |
| `sys_file` | `dept_id` | `create_by` | `create_by` |
| `biz_demo_task` | `dept_id` | `create_by` | `owner_id` |

## 5. 一致性核对（自动）

<!-- AUTO:INTEGRITY:START -->

- 授权指向不存在/已删除菜单：**0 条**。
- 启用的重复权限码：**0 组**（`sys_menu.permission_code` 已由 `uk_menu_permission_code` 唯一约束兜底，见 `docs/design/data-model.md` §3.3）。
- 按钮（F）父级缺失/被删：**0 个**。
- 未被任何启用角色授予的权限码：**0 个**。
- 授权给无权限码菜单（目录/个人中心，仅控制路由可见，属正常）：SUPER_ADMIN→系统管理、SUPER_ADMIN→个人中心、DEPT_MANAGER→系统管理、EMPLOYEE→系统管理、CROSS_AUDITOR→系统管理、CUSTOM_VIEWER→系统管理。
- 软删除菜单仍占物理 id：45（无权限码）、44（`demo:task:e2e`）。
- 软删除角色仍占物理 id：6（`TEST_DEPT`）、10（`E2E_TMP_ROLE`）。
- `sys_role_menu` 物理行数：**116**；其中启用角色授权行：见上表。

<!-- AUTO:INTEGRITY:END -->

## 6. 人工备注（重跑保留）

<!-- MANUAL:NOTES:START -->
- **权限模型变更（2026-11-08，V7 已执行）**
  1. 部门管理员（`DEPT_MANAGER`）已撤销 `system:user:assign`、`system:role:menu`、`system:role:scope`：不再能给自己或他人挂角色/扩权。部门内配人仍保留 `system:user:create/update/delete/reset/status`，可管理本部门及下级用户资料与状态；**角色分配收归 SUPER_ADMIN**。
  2. 普通员工（`EMPLOYEE`）已撤销 `system:user:list`、`system:role:list`：`/api/v1/users`、`/api/v1/roles` 均返回 403，仅保留 `demo:task:list`。
  3. 应用层已加校验：`UserAppService.create/update` 在 `roleIds` 发生变化时要求 `system:user:assign`，否则 403「无权分配角色，请由超级管理员操作」；前端用户表单的角色项也按该权限码隐藏。
  4. 软删除的测试菜单/角色仍占物理 id（见上文第 5 节），与 `docs/baseline/assertions.md` #12 的踩坑同源；按决策**只标注不清理**，当前鉴权查询均过滤 `deleted=0`，不影响线上权限。
<!-- MANUAL:NOTES:END -->

## 7. 自检清单

- [x] 权限码清单与 `sys_menu.permission_code` 一致（MUST, STATIC）
- [x] 矩阵与 `sys_role_menu` 一致，空白与 `✓` 可逐格核对（MUST, STATIC）
- [x] 数据范围每角色一行，写明多角色并集与归属最宽松档（MUST, STATIC）
- [x] 例外项单列：`sys_user` 豁免、公开 5 字段、受控/豁免分类（MUST, STATIC）
- [x] 文件头写明“库变了要重跑”并给出脚本（MUST, STATIC）