# 计划 · 工作台统计接口（2026-11-12）

| 项目 | 内容 |
|---|---|
| 档位 | **B（模块级）**：单模块纯新增（1 个接口 + 1 枚权限码），向后兼容，未命中 `PRC-APR-001` |
| 关联 | `CR-20261112-031`；契约 `docs/api/openapi.yaml` 1.0.4 → **1.1.0**；Flyway **V18** |
| 目标 | 首页从"只有欢迎语 + 快捷入口"变成有真实运营数据的工作台 |

## 1. 范围

**做**：

- `GET /api/v1/dashboard/overview`：一次返回 counts / storage / today / trends(近 7 天) / taskStatus
- 权限码 `system:dashboard:view`（Flyway V18 新增，仅授 `SUPER_ADMIN`）
- 前端首页：4 张主 KPI + 8 张次级指标 + 近 7 天双色柱状图 + 示例任务状态分布
- 无权限时**隐藏统计区并说明原因**（不是渲染一排 0）

**不做**：

- 不做可配置的图表/自定义看板（无可配置需求的验收方）
- 不引入图表库（`echarts` 等）——柱状图用 CSS 实现，避免为一张图引入 ~300KB 依赖（需 ADR 才能加库）
- 不做数据权限收窄（统计是系统级口径，用权限码而非数据范围把关，取舍见 CR）
- 不做实时推送/自动刷新（手动刷新页面即可，避免长连接与轮询成本）

## 2. 聚合口径

| 指标 | 口径 |
|---|---|
| users / enabledUsers | `sys_user` 未删除 / 未删除且启用 |
| roles / depts / posts / dictTypes / dictItems / configs | 各自表未删除计数 |
| notices | 未删除且未撤回（与列表页 PUBLISHED 一致） |
| jobs / enabledJobs | `sys_job` 未删除 / 状态 ENABLED |
| activeSessions | 未失效且最近 30 分钟有活跃（与会话页"在线"一致） |
| storage | `sys_file` 未删除的文件数 + 字节和（不含回收站） |
| today | 当日（UTC 自然日）登录成功 / 登录失败 / 操作条数 |
| trends | 近 7 天按天分组，缺日补 0，按时间升序 |
| taskStatus | `biz_demo_task` 未删除按 status 分组 |

## 3. 落地步骤

1. Flyway V18：新增权限码菜单行 + 授 `SUPER_ADMIN`
2. `DashboardMapper`（显式 SQL 聚合）→ `DashboardAppService`（补零、排序）→ `DashboardController`
3. 契约：新增 path + 6 个 schema，版本 1.1.0，重新导出 `openapi.generated.yaml`
4. 前端：`api/dashboard.ts` + 共享类型 + 首页改造（含 403 分支与加载态）
5. 测试：后端 3 条（结构/契约、无权限 403、未登录 401）+ E2E 1 条（KPI 非 0、趋势 7 天升序）
6. 回填：`modules.md`、`system-modules.md`、`permission-matrix.md`（脚本重跑）、`assertions.md`、`impl-notes.md`、README

## 4. 风险

| 风险 | 处置 |
|---|---|
| 12 条 COUNT 在超大表上变慢 | 均为索引/主键计数；趋势查询按 `created_at` 有索引。数据量上去后再考虑缓存 |
| 聚合不受数据权限收窄 | 用权限码把关（仅 SUPER_ADMIN）；已在 CR 与断言中写明，避免误授予部门管理员 |
| 首页多一个请求 | 与登录后 bootstrap 并行，失败不影响主流程（有独立错误态） |