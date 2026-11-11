# 会话管理设计（to-be）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（2026-11-09） |
| 状态 | 设计已确认（需求方在本轮指令中给定验收口径），实现见 `docs/audit/impl-notes.md` |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **to-be**：会话记录、在线口径、单设备吊销与每请求校验成本的方案说明 |
| 依赖 | `docs/design/auth-and-datascope.md` §2（原令牌失效选型）；`docs/specs/api-contract.md` §2–§3、§15；`docs/specs/backend-spec.md` §9 |

> 本文回答三件事：为什么原“用户级版本号”选型需要修订、会话有效性最终怎么校验、每请求多付多少成本。
> 接口字段与错误码的规范定义仍以 `docs/specs/api-contract.md` 为准，本文只写本模块的实例口径。

## 1. 目标与范围

| 能力 | 管理端 | 用户端 |
|---|---|---|
| 会话列表（分页、按用户筛选、在线/已失效） | 是 | 我的登录设备列表 |
| 强制下线单条会话 | 是 | 踢掉自己的其他设备 |
| 按用户一键踢全部 | 是 | 否 |
| 清理已失效记录 | 是 | 否 |
| 退出登录 | 否 | 是（现有 `POST /api/v1/auth/logout`） |

不做：设备指纹、地理位置解析、按 IP 限制登录、并发登录数上限、会话续期策略调整。

## 2. 设计反转：原选型为什么需要修订

### 2.1 原选型原文（`auth-and-datascope.md` §2.2）

> 决策：采用 A（用户级令牌版本号）。本系统的失效诉求是“按用户整体失效”，不需要按设备精细吊销；版本号持久在库、可承受 Redis 清空、refresh 与 access 同一口径，复杂度最低。B（逐令牌黑名单）的唯一优势（单设备吊销）不在本轮需求内。

同一节的选择性对照表也写明：`选择性失效单设备 | 不支持`。

### 2.2 本轮为什么需要单设备吊销

会话管理要交付的验收项是“同一账号在两处登录，踢掉其中一条，另一条仍可用”。这条验收与“版本号 + 1”在语义上直接冲突：

| 做法 | 踢掉一台设备后的实际效果 | 是否满足验收 |
|---|---|---|
| 复用 `token_version + 1` | 该用户**全部设备**一起失效，包括操作者自己的会话 | 不满足（硬要求：只能影响那一台） |
| 按会话逐条失效（本轮采用） | 只有目标会话失效，其他设备不受影响 | 满足 |

因此本轮不是“推翻版本号机制”，而是**在版本号之外补上一条设备级失效通道**。两条通道各管一类事件，见 §5。

### 2.3 选型结论

| 项 | 结论 |
|---|---|
| 是否保留 `token_version` | **保留**。它仍然是“账号级事件”（改密、管理员重置、停用、删除）的唯一权威开关，且不依赖 Redis，缓存清空不会让旧令牌复活 |
| 是否改成“逐令牌黑名单” | **不整体改**。只对“服务端可枚举的会话”落库记录，不把 access jti 逐条塞进 Redis；会话状态存在 MySQL，可查询、可分页、可审计 |
| 单设备吊销的落点 | `sys_user_session` 每条会话一行，令牌携带 `sid`；过滤器每请求按 `sid` 校验会话状态 |
| 与原选型的关系 | 原选型回答“账号级失效怎么做”，本轮回答“设备级失效怎么做”；两者叠加，不是二选一 |

### 2.4 代价（必须承认）

1. 每请求多一次会话查询（见 §4）。
2. 令牌新增 `sid` claim；**上线瞬间旧令牌没有 `sid`，会被判定失效，所有在线用户需重新登录一次**（见 §7.3）。
3. 多了一张随设备数增长的表，需要清理策略（见 §8）。
4. 会话状态与 Redis 黑名单并存，出现了“两套失效数据”，必须明确优先级与写入口径（见 §5）。

## 3. 在线口径（定死）

