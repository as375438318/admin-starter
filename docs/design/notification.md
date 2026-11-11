# 通知 / 站内信（设计 v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（2026-11-09） |
| 状态 | APPROVED（按用户 2026-11-09 批准的范围实施） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | to-be 设计：通知模型、接收范围口径、已读语义、发送服务接口、轮询取舍；实现与实测见 `docs/audit/impl-notes.md` |
| 依赖 | `docs/design/data-model.md`、`docs/design/permission-matrix.md`、`docs/baseline/assertions.md` #2/#19 |

## 1. 范围

- 本轮只做“站内信”这一个投递渠道，但**按“通知”抽象建模**：渠道（站内信）与通知本体分离，未来增加邮件/短信时复用同一通知记录与接收范围，不另建消息表。
- 管理端：列表（受数据权限）、发布、撤回、删除、已读统计（应读 / 已读 / 谁没读）。
- 用户端：我的消息分页（按已读/未读筛选）、未读数、点开即已读、全部已读。
- 发送能力对外暴露为**应用服务接口**（`NotificationPublisher`），供定时任务失败、密码重置等场景在事务内/外调用，不只有 HTTP 入口。

## 2. 现状（2026-11-09 核对）

| 事实 | 结论 |
|---|---|
| 库内无 notification / notice / message 相关表 | 本轮为**新增模块**，不改造既有表 |
| `sys_menu` 系统管理目录 id=1，子菜单按业务键插入（V5/V8 风格） | 新菜单沿用“按业务键匹配、不硬编码 id” |
| `DataPermissionTables` 未登记表在受控查询中直接报错 | 三张新表必须显式登记（`assertions.md` #2） |
| 当前最高迁移版本 V9 | 本轮只新增 `V10`，不改 V1–V9 |
| 申请数据权限按 `dept_id` 组织范围 + `create_by` 归属过滤 | 通知主表需带 `dept_id`（发布人部门）与 `create_by`（发布人） |

## 3. 为什么按“通知”抽象，而不是消息表

| 维度 | 消息表（否决） | 通知模型（采用） |
|---|---|---|
| 语义 | “一条消息 = 一个收件人”，群发要复制 N 行 | “一条通知 = 一份内容 + 一组接收范围”，按人只记录应读/已读 |
| 范围扩展 | 新增“按角色/按部门”要改表结构 | 范围是独立行（`sys_notice_scope`），组合即并集 |
| 统计 | 应读数靠 `count(*)`，撤回/删除后口径易漂移 | 发布时快照 `sys_notice_recipient`，应读数固定可追溯 |
| 复用 | 其他模块直接操作消息表，绕过权限与审计 | 只能调用 `NotificationPublisher`，统一校验与审计 |

## 4. 数据模型

### 4.1 `sys_notice`（通知本体）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `title` | VARCHAR(200) NOT NULL | 标题 |
| `content` | TEXT NOT NULL | 内容（纯文本，前端按文本渲染，禁止当 HTML 注入） |
| `level` | VARCHAR(16) NOT NULL DEFAULT 'NORMAL' | `NORMAL` 普通 / `IMPORTANT` 重要 / `URGENT` 紧急 |
| `type` | VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' | `SYSTEM` 系统 / `BUSINESS` 业务 / `SECURITY` 安全 / `TASK` 任务 |
| `is_revocable` | TINYINT(1) NOT NULL DEFAULT 1 | 是否允许撤回；`false` 时撤回接口返回 409 |
| `revoked_at` | DATETIME NULL | 撤回时间；非空即已撤回 |
| `revoked_by` | BIGINT NULL | 撤回人 |
| `dept_id` | BIGINT NULL | **发布人部门**，供数据权限按组织范围过滤；系统调用可为空 |
| `create_by` | BIGINT NULL | 发布人；系统调用可为空 |
| `created_at` / `updated_at` / `deleted` | | 与既有表一致；`created_at` 即发布时间 |

