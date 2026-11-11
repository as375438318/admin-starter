# 接口契约与错误码规范

| 项目 | 内容 |
|---|---|
| 版本 | **v1.7**（2026-11-08） |
| 状态 | AI 可执行规则集；规则编号 `CT-*`；契约定义方；每章末尾附自检清单 |
| 适用范围 | HTTP/JSON 接口通用契约，不绑定具体业务 |
| 角色 | **定义方**：响应结构、HTTP 语义、业务错误码、分页、数据格式、幂等、版本、OpenAPI |
| 被引用方 | 《前端编码规范》《后端编码规范》《前端UI设计规范》均引用本文件编号 |
| 最终读者 | AI 编码助手；人类仅用于契约评审与人工确认项 |

**变更记录**

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.7 | 2026-11-08 | `CT-HTTP-003` 增加 `413`（体积超限），并新增「413 / 422 唯一判定表」；`CT-ERR-011` 基础码增加 `PAYLOAD_TOO_LARGE`＝`10002`／`413`（基础码 9→10）；新增 `CT-ERR-012`：`413` 必须为标准信封且 `data.maxSize` 为当前上限字节数（正整数），`message` 必须含可读上限；§16 索引与总数同步（94→95） |
| v1.6 | 2026-11-08 | `CT-ERR-006` 明确顶层业务码与 `data.errors[].code` 分属不同命名空间；字段级 `code` 固定为 `REQUIRED`／`INVALID_FORMAT`／`TOO_LONG`／`VALIDATION_FAILED` 四值枚举（`VALIDATION_FAILED` 仅作字段兜底），并明确空白串归 `REQUIRED`；`CT-FMT-009` 同步引用三码，缺失/格式/超长均按 `422`，不得返回 `401` |
| v1.5 | 2026-11-08 | 新增 `CT-HTTP-009`：请求不可解析（Content-Type 不受支持、JSON 语法错误、请求体为空、畸形 multipart、缺少必需 part）统一 `400`＋`INVALID_PARAMETER`（`10001`），不引入 `415`；`CT-HTTP-003` 明确 400（不可解析）与 422（可解析但值不合规）的边界；`CT-ERR-011` 基础码增加 `INVALID_PARAMETER`；新增 `CT-FMT-009` 与登录字段长度上限表（`username` ≤ 64、`password` ≤ 128），超长按 `422`＋`data.errors`，不得返回 `401` |
| v1.4 | 2026-11-08 | `CT-ERR-010` 与 `CT-ERR-011` 表格行与自检清单恢复编号升序；`CT-IDEM-002` 指定有效期声明位置为 OpenAPI `x-idempotencyTtl`；`CT-IDEM-003` 重放标记改为响应头 `Idempotent-Replay: true`，与 OpenAPI `components/headers` 对齐 |
| v1.3 | 2026-11-08 | 成功响应统一带信封（删除也 200，禁止 204）；`data` 必存在（无值为 `null`）；`X-Trace-Id` 头；校验错误统一 `CT-ERR-006` 结构、状态码 `422`；新增删除幂等与 403/404 判定表；新增 `CT-AUTH` 令牌协议载体 4 条；`CT-OAS` 补 `x-status` 与错误响应头；优先级与双定义处置改为引用 `AGENTS.md` §1；规则总数按索引求和校正为 92 条 |
| v1.2 | 2026-11-07 | §15.1 唯一事实源分工表迁出至 `docs/specs/INDEX-facts.md`（由《项目启动与变更流程》§3.2 维护初始内容），本文只保留引用；避免同一事实两处登记 |
| v1.1 | 2026-11-07 | 全量改为 AI 可执行规则集：`CT-*` 编号、MUST/SHOULD/MAY、STATIC/PATTERN/CONTEXT、通过/失败条件、正反例；每章自检清单；新增自检流程、人工确认、评审提示词、索引；明确唯一事实源边界 |
| v1.0 | 2026-11-07 | 初稿：统一响应、状态码、错误码、分页、数据格式、幂等、版本、OpenAPI |

---

## 0. 文档定位与使用约定（CT-DOC）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-DOC-001` | MUST | CONTEXT | 本文件是响应结构、错误码、分页、数据格式、幂等、版本的唯一事实源 | 如果后端或前端另立一套同名定义，则判为不通过 | 前后端都引用 `CT-RESP-001` | 前端自定义 `Result`，后端另写一套 | 双定义必然漂移 |
| `CT-DOC-002` | MUST | CONTEXT | 冲突优先级与“同一事实双定义”处置只按 `AGENTS.md` §1 执行，本文件不复述 | 如果本文件或项目约定另排一套优先级，则判为不通过 | 本文件写“见 `AGENTS.md` §1” | 项目内改分页字段名 | 定义方被覆盖后契约失效 |
| `CT-DOC-003` | MUST | STATIC | 规范中不出现具体业务码值、业务字段或业务状态名词 | 如果出现业务专属命名或固定业务码，则判为不通过 | `RESOURCE_NOT_FOUND` 示例 | `ORDER_1001`、`业务状态X` | 契约必须通用 |
| `CT-DOC-004` | MUST | STATIC | 新增/修改规则先改本文件，再改实现与消费方 | 如果代码先上线、契约后补，则判为不通过 | 契约 PR 先行 | 实现合并后补文档 | 契约是多方联调前提 |

### 本章自检清单

- [ ] `CT-DOC-001` 本文件未出现第二套定义（MUST, CONTEXT）
- [ ] `CT-DOC-002` 冲突按固定优先级（MUST, CONTEXT）
- [ ] `CT-DOC-003` 无业务残留（MUST, STATIC）
- [ ] `CT-DOC-004` 契约先行（MUST, STATIC）

---

