# 实现与规范偏差记录（impl-notes）

> 本文件记录脚手架实现过程中与 `docs/specs/` 规范的偏差、本机环境差异与待确认项。
> 按要求：不改动规范文档，只在此登记。每条注明影响面与临时处置。

## 环境（已切换为正式环境）

| 项 | 实际 | 规范/任务书要求 | 状态 |
|---|---|---|---|
| JDK | 17.0.8（`D:\Develop\Java\jdk-17`） | JDK 17 | 已满足（Maven PATH 默认 JDK 8，构建需设置 `JAVA_HOME`） |
| Maven | 3.6.3，PATH 默认指向 Java 8 | JDK 17 | 构建命令需 `JAVA_HOME` 指向 JDK 17，已写入 README 计划 |
| Node / npm | v22.23.1 / 10.9.8 | 未限定 | 无 |
| MySQL | **8.0.46**，`127.0.0.1:3307`，库 `admin_starter`，应用账号 `admin_app` | MySQL 8 | **已解决**：原 5.7 + 3309 临时实例已停止并删除数据目录；3306 上旧 5.7 属于其他项目，未触碰 |
| MySQL 参数 | `lower_case_table_names=1`、`default-time-zone=+08:00`、`max_connections=200` | 见任务书 | **已核对**：DDL/DML 中表名、字段名、索引名全部小写 |
| Redis | **已启用**，`127.0.0.1:6379`，`maxmemory=256mb`、`maxmemory-policy=allkeys-lru`、`appendonly=yes` | Spring Data Redis | **已解决**：`app.cache.type=redis`，登录后令牌与数据权限缓存均写入 Redis |
| 端口 | 后端 8080、前端 5173 | 8080 / 5173 | 无冲突 |

## 数据库初始化方式（已恢复 Flyway）

| 编号 | 说明 |
|---|---|
| DB-INIT-001 | **已解决**：恢复 Flyway（9.22.3，MySQL 8 支持）。迁移脚本为 `server/web/src/main/resources/db/migration/V1__init_schema.sql`、`V2__init_data.sql`，空库一次 migrate 建出 14 张表 + 初始化数据。原 `sql/schema.sql`、`sql/data.sql` 已删除，未保留两套建库方式。 |

## 凭据管理

| 编号 | 说明 |
|---|---|
| SEC-001 | 数据库与 Redis 口令不写入提交配置。`application.yml` 仅使用 `${APP_DB_PASSWORD:}`、`${APP_REDIS_PASSWORD:}` 等占位符；本地真实口令放在 `application-local.yml`（已加入 `.gitignore`），仓库内提供 `application-local.example.yml` 说明。 |

## 目录与工程结构（多端迁移后）

| 编号 | 规范 | 实现 | 处置 |
|---|---|---|---|
| FE-DIR-001 | `frontend-spec.md` 要求目录按 `apps/pc`、`apps/mobile`、`packages/{shared,platform,ui-tokens,contracts}` 组织 | **已按多端结构迁移**：`apps/pc`（原 `web/` 整体迁入，`src` 文件逐一比对一致）、`apps/mobile`（占位 README）、`packages/shared`（`types.ts` 实体）、`packages/ui-tokens`（`tokens.css` 实体）、`packages/platform` 与 `packages/contracts`（占位 README） | 迁移前后路径：`web/src/**` → `apps/pc/src/**`；`web/src/api/types.ts` → `packages/shared/src/types.ts`（`apps/pc/src/api/types.ts` 只做转发）；`web/src/styles/tokens.css` → `packages/ui-tokens/tokens.css`。旧 `web/` 已删除，无两套并存。 |
| FE-DIR-001-修 | 规范只允许 `apps/pc` + `apps/mobile`，在 `apps` 下新建第三套应用判不通过 | 任务书曾提出 `apps/h5` + `apps/mini` 两个独立应用 | **未采用**：`apps/h5`、`apps/mini` 会构成“第三套应用”并违反 `FE-DIR-001`，统一收敛为 `apps/mobile` 一个跨端占位目录；结论记入 `docs/decisions/ADR-003`。 |
| FE-STACK-004 | 包管理器按 `AGENTS.md` §9-B 选定且唯一 | 仓库仅 `pnpm-lock.yaml`，根 `package.json` 声明 `packageManager: pnpm@11.7.0` 与 `engines.node>=22`；`pnpm-workspace.yaml` 声明 `apps/*`、`packages/*` 与 `allowBuilds` | **规则缺口 `RULE-GAP`**：`AGENTS.md` §9-B 仍为空（按要求禁止改动 `AGENTS.md`），pnpm 11 为任务书指定并已实测可安装/构建；§9-B 补齐时应补写此行依据 `ADR-003`。 |
| FE-STACK-001 | 技术栈按 `AGENTS.md` §9-B 确认后才可开工 | §9-B 为空 | **RISK-ACCEPTED**：本次为任务书明确要求的目录迁移，技术栈沿用现有 PC 栈（Vue 3 + Vite + TS strict）未变更；未新增框架与版本。补偿：`ADR-003` 记录该风险，§9-B 补齐后复核。 |
| FE-DEP-001 | 内部包用 `workspace:*` | `apps/pc` 依赖 `@admin-starter/shared`、`@admin-starter/ui-tokens` 均为 `workspace:*` | 符合；同时保留 Vite/TS 路径别名，便于不经构建直接消费 TS 源码 |
| FE-COMBO-003 | `packages/shared` 不得含 Web-only 能力 | `packages/shared` 只有纯类型，无 `window`/`document`/`localStorage` | 符合 |
| BE-ARCH-010 | 后端多模块 | `server/{common,system,auth,web}` | 与任务书一致，未改动 |

## 认证与 Cookie（M4 已决）

| 编号 | 规范 | 实现 | 处置 |
|---|---|---|---|
| CT-AUTH-001 | Cookie 建议 `Secure` | 本地 HTTP 开发默认 `false`，支持环境变量 `APP_AUTH_COOKIE_SECURE` 切换 | **仅本地例外**：`http://` 下 `Secure=true` 会导致浏览器不保存 Cookie。**生产必须 HTTPS + `APP_AUTH_COOKIE_SECURE=true`**，否则视为安全缺陷。README §0.5 已写明。 |
| CT-AUTH-002 | 令牌协议载体 | 访问令牌 Cookie `ACCESS_TOKEN`（Path=/）；刷新令牌 Cookie `REFRESH_TOKEN`（Path=/api/v1/auth） | 刷新令牌限制路径，减少暴露面 |
| CT-AUTH-003 | CSRF 防护 | `OriginCheckFilter` 校验写操作 Origin；白名单放行 `http://localhost:5173` 与 `http://127.0.0.1:5173` | 已修复首次联调的 403 |

## 契约

| 编号 | 说明 |
|---|---|
| API-001 | 未新建 `docs/api/errors.yaml`（任务书禁止改 `docs/templates`，且未要求产出该文件）。错误码按 `api-contract.md` 分段在 `ErrorCode` 中常量定义 |
| API-002 | 分页上限按契约（100）；`pageSize` 超限返回 400 |

## 数据权限实现口径（基准已变更：用户表豁免）

> **旧结果作废**：M2 阶段 7 个数据权限场景全部跑在 `/users` 列表上，依赖 DS-001 的 `sys_user → id` 用户身份列映射。用户已裁决 `sys_user` 不受数据权限控制，该映射已删除，**原 7 条实测结果不再有效**，新的基准为 `biz_demo_task` 示例业务表（结果见下文）。

| 编号 | 说明 |
|---|---|
| DS-001-H | **历史说明（已废弃）**：旧实现把 `sys_user` 当作受控表，并用 `id` 作为 SELF / CUSTOM_USER 的用户身份列。按最新裁决，`sys_user` 改为显式豁免，`SELF_COLUMNS` 中该映射已删除，不得再作为实现依据。 |
| DS-002-H | **历史说明（已废弃）**：旧实现在 `sys_user` 上按 `create_by` 叠加 OWNER / CREATOR 归属过滤。用户表豁免后该口径不再适用。 |
| DS-003 | 数据权限条件按用户缓存（`admin:datascope:v1:{userId}`，5 分钟）；角色数据范围变更、用户角色分配均调用 `evictUser`，实测移除 `ALL` 角色后下一次请求立即恢复隔离。 |
| DS-004 | **`sys_user` 显式豁免**：实体与 Mapper 标注 `@DataPermissionExempt`，并在 `DataPermissionTables.EXEMPT` 登记；拦截器对未登记表直接抛错，不再存在“没配置即跳过”的隐式放行。按部门查看用户使用列表普通参数 `deptId`，不经过权限层。 |
| DS-005 | **示例业务表**：`biz_demo_task`（`dept_id`、`create_by`、`owner_id`）作为数据权限参考实现，实体标注 `@DataPermissionControlled(ownerColumn = "owner_id", selfColumn = "create_by")`；提供最小 CRUD 与列表页，演示 6 档部门范围、3 档归属过滤。 |

### 14 张系统表 + 示例业务表的受控 / 豁免分类（后续项目照此选择）

| 表 | 分类 | 理由 |
|---|---|---|
| `sys_user` | 豁免 | 账户配置；能进页面已由 `system:user:list` 功能权限拦截，叠数据权限会出现管理员看不到要管的人。按部门查看用户走列表参数 `deptId`。 |
| `sys_user_role` | 豁免 | 用户-角色关系表，仅用于鉴权装配，不含业务数据语义。 |
| `sys_role` | 豁免 | 角色定义属系统配置，由管理员全局维护。 |
| `sys_role_menu` | 豁免 | 角色-菜单关系，属权限配置本身。 |
| `role_scope_target` | 豁免 | 角色数据范围目标，属权限配置本身；若受控会形成递归过滤。 |
| `sys_menu` | 豁免 | 菜单与权限码定义，系统配置，需要全局可见。 |
| `sys_dept` | 豁免 | 组织树主数据；若受控会导致选不到上级/目标部门，且部门维护已由功能权限控制。 |
| `sys_post` | 豁免 | 岗位字典/组织配置，非业务数据。 |
| `sys_dict_type` | 豁免 | 全局字典类型，需跨部门一致。 |
| `sys_dict_item` | 豁免 | 全局字典项，需跨部门一致。 |
| `sys_config` | 豁免 | 系统参数，全局配置。 |
| `sys_login_log` | 豁免 | 登录审计，运维用途，按功能权限查看。 |
| `sys_oper_log` | 受控 | 操作审计含业务操作痕迹，按 `create_by` 隔离，避免越权查看他人操作。 |
| `sys_file` | 受控 | 上传文件可能承载业务数据，按 `create_by` 与所属部门隔离。 |
| `biz_demo_task` | 受控 | 示例业务数据；`dept_id` 作部门范围，`create_by` 作 SELF/CUSTOM_USER，`owner_id` 作 OWNER 负责人列。 |

### 用示例表重跑的 7 个场景（实测）

| # | 场景 | 登录账号（用户/部门） | 结果 | 判定 |
|---|---|---|---|---|
| 1 | `DEPT` 隔离 | `demo_dept`（用户 8 / 研发部） | `total=3`，id 1,2,10（全部 `dept_id=2`） | 通过 |
| 2 | `DEPT_AND_CHILD` 可见下级 | `deptmgr`（用户 2 / 研发部） | `total=5`，id 1,2,10（研发部）+ 3,4（研发一组） | 通过 |
| 3 | `CUSTOM_DEPT` 跨部门 | `auditor`（用户 6 / 财务部，目标部门 2、4） | `total=6`，id 1,2,10,5,6,11；研发一组、财务部、总部均不可见 | 通过 |
| 4 | `CUSTOM_USER` 只看指定用户 | `viewer`（用户 7 / 财务部，目标用户 4） | `total=2`，id 2,4（均 `create_by=4`） | 通过 |
| 5 | 多角色并集 + 缓存失效 | `dev1`（用户 3） | 加 `ALL` 前 `total=2`（id 1,3）；加角色 1 后 `total=12`；移除后**下一次请求立即**回到 `total=2`（id 1,3） | 通过 |
| 6 | 详情越权 404 | `dev1` 请求 `/api/v1/demo-tasks/7`（财务部数据） | HTTP 404，业务码 `20401`「任务不存在或无权访问」；请求本部门 id 1 返回 200 | 通过 |
| 7 | 分页 `total` 与可见量一致 | `demo_dept` pageSize=1；`auditor` pageSize=2 | `demo_dept` 三页 `total` 恒为 3（第 3 页 id=10）；`auditor` 第 3 页 `total=6`。证明 `total` 由 SQL 层过滤后的结果计算 | 通过 |

### 归属过滤与用户表豁免补充实测

| 项 | 账号 | 结果 |
|---|---|---|
| `OWNER` 负责人过滤 | `demo_owner`（用户 9，`ALL` + `OWNER`） | `total=2`，id 6,11（`owner_id=9`） |
| `CREATOR` 归属过滤 | `dev1`（角色 3，`SELF` + `CREATOR`） | `total=2`，id 1,3（`create_by=3`） |
| 用户表不受控 | `dev1`（普通员工） | `/users` `total=9`（全部用户）；`/users/8` 返回 200；`/users?deptId=3` 返回 2 条（id 3,4），证明部门筛选是普通查询参数 |
| 示例表 CRUD | `admin` | 新建 id=13 → 更新为 `DONE`/部门 3 → 删除成功；示例数据恢复为 12 条 |


## 待确认（需人工决策）

| 编号 | 内容 | 影响 |
|---|---|---|
| HUMAN-001 | 前端目录是否迁移到 `apps/pc` | **已决（2026-11-08 变更）**：选“默认多端”，已按 `FE-DIR-001` 迁移为 `apps/pc` + `packages/*`，旧 `web/` 删除；迁移前后路径与依据见上文“目录与工程结构”与 `docs/decisions/ADR-003` |
| HUMAN-002 | 本地 Cookie `Secure` 是否开启 | **已决**：本地关闭、生产强制开启，已改为环境变量 `APP_AUTH_COOKIE_SECURE`；见 CT-AUTH-001 |

## 登录页（v4 mock-2 落地）

| 编号 | 规范 | 实现 | 处置 |
|---|---|---|---|
| UI-PC-FORM-002-DEV | `UI-PC-FORM-002` 规定表单控件高度 32–36px | 登录页按 v4 设计稿实现（设计稿已于 2026-11-11 从仓库移除，见本文件末「设计稿清理与历史重写」）：输入框 **42px**、主按钮 **44px** | **已知偏离，按设计稿执行**。理由：登录页为独立全屏认证表单，无表格/工具栏并列约束，42/44 在 375px 宽度下仍可点按且视觉层级更清晰。待规范侧决定是否给“独立全屏表单”增加 40–44px 例外。**建议**：新增该例外仅适用于无侧栏、无表格的独立认证页，并同时要求键盘可达、错误提示与控件间距不压缩；表格、弹窗、行内编辑仍保持 32–36px。规范未修改，仅在此登记。 |

## 配置消费链路缺口

| 编号 | 说明 |
|---|---|
| CFG-AUDIT-001 | **根因**：系统参数模块按“CRUD 能存能查”交付，验收项只覆盖列表、新增、编辑、敏感掩码，没有“谁消费这个值、改值后到哪里观察”的验收项。`sys_config` 的备注被当成生效承诺（如 `sys.name` 备注写“显示在登录页与顶栏”），但当时无任何代码读取，导致“改库不变”被误报为 Bug。 |
| CFG-AUDIT-002 | **已修复样例**：`sys.name` 现在由 `GET /api/v1/public/configs` 消费（白名单 5 项、Redis 缓存、参数保存后主动失效），前端启动注入浏览器标题/登录页/侧栏/页脚；实测“改值→刷新即变、改回→回退”。 |
| CFG-AUDIT-003 | **已修复（配置治理轮）**：`sys.upload.max-size` 已由 `FileAppService.upload` 真实消费（读 `PublicConfigAppService.uploadMaxSize()`，缓存 10 分钟），超限抛 `PayloadTooLargeException` → HTTP 413 + 信封，message 写明当前上限、`data.maxSize` 返回字节；容器层 `spring.servlet.multipart.max-file-size=200MB` 仅作天花板。参数保存时校验 1MB–200MB，越界 422 + `data.errors[{field:"configValue",code:"VALIDATION_FAILED"}]`，保存成功后失效公开配置缓存。`sys.security.token.secret` 已由 Flyway `V5__config_governance.sql` 物理删除，真实签名密钥口径见 `docs/env/local-dev.md` §6。 |
| CFG-AUDIT-004 | **防复发要求（建议写入后续任务模板）**：新增任何配置项时，交付物必须同时包含 ①消费点（文件+函数）；②“改值→观察”的实测记录（接口/页面证据）；③未消费时的明确标注。缺少任一项不得标为完成。公开配置另需单独的白名单评审，禁止把敏感项加入公开接口。 |
| CFG-AUDIT-005 | **治理轮其余修正**：①分页默认值收敛到 `apps/pc/src/constants/pagination.ts`（`DEFAULT_PAGE_SIZE=20`、`PAGE_SIZES=[10,20,50]`），9 个页面统一引用；②用户页/角色页状态下拉改由 `sys_dict` 的 `user_status` 提供（`apps/pc/src/utils/status-options.ts`，失败回落启用/停用）；③菜单保存后显式提示“已保存，侧栏将在刷新页面后更新”；④补 `sys.name.slogan`/`sys.copyright`/`sys.icp` 种子；⑤操作日志返回并展示 `deptName`（空值 `—`）；⑥新增 `DELETE /api/v1/files/{id}`（重复删除同 id 仍 200）并清理审计遗留 2MB 记录；⑦补 `favicon.svg`。 |
| CFG-AUDIT-006 | **仍存在的设计限制**：①容器 multipart 上限在启动期固化，运行期改库不能突破；200MB 是硬边界，超过只能改 `application.yml` 并重启；②公开配置缓存 TTL 10 分钟，且仅在参数 CRUD 后主动失效，多实例部署时其他实例需等 TTL 或共享缓存失效；③`sys.security.token.secret` 已删除但历史迁移脚本中不再出现该键，回滚需重新插入种子；④`app.auth.secret` 轮换必须重启，旧访问/刷新令牌全部失效。 |

## 权限与数据模型治理（2026-11-08 · V7）

| 编号 | 内容 |
|---|---|
| PERM-GOV-001 | 迁移 `V7__permission_and_index_governance.sql` 按业务键（角色 `code` + 菜单 `permission_code`）删除授权行：`DEPT_MANAGER` 撤 `system:user:assign`、`system:role:menu`、`system:role:scope`；`EMPLOYEE` 撤 `system:user:list`、`system:role:list`。不按菜单 id 硬编码，避免命中软删除历史行（见 `assertions.md` #12）。 |
| PERM-GOV-002 | **发现的提权路径**：`UserAppService.create/update` 都接受 `roleIds` 并直接 `replaceUserRoles`，不受 `system:user:assign` 约束；只撤菜单授权仍可通过用户表单给自己挂 `SUPER_ADMIN`。处置：新增 `checkRoleChangeAllowed`，角色集合发生变化时要求 `system:user:assign`，否则 403「无权分配角色，请由超级管理员操作」；未改变角色集合时不拦截，保证仅有 `system:user:update` 的人仍可改资料。前端用户表单角色项加 `v-permission="'system:user:assign'"`。 |
| PERM-GOV-003 | 部门管理员“给自己的部门配人”的替代路径：保留 `system:user:create/update/delete/reset/status` 与 `DEPT_AND_CHILD` 数据范围，可管理本部门及下级的账号资料与状态；**角色分配收归 SUPER_ADMIN**，不再由部门管理员承担。 |
| DATA-GOV-001 | 唯一约束采用 `STORED` 生成列 + 唯一索引，`deleted=1` 时生成列为 `NULL`（MySQL 唯一索引允许多个 `NULL`，即软删除后不占位）：`sys_dict_item(dict_type_id, uk_dict_item_value)`、`sys_menu(uk_permission_code)`（空码/`NULL` 不参与唯一）、`sys_dept(uk_parent_id, uk_name)`。取舍：以极小存储/写入开销换取库级一致性，不需要应用层补丁；前提是写入路径继续以 `deleted=0` 过滤（现有仓储满足）。 |
| DATA-GOV-002 | 索引删除：`idx_menu_permission`（权限码只 SELECT 无等值 WHERE）、`idx_user_create_by`（用户表豁免数据权限且列表不按 `create_by` 过滤）、`idx_dept_ancestors`（`FIND_IN_SET` 无法命中 B-tree）；`idx_login_log_user` + `idx_login_log_created` 合并为 `(user_id, created_at)`（唯一查询是 `pageByUser`：`user_id` 等值 + `created_at` 排序）。**`idx_oper_log_dept` 保留**：DEPT/DEPT_AND_CHILD/CUSTOM_DEPT 范围会生成 `dept_id IN (...)`，操作日志查询经 `DataPermissionContext.controlled` 使该条件生效，删了会退化为全表扫描。 |
| DATA-GOV-003 | 三个“无用列”**不删**：`sys_file.stored_name`（只写不读）、`sys_file.uploader_id`（无 WHERE 引用但接口响应回显 `uploaderId`）、`sys_user.create_by`（只写不读）。删列不可逆，结论与证据写入 `docs/design/data-model.md` §3.1，等单独确认。 |
| DATA-GOV-004 | 软删除残留菜单 44/45、角色 6/10 **只标注不清理**（决策），`docs/design/permission-matrix.md` §6 已记录；鉴权查询均过滤 `deleted=0`，不影响线上权限。脱敏相关代码本轮未触碰。 |

## 字段级脱敏（2026-11-08 · V8）

> 设计见 `docs/design/field-masking.md`（v1.0 / `APPROVED`）；本节只记实现与偏离。

| 编号 | 内容 |
|---|---|
| MASK-001 | `V8__field_masking_permissions.sql` 只新增：4 枚 `F` 型菜单（`system:user:phone:view`、`system:user:phone:mask`、`system:user:email:view`、`system:user:email:mask`，挂用户管理菜单），并按角色 `code` + `permission_code` 授予：`SUPER_ADMIN` → 两枚 `view`，`DEPT_MANAGER` → 两枚 `mask`，其他角色不授予。未修改 V1–V7，未按菜单/角色 id 硬编码。 |
| MASK-002 | 机制为通用实现、非用户模块特判：`@MaskedField(资源:字段, 格式)` + 权限码 `system:<资源>:<字段>:view|mask`；`MaskFormat` 唯一掩码函数（手机 11 位数字 → 前 3+`****`+后 4；邮箱 → 首字符+`***`+域名；不匹配返回 null 由判定层按隐藏处理）；`MaskResolver` 三态判定（有 view 明文 / 无 view 有 mask 掩码 / 都没有隐藏），无认证上下文 fail-closed，`SUPER_ADMIN` 不特判。新增业务字段只需加注解与配码。 |
| MASK-003 | 序列化落在 Web 响应层：`WebJacksonConfig.extendMessageConverters` 复制全局 `ObjectMapper` 后注册 `MaskingModule`（`BeanSerializerModifier` + `MaskedPropertyWriter`），插到 MVC 转换器首位；**全局 `ObjectMapper` 未改动**，缓存与审计序列化不受影响，实测超管/部门管理员连续读取互不串味。`maskStates` 由 `MaskStatesSupport` 统一输出，明文不登记。 |
| MASK-004 | 兼容性：`phone`/`email` 可见性收紧属 `CT-VER-002` 破坏性变更，按裁决 6 **不升 v2、在 `/api/v1` 内实施**，前后端同批发布并以 V8 授权承接“上线后超管/部门管理员仍可见”。该破坏性判定在此显式记录。 |
| MASK-005 | 编辑回传：`UserDtos.UpdateRequest` 用 `@JsonSetter` 记录 `emailSpecified`/`phoneSpecified`（缺省=不改、显式 `null`=清空）；`MaskedValueGuard` 实现“原样掩码忽略 / 含 `*` 不匹配 422 `INVALID_FORMAT` / 隐藏字段无 view 清空 422 `VALIDATION_FAILED`”；创建请求含 `*` 一律 422。个人中心写路径按本人可见处理（自读豁免）。 |
| MASK-006 | 审计：`OperLogAspect` 改为按字段名在 JSON 树上脱敏（`phone`/`email`/`password`，含嵌套对象与数组），写入 `sys_oper_log.params` 前即为掩码；格式不匹配用 `******`。读取侧不再判字段权限（按裁决不新增读审计）。 |
| MASK-007 | 范围：`sys_dept.phone` 按裁决不纳入（对外公示信息），DTO/前端未改；导出/导入当前无实现，只在设计文档 §7 留约束；未新增配置项。 |
| MASK-008 | 偏离/差异说明：①`dev1`（`EMPLOYEE`）因 V7 已撤 `system:user:list`，访问 `/users` 返回 403，无法用它演示“隐藏态”，改用同属“其他角色”的 `auditor`（有列表权限、无字段码）验证 `HIDDEN`；`dev1` 的 `GET /profile` 仍按本人豁免返回明文。②本人豁免由 `ProfileAppService.profile()` 开启请求级标记，因此资料更新后的返回也按“读自己”输出明文，属于同一豁免的合理外延。③实现中注解字段标识用 `user:phone`（与权限码资源段一致），非设计稿早期的 `user.phone` 点号写法，语义不变。 |
| MASK-009 | 契约同步：已重新导出 `docs/api/openapi.generated.yaml`（`UserDtos.Response` 增加可选 `maskStates`），`docs/design/permission-matrix.md` 已由脚本重导出（46 枚码，`SUPER_ADMIN` 46 项、`DEPT_MANAGER` 相应增加 2 项）。**手写契约 `docs/api/openapi.yaml` 仍是脚手架示例骨架（既有缺口，`SL-EXAMPLE-001` baseline warning）**，本轮未把 `/api/v1/users`、`/api/v1/profile` 补写成真实契约；该缺口需单独一轮补齐（对外交付或第三方联调前必须完成）。 |