| 项 | 取值 | 说明 |
|---|---|---|
| 在线判据 | `invalidated_at IS NULL` **且** `last_active_at >= now - 5 分钟` | 两个条件同时满足才算在线 |
| N（在线窗口） | **5 分钟** | 小于 access token 有效期 15 分钟；能覆盖“用户在页面上但两次请求间隔较长”的常见情况，又不把已离开的用户长期算在线 |
| 活跃时间更新 | 每会话最多 **60 秒写一次库** | 见 §4 |
| 时间基准 | 数据库 `UTC_TIMESTAMP()` / Java `Instant`，接口按 ISO-8601 输出 | 与全库时间口径一致 |

“在线”是**展示口径**，不是鉴权口径：鉴权只看会话是否失效与令牌版本；`last_active_at` 不参与放行判断。

## 4. 每请求校验成本与活跃时间节流

### 4.1 现状（改造前）

`JwtAuthenticationFilter` 每请求重建身份，已有成本：

| 动作 | 次数 | 位置 |
|---|---|---|
| 查询用户（启用状态、部门、版本号） | 1 次 SELECT | `authUserQueryService.findById` |
| 查询角色码 | 1 次 SELECT | `roleCodes` |
| 查询权限码 | 1 次 SELECT | `permissionCodes` |
| 校验 access 黑名单 | 1 次 Redis | `AuthTokenStore.isAccessDenied` |

合计：**3 次 SELECT + 1 次 Redis**。

### 4.2 改造后

| 动作 | 次数 | 说明 |
|---|---|---|
| 上述 3 次身份查询 | 不变 | 不改现有授权口径 |
| 会话有效性查询 | **+1 次 SELECT**，按 `uk_session_sid` 唯一键命中 | 返回 `user_id`、`invalidated_at`、`invalidate_reason`、`last_active_at` |
| 活跃时间节流判断 | **+1 次 Redis**（`SETNX admin:session:touch:{sid}`，TTL 60 秒） | 命中（首次）才写库；未命中直接跳过 |
| 活跃时间写库 | **≤1 次 UPDATE / 60 秒 / 会话** | 只有节流键首次写入时执行 |
| access 黑名单 | 不变 | 1 次 Redis |

**合计：4 次 SELECT + 2 次 Redis + 每 60 秒最多 1 次 UPDATE。** 即固定多 1 次唯一键查询和 1 次 Redis `SETNX`，写库频率被压到“每会话每分钟最多一次”。

### 4.3 节流窗口与代价

| 项 | 取值 | 代价 / 取舍 |
|---|---|---|
| 节流窗口 | 60 秒 | `last_active_at` 最多滞后 60 秒；在线窗口 5 分钟，滞后不影响在线判定 |
| 节流载体 | Redis `SETNX` + 60 秒 TTL | 多实例共享、自动过期、无内存泄漏；代价是每请求多 1 次 Redis 调用 |
| Redis 不可用 | 节流键写入失败时**跳过本次写库**，不阻断请求 | `CacheService` 失败不抛到过滤器；最坏情况是 `last_active_at` 不更新，在线展示偏旧，鉴权不受影响 |
| 为什么不用进程内 Map | 单机可行，但多实例各自计数会放大写库次数，且重启后状态丢失 | 本模块从第一天就按多实例友好实现，避免将来返工 |
| 为什么不用“每请求直接 UPDATE” | 活跃用户每 30 秒刷新一次页面就会产生 2 次/分钟的写 | 明显放大写放大与锁竞争，且没有业务收益 |

## 5. 两套失效机制的关系（不打架）

### 5.1 职责划分

| 机制 | 管什么事件 | 数据位置 | 生效范围 |
|---|---|---|---|
| `sys_user.token_version`（既有） | 改密、管理员重置密码、停用、删除账号 | `sys_user` 一列 | 该用户**全部**令牌立即失效，不依赖 Redis |
| `sys_user_session.invalidated_at / reason`（本轮） | 主动退出、管理员踢单条、按用户踢全部 | 会话表每行 | 只有目标会话失效，其他设备不受影响 |

