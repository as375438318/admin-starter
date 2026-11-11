# 导入导出框架设计（通用框架：示例任务为首个落地，岗位管理为第二接入点）

| 项目 | 内容 |
|---|---|
| 版本 | v3.6（2026-11-10，持久化改多行批量 INSERT，§4.2/§4.5 的连接占用与耗时改为实测值，§13.2 补改造后注记，并发风险登记断言 #64）；v3.5（2026-11-10，§13.2 补 2 万行实测注记：目录装载 <2%、瓶颈在逐行 insert）；v3.4（2026-11-10，补充「部门严、负责人松」为有意设计的结论与上量阈值/替代方向；错误清单页面渲染经 CDP 真机补验）；v3.3（2026-11-10，引用字段翻译：`FieldKind` + `ReferenceDirectoryService` 统一正反向翻译、隐藏 sheet 下拉、导入越权/多义校验、表头核对改为「模板列必须齐全」以打通导出→导入闭环）；v3.2 第二接入点落地；v3.1 导入错误清单三个独立数字；导入单一同步 20000 行；导出异步 + 快通道 |
| 状态 | DRAFT（无待裁决项；实现按本文推进） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | to-be 设计：字段元数据、模板、导入校验、导出三重约束、执行模型的唯一设计说明 |
| 依赖 | `AGENTS.md` §4；`CR-20261110-002`；`docs/design/state-machine.md`；`docs/specs/api-contract.md`；`docs/design/field-masking.md` |

## 1. 目标与非目标

**目标**

- 后端从**一份字段元数据**派生：导入模板、导入校验、导出表头与取值。
- 导入只新增；单一同步通道，单事务，任一行错整批回滚；单批 20000 行、单文件 50MB。
- 导出：≤2000 行走同步快通道；>2000 行转异步任务，完成后通知并可下载；临时文件独立区、按人隔离、过期清理、不进文件管理。
- 导出受「数据权限 + 字段级脱敏 + 审计日志」三重约束，按导出人权限决定可见行与可见列。

**非目标**

- 不接入用户、角色、字典等其它模块（岗位管理是已批准的**第二个**接入点，用于验证通用性；其它模块不接入）。
- 不做「存在则更新」；不做 CSV；不做导入异步；不做「跳过错误行」与「部分导入」；不做错误清单文件下载（页面内展示）。
- 不改 `biz_demo_task` 结构；不新增业务错误码；不建独立任务中心页面。

## 2. 同源字段元数据（硬要求）

### 2.1 结构

唯一事实源：`DemoTaskExcelSchema`（`server/system/.../application/excel/demo/DemoTaskExcelSchema.java`）。

```java
ExcelField<T> {
  String key;              // 内部字段标识
  FieldKind kind;          // TEXT（默认）/ ENUM / REF_DEPT / REF_USER，决定正反向翻译由谁做
  String importHeader;     // 导入表头（null = 不参与导入）
  String exportHeader;     // 导出表头（null = 不参与导出）
  boolean required;        // 导入必填
  int maxLength;           // 导入长度上限（0 = 不限）
  String formatHint;       // 补充格式说明（不重复「必填/最多 N 字/可选值」，由生成器自动拼）
  String example;          // 示例值
  List<EnumOption> enums;  // 枚举值与文案
  String uniqueHint;       // 唯一性提示
  Function<String, Object> importer;  // 文本 → 值（仅 TEXT；ENUM 由框架按 enums 归一）
  Function<T, String> exporter;       // 实体 → 文本（仅 TEXT）
  Function<T, Object> refValue;       // 实体 → 原始值（部门 ID / 用户 ID / 枚举存储值）；REF_* 与 ENUM 必填
  String maskFieldKey;     // 非空时走 MaskResolver
  MaskFormat maskFormat;   // 掩码格式
}
```

`kind` 的语义与翻译责任见 §13。业务模块只声明「字段是什么类别」「实体里哪个字段是原始值」，不写显示值拼接、不写反查表。

### 2.2 三个消费方（禁止另写字段清单）

| 消费方 | 从元数据取什么 | 落点 |
|---|---|---|
| 模板生成 | `importHeader`、`required`（表头加 `*`）、`formatHint`、`example`、`enums`、`uniqueHint`、`kind`（生成隐藏 sheet 下拉） | `ExcelTemplateWriter`（入口 `ExcelTemplateAppService`） |
| 导入校验 | `required`、`maxLength`、`enums`、`importer`、`kind`/`refValue`（引用翻译）+ 业务唯一性 | `ExcelStreamParser` + `ExcelImportService` + `ExcelRowValidator` + 业务 `ExcelImportHandler` |
| 导出 | `exportHeader`、`kind`/`refValue`、`exporter`、`maskFieldKey`/`maskFormat` | `ExcelExportWriter`（编排 `ExcelExportPlanner`） |

**禁止**：在模板、导入校验、导出任一处另写字段清单或另写必填/格式判断；新增字段只改该模块的 `ExcelSchema` 实现。显示值与存储值的翻译只有一份实现（`ReferenceDirectoryService`），业务侧只提供原始值。

