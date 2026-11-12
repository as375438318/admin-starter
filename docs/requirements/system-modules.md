# 系统能力清单（as-built v1.8）

| 项目 | 内容 |
|---|---|
| 版本 | v1.8（2026-11-12，新增「工作台统计」：`GET /api/v1/dashboard/overview` + 权限码 `system:dashboard:view`，Flyway V18）；v1.7（2026-11-11，补回填：新增「岗位·导入导出」与「示例业务·导入导出」两个接入点，缓存命名空间 7→8 补 `import-dedup`）；v1.6（2026-11-09，新增缓存管理：命名空间白名单/SCAN 分页/清理审计） |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is 能力清单**，不是需求文档 |
| 依赖 | `docs/baseline/modules.md`、`docs/design/permission-matrix.md`、`docs/api/openapi.generated.yaml`、`docs/audit/impl-notes.md`、`docs/audit/final-report.md`、`docs/baseline/assertions.md` |

> **这是能力清单，不是需求文档。**
> 原因：这些通用模块当年按功能清单开发，**没有真实需求来源与验收人**；事后补写用户故事会把“实现现状”伪装成“用户需求”，误导后续二开读者。本文件只回答四件事：现在能做什么、明确不做什么、依赖哪些权限码与数据范围、有哪些已知限制。
> 内容全部从现有材料重组，不新造；无法确认的项标“未核实”。
> **代码/库/权限码变更时本文件必须同步（第 8 步回填）。**

## 0. 模块总览

| # | 模块 | 页面（`apps/pc`） | 接口前缀 | 权限码前缀 |
|---|---|---|---|---|
| 1 | 用户 | `views/system/user` | `/api/v1/users` | `system:user:` |
| 2 | 角色 | `views/system/role` | `/api/v1/roles` | `system:role:` |
| 3 | 菜单 | `views/system/menu` | `/api/v1/menus` | `system:menu:` |
| 4 | 部门 | `views/system/dept` | `/api/v1/depts` | `system:dept:` |
| 5 | 岗位 | `views/system/post` | `/api/v1/posts` | `system:post:` |
| 6 | 字典 | `views/system/dict` | `/api/v1/dicts` | `system:dict:` |
| 7 | 参数 | `views/system/config` | `/api/v1/configs` | `system:config:` |
| 8 | 操作日志 | `views/system/log` | `/api/v1/oper-logs` | `system:log:` |
| 9 | 文件 | `views/system/file` | `/api/v1/files` | `system:file:` |
| 10 | 示例业务 | `views/demo/task` | `/api/v1/demo-tasks` | `demo:task:` |
| 11 | 认证 | `views/login` | `/api/v1/auth` | 无（登录态） |
| 12 | 个人中心 | `views/profile` | `/api/v1/profile` | 无（仅需登录态） |
| 13 | 工作台统计 | `views/dashboard` | `/api/v1/dashboard` | `system:dashboard:` |
| 13 | 公开配置 | 无独立页面 | `/api/v1/public` | 无（免鉴权） |
| 14 | 健康检查 | 无 | `/api/v1/health` | 无（免鉴权） |
| 15 | 仪表盘 | `views/dashboard` | 无 | 无 |
| 16 | 基础层 | 无 | `com.adminstarter.common` / `com.adminstarter.web` | 无 |
| 17 | 通知公告 | `views/system/notice`、`views/message` | `/api/v1/notices`、`/api/v1/my-notices` | `system:notice:` |
| 18 | 定时任务 | `views/system/job` | `/api/v1/jobs`、`/api/v1/job-logs` | `system:job:` |
| 19 | 会话管理 | `views/system/session`、`views/profile`（登录设备） | `/api/v1/sessions`、`/api/v1/auth/sessions` | `system:session:` |
| 20 | 缓存管理 | `views/system/cache` | `/api/v1/cache` | `system:cache:` |
| 21 | 示例业务 · 导入导出（通用框架首个接入点） | `views/demo/task`（导入对话框 / 导出任务抽屉） | `/api/v1/demo-tasks`（`/import-template`、`/import`、`/export`、`/export-tasks`） | `demo:task:`（`import` 含模板下载、`export` 独立） |
| 22 | 岗位 · 导入导出（通用框架第二接入点） | `views/system/post`（导入对话框 / 导出任务抽屉） | `/api/v1/posts`（`/import-template`、`/import`、`/export`、`/export-tasks`） | `system:post:`（`import` 含模板下载、`export` 独立） |

## 1. 用户管理

### 1.1 提供的能力

- 页面：`/system/users`。列表（关键词/部门/状态筛选、分页）、新增、编辑、删除、重置密码、启停、分配角色、部门下拉选项。
- 接口：`GET/POST /api/v1/users`、`GET/PUT/DELETE /api/v1/users/{id}`、`PUT /api/v1/users/{id}/password`、`PUT /api/v1/users/{id}/status`、`PUT /api/v1/users/{id}/roles`、`GET /api/v1/users/options`。
- 能做：维护账号（用户名、昵称、部门、邮箱、手机号）、BCrypt 密码、单部门归属、多角色绑定、启停账号。
- 重置密码或停用账号会自增 `sys_user.token_version`，该用户已签发的 access/refresh 令牌立即失效（`assertions.md` #19）。

### 1.2 边界（明确不做）

- 不做多部门归属（`sys_user` 只有单列 `dept_id`）。
- 不做用户-岗位绑定：库内只有 `sys_post`，**没有** `sys_user_post` 关系表。
- 不做批量导入/导出、账号锁定、登录失败次数限制（未见实现）。
- 不做硬删除：删除为逻辑删除。

### 1.3 依赖的权限码与数据范围

