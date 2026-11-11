# 缓存管理设计（Redis 命名空间白名单）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（2026-11-09） |
| 状态 | `APPROVED`（本轮实施依据；无未解决 TODO） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | **设计文档（to-be）**：缓存管理模块的现状核查、白名单、安全边界与接口设计 |
| 依赖 | `docs/baseline/assertions.md`、`docs/design/data-model.md`、`docs/design/permission-matrix.md`、`docs/api/openapi.yaml` |

> 现状核查是本文档的基础：命名空间清单一律来自代码与运行中的 Redis 实测，不凭印象补充。
> 命名空间前缀的唯一定义处是 `com.adminstarter.common.cache.CacheNamespaces`；本文件只做说明，不改写前缀。

## 1. 背景与目标

系统使用 Redis 作为缓存实现（`app.cache.type=redis`），但此前没有任何可视化的缓存查看与清理入口。运维只能靠 `redis-cli`，存在三类风险：

1. 误用 `KEYS *` 阻塞 Redis；
2. 误删令牌/会话相关键导致全员掉线或安全降级；
3. 清理动作没有审计，出问题无法追溯。

本模块只做四件事：只读概览、按命名空间分页浏览、按白名单命名空间清理、清理留审计。**不提供任意键搜索、不提供值读取、不提供 `FLUSHDB`。**

## 2. 现状核查（2026-11-09 实测）

数据来源：运行中的 Redis（`127.0.0.1:6379`，`SCAN` 全量枚举）+ 代码写入点逐一定位。

| # | 命名空间 code | 键模式 | 消费方（代码位置） | TTL | 实测键数 |
|---|---|---|---|---|---|
| 1 | `public-config` | `admin:publicconfig:v2`（单键） | `PublicConfigAppService.current/evict` | 10 分钟 | 1 |
| 2 | `datascope-deptver` | `admin:datascope:deptver`（单键） | `DataScopeProvider.evictAll/deptVersion` | 365 天 | 1 |
| 3 | `datascope` | `admin:datascope:v1:{deptver}:{userId}` | `DataScopeProvider.resolve/cacheKey` | 5 分钟 | 0 |
| 4 | `session-touch` | `admin:session:touch:{sid}` | `UserSessionAppService.touch` | 60 秒 | 0 |
| 5 | `auth-refresh` | `admin:auth:refresh:{jti}` | `AuthTokenStore.saveRefresh/consumeRefresh` | 7 天（refresh-ttl） | 243 |
| 6 | `auth-deny` | `admin:auth:deny:{jti}` | `AuthTokenStore.denyAccess/isAccessDenied` | access 剩余有效期（≤15 分钟） | 1 |
| 7 | `auth-fail` | `admin:auth:fail:{username}` | `AuthAppService.login`（失败计数/锁定） | 15 分钟（lock-duration） | 0 |

汇总：`DBSIZE=246`，七个命名空间合计 246，未登记键 0。

> 说明：`auth-refresh` 的 243 条是开发期反复登录累积的刷新令牌白名单；它们会按 7 天 TTL 自然过期，但数量会随登录次数增长，是本轮新增清理入口的直接动因。
> 键数是运行态快照（随登录与请求变化）；交付时复查为 `DBSIZE=252`、七命名空间合计 252、未登记 0。

## 3. 命名空间白名单

白名单在 `CacheNamespaces` 中以常量 + 注册表形式登记。请求只能传 **code**（如 `auth-refresh`），服务端按 code 查表取前缀；**禁止**把前端或数据库传入的前缀直接拼进 Redis 命令。

| code | 名称 | 前缀 | 模式 | 用途 | 敏感 | 可清理 | 清除后果（前端原样展示） |
|---|---|---|---|---|---|---|---|
| `public-config` | 公开配置缓存 | `admin:publicconfig:v2` | 单键 | 免鉴权公开配置（名称/标语/版权/ICP/上传上限） | 否 | 是 | 下一次公开配置请求会回源数据库并重新缓存；用户无感知，仅多一次查询。 |
| `datascope-deptver` | 组织版本号 | `admin:datascope:deptver` | 单键 | 组织树变更版本号，驱动数据权限缓存失效 | 否 | 是 | 版本号重置；已缓存的数据权限条件按 5 分钟 TTL 自然失效，下一次请求重算。不改变授权结果。 |
| `datascope` | 数据权限条件 | `admin:datascope:v1:` | 前缀 | 按用户缓存数据范围 SQL 条件 | 否 | 是 | 下一次受控查询按数据库重算数据范围；可见范围不变，数据库压力短时上升。 |
| `session-touch` | 会话活跃节流 | `admin:session:touch:` | 前缀 | 每请求写 `last_active_at` 的 60 秒节流 | 是（含会话标识） | 是 | 下一次请求会重新写一次会话活跃时间；不影响登录态，在线状态最多滞后一个节流窗口。 |
| `auth-refresh` | 刷新令牌白名单 | `admin:auth:refresh:` | 前缀 | refresh 令牌一次性/白名单校验 | 是（含令牌标识） | 是 | 所有刷新令牌失效：现有 access 令牌到期（最长 15 分钟）后必须重新登录。 |
| `auth-deny` | Access 令牌黑名单 | `admin:auth:deny:` | 前缀 | 登出后 access 令牌立即失效 | 是（含令牌标识） | **否** | 清除会让已登出但未过期的 access 令牌重新可用（最长 15 分钟），属安全降级，因此不提供清理。 |
| `auth-fail` | 登录失败计数 | `admin:auth:fail:` | 前缀 | 连续失败锁定 15 分钟 | 是（含用户名） | **否** | 清除会提前解除登录失败锁定，弱化暴力破解防护，因此不提供清理。 |

