# CR-20261112-031 工作台统计接口（首页真实数据）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「首页仪表盘补真实统计接口（现在首页太空，第一印象就弱）」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **B（模块级）**：单模块纯新增（1 接口 + 1 权限码），向后兼容，未命中 `PRC-APR-001` |
| 关联 | 计划 `docs/plan/2026-11-12-dashboard-overview.md`；契约 **1.0.4 → 1.1.0**；Flyway **V18**；断言 #96 |

## 1. 改动

| 层 | 文件 | 说明 |
|---|---|---|
| DB | `V18__dashboard_permission.sql` | 新增权限码 `system:dashboard:view`（按 `path='/system'` 匹配父级，不硬编码 id），仅授 `SUPER_ADMIN`；无新表 |
| 后端 | `DashboardMapper` | 12 条计数 + 4 条分组，全部显式 SQL，只取 COUNT/SUM |
| 后端 | `DashboardAppService` | 趋势补零（缺日给 0）并按时间升序；状态分组转列表 |
| 后端 | `DashboardController` | `GET /api/v1/dashboard/overview`，`@PreAuthorize("hasAuthority('system:dashboard:view')")` |
| 契约 | `docs/api/openapi.yaml` | 新增 path + `DashboardOverviewResponse` 等 6 个 schema |
| 前端 | `api/dashboard.ts`、`views/dashboard/index.vue` | 4 张主 KPI + 8 张次级指标 + 近 7 天双色柱状图 + 任务状态分布 |

## 2. 三个刻意的取舍

### 2.1 无权限返回 403，不返回空数据

统计是**系统级聚合**（不随数据权限收窄），所以用权限码把关。无权限时如果返回 `{counts:{users:0,...}}`，前端会把"没权限"渲染成一排 0，用户以为系统是空的。因此：

- 后端：403 + 20301；
- 前端：捕获 403 → 隐藏统计区 → 显示"需要「查看仪表盘统计」权限"的说明卡。

### 2.2 聚合口径不受数据权限收窄 —— 已知取舍

用户数、文件数、日志数这些**按既有设计是有数据权限的**（部门经理只看本部门）。本接口故意**不收窄**，理由：

- 仪表盘的价值就是"全系统一览"，收窄后部门经理看到的是残缺数据，容易误判；
- 收窄需要 12 条 SQL 各自接入 `RoleDataPermissionHandler`，复杂度和出错面都大。

风险控制：权限码默认只授 `SUPER_ADMIN`。**若后续把该权限授予部门管理员，他会看到全系统聚合数**——这条已写进断言 #96，必须与授权操作一起看。

### 2.3 不引入图表库

柱状图用 CSS（`div` 高度百分比）实现，没有引入 `echarts`/`chart.js`。理由：为一张图引入 300KB+ 依赖不划算，且按本项目规则新增依赖要走 ADR。代价是图表能力有限（只有分组柱状图），出现更复杂图表需求时再评估。

## 3. 测试

| 用例 | 验证点 |
|---|---|
| `overviewMatchesContract` | 200 + 契约校验 + `trends` 长度 7 + **日期严格升序**（前端按数组顺序画图，乱序会画反） |
| `overviewRequiresPermission` | `dev1`（无权限）→ 403 + 20301，且 `data` 不存在 |
| `overviewRequiresLogin` | 未登录 → 401 |

E2E 新增 1 条：首页 KPI 数值 > 0（不是占位 0）、趋势 7 组、日期升序。

## 4. 自身踩的坑

1. `getCookies()` 返回数组，没有 `.stream()` → 用 `Arrays.stream`。
2. `Path.toUri()` 不抛 `URISyntaxException`，多写的 try/catch 反而编译不过。
3. 契约文件路径：测试工作目录是 `server/web`，用 `../docs/...` 找不到 → 复用 `ContractValidationTest` 的"逐级向上找"逻辑。
4. **测试库口令与 dev 库不同**：`*_test` 库种子账号仍是 `admin123`，而 dev 库被 E2E 改过 → 测试里用 `admin123`。
5. `V17` 给存量账号置了 `must_change_password=1`，`dev1` 登录后所有非白名单接口都是 403+20302，测不到权限判定 → 用例里先 `UPDATE sys_user SET must_change_password=0`。
6. 导出脚本 `export-openapi.ps1` 默认口令是 `admin123`，本地 dev 库改密后必须传 `-Password`。

## 5. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **200 用例通过**（common 26 / system 153 / web 21） |
| 前端 | `pnpm build` + `test:unit` | 通过 / **97 通过** |
| E2E | `pnpm exec playwright test` | **8/8 通过** |
| 契约导出 | `export-openapi.ps1 -Password ...` | `openapi.generated.yaml` 含 `/api/v1/dashboard/overview` |
| 权限矩阵 | `export-permission-matrix.ps1` | 权限码 70 个（含 `system:dashboard:view`） |
| 文档门禁 | `spec-lint` | error 0 |

## 6. 遗留

- 大表（操作日志百万级）下 12 条 COUNT 可能变慢：目前都走索引，量级上来后加缓存或采样。
- 指标不可配置（要改口径得改代码）：无验收方前不做。
- 未做自动刷新（避免轮询成本）。