## 1. 统一响应与成功判定（CT-RESP）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-RESP-001` | MUST | STATIC | 所有 JSON 接口返回统一信封：`code`、`message`、`data`、`traceId`；`data` 必须存在，无数据时为 `null` | 如果出现 `success/result/rows` 等第二套结构或缺 `data` 键，则判为不通过 | `{"code":0,"message":"ok","data":null,"traceId":"..."}` | `{"success":true}` 或省略 `data` | 统一结构让所有消费方只写一套解析 |
| `CT-RESP-002` | MUST | STATIC | 成功业务码固定为 `0`；非 `0` 一律视为失败 | 如果成功码用 `200`/`1`/`true` 混用，则判为不通过 | `code: 0` | `code: 200` 表示成功 | 成功判定必须唯一 |
| `CT-RESP-003` | MUST | STATIC | `data` 类型按接口定义；无数据时用 `null`，不返回空字符串或 `{}` 冒充 | 如果无数据返回 `""` 或 `[]` 掩盖语义，则判为不通过 | `data: null` | `data: ""` 表示无对象 | 类型稳定才能生成正确类型 |
| `CT-RESP-004` | MUST | STATIC | `message` 为面向调用方的简短说明，不含堆栈、SQL、类名、内部地址 | 如果 `message` 含内部细节，则判为不通过 | `"资源不存在"` | `"java.sql.SQLException..."` | 响应会直达客户端与日志采集 |
| `CT-RESP-005` | MUST | STATIC | `traceId` 每次请求返回，失败时必填 | 如果失败响应无 `traceId`，则判为不通过 | 失败响应带 `traceId` | 只有成功响应带 | 无 traceId 无法定位线上问题 |
| `CT-RESP-006` | MUST | STATIC | 文件下载/上传等二进制接口不受 JSON 信封约束，但错误仍返回 JSON 信封 | 如果下载失败返回 HTML 错误页或裸文本，则判为不通过 | 二进制成功 + JSON 错误 | 失败返回 500 HTML | 客户端需要统一错误处理 |
| `CT-RESP-007` | MUST | STATIC | 响应字段命名统一 `lowerCamelCase`，不混用下划线或大驼峰 | 如果同一接口出现 `user_name` 与 `userName`，则判为不通过 | `userName` | `user_name` | 命名混用会破坏类型生成 |
| `CT-RESP-008` | SHOULD | STATIC | 列表数据放在 `data.list`，不把数组直接作为 `data` 顶层 | 如果 `data` 直接是数组，则判为不通过 | `data: { list: [...] }` | `data: [...]` | 顶层对象便于扩展分页与元信息 |
| `CT-RESP-009` | MUST | STATIC | 所有成功响应都带响应体与信封，**不存在无响应体的成功响应**（禁止 `204`、禁止空 body） | 如果成功响应返回 `204` 或空 body，则判为不通过 | `200` + 信封 | `204 No Content` | 消费方无法区分“成功无数据”与“响应缺失” |

### 本章自检清单

- [ ] `CT-RESP-001` 只有一套统一信封（MUST, STATIC）
- [ ] `CT-RESP-002` 成功码为 `0`（MUST, STATIC）
- [ ] `CT-RESP-003` 无数据用 `null`（MUST, STATIC）
- [ ] `CT-RESP-004` `message` 无内部细节（MUST, STATIC）
- [ ] `CT-RESP-005` 失败必带 `traceId`（MUST, STATIC）
- [ ] `CT-RESP-006` 二进制接口错误仍为 JSON（MUST, STATIC）
- [ ] `CT-RESP-007` 字段 lowerCamelCase（MUST, STATIC）
- [ ] `CT-RESP-008` 列表放 `data.list`（SHOULD, STATIC）
- [ ] `CT-RESP-009` 无 204、无空 body（MUST, STATIC）

---

## 2. HTTP 状态与语义（CT-HTTP）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-HTTP-001` | MUST | STATIC | 业务失败使用与语义匹配的 4xx/5xx，不用 `200` 包装失败 | 如果失败响应 HTTP 为 `200`，则判为不通过 | 参数错误 `400` | 失败也 `200` + `code!=0` | 网关、监控与重试依赖状态码 |
| `CT-HTTP-002` | MUST | STATIC | 创建成功 `201`、删除成功 `200`（均带信封，删除不使用 `204`）、查询成功 `200` | 如果删除返回 `204` 或无 body，则判为不通过 | `200` + `data:null` | `204 No Content` | 状态码表达语义，信封保证可解析 |
| `CT-HTTP-003` | MUST | STATIC | 请求不可解析 `400`；未认证 `401`；缺功能权限 `403`；不存在或越权访问他人资源 `404`；状态冲突 `409`；文件或资源体积超过当前业务上限 `413`；请求可解析但字段值校验失败 `422`；限流 `429` | 如果错误语义与状态码错配，则判为不通过 | 体积超限 `413`；校验失败 `422` | 未登录返回 `403` | 客户端按状态码做统一处理 |
| `CT-HTTP-004` | MUST | STATIC | 服务端错误 `500`；上游不可用 `502/503/504`，不把可恢复错误报成 `500` | 如果超时统一返回 `500`，则判为不通过 | 上游超时 `504` | 所有异常都 `500` | 可恢复性决定重试策略 |
| `CT-HTTP-005` | MUST | STATIC | 不使用 `3xx` 做业务跳转；重定向只用于协议层 | 如果业务返回 `302` 让客户端跳转，则判为不通过 | 业务结果用 4xx/5xx | `302` 表示“未登录” | API 客户端不一定会跟随跳转 |
| `CT-HTTP-006` | MUST | STATIC | 同一业务码只对应一个 HTTP 状态，映射表唯一 | 如果同一码在不同接口返回不同状态，则判为不通过 | 映射表固定 | 同一码此处 `400` 彼处 `409` | 不一致会让客户端分支爆炸 |
| `CT-HTTP-007` | MUST | STATIC | 删除幂等：首次删除成功 `200`；同一 ID 重复删除也 `200`（`data:null`）；ID 从未存在返回 `404`。区别依据是服务端是否留存删除记录（软删标记或墓碑） | 如果重复删除返回 `404` 或 `409`，则判为不通过 | 首次 `200`、重复 `200`、从未存在 `404` | 重复删除报 `409 CONFLICT` | 客户端重试与网络抖动必须可安全重放 |
| `CT-HTTP-008` | MUST | STATIC | 越权访问他人资源统一 `404`（不暴露资源存在）；缺功能权限统一 `403`。判定表见下 | 如果越权访问返回 `403` 或返回数据体，则判为不通过 | 访问他人订单 `404` | 访问他人订单 `403 无权访问` | 403 会泄露“资源存在”这一信息 |
| `CT-HTTP-009` | MUST | STATIC | 请求不可解析统一 `400`＋`INVALID_PARAMETER`（`10001`）：Content-Type 不受支持（如按 `application/x-www-form-urlencoded` 调用 JSON 接口）、JSON 语法错误、必需请求体为空（未传任何字节）、畸形 multipart、缺少必需 part；不得落到 `500` | 如果上述情况返回 `500`、`415` 或其它状态码，则判为不通过 | 表单编码调 JSON 接口 → `400 INVALID_PARAMETER` | `500` 或 `415 Unsupported Media Type` | 统一 `400` 减少前端分支；`415` 语义更精确，但收益不足以抵消多状态码带来的复杂度 |

**403 / 404 唯一判定表**

| 场景 | 状态码 | 业务码 |
|---|---|---|
| 未登录或令牌失效 | `401` | `UNAUTHORIZED` |
| 已登录但角色缺少功能权限（接口级） | `403` | `FORBIDDEN` |
| 有功能权限但访问不属于自己数据范围的资源 | `404` | `RESOURCE_NOT_FOUND`（与真不存在同一码） |
| 资源确实不存在 | `404` | `RESOURCE_NOT_FOUND` |

**413 / 422 唯一判定表**

判据：**按“字节体积”超限走 `413`，按“字段内容”不合规走 `422`**。体积指请求体或文件的字节数大小（如上传文件、导入包），与字段内容的格式/长度语义无关；体积与字段两者同时命中时先判体积（请求在读取阶段即被拒绝，字段尚未进入校验）。

