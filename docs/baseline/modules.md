# 系统基线 · 模块索引（as-built v1.10）

| 项目 | 内容 |
|---|---|
| 版本 | v1.10（2026-11-10，新增「岗位 · 导入导出」接线，Flyway V16）；v1.9（2026-11-10，新增「示例业务 · 导入导出」模块与 `sys_export_task`，Flyway V15）；v1.8 删除 `packages/platform`、`packages/contracts` 空壳；v1.7 前端共享包迁至 `apps/shared`、新增 composables 分层；v1.6 新增缓存管理 |
| 状态 | 已按本仓库实际代码与数据库核对 |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is**：系统现在“在哪”，不是规范（to-be）；不写业务细节 |
| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |

> 接口清单不写在这里：接口事实源是导出的实现基线 `docs/api/openapi.generated.yaml`，手写契约源是 `docs/api/openapi.yaml`；本表只写接口前缀。
> 表数量：业务表 **23 张**（V1 建 14 张 + V3 建 1 张 `biz_demo_task` + V10 建 3 张通知表 + V11 建 2 张定时任务表 + V12 建 1 张会话表 + V13 建 1 张分片会话表 `sys_file_chunk_upload` + V15 建 1 张导出任务表 `sys_export_task`），另有 `flyway_schema_history` 1 张；V2/V4/V5/V6/V7/V8/V9 只改数据或结构，无新建表（V7 增加的是生成列与唯一索引/索引调整，不新增表）。V8 只新增 4 行权限菜单与授权；V9 只加 `sys_user` 两列；V10 新增 3 张通知表与 5 枚权限码；V11 新增 `sys_job`、`sys_job_log` 与 7 枚权限码；V12 新增 `sys_user_session` 与 3 枚权限码（菜单挂在系统管理下）；V13 给 `sys_file` 加 `sha256/deleted/deleted_at/deleted_by` 与 2 个索引、新建 `sys_file_chunk_upload`、新增 2 枚文件权限码并种入 `file-orphan-cleanup` 任务；V14 只新增「缓存管理」菜单与 2 枚权限码（`system:cache:list`、`system:cache:clear`，仅授 `SUPER_ADMIN`），无新表；V15 新增 `sys_export_task`、2 枚权限码（`demo:task:import`、`demo:task:export`，授 `SUPER_ADMIN`/`DEPT_MANAGER`）与 `export-file-cleanup` 任务；V16 只新增 2 枚岗位导入/导出权限码（`system:post:import`、`system:post:export`，按 `system:post:list` 菜单挂按钮，授 `SUPER_ADMIN`/`DEPT_MANAGER`），无新表。Flyway 当前版本为 **V16**。
> 后端分层：系统域 Service/Repository 在 `com.adminstarter.system`，Controller 统一在 `com.adminstarter.web.controller`；认证在 `com.adminstarter.auth`；前端在 `apps/pc`（共享包在 `apps/shared`）。

## 1. 模块索引