### 2.3 示例任务字段规则（逐条来源）

| key | 导入表头 | 导出表头 | 必填 | 规则 | 来源 |
|---|---|---|---|---|---|
| `title` | 任务标题 | 任务标题 | 是 | 非空白，≤128 字 | `DemoTaskDtos.SaveRequest` `@NotBlank`/`@Size(max=128)`；`V3__demo_business.sql` `title VARCHAR(128) NOT NULL` |
| `status` | 任务状态 | 任务状态 | 否 | `ENUM`：导入接受 `TODO/DOING/DONE` 或「待处理/进行中/已完成」，导出统一中文；空白按 `TODO` | `DemoTaskDtos.SaveRequest` `@Pattern`；`DemoTaskAppService.normalizeStatus`；V3 默认 `TODO`；文案与前端 `STATUS_LABELS` 对齐 |
| `deptId` | 归属部门 | 归属部门 | 是 | `REF_DEPT`：导入填完整路径（如 `总部/研发部/前端组`），必须存在且在当前用户数据权限可见范围内；导出同一路径 | `DemoTaskDtos.SaveRequest` `@NotNull`；`DemoTaskAppService.validateDept`；V3 `dept_id NOT NULL`；`uk_dept_parent_name` |
| `ownerId` | 负责人 | 负责人 | 否 | `REF_USER`：导入接受「姓名（部门）」、账号，或全库唯一的姓名；重名报错；空白按当前登录人；导出统一「姓名（部门）」 | `DemoTaskAppService.create`（缺省取 `CurrentUser`）、`validateOwner`；V3 `owner_id NULL` |
| `id` | — | 任务ID | — | 只读 | V3 主键 |
| `ownerPhone` | — | 负责人手机号 | — | `MaskResolver("user.phone", PHONE)`，列随权限出现/消失 | `User.phone`；`MaskResolver`；`system:user:phone:view/mask` |
| `createdAt` | — | 创建时间 | — | `yyyy-MM-dd HH:mm:ss` | V3 `created_at` |

**唯一性**：`biz_demo_task` 只有主键，`title`/`dept_id` 无唯一索引（V3 仅普通索引），因此**不做标题唯一校验**，模板须知页写明「标题允许重复」；同一文件内重复行也不拦截（与单条新增口径一致）。不为导入发明新的业务唯一规则。

## 3. 模板生成

- `ExcelTemplateWriter` 从 schema 生成 xlsx，三份 sheet：
  1. **任务数据**：第 1 行表头（必填列 `任务标题*`），列宽自适应，表头批注写必填/格式/长度/唯一性；第 2 行起留空。枚举与引用列绑定下拉。
  2. **填写须知**：规则条目（含「引用数据页已隐藏并锁定，下拉只是建议」）+ **示例行表格**（表头与数据页一致，值来自 `example`）。
  3. **引用数据**（隐藏、锁定）：每列一组下拉源，列头写成 `字段名（请勿修改）`；数据页用区域引用绑定，不用内联列表（内联受 255 字符上限）。
- 下拉**不是校验**：`showErrorBox=false`，允许手工填写；最终以导入时刻库中数据为准，下拉缺项不得造成「填对却报错」。
- 示例行**只放须知页**：数据页出现示例行会让解析无法区分「示例」与「真实数据」，只能靠值相等猜测跳过，存在静默丢行风险。
- 文件名 `示例任务导入模板.xlsx`；响应 `Content-Disposition: attachment`。
- 模板下载与导入共用一枚权限码（示例 `demo:task:import`、岗位 `system:post:import`）；模板是导入的必要前置，拆码无授权意义。
- 模板生成只有一份实现：`ExcelTemplateAppService.generate(schema)`，业务 Controller 直接调用，不写自己的模板代码。

## 4. 执行模型（最终口径，单一通道）

| 操作 | 通道 | 用户感知 |
|---|---|---|
| 导入（≤20000 行） | **同步，单事务，整批校验 + 整批写入；任一行错整批不落库** | 上传后显示「校验中 → 写入中」，结束时返回结果或逐行错误清单 |
| 导入（>20000 行 / >50MB） | 拒绝 | 提示实际行数或体积与上限；**不自动转异步、不要求用户分批** |
| 导出 ≤2000 行 | 同步快通道，内存生成直接下载 | 直接下载 |
| 导出 >2000 行 | 异步任务 | 立即返回任务号；完成后顶栏通知 + 「我的任务」下载；临时文件 24h 过期 |

- **2000 行只是导出的内部快通道阈值**，不是面向用户的规则；导入没有分档概念。
- 导出的「5 秒快通道」用行数阈值实现（`EXPORT_FAST_PATH_MAX_ROWS=2000`），不做「先跑 5 秒再转异步」的竞态切换。
- 阈值、去重 TTL 均为代码常量，不进配置台账；改阈值属于行为变更，须改本文档并复测。

### 4.1 上限（定死）