| 场景 | 状态码 | 业务码 |
|---|---|---|
| 上传文件字节数超过**当前业务上限**（上限取自配置 `sys.upload.max-size`） | `413` | `PAYLOAD_TOO_LARGE`（`10002`），响应结构见 `CT-ERR-012` |
| 上传文件超过容器 `multipart` 天花板（当前 200MB，启动期固化，改库不可突破） | `413`（由容器直接拒绝，不保证业务信封） | 无业务码（容器层拒绝） |
| 字段值格式不符、必填缺失、或单值长度超过 `maxLength`（整体请求体未超体积上限） | `422` | `VALIDATION_FAILED`（`30201`）＋`data.errors`（见 `CT-ERR-006`） |

### 本章自检清单

- [ ] `CT-HTTP-001` 失败不用 `200`（MUST, STATIC）
- [ ] `CT-HTTP-002` 创建/删除状态码正确（MUST, STATIC）
- [ ] `CT-HTTP-003` 4xx 语义匹配（MUST, STATIC）
- [ ] `CT-HTTP-004` 5xx 区分可恢复性（MUST, STATIC）
- [ ] `CT-HTTP-005` 无业务 3xx（MUST, STATIC）
- [ ] `CT-HTTP-006` 码到状态映射唯一（MUST, STATIC）
- [ ] `CT-HTTP-007` 删除幂等三态（MUST, STATIC）
- [ ] `CT-HTTP-008` 越权 404／缺权限 403（MUST, STATIC）
- [ ] `CT-HTTP-009` 不可解析请求统一 400＋`INVALID_PARAMETER`（MUST, STATIC）

---

## 3. 业务错误码（CT-ERR）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-ERR-001` | MUST | STATIC | 业务码为整数，按段分配：`0` 成功；`10xxx` 通用；`20xxx` 认证与权限；`30xxx` 校验；`40xxx` 业务规则；`50xxx` 系统与基础设施 | 如果码值落在未分配段或跨段混用，则判为不通过 | `40001` 业务规则 | `1` 表示各种失败 | 分段让归属一目了然 |
| `CT-ERR-002` | MUST | STATIC | 每个业务码同时登记符号名（`UPPER_SNAKE`）、默认文案、HTTP 状态 | 如果只有数字没有符号名或文案，则判为不通过 | `RESOURCE_NOT_FOUND` + 文案 + `404` | 仅登记 `40001` | 符号名供前后端引用，文案供兜底 |
| `CT-ERR-003` | MUST | STATIC | 错误码清单集中维护，前后端从同一生成物或同一文件读取 | 如果前后端各自手抄一份码表，则判为不通过 | 单一 `errors.yaml` | 两份 Excel 各维护 | 双份码表必然不同步 |
| `CT-ERR-004` | MUST | STATIC | 业务码全局唯一，一经分配不复用、不改语义；废弃码保留并标 `DEPRECATED` | 如果删除旧码后把同值分配给新语义，则判为不通过 | 旧码标废弃并保留 | `40001` 改指另一含义 | 复用会让旧客户端误判 |
| `CT-ERR-005` | MUST | STATIC | 错误码语义粒度是“可执行的下一步”，不是“每个 if 一个码” | 如果同一类失败拆出几十个无法区分的码，则判为不通过 | `VALIDATION_FAILED` + 字段明细 | 每个字段一个码 | 过细的码无法维护 |
| `CT-ERR-006` | MUST | STATIC | 校验失败的**顶层**业务码统一 `VALIDATION_FAILED`（`30201`）／`422`；字段明细放 `data.errors`，每项含 `field`、`code`、`message`，`code` 只能取 `REQUIRED`／`INVALID_FORMAT`／`TOO_LONG`／`VALIDATION_FAILED`；空白串归 `REQUIRED`，不得归 `INVALID_FORMAT`；整个请求体不可解析按 `CT-HTTP-009` 走 `400`，不适用本枚举 | 如果字段错误只放在 `message` 字符串里、字段 `code` 自造，或把缺失/空白串报成 `INVALID_FORMAT`，则判为不通过 | `data.errors: [{field:"username", code:"REQUIRED", message:"不能为空"}]` | `message: "name 太长, age 非法"` | 表单需要按字段与原因分支的结构化错误；顶层码与字段码分属不同命名空间 |
| `CT-ERR-007` | MUST | STATIC | 未知/未登记错误统一映射为系统类码，不透传原始异常类型 | 如果响应出现 `NullPointerException` 等类名，则判为不通过 | `SYSTEM_ERROR` + `traceId` | `code: -1, message: NPE` | 内部类型不应成为契约 |
| `CT-ERR-008` | MUST | STATIC | 错误码文案默认由服务端给出，客户端只能覆盖展示文案，不能改变语义 | 如果前端把 `CONFLICT` 当成功处理，则判为不通过 | 前端只改文案 | 前端按码改流程 | 语义必须在契约层统一 |
| `CT-ERR-009` | MUST | STATIC | 错误码不得包含敏感信息（账号是否存在、内部 ID 规则）；越权与不存在统一按 `CT-HTTP-008` 返回同一码 | 如果通过不同码暴露资源是否存在，则判为不通过 | 统一 `RESOURCE_NOT_FOUND` | `USER_EXISTS` 与 `USER_NOT_EXISTS` 区分探测 | 错误码也是信息泄露面 |
| `CT-ERR-010` | SHOULD | STATIC | 错误码清单有版本号与变更记录，新增码在评审中说明触发场景 | 如果新增码无场景说明，则判为不通过 | 变更记录含场景 | 随手加码 | 无场景的码通常会被误用 |
| `CT-ERR-011` | MUST | STATIC | 基础码固定不可改：`INVALID_PARAMETER`＝`10001`／400、`PAYLOAD_TOO_LARGE`＝`10002`／413、`UNAUTHORIZED`＝`20101`／401、`TOKEN_EXPIRED`＝`20102`／401、`FORBIDDEN`＝`20301`／403、`RESOURCE_NOT_FOUND`＝`20401`／404、`RATE_LIMITED`＝`20501`／429、`VALIDATION_FAILED`＝`30201`／422、`STATE_CONFLICT`＝`40901`／409、`SYSTEM_ERROR`＝`50001`／500；`413` 的响应体结构见 `CT-ERR-012`；项目只能在对应段扩展 | 如果项目改写基础码值或把 `FORBIDDEN` 改指其它状态，则判为不通过 | `FORBIDDEN`＝`20301`／403 | 把无权限返回 `10403` | 基础码是前后端、测试与文档共同依赖的常量 |
| `CT-ERR-012` | MUST | STATIC | **应用层判定的**体积超限 `413`（未触及容器天花板）必须是标准信封（`CT-RESP-001`）：`data` 为对象且必须含 `maxSize`，`maxSize` 为**当前生效上限的字节数**（正整数，JSON number，不得为字符串）；`message` 必须写出该上限的可读值（如 `1MB`），禁止只写“文件太大”“上传失败”；`data.maxSize` 禁止缺省、为 `null` 或为 `0`。触及容器 `multipart` 天花板（200MB）时由容器直接拒绝，按 `CT-HTTP-003` 的 413 / 422 判定表处理，不适用本条 | 如果 413 缺 `data.maxSize`、`maxSize` 非正整数、`message` 不含可读上限，或返回非信封结构，则判为不通过 | `{"code":10002,"message":"文件大小不能超过 1MB","data":{"maxSize":1048576}}` | `{"code":10002,"message":"文件太大","data":null}` | 前端要按真实上限做预校验与提示；缺上限只能写死文案，改配置即失效 |