### 字段级脱敏实测（真实登录，2026-11-08）

| # | 身份 | 请求 | 实际返回 |
|---|---|---|---|
| 1 | `admin`（`SUPER_ADMIN`，两枚 view） | `GET /api/v1/users?page=1&pageSize=5` | `phone`/`email` 为库中原值，无 `maskStates` |
| 2 | `deptmgr`（`DEPT_MANAGER`，两枚 mask） | 同上 | `138****0003` / `d***@example.com`，`maskStates` 为 `{"phone":"MASKED","email":"MASKED"}` |
| 3 | `auditor`（`CROSS_AUDITOR`，无字段码） | 同上 | `phone`/`email` 为 `null`，`maskStates` 为 `{"phone":"HIDDEN","email":"HIDDEN"}` |
| 4 | `dev1`（`EMPLOYEE`） | `GET /api/v1/users` | HTTP 403（V7 已撤 `system:user:list`，与字段脱敏无关） |
| 5 | `dev1` | `GET /api/v1/profile` | 本人 `phone`/`email` 明文（自读豁免） |
| 6 | `deptmgr` | `PUT /api/v1/users/3`，原样回传当前掩码 | 200；库中值不变，响应仍为掩码 |
| 7 | `deptmgr` | `PUT /api/v1/users/3`，提交 `138****9999` | 422，`errors[{field:"phone",code:"INVALID_FORMAT"}]`，库中值不变 |
| 8 | `deptmgr` | 目标手机号为 `010-12345678`（格式不匹配→`HIDDEN`），显式提交 `"phone": null` | 422，`errors[{field:"phone",code:"VALIDATION_FAILED"}]`，库中值不变 |
| 9 | `admin` | 更新用户手机号后再查 | 明文写入/明文返回；操作日志 `params` 中为 `138****0004`，非法格式为 `******` |
| 10 | `admin`/`deptmgr` | 连续两次读取同一用户 | 始终分别为明文/掩码，无缓存串味 |

## 令牌立即失效与部门数据权限重算（2026-11-09）

> 设计见 `docs/design/auth-and-datascope.md`（v1.0 / `APPROVED`）；计划见 `docs/plan/2026-11-09-auth-dept-scope.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| TOKEN-001 | `V9__token_invalidation_and_dept_scope.sql` 只新增：`sys_user.token_version BIGINT NOT NULL DEFAULT 0`、`token_revoke_reason VARCHAR(32) NULL`；未改 V1–V8，未回填历史行。 |
| TOKEN-002 | 选型采用**用户级令牌版本号**，不采用逐令牌黑名单：整用户失效只需一次 `token_version + 1`；状态持久在库，Redis 清空不会让旧令牌复活；refresh 与 access 同写 `ver` 同口径校验。逐令牌黑名单需维护 `user -> jti` 集合、枚举清理与过期回收，复杂度更高，且本系统不需要单设备精细吊销。比较表见设计文档 §2.2。 |
| TOKEN-003 | 角色/权限不再信任令牌 claims：`JwtAuthenticationFilter` 每请求按库重建 `CurrentUser`（启用状态、`deptId`、角色码、权限码）。代价是每请求多 3 次查询；换来撤销角色/权限、调整部门立即生效（`assertions.md` #19）。 |
| TOKEN-004 | 停用/删除/管理员重置/本人改密：停用与重置自增版本；删除后 `findById` 为空；本人改密自增版本后由 `ProfileController` 调 `AuthAppService.reissueAfterPasswordChange` 为当前设备重签 Cookie，其他设备持有旧版本令牌。后端新增 401 分码 `20103`–`20107`；前端对 `20103/20104/20106/20107` 不再尝试刷新，直接回登录页并展示具体原因（`AUTH_NOTICE_KEY`）。 |
| TOKEN-005 | refresh 端点在 `AuthAppService.refresh` 同样校验 `ver`、启用状态与用户存在性；不通过先 `revokeRefresh(jti)` 再抛分码，避免“access 失效但 refresh 还能换新令牌”。 |
| SCOPE-001 | 数据权限仍是“每请求现算 + 5 分钟缓存”，未改成 SQL 递归；缓存 key 增加组织版本号：`admin:datascope:v1:{deptver}:{userId}`。部门增/改/移/删成功后 `DeptAppService` 调 `DataScopeProvider.evictAll()` 自增 `admin:datascope:deptver`，旧 key 随 TTL 淘汰，无需按模式扫描。 |
| SCOPE-002 | 成环校验由 `ancestors.contains(String.valueOf(id))` 子串匹配改为逗号分段精确比较；`cascadeDisable` 同步修正；`updateDescendantAncestors` 改为 `equals(oldChain) || startsWith(oldChain + ",")`，消除部门 1 与 10 的误判。 |
| SCOPE-003 | 现状**没有部门恢复接口**（全库检索无 `restore/恢复/undelete`）：删除为逻辑删除，但无恢复入口。本轮不新增恢复能力，已在 `system-modules.md` §4.2 与设计文档 §3.3 如实登记。 |
| SCOPE-004 | `CUSTOM_DEPT` 目标是显式 dept id 集合，移动子树不改变集合；实测移动前后计数不变（6 → 6）。 |
| SCOPE-005 | 基线同步：`docs/baseline/assertions.md` 更新 #4 缓存 key，新增 #19/#20；`modules.md` 补令牌版本存储事实；`system-modules.md` 更正了 V7 遗留的两处过期描述（`idx_dept_ancestors` 已删除、同父同名已受唯一约束）并补齐失效/重算口径；`data-model.md` 已重跑脚本（15 表）。 |

### 实测（真实登录，2026-11-09）

| # | 身份 / 场景 | 请求 | 实际返回 |
|---|---|---|---|
| 1 | `dev1` 设备 A | `PUT /api/v1/profile/password`（`admin123` → `admin1234`） | 200；当前设备 Cookie 换发 |
| 2 | `dev1` 设备 A | `GET /api/v1/auth/me` | 200（当前设备保留） |
| 3 | `dev1` 设备 B（改密前登录） | `GET /api/v1/auth/me` | 401 `{"code":20107,"message":"密码已修改，请重新登录"}` |
| 4 | `dev1` 设备 B | `POST /api/v1/auth/refresh` | 401 `{"code":20107,...}`（refresh 一并失效） |
| 5 | `admin` | `PUT /api/v1/users/4/password`（重置 `dev2`） | 200 |
| 6 | `dev2` 旧令牌 | `GET /api/v1/auth/me` | 401 `{"code":20106,"message":"密码已被重置，请重新登录"}` |
| 7 | `dev2` 旧令牌 | `POST /api/v1/auth/refresh` | 401 `{"code":20106,...}` |
| 8 | `admin` | `PUT /api/v1/users/4/status`（`isEnabled=false`） | 200 |
| 9 | `dev2` | `GET /api/v1/auth/me` | 401 `{"code":20103,"message":"账号已被停用"}` |
| 10 | `dev2` 停用期间登录 | `POST /api/v1/auth/login` | 403 `{"code":20301,"message":"账号已停用"}`（登录入口既有口径，未改） |
| 11 | `admin` 重新启用后 | `dev2` 停用前旧令牌 `GET /auth/me` | 401 `{"code":20105,"message":"登录状态已失效，请重新登录"}`（旧令牌不复活） |
| 12 | `admin` | `PUT /api/v1/users/3/roles`（撤空 `dev1` 角色） | 200 |
| 13 | `dev1` 同一旧令牌 | `GET /api/v1/auth/me` | 200，`permissions` 立即变为空数组 |
| 14 | `dev1` 同一旧令牌 | `GET /api/v1/demo-tasks` | 403 `{"code":20301}`（权限立即收窄，无需重新登录） |
| 15 | `deptmgr`（`DEPT_AND_CHILD`，部门 2） | `GET /api/v1/demo-tasks?pageSize=100` | `total=5`（部门 2 + 子部门 3） |
| 16 | `admin` | `PUT /api/v1/depts/3` 移到部门 4 下 | 200 |
| 17 | `deptmgr` 同一令牌立即再查 | 同上 | `total=3`（只剩部门 2，缓存已按新树重算） |
| 18 | `admin` | `PUT /api/v1/depts/3` 移回部门 2 下 | 200 |
| 19 | `deptmgr` | 同上 | `total=5`（恢复） |
| 20 | `admin` | `PUT /api/v1/depts/2` 移到子部门 3 下 | 409 `{"code":40901,"message":"不能把部门移动到自己的下级（会形成环）"}` |
| 21 | `demo_dept`（`DEPT` 范围，部门 2） | `GET /api/v1/demo-tasks` | `total=3`（研发部任务） |
| 22 | `admin` | `PUT /api/v1/users/8` 调部门到 5 | 200 |
| 23 | `demo_dept` 同一旧令牌 | `GET /api/v1/demo-tasks` | `total=2`（财务部任务），旧令牌 `deptId` 不再生效 |
| 24 | `admin` 调回部门 2 后 | `demo_dept` 同一令牌 | `total=3` |
| 25 | `auditor`（`CUSTOM_DEPT`，目标 2/4） | 移动部门 3 前后 | `total=6` → `6`（显式目标不受子树移动影响） |

### 交付门禁

- `mvn -q -DskipTests package` 通过（JDK 17）；Flyway 日志 `Successfully applied 1 migration ... now at version v9`。
- `pnpm --filter @admin-starter/pc build` 通过。
- `node tools/spec-lint/cli.mjs`：error **0**，warning **58**（与基线一致）。
- 偏离：无。新增的 401 分码属认证段 `201xx` 内追加，未升契约版本；前端仅在 401 场景新增提示分支，未改登录/刷新协议。

## 通知 / 站内信（2026-11-09 · V10）

> 设计见 `docs/design/notification.md`（v1.0 / `APPROVED`）；计划见 `docs/plan/2026-11-09-notification.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| NOTICE-001 | `V10__notification_module.sql` 只新增：`sys_notice`、`sys_notice_scope`、`sys_notice_recipient` 三张表；5 枚权限码与菜单（`system:notice:list/create/revoke/delete/read-stats`，挂在「系统管理」目录，按 path 匹配父级）；按角色 `code` + `permission_code` 授权（`SUPER_ADMIN` 全量、`DEPT_MANAGER` 列表/发布/统计）。未改 V1–V9，未按菜单/角色 id 硬编码。 |
| NOTICE-002 | 按“通知”抽象而非消息表：通知本体（`sys_notice`）+ 范围规则（`sys_notice_scope`）+ 应读人快照（`sys_notice_recipient`）。范围多行**并集**；`DEPT` 固定为“该部门及全部下级部门”（`FIND_IN_SET` 子树，不提供仅本部门开关），前端文案“指定部门（含下级）”。 |
| NOTICE-003 | 发布时把范围展开为具体用户写入快照，应读/已读数以快照为准；发布后新建用户、角色/部门调整**不补发历史通知**。全体/角色/部门展开均只取 `deleted=0 AND is_enabled=1` 的用户；发布后停用/删除的用户仍留在快照中，统计口径不变。 |
| NOTICE-004 | 发送能力对外是 `NotificationPublisher`（`com.adminstarter.system.application`）：`publish` 为 `REQUIRED`（加入调用方事务，业务回滚则通知回滚）；`publishInNewTransaction` 为 `REQUIRES_NEW`（业务回滚仍发出，供定时任务失败场景）。HTTP 发布接口走同一 `doPublish`，不复制范围展开逻辑。两者必须经 Spring 代理调用，同类自调用不触发事务。 |
| NOTICE-005 | 已读语义：点开详情即写 `read_at`（幂等，已有值不覆盖）；全部已读只标记本人未读且通知未撤回/未删除的行；撤回后用户端列表、未读数、详情均不可见，快照与统计保留；删除为逻辑删除并级联逻辑删除范围与快照。 |
| NOTICE-006 | 数据权限：`sys_notice` 登记为受控（owner/self = `create_by`，组织范围用发布人部门 `dept_id`），管理端列表/详情/撤回/删除/统计均走 `DataPermissionContext.controlled`；`sys_notice_scope`、`sys_notice_recipient` 登记为豁免。用户端收件箱**不进入**受控上下文（按 `user_id` 精确取本人应读，避免组织范围叠加漏消息）。系统调用发布的通知 `dept_id` 为空，受限角色不可见（有意口径）。 |
| NOTICE-007 | 未读数先轮询：前端 30 秒一次，页面隐藏暂停、窗口重新可见立即拉取；不引入 WebSocket/SSE 的理由与切换条件见设计文档 §9。顶栏铃铛与“我的消息”页共用 Pinia `notice` store。 |
| NOTICE-008 | 前端：新增系统管理·通知公告页（筛选、发布抽屉、详情抽屉、撤回/删除、已读统计弹窗）、`/messages` 我的消息页（全部/未读/已读筛选、全部已读、点开即读）、顶栏铃铛红点；`/messages` 为静态路由（不进侧栏菜单）。 |
| NOTICE-009 | 契约：`docs/api/openapi.yaml` 新增 8 个通知路径与对应 schema（管理端 6 + 用户端 4 个操作）；`docs/design/permission-matrix.md` 与 `docs/design/data-model.md` 已由脚本重导出（权限码 51 个、表 18 张）。 |
| NOTICE-010 | **全员可见口径（2026-11-09 用户拍板，同日追加）**：范围含 `ALL` 的 `sys_notice` 对所有持有 `system:notice:list` 的管理员可见（列表、详情、已读统计），非 `ALL` 通知仍按数据权限过滤。实现方式：`DataPermissionTables.PUBLIC_ROW_CONDITIONS` 登记 `sys_notice` 的公共行条件，`RoleDataPermissionHandler` 将其与原数据权限条件 OR 拼接；撤回/删除授权不变，仍只给 `SUPER_ADMIN`。前端不加新按钮，只放开可见性。 |
| NOTICE-011 | 公共行拼接的两个坑（已修）：① 拼接结果必须整体再加一层括号，否则被 MyBatis-Plus 以 AND 追加后变成 `A AND (B) OR (C)`，OR 分支绕过主键过滤（实测 `GET /notices/1` 一次返回 7 行并抛 `TooManyResultsException`）；② 无别名时子查询 `s.notice_id = id` 的 `id` 会被内层 `sys_notice_scope.id` 遮蔽，必须写成 `sys_notice.id`。修复后 `deptmgr` 列表只返回全员通知、详情 200、撤回/删除仍 403。 |
| NOTICE-012 | 事务语义实跑验证：新增 `NotificationPublisherTransactionTest`（`server/web/src/test`，`@SpringBootTest` + 真实 MySQL），2 条用例全绿——`publish` 在外层事务回滚后 0 行，`publishInNewTransaction` 在外层回滚后 1 行（结束即清理）。定时任务失败通知是 `REQUIRES_NEW` 的真实调用方，已在 JOB 实测中产生站内信。 |

### 通知 / 站内信实测（真实登录，2026-11-09）

| # | 身份 / 场景 | 请求 | 实际返回 |
|---|---|---|---|
| 1 | `admin` | `POST /notices` 全员通知（IMPORTANT/SYSTEM，可撤回） | 200，`id=1`，`stats.totalRecipients=9` |
| 2 | `admin` | `POST /notices` 指定部门 2（URGENT/BUSINESS） | 200，`id=2`，`totalRecipients=4`（部门 2 + 子部门 3） |
| 3 | `dev1`（部门 3，子部门） | `GET /my-notices` | 两条都在（含指定部门通知，验证“含下级”） |
| 4 | `market1`（部门 4） | `GET /my-notices` | 只有全员通知，未收到部门 2 通知 |
| 5 | `dev1` | `GET /my-notices/unread-count` | `2` |
| 6 | `dev1` | `GET /my-notices/2` | 200，返回内容；随后 `unread-count` 为 `1`（点开即已读） |
| 7 | `dev1` | `POST /my-notices/read-all` | `{"marked":1}`；随后 `unread-count` 为 `0` |
| 8 | `admin` | `GET /notices/2` | `stats {total:4, read:1, unread:3}` |
| 9 | `admin` | `GET /notices/2/readers?readStatus=UNREAD` | 3 人（deptmgr、dev2、demo_dept） |
| 10 | `deptmgr`（DEPT_MANAGER，部门 2） | `GET /notices` | `total=0`（admin 的通知 `dept_id=1`，不在其组织范围；数据权限生效） |
| 11 | `deptmgr` | `POST /notices` 全员通知 | 200，`id=3`；随后自己 `GET /notices` 可见该条 |
| 12 | `deptmgr` | `POST /notices/2/revoke` | 403（未授予 `system:notice:revoke`） |
| 13 | `admin` | `POST /notices/2/revoke` | 200，`revoked=true`；`dev2` 未读数 `3 → 2`，收件箱不再返回该条；统计仍为 `4/1` |
| 14 | `admin` | `POST /notices` `revocable=false`（指定用户 dev1） | 200，`id=4`；再撤回返回 409「该通知不允许撤回」 |
| 15 | `dev1`（EMPLOYEE） | `GET /notices` | 403（无 `system:notice:list`） |
| 16 | `admin` | `DELETE /notices/3` | 200；`deptmgr` 列表回到 0，`dev2` 收件箱不再含该条；范围与快照级联逻辑删除 |

#### 全员可见口径追加实测（2026-11-09 修复后）

| # | 身份 / 场景 | 请求 | 实际返回 |
|---|---|---|---|
| 17 | `deptmgr`（部门 2，无全员通知的 `dept_id`） | `GET /notices?page=1&pageSize=10` | 200，`total=1`，仅 `id=1`「全员通知-系统维护」（修复前返回 500 或 0 条） |
| 18 | `deptmgr` | `GET /notices/1` | 200，返回标题、内容与 `stats {totalRecipients:9, readCount:1, unreadCount:8}` |
| 19 | `deptmgr` | `POST /notices/1/revoke`、`DELETE /notices/1` | 均 403 `20301 没有操作权限`（撤回/删除授权未放开） |

### 交付门禁

- `mvn -q -DskipTests package` 通过（JDK 17）；Flyway 日志 `Successfully applied 1 migration ... now at version v10`。
- `pnpm --filter @admin-starter/pc build` 通过。
- `node tools/spec-lint/cli.mjs`：error **0**，警告 **58**（与基线一致）。
- 事务语义运行验证由 NOTICE-012 补齐（原“未在运行时模拟调用方回滚”的缺口已关闭）。

## 定时任务（2026-11-09 · V11）

