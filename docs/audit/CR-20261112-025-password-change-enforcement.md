# CR-20261112-025 口令强制改密的服务端拦截与前端引导（等保 P0-1 的另一半）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「1、做」（承接 `CR-20261112-024` 的遗留项） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **B（模块级）**：新增错误码 30202 之外的 20302、新增响应字段（上一 CR 已加）；本 CR 主要为**行为收紧**，接口路径与请求字段不变 |
| 关联 | `CR-20261112-024`；断言 #90；错误码 `PASSWORD_CHANGE_REQUIRED(20302)` |

## 1. 问题

上一 CR 只做到"登录响应告诉前端必须改密"，**服务端没有拦**——知道 URL 就能继续调所有接口。等保要求的是"强制"，不是"提示"。

## 2. 实现

### 2.1 把标记写进令牌

`JwtTokenProvider.createAccessToken(...)` 新增 `mustChangePassword` 参数，为 true 时写入 `pwd` 声明（为 false 时不写，令牌保持小）。登录、静默刷新、改密后重签三条签发路径全部带上该标记。

> 为什么用令牌声明而不是 Redis 标志：省一次缓存读、无需登记新的缓存命名空间（`CacheNamespaces` 是白名单，新增前缀要连带改缓存管理页），且刷新令牌时会自动延续。

### 2.2 在鉴权过滤器里拦截

`JwtAuthenticationFilter` 认证成功后判定：若 `pwd` 为 true 且请求路径不在白名单 → 清空 SecurityContext 并置 `AUTH_ERROR_ATTRIBUTE = PASSWORD_CHANGE_REQUIRED`，由既有的 `authenticationEntryPoint` 输出 **403 + 20302**（不是 401——前端据此弹改密引导，而不是跳登录页）。

### 2.3 白名单（"能自救"的最小集合）

| 放行 | 理由 |
|---|---|
| `PUT /api/v1/profile/password` | 改密本身 |
| `GET /api/v1/profile` | 改密页要显示"我是谁" |
| `GET /api/v1/auth/me`、`/auth/routes`、`/auth/permissions` | **前端 bootstrap 必需**：不放行会导致改密页加载失败 → 被踢回登录页 → 用户永远改不了密码（实测踩到） |
| `POST /api/v1/auth/refresh` | 不给刷新，access 过期后用户彻底无法自救 |
| `POST /api/v1/auth/logout` | 必须能退出 |
| `/api/v1/public/**`、`/api/v1/health`、`/actuator/**` | 公开与探活 |

### 2.4 前端引导

| 位置 | 改动 |
|---|---|
| `apps/shared/src/types.ts` | `LoginResult` 增 `mustChangePassword`、`passwordExpired` |
| `stores/auth.ts` | 保存两个标记；`passwordExpired` 单独存（提示语不同） |
| `router/index.ts` | 守卫：必须改密时除 `/profile` 外一律重定向到 `/profile?mustChange=1` |
| `api/http.ts` | 收到 403 + 20302 → 置位标记并跳改密页（**不弹全局错误**，避免首屏一串红条） |
| `views/profile/index.vue` | 顶部阻断式提示条，说明"为什么点哪都不行"及当前是过期还是重置 |

## 3. 测试

| 层 | 覆盖 |
|---|---|
| 后端 `PasswordChangeEnforcementTest`（5 条，临时账号） | 登录标记正确；业务接口 **403+20302**；自救路径放行；改密时弱口令仍被策略拒（422+30202）；改密后新令牌恢复 200 |
| 后端 `AbstractIntegrationTest` | `@BeforeEach` 把内置 admin 恢复成"不需要改密"（`V17` 迁移把存量账号全置位），否则所有契约测试都会撞 20302 |
| 前端单测（97） | store 置位逻辑两条新增用例 |
| E2E（7） | 新增 `e2e/helpers.ts` 自适应登录：首次跑会真的走一遍"被强制改密 → 改密 → 恢复正常" |

## 4. 过程中的三个自身错误

1. **白名单漏了 bootstrap 接口**：`/auth/me|routes|permissions` 被拦 → 改密页加载失败 → 踢回登录页。E2E 报"候选口令全部不可用"就是这个。
2. **强口令含账号名**：助手第一版把管理员新口令写成 `Admin#Starter2026`，被策略拒（422+30202「密码不能包含账号名」）——策略本身没错，是我写测试数据时没过脑子。
3. **断言捕获到 `20301 来源校验失败`**：写操作必须带合法 `Origin`（`OriginCheckFilter` 的 CSRF 防护）。测试里补了 `Origin` 头，同时给 `refresh` 传了刷新令牌 Cookie（之前只传了 access）。

## 5. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **189 用例通过**（common 26 / system 149 / web 14） |
| 前端单测 | `pnpm --filter @admin-starter/pc test:unit` | **97 通过** |
| E2E | 起后端 + `pnpm exec playwright test` | **7/7 通过**（含强制改密路径） |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 6. 遗留

- `dev1` 等内置账号在 dev/test 库被 E2E 改成了强口令（`Staff#2026Pw9`），后续跑 E2E 由助手自适应；**手工登录请用该口令或重置**。
- 口令历史（不得复用最近 N 次）与到期前主动提醒未做。