| 模块 | 后端包 | 前端目录 | 主表 | 接口前缀 | 权限码前缀 |
|---|---|---|---|---|---|
| 系统管理 · 用户 | `com.adminstarter.system` | `apps/pc/src/views/system/user` | `sys_user` | `/api/v1/users` | `system:user:` |
| 系统管理 · 角色 | `com.adminstarter.system` | `apps/pc/src/views/system/role` | `sys_role` | `/api/v1/roles` | `system:role:` |
| 系统管理 · 菜单 | `com.adminstarter.system` | `apps/pc/src/views/system/menu` | `sys_menu` | `/api/v1/menus` | `system:menu:` |
| 系统管理 · 部门 | `com.adminstarter.system` | `apps/pc/src/views/system/dept` | `sys_dept` | `/api/v1/depts` | `system:dept:` |
| 系统管理 · 岗位 | `com.adminstarter.system` | `apps/pc/src/views/system/post` | `sys_post` | `/api/v1/posts` | `system:post:` |
| 系统管理 · 字典 | `com.adminstarter.system` | `apps/pc/src/views/system/dict` | `sys_dict_type`（另有 `sys_dict_item`） | `/api/v1/dicts` | `system:dict:` |
| 系统管理 · 参数 | `com.adminstarter.system` | `apps/pc/src/views/system/config` | `sys_config` | `/api/v1/configs` | `system:config:` |
| 系统管理 · 操作日志 | `com.adminstarter.system` | `apps/pc/src/views/system/log` | `sys_oper_log` | `/api/v1/oper-logs` | `system:log:` |
| 系统管理 · 文件 | `com.adminstarter.system`（存储抽象在 `domain.storage`，本地实现在 `infrastructure.storage`） | `apps/pc/src/views/system/file` | `sys_file`（另有 `sys_file_chunk_upload`） | `/api/v1/files` | `system:file:` |
| 系统管理 · 通知公告 | `com.adminstarter.system` | `apps/pc/src/views/system/notice`、`apps/pc/src/views/message` | `sys_notice`（另有 `sys_notice_scope`、`sys_notice_recipient`） | `/api/v1/notices`、`/api/v1/my-notices` | `system:notice:` |
| 系统管理 · 定时任务 | `com.adminstarter.system`（`application.job` 调度、`infrastructure.job` 持久化） | `apps/pc/src/views/system/job` | `sys_job`（另有 `sys_job_log`） | `/api/v1/jobs`、`/api/v1/job-logs` | `system:job:` |
| 系统管理 · 会话 | `com.adminstarter.system`（会话域）；`com.adminstarter.auth`（登录签发/校验挂钩）；Controller 在 `com.adminstarter.web` | `apps/pc/src/views/system/session`、`apps/pc/src/views/profile`（登录设备） | `sys_user_session` | `/api/v1/sessions`、`/api/v1/auth/sessions` | `system:session:` |
| 系统管理 · 缓存 | `com.adminstarter.system`（应用服务/Redis 网关）；命名空间白名单在 `com.adminstarter.common.cache`；Controller 在 `com.adminstarter.web` | `apps/pc/src/views/system/cache` | 无（Redis 运行态；审计写 `sys_oper_log`） | `/api/v1/cache` | `system:cache:` |
| 示例业务 | `com.adminstarter.system` | `apps/pc/src/views/demo/task` | `biz_demo_task` | `/api/v1/demo-tasks` | `demo:task:` |
| 示例业务 · 导入导出（通用框架） | `com.adminstarter.system`（通用实现在 `application/excel`、`application/export`；示例接线在 `application/excel/demo`） | `apps/pc/src/views/demo/task`（导入对话框/导出任务抽屉） | `sys_export_task`（临时文件不进 `sys_file`） | `/api/v1/demo-tasks`（`/import-template`、`/import`、`/export`、`/export-tasks`） | `demo:task:`（`import` 含模板下载、`export` 独立） |
| 系统管理 · 岗位 · 导入导出（通用框架第二接入点） | `com.adminstarter.system`（框架在 `application/excel`、`application/export`；岗位接线在 `application/excel/post`） | `apps/pc/src/views/system/post`（导入对话框/导出任务抽屉） | `sys_export_task`（临时文件不进 `sys_file`） | `/api/v1/posts`（`/import-template`、`/import`、`/export`、`/export-tasks`） | `system:post:`（`import` 含模板下载、`export` 独立） |
| 认证 · 登录/刷新/登出 | `com.adminstarter.auth` | `apps/pc/src/views/login` | `sys_login_log`（另有 `sys_user_session`；令牌存 Redis） | `/api/v1/auth` | 无 |
| 个人中心 | `com.adminstarter.system` | `apps/pc/src/views/profile` | `sys_user` | `/api/v1/profile` | 无（仅需登录态） |
| 公开配置（免鉴权） | `com.adminstarter.system` | 无（启动注入，无独立页面） | `sys_config` | `/api/v1/public` | 无 |
| 健康检查 | `com.adminstarter.web` | 无 | 无 | `/api/v1/health` | 无 |
| 仪表盘（纯前端） | 无 | `apps/pc/src/views/dashboard` | 无 | 无 | 无 |
| 基础层 · common | `com.adminstarter.common` | 无 | 无 | 无 | 无 |
| 基础层 · web | `com.adminstarter.web` | 无 | 无 | `/api/v1/**`（统一出口与鉴权） | 无 |
| 横切 · 字段级脱敏 | `com.adminstarter.common.masking`（注解/三态/掩码/回传守卫）、`com.adminstarter.web.config.masking`（响应序列化） | 无 | 无（作用于响应 DTO 字段） | 无 | `system:user:`（字段码 `...:phone:` / `...:email:`） |

## 2. 登记判断说明