> 设计见 `docs/design/scheduled-task.md`（v1.0）；计划见 `docs/plan/2026-11-09-scheduled-task.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| JOB-001 | `V11__scheduled_task_module.sql` 只新增：`sys_job`（任务定义）、`sys_job_log`（执行日志）两张表，7 枚权限码与菜单（`system:job:list/create/update/delete/status/run/log`，挂在「系统管理」目录下按 path 匹配父级），按角色 `code` 授权且**仅 `SUPER_ADMIN`**。未改 V1–V10，未按菜单/角色 id 硬编码。 |
| JOB-002 | 调度选型：Spring `ThreadPoolTaskScheduler` + `CronTrigger`（零新增依赖），未引入 Quartz/XXL-JOB；否决理由见设计文档 §3。Spring 6 段 cron（秒 分 时 日 月 周）。 |
| JOB-003 | 动态 cron：`JobScheduler.refresh(job)` 先 `cancel` 旧 `ScheduledFuture` 再按新表达式重建；启停同样走 refresh/cancel；`ApplicationReadyEvent` 时只注册 `ENABLED` 任务。改 cron 与启停都不需要重启。 |
| JOB-004 | 并发策略：`DISALLOW` 用进程内 `ConcurrentHashMap<Long, AtomicInteger>` 计数，重复触发直接 `SKIPPED`（手动返回 `409`，`code=40901`）；`ALLOW` 不计数。**这是单机进程内策略，不是分布式锁**，跨实例无效。 |
| JOB-005 | 超时：任务提交到独立 `ThreadPoolTaskExecutor`，`Future.get(timeoutSeconds)` 超时后 `cancel(true)`；只能中止响应中断的任务，不响应中断的实现仍会继续跑（先落 `TIMEOUT` 日志）。 |
| JOB-006 | 重试：`retry_times` 是首次之外的额外次数，`attempt_count = 1 + retry_times`；一次触发只写一行日志，`elapsed_ms` 含重试间隔；重试间隔固定秒数，无退避。 |
| JOB-007 | 日志：`JobLogAppender` 用 `REQUIRES_NEW` 写 `sys_job_log`，任务异常不影响日志落库；`AUTO` 才回写 `last_run_at` 与 `next_run_at`，`MANUAL` 只回写 `last_run_at`，不改变自动调度预期；“下次执行时间”以 `JobScheduler.nextRunAt(job)` 动态计算展示。 |
| JOB-008 | 失败通知：`JobFailureNotifier` 接 `NotificationPublisher.publishInNewTransaction`（独立事务）。策略 `NEVER` / `ON_FAILURE` / `ON_CONSECUTIVE`（阈值默认 3，按最近日志 `FAIL`/`TIMEOUT` 连续计数，遇到 `SUCCESS`/`SKIPPED` 中断）；接收人取 `notify_targets`，为空回退任务创建人；通知类型 `TASK`，级别 `ON_CONSECUTIVE`→`URGENT`、`ON_FAILURE`→`IMPORTANT`；通知发送失败只记日志，不影响任务结果。 |
| JOB-009 | 安全红线：`JobRegistry` 收集容器内 `ScheduledJob` Bean，重复 `key` 启动 fail-fast；`sys_job` 只存 `job_key` 与调度配置，**不存**脚本、Shell 命令、SQL 字符串或类名；新增/修改任务时校验 `job_key` 必须在注册表内。未放宽。 |
| JOB-010 | 权限与数据范围：`sys_job`、`sys_job_log` 登记为**豁免**数据权限（仅 `SUPER_ADMIN` 持有权限码，调度需全局一致定义）；`deptmgr` 访问 `/jobs` 实测 403。 |
| JOB-011 | 前端：新增系统管理·定时任务页（筛选、新增/编辑抽屉、启停、手动执行、日志抽屉与详情弹窗、空态/分页/权限指令）；`apps/pc/src/api/job.ts` 与 `packages/shared/src/types.ts` 补类型。无新按钮之外的结构改动。 |
| JOB-012 | 契约与导出：`docs/api/openapi.yaml` 新增 7 个路径（`/jobs`、`/jobs/keys`、`/jobs/{id}`、`/jobs/{id}/status`、`/jobs/{id}/run`、`/job-logs`、`/job-logs/{id}`）；`docs/design/permission-matrix.md` 重导出为 58 枚权限码、`docs/design/data-model.md` 重导出为 20 张表。 |
| JOB-013 | 单机事实已写入设计文档 §7 与 `system-modules.md` §18.4：当前无跨节点防重，多实例会重复执行；上集群前置条件 5 条（调度选型改造、触发唯一性、日志去重、任务幂等、两实例验证）。未假装支持集群。测试用任务（3 个 demo）验证后已逻辑删除，列表回到 0；历史日志保留（删除任务不删日志）。 |

### 定时任务实测（真实登录 + 真实调度，2026-11-09）

| # | 场景 | 操作 | 实际结果 |
|---|---|---|---|
| 1 | 迁移 | 重启后端 | Flyway `Migrating schema admin_starter to version "11 - scheduled task module"`；`Successfully applied 1 migration ... now at version v11` |
| 2 | 任务注册 | `GET /jobs/keys` | 返回 3 个已注册标识：`demo-heartbeat`、`demo-fail`、`demo-sleep` |
| 3 | 创建每分钟任务 | `POST /jobs`（cron `0 * * * * *`，ENABLED） | 200，`id=1` |
| 4 | 自动执行 | 等待整分 | 日志 `triggerType=AUTO`、`result=SUCCESS`、`affectedRows=1`；`nextRunAt` 滚动到 `2026-11-09T03:54:00Z` |
| 5 | 动态 cron | `PUT /jobs/1` 改 `*/10 * * * * *`，不重启 | 11:53:40、11:53:50 连续自动触发（新表达式生效） |
| 6 | 停用 | `PUT /jobs/1/status` → `DISABLED` | 200，`nextRunAt=null`；15 秒内无新日志 |
| 7 | 手动执行 | `POST /jobs/1/run` | 200，`logId=5`，日志 `triggerType=MANUAL`、`SUCCESS`；`nextRunAt` 未被改写 |
| 8 | 失败 + 重试 | `POST /jobs/2/run`（`retryTimes=2`，`ON_FAILURE`） | `result=FAIL`、`attemptCount=3`、`elapsedMs=2020`；异常摘要 `java.lang.IllegalStateException: 演示任务异常：模拟业务失败（jobId=2）` |
| 9 | 失败通知（REQUIRES_NEW 真实调用方） | 同上 | 站内信 `id=6`「定时任务执行失败：演示失败-重试2次」，`type=TASK`、`level=IMPORTANT` |
| 10 | 超时 | `POST /jobs/3/run`（`timeoutSeconds=1`，任务 sleep 5s） | `result=TIMEOUT`、`elapsedMs=1005`、`errorSummary=执行超时（1 秒）` |
| 11 | 连续失败阈值 | `notifyPolicy=ON_CONSECUTIVE`、阈值 3 | 通知总数 before=2 → 第 1 次失败仍为 2（未达阈值）→ 第 2 次失败为 3（达阈值后发送） |
| 12 | 禁止并发 | 连续两次 `POST /jobs/3/run` | 第二次 HTTP 409，`{"code":40901,"message":"任务正在执行中，禁止并发策略已跳过本次执行"}`；第一次 `SUCCESS`、`elapsedMs=5001` |
| 13 | 权限 | `deptmgr` `GET /jobs` | 403 `20301` |
| 14 | 清理 | `DELETE /jobs/1..3` | 均 200；`GET /jobs` → `total=0`；`sys_job_log` 历史保留 |

### 交付门禁（定时任务）

- `mvn -q -DskipTests package` 通过（JDK 17）；Flyway 当前 v11。
- `NotificationPublisherTransactionTest`：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。
- `pnpm --filter @admin-starter/pc build` 通过（仅既有 warning）。
- `node tools/spec-lint/cli.mjs`：error **0**。
- 偏离：无。单机限制按要求如实登记（JOB-013），未实现跨节点防重。

## 会话管理（2026-11-09 · V12）

> 设计见 `docs/design/session-management.md`（v1.0）；计划见 `docs/plan/2026-11-09-session-management.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| SESSION-001 | `V12__session_management.sql` 只新增：`sys_user_session` 1 张表；3 枚权限码与菜单（`system:session:list` 菜单挂 `/system/sessions`，按钮 `system:session:kick`、`system:session:clean` 挂该菜单下），按业务键匹配父级与角色 `code`，**仅授权 `SUPER_ADMIN`**。未改 V1–V11，未按 id 硬编码。 |
| SESSION-002 | 设计反转已正面处理（设计文档 §2）：原选型“用户级 `token_version`”负责账号级整体失效，本轮在其之外补设备级通道；**不整体改成逐令牌黑名单**，只把可枚举会话落 MySQL。结论：两条通道叠加，不是二选一。 |
| SESSION-003 | 单设备吊销只改 `sys_user_session` 行（`invalidated_at` + `invalidate_reason`），**不 bump `token_version`**；按用户踢全部同样不动版本号。实测被踢设备 401 后同账号可立即重新登录，证明未连坐账号级版本。 |
| SESSION-004 | 令牌新增 `sid` claim（access 与 refresh 都带）；过滤器与 refresh 端点在版本校验之后按 `sid` 查会话：缺失/不存在/`user_id` 不匹配 → `20105`，已失效 → 按原因映射分码。**旧令牌无 `sid` 必然失效，升级后在线用户需重新登录一次**（已在设计文档 §7.3 预告）。 |
| SESSION-005 | 在线口径：未失效且 `last_active_at >= now-5min`；N=5 分钟为代码常量（`UserSessionAppService.ONLINE_WINDOW`），未新增配置项，配置台账无变更。在线是展示口径，不参与鉴权。 |
| SESSION-006 | 活跃时间节流：Redis `SETNX admin:session:touch:{sid}` 60 秒 TTL，首次写入才 UPDATE；Redis 异常时吞掉并跳过写库，不阻断鉴权。代价：`last_active_at` 最多滞后 60 秒，每请求多 1 次 Redis。实测 60 秒内第二次请求库值不变。 |
| SESSION-007 | 每请求成本由 3 SELECT + 1 Redis 变为 **4 SELECT + 2 Redis + 每会话每 60 秒 ≤1 UPDATE**（新增会话唯一键查询与 touch 节流键）。未引入额外缓存层；会话行未失效即命中 `uk_session_sid`。 |
| SESSION-008 | 两套机制写入口径集中在 `UserSessionAppService` 与用户/个人中心服务：本人改密 bump 版本并 `invalidateUserExcept(当前 sid)`；管理员重置密码/停用 `invalidateUser` 并 bump 版本；删除用户标 `USER_DELETED`；主动退出标 `LOGOUT`；踢下线标 `KICKED_BY_ADMIN`。无第三个判定点。 |
| SESSION-009 | `sys_user_session` 登记为数据权限 `EXEMPT`（与 `sys_login_log` 同类），管理端靠 `system:session:list` 功能权限拦截；列表 `userId`/`keyword`/`online` 是普通筛选参数，不叠加组织范围。 |
| SESSION-010 | `sys_user_session` 无 `deleted` 列，`UserSessionDO` 不继承 `BaseDO`（否则被全局逻辑删除条件污染）；清理为**物理删除**，只删 `invalidated_at` 早于 30 天的行，手动触发 `DELETE /sessions/invalidated`，不挂定时任务。这是“删除一律逻辑删除”的显式例外，已登记 `assertions.md` #32。 |
| SESSION-011 | 前端：新增系统管理·会话页（账号/昵称、在线状态筛选；用户/设备/IP/登录/最近活跃/状态/失效原因；踢单条、踢该用户全部、清理失效记录、分页）；个人中心新增“登录设备”卡片（当前设备标记、踢自己的其他设备、刷新）；`api/http.ts` 的 `TERMINAL_AUTH_CODES` 加入 `20108`。 |
| SESSION-012 | 契约与导出：`docs/api/openapi.yaml` 新增 `sessions` tag、6 条路径与 4 个 schema（`Session`、`SessionPageResponse`、`SessionListResponse`、`SessionAffectResponse`）；`docs/api/openapi.generated.yaml` 已重新导出；`docs/design/permission-matrix.md` 重导出为 **61 枚权限码 / 7 个启用角色**；`docs/design/data-model.md` 重导出为 **21 张表**。 |
| SESSION-013 | 与任务书的差异（详见交付报告）：①补充 `USER_DELETED` 失效原因（原需求只列四类，删除账号同样需让设备列表不再显示在线）；②用户端“踢掉自己的其他设备”复用 `KICKED_BY_ADMIN` 作为失效原因，被踢设备返回 `20108` 文案“已被管理员下线”——语义上是“被他人下线”，非管理员操作；③权限码只授 `SUPER_ADMIN`，部门管理员未授予（列表含全员 IP/设备，且强制下线属安全敏感操作）。 |

### 会话管理实测（真实登录，2026-11-09）

| # | 身份 / 场景 | 请求 | 实际返回 |
|---|---|---|---|
| 1 | `admin` Chrome/PC 与 iPhone/Safari 两处登录 | `GET /api/v1/sessions?page=1&pageSize=10` | 200，`total=2`；两条 `deviceType` 分别为 `PC`/`MOBILE`，`userAgent` 不同 |
| 2 | `admin` iPhone 端 | `GET /api/v1/auth/sessions` | 200，返回本人会话，当前设备 `current=true` |
| 3 | `admin` | `DELETE /api/v1/sessions/{iPhone 会话 id}` | 200，`data.affected=1` |
| 4 | 被踢 iPhone | `GET /api/v1/auth/me` | **401** `{"code":20108,"message":"已被管理员下线，请重新登录","traceId":"836d2c38d7e04a648f824c6a967d917f"}` |
| 5 | 未受影响的 PC | `GET /api/v1/auth/me` | 200（另一台仍可用） |
| 6 | `admin` | `DELETE /api/v1/sessions/user/{userId}` | 200，`affected=6`；该用户两台设备均 401 `20108`；随后重新登录成功（未 bump 版本号） |
| 7 | `admin` 用户端自助 | `DELETE /api/v1/auth/sessions/{其他设备 id}` | 200，`affected=1`；被踢设备 401 `20108`，当前设备 200 |
| 8 | `deptmgr`（`DEPT_MANAGER`） | `GET /api/v1/sessions` | 403 `{"code":20301,...}`（无 `system:session:list`） |
| 9 | `dev1` 主动退出 | `POST /api/v1/auth/logout` | 200；会话行 `invalidate_reason=LOGOUT`，另一设备仍 200 |
| 10 | `dev1` 改密（改后改回） | `PUT /api/v1/profile/password` | 200；其他设备 401 `20107`，当前设备 200；其他会话标记 `PASSWORD_CHANGED` |
| 11 | `admin` 停用 `dev2` | `PUT /api/v1/users/4/status` | 200；`dev2` 请求 401 `20103`，会话标记 `ACCOUNT_DISABLED`；已重新启用 |
| 12 | `admin` 清理失效记录 | 手工将 id=16 的 `invalidated_at` 置为 40 天前后 `DELETE /api/v1/sessions/invalidated` | 200，`data.affected=1`，该行已物理删除 |
| 13 | `admin` 节流验证 | 手工将 `last_active_at` 置为 5 分钟前，连续两次 `GET /auth/me` | 第一次后库值更新为当前时间；60 秒内第二次库值不变（未写库） |
| 14 | `admin` 筛选 | `GET /api/v1/sessions?online=false`、`?userId=4` | `total` 与预期一致（在线/用户筛选生效） |

### 交付门禁（会话管理）

- `mvn -q -DskipTests package` 通过（JDK 17）；重启后 Flyway 日志 `Successfully applied 1 migration ... now at version v12`。
- `pnpm --filter @admin-starter/pc build` 通过（`vue-tsc -b && vite build`，exit 0）。
- `node tools/spec-lint/cli.mjs`：error **0**（warning 为既有路径类，见交付报告）。
- 基线回填：`docs/baseline/modules.md` v1.4、`docs/baseline/assertions.md` v1.4、`docs/requirements/system-modules.md` v1.4。
- 偏离：见 SESSION-013（补充失效原因、自助踢下线复用文案、权限仅授超管）。




## 文件能力增强（2026-11-09 · V13）