- 菜单权限：`system:user:list`；按钮权限：`system:user:*`（新增/修改/删除/重置密码/启停/分配角色，逐条见 `docs/design/permission-matrix.md`）。
- 数据范围：**本表豁免数据权限**；“按部门看用户”是普通查询参数 `deptId`，不经过权限层。

### 1.4 已知限制

- `sys_user` 豁免数据权限，管理员能看到全部用户（`assertions.md` #1）。
- 用户名唯一键不含 `deleted`：逻辑删除后不能再用同一用户名重建（`assertions.md` #3）。
- 状态下拉取自字典 `user_status`，不是前端写死（`assertions.md` #10）。
- 分页默认值/选项只有一处来源（`assertions.md` #11）。

## 2. 角色管理

### 2.1 提供的能力

- 页面：`/system/roles`。列表、新增、编辑、删除、分配菜单（功能权限）、配置数据范围（6 档组织范围 + 3 档归属过滤 + 自定义目标）。
- 接口：`GET/POST /api/v1/roles`、`GET /api/v1/roles/all`、`GET/PUT/DELETE /api/v1/roles/{id}`、`PUT /api/v1/roles/{id}/menus`、`PUT /api/v1/roles/{id}/data-scope`。
- 能做：角色是“功能权限 + 数据权限”的唯一边界；一个用户可绑多个角色。

### 2.2 边界（明确不做）

- 不做角色层级/继承、角色复制、字段级数据权限。
- 不做硬删除：删除为逻辑删除；`ROLE_ADMIN` 等内置角色的可改范围未在代码中额外限制（**未核实**是否允许删除内置角色）。

### 2.3 依赖的权限码与数据范围

- 菜单权限：`system:role:list`；按钮权限：`system:role:*`（新增/修改/删除/分配菜单/配置数据范围）。
- 数据范围：本表自身豁免；角色的 `data_scope` / `own_filter` 决定该角色用户在受控表上的可见范围。

### 2.4 已知限制

- 多角色组织范围取**并集**，归属过滤取**最宽松档**（任一角色 `NONE` 即不加限制）；缓存 key `admin:datascope:v1:{userId}`，改角色立即失效（`assertions.md` #4）。
- 种子数据按 id 硬编码插入菜单可能因历史逻辑删除行占用 id 被静默跳过（`assertions.md` #12）。
- 权限模型风险见 `permission-matrix.md` §6：`DEPT_MANAGER` 持有 `system:role:menu` / `system:role:scope` / `system:user:assign`，理论上可扩权（**需人工确认**）。

## 3. 菜单管理

### 3.1 提供的能力

- 页面：`/system/menu`。树形菜单列表、新增、编辑、删除；维护目录（M）/菜单（C）/按钮（F）、路由 `path`、组件 `component`、权限码、图标、排序、显示/启用。
- 接口：`GET/POST /api/v1/menus`、`PUT/DELETE /api/v1/menus/{id}`。

### 3.2 边界（明确不做）

- 不做拖拽排序（只有数值 `sort`）、不做多语言、不做按钮以外的字段级权限。
- 不做菜单导入/导出。

### 3.3 依赖的权限码与数据范围

- 菜单权限：`system:menu:list`；按钮权限：`system:menu:*`。
- 数据范围：`sys_menu` 豁免；菜单/权限码本身必须全局可见。

### 3.4 已知限制

- 改名/图标/排序/隐藏后需**刷新页面或重新登录**才反映到侧栏（动态路由只在登录或刷新时拉取，`assertions.md` #8）。
- `permission_code` 无唯一约束，可出现重复权限码（见 `data-model.md` §3.3）。
- 菜单 id 若被历史逻辑删除行占用，按 id 硬编码插数据会被静默跳过（`assertions.md` #12）。

## 4. 部门管理

### 4.1 提供的能力

- 页面：`/system/depts`。部门树、列表、新增、编辑、删除、上级部门选择。
- 接口：`GET/POST /api/v1/depts`、`GET /api/v1/depts/options`、`PUT/DELETE /api/v1/depts/{id}`。
- 能做：维护单父节点组织树；`ancestors` 存祖先路径，移动部门时级联更新下级 `ancestors`。
- 部门增/改/移/删后数据权限立即按新树重算（组织版本号 `admin:datascope:deptver` 自增，`assertions.md` #20）。
- 移动父级时若目标在自己或自己的子孙节点下，返回 `409`「不能把部门移动到自己的下级（会形成环）」。

### 4.2 边界（明确不做）

- 不做多租户/多公司隔离；不做部门负责人与用户的强关联（`leader` 是自由文本）。
- 不做部门合并、批量调整。
- 不做删除后的恢复入口：删除为逻辑删除，但接口没有 restore；恢复需另开能力变更。

### 4.3 依赖的权限码与数据范围

- 菜单权限：`system:dept:list`；按钮权限：`system:dept:*`。
- 数据范围：`sys_dept` 豁免；组织树必须全局可见，否则无法选择上级/目标部门。

### 4.4 已知限制

- 子部门查询用 `FIND_IN_SET(deptId, ancestors)`，`idx_dept_ancestors` 已在 V7 删除（`data-model.md` §3.2）。
- 同一父部门下同名部门受 `uk_dept_parent_name` 约束（V7，含删除后重建的生成列方案，`assertions.md` #3）。
- 移动部门用祖先链分段精确比较拒绝成环；改回子串 `contains` 会把部门 1 与 10 误判（`assertions.md` #20）。
- 未登记到 `DataPermissionTables` 的新表在受控查询中直接报错，不是静默跳过（`assertions.md` #2）。

## 5. 岗位管理

### 5.1 提供的能力