**字段级错误码枚举（`data.errors[].code`，字符串）**

| 字段错误 `code` | 语义 | 触发条件 | HTTP |
|---|---|---|---|
| `REQUIRED` | 必填项未提供 | 字段缺失、为 `null`、或为空白字符串（`@NotNull` / `@NotBlank` 类） | `422` |
| `INVALID_FORMAT` | 值已提供但格式不符 | 提供了值但不满足格式约束：`@Email`、`@Pattern`（如账号字符集）、日期/枚举之外的格式类校验 | `422` |
| `TOO_LONG` | 长度超限 | 值长度超过 `CT-FMT-009` 或 OpenAPI `maxLength` 声明的上限 | `422` |
| `VALIDATION_FAILED` | 字段级兜底 | 无法归入 `REQUIRED`／`INVALID_FORMAT`／`TOO_LONG` 的字段级校验错误 | `422` |

> **命名空间**：顶层 `code` 是整数业务码（校验失败固定 `30201`＋符号名 `VALIDATION_FAILED`）；`data.errors[].code` 是上表的字符串枚举。两者同名但不同层，客户端按所在层级取值，不得互相替代。
> **不可互换**：字段缺失、`null`、空白串一律 `REQUIRED`；只有“提供了值但格式不符”才用 `INVALID_FORMAT`。

### 本章自检清单

- [ ] `CT-ERR-001` 码值在分配段内（MUST, STATIC）
- [ ] `CT-ERR-002` 符号名/文案/HTTP 状态齐全（MUST, STATIC）
- [ ] `CT-ERR-003` 码表单一来源（MUST, STATIC）
- [ ] `CT-ERR-004` 码唯一且未复用（MUST, STATIC）
- [ ] `CT-ERR-005` 粒度可执行（MUST, STATIC）
- [ ] `CT-ERR-006` 字段错误结构化且 `code` 取自四值枚举（MUST, STATIC）
- [ ] `CT-ERR-007` 未知错误不透传异常类型（MUST, STATIC）
- [ ] `CT-ERR-008` 客户端不改变语义（MUST, STATIC）
- [ ] `CT-ERR-009` 错误码不泄露敏感信息（MUST, STATIC）
- [ ] `CT-ERR-010` 变更记录完整（SHOULD, STATIC）
- [ ] `CT-ERR-011` 基础码值未被改写（MUST, STATIC）
- [ ] `CT-ERR-012` 413 信封含正整数 `data.maxSize` 且 `message` 含可读上限（MUST, STATIC）

---

## 4. 分页、排序与筛选（CT-PAGE）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-PAGE-001` | MUST | STATIC | 分页请求字段固定为 `page`（从 1 开始）与 `pageSize`；响应字段固定为 `list`、`total`、`page`、`pageSize` | 如果出现 `pageNum/limit/rows/totalCount` 等别名，则判为不通过 | `?page=1&pageSize=20` | `?pageNum=1&limit=20` | 字段名是前后端最容易冲突的地方 |
| `CT-PAGE-002` | MUST | STATIC | 默认值固定：`page=1`，`pageSize=20`；**硬上限 100**，单接口可在 OpenAPI 声明更低上限，不得高于 100 | 如果不同接口默认值不一致，或允许 `pageSize>100`，则判为不通过 | 统一默认 20、上限 100 | 有的 10 有的 50；允许 100000 | 默认值与上限不一致会让前端翻页与性能不可控 |
| `CT-PAGE-003` | MUST | STATIC | `pageSize` 超上限返回 `400`，不静默截断 | 如果超上限被悄悄改成 20，则判为不通过 | 明确报错 | 静默截断 | 静默截断会让调用方以为拿到了全部 |
| `CT-PAGE-004` | MUST | STATIC | 排序参数固定 `sort`（字段）与 `order`（`asc/desc`），仅允许白名单字段 | 如果使用 `sortBy`、`direction` 或允许任意字段，则判为不通过 | `?sort=createdAt&order=desc` | `?orderBy=created_at` | 任意字段排序会带来注入与性能问题 |
| `CT-PAGE-005` | MUST | STATIC | 筛选参数平铺在 query，布尔用 `true/false`，多值用重复参数或逗号分隔并在 OpenAPI 标注 | 如果同一筛选在一处用逗号、一处用重复参数，则判为不通过 | `?status=ACTIVE&tag=a&tag=b` | 两套写法混用 | 解析规则必须唯一 |
| `CT-PAGE-006` | SHOULD | STATIC | 深分页（超过约 1 万条）改用游标分页，并在契约中单独定义 | 如果深分页仍用页码且性能不可接受，则判为不通过 | 游标 `cursor/nextCursor` | 第 500 页仍全表扫描 | 页码分页在深页时成本陡增 |

### 本章自检清单

- [ ] `CT-PAGE-001` 分页字段固定（MUST, STATIC）
- [ ] `CT-PAGE-002` 默认值统一（MUST, STATIC）
- [ ] `CT-PAGE-003` 超上限报错不截断（MUST, STATIC）
- [ ] `CT-PAGE-004` 排序参数与白名单（MUST, STATIC）
- [ ] `CT-PAGE-005` 筛选写法唯一（MUST, STATIC）
- [ ] `CT-PAGE-006` 深分页有替代（SHOULD, STATIC）

---

