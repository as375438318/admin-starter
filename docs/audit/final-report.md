# 脚手架交付报告（v1.5）

> **状态：历史快照（2026-11-08），不是当前现状。** 后续 Flyway V4–V16 已新增/改写多项能力（令牌与会话、字段脱敏、通知、定时任务、文件回收站、缓存管理、导入导出），
> 与本报告冲突之处一律以 `docs/baseline/modules.md`、`docs/baseline/assertions.md`、`docs/requirements/system-modules.md` 为准。
> 已核实被推翻的条目见 §5 表内标注（2026-11-11 补注）。本文件保留原文不改写，以免丢失当时的验收证据。

> 日期：2026-11-08　范围：M1–M4 + 用户表数据权限裁决 + 示例业务表
> 结论：**可作为可复制的单端 PC 后台脚手架交付**；无阻断性缺陷，已知限制见 §5。

## 1. 本轮完成内容

### 1.1 用户表数据权限裁决（先于 M4）

- `sys_user` **显式豁免**：`UserDO` 与 `UserMapper` 标注 `@DataPermissionExempt`，并在 `DataPermissionTables.EXEMPT` 登记；拦截器改为“未登记表直接抛错”，不再有“没配置就跳过”的隐式放行。
- 删除 `sys_user → id` 的 SELF / CUSTOM_USER 映射；旧口径降级为 `DS-001-H` / `DS-002-H` 历史说明，旧 7 条实测结果标注**作废**。
- 按部门看用户改为列表普通筛选参数 `deptId`，已实测 `/users?deptId=3` 返回 2 条（id 3、4），不经过权限层。

### 1.2 14 张系统表受控/豁免分类

已写入 [impl-notes.md](impl-notes.md) §“14 张系统表 + 示例业务表的受控 / 豁免分类”，每张表一句理由：

- **受控**：`sys_oper_log`、`sys_file`、`biz_demo_task`
- **豁免**：`sys_user`、`sys_user_role`、`sys_role`、`sys_role_menu`、`role_scope_target`、`sys_menu`、`sys_dept`、`sys_post`、`sys_dict_type`、`sys_dict_item`、`sys_config`、`sys_login_log`

### 1.3 示例业务表 `biz_demo_task`（数据权限参考实现）

- `V3__demo_business.sql`：建表 + 12 条跨 5 个部门的示例数据 + 4 个菜单/权限码 + 2 个演示角色 + 2 个演示账号。
- 后端完整最小 CRUD：列表/详情/新增/修改/删除；列表与详情进入数据权限上下文，越权详情返回 **404**。
- 前端列表页 `/demo/tasks`：sticky 左/右列、分页 10/20/50 默认 20、骨架屏、空状态/失败重试、权限按钮、480px 抽屉、422 字段错误映射与首错聚焦。
- 6 档部门范围演示账号：`admin`(ALL)、`demo_dept`(DEPT)、`deptmgr`(DEPT_AND_CHILD)、`dev1`(SELF)、`auditor`(CUSTOM_DEPT)、`viewer`(CUSTOM_USER)。
- 3 档归属过滤：`NONE`（多数角色）、`CREATOR`（`dev1`）、`OWNER`（`demo_owner`，业务表显式标注 `owner_id`）。

### 1.4 三项体验修复实测

| 项 | 怎么验的 | 结果 |
|---|---|---|
| 骨架屏 300ms 延迟、只替换表格 body | 临时把 `/users` 接口延迟 15s，0.8s 时检查 DOM | `.skeleton-row=6`，表头/工具栏/分页仍在；验证后已删除临时延迟代码 |
| 行内按钮点击区 ≥32×32 | 浏览器测量行内“编辑/删除/更多”按钮盒模型 | 均为 **32×32**（全局 `.row-actions .el-button`），视觉尺寸未放大，48px 行高不变 |
| 表单首错滚动并聚焦 | 新增用户直接点保存，检查焦点与视口 | 焦点落在“账号”输入框，输入框在视口内（`apps/pc/src/utils/form.ts`） |

## 2. 数据权限 7 场景实测（新基准 `biz_demo_task`）

