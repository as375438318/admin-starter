# CR-20261110-002 示例任务导入导出与模板下载（导入同步 20000 行 + 导出异步）

## 1. 背景

- 示例业务（`biz_demo_task`）目前只有列表与单条 CRUD，缺少批量入口；用户要求以「示例任务」为唯一落地模块，补齐模板下载、导入、导出，并把字段元数据、模板、导入校验、导出表头做成同源派生。
- 2026-11-10 用户最终拍板：**导入单一同步通道，单批 20000 行、单文件 50MB，单事务、任一行错整批回滚**；**不做自动转异步、不做部分导入、不做用户分批**。导出仍为异步 + 5 秒快通道。
- 不改的后果：批量数据无入口；手写模板与校验必然漂移；2 万行用 `XSSFWorkbook` 全量载入存在 OOM 风险。

## 2. 范围

- 涉及模块：示例业务导入、导出、模板下载；新增 1 张导出任务表 `sys_export_task`；新增导出任务状态机文档；新增 2 枚权限码；新增接口。
- 涉及条目：`docs/requirements/system-modules.md` §10、`docs/design/import-export-framework.md`、`docs/design/state-machine.md`、`docs/api/openapi.yaml`。
- 不涉及：其它业务模块；`biz_demo_task` 结构；错误码；`sys_file` 表与文件管理列表；导入异步。

## 3. 涉及事实

| 事实 | 现状（as-is） | 定义方 |
|---|---|---|
| 示例任务字段 | `biz_demo_task` 9 列；`title` 非空 128、`status` 非空默认 `TODO`、`dept_id` 非空、`owner_id`/`create_by` 可空 | `V3__demo_business.sql`；`docs/design/data-model.md` |
| 单条校验口径 | `title` 非空 ≤128；`status` 仅 `TODO/DOING/DONE`，空白归 `TODO`；`deptId` 必填且须存在；`ownerId` 选填、须存在、缺省当前用户 | `DemoTaskDtos.SaveRequest`；`DemoTaskAppService` |
| 唯一约束 | 仅主键；标题允许重复 | `V3__demo_business.sql` |
| 数据权限 | `biz_demo_task` 受控（`dept_id`/`create_by`/`owner_id`） | `DataPermissionTables`；`permission-matrix.md` |
| 字段脱敏 | 三态；`MaskResolver` 唯一判定、`MaskFormat` 唯一算法 | `MaskResolver`；`MaskFormat` |
| 审计 | `sys_oper_log` 由切面写 module/action/params/result/elapsed | `OperLogAspect` |
| 通知 | `sys_notice` 支持按人发布 | `NotificationAppService`；V10 |
| 定时任务 | `sys_job` + `JobRegistry` 单机调度 | `JobScheduler`；`JobRegistry` |

`INDEX-facts.md` 未登记导入/导出/模板事实，**索引未覆盖**；建议后续补入，本 CR 不修改索引正文。

## 4. 影响文档与代码

| 文件 | 改动 |
|---|---|
| `docs/audit/CR-20261110-002-demo-task-import-export.md` | 本变更单 |
| `docs/plan/2026-11-10-demo-task-import-export.md` | 档位、步骤、验收 |
| `docs/design/import-export-framework.md` | 新增设计 v3.0 |
| `docs/design/state-machine.md` | 新增导出任务状态机 |
| `docs/decisions/ADR-004-excel-library-selection.md` | 选型记录 |
| `docs/requirements/system-modules.md` | §10 能力与边界 |
| `docs/api/openapi.yaml` | 模板/导入/导出/导出任务接口 |
| `server/web/.../db/migration/V15__demo_task_import_export.sql` | 1 张任务表 + 2 权限码 + 菜单 + 授权 + 过期清理任务 |
| `server/system/.../application/excel/**` | SAX 事件流解析、模板、导出写入、schema |
| `server/system/.../application/DemoTaskImportService.java` / `DemoTaskExportService.java` | 同步导入、同步/异步导出 |
| `server/system/.../domain/repository/ExportTaskRepository.java` + 实现 | 任务持久化 |
| `server/web/.../controller/DemoTaskController.java` | 模板/导入/导出/任务端点 |
| `server/common/.../cache/CacheNamespaces.java` | 导入去重命名空间（TTL 5 分钟） |
| `server/common/.../log/OperLogContext.java` | 审计补充字段 |
| `apps/pc/src/api/http.ts`、`api/demoTask.ts`、`views/demo/task/index.vue` | 入口、阶段提示、任务抽屉 |
| `docs/baseline/modules.md`、`assertions.md`、`docs/audit/impl-notes.md` | 回填 |