索引：`idx_notice_created (created_at)`、`idx_notice_dept (dept_id)`、`idx_notice_create_by (create_by)`。

### 4.2 `sys_notice_scope`（接收范围规则）

| 列 | 类型 | 说明 |
|---|---|---|
| `notice_id` | BIGINT NOT NULL | 所属通知 |
| `scope_type` | VARCHAR(16) NOT NULL | `ALL` 全员 / `ROLE` 指定角色 / `DEPT` 指定部门 / `USER` 指定用户 |
| `target_id` | BIGINT NULL | `ROLE`→角色 id；`DEPT`→部门 id；`USER`→用户 id；`ALL`→NULL |

- 多条范围之间是**并集**（OR），不是交集。
- `DEPT` 的语义固定为“该部门及其全部下级部门”，见 §5。
- `(notice_id, scope_type, target_id)` 建唯一键；`ALL` 的 `target_id` 为 NULL，MySQL 唯一键允许多个 NULL，故 `ALL` 去重由应用层保证（同一通知至多一行 `ALL`）。

### 4.3 `sys_notice_recipient`（应读人快照 + 已读状态）

| 列 | 类型 | 说明 |
|---|---|---|
| `notice_id` | BIGINT NOT NULL | 通知 |
| `user_id` | BIGINT NOT NULL | 应读人 |
| `read_at` | DATETIME NULL | 阅读时间；NULL = 未读 |

索引：`uk_notice_recipient (notice_id, user_id)`、`idx_recipient_notice_read (notice_id, read_at)`、`idx_recipient_user_read (user_id, read_at)`。

- **发布时一次性快照**：由 `sys_notice_scope` 展开为具体用户写入本表。
- 快照后组织变化、新用户创建、角色调整**不补发历史通知**；已读统计以快照为准（见 §12 风险）。

## 5. 接收范围口径（最终）

| 范围 | 展开规则 |
|---|---|
| 全员 `ALL` | 全部 `deleted=0 AND is_enabled=1` 的用户 |
| 指定角色 `ROLE` | 绑定了该角色（`sys_user_role`）且启用未删除的用户 |
| 指定部门 `DEPT` | **该部门 + 其全部下级部门**内的启用未删除用户（`sys_dept.ancestors` 用 `FIND_IN_SET` 取子树）。语义固定，不提供“仅本部门”开关 |
| 指定用户 `USER` | 指定用户 id 中启用未删除者；已删除/停用的用户不再补入 |

- 组合范围 = 上述结果的**并集**；同一用户重复命中只留一行（`uk_notice_recipient`）。
- 停用/删除用户不进入快照；发布后被停用/删除的用户仍保留在快照中（已读统计口径不变）。
- 前端“指定部门”下拉的提示文案必须写明“含下级部门”，与后端一致。

## 6. 已读语义（最终）

| 行为 | 口径 |
|---|---|
| 未读数 | `sys_notice_recipient` 中 `user_id = 当前用户 AND read_at IS NULL AND 通知未撤回未删除` 的条数 |
| 点开即已读 | 用户端详情接口返回前写入 `read_at`（幂等，已有值不覆盖） |
| 标记已读 | 详情接口内置，不单独暴露“单条已读”接口 |
| 全部已读 | 将该用户全部未读（且通知未撤回未删除）的 `read_at` 置为当前时间 |
| 撤回 | 用户端列表/未读数/详情均**不再返回**该通知（不可见）；已读快照保留，管理端统计仍可查 |
| 删除 | 通知逻辑删除，同时级联逻辑删除范围与快照；用户端不可见，管理端列表不再出现 |
| 统计 | 应读 = 快照行数；已读 = `read_at` 非空数；未读名单 = `read_at` 为空的人（含昵称、部门、是否已读、阅读时间） |

- 用户端列表按“发布时间倒序”；筛选 `ALL / UNREAD / READ`。
- 撤回后不展示“已撤回”占位，直接从用户端消失（避免用户端出现无内容条目）；管理端列表以状态列展示。

## 7. 数据权限