## 5. 数据格式（CT-FMT）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-FMT-001` | MUST | STATIC | 时间点使用 ISO 8601 UTC（带 `Z` 或明确偏移），如 `2026-11-07T03:00:00Z`；纯日期用 `yyyy-MM-dd` | 如果出现 `2026/11/07 11:00`、`Nov 7, 2026` 或本地时区裸时间，则判为不通过 | `2026-11-07T03:00:00Z` | `2026-11-07 11:00:00` | 时区歧义会造成跨端错位 |
| `CT-FMT-002` | MUST | STATIC | 时间戳字段以 `At` 结尾（如 `createdAt`），布尔以 `is/has/can` 开头 | 如果时间字段命名为 `time/date/created`，则判为不通过 | `createdAt` | `createTime` | 命名可推断类型 |
| `CT-FMT-003` | MUST | STATIC | 金额不用 JSON 浮点；使用最小货币单位整数或十进制字符串，并在 OpenAPI 标注单位 | 如果金额是 `12.34` 的 JSON number，则判为不通过 | `"amount": "12.34"` 或 `1234` + 单位 | `"amount": 12.34` | 浮点会丢精度 |
| `CT-FMT-004` | MUST | STATIC | 超过 `2^53` 的整数 ID 用字符串传输 | 如果雪花 ID 用 JSON number，则判为不通过 | `"id": "9007199254740993"` | `"id": 9007199254740993` | JavaScript 数字会精度丢失 |
| `CT-FMT-005` | MUST | STATIC | 枚举以稳定的字符串常量传输，不使用序号 | 如果状态用 `1/2/3`，则判为不通过 | `"status": "ACTIVE"` | `"status": 2` | 序号会随枚举顺序变化 |
| `CT-FMT-006` | MUST | STATIC | 布尔只用 `true/false`，不用 `0/1`、`"Y"/"N"` | 如果布尔字段出现 `1` 或 `"yes"`，则判为不通过 | `"enabled": true` | `"enabled": 1` | 类型稳定才能正确生成 |
| `CT-FMT-007` | MUST | STATIC | 可选字段“未提供”用缺省，“显式清空”用 `null`，两者语义不同并在 OpenAPI 标注 | 如果所有可选字段都传 `null` 导致无法区分，则判为不通过 | 不传表示不改，`null` 表示清空 | 全传 `null` | 更新语义需要区分 |
| `CT-FMT-008` | MUST | STATIC | 字符串统一 UTF-8，不做隐式 trim 或大小写转换；长度限制在 OpenAPI 声明；登录与用户字段的契约层上限见 `CT-FMT-009` | 如果服务端静默 trim 导致回显不一致，则判为不通过 | 显式校验并报错 | 静默改写输入 | 隐式改写会让客户端无法复现 |
| `CT-FMT-009` | MUST | STATIC | 登录与用户字段长度上限：`username` ≤ 64 字符、`password` ≤ 128 字符；超长按 `CT-ERR-006` 返回 `422`＋`data.errors`（`code=TOO_LONG`，`message` 说明上限）；缺失、`null` 或空白串 `code=REQUIRED`，格式不符 `code=INVALID_FORMAT`；均不得返回 `401`；其余用户字段（昵称、手机号、邮箱、备注）在项目 `docs/api/openapi.yaml` 按 `CT-FMT-008` 声明 | 如果超长、缺失或格式错误返回 `401`、`500`，或 `data.errors` 缺字段名，则判为不通过 | `password` 129 字符 → `422`＋`data.errors[{field:"password", code:"TOO_LONG", message:"最多 128 字符"}]` | `password` 为空返回 `401 用户名或密码错误` | 字段错误伪装成认证失败会让用户无法定位 |

**登录与用户字段长度上限（契约层最小集）**

| 字段 | 上限 | 超限响应 |
|---|---|---|
| `username` | 64 字符 | `422`＋`VALIDATION_FAILED`（`30201`）＋`data.errors[{field:"username", code:"TOO_LONG", message:"最多 64 字符"}]` |
| `password` | 128 字符 | `422`＋`VALIDATION_FAILED`（`30201`）＋`data.errors[{field:"password", code:"TOO_LONG", message:"最多 128 字符"}]` |

> 昵称、手机号、邮箱、备注在契约层无既有上限；由项目 `docs/api/openapi.yaml` 声明 `maxLength`，并与 `docs/design/data-model.md` 的列长保持一致，不得出现第二套长度约定。当前模板仅有 `remark varchar(200)`，未发现冲突。
> 缺失、`null`、空白串按 `REQUIRED`；字符集等格式不符按 `INVALID_FORMAT`；超长按 `TOO_LONG`；三者顶层均 `VALIDATION_FAILED`（`30201`）／`422`。整个请求体不可解析按 `CT-HTTP-009` 走 `400`。

### 本章自检清单

- [ ] `CT-FMT-001` 时间格式统一（MUST, STATIC）
- [ ] `CT-FMT-002` 时间/布尔命名可推断（MUST, STATIC）
- [ ] `CT-FMT-003` 金额不用浮点（MUST, STATIC）
- [ ] `CT-FMT-004` 大整数 ID 用字符串（MUST, STATIC）
- [ ] `CT-FMT-005` 枚举用字符串（MUST, STATIC）
- [ ] `CT-FMT-006` 布尔用真布尔（MUST, STATIC）
- [ ] `CT-FMT-007` 缺省与 `null` 语义区分（MUST, STATIC）
- [ ] `CT-FMT-008` 字符串不隐式改写（MUST, STATIC）
- [ ] `CT-FMT-009` 登录字段超长返回 422＋字段明细（MUST, STATIC）

---

## 6. 幂等、重试与超时（CT-IDEM）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-IDEM-001` | MUST | STATIC | 写操作幂等键使用请求头 `Idempotency-Key`，值为客户端生成的唯一字符串 | 如果使用 `X-Request-Id`、`token` 或 body 字段替代，则判为不通过 | `Idempotency-Key: 7f3c...` | `X-Request-Id` 兼作幂等 | 名称不一致服务端无法识别 |
| `CT-IDEM-002` | MUST | STATIC | 幂等键作用域为“同一资源 + 同一操作 + 同一用户”，有效期在 OpenAPI 对应接口的 `x-idempotencyTtl` 扩展字段声明 | 如果全局共用且无过期，则判为不通过 | 作用域 + 24h | 永久保存所有键 | 作用域过宽会误判重复 |
| `CT-IDEM-003` | MUST | STATIC | 重复请求返回首次结果，不重复执行副作用；重放响应带响应头 `Idempotent-Replay: true`，该响应头在 OpenAPI `components/headers` 声明 | 如果重复请求再次扣减或再次写入，则判为不通过 | 首次结果 + `Idempotent-Replay: true` | 每次执行都生效 | 幂等的目的就是副作用唯一 |
| `CT-IDEM-004` | MUST | STATIC | 幂等键缺失时，服务端按接口声明处理：必须幂等的接口返回 `400` | 如果必须幂等的接口缺键仍执行，则判为不通过 | 缺键 `400` | 缺键照常执行 | 静默放行等于没有幂等 |
| `CT-IDEM-005` | MUST | STATIC | 可自动重试仅限：连接失败、超时、`429`、`502/503/504`；写操作重试必须带幂等键 | 如果对 `400/401/403/404/409` 重试，则判为不通过 | 仅可恢复错误重试 | 对 `409` 循环重试 | 不可重试错误重试会放大故障 |
| `CT-IDEM-006` | MUST | STATIC | 超时时间在契约中按接口类型声明；客户端不得无限等待 | 如果接口无超时约定，则判为不通过 | 查询 10s、导出 60s | 默认无限 | 无超时会耗尽连接 |

### 本章自检清单

- [ ] `CT-IDEM-001` 幂等头名称固定（MUST, STATIC）
- [ ] `CT-IDEM-002` 作用域与有效期明确（MUST, STATIC）
- [ ] `CT-IDEM-003` 重复请求返回首次结果（MUST, STATIC）
- [ ] `CT-IDEM-004` 必须幂等的接口缺键报错（MUST, STATIC）
- [ ] `CT-IDEM-005` 重试范围正确（MUST, STATIC）
- [ ] `CT-IDEM-006` 超时有约定（MUST, STATIC）

---

