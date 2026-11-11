# ADR-004 Excel 依赖选型

| 项目 | 内容 |
|---|---|
| 编号 | ADR-004 |
| 版本 | v1.1（2026-11-10：随导入上限 20000 行、导出上限 100000 行更新解析与写入方式） |
| 状态 | 已实施 |
| AGENTS 状态 | `IMPLEMENTED` |
| 读者 | 决策人、架构、AI |
| 依赖 | `docs/design/import-export-framework.md` §4、§9；`docs/audit/CR-20261110-002-demo-task-import-export.md` |

## 决策

选用 `org.apache.poi:poi-ooxml:5.4.1`，依赖放在 `server/system` 模块；`server/pom.xml` 用 `poi.version` 统一版本。

- **解析**：`XSSFReader` + SAX 事件流（`ExcelStreamParser`），不实例化 `XSSFWorkbook`。
- **模板与同步小结果（≤2000 行）**：`XSSFWorkbook` 内存构建。
- **异步导出（最大 100000 行）**：`SXSSFWorkbook`（窗口 100 行）按页 2000 行流式写临时文件。

## 背景

本轮需要同时做三件事：生成带样式、批注、多 sheet 的导入模板；解析用户回填的 xlsx；生成导出文件。三件事必须由同一份字段元数据派生。导入上限 20000 行、导出上限 100000 行，`XSSFWorkbook` 全量载入的堆占用会到百 MB 级。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| Apache POI 5.4.1 | 读写一套库；样式/批注/多 sheet 完全可控；`XSSFReader`/`SXSSFWorkbook` 覆盖大文件；5.4.1 含解析器安全修复 | API 偏底层，事件流解析需自行处理共享字符串与单元格类型 | 采用 |
| EasyExcel 4.x | 读写 API 简洁、流式处理大文件 | 维护活跃度下降；模板样式仍要回落 POI，等于两套库 | 不采用 |
| 纯手写 OOXML | 无依赖 | 不可维护，样式与兼容性风险极高 | 不采用 |

## 代价与边界

- 事件流解析需自行处理 `sharedStrings`、内联字符串、数字/布尔单元格与空行；实现收敛在 `ExcelStreamParser`，业务侧不感知。
- 导出内存上界为 SXSSF 窗口 100 行 + 单页 2000 行的 DTO，不随总行数增长；同步快通道 ≤2000 行可内存构建。
- 样式对象按列复用，避免触达 `CellStyle` 数量上限。
- 日志通过 Spring Boot 自带 `log4j-to-slf4j` 路由到 logback，无需额外桥接。
- 不引入 EasyExcel，避免两套 Excel 读写栈并存。

## 回滚

删除 `server/system/pom.xml` 的 POI 依赖与 `server/pom.xml` 的版本属性即可；代码侧 `application/excel`、`application/export` 整体移除。

## 编号说明

本 ADR 曾于 2026-11-10 因 `SL-ID-002` 编号校验随 ADR-003 一并重排为 002 号，同日已回退为 ADR-004；002 号作为历史重排编号作废，不得再被引用。