| # | 场景 | 账号 | 实测结果 | 判定 |
|---|---|---|---|---|
| 1 | `DEPT` 隔离 | `demo_dept` | `total=3`，id 1,2,10（全部 `dept_id=2`） | 通过 |
| 2 | `DEPT_AND_CHILD` 可见下级 | `deptmgr` | `total=5`，id 1,2,10（研发部）+ 3,4（研发一组） | 通过 |
| 3 | `CUSTOM_DEPT` 跨部门 | `auditor`（目标部门 2、4） | `total=6`，id 1,2,10,5,6,11；研发一组/财务部/总部不可见 | 通过 |
| 4 | `CUSTOM_USER` 只看指定用户 | `viewer`（目标用户 4） | `total=2`，id 2,4（均 `create_by=4`） | 通过 |
| 5 | 多角色并集 + 缓存失效 | `dev1` | 加 `ALL` 前 2 条 → 加角色 1 后 12 条 → 移除后**下一次请求立即**回到 2 条 | 通过 |
| 6 | 详情越权 404 | `dev1` 请求 `/demo-tasks/7` | HTTP **404**，业务码 `20401`；请求本部门 id 1 返回 200 | 通过 |
| 7 | 分页 `total` 与可见量一致 | `demo_dept` / `auditor` | `demo_dept` 每页 `total=3`（pageSize=1 第三页 id=10）；`auditor` 第三页 `total=6`，证明 `total` 是 SQL 过滤后的结果 | 通过 |

补充：`demo_owner`（ALL + OWNER）`total=2`（id 6,11，`owner_id=9`），验证 `owner_id` 显式标注生效。

> **基准变更说明**：原 M2 的 7 条结果跑在 `/users` 上，依赖已删除的 `sys_user → id` 映射，**旧结果作废**，不得引用。

## 3. M4 交付项

### 3.1 README 运行说明

已覆盖：`mvn spring-boot:run` 或 jar、前端根目录 `pnpm install` + `pnpm dev`、默认账号 `admin/admin123`、端口（后端 8080 / 前端 5173）、依赖（JDK 17、Node 22、pnpm 11、MySQL 8 @3307、Redis @6379）、首次启动 `application-local.yml`、`JAVA_HOME` 指向 JDK 17、本地 Cookie `Secure=false` 仅限本地/生产必须 `APP_AUTH_COOKIE_SECURE=true`。jar 路径为 `server/web/target/admin-starter.jar`。

### 3.2 偏差处置（二选一已落地）

- `FE-DIR-001`：**已按多端结构迁移**。原 `web/` 整体迁入 `apps/pc`，新增 `apps/mobile`（占位）与 `packages/{shared,ui-tokens}`（实体）、`packages/{platform,contracts}`（占位），根目录使用 pnpm workspace；旧 `web/` 已删除。详见第 4 节与 `docs/decisions/ADR-003-pc-multi-app-workspace.md`。
- `CT-AUTH-001`：本地 Cookie `Secure=false` 标为**仅本地例外**，生产必须 HTTPS + 开启，否则视为安全缺陷。

### 3.3 端到端走查

**接口层（admin，46 项全通过）**：登录 → 路由/权限码 → 用户（增/改/重置密码/启停/分配角色/查/删）→ 角色（增/改/数据范围指定部门/指定人员/分配菜单/删）→ 菜单/部门/岗位/字典/参数（增/改/查/删）→ 操作日志查询 → 文件上传/列表/下载 → 退出登录 → 未登录 401。

**界面层**：登录后 `/system/users`、`/system/roles`、`/system/menus`、`/system/depts`、`/system/posts`、`/system/dicts`、`/system/configs`、`/system/logs`、`/system/files`、`/demo/tasks` 十页均正常渲染数据；角色“数据范围”抽屉的组织范围显示六项（全部/本部门/本部门及下级/仅本人/指定部门/指定人员）、归属过滤显示三项（不过滤/仅本人创建/仅本人负责）；退出登录后回到 `/login`。

> 下拉显示中文为 UI 文案，提交值为 `ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM_DEPT/CUSTOM_USER` 与 `NONE/CREATOR/OWNER`，实测回读正确。

## 4. 构建与运行验证