- 页面：`/system/posts`。列表、按部门筛选、新增、编辑、删除；下载导入模板、导入、导出（含导出任务抽屉）。
- 接口：`GET/POST /api/v1/posts`、`PUT/DELETE /api/v1/posts/{id}`、`GET /api/v1/posts/import-template`、`POST /api/v1/posts/import`、`GET /api/v1/posts/export`、`GET /api/v1/posts/export-tasks`、`GET /api/v1/posts/export-tasks/{id}`、`POST /api/v1/posts/export-tasks/{id}/cancel`、`GET /api/v1/posts/export-tasks/{id}/file`（Flyway `V16`）。
- 导入导出是通用框架的**第二接入点**，用于验证框架通用性；字段规则来自 `PostExcelSchema`，模板/校验/导出表头同源派生（`docs/design/import-export-framework.md`）。

### 5.2 边界（明确不做）

- 不做用户-岗位绑定（无 `sys_user_post` 表）；岗位目前是“组织配置字典”，不参与鉴权。
- 导入只新增，不做“存在则更新”、不做 CSV、不做部分导入；岗位编码重复由唯一键整体回滚。

### 5.3 依赖的权限码与数据范围

- 菜单权限：`system:post:list`；按钮权限：`system:post:*`（含 `system:post:import` 覆盖模板下载与导入、`system:post:export` 独立）。
- 数据范围：`sys_post` 豁免；`dept_id` 只是岗位挂靠部门，不是数据权限列。

### 5.4 已知限制

- 岗位编码 `code` 全局唯一（`uk_post_code`），不是“部门内唯一”；软删除后不能复用同码（`assertions.md` #3）。
- 前端分页默认值与选项统一取自 `constants/pagination.ts`（`assertions.md` #11）。
- 导出**没有独立数据范围过滤**：列表与导出共用同一 `PostQuery` 条件（`assertions.md` #57）；岗位表本身不接数据权限。

## 6. 字典管理

### 6.1 提供的能力

- 页面：`/system/dict`。字典类型列表与字典项管理。
- 接口：`GET/POST /api/v1/dicts/types`、`PUT/DELETE /api/v1/dicts/types/{id}`、`GET /api/v1/dicts/types/{id}/items`、`POST /api/v1/dicts/items`、`PUT/DELETE /api/v1/dicts/items/{id}`、`GET /api/v1/dicts/bundles`。
- 能做：维护全局字典；`bundles` 一次取多个字典供前端下拉；用户/角色状态取自 `user_status`。

### 6.2 边界（明确不做）

- 不做字典导入/导出、不做按部门差异化字典（全局一致）。
- 不做硬删除：类型与项均为逻辑删除。

### 6.3 依赖的权限码与数据范围

- 菜单权限：`system:dict:list`；按钮权限：`system:dict:*`。
- 数据范围：`sys_dict_type` / `sys_dict_item` 豁免；字典必须跨部门一致。

### 6.4 已知限制

- 字典 `type` 全局唯一；软删除后不能复用同 `type`（`assertions.md` #3）。
- 同一字典下 `value` 没有唯一约束，可出现重复项（`data-model.md` §3.3）。

## 7. 参数管理

### 7.1 提供的能力

- 页面：`/system/config`。参数列表、新增、编辑、删除；敏感项列表掩码。
- 接口：`GET/POST /api/v1/configs`、`GET/PUT/DELETE /api/v1/configs/{id}`。
- 能做：维护键值参数；保存后由消费方读取（部分为 HOT，部分走公开配置缓存）。

### 7.2 边界（明确不做）

- 不做参数类型校验/枚举约束（除上传上限有专门校验）；不做版本历史、灰度、按环境覆盖（值在库中全局）。
- 不提供按参数名直接改库以外的生效通道；`remark` 只是说明，不是生效承诺。

### 7.3 依赖的权限码与数据范围

- 菜单权限：`system:config:list`；按钮权限：`system:config:*`。
- 数据范围：`sys_config` 豁免；参数是全局配置。

### 7.4 已知限制（来自 `CFG-AUDIT-001`–`006`）

- 参数模块曾因“只验收 CRUD、不验收谁消费”导致“改库不变”被误报为 Bug（`CFG-AUDIT-001`）。
- `sys.name` 由公开配置接口消费（白名单 5 项，`CFG-AUDIT-002`）；`sys.upload.max-size` 由上传逻辑真实消费（`CFG-AUDIT-003`）。
- 参数表几乎全部是 **HOT**；唯一启动期限制是上传的 200MB 容器天花板；`sys.security.token.secret` 已删除，真实密钥在 `application-local.yml` / `APP_AUTH_SECRET`（`assertions.md` #6、#9）。
- 哪个键被谁消费，以 `docs/design/config-registry.yaml` 的 `consumedBy` / `effect` 为准。

## 8. 操作日志

### 8.1 提供的能力

- 页面：`/system/log`。列表（按用户/模块/时间等筛选、分页）、查看详情。
- 接口：`GET /api/v1/oper-logs`（只读，无写接口）。
- 能做：查看操作人、模块、动作、请求方法/路径、参数快照、结果码、耗时、IP。

### 8.2 边界（明确不做）

- 不做删除/归档/清理/导出接口（页面只读）。
- 不做日志分区/冷热分离。

### 8.3 依赖的权限码与数据范围

- 菜单权限：`system:log:list`。
- 数据范围：`sys_oper_log` **受控**，按 `create_by` 隔离，避免越权查看他人操作。

### 8.4 已知限制

- 数据权限按 `create_by` 过滤，`dept_id` 只作展示快照；`idx_oper_log_dept` 未被查询使用（`data-model.md` §3.2）。
- `params` 写入前只对 `password` 脱敏；其他敏感字段（如证件号、手机号）可能原样落库（代码事实，**是否需要扩展脱敏待确认**）。
- 日志表无 `deleted` 列，不做逻辑删除。