| 项 | 上限 | 超限行为 | 理由 |
|---|---|---|---|
| 导入文件体积 | 50MB | 拒绝，提示体积与上限 | 事件流解析内存可控；再大一档要重做存储与校验 |
| 导入单批行数 | 20000 行 | 拒绝，提示行数与上限 | 单事务 undo log 与耗时在 2–5 秒可接受；再上一档必须重新设计事务边界 |
| 导出单次行数 | 100000 行 | 拒绝，提示缩小范围 | 异步通道 + `SXSSF` 可承载；再大一档需分片与断点续传 |
| 导出同步快通道 | 2000 行 | 自动转异步 | 保证请求时长稳定 |

### 4.2 事务代价（如实记录）

- 整批单事务执行期间，数据库连接被占用：改造前约 7–8 秒，改多行批量写入后约 0.8–1.5 秒（20000 行，实测见 `docs/audit/perf-2w-2026-11-10.md` §6）。示例业务可接受。
- **将来接入真实业务表时必须重新评估**：高并发下 2 万行事务会放大锁竞争与连接池占用；届时要么降上限，要么回到异步/分段方案（需另开 CR）。
- 实测 3 并发（各 2 万行）无慢查询、无行锁等待，Hikari `pending=0`；单请求仍占 1 条连接，10 并发会占满 `maximum-pool-size=10`。本轮**不实现限流**，风险登记为基线断言 #64，上线前需决策。
- 2 万行「错一个字符也要整批重传」的代价依旧成立；用户已接受，不在文档中美化。

### 4.3 事件流解析（不因 2 万行退回全量）

- **不用 `XSSFWorkbook` 全量载入**。用 `OPCPackage` + `XSSFReader` + SAX：
  1. `XSSFReader.getSheetsData()` 取目标 sheet 流；
  2. `XSSFReader.getSharedStringsTable()` 取共享字符串表；
  3. 自定义 `DefaultHandler` 只处理 `row`/`c`/`v`/`is`，按 `r` 属性还原列号；
  4. 每解析一行即转为**轻量文本行**（行号 + 表头→单元格文本），不保留 POI 的 `Row`/`Cell` 对象树；2 万行的轻量行约几 MB，用于整批校验后单事务写入。
- 表头在第一行解析并建立列映射；缺列/多列/顺序不符立即中止。
- 行数计数在 SAX 中累加，超过 20000 立即中止，不继续解析。
- 2 万行 × 多列约十万级单元格，`XSSF` 全量载入的堆占用可能达百 MB 级；SAX 只保留当前行，是硬要求而非优化项。

### 4.4 进度反馈（诚实提示，不做假阶段）

- 位置：示例任务页的「导入」对话框内，文件选择后、提交按钮上方的一条状态条。
- 文案：**单一状态**「正在校验并写入，请勿关闭页面，预计 10 秒内完成」；不显示百分比、不做阶段切换动画、不暗示当前处于哪个内部阶段。
- 原因：界面阶段与真实阶段不一致时，排查会依据错误信息走错方向；伪装进度比「处理中」更糟。
- 提交期间按钮禁用 + 加载态，直到收到响应；服务端不返回中间态，不引入 SSE/WebSocket。
- 若将来要真实进度，必须由服务端上报，另行设计。

### 4.5 重复提交（结论：5 分钟，按最坏耗时推导）

- 键：`admin:import:dedup:v1:{moduleKey}:{userId}:{sha256}`（`CacheNamespaces.IMPORT_DEDUP_PREFIX`），`setIfAbsent` 抢占，TTL **5 分钟**。
- **必须含模块标识**：否则示例任务与岗位上传同一份字节的文件会互相拦截。
- 依据：2 万行同步导入的服务端实测耗时为 0.1–1.5 秒（校验 + 单事务写入；写入本身 0.62–1.19 秒，冷 JVM 首跑最慢约 1.5 秒），叠加慢网络上传 50MB 文件与网关重试，单次完整执行的最坏耗时按 60 秒计；5 分钟是 60 秒的 5 倍余量，足以覆盖同一次提交的重试与连点，又不会让用户长时间无法重传。
- 成功后**保留**键到 TTL 到期（防止成功后短时重传造成重复数据）；失败（校验不通过/异常）**立即释放**，允许用户改完文件马上重试。
- 若进程崩溃导致键残留：最长 5 分钟内同一文件重传被拒；后果写入任务提示（「该文件刚刚已提交过，请稍后再试」）。

### 4.6 结果查看入口（三种，分工）

| 入口 | 适合 | 本轮实现 |
|---|---|---|
| 页面内联 | 停留在示例任务页 | 导入结果与错误清单直接展示；导出异步显示内联任务条（进度/取消/下载） |
| 顶栏铃铛（复用 `sys_notice`） | 稍后回来看 | 导出任务完成/失败发一条站内通知，点击回示例任务页 |
| 任务中心列表 | 查历史、下载 | 本轮用示例任务页「我的任务」抽屉（仅导出任务：状态、进度、下载、取消），不建独立页面与菜单 |

