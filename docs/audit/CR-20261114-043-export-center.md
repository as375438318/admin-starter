# CR-20261114-043 通用导出中心

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-13「全部都处理一下」（承接 P0-2 的另一半） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-13 |
| 档位 | **B**：单模块（`system`）＋纯新增且向后兼容（2 个只读接口、1 个权限码、Flyway V22）；未命中 `PRC-APR-001` |
| 断言 | #111 |

## 1. 背景

上一轮做完「导出按勾选列」（P3）时，能力只长在**两个模块**自己的 AppService 里（示例任务、岗位各一套入口）。每加一个可导出模块就要复制一遍"组装查询 → 计数 → 同步/异步规划 → 裁剪列 → 记录任务"。本轮把它收成 SPI + 一个统一入口。

## 2. 设计

| 层 | 内容 |
|---|---|
| SPI | `ExportModule`：`key()` / `name()` / `permission()` / `plan(Map<String,String> params)` |
| 注册表 | `ExportModuleRegistry`：启动期收集所有 bean 按键索引，**重复键直接启动失败** |
| 入口 | `GET /api/v1/exports/modules`（清单）、`GET /api/v1/exports/{moduleKey}`（导出） |
| 鉴权 | `@PreAuthorize("@exportGuard.allowed(#moduleKey)")` → 查表取模块声明的权限码，再比对当前用户权限 |
| 公共流程 | 不变：`ExcelExportPlanner` 负责同步/异步、列裁剪、任务记录；入口只做分发 |

已注册模块：`customer`（新写 `CustomerExcelSchema`）、`demo-task`、`post`（后两者委托既有服务，证明通用入口能复用旧实现）。

## 3. 影响面清单

| # | 对象 | 变化 | 验证 |
|---|---|---|---|
| 1 | 接口 | 新增 `GET /api/v1/exports/modules`、`GET /api/v1/exports/{moduleKey}` | 集成测试 4 条 + 运行时冒烟 |
| 2 | 权限码 | 新增 `demo:customer:export`（71 → 72），挂客户档案菜单下 | `sys_menu` 复核 |
| 3 | 数据 | `sys_export_task` 复用，无新表；Flyway **V22**（仅权限码与授权） | `flyway_schema_history` |
| 4 | 前端 | 新增 `api/export.ts`；客户档案页加「导出」按钮（生成器模板同步支持） | `pnpm build`、E2E |
| 5 | 既有导出接口 | `demo-tasks/export`、`posts/export` **保留不动**（前端仍在用），通用入口是新增通道 | 既有测试全绿 |
| 6 | 契约 | `1.4.0 → 1.5.0`（纯新增） | YAML 解析 + as-is 重导出 |

## 4. 关键取舍

1. **不在通用入口放宽鉴权**：权限码按模块查表，未注册/无权限统一 403——不给"模块是否存在"的探测面。
2. **入口保持很薄**：同步/异步阈值、列裁剪、任务快照都留在 `ExcelExportPlanner`，避免两套规则。
3. **生成器同步支持导出**：新生成的模块页面直接带导出按钮，`fields` 取「列设置」的勾选结果（设计 D6）。
4. **旧接口不删**：前端仍用，删了属于破坏性变更（C 档）。迁移到通用入口是后续独立动作。

## 5. 验证

| 项 | 结果 |
|---|---|
| 后端 | `mvn -pl web -am verify` **BUILD SUCCESS**：common 26 / system 164 / web **43**（+4 ExportCenterTest）＝ **233** |
| 前端 | 构建通过（含 `vue-tsc`）、单测 **116** |
| E2E | **13/13** |
| 冒烟 | `/exports/modules` 返回 3 个模块；`/exports/customer?fields=name,phone` 返回 xlsx（3608 字节、UTF-8 文件名）；无权限角色与未知模块均 403 |
| 契约 | 1.5.0，as-is 已重导出 |
| spec-lint | error 0 / warning 49 |

## 6. 未做

- 旧的两个导出接口未迁移到通用入口（保留兼容）；
- 未做"导出中心"管理页（任务列表仍按模块各自查看）；
- 未给全部 16 个列表页接导出：需要各自补 `ExcelSchema`，属逐模块增量工作。

## 7. 同时更正上一轮的误判

上一轮我报告"重复迁移导致 `main` 后端起不来"，**结论错误**：`V20/V21/V22__demo_customer` 版本号互不相同，Flyway 正常接受（PR #41 的 CI 日志可证）。真正的撞号是我随后新增 `V21__change_audit` 时目录里出现两个 V21。清理动作保留（删重复内容文件、生成器迁移幂等），归因已更正到 `CR-20261114-042` §5 与断言 #110。