## 9. 文件管理

### 9.1 提供的能力

- 页面：`/system/files`（菜单 `path` 是复数；组件 `system/file/index`）。列表、多文件上传（拖拽/粘贴）、分片上传与断点续传、秒传、下载、图片/PDF 在线预览、删除进回收站、恢复、彻底删除、回收站列表。
- 接口：`GET/POST /api/v1/files`、`POST /api/v1/files/instant`、`GET /api/v1/files/{id}/download`、`GET /api/v1/files/{id}/preview`、`GET /api/v1/files/recycle`、`DELETE /api/v1/files/{id}`、`POST /api/v1/files/{id}/restore`、`DELETE /api/v1/files/recycle/{id}`、分片会话 `POST/GET/DELETE /api/v1/files/chunk-sessions...`。
- 能做：扩展名白名单；SHA-256 秒传与物理字节复用；按日期分片落本地磁盘（`yyyy/MM/dd/{sha256}`）；分片临时区 `.chunks/{uploadId}`；存储层通过 `FileStorage` 接口隔离（本轮仅本地实现）。

### 9.2 边界（明确不做）

- 不做版本控制、病毒扫描、内容 MIME/文件头强校验。
- **不做对象存储真实实现**：只留 `FileStorage` 接口扩展点；不得对外声称已支持。
- 不做跨实例分片、分布式锁；孤儿清理沿用既有单机调度。
- 业务表**只允许存 `file_id`**，不存 URL、文件名或存储逻辑键。

### 9.3 依赖的权限码与数据范围

- 菜单权限：`system:file:list`；按钮权限：`system:file:upload`、`system:file:download`、`system:file:delete`、`system:file:restore`、`system:file:purge`（具体见 `permission-matrix.md`）。
- 数据范围：`sys_file` **受控**，按 `dept_id` 组织范围 + `create_by` 归属过滤；回收站列表/恢复/彻底删除同样受控。
- `sys_file_chunk_upload` **豁免**数据权限，靠 `uploader_id` 归属校验（会话只对本人可见/可续传）。

### 9.4 已知限制

- 上传上限双重约束：容器天花板 200MB（启动期固化，改库不能突破）；业务真实上限读 `sys.upload.max-size`；超限返回 413 且带 `data.maxSize`（`assertions.md` #6）。
- 公开配置只暴露 `uploadMaxSize`，前端据此做 `beforeUpload` 预校验；413 兜底由文件页提示一次，不触发登出（回归项）。
- 扩展名校验**不代表内容安全**；未校验真实 MIME/文件头（代码事实）。
- `stored_name` 只写不读、`uploader_id` 与 `create_by` 重复（`data-model.md` §3.1）；历史平铺 UUID 键不做迁移，读取兼容。
- 秒传仅限**同一上传者**；跨用户物理去重只在真实上传并校验字节后发生。
- 预览仅图片与 PDF，其余类型只下载；回收站无自动过期，需人工彻底删除。

## 10. 示例业务（demo）

### 10.1 提供的能力

- 页面：`/demo/tasks`。列表、新增、编辑、删除；下载导入模板、导入、导出。
- 接口：`GET/POST /api/v1/demo-tasks`、`GET/PUT/DELETE /api/v1/demo-tasks/{id}`、`GET /api/v1/demo-tasks/import-template`、`POST /api/v1/demo-tasks/import`、`GET /api/v1/demo-tasks/export`、`GET /api/v1/demo-tasks/export-tasks`、`GET /api/v1/demo-tasks/export-tasks/{id}`、`POST /api/v1/demo-tasks/export-tasks/{id}/cancel`、`GET /api/v1/demo-tasks/export-tasks/{id}/file`。
- 能做：演示受控表的 6 档组织范围与 3 档归属过滤（`dept_id` / `create_by` / `owner_id`）；演示「字段元数据 → 模板 / 导入校验 / 导出」同源派生。
- 导入：只新增；单一同步通道，单事务，任一行错整批回滚；单批 20000 行、单文件 50MB，超限直接拒绝（不转异步、不要求分批）；SAX 事件流解析；同人同文件 5 分钟去重；页面显示上传/校验/写入阶段提示。
- 导出：≤2000 行同步下载、>2000 行异步任务（上限 100000 行）；受数据权限（可见行）、字段级脱敏（可见列）、审计三重约束；隐藏字段整列不输出；异步临时文件写独立目录、按人隔离、24 小时过期清理，不进文件管理列表与回收站。

### 10.2 边界（明确不做）

- 示例性质，不承载真实业务；无审批、无附件。
- 导入不做「存在则更新」、不做异步、不做部分导入、不做 CSV；错误清单只在页面内展示。
- 不建独立任务中心页面（用「我的任务」抽屉 + 顶栏通知，仅导出任务）。
- 本轮接入点只有示例业务与岗位两个；其它模块不提供导入导出（新增接入点须另开变更并复用同一 schema 机制）。

### 10.3 依赖的权限码与数据范围

- 菜单权限：`demo:task:list`；按钮权限：`demo:task:*`（`create/update/delete/import/export`）。
- 模板下载与导入共用 `demo:task:import`（模板是导入的必要前置）；导出单独 `demo:task:export`。
- 数据范围：`biz_demo_task` **受控**；`dept_id` 组织范围、`create_by` SELF/CUSTOM_USER、`owner_id` OWNER。

### 10.4 已知限制

- 多角色并集与归属最宽松档规则同样适用（`assertions.md` #4）。
- 空条件返回 `1 = 0`，不会退化为全量可见（`assertions.md` #5）。
- 历史 7 个数据权限场景的旧口径（以 `sys_user` 为受控表）已作废，以 `biz_demo_task` 实测为准（`impl-notes.md` DS-001-H/DS-002-H/DS-005）。