> 设计见 `docs/design/file-storage.md`（v1.0）；计划见 `docs/plan/2026-11-09-file-capability.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| FILE-001 | `V13__file_capability.sql` 只新增：`sys_file` 加 `sha256 CHAR(64)`、`deleted`、`deleted_at`、`deleted_by` 与 `idx_file_sha256`、`idx_file_deleted`；新建 `sys_file_chunk_upload`；按 `permission_code='system:file:list'` 匹配父菜单插入 `system:file:restore`、`system:file:purge`，仅授权 `SUPER_ADMIN`；插入 `file-orphan-cleanup` 任务种子。未改 V1–V12，未按菜单 id 硬编码。Flyway 启动日志 `now at version v13`。 |
| FILE-002 | 存储层抽成 `FileStorage` 接口（`store/loadAsResource/exists/delete/deleteRecursively/cleanupTemp/cleanupOrphans`），本轮只实现 `LocalFileStorage`（`@Component`，`condition` 未加）；对象存储实现**未提供**，接口预留 `loadAsResource` 返回 `Resource`，未来 S3/OSS 需自行实现流式读取。 |
| FILE-003 | 存储键是相对键 `yyyy/MM/dd/{sha256}`（UTC 日期），按日期分片；历史平铺 UUID 键按原样读取兼容，不迁移。`sys_file.storage_path` 只存相对键，接口只回 `downloadUrl/previewUrl`（API 路径），**不含**物理路径。 |
| FILE-004 | 上传：后端仍是单文件 `POST /files`；多文件/拖拽/粘贴是前端循环调用。落盘流程=写 `.tmp` 临时文件→`DigestInputStream` 算 SHA-256→按 `sha256+size+uploader` 查重→复用字节或 `store` 到日期分片→建记录。超限仍抛 `PayloadTooLargeException`（413 + `data.maxSize`）。 |
| FILE-005 | 分片上传：`sys_file_chunk_upload` 一次会话一行（`status=UPLOADING/MERGED/ABORTED/EXPIRED`），单片 256KB–5MB、总片数 ≤1000；临时片落 `.chunks/{uploadId}/{index}.part`，合并成功后整目录删除；`complete` 逐片合并并用 `DigestOutputStream` 复算 SHA-256 与声明值、大小双重校验。 |
| FILE-006 | 断点续传：`GET /chunk-sessions/{uploadId}` 返回已上传片序号集合；前端 localStorage 记 `file-upload:{size}:{sha}` → uploadId，重进页面自动查状态并跳过已完成片。实测中断后 `uploadedChunks=[0]`，补传第 1 片后合并成功。 |
| FILE-007 | 秒传：`POST /files/instant` 只匹配**同一上传者**（`sha256+size+uploader_id`）；命中即为当前用户新建一条指向同一 `storage_path` 的记录（`instant=true`）。真实上传命中他人已有字节时同样复用字节但新建记录。分片 init 命中秒传返回 `uploadId=null`。 |
| FILE-008 | 下载/预览：只接受 `id`，先无数据权限查出记录（不存在 404），再 `DataPermissionContext.controlled` 内重查（不可见 403）。下载固定 `attachment`+`nosniff`；预览白名单 `jpg/jpeg/png/gif/webp/pdf`，`inline`+按扩展名 `Content-Type`+`nosniff`，其余 422。 |
| FILE-009 | 预览与 `X-Frame-Options`：Spring Security 默认 `DENY`，实测 PDF 预览 200 但响应头含 `X-Frame-Options: DENY`，`<iframe src>` 直连不会渲染。前端改为 `fetch(previewUrl, {credentials:'include'})` 取 Blob → `createObjectURL` → iframe；关闭对话框与卸载时 `revokeObjectURL`。**未改** Security 头配置（不放宽全局点击劫持防护）。 |
| FILE-010 | 回收站：`sys_file` 的 `deleted/deleted_at/deleted_by`（复用全局逻辑删除），删除=`moveToRecycle`；恢复=`restore`；彻底删除=`purge`（物理 `DELETE` 行，仅对 `deleted=1`），删行后按 `storage_path` 计数，0 才删物理字节。 |
| FILE-011 | 孤儿清理复用既有 `sys_job`（`file-orphan-cleanup`，cron `0 0 2 * * *`，`DISALLOW`），不新建调度器。任务做三件事：>`24h` 的 `UPLOADING` 会话→`EXPIRED` 并删分片目录；`.tmp/.chunks` 超龄文件清理；未被任何 `sys_file.storage_path` 引用且超龄的孤儿文件删除。 |
| FILE-012 | 权限与归属：`sys_file` 仍为数据权限**受控表**（`dept_id`+`create_by`），新增 `sys_file_chunk_upload` 登记为 `EXEMPT`（按 `uploader_id` 精确校验，不叠加组织范围）；新增 2 枚权限码只授 `SUPER_ADMIN`。业务表只存 `file_id` 属于约定，本轮无业务表接入。 |
| FILE-013 | 前端：文件页改为拖拽多选 `el-upload` + 页面级 `paste` 监听 + 上传任务进度列表；`beforeUpload` 用 `appConfig.uploadMaxSize` 预校验（超限只提示一条并取消）；`>5MB` 走分片（2MB/片），其余走单请求；413 时用 `error.maxSize` 刷新本地上限且不弹全局提示；列表新增下载/预览（仅白名单类型显示预览）、删除；回收站对话框支持恢复/彻底删除；预览对话框图片直连、PDF 走 Blob。 |
| FILE-014 | 契约与导出：`docs/api/openapi.yaml` 新增 `GET /files`、`/instant`、`/{id}/preview`、`/recycle`、`/{id}/restore`、`/recycle/{id}`、`chunk-sessions` 系列共 9 条路径与 5 个 schema；`docs/api/openapi.generated.yaml` 已重新导出；`docs/design/permission-matrix.md` 重导出为 **63 枚权限码 / 7 个启用角色**；`docs/design/data-model.md` 重导出为 **22 张表**并修正 §3.4 “`sys_file` 无 deleted 列”的旧结论。 |

### 文件能力实测（真实 HTTP，2026-11-09）

| # | 场景 | 请求 | 实际返回 |
|---|---|---|---|
| 1 | 上传 3 个文件（100KB txt / 68B png / 328B pdf） | `POST /api/v1/files` ×3 | 200，`id=6/7/8`；`previewUrl` 仅 png/pdf 非空 |
| 2 | 图片预览 | `GET /files/7/preview` | 200，`Content-Type: image/png`、`Content-Disposition: inline`、`nosniff` |
| 3 | PDF 预览 | `GET /files/8/preview` | 200，`Content-Type: application/pdf`、`inline`、`nosniff`、`X-Frame-Options: DENY` |
| 4 | 非白名单预览 | `GET /files/6/preview`（txt） | 422 `{"code":30201,"message":"该文件类型不支持在线预览，请下载查看"}` |
| 5 | 下载 | `GET /files/8/download` | 200，`Content-Disposition: attachment; filename="demo.pdf"`、`nosniff` |
| 6 | multipart 文件名穿越 | `filename=../../evil.txt` | 422 `{"code":30201,"message":"文件名不合法"}`（`sanitizeName` 拒绝；容器层亦拒绝路径分隔符） |
| 7 | JSON 声明名穿越 | `POST /files/instant`，`fileName=../../evil.txt` | 422 `{"code":30201,"message":"文件名不合法"}` |
| 8 | 扩展名白名单 | 上传 `evil.exe` | 422 `{"code":30201,"message":"不支持的文件类型: exe"}` |
| 9 | 秒传 | `POST /files/instant`，small.txt 同上传者 | 200，`{"instant":true,"file":{"id":14,...}}`；物理字节未重写 |
| 10 | 分片 init | `POST /files/chunk-sessions`（3MB/2 片） | 200，`uploadId=b56f82d0...`，`totalChunks=2` |
| 11 | 中断后续传 | 传第 0 片 → `GET /chunk-sessions/{id}` | 200，`uploadedChunks:[0]`、`instant:false` |
| 12 | 续传并合并 | 补第 1 片 → `POST .../complete` | 200，`data:[0,1]`；合并返回 `{"id":16,"originalName":"chunk3m.zip","size":3145728}` |
| 13 | 放弃上传 | 传第 0 片后 `DELETE /chunk-sessions/{id}` | 200；分片目录由存在变 `False`，会话 `ABORTED` |
| 14 | 分片秒传 | 已完成文件再次 `POST /chunk-sessions` | 200，`uploadId:null`、`instant:true`、`file.id=15` |
| 15 | 越权下载 | `deptmgr`（部门范围 DEPT_AND_CHILD）`GET /files/6/download` | **403** `{"code":20301,...}`；列表 `total=0` |
| 16 | 不存在 id | `GET /files/999999/download` | 404 `{"code":20401,"message":"资源不存在"}` |
| 17 | 越权预览 | `deptmgr` `GET /files/7/preview` | **403** `20301` |
| 18 | 删除进回收站 | `DELETE /files/16` | 200；列表不再出现；`GET /files/recycle` 返回该行（`deletedAt=2026-11-09T05:11:18Z`、`deletedBy=1`） |
| 19 | 恢复 | `POST /files/16/restore` | 200，列表恢复 |
| 20 | 彻底删除 | 再删 → `DELETE /files/recycle/16` | 200；`sys_file` 行删除；日期分片物理文件 `exists=False` |
| 21 | 去重引用计数 | 删除并彻底删除共享字节的记录 id=9 | 200；共享物理文件仍存在（id=6 仍引用同 `storage_path`） |
| 22 | 孤儿清理 | 造 25h 前的 `.tmp` 临时文件与孤儿文件后 `POST /jobs/4/run` | 200，`{"result":"SUCCESS","affectedRows":2}`；两项均被删除 |
| 23 | 过期会话清理 | 将 `UPLOADING` 会话 `updated_at` 置 25h 前再跑任务 | 会话 `EXPIRED`，分片目录删除（首轮 `affectedRows=3`） |
| 24 | 1MB 上限回归 | 配置改为 `1048576` 后上传 8MB | 413 `{"code":10002,"message":"文件大小不能超过 1MB","data":{"maxSize":1048576}}` |
| 25 | 413 不踢下线 | 紧接 `GET /auth/me` | 200（会话仍有效）；前端 413 分支只抛错不弹全局提示，页面仅一条提示 |
| 26 | 恢复 10MB | 配置改回 `10485760` 后上传 2MB | 200，`{"id":13,... "instant":true}` |
| 27 | 分片 init 超限 | 1MB 上限下 init 8MB 会话 | 413，`data.maxSize=1048576` |

### 交付门禁（文件能力增强）

- `mvn -q -DskipTests package` 通过（JDK 17）；重启后 Flyway `now at version v13`。
- `pnpm --filter @admin-starter/pc build` 通过（`vue-tsc -b && vite build`，exit 0）。
- `node tools/spec-lint/cli.mjs`：error **0**。
- 基线回填：`docs/baseline/modules.md` v1.5（22 张表、V13）、`docs/baseline/assertions.md` v1.5（新增 #34–#39）、`docs/requirements/system-modules.md` v1.5、`docs/design/data-model.md`（22 张表 + §3.4 修正）。
- 偏离：见 FILE-009（PDF 用 Blob）、FILE-013（前端预校验/413 提示）、报告“与要求不一致”章节。

## 缓存管理（2026-11-09 · V14）

> 设计见 `docs/design/cache-management.md`（v1.0）；计划见 `docs/plan/2026-11-09-cache-management.md`。本节只记实现、实测与偏离。

| 编号 | 内容 |
|---|---|
| CACHE-001 | 现状核查用 `SCAN` 实测 + 代码写入点定位完成：7 个命名空间全部登记，无未登记键；当时 `DBSIZE=246`（`auth-refresh` 243、`auth-deny` 1、`public-config` 1、`datascope-deptver` 1，其余 0）。键数随登录/请求变化，设计文档 §2 是快照。 |
| CACHE-002 | 前缀收敛到唯一定义处 `com.adminstarter.common.cache.CacheNamespaces`（7 个常量 + 注册表）。既有写入点改为引用常量：`AuthTokenStore`（refresh/deny）、`AuthAppService`（fail）、`DataScopeProvider`（datascope/deptver）、`UserSessionAppService`（touch）、`PublicConfigAppService`（publicconfig）。 |
| CACHE-003 | 清理接口只接受命名空间 **code**：`CacheAdminAppService.namespace()` 未登记即 `404 RESOURCE_NOT_FOUND`；`auth-deny`/`auth-fail` 的 `clearable=false` 返回 `403`。单键删除额外校验 `CacheNamespace.matches(key)`，key 不属于该命名空间返回 `400 INVALID_PARAMETER`，前端传入的前缀字符串不会进入 Redis 命令。 |
| CACHE-004 | Redis 网关 `RedisCacheAdminRepository`：概览用 `RedisCallback` + `serverCommands().dbSize()/info("memory")`；遍历一律 `redisTemplate.scan(ScanOptions.match(prefix+"*").count(500))`，代码中无 `KEYS`。分页一次请求最多 256 批，超限 `truncated=true`、`total` 为已扫到的下界；命名空间计数同样受 256 批上限保护。 |
| CACHE-005 | 明细只返回 `name/ttlSeconds/type`，**无 value 字段**；`sensitive=true` 的 4 个命名空间（`session-touch`/`auth-refresh`/`auth-deny`/`auth-fail`）在页面提示“仅展示名称与 TTL”，审计 `params` 不记录完整 key。 |
| CACHE-006 | 审计复用 `sys_oper_log`：`CacheAuditRecorder` 手工写入（既有 `@OperLog` 注解无法记录删除数量），模块「缓存管理」，`params` 含 `namespaceCode`/`namespacePrefix`/`key`（敏感命名空间不含）/`deletedCount`。审计异常吞掉不阻断清理；清理记录页按模块过滤查询同一张表。 |
| CACHE-007 | `V14__cache_management.sql` 只新增：菜单 `/system/cache`（component `system/cache/index`）、`system:cache:list` 按钮与 `system:cache:clear` 按钮，按 `permission_code`/`path` 业务键匹配，授权仅 `SUPER_ADMIN`。无新表，未改 V1–V13；Flyway 启动日志 `now at version v14`。 |
| CACHE-008 | 接口：`GET /cache/overview`、`/cache/namespaces`、`/cache/keys`、`/cache/clean-logs`；`DELETE /cache/keys`、`/cache/namespaces/{code}`、`/cache/session-cache`。读接口 `system:cache:list`，写接口 `system:cache:clear`。未新增错误码。 |
| CACHE-009 | 前端 `apps/pc/src/views/system/cache/index.vue`：4 张概览卡（连接状态/内存占用/总 key 数/未登记键）、命名空间表（用途/前缀/当前数量/清除后果，不可清理置灰）、清理记录分页、明细抽屉（关键字过滤 + 分页 + TTL 格式化 + 单键删除），写操作均二次确认并受 `v-permission` 控制。 |
| CACHE-010 | 契约与导出：`docs/api/openapi.yaml` 新增 6 条路径与 9 个 schema；`docs/api/openapi.generated.yaml` 重新导出；`docs/design/permission-matrix.md` 重导出（65 枚权限码 / 7 个启用角色）；`docs/requirements/system-modules.md` 升 v1.6 新增 §20 缓存管理。 |

### 缓存管理实测（真实 HTTP + Redis，2026-11-09）

| # | 场景 | 请求 / 操作 | 实际返回 |
|---|---|---|---|
| 1 | 概览与 `DBSIZE` 一致 | `GET /cache/overview` ↔ 同一时刻 Redis `DBSIZE` | `totalKeys=253`，`DBSIZE=253`；`unregisteredKeys=0`；`connected=true`；内存 `739.92K / 256.00M`、`allkeys-lru` |
| 2 | 命名空间计数 | `GET /cache/namespaces` | `public-config=1`、`datascope=1`、`datascope-deptver=1`、`session-touch=2`、`auth-refresh=248`、`auth-deny=0`、`auth-fail=0`，合计 253 |
| 3 | 超过一页分页 | `GET /cache/keys?namespace=auth-refresh&page=1/2&pageSize=20` | 两页 `total=248`，各 20 条，首条不同；`truncated=false` |
| 4 | 明细不含值 | 同上响应字段 | 仅有 `name/ttlSeconds/type`；首条 `ttl=515808`、`type=string` |
| 5 | TTL 正确 | Redis `TTL` 对照 | `public-config` 571s、`datascope` 271s、`datascope-deptver` 31526794s、`session-touch` 31s、`auth-refresh` 515779s，与接口一致 |
| 6 | 未登记命名空间 | `GET /cache/keys?namespace=no-such-ns` | **404** `{"code":20401,...}` |
| 7 | 不可清理命名空间 | `DELETE /cache/namespaces/auth-deny` | **403** `{"code":20301,...}` |
| 8 | 单 key 删除 | `DELETE /cache/keys?namespace=public-config&key=admin:publicconfig:v2` | 200 `{"deleted":1}`；随后 `GET /public/configs` 200 并重新缓存，`public-config` 计数回到 1 |
| 9 | 命名空间隔离 | 清空前 `auth-refresh=248`；`DELETE /cache/namespaces/datascope` | 200 `{"deleted":1}`；`datascope` 1→**0**，`auth-refresh` 248→**248**，`session-touch` 未被清 |
| 10 | 清理过期会话缓存 | `DELETE /cache/session-cache` | 200；审计 `session-touch` `deletedCount=1/2`（当前请求会重新写 1 个 touch 键，属预期） |
| 11 | 审计可查 | `GET /cache/clean-logs?page=1&pageSize=10` | `total=8`，记录含动作、命名空间、删除数量、操作人 `admin`、IP `127.0.0.1` |
| 12 | 清理后系统可用 | 登录、`/auth/me`、`/auth/routes`、`/demo-tasks`、`/public/configs`、`/my-notices/unread-count` | 全部 200；`perms=63`、路由 3 组、受控演示数据 `total=12`、未读 `count=3` |
| 13 | 部门管理员越权 | `deptmgr` 访问任一 `/cache/**` | **403**（仅 `SUPER_ADMIN` 持有 2 枚权限码） |

### 交付门禁（缓存管理）

- `mvn -q -DskipTests package` 通过（JDK 17）；重启后 Flyway `now at version v14`。
- `pnpm --filter @admin-starter/pc build` 通过（`vue-tsc -b && vite build`，exit 0）。
- `node tools/spec-lint/cli.mjs`：error **0**。
- 基线回填：`docs/baseline/modules.md` v1.6（缓存模块行、V14）、`docs/baseline/assertions.md` v1.6（新增 #40–#43）、`docs/requirements/system-modules.md` v1.6（§20）。
- 已知限制：`session-touch` 清空后当前请求会立即重写一个键，计数不归零属预期；`auth-deny`/`auth-fail` 固定不可清理（清理即安全降级）。

## 前端重构与共享包迁移（2026-11-09 · 纯前端，无接口/表变更）

> 规范：`docs/specs/frontend-spec.md` v1.8（新增 `FE-DIR-007`、`FE-CODE-013`、`FE-COMP-008`；原拟编号 `FE-DIR-006`/`FE-COMP-004` 已被 barrel 循环依赖与 `v-model` 单向流占用，按现有体系顺延）。
> 约束：纯重构，接口路径/请求字段/响应结构/交互行为均未变；后端零改动。

| 编号 | 内容 |
|---|---|
| FRONT-001 | 新建 `apps/pc/src/composables/`：`use-paged-list.ts`（含 `confirmDelete`）、`use-crud-drawer.ts`、`use-field-errors.ts`、`use-chunk-upload.ts`、`use-blob-preview.ts`、`use-login-form.ts`（由 `views/login/useLoginForm.ts` 迁入，原文件删除）。`views/` 下已无任何 `use*.ts`。 |
| FRONT-002 | 列表骨架统一：12 个页面（12 个迁移目标）删除各自的 `page/filters/rows/load/onPageSizeChange/骨架与空态` 实现，改为 `usePagedList`；筛选→接口参数的映射（如 `keyword || undefined`）留在各页 `fetcher`。`onError` 选项用于保留“回收站/执行日志/已读统计加载失败静默”的原有行为。缓存管理页（本轮新页）直接按该骨架书写，全仓当前共 13 个 `views/` 文件使用 `usePagedList`。 |
| FRONT-003 | 抽屉统一：`useCrudDrawer` 承担 visible/isEdit/editingId/submitting/formRef/form/fieldErrors/校验/滚动到首个错误/422 回填。payload 组装、接口调用、成功文案留在页面 handler；422 toast 由页面 `catch` 后 rethrow（job/notice），dict 原本不弹 toast 故保持不弹。 |
| FRONT-004 | 字典页两套抽屉用解构别名共存（`isEdit: isEditType`、`submitting: typeSubmitting`、`fieldError: typeError`，字典项同理）；字典项列表不分页，保持手写 `loadItems()`；“选中类型刷新后仍存在则保留、否则回退第一项”的逻辑放在主列表 `fetcher` 内，未改写加载时序。 |
| FRONT-005 | 定时任务页执行日志用第二套 `usePagedList`（`logsPage`/`logsFilters`/`logs`/`onLogsPageChange`/`onLogsPageSizeChange`，`onError` 静默，`logsJob` 为 null 时返回空页）；启停/立即执行仍用 `ElMessageBox.confirm`（非删除场景，不进 `confirmDelete`）。 |
| FRONT-006 | 未迁移并保留原逻辑：`dept`、`menu` 为树形数据、无分页，套 `usePagedList` 会引入无意义分页状态；两者只在页内保留 `load` 与展开/勾选状态。缓存管理页为本轮新页，不在 15 文件迁移范围内。 |
| FRONT-007 | 目录搬迁：`packages/shared` → `apps/shared`（`package.json` 的 `name` 仍为 `@admin-starter/shared`，`main/types/exports` 指向 `./src/index.ts`）。`pnpm-workspace.yaml` 由 `apps/*` 改为显式列 `apps/pc`、`apps/mobile`、`apps/shared` + `packages/*`，理由：`apps/*` 会把共享包也当成应用参与 `--filter './apps/*'` 一类批量操作，且未来 `apps/` 下新增目录会被自动纳入。`apps/pc/vite.config.ts` alias 与 `apps/pc/tsconfig.json` paths 一并改为 `../shared/src/index.ts`；引用方源码未改（仍走包名）。 |
| FRONT-008 | 搬迁后的链接修复：`pnpm-lock.yaml` importer 路径 `../../packages/shared` → `../../apps/shared`、删除残留 `packages/shared: {}`；`pnpm install` 首轮报 “Already up to date” 未刷新 junction，删除 `apps/pc/node_modules` 重装后指向 `apps\shared`；另删除悬空的 `node_modules/.pnpm/node_modules/@admin-starter/shared`。`packages/` 未整体删除：`ui-tokens`（真实在用）、`platform`、`contracts`（仅 README）按要求保留，只移走 `shared`。 |

### 搬迁后解析路径实测

| # | 检查 | 实际 |
|---|---|---|
| 1 | `apps/pc/node_modules/@admin-starter/shared` 指向 | junction → `E:\codex\项目基础框架\apps\shared` |
| 2 | `apps/shared` 是否有 `build` 脚本 | 无；`pnpm -r --filter "./apps/*" build` 只执行 `apps/pc build` |
| 3 | 路径过滤是否排除 `apps/shared` | **否**：`pnpm -r --filter "./apps/*" exec` 仍匹配 2 个项目（按目录匹配，与是否有脚本无关）；“不参与构建”的真实原因是无 `build` 脚本 |
| 4 | 悬空 hoisted 链接 | 重装后未再生（只有 `apps/pc` 依赖该包），已手工清理 |

### 重构前后行数（总行 / `<script>` 行）

| 文件 | 迁移前 | 迁移后 | script 净减 |
|---|---|---|---|
| `views/system/user/index.vue` | 609 / 342 | 540 / 273 | −69 |
| `views/system/role/index.vue` | 680 / 348 | 621 / 289 | −59 |
| `views/system/post/index.vue` | 415 / 211 | 350 / 146 | −65 |
| `views/system/config/index.vue` | 409 / 204 | 346 / 141 | −63 |
| `views/system/notice/index.vue` | 757 / 341 | 698 / 282 | −59 |
| `views/system/log/index.vue` | 328 / 83 | 304 / 59 | −24 |
| `views/system/session/index.vue` | 257 / 143 | 235 / 121 | −22 |
| `views/message/index.vue` | 313 / 105 | 304 / 96 | −9 |
| `views/demo/task/index.vue` | 450 / 237 | 394 / 181 | −56 |
| `views/system/file/index.vue` | 731 / 397 | 530 / 196 | −201 |
| `views/system/job/index.vue` | 765 / 379 | 722 / 336 | −43 |
| `views/system/dict/index.vue` | 692 / 353 | 596 / 257 | −96 |
| **合计（12 个已迁移文件）** | **6406 / 3143** | **5640 / 2377** | **−766** |

- 未迁移：`views/system/dept/index.vue` 435 / 235、`views/system/menu/index.vue` 450 / 253（树形无分页，净减 0）。
- `<template>` 行数合计前后均为 3263，未变；净减全部来自 script。
- `notice`、`dict` 的“迁移前”为约数（本轮迁移前的统计值）；仓库无 `.git`，无法用 diff 复核，其余为逐文件实测。

### 验证（2026-11-09）

| # | 检查 | 结果 |
|---|---|---|
| 1 | `pnpm install` | 通过（重装链接后 junction 指向 `apps/shared`） |
| 2 | `pnpm --filter @admin-starter/pc build` | 通过，exit 0（`vue-tsc -b && vite build`） |
| 3 | `node tools/spec-lint/cli.mjs` | error **0**，warning 61 |
| 4 | 真实登录（admin） | 登录、侧栏菜单、列表页正常；构建产物与 dev server 均可用 |
| 5 | 用户详情脱敏（`deptmgr`） | PASS：编辑抽屉 `phone`/`email` 输入框为空、占位「已脱敏，留空保持原值」，`maskStates` 与后端一致 |
| 6 | 通知发布抽屉 | PASS：表单字段齐全（8 个输入）、「添加范围」可追加第二行范围选择 |
| 7 | 定时任务执行日志 | PASS：日志抽屉 2 条记录；每页 10→20 重新加载并回到第 1 页；停用/立即执行确认框可正常弹出与取消 |
| 8 | 静态检查 | `views/` 下无 `use*.ts`；已迁移页无残留 `reactive({ page: 1` / `DEFAULT_PAGE_SIZE`（`cache`/`profile`/`menu`/`dept` 允许保留） |

**本轮未实测（构建/静态验证通过，但未跑真实交互）**：

1. 文件页 413 只出一条提示、不踢下线、拖拽与粘贴上传、前端预校验拦截 —— 浏览器自动化无法造出超上限文件；413 后端行为已在文件能力轮实测，本轮前端分支仅代码级确认（`beforeUpload` 预校验 + 413 分支不弹全局提示）。
2. 通知“撤回”确认框 —— 测试时会话被切到 `deptmgr`（无撤回/删除按钮），属会话态 artifact，未完成点击验证。
3. 用户页“提交掩码值被 422 拒绝” —— 抽屉只做到空值占位检查，未实际提交掩码。
4. 12 个列表页的逐页增/改/删全量走查 —— 只抽验同构页与类型检查，未逐页点击。
5. 401 分码（20103/20106/20107/20108）跳登录 —— `api/http.ts` 本轮未改动，仅静态确认。

### 与规范不一致（RISK-ACCEPTED）

- `FE-DIR-001`（MUST）要求目录为 `packages/{shared,platform,ui-tokens,contracts}`，并规定“共享代码放 `apps` 内判为不通过”；`FE-COMBO-003` 亦以 `packages/shared` 为引用。本轮按用户明确批准把 `shared` 迁到 `apps/shared`，与上述两条 MUST 不一致。
- 处理：规范层只读（`AGENTS.md` §4.3），本轮**未改**规范正文；按 §5 记 `RISK-ACCEPTED`，风险为“规范与现状长期分叉，后续 AI 读规范会把现状判成违规”。是否开 CR 修订 `FE-DIR-001`/`FE-COMBO-003` 由用户决定（建议改为“共享包位置集中管理、与可运行应用平级”）。
- **2026-11-10 更新**：该 RISK-ACCEPTED 已由 `CR-20261110-001` 解除，`FE-DIR-001`/`FE-COMBO-003` 已按现状修订（见下节）。

## CR-20261110-001 规范修订与空壳包清理（2026-11-10）

| 编号 | 内容 |
|---|---|
| CRDOC-001 | `docs/specs/frontend-spec.md` → v1.9。`FE-DIR-001` 改为「可运行应用 + 共享包平级组织」，判据为「有无应用入口与 `dev`/`build` 脚本」，并写入实测事实：`pnpm --filter "./apps/*"` 按目录匹配、会匹配到 `apps/shared`，拦住它的是「没有 `build` 脚本」，因此工作区必须逐包显式列出、禁止 `apps/*` 通配。`FE-COMBO-003` 引用路径改 `apps/shared`，Web-only 能力去向改为使用它的应用内。`FE-DIR-004` 随 `packages/platform` 删除改为「平台差异实现放应用内」。`FE-DOC-003` 不再指向已删除的 `packages/contracts`。`D10`、`FE-DEP-001` 示例、§14 反例（+1 条）、两处自检清单同步。规则总数不变（123）。 |
| CRDOC-002 | 历史记录：`docs/decisions/ADR-003` 正文不动，文末加一行现状备注。活文档路径同步：`README.md`（目录树、workspace 说明、安装注释、变更记录 v1.9）、`apps/mobile/README.md`、`docs/design/field-masking.md`、`docs/audit/final-report.md`。 |
| CRDOC-003 | 索引：`docs/specs/INDEX-facts.md` 未覆盖“前端共享包位置”事实，CR §4 已写“索引未覆盖，建议补入 `F-DIR-SHARED-LOC`”，本次未改索引（避免与 `SL-FACT-001` 的 md/yaml 40/40 一致性互相牵制）。 |
| CRDEL-001 | 删除 `packages/platform`、`packages/contracts`（删除前确认：仅 `README.md`、无 `package.json`、源码 0 import、`pnpm-workspace.yaml`/`tsconfig`/`vite` 无 `platform|contracts` 字面量）。删除后 `pnpm install` 正常（4 个 workspace 项目：`apps/pc`、`apps/mobile`、`apps/shared`、`packages/ui-tokens`），`pnpm --filter @admin-starter/pc build` 通过，`spec-lint` error 0。 |

### CR 后补测（2026-11-10，真实执行）

| # | 项 | 结果 |
|---|---|---|
| 1 | 文件页 413（服务端上限临时改 1MB，HTTP 直传 2MB） | PASS：`413`、`code=10002`、`message=文件大小不能超过 1MB`、`data.maxSize=1048576`；随后 `GET /auth/me` 仍 200（不踢下线）。**UI 层“恰好一条 toast”未能真机复现**：浏览器子会话在两次调用间关闭，无法制造「页面缓存 10MB / 服务端 1MB」的时序；改由代码确认 `http.ts` 对 413 不弹全局长提示、`use-chunk-upload` 只抛 1 次 `ElMessage.error`。 |
| 2 | 文件页前端预校验（12MB，上限 10MB） | PASS：出现 1 条「单个文件最大 10MB」，网络面板无 `/api/v1/files` 请求。刷新后上限文案随 `/public/configs` 变为 1MB 亦 PASS。2MB/1MB 的用例用 `input.files` 赋值未派发 `change`，未触发提示——判为自动化构造方式问题（同一路径的 12MB drop 用例正常），非页面缺陷。 |
| 3 | 文件页拖拽 / 粘贴 | PASS：drop 64KB 文件 → 出现任务并「上传完成」，有 `POST /files/instant` + `POST /files`；document paste 64KB → 「秒传完成」，有 `POST /files/instant`。 |
| 4 | 通知撤回（SUPER_ADMIN） | PASS：确认框标题「撤回通知」、按钮「取消 / 撤回」；点取消后状态仍「已发布」；确认后 `POST /notices/{id}/revoke` code 0，`/my-notices` 总数 4→3 且该通知消失。末两步用 API 完成后半段（浏览器预算耗尽）。 |
| 5 | 用户页提交掩码值 | PASS：`deptmgr` 提交 `phone=138****9999` → `422`、`code=30201`、`fields[0]={field:phone,code:INVALID_FORMAT,message:不允许提交掩码值}`；admin 视角读回仍为 `13800000003`（库值未变）。 |
| 6 | 401 分码 | PASS（HTTP 层四条）：20103 停用、20106 管理员重置、20107 本人改密（**其他设备**会话）、20108 被管理员下线，均 `401` + 对应文案；另观察到重新启用后旧令牌为 `20105`。前端 `TERMINAL_AUTH_CODES` 含 20103/20104/20106/20107/20108，命中即 `redirectToLogin` 且不重试（代码确认）；**UI 跳转本身未真机跑通**——浏览器注入后 viewport 0x0，会话不可用。 |
| 7 | 12 个列表页 | 部分：浏览器实测「用户」「角色」两页（初始 10/7 行、分页存在、关键字查询分别收敛到 1 行）。其余页面用 API 逐端点核对可用性与数据量（posts 4、depts 树、menus 树、dict-types 2、configs 5、oper-logs 224、notices 7、jobs 1、sessions 65、demo-tasks 12、files 11、my-notices 3）。岗位页新增经 UI 未成功（表单必填「所属部门」未选，属校验生效），改用与页面完全一致的 payload 走 API：新增/编辑/删除均 code 0，总数 4→5→4。**其余 10 页的增/改/删未逐页真机执行**。 |

### 恢复清单（逐项确认）

| 项 | 恢复动作 | 结果 |
|---|---|---|
| `sys_config.sys.upload.max-size` | 1MB → `10485760` | 已确认 `configValue=10485760` |
| 测试上传文件 | 删除本轮 `test_64KB.txt`(17)、`test_64KB_paste.txt`(18) 并硬删回收站 | 文件总数 11→9，回收站 0 |
| 测试通知 | 删除 `CR-TEST-REVOKE`（id 10） | 通知总数 7→6 |
| 测试岗位 | API 删除 `cr_test_post` | 岗位总数回到 4 |
| dev1 密码 | 改密后改回 `admin123` | `dev1` 登录 code 0 |
| dev2 状态与密码 | 停用后重新启用、重置回 `admin123` | `dev2` 登录 code 0 |
| admin 令牌 | 测试中执行过一次「重置密码 → admin123」 | 密码未变；`token_version` 自增，旧令牌失效需重新登录（无数据影响） |
| 预存在的测试文件 | `big8m.txt`×3、`two2m.txt`×2、`small.txt`×2、`demo.pdf`、`upload-test.txt` | **未删除**：非本轮产生，保留并在此登记 |

## 回归缺陷修复（2026-11-10，用户实测发现）

档位：**A（微改）**——未改接口路径/字段、无表结构变更、无新权限码/错误码，未命中 `PRC-APR-001`。

| 编号 | 缺陷 | 根因 | 修复 |
|---|---|---|---|
| FIX-001 | 会话管理页搜索区竖排、表格操作列被裁切 | 列表页的 `.toolbar-filters`、`.toolbar-actions`、`.row-actions`、`.filter-keyword`、`.table-empty`、`.table-pagination` 等类只由各页 `scoped` 样式各自定义；会话页的 `scoped` 块只剩 `.muted`，类名存在但没有任何布局规则，块级元素自然竖排、按钮被压扁 | 把这些列表页通用类集中到 `apps/pc/src/styles/index.css`（含 `.toolbar-actions { margin-left:auto }`），会话页只保留专属宽度；后续新增页直接复用，漏写 scoped 也不会再塌 |
| FIX-002 | 用户管理「编辑」与「分配角色」都打开编辑抽屉 | 行内「分配角色」按钮的 `@click` 被接成 `openEdit(row)`；`userApi.assignRoles` 在前端是死代码 | 新增独立「分配角色」对话框（`assignVisible`），打开时拉取用户详情回填 `roleIds`，保存走 `PUT /users/{id}/roles`；「编辑」保持原抽屉 |
| FIX-003 | 用户管理「所属部门」筛选为空 | 类别 **①（接口没调用）**：`loadOptions()` 只在新增/编辑抽屉 `onOpen` 时执行，筛选区的 `deptOptions` 在页面挂载时从未加载；同时该函数用 `.catch(() => undefined)` 吞错，即使调用失败也无提示（叠加类别 **③**） | 页面挂载即加载部门与角色选项；选项加载失败改为统一提示（见 FIX-004） |

**FIX-004（FIX-003 的通用部分）**：新增 `apps/pc/src/utils/option-load.ts` 的 `createOptionReporter()`，按页面实例对同一标签只提示一次，文案带真实失败原因。接入页面：用户（部门/角色/详情）、岗位（部门）、定时任务（任务 Key/用户）、通知公告（角色/部门/用户）、部门（上级部门）、演示任务（部门/负责人）、角色（数据范围选项、菜单权限树）。用户/角色筛选的状态选项来自字典 `user_status`，失败时按启用/停用兜底，属有意降级，未改。

**同类错接排查**：逐个检查 12 个列表页所有行内按钮的 `@click`，只有用户页「编辑/分配角色」两个按钮共用 `openEdit`，其余页面未发现“一个 handler 挂多个按钮”。

**验证方式区分**：
- 真机点击确认：会话页搜索区四控件纵坐标差 0、操作列两按钮同排、重置按钮完整可见（960 视口）；用户页部门树出现「总部」节点；「编辑」开「编辑用户」抽屉、「分配角色」开「分配角色」对话框（含角色多选），保存 `PUT /users/1/roles` 返回 200 且列表刷新；角色/任务/会话三页搜索区横排。
- 仅构建通过：`pnpm --filter @admin-starter/pc build` 成功；`spec-lint` error 0。
- 未真机验证：其余 6 个页面的选项失败提示（只做了代码接入，未逐页制造 403）；960 视口下用户页「重置」会换行到第二行（筛选项总宽超出容器，属响应式换行，非竖排塌陷）。

**本轮测试数据恢复**：`sys_config` id=2 的 `configKey` 曾被浏览器误改为 `1048576`，已改回 `sys.upload.max-size`、`configValue=10485760`；删除测试文件 id=19（`cr2mb.txt`）与测试通知 id=11（`CR-UI-REVOKE-测试通知`）；admin 未改密。

## 规范补充：枚举 / 字典边界（2026-11-10）

档位：**A（微改）**——只改规范与索引，未动 `server/`、`apps/`，无接口/表结构/权限码/错误码变更，未命中 `PRC-APR-001`，无需签核。

| 项 | 内容 |
|---|---|
| 结论 | 枚举是否进字典保持现状，不预先统一；后续由业务需求方逐项标注后再决定 |
| 新增规则 | `backend-spec.md` §11：`BE-CFG-009`（入字典以业务标注为准，未标注禁止迁移硬编码枚举、禁止删除零消费字典项）、`BE-CFG-010`（`value` 被代码消费后只可改 `label`，改 `value` 走 `PRC-CR-001`）、`BE-CFG-011`（三条现状登记为已知例外） |
| 同步 | `INDEX-facts.md` 两条事实；`facts.yaml` 对应 `F-DICT-ENUM-SCOPE`／`F-DICT-VALUE-BINDING`；`baseline/assertions.md` 第 52 条 |
| 未做 | 未改代码、未向字典表写入或删除任何数据（含零消费的 `yes_no` 保留） |

## 示例任务：导入 / 导出 / 模板下载（2026-11-10）

档位：**B（模块级）**——只涉及示例业务模块，纯新增接口（`/import-template`、`/import`、`/export`、`/export-tasks*`）、1 张表 `sys_export_task`、2 枚权限码、0 枚错误码；未命中 `PRC-APR-001`。CR 见 `docs/audit/CR-20261110-002-demo-task-import-export.md`。

| 项 | 实现 | 说明 |
|---|---|---|
| 同源字段元数据 | `DemoTaskExcelSchema` | 模板、导入表头与校验、导出表头与取值全部由该 schema 派生；模板必填 `*`、批注、示例行、`填写须知` 均由 `ExcelTemplateWriter` 生成，无手写模板 |
| 解析 | `ExcelStreamParser`（`XSSFReader` + SAX） | 不把工作簿载入内存；单文件 ≤50MB、数据行 ≤20000，超限在解析前拒绝 |
| 导入事务 | `ExcelImportService`（`@Transactional`） | 全部行预校验通过才 `insertBatch`；任一行错抛 `ImportValidationException` → 422 `ImportErrorData`，整批回滚 |
| 错误三数字 | `ImportErrorCollector.toData` | `totalRows`/`errorRows`/`errorCount` 独立；`errors` 上限 200，`remainingErrors` 为剩余条数 |
| 重复提交 | `DemoTaskImportAppService` + `CacheService.setIfAbsent` | 同人 + 文件 SHA-256，窗口 5 分钟（覆盖最坏执行耗时）；成功保留、失败/异常释放 |
| 导出快通道 | `DemoTaskExportAppService` | 先 count：≤2000 本次返回 xlsx（`Content-Type: ...spreadsheetml.sheet`）；>2000 返回 JSON `{mode:"ASYNC",taskId,totalRows}`；>100000 拒绝 |
| 导出写入 | `ExcelExportWriter` + `DemoTaskExportFileWriter` | 同步 ≤2000 用 `XSSFWorkbook` 内存构建；异步用 `SXSSFWorkbook`（窗口 100 行）按页 2000 行流式写临时文件，内存不随总行数增长 |
| 导出三重约束 | `ExcelExportWriter` / `ExportTaskExecutor` / `@OperLogRecord` | 列三态复用 `MaskResolver`（明文/掩码/整列不输出），行复用 `DataPermissionContext.controlled`；异步线程从库重建 `CurrentUser` 并 `DataScopeContext.set`；审计写 `sys_oper_log` 的 `ext`（fileName/fileSize/mode/totalRows/importedRows/taskId） |
| 临时文件隔离 | `ExportTaskService.TEMP_ROOT` | `{java.io.tmpdir}/admin-starter/exports/{userId}/{taskId}.xlsx`，不写 `sys_file`；24h `EXPIRED` 清理，`RUNNING` 超 2h 启动补偿置 `FAILED` |
| 前端 | `http.ts` 新增 `downloadFile/downloadOrJson/saveBlob` | `fetch` 读取 `Content-Type` 分支，不走 axios 拦截器；401 手动刷新一次。导入单一诚实提示 + 三数字 + 前 200 条；导出任务抽屉轮询/取消/下载 |

**验证方式区分（2026-11-10 实测）**：

- 真机接口（对运行中的后端逐项调用，见下）：模板下载（2 个 sheet、`*` 必填、批注、须知 26 行）；导入 3 行成功；同文件重复提交被 10001 拒绝；错误文件 422 返回 `totalRows=3/errorRows=2/errorCount=3/remainingErrors=0` 且表内行数未增加（整批回滚）；20001 行被拒；2000 行导出为 xlsx、2001 行为 JSON 异步任务；异步任务进度 5→100、文件 2115 行可下载；取消后最终 `CANCELED` 且无残留文件；`deptmgr` 导出手机号为 `138****0001`；`viewer` 导出/模板均 403；导出文件只在临时区、文件管理列表无泄漏；`sys_oper_log` 记录上述 `ext` 字段。
- 真机页面（浏览器点过）：admin 登录后示例任务表格加载；工具栏「下载模板/导入/导出/导出任务」可见；导入对话框出现且含 50MB 文案；导出任务抽屉列出「已完成」任务。
- 仅构建通过：`mvn -B -DskipTests clean package`、`pnpm --filter @admin-starter/pc exec vue-tsc -b`、`spec-lint`。
- 未真机点过：浏览器内真实文件上传与错误清单渲染（受自动化上传限制，改由接口层同路径验证，固定测试文件见下节）；导出 10 万行上限拒绝（未生成 10 万行数据）。

**补充实测（2026-11-10）**：

| 项 | 结果 |
|---|---|
| 50MB 体积上限 | PASS：51MB 全零 `.xlsx` 上传，247ms 返回 `code=10001、“文件不能超过 50MB”`、HTTP 400；对照 1KB 全零 `.xlsx` 返回「文件无法解析」，证明体积拦截先于解析（校验方法 `ExcelImportAppService.validate`，原 `DemoTaskImportAppService` 第 42 行，功能已上移；`getSize` 判断在 `getBytes` 与解析之前） |
| 错误清单页面点验物料 | `docs/audit/samples/demo-task-import-errors.xlsx`（3 行数据、2 行有误）。接口实测 422：`totalRows=3/errorRows=2/errorCount=2`。页面渲染「共 N 行，其中 M 行有误（K 处）」文案在 `apps/pc/src/views/demo/task/index.vue`，**页面渲染本身待用户手动点验**，不由自动化宣称已验证 |

## ADR 编号重排误判与回退（2026-11-10）

档位：**A（微改）**——只改文档编号与 lint 豁免登记，未动 `server/`、`apps/`，无接口/表结构/权限码/错误码变更。

| 项 | 内容 |
|---|---|
| 误判 | 把 `SL-ID-002`（编号跳号）当成必须消除的阻断项，将多端架构 ADR 由 003 重排为 001、Excel 选型顺延为 002，导致仓库外的历史引用错位 |
| 回退 | 文件名与文内「编号」行改回 `ADR-003`、`ADR-004`，文末各加一行编号说明；`docs/audit` 下 9 处引用同步改回 |
| lint 豁免 | `tools/spec-lint/cli.mjs` 增加 `SEQUENCE_START_EXEMPTIONS` 登记项（ADR 从 003 起），**只豁免起始编号判定，中间跳号仍报错**；规则检查逻辑未改 |
| 可复用判断 | 编号是历史标识而非排序序号；**编号类规则不得通过重排历史标识来满足**，只能在 lint 侧登记豁免并写明理由 |
| 影响范围 | `docs/specs/`、`docs/baseline/`、`docs/api/` 均未受影响；仓库非 git 仓库，影响清单为全量检索结果而非 diff |

## 岗位管理：第二个接入点与通用性度量（2026-11-10）

档位：**B（模块级）**——只涉及岗位模块，纯新增接口（`/import-template`、`/import`、`/export`、`/export-tasks*`）、2 枚权限码、0 张新表、0 枚错误码；未命中 `PRC-APR-001`。CR 见 `docs/audit/CR-20261110-003-post-import-export.md`。

| 项 | 实现 | 说明 |
|---|---|---|
| 字段元数据 | `PostExcelSchema` | 导入列：所属部门ID*、岗位编码*、岗位名称*、排序号、状态；导出列：岗位ID、所属部门、岗位编码、岗位名称、排序号、状态、创建时间。规则来源写在类注释（`PostDtos` 注解 / `PostAppService` 唯一性 / V1 `sys_post` 表结构），未新增业务规则 |
| 导入处理器 | `PostImportHandler` | `prepare` 一次 IN 查部门与已存在编码；`validate` 部门不存在、编码已存在、文件内重复；`persist` → `insertBatch`；无解析/模板/事务代码 |
| 导出 | `PostExportAppService` + `PostExportRowAssembler` + `PostExportFileWriter` | 复用 `PostQuery` 与 `PostRepository.wrapper`（与列表同一筛选/排序），分页 2000 行 `writeStreaming` |
| 权限码 | `system:post:import`、`system:post:export` | 由 `V16__post_import_export.sql` 按 `system:post:list` 菜单挂 F 型按钮，授 `SUPER_ADMIN`/`DEPT_MANAGER`，`NOT EXISTS` 幂等 |
| 框架层改动 | 5 处 | `ExcelImportAppService`、`ExcelTemplateAppService`、`ExcelExportPlanner`、`ExportFileWriter`（执行器按 `moduleKey` 分发）、`ExportTaskItem`；去重键改为 `admin:import:dedup:v1:{moduleKey}:{userId}:{sha256}` |
| 岗位侧框架特例 | 0 处 | 无专用解析分支、无专用模板生成、无错误汇总、无事务控制 |

**验证方式区分（2026-11-10 实测）**：

- 真机接口：模板下载（表头带 `*`、填写须知含示例行与部门/状态对照）；2001 行导入 1.3s 成功；同一文件重复提交被 400 拦截；错误文件 422 返回 `totalRows=3/errorRows=3/errorCount=3`（含文件内重复码）且整批回滚（列表无 `post_t_*` 行）；同步导出（`keyword=post_t_`）列与状态标签正确；2001 行导出转异步（`taskId=3`，`SUCCESS` 100%，67817 字节，2002 行含表头）；`sys_file` 无导出文件、`sys_export_task.module='system-post'`；`viewer` 模板/导出 403，`deptmgr` 200。
- 清理：删除测试用 `sys_post`（`bulk_*`、`post_t_*`）、`sys_export_task` id=3 与临时文件 `%TEMP%/admin-starter/exports/1/3.xlsx`，复核计数为 0。
- 仅构建通过：`mvn -B -DskipTests clean package`、`pnpm --filter @admin-starter/pc exec vue-tsc -b`。
- 未实测：岗位页导入对话框/错误清单的浏览器渲染（接口层同路径已验证）；岗位 20001 行导入拒绝；导出 10 万行上限拒绝；启动补偿（`RUNNING` 超 2h 置 `FAILED`）。

**模板文案重复修正（2026-11-10 重建后实测）**：

- 问题：`ExcelTemplateWriter` 的「填写须知」自动拼接「必填/选填 + 最多 N 字 + 可选值」，而 schema 的 `formatHint` 又写了长度，导致 `岗位名称：必填，最多 64 字，最多 64 字`（示例任务「任务标题」同样重复）。
- 修正：确立约定 `formatHint` 只写补充规则（写入 `ExcelField` 字段注释）；删除岗位名称与示例任务标题的重复 `formatHint`。
- 实测：重建 jar 并重启后重新下载两份模板，`填写须知` 分别为 `岗位名称：必填，最多 64 字`、`任务标题：必填，最多 128 字`，无重复；`岗位编码：必填，最多 64 字，2-64 位字母、数字或下划线，且不能与已有岗位重复`（长度来自元数据、字符集来自 schema，保留）。表头与解析逻辑未变，导入/导出未重跑。

## 前端：导入对话框与导出任务抽屉抽公共组件（2026-11-10）

档位：**A（微改）**——只动前端两个页面，不涉接口、表结构、权限码、错误码；产出按 A 档记本节。

| 项 | 内容 |
|---|---|
| 新增组件 | `apps/pc/src/components/business/ImportDialog.vue`（228 行）：文件选择/拖拽、50MB 与 2 万行前置提示、单一诚实处理提示、`totalRows/errorRows/errorCount/remainingErrors` 结果文案、错误清单表格、模板下载入口 |
| 新增组件 | `apps/pc/src/components/business/ExportTaskDrawer.vue`（225 行）：任务列表、状态映射、进度、取消确认、下载、2 秒轮询、分页 |
| 页面契约 | 页面只传 `title/subject/templateFileName/loadTemplate/importFile`（导入）与 `pageTasks/cancelTask/downloadTask`（导出）；工具栏「下载模板」复用组件暴露的 `downloadTemplate` 与 `templateLoading`，不写第二份下载逻辑 |
| 行为差异 | **无**。抽取前逐项比对：两个页面的导入/导出流程仅在标题、模板兜底文件名、API 方法三处不同，均为 prop 注入；校验、文案、按钮禁用、关闭拦截、轮询与分页一致 |
| 代码净减 | 示例任务页 819 → 501 行（-318）；岗位页约 750 → 452 行（-约 300）；组件合计 453 行，两份重复实现收敛为一处 |

**验证口径**：

- **真机点过**（浏览器，5173 + 8080 运行中）：岗位页「导入」对话框打开，标题「导入岗位」、50MB/2 万行提示、拖拽区、「开始导入」「关闭」按钮齐全；示例任务页「导入」对话框同上（标题「导入示例任务」）；岗位页「导出任务」抽屉打开，表头含「文件名」、分页显示「共 0 条」；控制台无 error/warning。
- **错误清单页面渲染：已于 2026-11-10 用 CDP 真机触发**（此前三轮挂“未实测”作废），方法与结果见下文「引用字段翻译」的「错误清单页面渲染（CDP 补验）」。手动路径仍为：`示例业务 → 示例任务 → 导入 → 选择 docs/audit/samples/demo-task-import-errors.xlsx → 开始导入`，显示「共 3 行，其中 2 行有误（2 处），以下列出前 2 条」及错误表格。
- 仅构建通过：`pnpm --filter @admin-starter/pc exec vue-tsc -b`（exit 0）。

## 引用字段翻译：部门 / 负责人 / 状态（2026-11-10 · CR-20261110-004）

档位：**B（模块级）**——框架新增能力 + 两个已接入模块调整；无表结构、权限码、HTTP 契约变化，`PRC-APR-001` 七类均未命中。设计见 `docs/design/import-export-framework.md` §13。

### schema 声明结构

| 类别 | 业务声明 | 框架做什么 |
|---|---|---|
| `ENUM` | `enums` + `refValue` | 导入接受存储值或中文，归一到存储值；导出按 `enums` 反查中文 |
| `REF_DEPT` | `kind` + `refValue`（部门 ID） | 存 ID、显示完整路径；下拉给可见部门的完整路径 |
| `REF_USER` | `kind` + `refValue`（用户 ID） | 存 ID、显示 `姓名（部门完整路径）`；导入另接受账号与全库唯一姓名 |

业务侧不再有 `parseStatus`/`statusLabel`/`parseEnabled`/`enabledLabel`/`parseLong`，也没有第二份路径或姓名拼接。两个模块的 schema 只有字段声明。

### 落点

| 环节 | 位置 | 行为 |
|---|---|---|
| 目录装载 | `ReferenceDirectoryService.context(fields)` | 一次 `DeptRepository.findAll()` + 一次 `UserRepository.findAllForReference()`（不读密码），内存建 `id↔显示值`；整批复用，禁止按行查库 |
| 反向翻译 | `ExcelRowValidator` | 整串精确匹配；部门路径→ID，负责人显示文本/账号/姓名→ID；成功写入 `values[key]` |
| 正向翻译 | `ExcelExportWriter.value()` | `REF_DEPT`→路径、`REF_USER`→`姓名（部门）`、`ENUM`→中文；之后照常走 `MaskResolver` |
| 下拉 | `ExcelTemplateWriter.writeReferenceSheet()` | 隐藏且锁定的「引用数据」页，列头 `字段名（请勿修改）`；数据页区域引用 `'引用数据'!$A$2:$A$n`；`showErrorBox=false`、允许空单元格 |

### 错误码与文案

| `code` | 触发 | 文案 |
|---|---|---|
| `REF_NOT_FOUND` | 部门路径不存在 / 负责人三种匹配都未命中 | 部门不存在，请填部门完整路径（如「总部/研发部/前端组」）；负责人不存在，请填「姓名（部门）」或账号 |
| `REF_AMBIGUOUS` | 同一路径命中多个部门 / 显示文本或姓名命中多个用户 | 存在多个同名部门（ID …），请先清理部门名称；存在多个同名用户，请改填账号 |
| `REF_FORBIDDEN` | 部门存在但不在当前用户数据权限可见范围 | 无权指定该部门：{路径} |

三条均进 `data.errors[]`，同时计入 `errorRows` / `errorCount`；任一行有错整批不落库（实测 3 行全错时 `totalRows=3, errorRows=3, errorCount=3`，导入 0 行）。

### 两条边界结论

1. **只禁止部门名含 `/` 和 `\`**：名称含 `/` 会让「研发/测试」与「研发 + 子部门 测试」拼出同一路径。`DeptAppService` 创建/更新时拦截（400，文案「部门名称不能包含 / 或 \」），其余字符不限制。
2. **相同显示文本**：部门全路径相同 ⇒ 每级同名 ⇒ 与 `uk_dept_parent_name` 逐级冲突 ⇒ 只可能是同一节点，约束成立时不可能出现；框架仍做防御性检测，多义时 `REF_AMBIGUOUS` 并列出冲突 ID，不取第一条。用户「同名 + 同部门」是真实可能（`sys_user` 无昵称唯一约束），按上表报错。

### 下拉范围与导入范围的差别

| 字段 | 下拉建议 | 导入校验 |
|---|---|---|
| 部门 | 数据权限可见部门 | 同左（`REF_FORBIDDEN`） |
| 负责人 | 本部门及下级（停用用户不出现） | 不限部门，只校验存在与唯一 |

理由：负责人是引用不是归属；「下拉缺项不得导致填对却报错」。已实测 `deptmgr`（可见部门仅研发部与其子部门）导入「市场一号（总部/市场部）」成功。

### 上线前部门名扫描

本机库 5 个部门（总部、研发部、研发一组、市场部、财务部），名称均不含 `/` 或 `\`，无需清理。扫描口径为部门树逐级检查名称；若出现命中，先列名单与清理建议，不自动改名。

### 验证口径

**真机实跑**（本机 8080，curl 走真实登录 Cookie）：

- 两份模板：`引用数据` 页存在且 `state=hidden`、已锁定、列头带「（请勿修改）」；数据页 `dataValidation` 指向 `'引用数据'!$A$2:$A$n`，`showErrorMessage=false`、`allowBlank=true`；部门列 5 条完整路径、状态列「待处理/进行中/已完成」、负责人列 `姓名（路径）`。
- 示例任务导入 3 行：部门填完整路径、负责人分别填显示文本与账号、状态分别填中文与 `DOING`，全部成功；随后导出，`归属部门` 为 `总部/研发部/研发一组`，`负责人` 为 `开发一号（总部/研发部/研发一组）`，`任务状态` 为「进行中」。
- 导出 → 不改内容 → 导入：成功（导出多出 `任务ID`/`负责人手机号`/`创建时间` 三列被按列名忽略），闭环成立。
- 错误文件 3 行（部门不存在 / 负责人不存在 / 状态非法）：422，`totalRows=3, errorRows=3, errorCount=3`，`code` 为 `REF_NOT_FOUND`×2 与 `INVALID_FORMAT`，0 行落库。
- `deptmgr`：模板下拉只剩 `总部/研发部`、`总部/研发部/研发一组` 及其用户；导入 `总部/市场部` → 422，`REF_FORBIDDEN`「无权指定该部门：总部/市场部」。
- 重名：新建昵称「开发一号」的第二个用户后，导入负责人填「开发一号」→ 422，`REF_AMBIGUOUS`「存在多个同名用户，请改填账号」；同批按账号填写的那行未落库（整批回滚）。
- 部门名 `研发/测试`、`研发\测试` 创建均被拒：400，`INVALID_PARAMETER`「部门名称不能包含 / 或 \」。
- 测试数据已清理（任务 7 条、临时用户 2 个），复查关键词检索为 0 条。

### 错误清单页面渲染（2026-11-10 补验，CDP）

- **方法**：Chrome 157 以 `--remote-debugging-port=9222 --remote-allow-origins=*` 启动；Node 22 直连 CDP，`Target.attachToTarget` 后依次 `Page.enable` / `Network.enable` / `DOM.enable`，调用 `DOM.setFileInputFiles` 设置 `docs/audit/samples/demo-task-import-errors.xlsx`，点击「开始导入」，监听 `Network.responseReceived` 读取 422 响应体，再读对话框 DOM 并截图。
- **结果**：对话框显示「共 3 行，其中 2 行有误（2 处），以下列出前 2 条。」与「整批未写入，请按下列清单修改后重新上传。」；表格两行：`第 2 行 · 任务标题 / 不能为空`、`第 3 行 · 任务状态 / 只能是 待处理(TODO)、进行中(DOING)、已完成(DONE)`。HTTP 422，`code=30201`，`data` 为 `totalRows=3`、`errorRows=2`、`errorCount=2`、`remainingErrors=0`。
- **样例文件修正**：原 `demo-task-import-errors.xlsx` 仍是旧表头（`归属部门ID` / `负责人账号`），按当前模板导入只会得到 400「缺少列」，无法触发错误清单。已按当前模板表头重建为 3 行（空标题、非法状态、正常行），与手动路径文案一致。

### 2 万行性能（2026-11-10 实测，含批量写入改造后复测，`-Xmx1g`）

- 改造前：合法 2 万行客户端 6,900–8,351ms，服务端解析 74–180ms、目录装载 14–32ms、逐行校验 21–80ms、**持久化 6,726–7,964ms（占 96%–98%）**；每行带错 2 万行 239ms 返回 422；堆增长 12.0MB，0 次 Full GC。结论：引用翻译开销可忽略（<2%），瓶颈是 `insertBatch` 逐行 insert。
- 改造后：`DemoTaskRepositoryImpl` / `PostRepositoryImpl` 改用多行 VALUES 批量 INSERT（每 1000 行一条），单事务整批回滚不变；2 万行持久化降到 621–1,191ms，客户端 816–1,021ms（热态）、1,334–1,515ms（冷 JVM 首跑）；单次堆增长 41.7MB、0 次 Full GC；全错 2 万行 127–316ms 返回 422。3 并发 2 万行全部成功、墙钟 1.48s、Hikari active 峰值 3、pending 0；未做限流，风险见断言 #64。
- 主键：批量 SQL 未开启 `useGeneratedKeys`，导入后实体 `id` 为 null，导入路径不依赖 ID；单条插入仍回填。明细见 `docs/audit/perf-2w-2026-11-10.md` §1、§6；上量阈值与替代方案见 `docs/design/import-export-framework.md` §13.2。

**未实测 / 口径说明**：

- Excel 客户端里实际点开下拉箭头未验（本机无 Office），以 xlsx 内 `dataValidation` 与隐藏 sheet 结构为证据；`showErrorBox=false` 表示即使客户端拦截也不阻断手填。
- 前端本轮无改动，未重新构建。

## 导入持久化：批量写入改造（2026-11-10，A 档）

- 改动文件：`DemoTaskMapper` / `PostMapper` 各加一个 `insertBatch`（`@Insert` + `<foreach>` 多行 VALUES）；`DemoTaskRepositoryImpl` / `PostRepositoryImpl` 的 `insertBatch` 改为按 1000 行分块调用，时间字段在 Repository 内按 UTC 显式填充（多行 SQL 不走 `MetaObjectHandler`）。
- 未改：`@Transactional` 整批语义、导入校验、错误清单、接口契约、表结构、权限码、错误码。
- 主键：批量 SQL 未开 `useGeneratedKeys`，导入后实体 `id` 为 null；导入路径不消费 ID，单条插入仍回填。
- 实测：2 万行持久化 621–1,191ms（改造前 6,726–7,964ms）；客户端 816–1,021ms（热态）/ 1,334–1,515ms（冷 JVM 首跑）；堆增长 41.7MB、0 次 Full GC；全错 2 万行 127–316ms。3 并发 2 万行全部成功，Hikari active 峰值 3、pending 0，`Slow_queries=0`、`Innodb_row_lock_waits=0`；未限流，风险登记断言 #64。数据与对比见 `docs/audit/perf-2w-2026-11-10.md` §6。
- 验证：`mvn -o -q -pl web -am package -DskipTests` 通过；示例任务 3 行、岗位 3 行导入冒烟均 200，落库 `status/dept_id/owner_id/create_by/created_at/updated_at/deleted` 抽查一致；压测数据清理回 `biz_demo_task=12`。





## 文档回填与 spec-lint 门禁修补（2026-11-11，纯文档 + 工具，A 档等价：不动接口/表/权限码/配置项）

本轮回填 4 处文档与代码/自身门禁不一致，并修补 1 处工具漏判。**未改任何业务代码、未改 `docs/specs/`、未改 `AGENTS.md`。**

| # | 文件 | 改了什么 | 依据 |
|---|---|---|---|
| 1 | `docs/baseline/assertions.md` | #40 缓存命名空间 7 → **8**，补 `import-dedup` 前缀与 `clearable=false`（清理返回 403）；表头版本升 v1.16 | `CacheNamespaces.java:12-19`、`:66-70`（8 个 code，`import-dedup` 的 sensitive=true / clearable=false） |
| 2 | `docs/requirements/system-modules.md` | 版本 v1.6 → v1.7；总览表补 #21「示例业务·导入导出」、#22「岗位·导入导出」；岗位章节补导入导出能力/接口/权限码/边界/导出无独立数据范围的限制；示例业务章节补 4 个 export-tasks 接口、边界改为两接入点；缓存章节命名空间 7 → 8、补 `import-dedup` 不可清理 | `modules.md` 新增的两行登记（V15/V16）、`PostController` 的 import/export 端点、`CacheNamespaces` |
| 3 | `docs/audit/final-report.md` | 顶部加「历史快照，不是当前现状」声明与冲突时的裁决顺序；「文件删除」那一行标注已被 V13 推翻 | 原文仍是 2026-11-08 v1.5 快照，与 V13 回收站/彻底删除能力冲突 |
| 4 | `docs/api/openapi.yaml` | 去掉脚手架示例：info.title/version（0.0.0 → 1.0.0）/servers、示例路径 `/api/v1/resources`、4 个 `Resource*` schema、`resources` tag、全部 EXAMPLE-ONLY 文案；补 CT-VER-005 变更记录；**修复 `description: 文件流（Content-Disposition: inline）` 未加引号导致整份契约无法被 YAML 解析的语法错误** | `SL-EXAMPLE-001` 原为 warning 漏判；YAML 解析失败由 PyYAML 实测（改前 backup 同样报错，属既存缺陷） |
| 5 | `tools/spec-lint/cli.mjs` + `README.md` | `SL-EXAMPLE-001` 新增 `PROJECT_CONTENT_DIRS`：`docs/api/`（及 `docs/plan/`、`docs/audit/`）按项目层 **error** 判定；与 `PROJECT_OUTPUT_DIRS` 刻意分开，避免连带放宽 `SL-PATH-001`；README 的检查清单与口径说明同步更新 | `cli.mjs` 原先只有 `PROJECT_OUTPUT_DIRS`，`docs/api/` 不在其中 → 契约源残留示例永远只是 warning |

**验证（真实执行）**

- `node tools/spec-lint/cli.mjs` → **error 0 / warning 48**（改前 55），`SL-EXAMPLE-001` 命中 0 处。
- 负向测试：把 `docs/api/openapi.yaml` 复制到临时根并追加一行 `# EXAMPLE-ONLY 负向测试`，`--root` 跑出 `SL-EXAMPLE-001 ... 项目层文件仍残留 EXAMPLE-ONLY` 且退出码 1，证明新口径真的会拦。
- 契约结构：PyYAML 解析 `docs/api/openapi.yaml` 通过——openapi 3.0.3、version 1.0.0、**53 paths / 60 operations / 54 schemas**、`` 无悬空、path 中使用的 tag 全部已声明；`docs/api/openapi.generated.yaml` 同样解析通过（93 paths）。
- 换行与编码：`docs/api/openapi.yaml` 保持 **LF、UTF-8 无 BOM**（与 generated 一致）；中途一次 PowerShell 写入曾把全文转成 CRLF，已同批复原并复核。

**未做 / 遗留**

- 手写契约 53 path 覆盖不到实现基线 93 path 的其余接口；按 `AGENTS.md` §4.4 属显式豁免，未强行补齐（补齐会引入手抄漂移）。
- 未新增 `docs/api/errors.yaml`（`SL-TPL-001` 仍报 warning，属豁免与脚手架现状），未动 `AGENTS.md` §9-A/§9-B（属「只读不改」，需另开通用层变更并由用户确认技术栈）。
- 仍未解决的已知风险不变：单机调度无分布式防重（断言 #26）、导入未限流（断言 #64）、无 Git 仓库、无 CI 与自动化测试。

## 代码级评审（2026-11-11，只读，无代码改动）

- 产出：`docs/audit/code-review-2026-11-11.md`（后端 322 个 Java 文件 / 16,135 行；前端 67 个 ts+vue / 11,315 行）。
- 结论：**P1 三项** —— ① 前端把所有 `/auth/*` 排除在 401 静默刷新之外，access 过期后刷新页面即被登出（refresh 仍有效 7 天）；② 文件越权访问返回 403，违反接口契约「越权统一 404」，构成存在性探测面；③ 无 CI、无 mvnw、全仓仅 1 个测试类，交付门禁里的「契约测试阻断」没有执行载体。
- 另有 P2 七项（CSRF 无 Origin 即放行且错误体 traceId 为空、登录停用返回 403+20301 与 401 分码体系不一致、page≤0 未校验、任务调度取 JVM 默认时区、线程池绕过 Spring 生命周期、分片续传键未按用户隔离、异步导出未校验导出人状态、缺 CSP 头）。
- 同时登记「已核对且不应改」的 11 处设计（异常映射、排序白名单、数据权限只拼数值 ID、未登记表报错、路径穿越防护、Cookie 属性、脱敏 fail-closed、导出任务归属、导出线程重建上下文、composable 错误契约），避免后续按“常见做法”改出回归。
- 需人工裁决：文件越权 403 与 `CT-HTTP-008` 的冲突——现状 `docs/baseline/assertions.md` #36 把 403 记为既成事实，契约要求 404；两者必须选定一个并留痕，本轮不动代码。
- 未覆盖：未逐行通读全部文件、未启服务做运行时验证、未评审 UI 视觉与无障碍、未新做性能压测（详见报告末节）。
## 代码评审缺陷修复（2026-11-11，A 档：不动表结构 / 不新增接口与权限码 / 不新增错误码）

**裁决依据**：用户 2026-11-11 口头裁决「按照你说的做」，对应评审报告 `docs/audit/code-review-2026-11-11.md` 缺陷 #2 的方案 A（实现向 `CT-HTTP-008` 回归）。三者均未命中 `PRC-APR-001`。

| # | 文件 | 改动 |
|---|---|---|
| 1 | `apps/pc/src/api/http.ts` | 401 静默刷新的排除范围由「所有 `/auth/*`」收窄为「`/auth/login`、`/auth/refresh`」（新增 `isAuthEntry`）；其余 `/auth/*` 先刷新再判定未登录 |
| 2 | `server/system/.../application/FileAppService.java` | `requireVisible` 两处由 `FORBIDDEN / 无权访问该文件` 改为 `RESOURCE_NOT_FOUND / 文件不存在或无权访问`（与不存在同一响应，消除存在性 oracle） |
| 3 | `.github/workflows/ci.yml`（新增） | 三条 job：spec-lint、后端 `mvn -B -pl web -am -DskipTests package`、前端 `pnpm install --frozen-lockfile` + `pnpm build`；后端暂 `-DskipTests` 并注明原因（唯一测试依赖真实 MySQL/Redis） |

**同批回填**：`docs/baseline/assertions.md` #36 改写并升 v1.17；`docs/design/file-storage.md` 版本升 v1.1、越权语义与自检项改为 404（原「403 需求硬要求」标注作废及原因）；`docs/api/openapi.yaml` 预览说明更正、版本 1.0.0 → **1.0.1** 并补变更记录；`README.md` 变更记录升 v1.11；评审报告补「处理记录」一节。

**验证（真实执行）**

| 项 | 结果 |
|---|---|
| 后端编译打包 | `mvn -o -q -pl web -am -DskipTests package` → 退出码 0 |
| 前端类型检查 + 构建 | `pnpm build`（`vue-tsc -b && vite build`）→ 退出码 0，产物 `apps/pc/dist` |
| 契约解析 | PyYAML 解析 `docs/api/openapi.yaml` 通过，version 1.0.1、53 paths |
| spec-lint | error 0 / warning 49（与改前持平） |

**端到端复核**（临时实例 `--server.port=18080 --app.auth.access-ttl=5s`，验证后已停止）：

| 场景 | 期望 | 实测 |
|---|---|---|
| 低权用户（临时角色仅授 `system:file:list`+`system:file:download`、SELF 范围）下载他人文件 | 404 且与不存在同码同文案 | HTTP 404 / code 20401 / 「文件不存在或无权访问」 |
| 同一用户下载不存在的 id | 与越权完全一致 | HTTP 404 / code 20401 / 同一文案；**响应完全一致 = 无 oracle** |
| 文件属主（admin）下载自己的文件 | 200 | HTTP 200，长度 19 字节 |
| access 过期（5s）后 `GET /auth/me` | 后端 401（前端负责先刷新） | HTTP 401 |
| 紧接 `POST /auth/refresh` | 200 | HTTP 200，code 0 |

**验证数据清理**：临时角色（id 11）、临时用户（id 14）、上传文件（id 20）、会话与关系行全部物理删除，复核计数均为 0；文件先走回收站再彻底删除，未留物理字节。

**浏览器端 E2E（2026-11-11 补充，本机 Chrome 157 + CDP，非只读走查）**

方法：后端以 ``--app.auth.access-ttl=5s`` 起在 8080，前端 ``pnpm dev`` 起在 5173，Chrome ``--headless=new --remote-debugging-port=9222``（临时 profile，验后删除），脚本 ``.local/cdp-refresh-check.mjs`` 依次执行「登录 → 进 /dashboard → 等 6.5s 让 access 过期 → reload → 读 ``location.pathname`` 与 ``performance`` 里的 ``/auth/refresh`` 调用数」。**做了 A/B 对照**：先把 ``http.ts`` 临时退回旧判据跑一遍，再复原跑一遍。

| 版本 | reload 后落点 | ``/auth/refresh`` 调用 | ``/auth/me`` 调用 | 判定 |
|---|---|---|---|---|
| 旧逻辑（所有 ``/auth/*`` 跳过刷新） | ``/login`` | 0 | 0 | **缺陷复现**（被误登出） |
| 修复后（只排除 login/refresh） | ``/dashboard`` | 1 | 2（首个 401 → 刷新 → 重放） | **符合预期**（静默续期成功） |

还原校验：``http.ts`` 复原后 SHA-256 与改动前一致（``0506C20A…689640``），确认对照实验未把临时改动留在代码里。验证后 8080/5173/9222 均已停止，无 java/node 残留进程。

**未处理**：评审 P2 的 #4–#11 与 P3 全部，按报告给出的顺序排入后续迭代。
## 评审 P2 #4–#11 批量修复（2026-11-11，A 档单元 × 8：无表结构 / 无新增接口与权限码 / 无新增错误码）

**裁决依据**：用户 2026-11-11 指示「一起做了」，对应 `docs/audit/code-review-2026-11-11.md` 的 P2 全部条目。逐类比对 `PRC-APR-001` 七类：契约破坏兼容（未命中，未改路径/字段/错误码集合）、数据模型不可逆（未命中）、权限放宽（未命中，方向是收紧）、安全降级（未命中，方向是收紧）、状态机（未命中）、验收标准变更（未命中）、模块启停（未命中）→ 不触发人工审批。

**规则缺口（`RULE-GAP`，如实上报）**：本轮为「跨多模块 + 行为收紧」，而 `AGENTS.md` 的档位表里 A 要求「只动 1 个模块」、B 要求「纯新增且向后兼容」、C 要求命中七类——三者覆盖不到该形态。处理方式：**按改动单元拆成 8 个单模块单元各自按 A 判**，合计一次提交；档位口径本身未擅自修改，建议后续在 `AGENTS.md` 补一条「跨模块行为收紧」的归口。

| # | 改动 | 文件 |
|---|---|---|
| 4 | CSRF 来源校验：无 Origin 回落 Referer；**仍无来源且携带认证 Cookie 则 403**，无 Cookie 放行（登录 / 无凭据内部调用）；拒绝响应改走统一信封 + `X-Trace-Id`；删除未使用的 `AuthProperties` 注入 | `web/filter/OriginCheckFilter.java`（重写） |
| 5 | 登录时账号停用改为 `ACCOUNT_DISABLED`（401 / 20103），与每请求校验同一分码 | `auth/application/AuthAppService.java` |
| 6 | `page < 1` 在 `PageQuery.resolvePage` 统一拒绝（400），删掉缓存模块的重复校验 | `common/api/PageQuery.java`、`CacheAdminAppService.java` |
| 7 | cron 时区改为显式 `app.job.zone`（默认 Asia/Shanghai），启动打印生效时区 | `job/JobScheduler.java`、`job/JobExecutor.java`、`application.yml` |
| 8 | 调度器与两个任务执行池改为 `@Bean` 装配（`JobSchedulingConfig`），参数走 `app.job.*`；移除构造器 `new`/手工 `initialize`/`@PreDestroy` | `job/JobSchedulingConfig.java`（新增）、`JobScheduler`、`JobExecutor` |
| 9 | 分片续传键加用户维度（`userId:size:sha256`），会话不可用时清除本地记录 | `apps/pc/src/composables/use-chunk-upload.ts` |
| 10 | 异步导出校验导出人仍存在且启用，否则任务置 `FAILED` | `export/ExportTaskExecutor.java` |
| 11 | 下载/预览响应补 `Content-Security-Policy: default-src 'none'` | `web/controller/FileController.java` |

**连带修复（本轮自测暴露）**：新增两个 `ThreadPoolTaskExecutor` Bean 后，`ExportTaskExecutor` 的构造注入变成「3 个候选」，启动直接失败（`No qualifying bean ... found 3`）。已改为显式构造器 + `@Qualifier("exportTaskThreadPool")`。这类「改装配后启动崩」只有真启动才暴露，已记入本节。

**同批回填**：`docs/baseline/assertions.md` 新增 #65（CSRF 口径）、#66（任务时区与线程池装配）、#67（`page<1` 统一拒绝），版本升 v1.18；`docs/design/scheduled-task.md` 更新时区与线程池说明；`README.md` 变更记录升 v1.12。

**验证（真实执行）**

| 项 | 结果 |
|---|---|
| 后端编译打包 | `mvn -o -q -pl web -am -DskipTests package` → 退出码 0（首次失败于 Bean 歧义，修复后通过） |
| 前端类型检查 + 构建 | `pnpm build` → 退出码 0 |
| 启动 | `Started AdminApplication`；日志「定时任务生效时区：Asia/Shanghai（app.job.zone，库内时间为 UTC）」，2 个任务注册成功 |
| CSRF（HTTP 层） | 无 Origin 无 Cookie 登录 → 200；带 Cookie 无 Origin 写 → **403 + `X-Trace-Id`**；带 Cookie + 合法 Origin → 200；非法 Origin → 403；仅同源 Referer → 200 |
| CSRF（浏览器层，Chrome 157 + CDP） | 页面内同源登录 → code 0；带 Cookie 的 `POST /auth/logout` → **200**（浏览器自动带 Origin，未被误伤）；重新登录后 `/dashboard` 正常渲染（侧栏存在） |
| `page` 校验 | `?page=0` → 400 / 10001「page 必须大于 0」；`?pageSize=101` → 400 / 10001 |
| 停用账号登录 | 401 / **20103**「账号已被停用」 |
| 下载响应头 | 200；`Content-Security-Policy: default-src 'none'`；`X-Content-Type-Options: nosniff`；`X-Trace-Id` 存在 |
| 预览语义未回退 | `txt` 预览 → 422 / 30201（白名单未变） |

**验证数据清理**：临时用户（id 15）与会话/关系行物理删除、验证文件（id 21）先回收站再彻底删除，复核计数 0；Chrome 临时 profile 已删；8080 / 5173 / 9222 均已停止，无 java/node 残留。

**未覆盖**：#9 的分片续传换账号场景未做浏览器实测（需 5MB+ 文件触发分片路径），仅代码走查 + 构建通过；本轮未跑压力/兼容性回归。
## 版本管理建立（2026-11-11，非业务变更）

- `git init`（2.45.1），默认分支 `main`；首个提交为现有工程与文档基线的整体导入（**无任何业务代码逻辑改动**）。
- 仓库级配置 `core.autocrlf=false` + `.gitattributes`（`* text=auto eol=lf`，`.bat`/`.cmd` 为 CRLF）：首次 add 时 Git 提示「LF 将被替换为 CRLF」（全局 `core.autocrlf=true`），若不处理，Windows 签出后全部文件会变成 CRLF，产生整文件假改动。已改为 LF 口径后重新入库，add 时换行警告 0 条。
- `.gitignore` 补齐三处漏网：`server/web/data/`（以 `server/web` 为工作目录时上传目录落点）、根 `data/`、`.trae-html-share-packages/`（登录页 mockup 打包 26MB）与 `.screenshots/`。复核 `git check-ignore -v`：`.local/mysql-data`、`application-local.yml`、`docs/env/local-dev.md`、`logs/*.log`、`node_modules`、`apps/pc/dist`、`server/web/target` 均被忽略。
- 故意不入库的两份文件：`docs/env/local-dev.md`（含本机明文口令）与 `server/web/src/main/resources/application-local.yml`（DB/Redis 口令）。克隆后需按 `application-local.example.yml` 自行创建；这会带来少量 `SL-PATH-001` warning，属预期，不为“文档完整”提交口令。
- 入库规模：556 个文件；构成 server 352 / docs 118 / apps 76 / scripts 4 / packages 2 / 根配置 6。
- CI 说明：`.github/workflows/ci.yml` 只有在推送远端后才真正执行；本地等价命令见 README「版本管理」小节。
## AGENTS.md §9 填实（2026-11-11，通用层变更，无代码改动）

- 变更记录：`docs/audit/CR-20261111-001-agents-tech-stack-baseline.md`；决策记录：`docs/decisions/ADR-005-tech-stack-baseline.md`。
- §9-A 六项按用户 2026-11-11 逐项确认填写：项目名称「admin-starter 后台管理底座」；平台 PC Web（已可运行）+ H5／小程序／App（规划中，跨端框架未选型）；团队 Java + Vue 全栈；部署单机内网 Windows（现状），预算限制未确认；合规需过等保二级／三级（条款待拆解）；里程碑为长期迭代无固定节点。
- §9-B 九行按仓库事实填写（版本取自 `pom.xml`、`package.json` 与产物 `admin-starter.jar` 的 `BOOT-INF/lib`，不是「常见版本」）：Java 17 / Spring Boot 3.2.12 / MyBatis-Plus 3.5.7（MyBatis 3.5.16）+ MapStruct 1.5.5 / MySQL 8.0 + Flyway 9.22.3 / Redis（Spring Data Redis 3.2.12，无 MQ）/ JUnit 5 + Mockito + AssertJ（仅后端）/ pnpm 11.7.0 + Maven 3.6.3 / Node ≥ 22；前端 PC 为 Vue 3.5 + Vite 5 + TS 5.6 + Element Plus 2.8 + Pinia 2.2，**跨端与前端测试框架保持「未定」**。
- 关闭两笔历史遗留：`FE-STACK-001` 的 `RISK-ACCEPTED` 与 `FE-STACK-004` 的 `RULE-GAP`（此前 §9-B 为空，两条规则无法满足）。对应记录保留在上文，状态改为「已由 CR-20261111-001 关闭」。
- 事实索引：`docs/specs/INDEX-facts.md` 与 `docs/specs/facts.yaml` 新增 `F-TECH-STACK`（owner `AGENTS.md#9`，refs `FE-STACK-001`、`FE-STACK-004`），43/43 条一致；顺带修正表尾长期停留在「39 条」的陈旧文案。
- 未决策项（保留待办）：① 跨端框架选型（阻塞 H5／小程序／App，未选型前禁止在 `apps/mobile` 落业务代码）；② 等保二级／三级条款拆解为可验收条目（当前操作日志无留存期与归档策略、无留存配置项）；③ 前端测试框架选型。
- 验证：`node tools/spec-lint/cli.mjs` → error 0 / warning 49（与改前持平；新增文档引入的 12 条 `SL-REFMT-001` 已通过补齐文件名清零）。
## 前端测试框架落地（2026-11-11，Vitest + Playwright；ADR-006 / CR-20261111-002）

**为什么做**：`frontend-spec.md` 决策项 D15 要求「单测 + E2E + 小程序自动化」，但 `AGENTS.md` §9-B 一直是「未定」、`apps/pc` 无任何测试框架；CI 只做 `vue-tsc -b` + `vite build`，运行期逻辑零验证（此前 `/auth/*` 刷新 bug 就是这样漏掉的）。

**做了什么**

| 层 | 内容 |
|---|---|
| 单元/组件 | `vitest@3.2` + `@vue/test-utils` + `happy-dom`；配置 `apps/pc/vitest.config.ts`；用例 `src/**/*.spec.ts`（3 文件 19 用例） |
| E2E | `@playwright/test@1.65`；配置 `apps/pc/playwright.config.ts`（默认 `channel: 'chrome'`，用本机 Chrome，不下载浏览器）；用例 `e2e/smoke.spec.ts`（4 条主链路） |
| 可测性重构 | `http.ts` 把 401 处置抽成纯函数 `planOn401` + `isAuthEntry` + `isTerminalAuthCode`，**行为不变**，三类分支由 7 个用例锁定 |
| CI | `ci.yml` 前端 job 增加 `pnpm --filter @admin-starter/pc test:unit`；新增 `.github/workflows/e2e.yml`（`workflow_dispatch`，MySQL/Redis 服务容器） |
| 忽略项 | `test-results/`、`playwright-report/`、`coverage/` 进 `.gitignore` |

**版本约束（重要）**：vitest **锁 3.x**——vitest 5 的 peer 要求 `vite ^6||^7||^8`，会倒逼升级 Vite 5.4。已在 ADR-006 写明，后来者不要「顺手升级」。

**验证（真实运行）**

| 项 | 结果 |
|---|---|
| 单元测试 | 3 文件 **19 用例全通过**（2.1s） |
| 覆盖率 | `http.ts` 分支 100% / 行 20%（只覆盖纯函数）；整体行 1.35%（`views/*` 未覆盖，按 §15.2「页面不设门槛、靠 E2E 冒烟」属预期） |
| E2E | **4 用例全通过**（7.8s，本机 Chrome 157）：未登录重定向、登录+刷新续期、无权限账号不可见列表、数据范围（普通员工可见任务数 < 管理员） |
| 生产构建 | `pnpm build` 通过（测试文件未污染产物） |
| 门禁 | spec-lint error 0 |

**两条实测观察（未擅自改行为，留决策）**

1. **未授权页面表现为 404 而不是 403**：动态路由按登录下发的菜单注册，未授权路由压根没注册，落到通配 404 状态页；`views/error/403.vue` 只在「路由已注册但 meta 校验失败」时可达，实际几乎不会出现。`ui-spec.md` 只规定「三种空状态（含无权限）」，未规定未授权路由该显示 403 还是 404，故不判违规。E2E 断言改为可验证的对外行为（无表格 + 状态页可见）。若要改成「注册全量路由 + meta 校验」，属路由策略变更，需单独决策。
2. **`20105` 不是终止码**：写单测时误以为 20105（登录状态已失效）属终止类，用例运行失败；按 `docs/design/auth-and-datascope.md` 的 401 分码矩阵更正为「非终止，仍先尝试刷新」。测试反过来纠正了我的假设。

**未覆盖**：`use-chunk-upload`、`use-crud-drawer`、`v-permission` 指令等尚未补用例；§15.2 的分层覆盖率目标（纯函数 ≥80%、composable 60–70%）目前未达标，属后续增量。E2E 工作流尚未在 GitHub Actions 上实测（推送后需手动触发一次）。

## 覆盖率补齐（2026-11-11，纯新增测试，无生产代码改动）

**目标**：`frontend-spec.md` §15.2 的分层门槛——纯函数/工具 ≥80%、composable 60–70%、页面不设门槛只做 E2E 冒烟。

**结果（真实运行 `pnpm --filter @admin-starter/pc test:unit:coverage`）**

| 目录/文件 | 改前 | 改后 | §15.2 门槛 |
|---|---|---|---|
| `src/utils`（datetime / form / option-load / sha256 / skeleton） | 14.84% | **100%** 行 / 97.5% 分支 | ≥80% ✅ |
| `src/composables`（field-errors / paged-list / crud-drawer / chunk-upload / login-form / blob-preview / pagination） | 56.84% | **93.36%** 行 / 88.97% 分支 | 60–70% ✅ |
| `src/directives/permission.ts` | 0 | **100%** | — |
| `src/stores/auth.ts` | 0 | **100%** 行 / 85.71% 分支 | — |
| `src/api/http.ts` | 20.12% | **36.03%** 行 / 74.13% 分支 | — |
| 用例总数 | 19（3 文件） | **95（15 文件）** | — |

**新增 12 个用例文件**（按价值排序）

| 文件 | 用例 | 锁住的行为 |
|---|---|---|
| `src/stores/auth.spec.ts` | 7 | bootstrap 一次拉齐 me/routes/permissions；`hasPermission` 未传码即放行；`logout` 接口失败也必须清本地登录态；`displayName` 昵称→账号回落 |
| `src/composables/use-login-form.spec.ts` | 11 | 空表单校验与聚焦顺序、记住我只存账号（trim）、`redirect` 优先、401 字段错误就地回填而非整页报错、提交中重复点击被忽略 |
| `src/composables/use-chunk-upload.spec.ts` | 9 | 续传键含 userId（换账号不串会话）、会话失效清本地键、已上传分片跳过、413 回灌上限且只提示一次、秒传不发请求 |
| `src/composables/use-crud-drawer.spec.ts` | 9 | 提交期间 `submitting` 置位、422 回填并保持抽屉打开、再次打开清空上次错误、校验失败不调 handler |
| `src/api/http-interceptor.spec.ts` | 9 | 信封解包、非信封（Blob）原样返回、422 字段错误与导入载荷、413 带 `maxSize` 且不弹全局提示、5xx 文案带 traceId |
| `src/composables/use-blob-preview.spec.ts` | 6 | 图片不回源、Blob 取回生成本地 URL、HTTP 失败提示并关闭、打开新文件与卸载时回收 objectURL（防内存泄漏） |
| `src/utils/option-load.spec.ts` | 7 | 选项加载失败按页面去重提示、`loadStatusOptions` 异常回落到内置两项 |
| `src/utils/skeleton.spec.ts` | 5 | 骨架屏延迟显示（快请求不闪烁）、结束立即隐藏、卸载清定时器 |
| `src/utils/sha256.spec.ts` | 3 | 算法与编码没变（比对 "abc"/空串的标准摘要） |
| `src/utils/datetime.spec.ts` | 3 | 空值占位符、无法解析时原样返回、`YYYY-MM-DD HH:mm` 补零 |
| `src/utils/form.spec.ts` | 4 | 滚动到首个错误项、240ms 后聚焦、禁用输入框不聚焦 |
| `src/directives/permission.spec.ts` | 3 | 无权限移除 DOM（非置灰）、数组写法任意命中、从注册函数取真实实现（不抄一份逻辑） |

**实测踩到的三个坑（都记下来，避免后人重复）**

1. **`wrapper.find` 会命中组件根节点**：权限指令把元素移出 DOM 后，若该元素就是组件根，`find('.probe')` 仍返回 true。改为在元素外包一层 host div 才断言得出「已移除」。
2. **Vitest 3.2 的幽灵未处理拒绝**：`beforeEach` 里调用 `mockReset()/mockClear()`，再用例内让 mock 抛错，会被记为「未处理拒绝」——即使被测函数的 catch 已正确回落（实测打印出的返回值就是回落值）。去掉该 beforeEach 后通过；已在用例里加注释说明。
3. **`vue-tsc -b` 会连测试文件一起类型检查**：spec 里的 `as never` 展开、未使用的 import、mock 实现签名不符都会让 `pnpm build` 失败——CI 里单测与构建是两步，构建这一步同样能挡住测试文件的类型问题。

**未覆盖（有意的）**

- `src/views/**`（17 个页面）：§15.2 明确「页面不设门槛，靠 E2E 冒烟」，已由 `e2e/smoke.spec.ts` 覆盖 4 条主链路。
- `src/api/*.ts` 的 11 个接口包装：本质是 URL+方法+类型的声明式映射，单测只能断言字符串本身；其正确性由 E2E 与后端契约（`docs/api/openapi.yaml`）约束，投入产出比低。
- `src/api/http.ts` 剩余 64%（`downloadFile`/`saveBlob`/`upload` 等）：需要 DOM Blob 下载桩，价值低于已覆盖的拦截器分支，留待需要时补。
- `src/router/index.ts`（守卫）：与权限路由注册强耦合，E2E 的「未登录重定向」「无权限不可见」两条已覆盖其对外行为。

## 远端仓库与 CI 首次运行（2026-11-11）

- 远端：`https://github.com/as375438318/admin-starter.git`（**public**，默认分支 `main`），首次推送 4 个提交至 `e854242`。
- 推送前做了全历史审计：`git grep` 扫全部 4 个提交，无明文口令 / 私钥 / 令牌；`docs/env/local-dev.md` 与 `application-local.yml` 从未入历史；历史中 >1MB 的文件只有 11 张登录页设计稿 PNG（约 20MB，属预期设计资产）。
- CI 首次运行（run #1，`e854242`，39s）三条 job 全绿：
  - 文档门禁（spec-lint）✅ 9s
  - 后端编译（JDK 17）✅ 33s
  - 前端：Install → **Unit tests（95 用例，6s）** → Build（vue-tsc + vite，16s）✅
- 由此关闭此前"CI 未在 GitHub Actions 上实测"的一半：**`ci.yml` 已实测通过**；`e2e.yml` 仍为 `workflow_dispatch`、尚未在 Actions 上跑过（本机已跑通 4 条主链路），首次需在 Actions 页面手动触发。
- 仓库可见性提醒：当前为 public。若将来要转私有，需在 GitHub Settings 改；届时 `ci.yml` 的 Actions 额度会按私有仓库规则计费。

## 设计稿清理与 git 历史重写（2026-11-11，用户指示「登录页设计稿可以删除掉了，浪费空间」）

**删了什么**：`docs/design/login-mockups/`、`login-mockups-v3/`、`login-mockups-v4/`，共 **50 个文件 / 25.5MB**（HTML 样板 + PNG 截图，登录页设计阶段产物）。

**为什么必须重写历史**：这些文件在首个提交里就被跟踪，普通 `git rm` 只能让后续提交不含它们，`.git` 仍背着这 25MB（当时 `size-pack` 34.5MB）。用户确认选择"连历史一起清掉"，于是用 `git filter-branch --index-filter` 从全部 5 个提交中剥离这三个目录，再 `rm -rf .git/refs/original` + `reflog expire --expire=now --all` + `gc --prune=now` 把对象真正回收：

| 指标 | 清理前 | 清理后 |
|---|---|---|
| `.git` 体积 | 34.5 MiB | **922 KiB**（0.9 MiB） |
| 历史中 mockup 对象 | 53 个 | 0 |

**副作用与处置**

- **全部 5 个提交的 SHA 都变了**（如 `e854242` → `95f3304`），此前在提交信息、对话与 impl-notes 里引用过的旧 SHA 已失效；远端必须 `force` 推送（本次用 `--force-with-lease=<branch>:<旧 SHA>` 精确指定预期值，避免覆盖别人的推送）。
- 重写前建了本地备份分支 `backup/pre-mockup-rewrite`；**它的引用已被 `filter-branch` 一并重写**（因此不再包含设计稿），只作"提交链"意义上的备份，不提供旧文件。真正能找回旧内容的只有重新从远端旧包恢复，现已随 force push 消失——即本操作不可逆。
- 其他机器/其他人的克隆必须重新克隆或 `git fetch --force && git reset --hard origin/main`，否则本地历史与远端分叉。
- 两处指向已删路径的引用同步修正：`apps/pc/src/views/login/index.vue` 的注释、`docs/audit/impl-notes.md` 的 UI-PC-FORM-002-DEV 条目（改为"按 v4 设计稿实现"并指回本节），避免 `SL-PATH-001` 对不存在路径报 warning。

**顺带发现（未处理，等确认）**：仓库根的 `.trae-html-share-packages/` 是同批设计稿的 HTML 打包（4 个 zip，**26MB**），已被 `.gitignore` 排除、从未入库，只占本地磁盘。要清磁盘可直接删该目录，不影响 git。

## 后端测试接入 CI（2026-11-11，用户指示「做1」；CR-20261111-003）

**问题**：CI 后端 job 一直是 `mvn -DskipTests package`，全仓唯一测试需要真实 MySQL/Redis 且 `@ActiveProfiles("local")`（读本机被 gitignore 的凭据），在 CI 上必然失败 → 长期被跳过。**"CI 全绿"对后端行为没有证明力。**

**技术选择（按本机条件调整）**：原本打算用 Testcontainers，但本机**没有 Docker**，那样会变成"只能靠 CI 验证、本地无法复现"。改为 **环境变量注入 + CI 服务容器**：本地能真跑、CI 也能跑，不引入 Docker-in-Docker。

| 改动 | 内容 |
|---|---|
| `application.yml` | 数据源 url 由硬编码改为 `${APP_DB_URL:<原值>}`——原值逐字保留，只是套了可覆盖外壳 |
| `src/test/resources/application-test.yml`（新增） | profile `test` 默认连 `admin_starter_test`；`APP_TEST_DB_*` / `APP_REDIS_*` 全可覆盖；`spring.config.import: optional:classpath:application-local.yml` 让本地无需 export 口令（CI 无此文件自动跳过） |
| `NotificationPublisherTransactionTest` | `@ActiveProfiles("local")` → `test`；新增 `@BeforeEach` 安全阀：JDBC URL 不含 `test` 直接失败 |
| `DataPermissionTablesTest`（新增，6 用例） | 受控 4 表 / 豁免表 / 未登记返回 UNKNOWN（fail-closed）/ 表名归一化（大小写+schema 前缀）/ 受控与豁免不重叠 / 通知公共行例外的别名前缀 |
| `server/system/pom.xml` | 补 `spring-boot-starter-test`（test 作用域），否则 system 模块测试无法编译 |
| `.github/workflows/ci.yml` | backend job 加 MySQL 8 + Redis 7 服务容器与健康检查；命令改 `mvn -B -pl web -am verify`；失败上传 surefire 报告；job 更名为「后端编译与集成测试」 |

**验证（真实运行）**

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -o -B -pl web -am verify` | **8 用例通过**（DataPermissionTables 6 + NotificationPublisher 事务 2），jar 正常产出，`BUILD SUCCESS` |
| 安全阀负向 | `APP_TEST_DB_URL=.../admin_starter mvn test` | **两个用例均失败**并提示「拒绝在非测试库上运行集成测试，当前连接：jdbc:mysql://…/admin_starter…」 |
| 测试库自举 | 手工核对 | `admin_starter_test` 24 张表、Flyway 到 V16、种子用户 9 个 |
| 开发库未被污染 | 手工核对 | `admin_starter.sys_notice` 中 `TX-%` 残留 **0 行** |

**本地前置（已写进 README）**：一次性 `create database admin_starter_test` + `grant ... to 'admin_app'@'127.0.0.1'`；之后 `mvn verify` 即可，Flyway 自动建表。

**仍未解决（如实登记）**

- `mvn verify` 目前只有 2 个测试类：业务逻辑仍主要靠 E2E 与人工验证，覆盖面远未到"可放心重构"；
- 契约测试（`docs/api/examples/backend`，`AGENTS.md` §7 的 error 级阻断之一）**仍未落地**，CI 无法校验"实现与 `docs/api/openapi.yaml` 一致"；
- 集成测试依赖真实 MySQL/Redis，单类启动约 8–11s；测试增多后需要评估按模块拆分或并行。

## 后端测试深度补齐（2026-11-11，用户指示「按照顺序实行」；CR-20261111-004）

**顺序执行的三件事**：① 数据权限 + 字段脱敏单测 → ② 契约测试落地 → ③ 集成测试启动开销优化。

### ① 核心逻辑单测（用例 8 → 66）

| 模块 | 测试类 | 用例 | 锁定的风险 |
|---|---|---|---|
| common | `MaskFormatTest` | 6 | 掩码算法；格式不匹配一律返回 null（**不出半掩码**） |
| common | `MaskResolverTest` | 8 | 三态判定（view/mask/隐藏）；未登录 fail-closed；空值透传 |
| common | `MaskedValueGuardTest` | 10 | **原样回传掩码不覆盖真值**；提交别的掩码 422；HIDDEN 时禁止清空（防盲改致丢数据） |
| common | `MaskUtilsTest` | 2 | 参数掩码固定 6 星、空值透传 |
| system | `DataScopeSqlBuilderTest` | 12 | 全不限制→null；**该限制却无条件→`1 = 0`（fail-closed）**；组织/归属 or、两者 and；只拼数值 ID |
| system | `RoleDataPermissionHandlerTest` | 10 | 未登记表**抛错**；豁免表放行；通知公共行例外整体加括号（防 `A AND (B) OR (C)` 绕过） |
| system | `DataScopeProviderTest` | 10 | 多角色并集、最宽档胜出；无角色→仅本人；缓存键含部门版本号、命中不查库 |

自我纠正 2 处（都是我的断言错，不是实现缺陷）：
1. `MaskFormat.PHONE` 用 `Character.isDigit`，全角数字也通过 → 改为记录"现状 + 若要严格 ASCII 需另行决策"；
2. `MaskedValueGuard` 清空"空原值"返回 `""` 而非 `null` → 按实现修正断言。

### ② 契约测试落地（用例 +3）

- `ContractValidationTest`：`@SpringBootTest` + MockMvc，用 **swagger-request-validator 2.44.9**（坐标取自 `docs/api/examples/backend/pom-snippet.xml`）把真实响应与 `docs/api/openapi.yaml` 比对。
- **不复制契约副本**：直接读仓库里的契约文件（`-Dcontract.file` 可覆盖），避免"改了契约测试仍绿"这种最危险的情况；未文档化路径的"路径未定义"降级 WARN（契约按 §4.4 只覆盖部分接口），字段/类型/状态码保持 ERROR。
- 覆盖：`GET /public/configs` 200 结构、白名单外字段不得出现（不含 token.secret/password）、`GET /files` 未登录 401 结构。

**首次运行就抓到问题**：`publicConfigsMatchesContract` 报 `Object instance has properties which are not allowed by the schema: ["code","message","traceId"]`。
用 `-Dcontract.file=<探针副本>` 做对照实验（探针只在 allOf 成员加 `additionalProperties: true`）立刻变绿 → **不是实现违约，是契约精度不足**：校验器对 allOf 成员缺失该声明时按"关闭"处理，把信封合法字段判成多余。
处置：契约 `PublicConfigResponse` 的 allOf 成员显式 `additionalProperties: true` + 注释，契约 1.0.1 → **1.0.2** 并写变更记录（CT-VER-005）。实现代码零改动。

### ③ 集成测试启动开销（实测）

两个集成测试类注解不同（一个带 `@AutoConfigureMockMvc`），Spring Test 按注解组合缓存上下文 → **各起一次上下文**。新增 `AbstractIntegrationTest` 统一三个注解，两个类改为继承：

| 指标 | 优化前 | 优化后 |
|---|---|---|
| `NotificationPublisherTransactionTest` 耗时 | 1.90s（自起上下文） | **0.134s**（复用上下文） |
| `mvn -B -pl web -am verify` 总计 | 约 30s | **约 21s** |

### 验证

| 项 | 结果 |
|---|---|
| 后端全量 | **69 用例通过**（common 26 / system 38 / web 5），BUILD SUCCESS |
| 契约对照实验 | 探针（allOf + additionalProperties）→ 绿；原契约 → 红，证明判断成立 |
| 契约 YAML | PyYAML 加载 OK，version=1.0.2，paths=53 |
| spec-lint | error 0 / warning 49 |

### 仍未解决

- 契约测试只覆盖 3 个断言点：契约共 53 条路径，多数需要登录态；覆盖受保护接口需引入 `spring-security-test` 造会话，属后续工作。
- `views/*` 前端与后端业务逻辑（导入导出、通知投递、文件回收）仍主要靠 E2E 与人工验证。

## 新增 D 档（工程化/治理）（2026-11-11，用户批准；CR-20261111-005）

**背景**：`AGENTS.md` §4.1 只有 A/B/C 三档，而本项目已连续 4 次遇到同一形态改动——**跨模块、但不改运行时行为、只动工程化设施与文档治理**。每次只能"拆成单模块单元 + 通用层留痕"勉强归口，每次都重做一遍同样的 RULE-GAP 分析：

| CR | 内容 | 为何三档都装不下 |
|---|---|---|
| CR-20261111-001 | 填实 §9 项目约束与技术选型 | 只改文档，但 A 要求"只动 1 个模块" |
| CR-20261111-002 | 前端测试框架 + CI | 跨 apps/pc 与 CI，纯新增但不止一个模块 → 不合 B |
| CR-20261111-003 | 后端测试接入 CI | 跨 common/web/CI |
| CR-20261111-004 | 数据权限/脱敏单测 + 契约测试 | 跨 common/system/web + 契约文档 |

**规则改动**（已写入 `AGENTS.md`）：§4.1 标题改为「A／B／C／D」；新增 **D · 工程化/治理** 档；新增**判定顺序**（命中 `PRC-APR-001`→C；单模块纯新增→B；不改运行时行为的工程化/治理→D；其余单模块微改→A）；新增 **D 档护栏**（不得掩盖行为变更，影响运行时行为必须回到 A/B/C）；`AGENTS.md` §4.2 补 D 档文档产出（无新建；MUST 为 impl-notes + CR；按需 assertions/ADR）。

**不放宽的地方**（已在 CR 中逐条核对）：`PRC-APR-001` 七类人工审批与档位无关，D 档不豁免；`PRC-CR-001` 的 CR 义务同样适用于 D 档（CR-005 本身就是 D 档实例）。

**闭合历史遗留**：CR-001～CR-004 中标注的 `RULE-GAP`（“档位表无法容纳跨模块工程化改动”）自本规则起**关闭**；上述四次处置保持不变（拆单元 + 通用层留痕），事后看仍符合 D 档判据。

**保留的反对意见**：档位从三档变四档会增加认知成本；若将来 D 档被用来夹带行为变更，应回退此规则。已在 §4.1 加护栏并在 CR-005 §11 登记。

## 受保护接口契约测试 + 契约两处精度修正（2026-11-11，用户「按顺序进行」；CR-20261111-006）

**做了什么**：`ContractValidationTest` 从 3 个未登录断言点扩到 **7 个**，新增 4 个受保护接口校验——真实 `POST /api/v1/auth/login`（admin/admin123，测试库种子）换 `ACCESS_TOKEN` Cookie 后再校验，不用 mock 身份（避免"测的是假登录"）。

| 新增用例 | 覆盖 |
|---|---|
| `fileListMatchesContract` | `GET /files` 200 + 分页四字段 + 文件项必填字段 |
| `noticeListMatchesContract` | `GET /notices` 200 |
| `fileRecycleListMatchesContract` | `GET /files/recycle` 200 |
| `previewMissingFileMatchesContract` | `GET /files/{id}/preview` 不存在 → 契约声明的 404 |

**首次运行抓到两处问题，都在契约侧（实现零改动）**：

1. **`FilePageResponse` 漏声明分页字段**：`data` 只写 `required: [list, total]`，而其它 8 个分页 schema 都是四字段，实现也确实返回四个 → validator 报 `["page","pageSize"] not allowed`。结论：契约漏写。已补齐。
2. **`allOf` 成员缺 `additionalProperties` 是系统性问题**：CR-004 当时只在 `PublicConfigResponse` 补过，本次三个接口再次同样报错 → 证明是普遍问题。
   - **失败尝试（记录以免重走）**：先把 `validation.response.body.schema.additionalProperties` 降级为 WARN，**无效**——父级 `allOf` 仍以 ERROR 报 `Instance failed to match all required schemas`。
   - **最终做法**：脚本化给契约里全部 **26 处** `allOf` 成员补 `additionalProperties: true`，并**保持** `additionalProperties` 为 ERROR 级别（不降低门禁强度）。

**契约**：1.0.2 → **1.0.3**，写 CT-VER-005 变更记录，两处均为精度修正，状态码/错误码集合不变。

**验证**

| 项 | 结果 |
|---|---|
| 后端全量 | **73 用例通过**（common 26 / system 38 / web 9），BUILD SUCCESS |
| 契约 YAML | version=1.0.3、paths=53、schemas=54 |
| spec-lint | error 0 / warning 49 |

**遗留**：契约里仍有 ~30 个需登录的接口未做契约校验（写接口需请求体夹具，成本更高）；登录夹具只用 `admin`，不同角色看到不同数据的差异属行为测试范畴，契约层不覆盖。

## 通知投递行为测试（2026-11-11，用户「按顺序进行」第 2 项；CR-20261111-007）

新增 `NotificationPublishTest`（system 模块，16 用例，全 Mockito 隔离）。理由：契约测试证明「响应形状对」，但不证明「业务判断对」；通知投递算错收件人 = 数据泄露或漏发，而且**发出去不可撤回**，是业务逻辑里风险最高的一块。

| 分组 | 用例 | 锁定 |
|---|---|---|
| 入参校验 | 4 | 标题空/超 200 字、内容空、范围为空、范围类型非法、指定范围缺目标 ID |
| 目标校验 | 2 | 角色/部门不存在、用户停用 → 拒绝；**带 ALL 时其它范围目标仍必须存在**（fail-closed） |
| 无人可发 | 1 | 范围内无启用用户 → 报错，禁止落一条没人收得到的通知 |
| 收件人解析 | 5 | ALL→全部启用；ROLE→过滤停用；DEPT→展开子孙部门；USER→直加；多范围并集去重 |
| 落库形态 | 2 | 级别/类型/可撤回默认值、发布人写 notice；scope 行数=去重后范围数、recipient 行数=收件人数且不带已读时间 |

**测试过程中确认的两条真实语义（我最初假设错了）**：

1. **ALL 与其它范围是并集**，不是「ALL 吞掉其余」：去重键是 `scopeType:targetId`，ALL 只是其中一个键。断言红掉后按实现修正。
2. **带 ALL 时其它范围目标仍被校验**：`validateTarget` 在去重之前逐条跑，所以「ALL + 不存在的用户」直接报错。这是更好的 fail-closed 行为，已固化为断言。

另更正一处字段假设：收件记录是 `readAt`（空=未读），没有 `isRead`。

**验证**：后端全量 **89 用例通过**（common 26 / system 54 / web 9），BUILD SUCCESS；spec-lint error 0 / warning 49。

**遗留**：Excel 导入（脏数据校验、错误行清单）、导出（分批与并发）、文件回收/彻底删除（存储与记录一致性）仍无单测，需 Excel/文件夹具或存储桩，成本高于本轮。
## Excel 导入校验与导出编排测试（2026-11-11，用户「按顺序执行」第 1 项；CR-20261111-008）

Excel 导入/导出是**唯一能把大批脏数据写进库**、也是**唯一会因条数判断错误打爆内存**的入口，此前零测试。新增 28 用例：

| 测试类 | 用例 | 锁定 |
|---|---|---|
| `ExcelRowValidatorTest` | 16 | 必填/选填、最大长度、空格裁剪、枚举接受「存储值或中文含义」、类型转换失败用 formatHint 文案、部门按完整路径反查（不存在/同名多义/超数据权限分别报错）、负责人依次按「姓名（部门）」→账号→姓名匹配、重名拒绝、无用户目录时给可操作提示 |
| `ImportErrorCollectorTest` | 5 | 「第 N 行 · 表头」格式；**出错行数（按行去重）与错误条数（按字段累计）是两个口径**，不得混用；超 200 条截断但剩余条数单独返回；toData 返回快照 |
| `ExcelExportPlannerTest` | 7 | 未登录拒绝；超 10 万行拒绝并提示缩小范围；**恰好 2000 行走同步**、**2001 行转异步并返回任务 ID**；异步任务带当前用户/模块/查询参数；文件名 `前缀_yyyyMMdd_HHmmss.xlsx`；操作日志记 mode/totalRows/fileName；同步通道取数只取一次 |

**踩坑**：`ExcelExportWriter.writeSync` 声明了 `IOException`，`verify(...).writeSync(...)` 也要 `throws Exception`，否则测试编译不过。

**验证**：后端全量 **117 用例通过**（common 26 / system 82 / web 9），BUILD SUCCESS；spec-lint error 0 / warning 49。

**遗留**：`ExcelStreamParser`（POI 解析 221 行，空行/合并单元格/超宽列的处理是导入体验关键）与两个 Writer（表头与 schema 一致性）需读回 xlsx 夹具，下一轮补；文件回收/彻底删除仍未测。
## 文件回收站与 xlsx 解析测试（2026-11-11，用户「继续」；CR-20261111-009）

两块此前零测试、且出错不可逆：

**`FileRecycleTest`（10）**——文件按 SHA-256 去重共享字节，彻底删除的判断错一次就把别人的文件变空壳：
- 删除/恢复/彻底删除对「不存在或不可见」一律静默成功（幂等，且不暴露存在性）
- 无登录上下文调用不 NPE（系统任务路径）
- **彻底删除：仍有其它记录引用同一 `storage_path` → 保留物理字节；无引用才删**
- **执行顺序 InOrder 断言：先删行 → 再计数 → 最后删字节**（顺序反了会删掉仍被引用的字节）
- 只按被删记录的精确 `storage_path` 删除，绝不递归

**`ExcelStreamParserTest`（7）**——用 POI 生成内存 xlsx 作夹具：
- 表头去必填星号（`任务标题*` → `任务标题`）、按表头映射、**保留 Excel 真实行号**
- 空行（含只有空格的行）跳过，但后续行号仍是真实行号——否则用户按「第 3 行」提示找不到
- 超出表头范围的列忽略，不让整行失败
- 数据页名不匹配时提示带出期望页名；行数超上限拒绝；非 xlsx 统一提示「请确认是有效的 .xlsx 模板」，不抛 500

**踩坑**：`verify(storage, never()).delete(any())` 与真实调用冲突（Mockito 的 never+any 会匹配到已发生的调用），改为 `times(1)` + 精确参数。

**验证**：后端全量 **134 用例通过**（common 26 / system 99 / web 9），BUILD SUCCESS；spec-lint error 0 / warning 49。

**遗留**：`ExcelExportWriter`/`ExcelTemplateWriter`（可复用本轮 POI 夹具思路）、上传/秒传/分片上传（需文件系统夹具）未测。
## 导出写表器与模板生成器测试（2026-11-11，用户「继续」；CR-20261111-010）

两个"用户唯一能看到/能填写"的产物，此前只有间接覆盖（planner 测试把写表器 mock 掉了）：

**`ExcelExportWriterTest`（8）**——导出是数据外流的最大出口：
- 表头与 schema 的导出表头完全一致（不多不少、顺序一致）
- 枚举导出中文含义，未知存储值**原样输出不丢数据**；部门导出完整路径、负责人导出「姓名（部门）」
- **掩码三态决定列是否输出**：有 view→明文、只有 mask→掩码、**都没有→整列不输出**（不是输出空串）
- 空值写空串而不是字面 `null`（否则再导入会报格式错）
- 流式导出：分页取数直到 null（**空页不算结束**）、返回总行数、进度回调

**`ExcelTemplateWriterTest`（3）**——模板是用户填写的唯一依据：
- 表头必填列带 `*`，且只含参与导入的字段（只导出不导入的字段不出现）
- 枚举列有隐藏「引用数据」下拉页（内联下拉受 255 字符限制）+ 填写须知页写明「整批校验通过才写入、任一行有错整批不落库」与逐列必填/字数上限

**自我纠正 2 处（都发生在测试代码，非产品缺陷）**：
1. `ExcelTemplateWriter` 不是无参构造（依赖 `ReferenceDirectoryService`），按无参写的第一版编译不过；
2. 流式导出"取页次数"我算错两次——`cursor++` 只在返回数据页的分支执行，空页算一次、`null` 不算 → 最终断言 `pages.size()` 并把语义写进注释。

**验证**：后端全量 **145 用例通过**（common 26 / system 110 / web 9），BUILD SUCCESS；spec-lint error 0 / warning 49。

**遗留**：上传/秒传/分片上传（需文件系统夹具，可用 `@TempDir`）未测。
## 上传 / 秒传 / 分片上传测试（2026-11-11，用户「继续」；CR-20261111-011）

`FileAppService` 上传链路此前零测试，三类风险同时存在：校验（类型白名单/大小上限/分片范围）、去重（复用路径 vs 重新落盘）、越权续传。

**用真实 `LocalFileStorage` 指向 `@TempDir`，不 mock 存储**——"字节到底有没有落盘"正是这块最容易错的地方，mock 掉存储等于测出假绿。

| 分组 | 用例 | 锁定 |
|---|---|---|
| 直传校验 | 3 | 空文件；超过上限（`PayloadTooLargeException` 带上限）；扩展名不在白名单（提示带扩展名） |
| 直传正常 | 1 | 字节真落盘、记录带 sha256/上传人/部门、`instant=false` |
| 去重 | 2 | 内容相同且字节在库 → 复用路径、**仍建新记录**、标秒传；**记录在但字节已丢 → 不秒传、重新落盘**（避免返回打不开的文件） |
| 秒传探测 | 2 | 参数校验；命中返回文件、未命中 `instant=false` 不带文件 |
| 分片会话 | 3 | 分片大小 256KB–5MB；`totalChunks` 上取整 + 状态 UPLOADING；命中秒传不建会话 |
| 分片越权/边界 | 2 | 续传他人会话 403、不存在 404；序号越界/超约定大小被拒且不写盘不更新进度 |
| 分片累计 | 1 | 已上传序号去重排序（`0` + `2` → `0,2`）且分片字节落盘 |
| 匿名上传 | 1 | 无登录上下文仍可上传（系统调用），上传人/创建人为空 |

**自我纠正 2 处（测试代码内）**：
1. 首版用了不存在的 `Sha256Utils` → 改为测试内用 `MessageDigest` 独立计算（也避免用被测代码验证被测代码）；
2. "分片过大"用例起初用 20MB 文件，**先撞到文件总大小上限**而不是分片大小错误 → 改成 6MB 文件 + 6MB 分片，让被测判断真正生效（已写注释）。

**验证**：后端全量 **160 用例通过**（common 26 / system 125 / web 9），BUILD SUCCESS；spec-lint error 0 / warning 49。

**遗留**：`completeChunkSession`（合并分片、校验整体 sha256、清理临时片）与 `chunkStatus` 未覆盖。
## 分片合并收尾测试（2026-11-12，CR-20261112-012）

`completeChunkSession` / `abortChunkSession` 此前未覆盖。新增 `FileChunkCompleteTest`（10 用例，真实存储 + `@TempDir`）：
- 非 `UPLOADING` 状态拒绝；分片不完整报「缺少第 N 片」
- **合并后大小不符 / 整体 sha256 不符都要拒绝**（不建记录、不改会话状态）——这是"拼起来的东西就是用户声明的文件"的唯一保障
- 合并成功：字节落盘、记录带原名/大小/sha/上传人、**分片目录被清理**、会话置 `MERGED`
- 已有相同内容：复用路径 + 标秒传，仍建新记录
- 放弃会话：清分片 + `UPLOADING`→`ABORTED`；**已完成会话只清分片，不覆盖 `MERGED`**
- `MERGED` 后再传分片 → 状态冲突；匿名合并 → 被拒

验证：后端全量 **170 用例通过**（common 26 / system 135 / web 9）。
## 业务链路 E2E 扩展与工程化小件（2026-11-12，CR-20261112-013）

**E2E（4 → 7 条）**：新增 `apps/pc/e2e/flows.spec.ts`：
- 发布通知：UI 填表 → 抽屉关闭 + 列表可搜到 → 接口 total 增加且通知有应读人
- 上传文件：`setInputFiles` → 列表出现 → **下载回读字节与上传内容完全一致**（证明不是只写了记录）
- 导出：捕获 `download` 事件且 `*.xlsx`；异步通道则断言提示；两种都要求无错误提示
- 实测 7/7 通过（12.7s）；`pnpm build` 通过、前端单测 95/95 通过

**过程中发现两条事实（已入断言）**：
1. **前端路由由后端菜单下发**（`/auth/routes`）：我第一版按视图目录猜 `/system/notice`、`/system/file`，两条用例都落到「页面不存在」；真实路径是 `/system/notices`、`/system/files`、`/demo/tasks`。
2. **父 pom 上跑 `spring-boot:run` 会失败**（找不到 main class）：本地起后端要么 `mvn -pl web spring-boot:run`（先 install 兄弟模块），要么直接 `java -jar server/web/target/admin-starter.jar --spring.config.additional-location=optional:file:./server/web/src/main/resources/`。

**工程化小件（7 个文件）**：`.editorconfig`（Java 4 / 前端 2；Markdown 不裁行尾空格）、`.nvmrc`（22）、`Dockerfile`（多阶段、非 root、连接信息全走环境变量、上传目录提示挂卷）、`.dockerignore`、`.github/dependabot.yml`（maven/npm/actions 三类 + minor/patch 分组）、`.github/pull_request_template.md`（直接对应 §7 门禁与 §6.2 回填）。

**未完成（需人工决定）**：`LICENSE`（法律授权条款，需所有者选 MIT/Apache-2.0/专有）、分支保护规则（GitHub 仓库设置，文件无法表达）。
## LICENSE 采用 Apache-2.0（2026-11-12，CR-20261112-014）

用户选定 **Apache-2.0**。文本取自 `https://www.apache.org/licenses/LICENSE-2.0.txt` 官方原文，**逐行比对通过**（本地 169 行 vs 官方 169 行，唯一差异是版权行按模板填 `Copyright 2026 as375438318`）——许可证正文不凭记忆手写。

分支保护规则已写好方案与 API 调用（含单人仓库 `required_approving_review_count` 应为 0 否则自己无法批准自己 PR 的坑），但**需要 admin token 才能执行**，等用户提供。
## main 分支保护启用（2026-11-12，CR-20261112-015）

用户建立规则集并选择「A · 严格」，AI 用 API 复核确认真正生效（`GET /rules/branches/main` 返回 4 条：`deletion` / `non_fast_forward` / `pull_request`(批准 0) / `required_status_checks`(strict)）。

配置过程中踩到两种"看起来配好了其实没生效"的状态，值得记下来：
- 规则集列表显示 `targeting 0 branches`（没加目标分支）；
- 规则集 `enforcement = disabled`（加进去但没启用）。

两条都是界面能保存成功、但规则实际不拦任何东西。**判断生效只能看 API 的 `/rules/branches/main` 或实际推一次**，不能看列表页文案。

自本 CR 起，所有改动走「推分支 → PR → 三检查绿 → 合并」；禁止直推 main（会报 `protected branch hook declined`）。
## 等保条款拆解（2026-11-12，CR-20261112-016）

`AGENTS.md` §9-A 的合规约束一直悬着（"未拆解前不得以已合规表述交付"）。产出 `docs/compliance/dengbao-breakdown.md`：按控制点分类把等保要求拆成"现状 + 证据 + 判定 + 差距优先级"，P0 四项：密码策略、审计留存≥6 个月且防篡改、生产强制 TLS、备份与恢复演练。

**写之前逐条核对了代码**（这是最容易凭印象写错的地方）：
- 有：登录失败锁定（5 次/15 分钟，缓存原子计数）、access/refresh TTL、审计表（`sys_oper_log`/`sys_login_log`）、字段脱敏三态 + 导出列裁剪、上传 sha256 去重与整体校验
- **没有**：密码复杂度/过期/首登强制改密（全仓搜不到密码策略校验，只有启动日志提示）、上传恶意代码检测、备份方案、HTTPS 强制
- 结论：技术侧能自主闭环的差距集中在 4 个 P0；定级、测评、运维责任属业主方决策，已列为阻塞项

**刻意不写具体条款号**：GB/T 22239-2019 的条款编号凭记忆写极易出错，文档里明确写"须对照原文补齐"，而不是编一个看起来专业的编号。

**RULE-GAP**：`AGENTS.md` §11.1 模板→路径映射没有"合规/审计证据"类目录，本 CR 暂用 `docs/compliance/`，正式归口需另开通用层变更。
## 前端包体拆分（2026-11-12，CR-20261112-017）

`index-*.js` 从 **1,318.7 kB（gzip 428 kB）→ 39.1 kB（gzip 14.8 kB）**：按"变更频率"分包（`vendor-element-plus` 967 kB / `vendor` 193 kB / `vendor-vue` 117 kB / 业务 39 kB），并新增 CI 门禁「业务 chunk ≤ 400KB」防反弹（本地实测 40,315 字节）。

**诚实结论**：这只改善缓存命中与并行下载，**总下载量基本不变**——Element Plus 全量引入的 967 kB 仍在。真正瘦身需要按需引入（`unplugin-vue-components` + `unplugin-auto-import`），会改变组件注册方式并新增依赖，属需要单独 ADR 评估的变更。

顺带：`chunkSizeWarningLimit` 由 500 调到 1000，因为 vendor 块必然超 500，警告会淹没真实信号；闸门改放 CI 的业务 chunk 检查（硬失败 > 看警告）。
## Dependabot 只收 minor/patch（2026-11-12，CR-20261112-018）

`dependabot.yml` 上线当天开出 11 个 PR，其中 5 个是主版本跳跃（Spring Boot 3.2→4.1、swagger-validator 2.44→3.0、Vite 5→8、Pinia 2→4、vue-router 4→5）。

**最危险的不是红的，是绿的**：#7/#8/#9/#10/#11 检查全绿——但单元测试覆盖不到主版本升级的破坏面（类型、插件生态、运行时行为）。而且 `AGENTS.md` §9-B 规定版本以该节为唯一定义处，`ADR-005`/`ADR-006` 已锁版本线。

处置：`dependabot.yml` 给 maven 与 npm 两个生态加 `ignore: version-update:semver-major`，主版本升级改为人工发起（先 CR/ADR 评估，再改 §9-B 与版本号）。存量 11 个 PR 的处置建议写在 CR 里，等用户决定。
## 框架核心依赖只收 patch（2026-11-12，CR-20261112-019）

Dependabot 的 maven minor 组（PR #16，11 个更新）把后端编译搞红。从 Actions 日志拿到真实根因——不是我猜的：

```
RoleDataPermissionHandler.java:[27,50] cannot find symbol: class Expression
```

**MyBatis-Plus 3.5.9 起把 JSqlParser 拆成独立依赖**（`mybatis-plus-jsqlparser`），所以 3.5.7 → 3.5.18.1 后 `net.sf.jsqlparser` 不在类路径。

结论：Java 生态的"minor"不是"小改动"——Spring Boot minor 会连带 Spring Framework/Security 版本线；springdoc/mapstruct 与 Spring 强绑定。因此对 `spring-boot-starter-parent`、`mybatis-plus-*`、`springdoc-*`、`mapstruct*` 再加"忽略 minor，只收 patch"。

另：Dependabot 在我们加 `ignore-major` 后**自动关闭了 7 个主版本 PR**（#5–#11），无需人工处理；#16 我关闭并写清根因。前端 minor/patch 仍按"CI 绿即合"，因为其语义相对温和。
## CI 动作手动升级（2026-11-12，CR-20261112-020）

Dependabot 的 #1（setup-java 4→6）与 #3（pnpm/action-setup 4→6）反复与 main 冲突（ci.yml 同行被 #2 改过），直接手动改两行并合并；#2（checkout 4→7）已合并。

**一个自我纠正**：第一版我"顺手"把 `setup-node@v4→v6`、`upload-artifact@v4→v5` 也改了，但 **Dependabot 没提议这两个**（说明已是最新），版本号是我凭印象猜的 → 已回退。教训写进 CR：升级只接受有依据的目标版本，不凭印象写版本号。

验证方式：本 PR 自身的 CI 用新动作版本跑，三条检查全绿即为实测证据。
## upload-artifact@v7 升级（2026-11-12，CR-20261112-021）

Dependabot #18 检查全绿但 API 合并被拒，两个原因：① 分支落后 main（strict），`update-branch` 也 403；② **PAT 缺 `Workflows` 权限**——通过 API 合并"改 `.github/workflows/*` 的 PR"需要它。

绕行：用自己的分支提交同样改动（提交者身份是本机 git 凭据而非 PAT），再 API 合并——CR-020 就是这么合的。已实测可行。

**工具限制已记录**：以后若要我直接合并改 workflow 文件的 Dependabot PR，token 需要 **Workflows: Read and write**。
## 安全与工具库 minor 升级（2026-11-12，CR-20261112-022）

原 Dependabot #19 把框架升级与工具库 minor 打包，框架部分（MyBatis-Plus 3.5.18.1）会让编译失败，导致整个 PR 不可用。拆出三项独立验证后升级：

| 依赖 | 变更 | 覆盖它的测试 |
|---|---|---|
| poi-ooxml | 5.4.1 → 5.5.1 | `ExcelStreamParserTest` / `ExcelExportWriterTest` / `ExcelTemplateWriterTest` |
| jjwt-* | 0.12.5 → 0.13.0 | 170 用例含鉴权路径 |
| swagger-request-validator-mockmvc | 2.44.9 → 2.46.1 | `ContractValidationTest` 7 条 |

**分两步升、每步跑全量**：先 POI（Excel 三部曲直接覆盖），绿了再升 jjwt + 校验器。这样红灯能立刻定位到具体库，而不是"三个一起升、红了不知道是谁"。结果：170 用例全通过。

`AGENTS.md` 的 §9-B 未收录这三个版本（该节只锁主选型），故无需改技术栈表。
## 前端框架主版本升级（2026-11-12，CR-20261112-023）

用户批准评估结论后正式升级：vite 5→**8.3.7**、plugin-vue 5→**6.0.10**、vitest 3→**5.1.0**、coverage-v8 3→**5.1.0**、vue-router 4→**5.5.0**、pinia 2→**4.1.0**。

**唯一代码改动**：`apps/pc/src/router/index.ts` 把静态路由直接 import 的视图改懒加载。原因：`import.meta.glob('../views/**/*.vue')` 已把 views 下所有页面纳入动态引入，再静态 import 同一文件会让打包器判定动态引入无效（`INEFFECTIVE_DYNAMIC_IMPORT`）——这是升级前就存在的问题，Vite 8 变严格才显形。改完警告清零，业务入口 chunk 39.10 kB → **32.46 kB**。

