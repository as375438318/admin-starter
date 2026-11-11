# 令牌失效与数据权限重算（设计 v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（2026-11-09） |
| 状态 | APPROVED（按用户 2026-11-09 批准的失效口径实施） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | to-be 设计：为什么这么做、触发矩阵、拒绝口径；实现与实测见 `docs/audit/impl-notes.md` |
| 依赖 | `docs/baseline/assertions.md` #4、#8、#14；`docs/design/data-model.md`；`docs/specs/api-contract.md` CT-ERR-011 |

## 1. 范围

- ① 用户被停用、删除、管理员重置密码、本人修改密码后，旧令牌立即失效；撤销角色/权限后不得继续用旧权限访问。
- ② 部门新增、改名、移动父级、删除后，`DEPT` / `DEPT_AND_CHILD` / `CUSTOM_DEPT` 数据范围立即按新树生效；用户调部门后旧令牌里的 `deptId` 不得继续生效；禁止把部门移动到自己的子孙节点。

## 2. ① 令牌立即失效

### 2.1 现状（2026-11-09 核对）

| 事实 | 位置 | 结论 |
|---|---|---|
| access token 为无状态 JWT，claims 内嵌 `roles` / `perms` / `deptId` | `JwtTokenProvider.createAccessToken` | 每个请求不查库，撤销角色/权限最长 `access-ttl`（15 分钟）后才生效 |
| 仅登出写入 jti 黑名单 | `AuthTokenStore.denyAccess`、`JwtAuthenticationFilter` | 停用/删除/改密都不会让已签发令牌失效 |
| refresh token 白名单只校验 jti 是否在 Redis | `AuthTokenStore.saveRefresh/isRefreshActive` | 只让 access 失效而 refresh 仍可换新令牌，功能等于没做 |

### 2.2 方案比较

| 维度 | A. 用户级令牌版本号（采用） | B. 逐令牌黑名单 |
|---|---|---|
| 状态量 | 每用户 1 个整数（持久化列） | 每会话 1 条 jti，随登录设备数增长 |
| 失效一整用户 | `token_version + 1` 一次写 | 必须枚举该用户全部 jti 并逐条删除，需维护 `user -> jti` 索引集合 |
| 刷新令牌 | refresh 同样携带并校验 `ver`，一并失效 | 同样需要枚举 refresh jti |
| Redis 丢失/清空 | 版本在库中，不因缓存丢失而复权 | 黑名单丢失即旧令牌复活 |
| 选择性失效单设备 | 不支持；本人改密用“先升版本、再给当前设备重签”实现同样效果 | 支持 |
| 实现复杂度 | 低：1 列 + 1 次比较 | 高：集合索引、并发清理、过期回收 |

**决策：采用 A。** 本系统的失效诉求是“按用户整体失效”，不需要按设备精细吊销；版本号持久在库、可承受 Redis 清空、refresh 与 access 同一口径，复杂度最低。B 的唯一优势（单设备吊销）不在本轮需求内。

**修订（2026-11-09，会话管理）**：上述“单设备吊销不在需求内”的前提已被推翻。会话管理要求“踢掉一台设备只影响那一台”，版本号 +1 会连坐全部设备，因此新增设备级通道 `sys_user_session`（令牌携带 `sid`）。A 仍保留并继续负责账号级事件（改密/重置/停用/删除），B 只按服务端可枚举的会话落 MySQL，不改成逐 jti 黑名单。两套机制的职责、校验顺序与每请求成本见 `docs/design/session-management.md` §2、§4、§5。本节其余内容仍有效，不删除原决策与理由，保留反转记录。

### 2.3 实现口径

- `sys_user` 新增 `token_version BIGINT NOT NULL DEFAULT 0` 与 `token_revoke_reason VARCHAR(32) NULL`。
- access / refresh 令牌都写入 `ver`（签发时的 `token_version`）。
- 每个请求由 `JwtAuthenticationFilter` 校验：令牌 `ver` == 库中 `token_version`；不相等按 `token_revoke_reason` 返回分码。
- 角色/权限**不再信任令牌 claims**：过滤器每请求从库读取 `is_enabled`、`dept_id`、角色码、权限码，重建 `CurrentUser`。这样撤销角色/权限、调整部门立即生效，无需额外版本号。
- 刷新令牌时同样校验 `ver` 与 `is_enabled`；不通过则删除该 refresh jti 并返回分码。

### 2.4 触发矩阵