## 11. 认证（登录 / 刷新 / 登出）

### 11.1 提供的能力

- 页面：`/login`（独立全屏认证页）。
- 接口：`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me`、`GET /api/v1/auth/permissions`、`GET /api/v1/auth/routes`。
- 能做：账号密码登录、签发访问令牌与刷新令牌、刷新令牌、登出、下发当前用户权限码与动态路由。
- 令牌立即失效：停用/删除/管理员重置密码/本人改密后旧令牌立即失效；本人改密保留当前设备、踢其他设备；角色/权限撤销与调部门按库实时生效（`assertions.md` #19）。

### 11.2 边界（明确不做）

- 不做 MFA、SSO、第三方登录、验证码（未见实现）。
- 不做按设备/会话的精细吊销：失效口径是“账号级令牌版本号”，只支持整用户失效；本人改密通过“升版本 + 当前设备重签”实现保留当前设备。
- 失效后接口返回 401 分码：20103 停用 / 20104 删除 / 20105 失效 / 20106 密码被重置 / 20107 密码被修改；前端据此区分提示。

### 11.3 依赖的权限码与数据范围

- 无菜单权限；登录后按角色装配权限码与路由。

### 11.4 已知限制

- 令牌存 Cookie：`ACCESS_TOKEN`（Path=/）、`REFRESH_TOKEN`（Path=/api/v1/auth）；本地 HTTP 下 `Secure=false`，生产必须 HTTPS + `APP_AUTH_COOKIE_SECURE=true`（`impl-notes.md` CT-AUTH-001/002）。
- 账号级令牌状态存 `sys_user.token_version` / `token_revoke_reason`；access 与 refresh 都带 `ver`，刷新令牌不再是“只查 jti 白名单”。
- 每请求按库重取角色/权限/部门，代价是每请求多 3 次查询；这是“撤销立即生效”的取舍（`docs/design/auth-and-datascope.md` §2.3）。
- 写操作有 `OriginCheckFilter` 校验，白名单 `http://localhost:5173`、`http://127.0.0.1:5173`（`CT-AUTH-003`）。
- 登录页控件高度 42/44px，偏离 `UI-PC-FORM-002` 的 32–36px，属已知偏离（`UI-PC-FORM-002-DEV`）。

## 12. 个人中心

### 12.1 提供的能力

- 页面：`/profile`。查看个人资料、修改密码、查看登录日志。
- 接口：`GET/PUT /api/v1/profile`、`PUT /api/v1/profile/password`、`GET /api/v1/profile/login-logs`。

### 12.2 边界（明确不做）

- 不做头像上传、消息通知、第三方账号绑定。
- 不做自助改部门/角色（由管理员维护）。

### 12.3 依赖的权限码与数据范围

- 无额外权限码，仅需登录态；数据范围天然是“本人”。

### 12.4 已知限制

- 修改密码后**其他会话立即失效、当前设备保留**：本人改密自增 `token_version` 并为当前请求重签（`assertions.md` #19）。
- 登录日志按 `user_id` 过滤并按 `created_at` 倒序，两个单列索引会 filesort（`data-model.md` §3.2）。

## 13. 公开配置（免鉴权）

### 13.1 提供的能力

- 接口：`GET /api/v1/public/configs`。
- 能做：登录前注入站点名称、标语、版权、ICP、上传上限。

### 13.2 边界（明确不做）

- 只返回 5 个字段，不返回任何其他参数；不提供写接口。

### 13.3 依赖的权限码与数据范围

- 免鉴权；白名单 `PublicConfigAppService.PUBLIC_KEYS`。

### 13.4 已知限制

- 白名单**只有 5 项**（`name` / `slogan` / `copyright` / `icp` / `uploadMaxSize`）；随手加字段会把敏感参数暴露到免鉴权接口（`assertions.md` #7）。
- 敏感项（`is_sensitive=1`）与停用项双重兜底不返回。

## 14. 健康检查

### 14.1 提供的能力

- 接口：`GET /api/v1/health`，返回 `status=UP`、服务名与时间。

### 14.2 边界（明确不做）

- 不做依赖健康检查：代码无条件返回 `UP`，**不检查 MySQL / Redis**（`HealthController` 无数据库调用）。

### 14.3 依赖的权限码与数据范围

- 免鉴权；无数据范围。

### 14.4 已知限制

- 该端点只能证明“进程活着”，不能作为数据库/缓存可用性的探针（二开若用于 K8s readiness 需自行扩展）。

## 15. 仪表盘（纯前端）

### 15.1 提供的能力

- 页面：`/dashboard`。欢迎区、快捷入口（按权限码过滤）、开始使用提示。

### 15.2 边界（明确不做）

- 不提供统计接口、图表、待办聚合；数据全部来自当前登录用户与权限码。

### 15.3 依赖的权限码与数据范围

- 快捷入口按 `system:user:list` / `system:role:list` / `system:dept:list` / `system:log:list` 过滤；无数据范围。

### 15.4 已知限制

- 快捷入口权限码是**前端硬编码字符串**，新增/改名权限码时需同步前端；不在 `permission-matrix.md` 的库导出范围内（需人工核对）。

## 16. 基础层（common / web）

### 16.1 提供的能力

- `common`：统一响应信封、业务错误码、异常类型、工具类。
- `web`：全部 Controller、`SecurityConfig`、`GlobalExceptionHandler`、`OriginCheckFilter`、操作日志切面、OpenAPI 配置。

### 16.2 边界（明确不做）

- 不承载业务表、不定义业务规则；不单独登记接口前缀（统一 `/api/v1/**`）。