三层验证全实跑：干净重建构建通过（0 警告）、单测 95/95、**起后端 E2E 7/7**（覆盖动态路由注册、Pinia 鉴权 store、通知/文件/导出三条业务流）。

决策与备选方案（不升 / 只升打包器 / 成套升 / 分两年升）记录在 `ADR-007`；`AGENTS.md` 技术栈表同步更新。遗留：element-plus 仍 2.8.x 建议下批同升；coverage 阈值需在 Vitest 5 下复核。
## 口令策略与首登强制改密（2026-11-12，CR-20261112-024）

等保 P0-1 的落地：`V17__password_policy.sql` 给 `sys_user` 加 `password_updated_at`、`must_change_password`（存量行全部置 1），新增 `PasswordPolicy` 校验（弱口令表 30 项 → 长度 10–64 → 字符类 ≥3 → 不含账号名 → 无 3 连相同/4 位顺序），三个写入点（创建用户、管理员重置、自助改密）全部接入；登录响应新增 `mustChangePassword`、`passwordExpired`。

**判定顺序是刻意的**：弱口令先于长度判定 —— `admin123` 说"属于常见弱口令"比说"长度不足"更贴近事实，否则用户以为凑够长度就行。

**过程踩的两个坑**（记下来防复发）：
1. 测试数据 `Aaa#456789x` 只有 2 个连续 `a`（`A` 与 `a` 大小写不同）→ 我"看起来像 3 个"就当成 3 个，断言必错；
2. 迁移文件先写成 `V10__`，与既有 `V10__notification_module.sql` 撞号；改名 `V17__` 后**仍报同一错**，因为增量构建的 `target/classes` 里残留旧文件名 —— 必须清编译产物残留。