## 5. 兼容性

- 新增接口与 1 张任务表（纯新增）；既有接口/字段不变；`openapi.yaml` 版本号不升级。
- 存量数据不迁移；`biz_demo_task` 不变。
- 旧前端未重新登录只缺按钮，不影响既有功能。
- 导出临时文件不写 `sys_file`、不进文件管理列表与回收站。

## 6. 回滚方式

- 代码：移除导出任务通道与 Excel 包、前端入口、`http.ts` 的 blob 支持、`CacheNamespaces` 新命名空间。
- 数据：`DROP TABLE sys_export_task`；删除 V15 插入的菜单/授权/任务行，并删除临时目录。
- 文档：删除本 CR、计划、设计、状态机、选型记录，`openapi.yaml` 删除新增路径，`system-modules.md` §10 恢复原文。
- 回滚后：示例业务回到单条 CRUD；Redis 去重键 5 分钟自然过期；无后台文件残留（手动删临时目录）。

## 7. 分级与审批

- 档位：**B（模块级）**。只涉及示例业务；纯新增接口/表/权限码；未改变权限范围与安全强度。
- `PRC-APR-001` 七类逐条：

| 类别 | 是否命中 | 依据 |
|---|---|---|
| 1. 契约破坏兼容或版本演进 | 未命中 | 只新增接口 |
| 2. 数据模型不可逆变更 | 未命中 | 只新增 1 张任务表，可回滚删除；不改既有表 |
| 3. 权限模型或数据范围放宽 | 未命中 | 新增 2 枚权限码，只授管理员/部门管理员；不放宽数据范围 |
| 4. 安全降级 | 未命中 | 导出复用脱敏与数据权限；只新增；临时文件按人隔离 |
| 5. 状态机新增状态或新增非法流转豁免 | 未命中（判据解释） | 新建的是**独立的导出任务状态机**，未给既有业务状态机新增状态，也未新增非法流转豁免；新状态机自身禁止非法流转 |
| 6. 验收标准变更或新增业务术语定义 | 未命中 | 字段规则沿用既有 DTO/表；「快通道」属实现语义，不新增对外业务术语 |
| 7. 可选模块启用或停用 | 未命中 | 无 |

- 必须人工项：无。
- 签核：用户 2026-11-10 拍板范围、默认值、导出红线、导入 20000 行/50MB 单事务口径（见本轮任务描述）。签核早于本次文档与代码改动。
- 无待拍板项。

## 8. 代码改动顺序

1. 设计与计划更新、状态机文档；
2. `system-modules.md`、`openapi.yaml`；
3. V15 迁移（任务表 + 权限 + 清理任务）；
4. 后端 SAX 解析 / 模板 / 导出写入；
5. 同步导入（20000 行、单事务、去重、阶段提示）；
6. 导出快通道与异步任务（执行器、进度、取消、通知、临时文件、清理、启动补偿）；
7. 前端入口、阶段提示、任务抽屉；
8. 构建、重启、真机验证；回填基线、权限矩阵、generated、impl-notes，`spec-lint` error 0。

## 9. 回归范围

- 示例任务 CRUD 不受影响。
- 导入成功、整批回滚、2 万行上限、50MB 上限、重复提交、阶段提示。
- 导出同步/异步、超限拒绝、下载权限、脱敏列随权限变化、临时文件不进文件管理、取消与过期。
- 权限 403、审计日志、任务非法流转、启动补偿。
- 证据逐点记入 `docs/audit/impl-notes.md`。

## 10. 异议记录

- 初版设计主张「超过 2000 行拒绝、让用户分批」→ 用户否决；曾改为自动双通道，后用户再次收敛为单一同步通道 20000 行。
- 初版把事件流列为后续改造 → 用户要求本轮就换；已改为 SAX 解析。
- 初版 10 分钟去重窗口 → 无依据；已改为按最坏耗时推导的 5 分钟，并在失败时立即释放。
- 初版导出上限 5000 行 → 异步通道下上调为 100000 行（设计 §4.1），同步快通道仍为 2000 行。