### 16.3 依赖的权限码与数据范围

- 无权限码；鉴权与数据权限的“开关”在 `web` 与 `system` 的配置类中，不在 `common`。

### 16.4 已知限制

- 数据权限拦截器对未登记表**直接报错**：新增受控/豁免表必须改 `DataPermissionTables`（`assertions.md` #2）。
- 错误响应与 HTTP 状态以 `docs/api/openapi.yaml` 契约为准；413 已补进契约（见 `docs/audit/impl-notes.md` CFG-AUDIT-004）。

## 17. 通知公告（站内信）

### 17.1 提供的能力

- 页面：管理端 `/system/notices`（`views/system/notice`）；用户端 `/messages`（`views/message`，顶栏铃铛入口）。
- 接口（管理端）：`GET/POST /api/v1/notices`、`GET /api/v1/notices/{id}`、`POST /api/v1/notices/{id}/revoke`、`DELETE /api/v1/notices/{id}`、`GET /api/v1/notices/{id}/readers`。
- 接口（用户端）：`GET /api/v1/my-notices`、`GET /api/v1/my-notices/unread-count`、`GET /api/v1/my-notices/{id}`、`POST /api/v1/my-notices/read-all`。
- 消息本体：标题、内容、级别（`NORMAL` / `IMPORTANT` / `URGENT`）、类型（`SYSTEM` / `BUSINESS` / `SECURITY` / `TASK`）、发布时间、发布人、是否可撤回、撤回时间与撤回人。
- 接收范围：`ALL`（全员）/ `ROLE`（角色）/ `DEPT`（部门）/ `USER`（用户），多条规则取**并集**；`ALL` 出现时忽略其他规则。
- 已读：按人记录 `read_at`；支持未读数、单条标记已读（点开即读）、全部已读。
- 管理端：列表（受数据权限）、发布、撤回、删除、已读统计（应读/已读/未读名单）。
- 发送能力：`NotificationPublisher` 服务接口，供定时任务失败、密码被重置等内部场景直接调用；`publish`（`REQUIRED`）与 `publishInNewTransaction`（`REQUIRES_NEW`）两种事务语义。
- 未读数：前端 30 秒轮询 + 页面重新可见立即拉取；页面隐藏时暂停。

### 17.2 边界（明确不做）

- 只做**站内信**一个渠道：不发邮件/短信/推送/IM；渠道抽象留在模型（`type` 与通知本体分离），不实现多渠道发送器。
- 未读数**不引入 WebSocket/SSE**：当前为轮询，切换条件见 `docs/design/notification.md` §7（需要 1 秒级延迟、在线人数达到瓶颈、需即时弹窗、多实例广播）。
- 不做定时发布、草稿、模板变量、附件、富文本、回执/确认（只有已读）。
- 不做“仅本部门”口径：`DEPT` 固定为“本部门 + 全部下级部门”，不提供开关。
- 撤回后收件箱不可见，但**发布快照与已读统计保留**（可追溯）。
- 不做硬删除：通知、范围、收件人均为逻辑删除。

### 17.3 依赖的权限码与数据范围

- 菜单权限：`system:notice:list`；按钮权限：`system:notice:create`、`system:notice:revoke`、`system:notice:delete`、`system:notice:read-stats`（菜单挂在“系统管理”下）。
- 数据范围：`sys_notice` **受控**（`dept_id` 组织范围 + `create_by` 归属过滤）；`sys_notice_scope`、`sys_notice_recipient` 豁免。
- **全员通知例外**（2026-11-09 用户拍板）：范围含 `ALL` 的通知对所有持有 `system:notice:list` 的管理员可见（列表、详情、已读统计），读取不受数据权限限制；非 `ALL` 通知仍按数据权限过滤。撤回/删除授权不变（仍只给 `SUPER_ADMIN`）。
- 用户端收件箱**不进入**数据权限上下文：可见性由 `sys_notice_recipient` 快照决定，避免组织范围叠加导致漏消息（`assertions.md` #22）。

### 17.4 已知限制

- 应读人数是**发布时快照**：发布后新入职/换部门的人不会补收，离职/停用的人仍计入应读（`assertions.md` #21）。
- 撤回不改变 `sys_notice_recipient`：已读统计仍按快照计算，撤回前已读的人仍计为已读。
- 收件人解析按 `is_enabled=1` 过滤；范围内没有可用启用用户时发布失败（`INVALID_PARAMETER`）。
- 未读数轮询间隔 30 秒，最坏延迟 30 秒；多实例部署时各实例各自轮询，无广播。
- 权限码与菜单由 `V10__notification_module.sql` 按角色 `code` 授权：`SUPER_ADMIN` 全部 5 枚，`DEPT_MANAGER` 仅 `list/create/read-stats`。
- 全员通知对所有 `system:notice:list` 持有者可见，部门管理员能在列表/详情/统计里看到全员通知，但看不到撤回/删除按钮。

## 18. 定时任务

### 18.1 提供的能力