### 4.7 异步导出与临时文件隔离（硬约束）

- 任务表 **`sys_export_task`**：`PENDING → RUNNING → SUCCESS | FAILED | CANCELED`，`SUCCESS → EXPIRED`（过期清理）。非法流转见 `docs/design/state-machine.md`。
- 文件写入**独立临时区**（`java.io.tmpdir/admin-starter/exports/{userId}/{taskId}.xlsx`），按人隔离；**禁止**写 `sys_file`、禁止出现在文件管理列表与回收站。
- 过期：`expires_at = finished_at + 24h`；清理任务每小时扫描，删除文件并置 `EXPIRED`；下载时若已过期返回 404 与提示。
- 下载只能本人：按 `user_id` 校验，禁止凭 taskId 越权下载。
- 导出任务执行时重新计算数据权限与脱敏（按任务创建人），不缓存旧权限结果。

## 5. 导入流水线

| 步 | 动作 | 失败处理 |
|---|---|---|
| 1 | 校验体积（≤50MB）、扩展名 `.xlsx`、可被 `OPCPackage` 打开 | `400 INVALID_PARAMETER`，message 含上限与实际值 |
| 2 | 定位 `任务数据` sheet | 缺失 → `400` |
| 3 | SAX 解析表头，按 `importHeader` 建立列映射；模板要求的列必须全部存在（允许导出专用列存在，按列名读取） | `400`，message 列出缺少的列 |
| 4 | SAX 逐行读取；空行跳过；超过 20000 行中止 | `400`，message 含实际行数与上限 |
| 5 | 逐行元数据校验（必填/长度/枚举/引用翻译/类型） | 收集 `data.errors[]`（字段写作 `第 N 行 · 任务标题`），并累计 `totalRows`／`errorRows`／`errorCount` |
| 6 | 引用目录一次性装载（部门 `findAll()` + 用户轻量全量查询各一次，内存建双向表，禁止按行查库） | 部门/负责人不存在、多义、部门越权 → 对应行错误，见 §13 |
| 7 | 任一行有错 → 不落库，返回 `422 VALIDATION_FAILED`；`data.errors` 只返回前 200 条，其余字段返回真实数字 | 整批回滚；页面显示「共 20,000 行，其中 37 行有误（42 处），以下列出前 200 条」 |
| 8 | 全部通过 → **单事务**批量插入，`create_by` 取当前用户、`owner_id` 缺省取当前用户、`status` 缺省 `TODO` | 任一插入失败整批回滚 |
| 9 | 返回 `{ totalRows, imported }` | — |

- 错误清单只在页面内展示（位置/原因/类型），不做文件下载。
- 去重见 §4.5。

### 5.1 错误清单的三个数字（独立字段，不合并）

同一行可能有多个字段出错：该行只算 1 行出错，但算多条字段级错误。因此响应必须同时给出三个独立数字，禁止用单一 `errorCount` 表达两种含义：

| 字段 | 含义 | 计算方式 |
|---|---|---|
| `totalRows` | 本次上传的数据行数（不含表头） | SAX 解析累计 |
| `errorRows` | 存在至少一处错误的行数 | `Set<行号>` 去重计数 |
| `errorCount` | 字段级错误总数 | 每条字段错误 +1 |
| `remainingErrors` | 未显示的剩余条数 | `errorCount - errors.size()` |

- `errors` 仍最多返回 200 条；`errorCount` 与 `remainingErrors` 始终为真实值。
- 界面文案：「共 20,000 行，其中 37 行有误（42 处），以下列出前 200 条」。当 `remainingErrors > 0` 时补一句「其余 N 条未显示」。
- 成功响应同时返回 `totalRows` 与 `imported`，与错误响应共用 `totalRows` 口径。

## 6. 导出三重约束

| 约束 | 落点 | 实现要点 |
|---|---|---|
| ① 数据权限 | 业务导出 AppService 的取数方法外层包裹 `DataPermissionContext.controlled(...)`，条件复用该模块列表查询（示例：`DemoTaskQuery`；岗位：`PostQuery`） | 先计数：≤2000 同步；>2000 建任务异步执行；执行时重新包数据权限 |
| ② 字段级脱敏 | 写入前逐列判定：`MaskResolver.canView` → 明文；否则有 `mask` → `MaskFormat.mask`；都无 → 整列不输出 | 复用 `MaskFormat`，禁止另写掩码；掩码返回 null 的单元格留空 |
| ③ 审计 | `@OperLogRecord` + `OperLogContext` 补充条数/文件名/任务号/通道 | 同步与异步都记录；异步执行完成后再记一条结果日志 |

