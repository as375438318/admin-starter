# 会话管理实施计划（2026-11-09）

## 1. 档位判定（`AGENTS.md` §4.1）

**结论：B 档（模块级）**。只涉及“认证 · 会话管理”一个模块及其前端页面；只做纯新增且向后兼容：新表、新接口、新权限码、新错误码。

`PRC-APR-001` 七类逐条核对：

| 类别 | 是否命中 | 依据 |
|---|---|---|
| 1. 契约破坏兼容或版本演进 | 未命中 | 只新增接口与字段，既有接口的路径、请求字段、响应字段均不改；`openapi.yaml` 版本号不升级 |
| 2. 数据模型不可逆变更 | 未命中 | 只新增 `sys_user_session` 表；不删列、不改类型、不缩长度、不回填历史数据 |
| 3. 权限模型或数据范围放宽 | 未命中 | 新增 3 枚权限码且只授予 `SUPER_ADMIN`；既有权限与数据范围不变 |
| 4. 安全降级 | 未命中 | 增加单设备吊销与会话审计，属于安全增强 |
| 5. 状态机新增状态或豁免 | 未命中 | 无 `docs/design/state-machine.md`（§4.4 豁免）；失效原因是字段枚举，不是状态流转 |
| 6. 验收标准变更或新增业务术语定义 | 未命中 | “在线 = 最近 5 分钟内有请求”由需求方在本轮指令中直接给定（2026-11-09），属于既有验收口径落地，不是 AI 新增 |
| 7. 可选模块启用或停用 | 未命中 | 不启停任何模块 |

## 2. 实施步骤

| 步 | 内容 | 产出 | 完成标准 |
|---|---|---|---|
| 1 | 设计文档 | `docs/design/session-management.md` | 反转说明、在线口径、节流成本、失效原因与错误码齐全 |
| 2 | 能力与契约 | `docs/requirements/system-modules.md`、`docs/api/openapi.yaml` | 接口与权限码可逐条核对；`20108` 已登记 |
| 3 | 迁移 V12 | `server/web/src/main/resources/db/migration/V12__session_management.sql` | 只新增；建表 + 3 菜单 + 3 权限码 + `SUPER_ADMIN` 授权；按业务键匹配不硬编码 id |
| 4 | 后端领域与持久化 | `UserSession`、`UserSessionDO`、`UserSessionMapper`、`UserSessionRepository(Impl)`、`UserSessionConverter` | 编译通过；`sys_user_session` 登记为数据权限豁免 |
| 5 | 认证接入 | `JwtTokenProvider` 增 `sid`；`AuthAppService` 登录建会话、刷新校验会话、登出标记 | 登录签发含 `sid`；刷新被踢会话返回分码 |
| 6 | 过滤器校验 | `JwtAuthenticationFilter` 增加会话状态校验与活跃时间节流 | 每请求 4 SELECT + 2 Redis；踢下线立即 401 |
| 7 | 账号级事件联动 | `UserAppService`（重置密码/停用/删除）、`ProfileAppService`（本人改密） | 会话状态与 `token_version` 同批写入 |
| 8 | 管理端/用户端接口 | `SessionController`、`AuthSessionController`（或并入 `AuthController`） | 权限码生效；当前会话保护（409） |
| 9 | 前端 | 管理端 `views/system/session/index.vue`；个人中心“登录设备”；`api/session.ts` | `pnpm --filter @admin-starter/pc build` 通过 |
| 10 | 实测 | 双 UA 登录、踢单条、踢全部、主动退出、改密、停用、清理 | 见 §3 验收项，贴真实响应 |
| 11 | 回填与门禁 | 基线、数据模型/权限矩阵重跑、`openapi.generated.yaml` 重导、`spec-lint` | `node tools/spec-lint/cli.mjs` error 0 |

## 3. 验收口径

| 编号 | 验收项 |
|---|---|
| `AC-SESSION-01` | 同一账号两处登录（不同 UA），管理端列表两条且 UA/设备可区分 |
| `AC-SESSION-02` | 踢单条：目标会话立即 401（`20108`），另一条 200 |
| `AC-SESSION-03` | 按用户一键踢全部：全部活跃会话失效 |
| `AC-SESSION-04` | 用户主动退出：该会话 `LOGOUT`，不是 `KICKED_BY_ADMIN` |
| `AC-SESSION-05` | 改密码 / 停用账号：会话状态正确标记且与 `token_version` 不冲突 |
| `AC-SESSION-06` | 在线口径：最近 5 分钟有请求且未失效才算在线 |
| `AC-SESSION-07` | 清理：只删除失效超过 30 天的行，返回条数 |
| `AC-SESSION-08` | 前端构建通过；`spec-lint` error 0 |

## 4. 风险与处置

| 风险 | 触发条件 | 处置 |
|---|---|---|
| 旧令牌无 `sid` 被判定失效 | 部署后首次请求 | 已知影响：在线用户需重新登录一次；记入 `impl-notes`，发版公告 |
| 节流 Redis 不可用 | Redis 故障 | 跳过活跃时间更新，不阻断请求；在线展示偏旧 |
| 踢全部把操作者自己踢下线 | 管理员对自己执行“踢全部” | 接口语义为“该用户全部会话”，返回前已完成；界面二次确认；文档写明 |
| 物理删除会话记录违背“全部删除为逻辑删除” | 清理接口执行 | 显式例外：运行态数据，保留 30 天后物理删除；登记基线断言 |

## 5. 执行记录（2026-11-09）

| 项 | 结果 |
|---|---|
| 状态 | 全部 11 步完成；实测 14 项通过 |
| 交付证据 | `docs/audit/impl-notes.md` §会话管理（SESSION-001～013、实测表、门禁记录） |
| 与计划差异 | `docs/api/errors.yaml` 未新建（既有 `API-001` 口径）；`USER_DELETED` 为补充失效原因；自助踢其他设备复用 `KICKED_BY_ADMIN`；权限码仅授 `SUPER_ADMIN` |
| 门禁 | `mvn package` 通过、Flyway v12、前端构建通过、`spec-lint` error 0 |