| 表 | 登记 | 理由 |
|---|---|---|
| `sys_notice` | `CONTROLLED`（owner/self = `create_by`） | 管理端列表按角色组织范围（`dept_id`）与归属过滤（`create_by`）收敛；与文件、示例业务同口径 |
| `sys_notice_scope` | `EXEMPT` | 仅随通知 id 读取，不单独承载可见范围 |
| `sys_notice_recipient` | `EXEMPT` | 个人收件箱按 `user_id` 精确过滤，不参与组织范围 |

- 管理端列表、详情、撤回、删除、统计都走 `DataPermissionContext.controlled`；越权详情统一返回 `404 资源不存在`（与示例业务一致）。
- **全员通知例外（2026-11-09 用户拍板）**：某条通知的接收范围包含 `ALL`（全员）时，该通知对所有持有 `system:notice:list` 的管理员可见（列表、详情、已读统计），**不受发布人部门/归属的数据权限过滤**；非全员通知仍按既有数据权限过滤。
  - 实现口径：数据权限 SQL 条件与 `exists (select 1 from sys_notice_scope s where s.notice_id = <通知>.id and s.scope_type = 'ALL' and s.deleted = 0)` 取 `OR`；仅影响查询可见性。
  - **授权不变**：撤回 `system:notice:revoke`、删除 `system:notice:delete` 仍只给 `SUPER_ADMIN`；部门管理员即使能看到全员通知，也看不到按钮、调不到接口（`403`）。
  - 前端不加新按钮，仅列表/详情数据可见性放开。
- 用户端收件箱**不进入**数据权限上下文：它按 `user_id` 精确取本人应读，叠加组织范围会漏消息。
- 系统调用发布的通知 `dept_id` 为空且非全员范围：不受组织范围限制的角色可见，受限角色不可见；这是有意的“系统通知仅超管可见”口径，需在发布入口提示。若系统通知使用 `ALL` 范围，则按上面的例外对所有持 `system:notice:list` 的管理员可见。

## 8. 发送服务接口（供其他模块调用）

```java
// com.adminstarter.system.application.NotificationPublisher
Long publish(NotificationCommand command);              // REQUIRED：有外层事务则加入，无则自建
Long publishInNewTransaction(NotificationCommand command); // REQUIRES_NEW：业务回滚也保证通知发出
```

- `NotificationCommand`：`title`、`content`、`level`、`type`、`isRevocable`、`scopes`、`publisherId`（可空）、`publisherDeptId`（可空）。
- 事务边界：
  - 定时任务失败、业务失败后必须通知 → `publishInNewTransaction`（独立事务，业务回滚不影响通知落库）。
  - 业务成功同事务通知（如“审批通过并通知”）→ `publish`，业务回滚则通知一并回滚。
- 两个方法都经 Spring 代理进入，禁止在同类内部自调用（自调用不触发事务）。
- 调用方示例（后续定时任务模块使用）：

```java
notificationPublisher.publishInNewTransaction(NotificationCommand.builder()
        .title("定时任务执行失败")
        .content("任务 demo-task-cleanup 于 2026-11-09 03:00 失败：连接超时")
        .level("URGENT")
        .type("TASK")
        .scopes(List.of(Scope.ofRole("SUPER_ADMIN")))
        .build());
```

- 服务层是唯一写入口；HTTP 发布接口也调用同一服务，不复制接收人展开逻辑。

## 9. 未读数刷新：为什么先轮询

| 方案 | 结论 |
|---|---|
| 轮询（采用） | 前端 30 秒拉一次未读数；窗口重新获得焦点时立即拉一次。实现零新增基础设施，失败可降级 |
| WebSocket | 需要长连接网关、心跳、断线重连、多实例广播（Redis pub/sub）；当前单实例、消息量低，收益不抵复杂度 |
| SSE | 同 WebSocket，且反向代理需关缓冲；同样需要多实例广播 |