### 5.2 校验顺序（过滤器固定顺序）

1. 解析令牌；类型必须是 `access`。
2. access jti 黑名单（既有）。
3. 按 `user_id` 查用户：不存在 → `20104`；停用 → `20103`。
4. 令牌 `ver` 与 `sys_user.token_version` 比较：不等 → 按 `token_revoke_reason` 返回 `20106/20107/20105`。
5. **按 `sid` 查会话**：会话不存在或 `invalidated_at` 非空 → 按会话失效原因返回分码（见 §6）。
6. 重建 `CurrentUser`（角色、权限码、部门）并放行。

顺序决定了“账号级事件”优先于“设备级事件”：账号被停用时返回 `20103`，而不是“被管理员下线”。

### 5.3 账号级事件如何同步会话状态

| 事件 | `token_version` | 会话表动作 | 目的 |
|---|---|---|---|
| 本人改密 | +1，`PASSWORD_CHANGED` | 除当前会话外，其余会话置 `PASSWORD_CHANGED` | 版本号负责“旧令牌立即失效”；会话标记负责“设备列表显示真实状态” |
| 管理员重置密码 | +1，`PASSWORD_RESET` | 该用户全部活跃会话置 `PASSWORD_CHANGED` | 同上；错误分码仍由 `token_version` 决定，保持 `20106` |
| 停用账号 | +1，`ACCOUNT_DISABLED` | 该用户全部活跃会话置 `ACCOUNT_DISABLED` | 重新启用后设备列表不出现“仍在线”的幽灵会话 |
| 删除账号 | 不升版本 | 全部活跃会话置 `USER_DELETED` | 用户已查不到，会话不应再显示在线 |
| 主动退出 | 不变 | 当前会话置 `LOGOUT` | 只影响当前设备 |
| 管理员踢单条 / 踢全部 | 不变 | 目标会话置 `KICKED_BY_ADMIN` | **不触碰版本号**，其他设备继续可用 |

**结论**：两条通道同时存在，但不是“两套并行且互不知情”的失效逻辑。账号级事件是权威开关（决定令牌能否用），会话状态是设备级事实（决定列表显示与单设备吊销）。同一次账号级事件会同时写两处，写入口径集中在 `UserSessionAppService` 与对应的用户服务中，不存在第三个判定点。

## 6. 失效原因与 401 分码

### 6.1 失效原因枚举（`invalidate_reason`）

| 值 | 含义 | 产生方 |
|---|---|---|
| `LOGOUT` | 主动退出 | 用户端 `POST /api/v1/auth/logout` |
| `KICKED_BY_ADMIN` | 管理员踢下线（含按用户踢全部） | 管理端接口 |
| `PASSWORD_CHANGED` | 密码变更（含管理员重置密码） | 本人改密 / 管理员重置 |
| `ACCOUNT_DISABLED` | 账号停用 | 管理员启停用户 |
| `USER_DELETED` | 账号删除 | 管理员删除用户（补充项：原需求只列四类，删除账号同样需要让设备列表不再显示在线） |

### 6.2 错误码映射

| 会话失效原因 | 返回错误码 | HTTP | 文案 |
|---|---|---|---|
| `KICKED_BY_ADMIN` | **20108（新增）** | 401 | 已被管理员下线，请重新登录 |
| `LOGOUT` | 20105（既有） | 401 | 登录状态已失效，请重新登录 |
| `PASSWORD_CHANGED` | 20107（既有） | 401 | 密码已修改，请重新登录 |
| `ACCOUNT_DISABLED` | 20103（既有） | 401 | 账号已被停用 |
| `USER_DELETED` | 20104（既有） | 401 | 账号已被删除 |
| 会话不存在（旧令牌无 `sid`） | 20105（既有） | 401 | 登录状态已失效，请重新登录 |

“被管理员下线”与“登录过期”由此可分：过期是 `20102`（JWT 解析失败），被踢是 `20108`。

## 7. 数据模型

### 7.1 表 `sys_user_session`