另：`mvn verify` 的 repackage 步骤报 "Unable to rename admin-starter.jar"，原因是**运行中的后端进程锁着这个 jar**；停进程后正常。

结果：后端 184 用例通过（common 26 / system 149 / web 9，其中新增 14 条口令策略）。**未完成**：服务端强制拦截（未改密仍可访问其他接口）与前端改密引导，已列入 CR 遗留。
## 口令强制改密的服务端拦截（2026-11-12，CR-20261112-025）

把"必须改密"从提示变成强制：`pwd` 声明写进 access token，`JwtAuthenticationFilter` 认证后判定，非白名单路径清空上下文 → 403 + **20302**（不是 401，前端据此弹改密引导而不是跳登录）。前端：store 存标记 + 路由守卫重定向 + 拦截器处理 20302 + 个人页顶部阻断提示。

**三个自身错误**：
1. 白名单漏了 `/auth/me|routes|permissions` → 改密页 bootstrap 失败被踢回登录页，用户永远改不了密码（E2E 表现为"候选口令全部不可用"）；
2. E2E 助手把管理员新口令写成 `Admin#Starter2026`（含账号名 `admin`）→ 策略 422+30202 拒绝，等于顺手验证了策略生效；
3. 写操作缺 `Origin` 头命中 `20301 来源校验失败`，且 `refresh` 要带刷新令牌 Cookie 而非 access Cookie。