- 页面：`/system/jobs`（`views/system/job`）。列表（关键词/分组/状态筛选、分页）、新增、编辑、删除、启停、手动执行一次、执行日志列表与详情。
- 接口：`GET/POST /api/v1/jobs`、`GET /api/v1/jobs/keys`、`PUT /api/v1/jobs/{id}`、`PUT /api/v1/jobs/{id}/status`、`POST /api/v1/jobs/{id}/run`、`DELETE /api/v1/jobs/{id}`、`GET /api/v1/job-logs`、`GET /api/v1/job-logs/{id}`。
- 任务定义：名称、分组、`job_key`（已注册任务标识）、cron 表达式、描述、状态（`ENABLED` / `DISABLED`）、并发策略（`DISALLOW` 禁止并发 / `ALLOW` 允许并发）、超时秒数、失败重试次数与间隔、失败通知策略（`NEVER` / `ON_FAILURE` / `ON_CONSECUTIVE`）、连续失败阈值、通知接收人。
- 调度：Spring `ThreadPoolTaskScheduler` + `CronTrigger`；改 cron 后取消旧 `ScheduledFuture` 并按新表达式重建，**不用重启**；列表展示根据表达式动态计算的 `nextRunAt`。
- 执行：自动触发按 cron；手动执行一次立即提交，日志触发方式为 `MANUAL`，且不改变 `nextRunAt`；停用后取消调度并不再自动执行。
- 日志：任务名、`job_key`、触发方式（`AUTO` / `MANUAL`）、开始/结束时间、耗时、结果（`SUCCESS` / `FAIL` / `TIMEOUT` / `SKIPPED`）、实际尝试次数、异常摘要与堆栈、影响条数 `affectedRows`。
- 失败通知：接 `NotificationPublisher.publishInNewTransaction`（独立事务），按策略发站内信；接收人取 `notify_targets`，为空回退任务创建人；通知类型 `TASK`，级别按失败策略 `URGENT` / `IMPORTANT`。

### 18.2 边界（明确不做）

- **安全红线**：只允许调度“已注册的任务标识”。任务实现必须是代码中注册的 Spring Bean（`ScheduledJob` 接口），数据库只存 `job_key` 与参数；**禁止**执行任意脚本、Shell 命令、SQL 字符串，也不做动态代码加载。
- 不做分布式调度、不做任务分片、不做任务依赖编排（DAG）、不做补偿/工作流。
- 不做任务参数模板与自由参数（当前 demo 任务无业务参数；如需参数，另开能力变更并先完成参数模型设计）。
- 不做任务分组管理页面：`group_name` 只是自由文本筛选维度。
- 不做日志自动清理/归档；执行日志只增不删（删除任务不删历史日志）。

### 18.3 依赖的权限码与数据范围

- 菜单权限：`system:job:list`；按钮权限：`system:job:create`、`system:job:update`、`system:job:delete`、`system:job:status`、`system:job:run`、`system:job:log`（菜单挂在“系统管理”下）。
- 数据范围：`sys_job`、`sys_job_log` **豁免**数据权限（仅 `SUPER_ADMIN` 持有权限码，且调度需全局一致的任务定义，按部门过滤会造成同名任务多份定义）。
- 权限码与菜单由 `V11__scheduled_task_module.sql` 授权：仅 `SUPER_ADMIN`。

### 18.4 已知限制（单机部署事实）

- **当前是单机部署**：同一任务由本进程的 `ThreadPoolTaskScheduler` 触发，**没有任何跨节点防重**。将来多实例部署时，同一任务会被多台机器重复执行。
- 现在如何“防”：只有进程内的并发策略（`DISALLOW` 用 `ConcurrentHashMap` 计数，同一实例内重复触发返回 `SKIPPED`/`409`），它不是分布式锁，跨进程无效。
- 上集群的前置条件（缺一不可）：① 调度选型改造（如 Quartz 集群 / XXL-JOB / ShedLock 类方案）；② 触发唯一性（选主或分布式锁）；③ 日志去重（避免多实例各写一条）；④ 任务实现幂等；⑤ 至少两实例并发验证记录。详见 `docs/design/scheduled-task.md` §7。
- 改 cron 只对当前实例生效；多实例时其他实例不会自动感知，需要重启或引入配置同步机制。
- 超时通过 `Future.get(timeout)` + `cancel(true)` 中止，只能中断**响应中断**的任务；不响应中断的任务仍会继续跑（`TIMEOUT` 日志会先落，但线程可能仍在执行）。
- 重试语义为“一次触发一条日志、`retry_times` 是首次之外的额外次数”，`attempt_count = 1 + retry_times`；每次重试间隔固定秒数，无退避。
- 失败通知的工具类只在通知发送失败时记日志，不反向影响任务执行结果。

## 19. 会话管理

### 19.1 提供的能力

- 页面：管理端 `/system/sessions`（`views/system/session`）；用户端在个人中心 `/profile` 内展示“登录设备”。
- 接口（管理端）：`GET /api/v1/sessions`、`DELETE /api/v1/sessions/{id}`、`DELETE /api/v1/sessions/user/{userId}`、`DELETE /api/v1/sessions/invalidated`。
- 接口（用户端）：`GET /api/v1/auth/sessions`、`DELETE /api/v1/auth/sessions/{id}`；退出登录沿用 `POST /api/v1/auth/logout`。
- 会话记录：用户、账号快照、登录时间、最后活跃时间、IP、User-Agent、设备类型、浏览器、操作系统、失效时间、失效原因（`LOGOUT` / `KICKED_BY_ADMIN` / `PASSWORD_CHANGED` / `ACCOUNT_DISABLED` / `USER_DELETED`）。
- 在线口径：`invalidated_at IS NULL` 且 `last_active_at >= now - 5 分钟`。
- 活跃时间节流：每会话最多 60 秒写一次库（Redis `SETNX` 节流键）。
- 管理端：列表（分页、按用户筛选、在线/已失效）、踢单条、按用户踢全部、清理失效超过 30 天的记录。
- 用户端：查看自己的登录设备（标记当前会话）、踢掉自己的其他设备。

### 19.2 边界（明确不做）

- 不做设备指纹、地理位置、按 IP 限制登录、并发登录数上限。
- 不做会话自动续期策略调整：access 仍是 15 分钟、refresh 仍是 7 天。
- 不做自动清理：清理为管理端手动触发，保留 30 天。
- 会话表不做逻辑删除：运行态数据，清理为物理删除（`assertions.md` 新增条目）。

