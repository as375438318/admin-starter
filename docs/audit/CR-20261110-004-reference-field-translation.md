# CR-20261110-004 引用字段翻译（部门 / 负责人 / 状态）

| 项目 | 内容 |
|---|---|
| 状态 | IMPLEMENTED（2026-11-10） |
| 档位 | **B · 模块级**（框架 + 两个已接入模块，向后兼容的纯新增能力；导入文件格式口径调整见 §5） |
| 触发 | 用户指令：引用字段翻译作为框架级能力，示例任务三列改显示值、岗位状态改中文，模板/导出/导入同源 |
| 关联 | `docs/design/import-export-framework.md` §13、`docs/design/import-export-onboarding.md`、`CR-20261110-003` |

## 1. 变更内容

1. 框架新增 `FieldKind`（`TEXT`/`ENUM`/`REF_DEPT`/`REF_USER`）与 `ExcelField.kind`、`ExcelField.refValue`：业务只声明字段类别与实体原始值。
2. 框架新增 `ReferenceDirectoryService`：一次查询建立「显示值 ↔ 存储值」双向表（部门完整路径、`姓名（部门）`、枚举中文），供模板下拉、导入反查、导出正译共用。
3. `ExcelRowValidator` 承接引用翻译与全部引用类错误码（`REF_NOT_FOUND`/`REF_AMBIGUOUS`/`REF_FORBIDDEN`）；`DemoTaskImportHandler`、`PostImportHandler` 删除部门/负责人预加载与存在性校验。
4. `ExcelTemplateWriter` 新增隐藏且锁定的「引用数据」页，数据页用区域引用绑定下拉；`showErrorBox=false`，下拉只是建议。
5. `ExcelExportWriter` 按 `kind` 正译显示值，导出列与模板显示值一致。
6. `ExcelImportService.verifyHeader` 从「表头完全相等」改为「模板要求的列必须齐全」，允许导出文件带导出专用列直接回导。
7. `DeptAppService` 创建/更新部门时禁止名称含 `/` 或 `\`。
8. 示例任务三列改显示值（部门完整路径、`姓名（部门）`、中文状态）；岗位状态与部门同步改显示值。

## 2. 范围

| 面 | 影响 |
|---|---|
| 后端框架 | `application/excel/*`（`FieldKind`、`ReferenceDirectoryService`、`ExcelRowValidator`、`ExcelImportService`、`ExcelTemplateWriter`、`ExcelExportWriter`、`ExcelField`） |
| 后端接入点 | `demo/*`、`post/*` 的 schema、导出行、装配器、导入处理器 |
| 前端 | **无改动**（列表与表单原本就显示中文/名称） |
| 数据库 | 无表结构变更、无迁移 |
| 权限码 | 无新增、无变更 |
| 配置项 | 无新增 |

## 3. 涉及事实

| 事实 | 来源 |
|---|---|
| `sys_dept` 无 path 字段，只有 `parent_id`/`ancestors` | `V1__init_schema.sql`；`Dept` |
| `(parent_id, name)` 在未删除范围内唯一 | `uk_dept_parent_name` |
| `sys_user` 无 `nickname` 唯一约束 | `V1__init_schema.sql` |
| `sys_dept`/`sys_user` 属于数据权限 `EXEMPT` 表 | `DataPermissionTables` |
| 数据权限条件含 `unrestricted`/`deptIds`/`selfIncluded` | `DataScopeCondition`、`DataScopeProvider` |
| 导入错误 DTO 字段级 `code` 可扩展 | `ValidationErrorData.FieldError` |

## 4. 档位与审批

按 `PRC-APR-001` 七类逐项判断：

| 类别 | 结论 | 理由 |
|---|---|---|
| ① 契约破坏兼容或版本演进 | **未命中** | HTTP 契约（路径、请求/响应结构、错误码）不变；导入文件格式口径调整见 §5，不涉及 `openapi.yaml` |
| ② 数据模型不可逆变更 | 未命中 | 无表结构变化 |
| ③ 权限模型或数据范围放宽 | 未命中 | 部门可指定范围是**收紧**（新增数据权限校验），无角色新增或可见范围扩大 |
| ④ 安全降级 | 未命中 | 下拉不阻断输入，但校验在服务端且更严 |
| ⑤ 状态机变更 | 未命中 | 无状态机 |
| ⑥ 验收标准或术语变更 | 未命中 | 术语「部门完整路径」「姓名（部门）」写入设计文档，不新增业务术语 |
| ⑦ 模块启用/停用 | 未命中 | 无 |

结论：**B 档**，需 CR（本文），不需要 ADR 与人工签核。

## 5. 兼容性

- 旧模板（`归属部门ID` / `负责人账号` 表头）不再可直接导入，会返回 `400` 并列出缺少的列；需重新下载模板。当前为开发期首个落地，无生产存量模板，影响面为零。
- 状态枚举文案由「处理中」改为「进行中」，与前端 `STATUS_LABELS` 对齐；旧文件若写「处理中」会返回 `INVALID_FORMAT`。
- 导出文件新增可在导入时忽略的列，回导不再报错；其余接口行为不变。
- 回滚：还原 `ExcelField`/`ReferenceDirectoryService`/`ExcelTemplateWriter`/`ExcelExportWriter`/`ExcelRowValidator`/`ExcelImportService` 与两个模块的 schema/handler，删除部门名分隔符校验；无数据回滚。

## 6. 验证

见 `docs/audit/impl-notes.md`「引用字段翻译实测」：本机实跑模板隐藏页与下拉、显示值导入、越权拒绝、重名拒绝、下拉外负责人放行、导出→导入闭环、部门名分隔符拦截、部门名扫描。

补充（2026-11-10）：错误清单页面渲染已用 CDP（`DOM.setFileInputFiles` + `Network.responseReceived`）真机触发，对话框显示「共 3 行，其中 2 行有误（2 处）」及两行错误表格，HTTP 422、`code=30201`。原样例文件 `docs/audit/samples/demo-task-import-errors.xlsx` 为旧表头（`归属部门ID`/`负责人账号`），按当前模板只会得到 400「缺少列」，已按当前模板表头重建。

「部门严、负责人松」确认为有意设计，理由与边界见 `docs/design/import-export-framework.md` §13.4；上量阈值与替代方向见 §13.2；基线断言见 `docs/baseline/assertions.md` #62。