- 可见行/列按导出人权限；隐藏列整列不输出。
- 超过 100000 行拒绝并提示缩小范围。
- **快通道判定与返回体**：先 `count` 命中行数；`≤2000` 走同步并在本次响应直接返回 `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 的文件流；`>2000` 建任务并返回 `Content-Type: application/json` 的 `{mode:"ASYNC", taskId, totalRows}`。前端按响应 `Content-Type` 分支：xlsx → 直接 `Blob` 下载；json → 取 `taskId` 转任务抽屉/轮询。**禁止实现成总是异步**，否则快通道名存实亡。
- **两条写入路径的差异**：
  - 同步（≤2000 行）：`XSSFWorkbook` 内存构建后一次写出，堆占用约「行数 × 可见列 × 单元格对象」，2000 行量级为几 MB 到十几 MB，可接受。
  - 异步（>2000 行，上限 10 万）：`SXSSFWorkbook`（滑动窗口，默认 100 行驻留内存）+ 直接写独立临时文件；内存上界与总行数无关，约为窗口行数 × 列数 + 当前行对象，常驻堆为 MB 级；POI 溢写文件在 `dispose()` 时清理。
  - **禁止**对 10 万行走「内存全量构建后一次写出」。

### 6.1 前端判定写法（实现约束）

```
const res = await api.exportDemoTasks(params)   // axios responseType: 'blob'
if ((res.headers['content-type'] || '').includes('spreadsheetml')) {
  saveBlob(res.data, '示例任务.xlsx')
} else {
  const payload = JSON.parse(await res.data.text())  // Blob 里是 JSON 信封
  openTaskDrawer(payload.data.taskId)
}
```

- 后端在异步分支必须返回 JSON 信封，不得返回空文件或 202 空体。

## 7. 权限码（结论：2 枚）

| 权限码 | 覆盖 | 理由 |
|---|---|---|
| `demo:task:import` | 模板下载 + 导入 | 模板是导入前置；拆开会产生「能下模板不能导入」的无意义组合 |
| `demo:task:export` | 导出（同步/异步）与任务下载 | 批量读取通道，风险高于列表，需能与导入分开授予 |

- 不用 1 枚：导入写、导出批量读，风险与受众不同。
- 不用 3 枚：模板不泄露业务数据，单独成码无授权意义。
- 两枚只授 `SUPER_ADMIN`、`DEPT_MANAGER`，不改变既有数据范围。

## 8. 审计字段（写入 `sys_oper_log`）

| 操作 | module / action | params |
|---|---|---|
| 下载模板 | 示例业务 / 下载导入模板 | `{fileName}` |
| 导入 | 示例业务 / 导入任务 | `{fileName,fileSize,totalRows,importedRows,errorRows,errorCount}` |
| 导出（同步） | 示例业务 / 导出任务 | `{fileName,rowCount,filters,columns,mode:"SYNC"}` |
| 导出（异步提交） | 示例业务 / 提交导出任务 | `{taskId,rowCount,filters,columns,mode:"ASYNC"}` |
| 导出任务完成 | 示例业务 / 导出任务完成 | `{taskId,rowCount,fileName}` |

- 不新增 `sys_oper_log` 列；耗时用既有 `elapsed_ms`，成功失败用 `result_code/result_message`。
- 导入文件内容不落审计。

## 9. 依赖选型（Apache POI 5.4.1）

- `org.apache.poi:poi-ooxml:5.4.1`，放 `server/system`。
- 理由：模板需要样式/批注/多 sheet；读写一套库；5.4.1 含解析器安全修复；EasyExcel 在模板样式上仍回落 POI。
- 2 万行解析用 `XSSFReader` + SAX（§4.3）；导出 10 万行用 `SXSSFWorkbook`。
- 已知坑：共享字符串表仍是内存热点；`CellStyle` 按列复用；`SXSSF` 临时文件需 `dispose()`；POI 的 `log4j-api` 由 Spring Boot 自带 `log4j-to-slf4j` 路由。

## 10. 风险与处置

| 风险 | 触发条件 | 处置 |
|---|---|---|
| 模板与校验漂移 | 手改模板/另写字段清单 | 只改 schema；基线断言固化 |
| 导出泄露 | 新增敏感列未标 mask | 导出列必须显式取值；隐藏列整列不输出 |
| 解析 OOM | 退回 `XSSFWorkbook` 全量载入 | 解析强制 SAX；验收含 2 万行导入 |
| 同步导入卡顿 | 上限调大/真实业务表接入 | 先实测；2–5 秒代价写入 §4.2，接真实表前重新评估 |
| 重复导入 | 连点/重传 | 5 分钟文件指纹去重（§4.5） |
| 任务失控 | 进程崩溃 | 启动补偿：RUNNING 超 2 小时置 FAILED 并清理临时文件（仅导出） |
| 临时文件污染 | 误写 `sys_file` | 独立临时区常量只在一处；验收检查文件管理列表 |
| 取消无效 | 只在段间检查 | 导出按行分段写，取消检查点每 2000 行一次 |

## 11. 通用性：唯一实现与接入方式

- **Excel 解析、模板生成、校验流水线、错误汇总、事务控制、导出打表只有一份实现**，位于 `server/system/.../application/excel/` 与 `application/export/`（公共模块）；业务模块只提供 schema 与钩子。
- **业务模块内禁止出现自己的 Excel 解析或模板生成代码**；出现第二份即视为设计失败，须修正后交付。

### 11.1 框架层（唯一实现，业务模块可直接复用的入口）

| 类 | 职责 | 业务模块是否需要关心 |
|---|---|---|
| `ExcelSchema<T>` / `ExcelField<T>` | 字段元数据契约 | 需实现（每个模块一份） |
| `ExcelTemplateWriter` / `ExcelTemplateAppService` | 模板生成（数据页 + 填写须知，`*` 必填标记、示例行、枚举清单） | 不需写，Controller 调用 |
| `ExcelStreamParser` | `XSSFReader` + SAX 逐行解析，产出轻量文本行 | 不需写 |
| `ExcelImportService` | 元数据校验 + 业务校验 + 单事务写入 + 整批回滚 + 行数上限 | 不需写 |
| `ImportErrorCollector` | 错误汇总：`totalRows`/`errorRows`/`errorCount`/前 200 条/`remainingErrors` | 不需写，由处理器 `add(...)` 上报 |
| `ExcelImportAppService` | 通用导入入口：扩展名、50MB 体积（先于解析）、SHA-256 去重、审计补充字段 | 不需写，Controller 注入 |
| `ExcelExportWriter` | 同步 `writeSync`（内存）/ 流式 `writeStreaming`（`SXSSF`）；三态列权限 | 不需写 |
| `ExcelExportPlanner` | 行数判定（≤2000 同步 / >2000 异步 / >10 万拒绝）、建任务、提交执行 | 不需写，业务 AppService 调用 |
| `ExportTaskExecutor` + `ExportFileWriter` | 异步执行、进度、取消、临时文件、按 `moduleKey` 分发到业务写入器 | 需实现 `ExportFileWriter`（一个模块一个） |
| `ExportTaskItem` / `ExportTaskService` | 通用任务 DTO 与查询/取消/下载 | 不需写 |

### 11.2 业务模块必须提供的最小集合

1. `{Module}ExcelSchema`（字段元数据，含字段规则来源注释）；
2. `{Module}ImportHandler`（`prepare` 一次 IN 查引用数据 / `validate` 业务校验 / `toEntity` / `persist` 批量写）；
3. `{Module}ExportAppService`（构造列表查询、`count`、序列化查询快照，交 `ExcelExportPlanner`）；
4. `{Module}ExportFileWriter`（`moduleKey()` + 分页取数 + `writeStreaming`）；
5. `{Module}ExportRow` 与装配器（导出列取值、隐藏列不取值）；
6. 仓储补充：`count(query)`、`findForExport(query, offset, limit)`、`insertBatch(entities)`；
7. Controller 7 个端点 + 2 枚权限码 + 迁移脚本 + 前端按钮/对话框。

接入步骤、必须/可选钩子、代码量级、明确不支持场景见 `docs/design/import-export-onboarding.md`。

### 11.3 第二接入点实测（岗位管理，2026-11-10）

- 框架层改动 **5 处**：新增 `ExcelImportAppService`、`ExcelTemplateAppService`、`ExcelExportPlanner`、`ExportFileWriter`（执行器按 `moduleKey` 分发）、`ExportTaskItem`（替换示例专用 DTO）；同时把去重键改为含模块标识。这 5 处是**把示例硬编码上移**，不是为岗位开特例。
- 岗位侧**零框架特例**：只有 schema、导入处理器、导出查询/装配/写入器，无 Excel 解析、模板生成、错误汇总、事务控制代码。
- 判定：**抽象部分成立**——字段/校验/模板/写表这层首次即通（岗位零改动），失败点在入口层（示例实现被当成通用层），已补齐。框架改动 5 处超出 3–4 处阈值，原因已在 onboarding §5 逐条列出。

## 12. 明确不支持的场景（宁缺勿夸）

| 场景 | 当前表现 | 若要支持需改哪里 |
|---|---|---|
| 主从表导入（一行主表 + 多行明细） | 只支持单表「一行一条记录」 | 需在 `ExcelStreamParser` 增加分组归并回调，并新增主从事务写入钩子；模板也要支持子表区域 |
| 跨行校验（第 3 行与第 40 行冲突） | 流式逐行校验，看不到全表 | 需在解析完成后增加「全量内存校验」阶段，或引入可落地的中间态；与「不保留全表」原则冲突，须重新设计 |
| 导入后触发业务动作（通知/关联数据/状态流转） | 落库后直接返回，**无回调扩展位** | 需新增 `ImportPostProcessor` 钩子并明确其在事务内还是事务后执行 |
| 导入更新已有数据 | 只新增 | 需新增 upsert 模式与唯一键定义，且必须重新评估数据权限 |
| CSV / 多 sheet 数据页 | 仅 `.xlsx` 单数据页 | 需新增解析器与模板生成分支 |
| 导入异步 / 部分导入 | 不支持 | 需重新引入任务表、状态机与失败语义（本轮已明确取消） |

## 13. 引用字段翻译（REF_DEPT / REF_USER / ENUM）

### 13.1 声明方式

| 类别 | 业务声明 | 存储值 | 显示值 | 框架落点 |
|---|---|---|---|---|
| `TEXT` | `importer` / `exporter` | 文本 | 文本 | 原样 |
| `ENUM` | `enums` + `refValue` | 枚举存储值（如 `TODO`、`true`） | 中文含义（如「进行中」「启用」） | `ExcelRowValidator` 归一到存储值；`ExcelExportWriter` 按 `enums` 反查中文 |
| `REF_DEPT` | `kind=REF_DEPT` + `refValue`（部门 ID） | `deptId` | 完整路径 `总部/研发部/前端组` | `ReferenceDirectoryService` |
| `REF_USER` | `kind=REF_USER` + `refValue`（用户 ID） | `userId` | `姓名（部门完整路径）` | `ReferenceDirectoryService` |

业务模块**只**声明类别与取值器。示例任务与岗位均未出现第二份路径拼接、姓名拼接或反查代码；`parseStatus`/`statusLabel`/`parseEnabled`/`enabledLabel`/`parseLong` 等逐模块翻译逻辑全部删除。

### 13.2 目录装载（2 万行不退化）

`ReferenceDirectoryService.context(fields)` 在一次导入/导出内只装载一次：

- 部门：`DeptRepository.findAll()` 一次，按父链在内存拼完整路径（`sys_dept` 无 path 字段），建立 `id → path`、`path → id[]`。
- 用户：`UserRepository.findAllForReference()` 一次轻量查询（只取 `id/username/nickname/dept_id/is_enabled`，不加载密码），建立 `id → 显示文本`、`显示文本 → id[]`、`账号 → id`、`姓名 → id[]`。
- 目录作为 `ReferenceContext` 传入逐行校验与逐行写表，**禁止按行查库**。
- 只有 schema 真的声明了引用字段才装载；纯文表不产生额外查询。

**上量阈值（可执行，不是“以后要改”）**

| 触发条件 | 默认阈值 | 处置 |
|---|---|---|
| 未删除用户数 | ≥ 30,000（HTTP 导入并发按 3 计） | 改为两遍扫描 + `IN` |
| 单次目录估算内存 | ≥ 64MB（按 1KB/用户 × 用户数） | 同上 |
| 运行时观测 | 目录装载后堆增长 > `-Xmx` 的 10%，或 Full GC 频次明显上升 | 同上 |

估算依据：每用户目录约占 5 个 map/set 条目（`displayById`、`idsByDisplay`、`idByUsername`、`idsByNickname`、`selectableIds`）+ 显示字符串（`姓名（部门路径）`）+ 轻量实体，保守按 **1KB/用户** 计。3 并发 × 30,000 × 1KB ≈ 90MB，约为 1GB 堆的 9%，故取 30,000 为默认阈值；`-Xmx` 更小时按“≤10% 堆”等比下调。当前本机 9 个用户，远低于阈值。

**实测注记（2026-11-10，`-Xms256m -Xmx1g`，冷启动后先预热）**：2 万行合法导入时目录装载 11–32ms，占服务端总耗时不到 2%；解析 74–180ms、逐行校验 19–80ms；单次堆增长 12.0MB（逐行写入时）到 41.7MB（多行批量写入后，整批实体与 SQL 参数先在堆内构造），全程 0 次 Full GC。改造前的耗时大头是持久化（6,726–7,964ms，占 96%–98%），已改为多行批量 INSERT，降到 621–1,191ms（热态 621–776ms）。数据见 `docs/audit/perf-2w-2026-11-10.md` §1 与 §6，持久化现状另见基线断言 #63。

到阈值时的替代方向（本轮不实现，只定方向）：

1. 第一遍只收集引用列的文本集合（行本就全量驻留内存，不额外增加行内存），不建全量用户目录。
2. 部门量级小（千级以内）仍全量；用户改为一次 `username IN (...) OR nickname IN (...)`（或两条 `IN` 求并集），只对命中用户拼显示文本。
3. 对命中用户的部门及其祖先链补一次查询，路径仍在内存拼装。
4. 若 `nickname` 无索引，先 `EXPLAIN` 再决定是否加索引。
5. 不加跨请求缓存：与本轮“最终以导入时刻库中数据为准”冲突；若必须缓存，键需含组织版本号与用户更新时间，TTL 控制在数秒并接受口径漂移。

### 13.3 反向翻译（导入）规则与错误码

**一律整串精确匹配，不解析字符串**（不按 `（`/`）` 或 `/` 拆解用户输入）。

| 字段 | 匹配顺序 | 失败 | `code` | 文案 |
|---|---|---|---|---|
| 部门 | 完整路径精确匹配 | 未命中 | `REF_NOT_FOUND` | 部门不存在，请填部门完整路径（如「总部/研发部/前端组」） |
| 部门 | 同上 | 同一路径命中多个 ID | `REF_AMBIGUOUS` | 存在多个同名部门（ID 1、2），请先清理部门名称 |
| 部门 | 同上 | 命中但不在当前用户数据权限可见范围 | `REF_FORBIDDEN` | 无权指定该部门：{路径} |
| 负责人 | ① 完整显示文本 ② 账号 ③ 姓名 | 全未命中 | `REF_NOT_FOUND` | 负责人不存在，请填「姓名（部门）」或账号 |
| 负责人 | 同上 | 显示文本或姓名命中多个用户 | `REF_AMBIGUOUS` | 存在多个同名用户，请改填账号 |

- 错误计入 `errorRows` / `errorCount`，与其余字段错误同一口径；任一错误整批不落库。
- 成功解析后把 ID 写入 `values[key]`，业务 `toEntity` 直接取 `Long`，不再做存在性校验（`DemoTaskImportHandler` / `PostImportHandler` 的预加载与校验已删除）。

### 13.4 数据权限口径

| 字段 | 可指定范围（导入校验） | 下拉建议范围 | 理由 |
|---|---|---|---|
| 部门 | 当前用户数据权限可见部门：`ALL` → 全部；否则 `condition.deptIds` ∪（`SELF` 档时当前用户所属部门） | 同左 | 部门是数据归属，越权指定等于越权造数据 |
| 负责人 | **不限部门**，只校验存在性与唯一性 | 本部门及下级（停用用户不出现） | 负责人是引用而非归属；下拉只是建议，缺项不得造成「填对却报错」 |

`sys_dept` / `sys_user` 是数据权限 `EXEMPT` 表，现有查询不带范围过滤，因此可见部门集合由 `DataScopeProvider.current()` 现算，不依赖 SQL 拦截。

**结论：这是有意设计，不是漏校验——「部门严、负责人松」。**

1. **部门是数据归属**：`dept_id` 决定这一行落进哪个组织范围，越权指定等于越权造数据，所以导入必须校验 `REF_FORBIDDEN`。
2. **负责人是引用与协作对象**：把任务指派给上级、跨部门协作方是正常业务，不是越权；若也限制在“本部门及下级”，跨部门协作就得先改组织架构。
3. **对称校验会自相矛盾**：负责人下拉是本部门及下级且只是下载当时的快照；若导入按同一窄范围校验，用户填了一个真实存在、显示文本完全正确的跨部门同事，却会被拒——违反“下拉缺项不得导致填对却报错”。
4. **残留风险与边界**：指派给非当前用户可见部门的用户后，该行会在对方的 `OWNER` 归属范围下对其可见，这是“任务要派给人”的业务预期。若某个模块不允许跨部门指派，应在该模块的 `ExcelImportHandler.validate` 里加业务规则，**不改框架默认，也不要给 `REF_USER` 加部门范围参数**。
5. 后续评审不得以“口径统一”为由给负责人补部门范围校验；确需收紧按 CR 走，并同步影响面。

### 13.5 两条边界的结论

1. **部门名含分隔符**：`sys_dept` 只用 `(parent_id, name)` 唯一（`uk_dept_parent_name`）。若名称允许 `/`，`研发/测试` 与「`研发` + 子部门 `测试`」会拼出同一路径，反查必然多义。因此**只禁止部门名包含 `/` 与 `\`**，其余字符一律允许；创建/更新时拦截（`400 INVALID_PARAMETER`，文案「部门名称不能包含 / 或 \」）。
2. **不同记录生成相同显示文本**：
   - 部门：全路径相同意味着每一级同名，而每级 `(parent_id, name)` 唯一，逐级归纳可得是同一个节点，因此**在约束成立时不可能不同记录同路径**；框架仍做防御性检测，一旦出现即 `REF_AMBIGUOUS` 并指出冲突 ID，不静默取第一条。
   - 用户：「同名 + 同部门」必然生成相同显示文本，这是真实可发生的（`sys_user` 无 `nickname` 唯一约束），因此按 §13.3 报错并要求改填账号。

**上线前扫描**：按部门树逐级检查名称是否含 `/` 或 `\`。2026-11-10 实测 5 个部门（总部、研发部、研发一组、市场部、财务部）均不含分隔符，无需清理；若未来出现，先列出名单并人工确认清理方案，**不自动改名**。

### 13.6 正向翻译（导出）与闭环

- `ExcelExportWriter` 按 `kind` 取值：`REF_DEPT` → 路径；`REF_USER` → `姓名（部门）`；`ENUM` → 中文；`TEXT` → `exporter`。之后照常走 `MaskResolver`（隐藏列整列不输出）。
- 导出列与模板显示值完全一致，因此**导出的文件可直接回导**。为此表头核对从「完全相等」改为「模板要求的列必须全部存在」：导出文件多出的 `任务ID`、`负责人手机号`、`创建时间` 等列按列名忽略，不缺列即可。
- 实测闭环：导出 3 行 → 不改内容直接导入 → `{totalRows:3, imported:3}`；导出的「进行中」「总部/研发部/研发一组」「开发一号（总部/研发部/研发一组）」均被正确反解。