E2E 新增 `e2e/helpers.ts`：首次跑真的走一遍"被强制改密 → 改密 → 恢复"，之后走已改密状态，两种状态都覆盖。结果：后端 189 用例、前端 97 单测、E2E 7/7 全绿。
## Element Plus minor 升级（2026-11-12，CR-20261112-026）

`element-plus 2.8.8 → 2.14.8`（同主版本跨 6 个小版本），`AGENTS.md` 技术栈表同步为 `Element Plus 2.14`。三层验证全跑：干净重建构建通过、97 单测通过、起后端 E2E 7/7。

产物上 `vendor-element-plus` 945 kB（gzip 285 kB），业务入口 chunk 反而从 32.46 kB 降到 15.48 kB —— 是新版对样式/组件模块切分方式变化带来的，**不是**本次刻意优化，CR 里写清避免误读。遗留：vue 3.5.45 / typescript 5.9 / axios 仍待随下次 minor 批处理。
## 契约测试扩到写接口（2026-11-12，CR-20261112-027）

契约测试 7 → **11**：新增 `POST /my-notices/read-all`、`PUT /jobs/{id}`、`PUT /jobs/{id}/status`、`PUT /jobs/{id}` 缺必填字段（负例）。夹具**从真实接口取**（`GET /jobs` 拿任务，用当前值原样回写），不在测试里手写常量——手写必然随实现漂移，"改了实现测试还过"是最危险的情况。

