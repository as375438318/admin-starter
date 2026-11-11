# 接入新业务模块：导入导出通用框架使用说明

| 项目 | 内容 |
|---|---|
| 版本 | v1.2（2026-11-10，补引用字段声明方式：`kind`/`refValue` 由框架统一翻译，业务不再拼显示文本、不再做存在性校验）；v1.1（2026-11-10，按岗位接入回填框架入口与实测度量） |
| 状态 | 已用岗位管理验证（见 §5） |
| 适用 | 已由 `docs/design/import-export-framework.md` 落地的通用 Excel 框架 |
| 目的 | 让第二个业务模块能照着本文接入，而不是靠读源码猜 |

## 0. 唯一实现约束（判失败条件）

Excel 解析、模板生成、校验流水线、错误汇总、事务控制、导出打表**只有一份实现**，位于 `server/system/.../application/excel/`。业务模块只允许提供：

- schema（字段元数据）
- 导入处理器（引用数据预加载、业务校验、实体转换、批量落库）
- 导出查询（复用该模块现有列表查询）
- 必要的权限码与迁移脚本
- 前端页面按钮与对话框

**业务模块内出现任何 Excel 解析、模板生成、错误汇总、事务控制代码，即判为设计失败**，必须改回框架层。

## 1. 接入步骤（按顺序）

以「示例任务」为参照，新模块按下列顺序改文件。

| 步 | 文件 | 写什么 | 必须/可选 | 量级 |
|---|---|---|---|---|
| 1 | `application/excel/{module}/{Module}ExportRow.java` | 导出行模型：实体原始值（部门 ID、用户 ID、枚举存储值、需脱敏的原始值）+ 纯文本列。**不拼部门路径、不拼「姓名（部门）」**，显示值由框架翻译 | 必须 | 30–60 行 |
| 2 | `application/excel/{module}/{Module}ExcelSchema.java` | 实现 `ExcelSchema<T>`：字段 key、导入/导出表头、必填、长度、`enums`、格式提示、示例、`maskFieldKey`/`maskFormat`；引用列写 `kind=REF_DEPT/REF_USER` + `refValue`（实体 → 原始值），纯文本列写 `importer`/`exporter` | 必须 | 60–120 行 |
| 3 | `application/excel/{module}/{Module}ImportHandler.java` | 实现 `ExcelImportHandler<T,C>`：`prepare`（只预加载业务唯一性数据，如已有编码）、`validate`（只写业务附加规则；部门/负责人存在性、多义、越权由框架做）、`toEntity`（直接取 `values` 里的 `Long` ID）、`persist`（批量插入） | 必须 | 40–100 行 |
| 4 | 该模块 `Repository` / `RepositoryImpl` | 导出用 `count(query)` 与分页查询 `findForExport(query, offset, limit)`；如需按账号查用户，补批量查询方法 | 必须 | 20–40 行 |
| 5 | **不需要新建**：Controller 直接注入通用 `ExcelImportAppService` | 文件体积/扩展名检查、SHA-256 去重、审计补充字段已通用；业务侧只传 `ImportHandler` | 必须（调用） | 0 行 |
| 6 | `application/excel/{module}/{Module}ExportAppService.java` | 构造该模块查询、`count`、序列化查询快照，交 `ExcelExportPlanner` 决定同步/异步 | 必须 | 40–60 行 |
| 7 | `web/controller/{Module}Controller.java` | 模板下载、导入、导出、任务列表/详情/取消/下载 7 个端点 | 必须 | 60–100 行 |
| 8 | `db/migration/Vxx__{module}_import_export.sql` | 2 枚权限码、菜单、按角色 code 授权、导出清理任务（复用第一次即可，不重复建） | 必须 | 40–80 行 |
| 9 | 前端 `api/{module}.ts` + 页面 | 模板下载、导入对话框、导出分支、我的任务抽屉 | 必须 | 150–300 行 |

**可选覆盖**：

- `ExcelSchema.templateSections()`：模板「填写须知」页的额外说明区块（如业务专属对照表）。**部门清单、枚举清单不再写在这里**，引用列与枚举列的下拉由框架生成到隐藏的「引用数据」页。
- `ExcelField` 的 `maskFieldKey`/`maskFormat`：仅当该列需要三态脱敏时填写。
- `ExcelField.enums`：枚举列；填了即自动生成中文下拉，导入接受存储值或中文，导出统一中文。
- 导出任务通知文案：默认「导出完成」，可按模块覆盖标题。

**引用字段的三条硬约束**（详见 `docs/design/import-export-framework.md` §13）：

1. `refValue` 返回实体里的原始值（`Long` 部门/用户 ID、枚举存储值），不要返回显示文本。
2. 导入 `toEntity` 从 `values` 取 `Long`，不要再查一次部门/用户；存在性与权限已由框架判定。
3. 部门显示值依赖「部门名不含 `/` 与 `\`」；新建部门由 `DeptAppService` 拦截，历史数据上线前需扫描。

## 2. 必须实现的钩子（签名）

```java
public interface ExcelImportHandler<T, C> {
    ExcelSchema<T> schema();

    /** 一次批量查引用数据，禁止逐行查库。 */
    C prepare(List<RawRow> rows);