| 字段 | 类型 | 可空 | 默认 | 含义 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增 | 主键 |
| `session_id` | VARCHAR(64) | 否 | — | 会话标识（32 位 UUID，写入 access/refresh 令牌的 `sid`） |
| `user_id` | BIGINT | 否 | — | 用户 id |
| `username` | VARCHAR(32) | 否 | — | 登录账号快照（用户改名后列表仍可读） |
| `login_at` | DATETIME | 否 | — | 登录时间（UTC） |
| `last_active_at` | DATETIME | 否 | — | 最近活跃时间（UTC），按 §4 节流更新 |
| `ip` | VARCHAR(64) | 是 | — | 登录 IP |
| `user_agent` | VARCHAR(255) | 是 | — | 原始 User-Agent |
| `device_type` | VARCHAR(32) | 否 | `UNKNOWN` | PC / MOBILE / TABLET / UNKNOWN |
| `browser` | VARCHAR(64) | 是 | — | 浏览器识别（如 Chrome 120） |
| `os` | VARCHAR(64) | 是 | — | 操作系统识别（如 Windows 10） |
| `invalidated_at` | DATETIME | 是 | — | 失效时间（UTC）；`NULL` = 未失效 |
| `invalidate_reason` | VARCHAR(32) | 是 | — | 失效原因，见 §6.1 |
| `created_at` | DATETIME | 否 | — | 创建时间（UTC） |
| `updated_at` | DATETIME | 否 | — | 更新时间（UTC） |

索引：

| 索引 | 列 | 用途 |
|---|---|---|
| `uk_session_sid` | `session_id` | 过滤器每请求唯一键命中 |
| `idx_session_user` | `user_id`、`login_at` | 用户设备列表、按用户踢全部 |
| `idx_session_active` | `invalidated_at`、`last_active_at` | 管理端在线筛选与清理 |
| `idx_session_last_active` | `last_active_at` | 在线窗口筛选 |

### 7.2 与既有表的关系

- `user_id` → `sys_user.id` 逻辑关联，无物理外键（全库一致）。
- 不新增 `refresh_jti` 列：refresh 令牌同样携带 `sid`，刷新时校验会话状态；踢下线不需要枚举 Redis 键。
- 会话表**不参与软删除**：它是运行态数据，清理为物理删除（见 §8）。这是对“全部删除为逻辑删除”的显式例外，已登记到 `docs/baseline/assertions.md`。
- 数据权限：`sys_user_session` 登记为**豁免**（与 `sys_login_log` 同类）。管理端按功能权限 `system:session:list` 拦截，列表按 `user_id` 普通筛选，不叠加组织范围。

### 7.3 令牌变更与旧令牌处理

- access / refresh 令牌新增 `sid` claim。
- 过滤器与 refresh 接口对“缺少 `sid` 的旧令牌”返回 `20105 登录状态已失效，请重新登录`。
- 影响：**部署后所有在线用户需重新登录一次**。本地联调环境可接受；生产发版应安排在低峰并提前公告。

## 8. 清理策略

| 项 | 取值 | 说明 |
|---|---|---|
| 清理条件 | `invalidated_at IS NOT NULL` 且 `invalidated_at <= now - 30 天` | 保留 30 天供审计与排查 |
| 执行方式 | 管理端手动触发 `DELETE /api/v1/sessions/invalidated` | 不引入定时任务，避免与调度模块耦合 |
| 返回 | 实际删除条数 | 幂等：无匹配行返回 0 |
| 为什么不做自动清理 | 单机演示项目没有运维清理窗口；手动入口可见、可审计 | 数据量增大后再评估挂到定时任务 |

## 9. 接口清单

### 9.1 管理端（权限码 `system:session:*`）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/v1/sessions` | `system:session:list` | 分页；筛选 `userId`、`keyword`（账号/昵称）、`online`（true/false） |
| DELETE | `/api/v1/sessions/{id}` | `system:session:kick` | 踢单条；不能踢当前请求所在会话（409） |
| DELETE | `/api/v1/sessions/user/{userId}` | `system:session:kick` | 踢该用户全部活跃会话；返回影响条数 |
| DELETE | `/api/v1/sessions/invalidated` | `system:session:clean` | 清理失效超过 30 天的记录；返回删除条数 |