- 后端：`mvn -B -DskipTests clean package` 成功；启动后 Flyway 依次执行 V1/V2/V3，健康检查通过，8080 正常。
- 前端：根目录 `pnpm install` 成功（4 个 workspace 包）；`pnpm build` 成功（`vue-tsc -b` 无类型错误，产物在 `apps/pc/dist`）。
- 数据库：V3 已应用；示例任务 12 条；E2E 临时数据已清理。

### 4.1 目录迁移（`web/` → `apps/pc`）

| 项 | 迁移前 | 迁移后 |
|---|---|---|
| PC 端应用 | `web/` | `apps/pc/`（`src` 文件逐一比对一致） |
| 共享类型 | `web/src/api/types.ts` | `apps/shared/src/types.ts`（`apps/pc/src/api/types.ts` 仅转发；2026-11-10 由 `packages/shared` 迁入，见 `CR-20261110-001`） |
| 设计令牌 | `web/src/styles/tokens.css` | `packages/ui-tokens/tokens.css`（由 `main.ts` 引入） |
| 跨端占位 | 无 | `apps/mobile/README.md` |
| 平台/契约占位 | 无 | `packages/platform/README.md`、`packages/contracts/README.md`（2026-11-10 已删除这两个空壳目录，见 `CR-20261110-001`） |
| 工作区配置 | 无（`web/package-lock.json`） | 根 `package.json`、`pnpm-workspace.yaml`、`pnpm-lock.yaml` |

改动的配置文件：`apps/pc/package.json`（改名 `@admin-starter/pc` + `workspace:*` 依赖）、`apps/pc/vite.config.ts`（新增共享包别名）、`apps/pc/tsconfig.json`（新增 `paths`）、`apps/pc/src/main.ts`（引入 tokens）、`apps/pc/src/styles/index.css`（去掉本地 `tokens.css` 引用）、`.gitignore`、根 `package.json`、`pnpm-workspace.yaml`。

**功能回归**：无。路由表、接口路径、页面行为、权限逻辑、后端代码均未改动；浏览器实测 `/dashboard`、`/system/users`、`/system/roles`、`/system/menus`、`/system/depts`、`/system/posts`、`/system/dicts`、`/system/configs`、`/system/logs`、`/system/files`、`/profile`、`/demo/tasks` 全部正常渲染，业务接口均 200，无 JS pageerror；控制台仅剩 `favicon.ico` 404（站点未提供图标，与迁移无关）。

## 5. 没做什么 / 已知限制（非阻断）

| 项 | 说明 |
|---|---|
| 多端适配 | 只有 PC 端可运行；`apps/mobile` 为占位 README，H5/小程序/App 不含代码，跨端选型待 `AGENTS.md` §9-B 补齐 |
| 自动化测试套件 | 未建设单元/集成测试工程；本轮采用接口实测 + 浏览器走查 |
| CI/CD 与生产部署 | 未提供流水线、容器化、反向代理与 HTTPS 配置 |
| 国际化 | 仅中文文案 |
| 文件删除 | 文件模块按规范仅列表/上传/下载，无删除接口；E2E 上传记录已从库与磁盘清理 —— **已被推翻**：V13 已上线逻辑删除进回收站、恢复、彻底删除（`docs/baseline/modules.md`） |
| 操作日志治理 | 无归档/清理策略，仅列表查询 |
| 数据权限粒度 | 表级行过滤，不含字段级/列级权限 |
| 历史数据脏点 | 角色 id 6/7 被早期软删除测试数据占用主键，V3 演示角色改用 id 8/9；无功能影响 |
| 本地 Cookie | 默认 `Secure=false`，仅本地 HTTP 联调例外，生产必须开启 |

## 6. 后续项目复用指引

1. 整目录复制，保留相对路径；填写 `AGENTS.md` §9-A/§9-B。
2. 默认账号与端口按需修改，务必替换默认密码。
3. 新业务表照 `biz_demo_task` 做：建表带 `dept_id`/`create_by`，负责人列显式标注，实体加 `@DataPermissionControlled`，并在 `DataPermissionTables` 登记。
4. 系统配置类表默认加入 `EXEMPT` 并写明理由；不要在用户表上叠数据权限。
5. 列表页直接复制 `apps/pc/src/views/demo/task/index.vue` 的 sticky/分页/骨架/权限按钮/首错聚焦写法。

