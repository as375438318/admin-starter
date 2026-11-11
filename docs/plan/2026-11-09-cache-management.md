# 缓存管理实施计划（2026-11-09）

## 1. 档位判定（`AGENTS.md` §4.1）

**结论：B 档（模块级）**。只涉及“系统管理 · 缓存”一个新模块及其前端页面；只做纯新增且向后兼容：新接口、新权限码、新菜单，不新增业务表，不改既有接口与表结构。

`PRC-APR-001` 逐条核对：

| 类别 | 是否命中 | 依据 |
|---|---|---|
| 1. 契约破坏兼容或版本演进 | 未命中 | 只新增 `/api/v1/cache` 接口；既有路径、请求字段、响应字段均不改；`openapi.yaml` 版本号不升级 |
| 2. 数据模型不可逆变更 | 未命中 | 不新增/删除表与列；V14 只插入菜单与权限码 |
| 3. 权限模型或数据范围放宽 | 未命中 | 新增 2 枚权限码且只授 `SUPER_ADMIN`；既有权限与数据范围不变 |
| 4. 安全降级 | 未命中 | 新增缓存清理能力，但禁止任意键操作、禁止 `KEYS`、不做值读取，属于安全增强 |
| 5. 状态机新增状态或豁免 | 未命中 | 无状态机；命名空间是否可清理是静态登记，不是流转状态 |
| 6. 验收标准变更或新增业务术语定义 | 未命中 | 命名空间行为与清除后果由本轮指令与代码现状确定，不是 AI 新增业务口径 |
| 7. 可选模块启用或停用 | 未命中 | 不启停任何模块 |

## 2. 实施步骤

| 步 | 内容 | 产出 | 完成标准 |
|---|---|---|---|
| 1 | 现状核查 | Redis `SCAN` 实测 + 代码写入点定位 | 七个命名空间、前缀、TTL、实测键数齐全（见设计文档 §2） |
| 2 | 设计文档 | `docs/design/cache-management.md` | 白名单、防 `KEYS`、分页、敏感值、审计、清除后果齐全 |
| 3 | 能力与契约 | `docs/requirements/system-modules.md`、`docs/api/openapi.yaml` | 接口与权限码可逐条核对 |
| 4 | 迁移 V14 | `server/web/src/main/resources/db/migration/V14__cache_management.sql` | 只新增菜单 + 2 权限码 + `SUPER_ADMIN` 授权；按业务键匹配 |
| 5 | 白名单与 Redis 网关 | `common.cache.CacheNamespace(s)`、`RedisCacheGateway` | 前缀唯一定义；全部 `SCAN`；未登记 code 拒绝 |
| 6 | 应用服务与控制层 | `CacheAdminAppService`、`CacheController`、`CacheDtos`、审计记录 | 权限码生效；清理写操作日志且含删除数量 |
| 7 | 前端 | `apps/pc/src/views/system/cache/index.vue`、`api/cache.ts`、`types.ts` | `pnpm --filter @admin-starter/pc build` 通过 |
| 8 | 实测 | 概览一致性、命名空间隔离、分页、单删、TTL、未登记拒绝、审计 | 见 §3 验收项，贴真实响应 |
| 9 | 回填与门禁 | 基线、权限矩阵/数据模型重跑、`openapi.generated.yaml` 重导、`spec-lint` | `node tools/spec-lint/cli.mjs` error 0 |

## 3. 验收口径

| 编号 | 验收项 |
|---|---|
| `AC-CACHE-01` | 概览 `totalKeys` 与 Redis `DBSIZE` 一致；命名空间计数之和 + 未登记键数 = 总数 |
| `AC-CACHE-02` | 命名空间 key 数与实测一致；`auth-refresh` 超过一页时分页正确 |
| `AC-CACHE-03` | 清空某命名空间后该命名空间归零，其他命名空间数量不变（会话不被误清） |
| `AC-CACHE-04` | 单个 key 删除生效 |
| `AC-CACHE-05` | TTL 显示正确（含永久键 -1） |
| `AC-CACHE-06` | 清理记录在操作日志可查，含命名空间与删除数量 |
| `AC-CACHE-07` | 未登记命名空间 code 返回 404；不可清理命名空间被拒绝 |
| `AC-CACHE-08` | 清理后登录、菜单、数据权限、公开配置、通知未读正常 |
| `AC-CACHE-09` | 前端构建通过；`spec-lint` error 0 |

## 4. 风险与处置

| 风险 | 触发条件 | 处置 |
|---|---|---|
| 误清 `auth-refresh` 导致用户重登 | 操作人选错命名空间 | 清除后果在页面原样展示 + 二次确认；`auth-deny`/`auth-fail` 直接设为不可清理 |
| `SCAN` 大键量耗时 | 键量达到百万级 | 每批 500、最多 256 批后返回 `truncated`；概览不自动轮询 |
| 审计写库失败 | 数据库异常 | 清理已生效，记录告警日志；页面清理记录可能缺失，与既有操作日志口径一致 |
| 前缀漂移 | 后续代码新增 Redis 写入点未登记 | 前缀收敛到 `CacheNamespaces` 单一定义；新增写入点必须走该常量（写入基线断言） |
