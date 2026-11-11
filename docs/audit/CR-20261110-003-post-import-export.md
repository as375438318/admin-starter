# CR-20261110-003 岗位管理接入导入导出

| 项目 | 内容 |
|---|---|
| 状态 | IMPLEMENTED（2026-11-10） |
| 档位 | **B · 模块级**（纯新增，向后兼容；逐条见 §4） |
| 触发 | 用户批准：示例任务跑通后，用岗位管理验证导入导出框架通用性 |
| 关联 | `docs/design/import-export-framework.md`、`docs/design/import-export-onboarding.md`、`ADR-004-excel-library-selection.md`、`V16__post_import_export.sql` |

## 1. 变更内容

1. 框架入口上移：导入入口（`ExcelImportAppService`）、模板入口（`ExcelTemplateAppService`）、导出编排（`ExcelExportPlanner`）、导出执行器按模块分发（`ExportFileWriter`）、导出任务 DTO（`ExportTaskItem`）从“示例模块专用”改为通用层；示例任务改为调用通用入口。
2. 岗位接入：`PostExcelSchema`（字段元数据）、`PostImportHandler`（引用数据预加载 + 落库）、`PostExportAppService` / `PostExportRowAssembler` / `PostExportFileWriter`（导出查询与分页取数）。
3. 接口：`/api/v1/posts/import-template`、`/import`、`/export`、`/export-tasks`（`/{id}`、`/cancel`、`/{id}/file`），权限码 `system:post:import`、`system:post:export`。
4. 数据：`V16` 新增 2 枚权限码菜单并授权 `SUPER_ADMIN`、`DEPT_MANAGER`；不改既有表、不改既有权限。

## 2. 事实来源（字段规则逐条）

| 字段 | 规则 | 来源 |
|---|---|---|
| 所属部门 | 必填、部门必须存在 | `PostDtos.SaveRequest.deptId` `@NotNull`；`PostAppService.validateDept`；`sys_post.dept_id` NOT NULL |
| 岗位编码 | 必填、`^[a-zA-Z0-9_]{2,64}$`、全局唯一（含文件内重复） | `PostDtos.SaveRequest.code` `@NotBlank`/`@Pattern`；`PostAppService.checkCodeUnique`；`sys_post.uk_post_code` |
| 岗位名称 | 必填、≤64 字 | `PostDtos.SaveRequest.name` `@NotBlank`/`@Size(max=64)`；`sys_post.name` varchar(64) |
| 排序号 | 可空、默认 0、仅要求整数 | `PostDtos.SaveRequest.sort`；`PostAppService.apply`（默认 0）；`sys_post.sort` INT。**后端无范围校验，导入不新增 0–9999 规则**（前端 `el-input-number` 的 0–9999 是界面约束） |
| 状态 | 可空、默认启用；接受“启用/停用”与 true/false | `PostDtos.SaveRequest.isEnabled`；`PostAppService.apply`；`sys_post.is_enabled` |

## 3. 权限码判断

- 沿用 `system:post:` 前缀新增 2 枚：`import`（模板下载复用，不单设）、`export`（导出与导出任务/下载复用）。
- **未合并成一枚**：导入是写操作、导出是批量读取；合并会让“只读角色被授予导出的同时也获得导入写权限”。
- 按 `PRC-APR-001` 逐类判断：①契约版本演进 未命中；②删列/改类型 未命中（`V16` 只新增菜单行）；③权限放宽 **未命中**（新增权限码并显式授权，既有角色可见范围不变，`viewer` 仍被拒绝，实测 403）；④安全降级 未命中；⑤状态机变更 未命中；⑥模块启停 未命中；⑦其他不可回滚变更 未命中。
- 结论：**B 档**，需 CR（本文），不需要 ADR；回滚方式为删除 2 枚权限码与菜单行，代码侧移除岗位接入文件。

## 4. 影响面

| 面 | 影响 |
|---|---|
| 契约 | `docs/api/openapi.yaml` 新增 7 个岗位导入导出路径；`ImportResult`/`ExportAccepted`/`ExportTaskItem` 由示例专用名改为通用名 |
| 前端 | 岗位页新增 4 个按钮、导入对话框、导出任务抽屉；`apps/shared` 类型别名化（`DemoTask*` 保留为别名） |
| 数据 | 仅 `sys_menu`、`sys_role_menu` 新增行；业务表 `sys_post` 未改结构 |
| 其他模块 | 示例任务调用点改为通用入口，行为不变（同一引擎、同一门槛） |

## 5. 验证

见 `docs/audit/impl-notes.md`「岗位接入实测」：模板下载、2001 行导入、错误整批回滚、同步/异步导出、重复提交拦截、`viewer` 403、`deptmgr` 放行、导出文件不进 `sys_file`，均为本机实跑。
