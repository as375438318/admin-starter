# CR-20261113-038 导出按勾选列（P3）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-13「导出按勾选的字段来」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-13 |
| 档位 | **B（纯新增且向后兼容）**：两个导出接口加可选参数 `fields`，契约版本 **1.1.2 → 1.2.0** |
| 设计 | `docs/design/list-sort-and-export-fields.md` §5（APPROVED，D3/D6 已确认） |
| 断言 | #105（`fields` 只能减少列、不得绕过脱敏） |

## 1. 做了什么

| 层 | 文件 | 改动 |
|---|---|---|
| 写表器 | `ExcelExportWriter` | 新增 `resolveColumns(schema, fieldKeys)`：**先**字段级脱敏可见性过滤，**再**与 `fields` 取交集，顺序以 schema 为准；`writeSync`/`writeStreaming` 各加一个带 `fieldKeys` 的重载（旧签名保留，向后兼容） |
| 计划器 | `ExcelExportPlanner` | `plan(...)` 新增带 `List<String> fieldKeys` 的重载，同步通道直接裁剪 |
| 参数解析 | `ExportFieldKeys`（新增） | `?fields=title,status` → 去重列表；合法性与否交给 Writer 统一判定（避免校验散落三处） |
| 异步快照 | `DemoTaskQuery` / `PostQuery` | 新增 `fields` 字段，随 `query_params` 一起落库；两个 `*ExportFileWriter` 从快照读回并裁剪 |
| 应用层 | `DemoTaskExportAppService` / `PostExportAppService` | 透传 `fields`，并写入 query 快照 |
| 接口 | `GET /demo-tasks/export`、`GET /posts/export` | 新增可选查询参数 `fields` |
| 前端 | `demoTask.ts` / `post.ts` 的 `*ExportQuery` | 新增 `fields?: string` |
| 前端 | 示例任务页 / 岗位页 | 导出时带上 `table.exportFields.join(',')`；**列全关时前端拦截**并提示（设计 D6） |
| 契约 | `docs/api/openapi.yaml` | 两个接口新增 `fields` 参数；版本 1.2.0 + 变更记录；重新导出 as-is |

## 2. 三条不能破的性质（都有测试）

1. **只能减少列**：`fields` 里出现 schema 未定义的 key → `400` + 10001，且错误信息带未知列名。
2. **顺序以 schema 为准**：`fields=status,title` 导出的列序是「任务标题、任务状态」，不是参数顺序 —— 同一份数据不同人导出结构一致（设计 D3）。
3. **不能绕过字段级脱敏**：裁剪发生在脱敏过滤**之后**。`ExportFieldsTest.fieldsDoNotChangeMasking` 断言同一列在"指定 fields"与"全量导出"两种情形下取值完全一致。

## 3. 影响面清单

| # | 对象 | 变化 | 验证 |
|---|---|---|---|
| 1 | `GET /api/v1/demo-tasks/export` | +`fields`（可选） | `ExportFieldsTest` 4 条 |
| 2 | `GET /api/v1/posts/export` | +`fields`（可选） | 同上（共用 Writer 裁剪逻辑）+ 手工点检 |
| 3 | 同步导出（≤2000 行） | 按 `fields` 裁剪 | `fieldsNarrowColumnsInSchemaOrder` |
| 4 | 异步导出（>2000 行） | `fields` 存进 `query_params` 快照，worker 裁剪 | 逻辑与同步共用 `resolveColumns`；`DemoTaskQuery.fields` 随快照序列化 |
| 5 | 旧数据/旧请求 | **行为不变**（缺省 = 全部可导出列） | `withoutFieldsExportsAllColumns` |
| 6 | 表 / 权限码 / 错误码 | 无新增（未知列名复用 `10001 INVALID_PARAMETER`） | — |
| 7 | 契约 | 1.1.2 → **1.2.0** | 契约测试 + 重新导出 |

## 4. 契约测试为何仍然全绿

新增的是**可选**查询参数，契约把 `fields` 声明为 `required: false`，既有 30 条契约测试的请求都没带它，因此校验行为不变；`ExportFieldsTest` 是行为测试，不属于契约校验。

## 5. 验证

| 项 | 结果 |
|---|---|
| 后端 | `ExportFieldsTest` 4/4；全量 `mvn -pl web -am verify` 见交付报告 |
| 前端 | 构建通过（含类型检查）、单测 110 |
| 手工 | 示例任务页勾掉几列 → 导出文件列数与勾选一致 |