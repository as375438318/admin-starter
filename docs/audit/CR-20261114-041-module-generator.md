# CR-20261114-041 模块生成器（P0-1）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-13「系统接下来的功能发展方向…」→「按照你的想法你觉得应该如何做」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-13 |
| 档位 | **B**：单模块（`system`）＋纯新增且向后兼容（新表 `demo_customer`、4 个权限码、5 个接口）；未命中 `PRC-APR-001` |
| 设计 | `docs/design/module-generator.md` |
| 断言 | #107（生成器红线）、#108（生成物验收口径） |

## 1. 背景与目标

用户问"功能发展方向"，我给的判断是：**瓶颈不是功能数量，而是"从底座到一个新模块"的单位成本**——当前一个 CRUD 模块要写 15–17 个文件（字典 15、岗位 17），且一致性靠自觉（列 key 与后端导出 schema 对不上、按钮权限挂到目录下，都是靠 E2E 才抓到的）。

本轮交付两件东西：
1. **生成器本体**：`tools/gen-module/cli.mjs`（Node，与 `tools/spec-lint` 同形态）+ JSON 描述文件；
2. **活样例**：用生成器真的拉出「客户档案」模块（表 + 菜单 + 权限码 + 后端 5 接口 + 前端页面 + 契约），编译、跑测试、过 E2E。

## 2. 形态决策（为什么不是菜单功能）

| 形态 | 结论 | 理由 |
|---|---|---|
| **CLI 脚手架** | **采用** | 产物进仓库、能 review、能过 CI；与既有 `scripts/gen-data-model.ps1`、`export-openapi.ps1` 同类 |
| 运行时菜单式生成器 | 否决 | 服务器写源码目录＝准 RCE；绕过 code review、契约测试、spec-lint；多环境必然漂移 |
| 纯约束（只写规范不生成） | 不足 | 约束只能"拦住错"，不能"默认对"；新模块仍要人肉写 15 个文件 |

三层分工：**约束守门**（已有）→ **生成器给正确起点**（本轮）→ **只读预览页**（可选，未做）。

## 3. 影响面清单

| # | 对象 | 变化 | 验证方式 |
|---|---|---|---|
| 1 | 表 | 新增 `demo_customer`（6 业务列 + 公共列），Flyway **V20** | `flyway_schema_history` 20 success + 建表语句复核 |
| 2 | 菜单 | 新增 C 型页面菜单「客户档案」`/demo/customers`（顶层，`sort=12`）+ 3 个 F 型按钮（create/update/delete）；`list` 权限码挂在 C 菜单上，与其它模块一致 | `sys_menu` 查询：76(C) + 77/78/79(F) |
| 3 | 权限码 | 新增 `demo:customer:list|create|update|delete`（总数 70 → 74） | `docs/design/permission-matrix.md`、`scripts/export-permission-matrix.ps1` |
| 4 | 接口 | 新增 `GET/POST /api/v1/demo/customers`、`GET/PUT/DELETE /api/v1/demo/customers/{id}` | 契约 **1.3.0** ＋ as-is 重新导出；运行时冒烟（创建/列表/排序/白名单外排序/修改/删除）全通 |
| 5 | 前端 | 新增 `api/demoCustomer.ts`、`views/demo/customer/index.vue`（列设置 + 排序 + 抽屉表单）；路由由菜单驱动，未改 `router` | `pnpm build`、E2E |
| 6 | 工具链 | 新增 `tools/gen-module/cli.mjs`、`examples/customer.json`、`docs/api/snippets/customer.yaml` | 生成命令实跑；`--dry` 清单核对 |
| 7 | 契约版本 | `1.2.0 → 1.3.0`（纯新增、向后兼容） | `docs/api/openapi.yaml` 变更记录 |
| 8 | 授权 | `SUPER_ADMIN` 全量；`DEPT_MANAGER` 只读（`demo:customer:list`） | SQL 复核 |
| 9 | 既有模块 | **未改动**（无回归面） | 全量 `mvn verify` |

## 4. 生成物清单（`customer` 模块实跑结果）

生成 14 个文件（`--dry` 与实跑一致）：领域 3、持久化 4、应用 3、接口 1、迁移 1、前端 2（api + 页面）、契约片段 1。

## 5. 验证

| 项 | 结果 |
|---|---|
| 后端 | `mvn -pl web -am verify` **BUILD SUCCESS**（221 用例；V20 同时应用到测试库 `admin_starter_test`） |
| 前端 | `pnpm build` 通过（含 `vue-tsc`）、单测 **116** 通过 |
| E2E | **12/12**（新增 `generated-module.spec.ts`：菜单可见 → 列表可开 → 新增 → 关键词搜索 → 删除，数据自清理） |
| 运行时冒烟 | 创建 code=0、列表 total=1、`sort=name&order=asc` 生效、**白名单外 sort 静默回落不报错**、修改、删除均 code=0 |
| 契约 | YAML 解析通过；`paths=56`、`schemas=65`；`CustomerPageResponse.list` 引用共享 `CustomerItem`（不再是错误的 `CustomerSaveRequest`） |
| spec-lint | error 0 / warning 49 |

## 6. 过程中踩的坑（都已在生成器里修掉）

1. **`keyword` 包装器生成了非法 Java**：`w.like(a).or()\n like(b)` —— 断链。改为 `w.like(a).or().like(b)` 单链。
2. **时间字段类型不匹配**：领域模型是 `LocalDateTime`，DTO 用 `Instant`，生成代码少了 `TimeSupport.toInstant(...)` 转换。已在模板里补上（与 `PostAppService` 同口径）。
3. **契约片段插进 `openapi.yaml` 时把两行粘连成一行**（PowerShell here-string 末尾换行丢失），导致 YAML 解析失败、**18 个契约测试同时红**。教训：改 `openapi.yaml` 后**必须先做一次 YAML 解析**再跑测试。
4. **`CustomerPageResponse.list` 最初引用了 `CustomerSaveRequest`**（语义错：列表项不是保存请求）。改为共享 `CustomerItem`。
5. **E2E 删除确认框文案**：`confirmDelete` 的确认键是「删除」不是「确定」。

## 7. 未做（明确边界）

生成器**不生成**测试、导入导出、数据权限、脱敏；只支持单表 CRUD，不支持主子表/树形/三级菜单自动化；契约片段较薄，仍需人工按现有风格补全。这些写进 `docs/design/module-generator.md` §7，避免被当成已有能力。

## 8. 异议记录

无。用户在本轮前已明确要求"按你的想法做"，形态选择（CLI 而非运行时菜单）在设计文档中留了备选与否决理由。