### 9.2 用户端（仅需登录态）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/auth/sessions` | 我的登录设备列表；`current=true` 标记当前会话 |
| DELETE | `/api/v1/auth/sessions/{id}` | 踢掉自己的其他设备；目标必须是本人会话，当前会话走退出登录（409） |
| POST | `/api/v1/auth/logout` | 既有；本轮补充“把当前会话标记为 `LOGOUT`” |

### 9.3 响应字段（会话）

`id`、`userId`、`username`、`nickname`、`ip`、`userAgent`、`deviceType`、`browser`、`os`、`loginAt`、`lastActiveAt`、`invalidatedAt`、`invalidateReason`、`online`、`current`（仅用户端）。

## 10. 权限码与菜单

| 权限码 | 名称 | 类型 | 授权 |
|---|---|---|---|
| `system:session:list` | 会话管理 | 菜单（`/system/sessions`，组件 `system/session/index`） | 仅 `SUPER_ADMIN` |
| `system:session:kick` | 强制下线 | 按钮/接口 | 仅 `SUPER_ADMIN` |
| `system:session:clean` | 清理失效记录 | 按钮/接口 | 仅 `SUPER_ADMIN` |

理由：会话列表包含全部用户的 IP 与设备信息，且强制下线是安全敏感操作；部门管理员不授予，避免跨部门读取他人登录信息。

## 11. 单机与多实例

| 项 | 现状 | 多实例前置条件 |
|---|---|---|
| 会话校验 | 查 MySQL，天然多实例一致 | 无额外改造 |
| 活跃时间节流 | Redis `SETNX`，多实例共享 | 无额外改造；Redis 不可用时退化为不更新 |
| 会话失效 | 写 MySQL，下一次请求立即生效 | 无额外改造 |
| access 令牌黑名单 | Redis | 无额外改造 |

与定时任务不同，本模块**不依赖进程内状态**，多实例可直接工作。

## 12. 验收口径（本轮）

| 编号 | 验收项 | 判定 |
|---|---|---|
| `AC-SESSION-01` | 同一账号两处登录（不同 UA），管理端看到两条，设备/浏览器可区分 | 列表两条、`userAgent` 不同 |
| `AC-SESSION-02` | 踢掉其中一条，另一条仍可用；被踢那条立即 401 | 被踢请求返回 `20108`，另一条 200 |
| `AC-SESSION-03` | 按用户一键踢全部 | 该用户全部会话 `invalidated_at` 非空，下一次请求 401 |
| `AC-SESSION-04` | 用户自己退出登录 | 该会话 `invalidate_reason=LOGOUT`，不是 `KICKED_BY_ADMIN` |
| `AC-SESSION-05` | 改密码 / 停用账号后会话状态正确标记 | 其他会话 `PASSWORD_CHANGED` / `ACCOUNT_DISABLED`，当前设备按既有逻辑处理 |
| `AC-SESSION-06` | 在线口径 | 最近 5 分钟有请求且未失效 = 在线；否则已失效/离线 |
| `AC-SESSION-07` | 清理已失效记录 | 只删除失效超过 30 天的行，返回条数 |

## 13. 自检清单

- [x] 正面说明原选型与本轮需求的反转，未假装不冲突（MUST, STATIC）
- [x] 在线窗口 N、节流窗口与每请求成本给出具体数值（MUST, STATIC）
- [x] 单设备吊销不触碰 `token_version`，硬要求“只影响一台”可判定（MUST, STATIC）
- [x] 两套失效机制给出职责划分、校验顺序与写入口径（MUST, CONTEXT）
- [x] 失效原因、错误码映射、旧令牌处理写明（MUST, STATIC）
- [x] 未新增配置项；在线/节流为代码常量，配置台账无需变更（MUST, STATIC）