## 7. traceId 与错误上报（CT-TRACE）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-TRACE-001` | MUST | STATIC | `traceId` 由服务端生成或透传入站请求头，全链路一致 | 如果每层各生成一个新 ID，则判为不通过 | 入站透传 + 响应返回 | 网关与服务各生成 | 断链会让排查无法串联 |
| `CT-TRACE-002` | MUST | STATIC | 响应体 `traceId` 与响应头 `X-Trace-Id` 同时存在且一致 | 如果两处值不同，则判为不通过 | 两处一致 | 响应头为空 | 客户端上报需要稳定来源 |
| `CT-TRACE-003` | MUST | STATIC | 错误上报内容含 `traceId`、路径、HTTP 状态、业务码；不含请求体敏感字段 | 如果上报含 token/密码/手机号，则判为不通过 | 脱敏上下文 | 整包上报 | 上报也是数据外泄面 |
| `CT-TRACE-004` | SHOULD | STATIC | 客户端错误上报走统一入口，同一次错误不重复上报 | 如果同一错误被多处上报，则判为不通过 | 单入口 + 去重 | 页面与拦截器各报一次 | 重复上报会污染监控 |

### 本章自检清单

- [ ] `CT-TRACE-001` traceId 全链路一致（MUST, STATIC）
- [ ] `CT-TRACE-002` 响应体与响应头一致（MUST, STATIC）
- [ ] `CT-TRACE-003` 上报已脱敏（MUST, STATIC）
- [ ] `CT-TRACE-004` 上报单入口（SHOULD, STATIC）

---

## 8. 版本与兼容（CT-VER）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-VER-001` | MUST | STATIC | 路径带主版本 `/api/v1`；破坏性变更升主版本 | 如果在 `/api/v1` 内删除字段或改类型，则判为不通过 | `/api/v1/orders` → `/api/v2/orders` | 原路径直接改字段 | 老客户端会直接崩溃 |
| `CT-VER-002` | MUST | STATIC | 新增可选字段视为兼容；删除字段、改类型、改语义、收紧校验视为破坏性 | 如果删除响应字段仍留在原版本，则判为不通过 | 新增字段 + 默认值 | 删除 `status` | 兼容性判定必须有统一标准 |
| `CT-VER-003` | MUST | STATIC | 废弃字段/接口标注 `deprecated`，并给出下线日期与替代方案 | 如果直接移除且无过渡，则判为不通过 | `deprecated: true` + `Sunset` | 静默删除 | 调用方需要迁移窗口 |
| `CT-VER-004` | SHOULD | STATIC | 版本并存期内，旧版接口行为不变，只做安全修复 | 如果旧版接口行为随新版一起变，则判为不通过 | 旧版冻结 | 两版共享可变实现 | 并存期要保证可预测 |
| `CT-VER-005` | MUST | STATIC | 契约变更记录包含版本号、日期、影响面、迁移方式 | 如果变更无影响面说明，则判为不通过 | 变更记录完整 | 只改 YAML | 消费方无法评估升级成本 |

### 本章自检清单

- [ ] `CT-VER-001` 主版本在路径中（MUST, STATIC）
- [ ] `CT-VER-002` 兼容性判定标准统一（MUST, STATIC）
- [ ] `CT-VER-003` 废弃有过渡期（MUST, STATIC）
- [ ] `CT-VER-004` 并存期旧版冻结（SHOULD, STATIC）
- [ ] `CT-VER-005` 变更记录完整（MUST, STATIC）

---

## 9. OpenAPI 与生成物（CT-OAS）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-OAS-001` | MUST | STATIC | `openapi.yaml` 是接口唯一描述源，路径、方法、参数、响应、错误码齐全 | 如果代码与 YAML 各写一套，则判为不通过 | YAML 先行 | 只靠代码注解导出且不校验 | 单一描述源才能生成一致客户端 |
| `CT-OAS-002` | MUST | STATIC | 每个响应声明状态码、schema、示例；错误响应同样声明 | 如果只声明 `200`，则判为不通过 | `400/401/403/404/409/422/500` 均声明 | 只写成功响应 | 客户端无法生成错误类型 |
| `CT-OAS-003` | MUST | STATIC | 生成物（TS 类型、客户端）由 CI 生成并校验无 diff | 如果生成物可手改或 CI 不校验，则判为不通过 | `gen` + `git diff --exit-code` | 手改生成文件 | 手改会在下次生成时丢失 |
| `CT-OAS-004` | MUST | STATIC | 契约变更必须做兼容性 diff，破坏性变更需评审 | 如果 CI 无兼容性检查，则判为不通过 | diff 报告 + 评审 | 直接合并 | 破坏性变更应在合并前拦截 |
| `CT-OAS-005` | SHOULD | STATIC | 示例数据来自真实结构，覆盖成功、校验失败、未授权、冲突 | 如果示例只有成功且字段不全，则判为不通过 | 四类示例 | 一个空示例 | 示例是 Mock 与测试的数据源 |
| `CT-OAS-006` | MUST | STATIC | 每个接口用 `x-status` 扩展声明 `DRAFT/APPROVED/IMPLEMENTED/DEPRECATED`，与 `AGENTS.md` §3 状态一致 | 如果接口无 `x-status` 或状态与条目不一致，则判为不通过 | `x-status: APPROVED` | 无状态字段 | 接口级状态可被脚本核对 |
| `CT-OAS-007` | MUST | STATIC | 统一在 `components/headers` 声明 `X-Trace-Id`，并在每个错误响应引用；错误响应覆盖 `400/401/403/404/409/422/500` | 如果错误响应未声明 `X-Trace-Id` 或漏状态码，则判为不通过 | `components.headers.XTraceId` | 只在 200 响应加头 | 响应头是排障与审计的稳定入口 |

### 本章自检清单

- [ ] `CT-OAS-001` YAML 为唯一描述源（MUST, STATIC）
- [ ] `CT-OAS-002` 错误响应已声明（MUST, STATIC）
- [ ] `CT-OAS-003` 生成物 CI 校验（MUST, STATIC）
- [ ] `CT-OAS-004` 破坏性变更评审（MUST, STATIC）
- [ ] `CT-OAS-005` 示例覆盖四类（SHOULD, STATIC）
- [ ] `CT-OAS-006` 接口有 `x-status`（MUST, STATIC）
- [ ] `CT-OAS-007` `X-Trace-Id` 与错误状态码齐全（MUST, STATIC）

---

## 10. 前后端边界（CT-BOUND）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-BOUND-001` | MUST | STATIC | 契约定义“传输什么”，不规定“界面怎么展示” | 如果契约中出现布局、颜色、提示位置，则判为不通过 | 只定义字段与码 | `message` 规定弹窗位置 | 展示属于 UI 规范 |
| `CT-BOUND-002` | MUST | STATIC | 契约只定义令牌的**协议载体**（`CT-AUTH-001`–`CT-AUTH-004`），不规定客户端**本地存储介质**；本地存储由 `frontend-spec.md` `FE-AUTH-001`／`FE-AUTH-002` 定义 | 如果契约规定 `localStorage` 或前端规范反过来规定 Cookie 属性，则判为不通过 | 契约只定义 `Set-Cookie` 属性 | 契约要求存 `localStorage` | 协议载体与本地存储是两个事实 |
| `CT-BOUND-003` | MUST | STATIC | 默认文案由服务端提供，客户端可覆盖展示但不改语义 | 如果客户端按码改变业务分支，则判为不通过 | 文案覆盖表 | 前端把冲突当成功 | 语义必须唯一 |
| `CT-BOUND-004` | MUST | STATIC | 契约规则可被前后端双向引用，引用时只写编号、不写章节号与行号 | 如果文档互相复述同一规则，则判为不通过 | “见 `CT-ERR-004`” | “见 `api-contract.md` §3”或重写一遍码表规则 | 章节号易漂移，复述会产生第二事实源 |