| 场景 | 动作 | 当前设备 | 其他设备 | 返回分码 |
|---|---|---|---|---|
| 本人改密码 `PUT /api/v1/profile/password` | `token_version+1`，`reason=PASSWORD_CHANGED`，随后用新版本为当前请求重签并下发 Cookie | 保持登录 | 全部失效 | 其他设备 `20107 密码已修改，请重新登录` |
| 管理员重置密码 `PUT /api/v1/users/{id}/password` | `token_version+1`，`reason=PASSWORD_RESET` | 失效 | 失效 | `20106 密码已被重置，请重新登录` |
| 停用账号 `PUT /api/v1/users/{id}/status`（`isEnabled=false`） | `token_version+1`（保证重新启用后旧令牌也不复活） | 失效 | 失效 | `20103 账号已被停用` |
| 删除账号 `DELETE /api/v1/users/{id}` | 逻辑删除；查询不到用户 | 失效 | 失效 | `20104 账号已被删除` |
| 撤销角色/权限、调整部门 | 不升版本；过滤器每请求重取 | 立即按新权限/新部门 | 立即 | 无（旧令牌继续可用，但权限已收窄） |
| 登出 | 现有 jti 黑名单 | 失效 | 不影响其他会话 | `20105 登录状态已失效，请重新登录` |
| 令牌自然过期 | 解析失败 | 失效 | — | `20102 登录状态已过期` |

### 2.5 401 分码（新增，落在认证段 201xx）

| code | 含义 | HTTP |
|---|---|---|
| 20103 | 账号已被停用 | 401 |
| 20104 | 账号已被删除 | 401 |
| 20105 | 登录状态已失效，请重新登录 | 401 |
| 20106 | 密码已被重置，请重新登录 | 401 |
| 20107 | 密码已修改，请重新登录 | 401 |

前端遇到 20103 / 20104 / 20106 / 20107 不再尝试刷新令牌，直接回登录页并展示对应文案；20101 / 20102 / 20105 仍先尝试刷新。

## 3. ② 部门树变更重算数据权限

### 3.1 现状（2026-11-09 核对）

| 事实 | 位置 | 结论 |
|---|---|---|
| 数据范围每次请求现算，结果缓存 5 分钟 | `DataScopeProvider.current/resolve`（`admin:datascope:v1:{userId}`） | 不是静态存储，但缓存会脏 |
| `DEPT_AND_CHILD` 在解析时展开成 `deptIds` 列表后入缓存 | `DataScopeProvider.resolve` + `DeptRepository.findDescendantIds` | 部门树变化后缓存里的 id 列表过期 |
| 子部门查询用 `FIND_IN_SET(deptId, ancestors)` | `DeptMapper.selectDescendantIds` | 非 SQL 递归；`ancestors` 是逗号字符串 |
| 用户调部门会清该用户缓存 | `UserAppService.update` → `evictUser` | 缓存层已处理，但 `CurrentUser.deptId` 来自令牌，仍会过期 |
| 部门增/改/移/删**不清理**数据范围缓存 | `DeptAppService` | 最长 5 分钟脏读 |
| 移动部门已有成环校验 | `DeptAppService.update` | 用 `ancestors.contains(String.valueOf(id))` **子串匹配**，部门 1 与 10 会误判 |

### 3.2 决策

1. **不改为每次全量 SQL 递归**：现有 `ancestors` + `FIND_IN_SET` 已能满足单父组织树，改动收益低、风险高。
2. **缓存加“组织版本号”**：`admin:datascope:deptver` 全局计数。部门增/改/移/删成功后自增；缓存 key 变成 `admin:datascope:v1:{deptver}:{userId}`。旧版本 key 随 5 分钟 TTL 自然淘汰，不需要按模式删 key。
3. **用户调部门**：过滤器每请求从库重取 `dept_id`，解决“旧令牌 deptId 过期”；`UserAppService.update` 的 `evictUser` 保留，避免解析结果滞后。
4. **成环校验**：改为按逗号分段精确比较祖先链，命中即 `409 不能把部门移动到自己的下级（会形成环）`；`cascadeDisable` 同样改为分段比较，消除子串误判。

### 3.3 覆盖矩阵

| 变更 | 数据范围是否重算 | 机制 |
|---|---|---|
| 新增部门 | 是（父部门 `DEPT_AND_CHILD` 应包含新部门） | `deptver + 1` |
| 改名 | 是（保险起见；范围本身不变） | `deptver + 1` |
| 移动父级 | 是（子树 `ancestors` 级联更新） | `deptver + 1`；成环拒绝 |
| 删除部门 | 是（逻辑删除，受控数据行仍按 `dept_id` 存在） | `deptver + 1` |
| 恢复部门 | **现状无恢复接口** | 本轮不新增；见 `system-modules.md` §4.2 |
| 用户调部门 | 是（`DEPT` / `DEPT_AND_CHILD` / `CUSTOM_DEPT` 立即按新部门） | 过滤器重取 `deptId` + `evictUser` |

## 4. 明确不做

- 不做按设备/会话的精细吊销（方案 B 的能力）。
- 不做部门恢复接口（现状不存在；新增属另一轮能力变更）。
- 不改 `docs/specs/` 规范正文；契约错误码只新增 20103–20107，属认证段内追加。

## 5. 自检清单

- [x] 两种令牌失效方案已比较并写明选型理由（MUST, STATIC）
- [x] refresh token 与 access token 同口径失效（MUST, STATIC）
- [x] 401 分码与前端处置已定义（MUST, STATIC）
- [x] 部门树变更与用户调部门的生效路径已逐项覆盖（MUST, STATIC）
- [x] 成环拒绝口径与错误码已写明（MUST, STATIC）
- [x] 现状无恢复接口已如实登记，未虚构能力（MUST, STATIC）