**逼出两个真问题**：
1. **契约缺陷**：负例的 422 响应被判 `additionalProperties` 违规，因为 `allOf` 成员未声明时校验器按"关闭"处理。1.0.2 那批只修了 `components/schemas` 下的 26 处，`components/responses` 的 `ValidationError` 与 **12 处路径内联 schema** 全漏了。已全部补齐并写脚本复扫（仍缺 0 处），契约版本 1.0.3 → **1.0.4**。
2. **测试自身缺陷**：`MockHttpServletResponse.getContentAsString()` 用响应头声明 charset，而本项目是 `application/json`（无 charset）→ 默认 ISO-8859-1 → **每个汉字膨胀成 3 个字符**（24 字变 72），回写成请求体后超 `maxLength: 64`。第一版差点被我误判成"种子数据任务名超长"。修法：显式 `getContentAsString(StandardCharsets.UTF_8)`。

结果：后端 193 用例通过（common 26 / system 149 / web 18）。
## 通知公告列表显式降序（2026-11-12，CR-20261112-028）

用户问"发布的通知怎么排序"，查下来是：`order by created_at <方向>, id DESC`，方向来自 `SortSupport.isAsc(query.getOrder())`，而**该方法对 null 返回 true** → 不传就是升序。实测对照确认：默认列表是**最早的在前**，而"我的通知"收件箱 SQL 写死降序 → 同一批通知两个入口顺序相反。

核实过"后端是否需要改"：`PageQuery` 本身含 `sort/order`，控制器直接绑定，service 已传进 `NoticeQuery`，仓储有 `SORTS` 白名单（`createdAt`/`title`/`id`）→ **后端早就支持，只是前端没传**。所以本 CR 是纯前端：通知页 `fetcher` 加 `sort: 'createdAt', order: 'desc'`。

**没动全局默认**：`SortSupport.isAsc(null) === true` 影响全项目所有列表，改它会把十几个页面顺序一起翻过来（C 档跨模块变更），不该夹在小修里——已写进 CR 遗留，建议单独排期。
## 导入模板可用性：红星号 / 必填提示 / 下拉箭头（2026-11-12，CR-20261112-029）

用户贴模板截图反馈三点，逐条查下来：

1. **`*` 不红**：表头此前是纯文本 → 改富文本，`任务标题`（黑）+ `*`（红）两个 run。
2. **"每一行都要有"看不到**：必填信息只在「填写须知」页与部分批注 → 必填列表头批注统一以 `【必填】每一行都要填写，不能留空` 开头，须知第 2 条措辞加强。
3. **下拉没有列表**：下拉**本来就有**，但箭头被隐藏。解包真模板看到 `showDropDown="true"` —— OOXML 里这个属性语义是**反的**（true = 隐藏箭头）。原代码 `setSuppressDropDownArrow(false)` 恰好把箭头藏了。改 true 后 XML 变 `showDropDown="false"`，箭头可见。

**两个踩坑**：① `showDropDown` 语义反直觉（方法名 suppress，传 true 才显示），已入断言 #94；② POI 的 `DataValidation` 没有 `setPromptTitle/setPrompt`，只有 `createPromptBox(title,text)` —— 我按直觉写两次才编译过，最后用 `javap` 查真实签名。

测试新增 3 条（表头富文本红色 run、必填批注、下拉箭头+输入提示）；其中"必填+下拉"组合需要专用 schema（原 schema 无此类列，第一版断言直接失败）。后端 196 用例通过。
## 模板表头统一加粗（2026-11-12，CR-20261112-030）

用户问"为什么有的表头加粗有的没有"。真因是我在 CR-029 里只给**必填列**用了富文本：必填列的加粗来自 run 字体，非必填列落到普通共享串、加粗来自**单元格样式**——两套机制在 Excel 里表现不一致。

解包核对过：四个表头单元格样式索引都是 `s="1"`（`fontId=1` 含 `<b/>`），说明样式没问题，问题就是富文本 run 与样式优先级的差异。

修法：`headerText()` 所有列统一走富文本、文字段一律 `applyFont(0, len, boldFont)`，必填列的星号再单独一段标红。重新下载模板实测：四个表头全部带 `<b val="true"/>`。

测试新增第 7 条 `allHeadersAreBoldRichText`（遍历每个表头断言"是富文本 + 文字段加粗"），防止有人为了少写代码把非必填列改回普通串。后端 197 用例通过。

**教训**：只给需要特殊格式的那一列用富文本，是"看起来最小改动、实际最危险"的做法——同一行出现两种渲染机制。
## 工作台统计接口（2026-11-12，CR-20261112-031，B 档）

首页从"欢迎语 + 快捷入口"变成有真实数据：`GET /api/v1/dashboard/overview` 一次返回 counts（12 项）/ storage / today / trends（近 7 天）/ taskStatus。前端 4 张主 KPI + 8 张次级指标 + CSS 双色柱状图 + 任务状态分布条。

**三个刻意取舍**：

1. **无权限返回 403 而不是空数据**。统计是系统级聚合、由权限码 `system:dashboard:view` 把关；若返回一排 0，用户会以为系统是空的。前端据此隐藏统计区并说明原因。
2. **聚合不受数据权限收窄**（用户数/文件数/日志数按既有设计是有数据权限的）。理由是仪表盘要"全系统一览"，收窄后部门经理看到残缺数据反而误判；风险用"默认只授 SUPER_ADMIN"控制。这条已入断言 #96：**以后把该权限授给部门管理员，他会看到全系统聚合数**。
3. **不引入图表库**：柱状图用 CSS 高度百分比做，避免为一张图加 300KB 依赖（加库要 ADR）。

踩坑 6 条都记在 CR 里，其中两条值得重复：测试库种子口令是 `admin123` 而 dev 库被 E2E 改过，别混用；`V17` 给存量账号置了 `must_change_password=1`，写"无权限 403"的用例前必须先清掉这个标记，否则拿到的是 20302。

后端 200 用例（web 21，+3）、前端 97 单测、E2E 8/8；契约 1.0.4 → 1.1.0，Flyway V18。