### 本章自检清单

- [ ] `CT-BOUND-001` 契约不含展示细节（MUST, STATIC）
- [ ] `CT-BOUND-002` 契约不含存储方式（MUST, STATIC）
- [ ] `CT-BOUND-003` 语义唯一、文案可覆盖（MUST, STATIC）
- [ ] `CT-BOUND-004` 双向引用只写编号（MUST, STATIC）

---

## 11. 契约评审（CT-REVIEW）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-REVIEW-001` | MUST | STATIC | 评审覆盖：字段命名、类型、可空性、错误码、状态码、分页、示例 | 如果评审只看路径是否通，则判为不通过 | 七项清单 | 只测 200 | 契约缺陷会在联调期集中爆发 |
| `CT-REVIEW-002` | MUST | STATIC | 每个错误码都有触发场景与唯一 HTTP 状态 | 如果错误码无场景，则判为不通过 | 场景 + 状态 | 只登记数字 | 无场景的码无法正确使用 |
| `CT-REVIEW-003` | MUST | STATIC | 契约评审在实现前完成，评审结论记录在变更记录 | 如果实现合并后才评审契约，则判为不通过 | 先评审后实现 | 先上线后补 | 顺序决定返工成本 |
| `CT-REVIEW-004` | SHOULD | STATIC | 跨团队消费方参与评审，确认兼容性与迁移窗口 | 如果只后端内部评审，则判为不通过 | 消费方确认 | 单方决定 | 契约影响外部团队 |

### 本章自检清单

- [ ] `CT-REVIEW-001` 七项评审齐全（MUST, STATIC）
- [ ] `CT-REVIEW-002` 错误码有场景与状态（MUST, STATIC）
- [ ] `CT-REVIEW-003` 评审先于实现（MUST, STATIC）
- [ ] `CT-REVIEW-004` 消费方参与（SHOULD, STATIC）

---

## 12. AI 交付前自检流程（CT-FLOW）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-FLOW-001` | MUST | PATTERN | 只读本次契约变更涉及的章节 | 如果引用无关章节，则判为不通过 | 改错误码只读 §3、§11 | 每次全量 | 全量会稀释注意力 |
| `CT-FLOW-002` | MUST | STATIC | 逐条记录结论并带 `文件:行号` | 如果只给总体结论，则判为不通过 | `CT-ERR-004 ／ 不通过 ／ errors.yaml:22` | “已检查” | 无记录无法复核 |
| `CT-FLOW-003` | MUST | CONTEXT | 不通过项最多迭代两轮，仍失败如实上报 | 如果隐瞒未解决项，则判为不通过 | 列未解决项 | 反复改到看似通过 | 两轮后需设计决策 |
| `CT-FLOW-004` | MUST | STATIC | 报告格式固定，通过项只汇总数量 | 如果逐条罗列通过项，则判为不通过 | 通过 N 项 + 失败清单 | 展开全部通过项 | 报告要暴露风险 |
| `CT-FLOW-005` | MUST | CONTEXT | 不得为过检查删字段、放宽校验、降级 MUST | 如果出现任一行为，则判为不通过 | 修契约不改规则 | 删必填求通过 | 检查目的是兼容 |
| `CT-FLOW-006` | MUST | CONTEXT | 存量契约只对改动范围负责，不做全文件重写 | 如果借自检重写无关接口，则判为不通过 | 只改受影响定义 | 顺手重排全文件 | 扩大改动面会引入回归 |

### 12.1 六个步骤

1. **定位规则**：按变更类型（响应/错误码/分页/格式/幂等/版本）读对应章节。
2. **改契约**：先改 `openapi.yaml` 与错误码清单。
3. **逐条比对**：`STATIC`/`PATTERN` 自行判定；`CONTEXT` 无把握转人工。
4. **自我修正**：只修“不通过”，最多两轮。
5. **输出报告**：四列 `规则编号 | 结论 | 文件:行号 | 说明`；通过项写总数。
6. **禁止作弊**：不删字段、不放宽校验、不降级 `MUST`。

### 12.2 新建与存量

| 场景 | 责任范围 | 自检要求 |
|---|---|---|
| 新增接口/错误码 | 新增内容全部 | 全部适用规则逐条判定 |
| 修改存量契约 | 仅改动定义及受影响消费方 | 判定改动范围；破坏性变更必须标出并评审 |

### 12.3 报告模板

```text
自检报告
适用模块：`CT-ERR`、`CT-VER`
通过：9 项（汇总）

| 规则编号 | 结论 | 文件:行号 | 说明 |
|---|---|---|---|
| CT-VER-001 | 不通过 | openapi.yaml:120 | 原路径删除字段 |
| CT-ERR-002 | 需人工确认 | errors.yaml:18 | 默认文案需业务确认 |
```

### 本章自检清单

- [ ] `CT-FLOW-001` 只读相关章节（MUST, PATTERN）
- [ ] `CT-FLOW-002` 逐条记录结论与行号（MUST, STATIC）
- [ ] `CT-FLOW-003` 最多两轮迭代（MUST, CONTEXT）
- [ ] `CT-FLOW-004` 报告格式固定（MUST, STATIC）
- [ ] `CT-FLOW-005` 无作弊式修改（MUST, CONTEXT）
- [ ] `CT-FLOW-006` 存量契约只对改动范围负责（MUST, CONTEXT）

---

## 13. 必须人工确认的规则（CT-HUMAN）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-HUMAN-001` | MUST | CONTEXT | 业务语义命名符合领域含义，经业务方确认 | 如果仅按命名格式判定，则判为不通过 | 标注语义待确认 | 格式正确即通过 | 语法正确可能语义相反 |
| `CT-HUMAN-002` | MUST | CONTEXT | 错误码粒度与用户可执行的下一步匹配 | 如果 AI 自行决定码粒度并宣称通过，则判为不通过 | 粒度待产品确认 | 直接上线新码 | 粒度是产品决策 |
| `CT-HUMAN-003` | MUST | CONTEXT | 破坏性变更的迁移窗口与影响面经消费方确认 | 如果单方决定下线，则判为不通过 | 迁移计划待确认 | 直接删除字段 | 影响面跨团队 |
| `CT-HUMAN-004` | MUST | CONTEXT | 分页上限、超时、幂等有效期等阈值经确认 | 如果 AI 自定阈值，则判为不通过 | 阈值待确认 | 写死 20 并称符合 | 阈值取决于容量与业务 |
| `CT-HUMAN-005` | MUST | CONTEXT | 默认错误文案的语气与业务表述经确认 | 如果 AI 自造文案并宣称通过，则判为不通过 | 文案待确认 | 自造术语 | 文案涉及业务表达 |
| `CT-HUMAN-006` | MUST | CONTEXT | 契约未覆盖的场景是否新增接口/码，需产品与架构确认 | 如果 AI 自行扩展契约，则判为不通过 | 列为缺口待确认 | 私自加字段 | 契约扩展影响多方 |