不可清理的命名空间只允许浏览 key 名与 TTL；单键删除与清空操作一律拒绝（`403` 语义的功能拒绝，返回业务错误码）。

## 4. 安全设计

| 约束 | 实现 |
|---|---|
| 只按命名空间操作 | 请求只接受白名单 `code`，前缀从注册表取；未登记 code 直接 `404 RESOURCE_NOT_FOUND`，未通过 code 校验的字符串不会进入任何 Redis 命令 |
| 禁止 `KEYS *` | 全部使用 `SCAN`（`ScanOptions.match(prefix).count(500)`）游标遍历；单键操作使用精确 `DEL`。代码中不出现 `keys(` 调用 |
| 页面分页 | 列表接口按页返回（默认 20、最大 100）；每次从游标 0 扫描到当前页所需条数即停止，不把全库载入内存；超过 256 个扫描批次仍未凑齐时返回 `truncated=true` |
| 不展示敏感值 | 后端从不读取 key 的 value；只返回 `key`、`ttlSeconds`、`type`。`sensitive=true` 的命名空间在响应中标记，前端提示“仅展示名称与 TTL” |
| 每次清理留审计 | 清理成功后写 `sys_oper_log`：模块「缓存管理」、动作、操作人、时间、IP、参数 JSON（`namespaceCode`、`key`、`deletedCount`）。审计失败不阻断清理（与既有审计一致），但会记录告警日志 |
| 幂等与隔离 | 单键删除返回实际删除数；清空命名空间只 `SCAN` 该前缀，天然不影响其他命名空间 |
| 连接异常 | Redis 不可用时概览返回 `connected=false` 并给出原因，清理操作返回业务错误，不返回半截成功 |

## 5. 接口设计

权限码：`system:cache:list`（概览/命名空间/明细）、`system:cache:clear`（所有清理动作）。两码只授 `SUPER_ADMIN`。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/cache/overview` | `system:cache:list` | 连接状态、内存占用、`DBSIZE`、各命名空间键数、未登记键数 |
| GET | `/api/v1/cache/namespaces` | `system:cache:list` | 白名单列表：code/名称/前缀/用途/键数/敏感/可清理/清除后果 |
| GET | `/api/v1/cache/keys?namespace={code}&page=&pageSize=&keyword=` | `system:cache:list` | 按命名空间分页浏览，返回 key 名、TTL 秒、类型；`sensitive` 标记 |
| DELETE | `/api/v1/cache/keys?namespace={code}&key={key}` | `system:cache:clear` | 删除单个 key；key 必须属于该命名空间 |
| DELETE | `/api/v1/cache/namespaces/{code}` | `system:cache:clear` | 清空命名空间（`SCAN` + 分批 `DEL`） |
| DELETE | `/api/v1/cache/session-cache` | `system:cache:clear` | 清理会话活跃节流缓存（等价于清空 `session-touch`，单独入口） |
| GET | `/api/v1/cache/clean-logs?page=&pageSize=` | `system:cache:list` | 本模块审计记录（从 `sys_oper_log` 过滤模块「缓存管理」） |

响应统一使用现有信封 `{ code, message, data }`；`ttlSeconds=-1` 表示永久，`-2` 表示已不存在。

## 6. 前端

页面：`/system/cache`，菜单挂在「系统管理」下。

- 概览区：Redis 连接状态、内存占用（人类可读）、key 总数、未登记键数。
- 命名空间表：名称、前缀、键数、用途、清除后果、是否敏感；操作按钮「浏览」「清空」（不可清理的置灰并提示原因）。
- 明细抽屉：分页浏览 key 名/类型/TTL，支持关键字过滤与单键删除；敏感命名空间顶部提示“不展示值内容”。
- 顶部操作：`清理过期会话缓存`，二次确认后调用独立接口。
- 清理记录：表格展示模块审计（操作人、时间、命名空间、删除数量、IP），分页。

## 7. 验证清单

1. 概览 `totalKeys` 与 `DBSIZE` 一致；命名空间计数之和 + 未登记键数 = `DBSIZE`。
2. 清空 `session-touch` 或 `datascope` 后该命名空间归零，`auth-refresh` 等其余命名空间数量不变（尤其会话不被清掉）。
3. 单键删除生效（删除公开配置单键后下一次公开配置请求自动回填）。
4. TTL 显示与 Redis `TTL` 一致；永久键显示 -1。
5. 清理记录可在 `sys_oper_log`（模块「缓存管理」）与页面「清理记录」中查到，包含删除数量。
6. 未登记命名空间 code 返回 404；不可清理命名空间返回功能拒绝。
7. `auth-refresh` 超过一页时，第 1/2 页 key 不重复且 `total` 正确。
8. 清理后登录、菜单、数据权限、公开配置、通知未读均正常。

## 8. 已知限制

- 概览的命名空间计数需要逐前缀 `SCAN`，键量极大时是 O(N)；这是“禁止 `KEYS *`”下的必然代价，页面加了手动刷新而非自动轮询。
- 分页为“扫描到第 page×pageSize 条即停”，翻到很后的页仍需从头扫描；超过 256 批返回 `truncated`，不承诺全量。
- 审计与清理不是原子操作：审计写库失败时清理已生效，仅告警。与既有操作日志的实现口径一致。
- 不可清理命名空间（`auth-deny`、`auth-fail`）是有意的安全取舍，与本轮“所有命名空间都能清”的字面要求存在偏差，已在交付报告单列。