- 不引入理由：站内信对延迟不敏感（分钟级可接受）；系统目前单实例部署，无消息推送基础设施；轮询接口只查一个计数，成本可控。
- **切换触发条件**（满足任一即评估 WebSocket/SSE）：① 未读延迟要求降到 1 秒级；② 在线用户数达到单实例轮询 QPS 瓶颈（以 30 秒 × 在线人数估算并压测确认）；③ 需要“新消息即时弹窗/声音提醒”；④ 部署变为多实例且必须广播同一事件。
- 前端轮询在页面隐藏时暂停（`document.visibilityState`），避免后台标签页持续请求。

## 10. 接口清单

### 10.1 管理端（`/api/v1/notices`）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/v1/notices` | `system:notice:list` | 分页列表（关键词、级别、类型、状态、时间范围；受数据权限） |
| POST | `/api/v1/notices` | `system:notice:create` | 发布通知（同一服务接口） |
| GET | `/api/v1/notices/{id}` | `system:notice:list` | 详情（含范围与统计摘要；受数据权限） |
| POST | `/api/v1/notices/{id}/revoke` | `system:notice:revoke` | 撤回；不可撤回或已撤回返回 409 |
| DELETE | `/api/v1/notices/{id}` | `system:notice:delete` | 逻辑删除并级联范围/快照 |
| GET | `/api/v1/notices/{id}/readers` | `system:notice:read-stats` | 已读/未读名单分页（`readStatus=READ｜UNREAD`） |

### 10.2 用户端（`/api/v1/my-notices`，仅需登录态）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/my-notices` | 我的消息分页（`readStatus=ALL｜UNREAD｜READ`，排除已撤回/已删除） |
| GET | `/api/v1/my-notices/unread-count` | 未读数（轮询用） |
| GET | `/api/v1/my-notices/{id}` | 详情；**返回前标记已读** |
| POST | `/api/v1/my-notices/read-all` | 全部已读，返回本次标记条数 |

## 11. 权限码与菜单

| 权限码 | 类型 | 挂靠 | 授予 |
|---|---|---|---|
| `system:notice:list` | 菜单 `/system/notices`，组件 `system/notice/index` | 系统管理目录 | SUPER_ADMIN、DEPT_MANAGER |
| `system:notice:create` | 按钮 | 通知公告菜单 | SUPER_ADMIN、DEPT_MANAGER |
| `system:notice:revoke` | 按钮 | 通知公告菜单 | SUPER_ADMIN |
| `system:notice:delete` | 按钮 | 通知公告菜单 | SUPER_ADMIN |
| `system:notice:read-stats` | 按钮 | 通知公告菜单 | SUPER_ADMIN、DEPT_MANAGER |

- `DEPT_MANAGER` 不授予撤回/删除：跨部门误发只能由超管处理。
- 用户端收件箱无权限码，所有登录用户可用。

## 12. 审计

- 发布、撤回、删除使用 `@OperLogRecord(module = "通知公告", action = ...)`，操作日志记录请求参数快照（内容字段可能较长，按既有 `OperLogAspect` 截断策略）。
- 服务接口被其他模块调用时不产生 HTTP 操作日志，属预期；调用方模块自行记业务日志。

## 13. 风险与已知限制

| # | 风险 | 处理 |
|---|---|---|
| 1 | 发布快照后新建用户看不到历史通知 | 明确口径；需要“历史可补发”时再开能力（补发=重新展开范围并补齐快照） |
| 2 | 全员通知在大用户量下写入 N 行 | 当前用户量小；超过 1 万用户时改为异步批量写入（接口不变） |
| 3 | 系统发布的通知 `dept_id` 为空，受限角色看不到 | 有意口径；发布页面不提供“系统发布”，仅服务接口可产生 |
| 4 | 通知内容为纯文本 | 前端按文本渲染，不解析 HTML；需要富文本时另行设计并做 XSS 过滤 |
| 5 | 撤回后用户端直接消失可能让已读用户困惑 | 管理端保留状态与统计；如需占位展示，后续在用户端增加“已撤回”标识 |