    /** 行级业务校验；元数据校验（必填/长度/枚举/类型）由框架先做。 */
    void validate(RawRow row, Map<String, Object> values, C context, ImportErrorCollector errors);

    T toEntity(Map<String, Object> values);

    /** 在调用方事务内批量写入。 */
    void persist(List<T> entities);
}
```

`RawRow` 提供 `rowNumber()`（Excel 真实行号）与 `cells()`（表头 → 文本）。`ImportErrorCollector.add(rowNumber, fieldLabel, code, message)` 由框架统一汇总三个数字（总行数、出错行数、错误条数）。

## 3. 当前框架明确不支持的场景

| 场景 | 当前表现 | 若要支持需改哪里 |
|---|---|---|
| 主从表导入（一行主表 + 多行明细） | 只支持单表「一行一条记录」 | `ExcelStreamParser` 增加分组归并；新增主从事务写入钩子；模板支持子表区域 |
| 跨行校验（第 3 行与第 40 行冲突） | 流式逐行校验，看不到全表 | 增加全量内存校验阶段（与「不保留 POI 全表」不冲突，但要额外保留轻量行数据）；须重新评估内存 |
| 导入后触发业务动作（通知/关联数据/状态流转） | 落库后直接返回，**无回调扩展位** | 新增 `ImportPostProcessor`，并明确事务内/事务后 |
| 导入更新已有数据 | 只新增 | 新增 upsert 模式与唯一键定义，重新评估数据权限 |
| CSV / 多 sheet 数据页 | 仅 `.xlsx` 单数据页 | 新增解析器与模板分支 |
| 导入异步 / 部分导入 | 不支持 | 重新引入任务表、状态机与失败语义（本轮已取消） |
| 大字段/图片/公式单元格 | 只读取文本与数值；公式取缓存值 | 解析器增加单元格类型分支 |
| 导出列动态排序/用户自选列 | 列顺序由 schema 固定 | 需要 schema 增加列顺序参数与前端列选择 |

## 4. 第二个落地（岗位管理）的度量要求

示例任务真机验证通过后，再用岗位管理接入一次，记录：

1. 框架层（公共模块）改动几处、各改了什么、为什么非改不可；
2. 岗位侧是否出现框架特例（专用解析分支、专用模板生成等）；
3. 岗位侧新增代码量（schema / 导入落库 / 导出查询分别多少）。

若框架改动超过 3–4 处，直接写明「抽象不成立」，不硬撑。

## 5. 实测度量（岗位管理，2026-11-10）

### 5.1 框架层改动：5 处（超出 3–4 处阈值，需按事实判定）

| # | 改了什么 | 为什么非改不可 |
|---|---|---|
| 1 | 新增 `ExcelImportAppService`，删除示例专用的 `DemoTaskImportAppService` | 文件门槛、50MB/2 万行限制、SHA-256 去重、审计字段原本写在示例模块里；第二模块若照抄即出现第二份导入入口 |
| 2 | 新增 `ExcelTemplateAppService`，删除 `DemoTaskTemplateAppService` | 同上，模板生成虽只有一份 writer，但入口层被示例模块持有 |
| 3 | 新增 `ExcelExportPlanner`（count → 同步/异步 → 建任务 → 提交） | “≤2000 行同步、>2000 行异步”的决策逻辑原本写在 `DemoTaskExportAppService`，第二模块复制即口径漂移 |
| 4 | 新增 `ExportFileWriter` 接口，`ExportTaskExecutor` 按 `task.module` 分发 | 执行器原来直接 import 示例的查询类型与 writer，岗位异步导出无法运行 |
| 5 | 导出任务 DTO 上移为通用 `ExportTaskItem`，`CacheNamespaces` 去重前缀改为按模块 | 岗位接口返回示例模块的 DTO 不成立；去重键不含模块会让跨模块同文件互相拦截 |

**判定**：字段/校验/模板/写表这一层是通的（岗位零改动），失败点在**入口层**——示例实现被当成了通用层。这 5 处是把示例硬编码上移，不是为岗位开特例。框架抽象**部分成立**：业务钩子成立，入口层此前不成立，现已补齐。

### 5.2 岗位侧是否有框架特例

无。岗位侧只有 schema、导入处理器、导出查询/装配/写入器；没有 Excel 解析、模板生成、错误汇总、事务控制代码。

### 5.3 岗位侧代码量

| 类别 | 文件 | 行数 |
|---|---|---|
| schema | `PostExcelSchema` + `PostExportRow` | 204 |
| 导入落库 | `PostImportHandler` | 102 |
| 导出查询 | `PostExportAppService` + `PostExportRowAssembler` + `PostExportFileWriter` | 173 |
| 仓储补充 | `PostRepository`/`Impl` 的 `insertBatch`/`count`/`findForExport` | 约 30 |
| 迁移 | `V16__post_import_export.sql` | 34 |

前端岗位页新增导入/导出交互约 250 行；其后已抽为公共组件 `components/business/ImportDialog.vue`（228 行）与 `ExportTaskDrawer.vue`（225 行），示例任务与岗位页面各自只保留按钮与接口绑定，不再各自实现对话框与错误清单渲染。