### 本章自检清单

- [ ] `CT-HUMAN-001` 业务语义待确认（MUST, CONTEXT）
- [ ] `CT-HUMAN-002` 错误码粒度待确认（MUST, CONTEXT）
- [ ] `CT-HUMAN-003` 破坏性变更影响面待确认（MUST, CONTEXT）
- [ ] `CT-HUMAN-004` 阈值待确认（MUST, CONTEXT）
- [ ] `CT-HUMAN-005` 默认文案待确认（MUST, CONTEXT）
- [ ] `CT-HUMAN-006` 契约缺口待确认（MUST, CONTEXT）

---

## 14. 附录：AI 代码评审提示词

```text
你是接口契约评审员。规则来源：docs/specs/api-contract.md（v1.3）。
检查范围：仅本次改动（openapi.yaml、错误码清单、相关消费方调用），不重写文件。
改动文件：
- <path>:<startLine>-<endLine>

要求：
1) 按变更类型定位章节，只读取相关规则区间。
2) 区分 STATIC/PATTERN/CONTEXT；CONTEXT 无把握标“需人工确认”。
3) 只输出“不通过”和“需人工确认”；通过项汇总为一句数量。
4) 输出表格：规则编号 | 级别 | 文件:行号 | 问题 | 修复建议。
5) 引用规则只写编号，不写章节号与行号；查不到写“未在文档中查到，需确认”。
6) 不得建议通过删字段、放宽校验、降级 MUST 消除问题。
7) 破坏性变更必须单独列出并标注影响面。
```

### 本章自检清单

- [ ] 提示词含规则来源与版本（MUST, STATIC）
- [ ] 提示词限定改动范围（MUST, STATIC）
- [ ] 提示词只报不通过与待确认项（MUST, STATIC）
- [ ] 破坏性变更单独列出（MUST, STATIC）

---

## 15. 令牌协议载体（CT-AUTH）

> 本节只定义**协议层载体**（Cookie 名称与属性、是否允许 Header 携带、CSRF 边界）。令牌在客户端的**本地存储方式**由 `frontend-spec.md` `FE-AUTH-001`／`FE-AUTH-002` 定义，两者是不同事实。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `CT-AUTH-001` | MUST | STATIC | Cookie 承载令牌时名称固定 `ACCESS_TOKEN`，属性必须同时具备 `HttpOnly`、`Secure`、`SameSite=Lax`、`Path=/` | 如果缺 `HttpOnly` 或 `Secure`，则判为不通过 | `Set-Cookie: ACCESS_TOKEN=...; HttpOnly; Secure; SameSite=Lax; Path=/` | `ACCESS_TOKEN=...; Path=/` | 属性缺失会放大 XSS 与 CSRF 风险 |
| `CT-AUTH-002` | MUST | STATIC | 服务端负责 `Set-Cookie` 下发与清除；退出登录用同名 Cookie + `Max-Age=0` | 如果前端用脚本删除 Cookie 或退出未清 Cookie，则判为不通过 | `Max-Age=0` 同名清除 | 前端 `document.cookie = ""` | 清除必须由服务端统一执行 |
| `CT-AUTH-003` | MUST | STATIC | Web/H5 只接受 Cookie 携带；仅小程序与 App 允许 `Authorization: Bearer`，且由 `AGENTS.md` §9-B 的 ADR 明确启用 | 如果 Web 端接受 Bearer 或允许 JS 读令牌，则判为不通过 | Web 仅 Cookie | Web 从 localStorage 读令牌塞 Header | 扩大令牌暴露面 |
| `CT-AUTH-004` | MUST | STATIC | 使用 Cookie 会话时必须实现 CSRF 防护：写操作校验 `Origin`／`Referer`，或采用双提交 Token | 如果写接口无 CSRF 校验，则判为不通过 | 写接口校验 `Origin` | 只靠 `SameSite` 不做任何校验 | CSRF 防护不能只靠单一手段 |

### 本章自检清单

- [ ] `CT-AUTH-001` Cookie 名称与四属性齐全（MUST, STATIC）
- [ ] `CT-AUTH-002` 下发与清除由服务端完成（MUST, STATIC）
- [ ] `CT-AUTH-003` Web 不接受 Bearer（MUST, STATIC）
- [ ] `CT-AUTH-004` 写操作有 CSRF 防护（MUST, STATIC）

---

## 16. 规则索引与文档关系

| 章节 | 模块前缀 | 编号区间 | 条数 | 自检清单 |
|---|---|---|---|---|
| §0 | `CT-DOC` | 001–004 | 4 | 有 |
| §1 | `CT-RESP` | 001–009 | 9 | 有 |
| §2 | `CT-HTTP` | 001–009 | 9 | 有 |
| §3 | `CT-ERR` | 001–012 | 12 | 有 |
| §4 | `CT-PAGE` | 001–006 | 6 | 有 |
| §5 | `CT-FMT` | 001–009 | 9 | 有 |
| §6 | `CT-IDEM` | 001–006 | 6 | 有 |
| §7 | `CT-TRACE` | 001–004 | 4 | 有 |
| §8 | `CT-VER` | 001–005 | 5 | 有 |
| §9 | `CT-OAS` | 001–007 | 7 | 有 |
| §10 | `CT-BOUND` | 001–004 | 4 | 有 |
| §11 | `CT-REVIEW` | 001–004 | 4 | 有 |
| §12 | `CT-FLOW` | 001–006 | 6 | 有 |
| §13 | `CT-HUMAN` | 001–006 | 6 | 有 |
| §14 | —（附录） | 无编号 | 4 项 | 有 |
| §15 | `CT-AUTH` | 001–004 | 4 | 有 |

**规则总数**：95 条（`CT-*`）。

### 16.1 唯一事实源分工

分工表已迁出至项目内 `docs/specs/INDEX-facts.md`（初始内容与维护规则见 `docs/specs/startup-change-process.md` `PRC-IDX-001`–`PRC-IDX-005`），本文不再维护第二份。本文只定义 `CT-*` 规则；事实的定义方与引用位置以索引为准。

### 16.2 与其他文档关系

| 文档 | 本文件提供 | 本文件索取 |
|---|---|---|
| 《后端编码规范》 | 契约字段、错误码、分页、格式、幂等 | 契约实现约束与导出校验 |
| 《前端编码规范》 | 消费口径与错误语义 | 展示层覆盖文案与提示分层 |
| 《前端UI设计规范》 | 无 | 错误状态与提示的视觉呈现 |

### 本章自检清单

- [ ] 新增规则已登记且编号连续（MUST, STATIC）
- [ ] 唯一事实源分工无重复定义（MUST, STATIC）
- [ ] 被引用编号真实存在（MUST, STATIC）
- [ ] 规则总数与索引一致（MUST, STATIC）