- `common` 登记为“无主表/无接口/无权限码”：它是错误码、响应信封、异常与工具类基础层，不承载业务表。
- `web` 登记为“统一出口”：所有 Controller 与 `SecurityConfig`、`GlobalExceptionHandler` 在此，接口前缀写 `/api/v1/**`，不重复登记各业务接口。
- 认证主表写 `sys_login_log`：JWT 令牌存 Redis，不在数据库；该表是认证模块唯一的登录审计表，账号级“令牌版本号/失效原因”新增在 `sys_user.token_version`、`token_revoke_reason`（V9）；设备级会话落在 `sys_user_session`（V12），由认证签发时写入、过滤器每请求校验。
- 会话主表写 `sys_user_session`：一行一次登录、`sid` 与令牌 claim 绑定；用户端“登录设备”复用同一后端模块（`/api/v1/auth/sessions`），无独立权限码；管理端权限码 `system:session:list/kick/clean`。
- `sys_user_role`、`sys_role_menu`、`role_scope_target` 为关系表，不单独成模块，随用户/角色模块维护。
- `sys_dict_item` 随字典模块，不单独成行。
- 通知公告主表写 `sys_notice`：`sys_notice_scope`（接收范围规则）与 `sys_notice_recipient`（应读人快照 + 按人已读）随该模块维护，不单独成行；用户端“我的消息”复用同一后端模块，无独立权限码。
- 定时任务主表写 `sys_job`：`sys_job_log`（一次触发一行的执行日志）随该模块维护，不单独成行；任务实现是 `application/job` 下由 Spring 容器注册的 Bean，库里只存 `job_key` 与参数，不存可执行内容。
- 文件主表写 `sys_file`：`sys_file_chunk_upload`（一次分片上传一行，`status` 为 `UPLOADING/MERGED/ABORTED/EXPIRED`）随该模块维护，不单独成行；物理字节按 `sha256` 去重、键为 `yyyy/MM/dd/{sha256}`，`sys_file` 只存相对键 `storage_path`，回收站用 `deleted/deleted_at/deleted_by`，孤儿清理复用既有 `sys_job`（`file-orphan-cleanup`）。
- 缓存管理主表登记为“无”：缓存是 Redis 运行态，不落业务表；清理审计复用 `sys_oper_log`（模块「缓存管理」）。命名空间前缀唯一定义在 `com.adminstarter.common.cache.CacheNamespaces`，新增 Redis 写入点必须登记，否则落入“未登记键”（只读不可清理）。
- 导入导出主表登记为 `sys_export_task`：只有异步导出落表；导入不建任务表。导出文件落独立临时区（`{java.io.tmpdir}/admin-starter/exports/{userId}/{taskId}.xlsx`），**不写 `sys_file`**、不进文件管理与回收站；清理复用既有 `sys_job`（`export-file-cleanup`）。
- 字段级脱敏是横切能力，无独立页面与接口；序列化入口只在 `WebJacksonConfig` 注入的专用 `ObjectMapper`，全局 `ObjectMapper` 不注册该模块。
- 前端分层：页面在 `apps/pc/src/views`（目录与后端模块一一对应）；列表骨架/抽屉/字段错误/分片上传/预览/登录表单的共享状态逻辑在 `apps/pc/src/composables`（`use-*.ts`，导出 `use*` + `confirmDelete`）；请求封装在 `apps/pc/src/api`；跨端类型在 `apps/shared`（`@admin-starter/shared`）。
- 共享包位置：`apps/shared`（v1.7 由 `packages/shared` 迁入），`apps/pc` 通过 workspace 依赖 + vite alias + tsconfig paths 三条路径解析，alias/paths 指向源码 `apps/shared/src/index.ts`；`apps/shared` 无 `build` 脚本，不参与 `pnpm -r build`。`packages/` 现仅有 `ui-tokens`（`tokens.css`，真实在用）；`platform`、`contracts` 两个空壳（原仅 README、无 `package.json`、0 引用）已于 v1.8 删除，见 `CR-20261110-001`。

## 3. 完成标准

- 每个模块六个字段齐全，无空白格。
- 主表、接口前缀、权限码前缀能在对应事实源中找到。
- 代码中存在的模块全部登记，无遗漏。

## 4. 自检清单

- [x] 每行只写位置，未写业务规则（MUST, STATIC）
- [x] 主表 / 接口前缀 / 权限码前缀与各自事实源一致（MUST, STATIC）
- [x] 新增或迁移模块已回填（MUST, CONTEXT）
- [x] 未手写接口清单（接口事实源为导出的 `docs/api/openapi.generated.yaml`）（MUST, STATIC）