### 19.3 依赖的权限码与数据范围

- 菜单权限：`system:session:list`；按钮权限：`system:session:kick`、`system:session:clean`（菜单挂在“系统管理”下）。
- 授权：仅 `SUPER_ADMIN`（会话列表含全部用户 IP/设备信息，强制下线为安全敏感操作）。
- 数据范围：`sys_user_session` **豁免**数据权限；管理端按功能权限拦截，按 `user_id` 普通筛选。
- 用户端只按当前登录用户过滤，不能查看或操作他人会话。

### 19.4 已知限制

- 令牌新增 `sid`；部署后旧令牌（无 `sid`）被判失效，所有在线用户需重新登录一次。
- 踢单条与按用户踢全部**不改** `sys_user.token_version`，只改会话行；账号级事件（改密/重置/停用/删除）仍由版本号负责，两套机制同批写入（`docs/design/session-management.md` §5）。
- 每请求成本：4 次 SELECT（用户/角色/权限/会话）+ 2 次 Redis（access 黑名单、活跃节流）+ 每 60 秒最多 1 次 UPDATE。
- 按用户踢全部会包含操作者自己的会话；界面需二次确认。
- 设备/浏览器识别是 UA 字符串启发式解析，不保证准确（尤其是自定义 UA 的脚本客户端）。
- 权限码与菜单由 `V12__session_management.sql` 创建并授权，仅 `SUPER_ADMIN`。

## 20. 缓存管理

### 20.1 提供的能力

- 页面：`/system/cache`（`views/system/cache`）。
- 接口：`GET /api/v1/cache/overview`、`GET /api/v1/cache/namespaces`、`GET /api/v1/cache/keys`、`DELETE /api/v1/cache/keys`、`DELETE /api/v1/cache/namespaces/{code}`、`DELETE /api/v1/cache/session-cache`、`GET /api/v1/cache/clean-logs`。
- 概览：Redis 连接状态、内存占用、key 总数（`DBSIZE`）、各命名空间键数、未登记键数。
- 命名空间：白名单 8 类（公开配置、组织版本号、数据权限、会话活跃节流、刷新令牌、access 黑名单、登录失败计数、导入去重指纹），每类含名称、前缀、用途、键数、敏感标记、是否可清理、清除后果。不可清理的有 3 类：`auth-deny`、`auth-fail`、`import-dedup`。
- 明细：按命名空间分页浏览 key 名、类型、TTL；支持单键删除与按前缀清空。
- 操作：删除单个 key、清空某个可清理命名空间、清理过期会话缓存（`session-touch` 命名空间）。
- 审计：每次清理写 `sys_oper_log`（模块「缓存管理」），参数含命名空间 code、key 名与删除数量；页面提供清理记录查询。

### 20.2 边界（明确不做）

- 不做任意键搜索、键名通配删除、`FLUSHDB`；不使用 `KEYS *`。
- 不读取、不返回任何 key 的 value；令牌、会话标识、用户名一类只展示 key 名与 TTL。
- 不提供 `auth-deny`（access 黑名单）、`auth-fail`（登录失败计数）、`import-dedup`（导入去重指纹）的清理：前两者清除会造成安全降级，后者清除会让同一文件被立即重传并可能产生重复数据；三者均标记为不可清理。
- 不做自动/定时清理缓存；不做 Redis 配置修改、内存淘汰策略调整。

### 20.3 依赖的权限码与数据范围

- 菜单权限：`system:cache:list`；按钮权限：`system:cache:clear`（菜单挂在“系统管理”下）。
- 授权：仅 `SUPER_ADMIN`；不授予 `DEPT_MANAGER`（清缓存影响全局，破坏性强）。
- 数据范围：不涉及数据库表与数据权限；缓存按命名空间全局操作。

### 20.4 已知限制

- 命名空间前缀的唯一定义处是 `com.adminstarter.common.cache.CacheNamespaces`；新增 Redis 写入点必须登记，否则会落入“未登记键”（只读、不可清理）。
- 概览的命名空间计数逐前缀 `SCAN`，键量大时为 O(N)；不自动轮询。
- 明细分页为“扫描到第 page×pageSize 条即停”；`auth-refresh` 累积 200+ 键时翻页仍需从头扫描，超过 256 批返回 `truncated=true`。
- 清理与审计非原子：审计写库失败时清理已生效，只留告警（与既有操作日志口径一致）。
- Redis 不可用时概览显示断开，清理接口返回业务错误，不做内存模式回退。

## 21. 未核实清单（不为整齐编内容）

| 模块 | 未核实项 | 为什么没写死 |
|---|---|---|
| 角色 | 是否允许删除内置角色（`ROLE_ADMIN` 等） | 代码未见保护分支，需确认业务预期 |
| 操作日志 | `params` 除密码外是否需扩展脱敏字段 | 属合规/安全决策，非代码事实 |
| 字典 | 字典项是否走缓存、缓存失效策略 | 未见明确缓存实现说明 |
| 健康检查 | 是否计划扩展为依赖探针 | 当前实现只返回进程存活 |
| 仪表盘 | 快捷入口是否应改为后端下发 | 当前为前端硬编码，需产品确认 |

## 22. 自检清单

- [x] 开头写清“能力清单，不是需求文档”及原因（MUST, STATIC）
- [x] 每个模块四件事齐全：能力 / 边界 / 权限码与数据范围 / 已知限制（MUST, STATIC）
- [x] 内容只从现有材料重组，未新造需求或用户故事（MUST, STATIC）
- [x] 无法确认的项标“未核实”并单列（MUST, STATIC）
- [x] 代码/库/权限码变更时必须同步的声明已写入文件头（MUST, CONTEXT）
