# 后端编码规范（Java）· AI 可执行规则集

| 项目 | 内容 |
|---|---|
| 版本 | **v1.6**（2026-11-08） |
| 状态 | AI 可执行规则集；规则编号 `BE-*`；每章末尾附自检清单 |
| 适用范围 | Java 后端通用规则，不绑定具体业务、项目或组织 |
| 配套文档 | 《前端UI设计规范》《前端编码规范》《接口契约与错误码规范》 |
| 事实源 | 接口契约由《接口契约与错误码规范》定义；本规范只规定“后端怎么实现契约” |
| 最终读者 | AI 编码助手；人类仅用于评审决策与人工确认项 |

**变更记录**

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.6 | 2026-11-08 | 新增 `BE-CFG-009`（枚举是否进字典由业务需求方标注决定，禁止未经标注迁移硬编码枚举、禁止删除无消费方字典项，标“待业务确认”）、`BE-CFG-010`（字典 `value` 已被代码消费后只允许改 `label`，改 `value` 属破坏性变更须先开 CR 并按 `PRC-APR-001` 逐类判定）、`BE-CFG-011`（三条既有现状登记为已知例外，保留不改）；同步自检清单与 §17 索引（`BE-CFG` 001–011，总数 156） |
| v1.5 | 2026-11-08 | 新增 `BE-CFG-007`（可配置项必须有明确消费点并登记配置台账，无消费点的值不得出现在参数管理）、`BE-CFG-008`（启动期配置不得伪装成可热改，仅展示类配置不得宣称影响行为）；同步自检清单、§13 工具映射与 §17 索引（`BE-CFG` 001–008，总数 153） |
| v1.4 | 2026-11-08 | `BE-CON-005` 时间格式改为只引用 `CT-FMT-001`，删除“或时间戳”的第二口径；`BE-API-007` 兼容性判定改为只引用 `CT-VER-001`–`CT-VER-003`，不复述；`BE-DB-007` 分页默认值与上限改为只引用 `CT-PAGE-002`；`BE-TX-009` 示例表名改 `biz_order`；`BE-SEC-003` 与 §0.3 术语映射改指项目层落位路径 |
| v1.3 | 2026-11-08 | 删除硬编码技术版本，改为引用 `AGENTS.md` §9-B；冲突优先级改为引用 `AGENTS.md` §1；权限码示例改为引用权限矩阵；删除 `202` 自定规则；校验错误对齐 `CT-ERR-006`／`422`；删除语义对齐 `CT-HTTP-007`；补 `BE-SEC-012`（下载鉴权）与 `BE-SEC-016`（`Set-Cookie` 属性）；引用只写编号；补术语映射引用 |
| v1.2 | 2026-11-07 | 全量改为 AI 可执行规则集：规则编号、MUST/SHOULD/MAY、STATIC/PATTERN/CONTEXT、通过/失败条件、正反例；每章自检清单；新增 AI 自检流程、人工确认清单、评审提示词；工具降为兜底 |
| v1.1 | 2026-11-07 | 测试与质量门禁按 L0/L1/L2 分档；补 Windows + IDEA 本机路径、Testcontainers/SonarQube 降级、覆盖率分层 |
| v1.0 | 2026-11-07 | 初稿：三方边界、决策点、工程结构与契约实现、异常/事务/校验/数据库/缓存/安全/接口/配置 |

---

## 0. 文档定位与 AI 使用约定

### 0.1 四份文档各管什么

| 文档 | 角色 | 负责定义 | 明确不负责 |
|---|---|---|---|
| 《前端UI设计规范》 | 界面与交互的定义方 | 视觉变量、布局、组件外观、交互反馈、无障碍 | 接口字段、错误码、后端实现 |
| 《接口契约与错误码规范》 | 接口契约的定义方 | URL、HTTP 方法、请求/响应结构、统一响应、业务错误码、分页/排序/筛选、时间与金额、幂等约定、OpenAPI | 前端组件实现、后端分层与框架 |
| 《前端编码规范》 | 前端实现方 | 工程结构、请求封装、错误消费、鉴权载体、跨端实现 | 契约定义、后端实现 |
| 《后端编码规范（Java）》（本文） | 后端实现方 | 分层、依赖方向、异常映射、事务、校验、幂等、并发、数据库、缓存、日志、安全、配置、测试、工具兜底 | 契约定义、UI 设计、前端实现 |

### 0.2 冲突优先级（MUST）

**通过条件**：冲突只按 `AGENTS.md` §1 的优先级链处理，本规范不复述；发现同一事实双定义时按 `AGENTS.md` §1 停机上报。

**失败信号**：如果后端代码或项目约定与契约字段、错误码、分页格式、时间/金额格式、幂等头不一致，则判为不通过。

- ✅ 契约定义 `code/message/data/traceId`，后端直接实现该结构；项目内不再包一层。
- ❌ 项目自定义 `Result` 包装，并在前端做两套适配。
- 为什么：契约是多方共同事实源，后端私自变体会让前端、测试、文档同时漂移。

**跨文档引用写法**：引用只写规则编号（如 `CT-ERR-004`），不写章节号与行号；规则归属由编号前缀区分。

### 0.3 规则编号与判定方式（MUST）

| 项 | 约定 |
|---|---|
| 编号格式 | `BE-{模块}-{三位序号}`，如 `BE-ERR-003` |
| 编号稳定性 | 编号一旦分配不再改；废弃规则保留编号并标 `DEPRECATED`，不删除、不复用 |
| 编号连续性 | 每个模块从 `001` 连续编号，不跳号 |
| 级别 | `MUST` 合并门槛；`SHOULD` 默认执行、可注明理由例外；`MAY` 可选 |
| 判定 | `STATIC` 看代码文本即可判定；`PATTERN` 需匹配结构/调用链；`CONTEXT` 需结合业务、契约或环境，AI 不能自行拍板时必须转人工确认 |
| 示例 | 每条规则给 ✅ 与 ❌ 各一个极简片段；示例只用通用命名 `User/Order/Product/Entity` |
| 术语 | `DO`／`DTO`／`Entity`／`VO`／`schema`／`model` 的映射只在项目层 `docs/requirements/glossary.md` 定义（模板见 `docs/templates/glossary.md`），本规范与代码统一按该映射使用，禁止同义替换 |

### 0.4 本章自检清单

- [ ] 本次改动涉及的文档边界已确认，未把契约定义写进后端（MUST, STATIC）
- [ ] 冲突只按 `AGENTS.md` §1 处理，未在代码或项目约定里另排优先级（MUST, CONTEXT）
- [ ] 使用或新增的规则编号符合 `BE-{模块}-{三位序号}` 且未复用旧号（MUST, STATIC）
- [ ] `CONTEXT` 规则无法确认时，已标记“需人工确认”而不是默认通过（MUST, CONTEXT）

---

## 1. 架构与分层（BE-ARCH）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-ARCH-001` | MUST | PATTERN | 依赖方向为 `app → application → domain`、`infrastructure → domain`、`common` 被各层依赖 | 如果出现 `domain → app`、`domain → infrastructure` 或 `application → app`，则判为不通过 | `app` 调用 `application`，`application` 调用 `domain` | `domain` 中 `import` 了 `app.controller` | 依赖倒置可避免领域逻辑被框架和传输层绑死 |
| `BE-ARCH-002` | MUST | PATTERN | Controller 只依赖 Application Service，不直接调用 Mapper/Repository | 如果 Controller 出现 `Mapper`、`Repository` 或 SQL 调用，则判为不通过 | `return orderAppService.get(id);` | `return orderMapper.selectById(id);` | 跨层调用会绕过事务、校验和领域规则 |
| `BE-ARCH-003` | MUST | PATTERN | `@Transactional` 只出现在 Application Service 的公共方法 | 如果 Controller、Repository、Domain 或 private/final 方法带事务注解，则判为不通过 | `OrderAppService.create()` 加 `@Transactional` | `OrderController.create()` 加 `@Transactional` | 事务边界应由用例编排层负责，传输层不应关心事务 |
| `BE-ARCH-004` | MUST | PATTERN | Domain 是纯 Java，不依赖 Spring、MyBatis、Web 类型 | 如果 Domain 出现 `@Service`、`@TableName`、`ResponseEntity`、`ApiResponse`，则判为不通过 | `public class Order { ... }` | Domain 中 `import org.springframework.stereotype.Service` | 领域模型被框架污染后无法独立测试与演进 |
| `BE-ARCH-005` | MUST | PATTERN | Repository 接口在 `domain`，实现与 Mapper 在 `infrastructure` | 如果 Domain 直接暴露 SQL、表名或 MyBatis Mapper，则判为不通过 | `domain.OrderRepository` + `infrastructure.OrderRepositoryImpl` | Domain 中直接 `import OrderMapper` | 领域只应表达持久化契约，不绑定具体数据库技术 |
| `BE-ARCH-006` | MUST | PATTERN | 跨模块只调用对方 `application` 层公开接口，不访问 `internal` 包 | 如果出现跨模块 `import ...internal...` 或直接调用对方 Mapper，则判为不通过 | `billingAppService.charge(orderId)` | `import com.example.billing.internal.BillingMapper` | 直接访问内部实现会让模块边界失效 |
| `BE-ARCH-007` | MUST | PATTERN | DO 不直接作为接口返回值；必须转为 Response DTO | 如果 Controller 返回 DO/Entity，或 DO 出现在响应类型中，则判为不通过 | `return OrderAssembler.toResponse(order);` | `public Order get(Long id)` 直接返回 DO | 实体字段会随数据库变化泄露给前端 |
| `BE-ARCH-008` | SHOULD | PATTERN | DTO 转换集中在 Assembler 或 MapStruct，且显式声明字段映射 | 如果核心对象用 `BeanUtils.copyProperties` 反射拷贝，则判为不通过 | `OrderAssembler.toResponse(order)` 显式赋值 | `BeanUtils.copyProperties(order, response)` | 反射拷贝会静默漏字段、错类型且难排查 |
| `BE-ARCH-009` | MUST | CONTEXT | 分层职责符合边界：Controller 只接收/校验/委派/返回；Service 编排用例与事务；Domain 表达规则；Infrastructure 实现技术细节 | 如果 Controller 写业务规则、Service 直接拼 SQL、Domain 处理 HTTP，则判为不通过 | Controller 只做 `@Valid` + 调用 Service | Controller 内计算折扣并更新库存 | 职责错位会让事务、权限和复用全部失控 |
| `BE-ARCH-010` | MUST | CONTEXT | 新增包/模块能明确归入现有分层，且依赖方向不破环 | 如果新增 `util2`、`common-business` 等无法归层的包，或引入循环依赖，则判为不通过 | `com.example.order.domain`、`com.example.order.infrastructure` | `com.example.order.misc` 混放控制器与 SQL | 归层不清会让后续所有规则无法判定 |
| `BE-ARCH-011` | SHOULD | STATIC | 模块依赖无环，构建工具或依赖测试可验证 | 如果模块间出现 A→B→A 的包依赖，则判为不通过 | 依赖单向：`app → application → domain` | `application` 反向依赖 `app` | 循环依赖会导致构建、测试和发布互相牵制 |
| `BE-ARCH-012` | SHOULD | STATIC | 存在 ArchUnit 或等价的依赖测试，覆盖 §1 的依赖方向 | 如果仓库没有任何依赖方向测试，则判为不通过 | `ArchUnit` 测试断言 `domain` 不依赖 `app` | 仅靠口头约定，无测试 | 依赖规则不落成测试就会随时间腐化 |

### 本章自检清单

- [ ] `BE-ARCH-001` 依赖方向无反向引用（MUST, PATTERN）
- [ ] `BE-ARCH-002` Controller 无 Mapper/Repository 直调（MUST, PATTERN）
- [ ] `BE-ARCH-003` 事务注解只在 Application Service 公共方法（MUST, PATTERN）
- [ ] `BE-ARCH-004` Domain 无框架/Web 依赖（MUST, PATTERN）
- [ ] `BE-ARCH-005` Repository 接口与实现分层正确（MUST, PATTERN）
- [ ] `BE-ARCH-006` 无跨模块 internal 访问（MUST, PATTERN）
- [ ] `BE-ARCH-007` 接口不返回 DO/Entity（MUST, PATTERN）
- [ ] `BE-ARCH-008` DTO 转换显式且集中（SHOULD, PATTERN）
- [ ] `BE-ARCH-009` 各层职责无错位（MUST, CONTEXT，需人工确认业务规则归属）
- [ ] `BE-ARCH-010` 新增包/模块可归层且无环（MUST, CONTEXT）
- [ ] `BE-ARCH-011` 模块依赖无环（SHOULD, STATIC）
- [ ] `BE-ARCH-012` 有依赖方向测试（SHOULD, STATIC）

---

## 2. 契约实现（BE-CON）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-CON-001` | MUST | STATIC | 所有接口返回契约定义的统一响应结构，字段与层级不变 | 如果出现 `Result`、`AjaxResult`、`payload` 等第二套包装，则判为不通过 | `ApiResponse<UserResponse>` | `Result<UserResponse>` 或 `{ success, result }` | 多套包装会让前端与契约测试无法统一消费 |
| `BE-CON-002` | MUST | PATTERN | 不新建业务错误码表；业务码只来自契约 | 如果代码中出现自增数字、字符串常量表或 `10086` 式业务码，则判为不通过 | `ContractCodes.ORDER_NOT_FOUND` | `if (missing) return 10086;` | 自建码表必然与契约漂移 |
| `BE-CON-003` | MUST | STATIC | 业务码通过契约生成物或常量引用，禁止散落字符串 | 如果在 Service/Controller 直接写 `"ORDER_NOT_FOUND"`，则判为不通过 | `throw new BizException(ContractCodes.ORDER_NOT_FOUND)` | `throw new BizException("ORDER_NOT_FOUND")` | 字符串散落无法编译期校验，契约删码也不会报错 |
| `BE-CON-004` | MUST | STATIC | 分页请求/响应字段与契约完全一致，不用数组替代分页对象 | 如果列表接口直接返回 `List<T>` 或改名字段，则判为不通过 | `PageResponse<OrderResponse>` 含契约字段 | `List<OrderResponse>` 直接返回 | 前端无法拿到总数、页码，契约测试也会失败 |
| `BE-CON-005` | MUST | STATIC | 时间格式只按契约 `CT-FMT-001` 执行，内部统一 `Instant`/`OffsetDateTime` | 如果使用 `Date`、自定义 `yyyy-MM-dd` 或个别 DTO 单独格式化，则判为不通过 | `Instant createdAt` 全局序列化，格式按 `CT-FMT-001` | `Date createdAt` + 局部 `@JsonFormat` | 多套时间格式会导致时区与解析错误 |
| `BE-CON-006` | MUST | STATIC | 金额按契约使用 `BigDecimal` 或最小货币单位整数，禁止浮点 | 如果金额字段是 `double`/`float`，则判为不通过 | `BigDecimal amount` | `double amount` | 浮点会丢精度，金额对账必然出错 |
| `BE-CON-007` | MUST | STATIC | 幂等键使用契约规定的请求头/字段，不另起名字 | 如果使用 `requestId`、`token` 等自定义头替代契约幂等键，则判为不通过 | 读取契约约定的幂等头 | 自建 `X-Request-Id` 当幂等键 | 幂等语义必须前后端与契约一致 |
| `BE-CON-008` | MUST | PATTERN | CI 导出 OpenAPI 并与契约文件做结构 diff | 如果仓库没有契约 diff 步骤，或 diff 失败仍可合并，则判为不通过 | `springdoc` 导出后执行契约 diff | 手写文档与代码各一套 | 没有 diff 就无法发现后端偷偷改字段 |
| `BE-CON-009` | MUST | STATIC | 错误响应不包含堆栈、SQL、类名、内部地址、依赖原始报文 | 如果响应 message 直接使用 `e.getMessage()` 或拼接 SQL，则判为不通过 | 返回契约通用文案 + `traceId` | 返回 `SQLSyntaxErrorException: ...` | 内部细节会泄露实现并带来安全风险 |
| `BE-CON-010` | MUST | PATTERN | URL、HTTP 方法、成功/失败状态码按 `CT-HTTP-001`–`CT-HTTP-009` 与 `CT-VER-001`–`CT-VER-005` 实现 | 如果出现 `GET` 写操作、`POST` 查询、成功却返回 `500` 等，则判为不通过 | `POST /api/v1/orders` 返回 `201` | `GET /api/v1/createOrder` | 方法与状态码是契约的一部分，客户端依赖它做重试与缓存 |

### 本章自检清单

- [ ] `BE-CON-001` 只有一套契约响应包装（MUST, STATIC）
- [ ] `BE-CON-002` 无自建业务错误码表（MUST, PATTERN）
- [ ] `BE-CON-003` 业务码均引用契约常量（MUST, STATIC）
- [ ] `BE-CON-004` 分页字段与契约一致（MUST, STATIC）
- [ ] `BE-CON-005` 时间类型与格式符合契约（MUST, STATIC）
- [ ] `BE-CON-006` 金额使用精确类型（MUST, STATIC）
- [ ] `BE-CON-007` 幂等键名称符合契约（MUST, STATIC）
- [ ] `BE-CON-008` CI 有 OpenAPI 契约 diff（MUST, PATTERN）
- [ ] `BE-CON-009` 错误响应无内部细节（MUST, STATIC）
- [ ] `BE-CON-010` URL/方法/状态码与契约一致（MUST, PATTERN）

---

## 3. 异常与日志（BE-ERR / BE-LOG）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-ERR-001` | MUST | PATTERN | 业务规则失败抛 `BizException` 或其子类，携带契约业务码 | 如果业务分支抛裸 `RuntimeException` 或返回错误码对象，则判为不通过 | `throw new BizException(ContractCodes.ORDER_STATE_INVALID)` | `throw new RuntimeException("状态不对")` | 业务异常可被统一映射、审计与前端消费 |
| `BE-ERR-002` | MUST | STATIC | 不用异常控制正常流程；正常分支直接返回 | 如果“查不到就抛异常再捕获”作为常规分支，则判为不通过 | `Optional.empty()` 或明确返回未找到 | `try { find(); } catch (Exception e) { ... }` 做常规分支 | 异常成本高且会掩盖真实错误 |
| `BE-ERR-003` | MUST | PATTERN | 一个异常类型只映射一个 HTTP 码与一个契约业务码 | 如果同一异常在不同 Controller 被改成不同码，则判为不通过 | 全局 `ErrorMapping` 统一映射 | Controller 内临时 `response.setStatus(200)` | 映射不一致会让客户端无法统一处理 |
| `BE-ERR-004` | MUST | STATIC | 4xx 保持 4xx，5xx 保持 5xx，不用 `200` 包装失败 | 如果失败响应 HTTP 为 `200` 且靠 body 里的 `success=false`，则判为不通过 | 参数错误返回 `400` | 失败也返回 `200` + `{"success":false}` | 网关、监控和重试策略都依赖 HTTP 状态码 |
| `BE-ERR-005` | MUST | PATTERN | 异常栈只在边界打印一次，业务代码不重复记录 | 如果同一异常在 Service 和 Advice 各打一次，则判为不通过 | 只在 `@RestControllerAdvice` 兜底打栈 | Service `log.error` 后再抛，Advice 再打 | 重复栈会淹没日志并放大磁盘占用 |
| `BE-ERR-006` | MUST | STATIC | 对外 message 不含 SQL、堆栈、类名、内部 URL、密钥、IP | 如果 message 拼接 `e.getMessage()` 或数据库错误，则判为不通过 | 统一“系统繁忙，请稍后重试” | `"java.sql.SQLException: ..."` | 内部信息会泄露实现细节与攻击面 |
| `BE-ERR-007` | MUST | CONTEXT | 可重试仅限超时、连接失败、429、502/503/504；其余不自动重试 | 如果对 400/401/403/404/409 做自动重试，则判为不通过 | 仅对超时与 5xx 有限重试 | 对 `409` 冲突循环重试 | 不可重试错误重试只会放大故障 |
| `BE-ERR-008` | MUST | PATTERN | 禁止捕获后 `return null`、返回空对象或静默忽略 | 如果 `catch` 块为空，或捕获后继续正常返回，则判为不通过 | 记录后抛业务异常，或明确降级并标记 | `catch (Exception e) { return null; }` | 吞异常会让数据处于未知状态且无法排查 |
| `BE-LOG-001` | MUST | STATIC | 日志至少含 `traceId`、`userId`（脱敏/哈希）、`method`、`path`、`elapsedMs`、`code`、`result` | 如果接口日志只有一句“请求完成”，则判为不通过 | 结构化字段齐全 | `log.info("done")` | 缺字段无法定位慢请求与失败原因 |
| `BE-LOG-002` | MUST | STATIC | 禁止打印手机号、身份证、银行卡、邮箱、密码、验证码、token、密钥、Cookie | 如果日志出现完整敏感值或 `Authorization` 原文，则判为不通过 | 脱敏后打印或只打哈希 | `log.info("token={}", token)` | 日志是最常见的敏感数据泄漏渠道 |
| `BE-LOG-003` | MUST | STATIC | 禁止 `e.printStackTrace()`；异常栈由日志框架输出 | 如果代码出现 `printStackTrace` 或 `System.out`，则判为不通过 | `log.error("...", e)` | `e.printStackTrace();` | 标准错误流不进日志系统，线上无法检索 |
| `BE-LOG-004` | MUST | STATIC | 日志内容过滤 `\r\n` 与制表符，防日志注入 | 如果直接把外部字符串拼进日志行，则判为不通过 | 结构化输出或转义后输出 | `log.info("name=" + request.getName())` | 换行可伪造日志条目，干扰审计 |
| `BE-LOG-005` | MUST | PATTERN | `MDC`/`ThreadLocal` 在 `finally` 中 `clear()`/`remove()` | 如果线程池任务结束后未清理上下文，则判为不通过 | `finally { MDC.clear(); }` | 只在成功分支清理，异常分支不清理 | 线程复用会串用户、串 traceId 并造成泄漏 |
| `BE-LOG-006` | MUST | PATTERN | `traceId` 在 HTTP、线程池、`CompletableFuture`、MQ、定时任务中正确传播 | 如果异步日志丢失 traceId 或用默认 `ForkJoinPool`，则判为不通过 | `TaskDecorator` 复制 MDC；消息头透传 | 异步任务直接 `supplyAsync` 无装饰 | 断链后无法还原一次请求的完整调用 |
| `BE-LOG-007` | MUST | CONTEXT | `ERROR` 用于系统/依赖失败；`WARN` 用于可预期业务失败与限流；`INFO` 用于关键状态；生产不开 `DEBUG` 刷业务 | 如果业务异常打 `ERROR` 或循环内打 `INFO`，则判为不通过 | 参数校验失败打 `WARN` | 每个循环项打 `INFO` | 级别错配会导致告警疲劳或漏报 |
| `BE-LOG-008` | SHOULD | PATTERN | 高频成功请求可采样；单条日志有大小上限；异步 Appender 使用有界队列 | 如果日志无上限、同步阻塞业务线程，则判为不通过 | 成功采样 1%–5%，错误 100% | 每条请求打印完整请求体 | 日志量失控会拖慢应用并推高存储成本 |

### 本章自检清单

- [ ] `BE-ERR-001` 业务失败使用 `BizException`（MUST, PATTERN）
- [ ] `BE-ERR-002` 不用异常控制正常流程（MUST, STATIC）
- [ ] `BE-ERR-003` 异常到 HTTP/业务码映射唯一（MUST, PATTERN）
- [ ] `BE-ERR-004` 失败不包成 HTTP 200（MUST, STATIC）
- [ ] `BE-ERR-005` 异常栈只在边界打印一次（MUST, PATTERN）
- [ ] `BE-ERR-006` 对外 message 无内部细节（MUST, STATIC）
- [ ] `BE-ERR-007` 可重试分类正确（MUST, CONTEXT）
- [ ] `BE-ERR-008` 无吞异常/静默返回 null（MUST, PATTERN）
- [ ] `BE-LOG-001` 日志必打字段齐全（MUST, STATIC）
- [ ] `BE-LOG-002` 无敏感信息与 token（MUST, STATIC）
- [ ] `BE-LOG-003` 无 `printStackTrace`/`System.out`（MUST, STATIC）
- [ ] `BE-LOG-004` 日志已防注入（MUST, STATIC）
- [ ] `BE-LOG-005` MDC/ThreadLocal 已清理（MUST, PATTERN）
- [ ] `BE-LOG-006` traceId 跨线程/消息正确传播（MUST, PATTERN）
- [ ] `BE-LOG-007` 日志级别使用正确（MUST, CONTEXT）
- [ ] `BE-LOG-008` 日志有采样/上限/异步（SHOULD, PATTERN）

---

## 4. 事务（BE-TX）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-TX-001` | MUST | PATTERN | 事务加在 Application Service 的公共方法；一个用例一个事务 | 如果事务加在 Controller、Repository、Domain 或一个方法里跨多个用例，则判为不通过 | `OrderAppService.create()` 一个事务 | Controller 上开事务，内部调三个 Service | 事务边界应与用例边界一致 |
| `BE-TX-002` | MUST | STATIC | 写事务使用 `rollbackFor = Exception.class` | 如果只写 `@Transactional` 且存在受检异常，则判为不通过 | `@Transactional(rollbackFor = Exception.class)` | 仅在受检异常时提交半成品 | 默认只回滚运行时异常，受检异常可能提交 |
| `BE-TX-003` | MUST | STATIC | 事务方法为 `public`、非 `final`、非 `private` | 如果事务方法被 `private/final` 修饰，则判为不通过 | `public void create()` | `private void create()` 加 `@Transactional` | 代理无法拦截非公共/最终方法 |
| `BE-TX-004` | MUST | PATTERN | 事务内只做数据库操作；远程调用、MQ、文件 IO、缓存刷新在事务外或走 Outbox | 如果 `@Transactional` 方法内直接 `httpClient.call()`、`mq.send()`、`file.write()`，则判为不通过 | 先提交业务事务，再投递消息 | 事务内调用第三方并等待响应 | 长事务占连接、外部副作用无法回滚 |
| `BE-TX-005` | MUST | PATTERN | 不在同一个 Bean 内 `this.method()` 调用事务方法 | 如果同类自调用带 `@Transactional` 的方法，则判为不通过 | 拆到另一个 Bean 或显式编程式事务 | `this.create()` 期望事务生效 | 自调用不经过代理，事务注解失效 |
| `BE-TX-006` | MUST | PATTERN | 捕获异常后要么抛、要么 `setRollbackOnly`，不得吞掉 | 如果 `catch` 后继续提交，则判为不通过 | `catch (Exception e) { setRollbackOnly(); throw e; }` | `catch (Exception e) { log.warn("ignore"); }` | 吞异常会让部分写入被提交 |
| `BE-TX-007` | SHOULD | STATIC | 事务设置超时，避免长事务拖垮连接池 | 如果事务无超时且包含循环/批量操作，则判为不通过 | `@Transactional(timeout = 10)` | 事务内跑全表更新且无超时 | 长事务会锁行、占连接并放大故障 |
| `BE-TX-008` | SHOULD | STATIC | 只读查询使用 `readOnly = true` 或不加事务 | 如果只读方法开写事务，则判为不通过 | `@Transactional(readOnly = true)` | 查询方法用默认写事务 | 只读事务可让数据库优化并减少锁 |
| `BE-TX-009` | MUST | PATTERN | 业务表与消息表同一事务提交，使用本地消息表（Outbox）或事务消息，消费端幂等 | 如果事务内直接发 MQ 且无补偿，则判为不通过 | 同一事务写 `biz_order` 与 `outbox` | `orderRepo.save(); mq.send();` 无 Outbox | 发送与提交无法原子化，必然出现不一致 |

### 本章自检清单

- [ ] `BE-TX-001` 事务边界在 Application Service（MUST, PATTERN）
- [ ] `BE-TX-002` 写事务声明 `rollbackFor = Exception.class`（MUST, STATIC）
- [ ] `BE-TX-003` 事务方法 public 且非 final（MUST, STATIC）
- [ ] `BE-TX-004` 事务内无远程/MQ/文件/缓存操作（MUST, PATTERN）
- [ ] `BE-TX-005` 无自调用事务失效（MUST, PATTERN）
- [ ] `BE-TX-006` 异常不被吞掉（MUST, PATTERN）
- [ ] `BE-TX-007` 事务有超时（SHOULD, STATIC）
- [ ] `BE-TX-008` 只读查询用只读事务（SHOULD, STATIC）
- [ ] `BE-TX-009` 事务与消息一致性方案落地（MUST, PATTERN）

---

## 5. 校验与幂等（BE-VAL / BE-IDEM）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-VAL-001` | MUST | PATTERN | Controller/DTO 使用 Bean Validation 校验必填、格式、长度、范围、枚举 | 如果外部输入直接进 Service 且无任何校验，则判为不通过 | `@NotNull @Size(max = 64) String name` | `public void create(UserRequest req)` 无校验 | 边界校验能拦住大多数脏数据 |
| `BE-VAL-002` | SHOULD | PATTERN | 创建与更新规则不同时使用分组校验 | 如果创建/更新共用同一套规则导致更新误判，则判为不通过 | `@Validated(CreateGroup.class)` | 更新时把 `id` 当必填 | 分组可让同一 DTO 适配不同用例 |
| `BE-VAL-003` | MUST | STATIC | 嵌套对象与集合元素加 `@Valid`，否则嵌套校验不生效 | 如果只校验外层对象，则判为不通过 | `@Valid @NotNull OrderItemRequest item` | 嵌套 DTO 无 `@Valid` | 缺 `@Valid` 时嵌套字段完全不校验 |
| `BE-VAL-004` | MUST | CONTEXT | 自定义校验注解只做格式/规则校验；跨表、跨服务、权限校验放 Service | 如果在自定义注解里查数据库或调远程服务，则判为不通过 | 注解校验“金额必须为正” | 注解内查用户是否存在 | 校验注解应无副作用、可重复执行 |
| `BE-VAL-005` | MUST | CONTEXT | 业务不变量、状态流转、归属与唯一性在 Application Service/Domain 校验 | 如果只靠 Controller 校验业务状态，则判为不通过 | Service 校验订单状态与归属 | Controller 里判断“是否已支付” | 业务规则可能被其他入口绕过 |
| `BE-VAL-006` | MUST | STATIC | 校验失败按 `CT-ERR-006` 返回 `data.errors[{field, code, message}]`（`code` 取 `CT-ERR-006` 的四值枚举），HTTP 状态按 `CT-HTTP-003` 为 `422`，不回显敏感输入；请求体不可解析（`CT-HTTP-009`）返回 `400`＋`INVALID_PARAMETER`，不按本条报 `422` | 如果只返回“参数错误”、顶层 `errors`／`fields` 变体、字段 `code` 自造或回显完整身份证号，则判为不通过 | `data.errors: [{field, code, message}]` + `422` | `message: "bad request"` 或顶层 `errors: []` | 字段级错误才能让调用方修正 |
| `BE-VAL-007` | MUST | PATTERN | 不信任客户端传入的 `userId`、`tenantId`、`orgId` 作为身份或权限依据 | 如果查询条件直接用客户端 `userId` 且不与当前身份取交集，则判为不通过 | 从 `SecurityContext` 取身份后叠加范围 | `where user_id = request.userId` | 客户端可伪造身份造成水平越权 |
| `BE-IDEM-001` | MUST | STATIC | 幂等键名称、生成规则、有效期来自契约 | 如果后端自创幂等头或自行定义有效期，则判为不通过 | 读取契约约定的幂等键 | 自建 `X-Request-Id` 并当幂等键 | 幂等语义必须跨端一致 |
| `BE-IDEM-002` | MUST | STATIC | 幂等记录落库，唯一索引兜底；Redis 只做前置加速 | 如果只用 Redis `SETNX` 作为唯一保障，则判为不通过 | `UNIQUE KEY uk_idem_key (idempotency_key)` | 仅 `redis.setIfAbsent` 无数据库兜底 | Redis 丢数据/过期会导致重复写入 |
| `BE-IDEM-003` | MUST | PATTERN | 幂等键绑定“用户 + 接口 + 业务指纹”，防止换键重放 | 如果只校验幂等键存在、不校验业务指纹，则判为不通过 | 指纹含用户、操作、关键参数哈希 | 同一用户换键重复创建 | 换键重放会绕过幂等 |
| `BE-IDEM-004` | MUST | CONTEXT | 重复请求按契约返回：已完成返回原结果，处理中返回处理中，失败按策略允许重试 | 如果重复请求返回随机结果或再次执行写操作，则判为不通过 | 返回首次结果或 `PROCESSING` | 第二次请求重新扣款 | 幂等的目标是让重复请求安全 |
| `BE-IDEM-005` | MUST | PATTERN | `PUT`/`DELETE` 真正幂等：重复删除返回 `200` + 信封，重复置相同状态不报错，判定按 `CT-HTTP-007` | 如果第二次删除返回 `500`／`404`／`409`，则判为不通过 | 删除已删除记录返回 `200` + `data:null` | 重复删除抛空指针或报 `409` | 方法语义承诺了幂等，实现必须兑现 |
| `BE-IDEM-006` | SHOULD | PATTERN | 幂等记录有过期清理策略，不能无限增长 | 如果幂等表无清理任务或保留期，则判为不通过 | 按保留期归档/删除 | 幂等表只增不减 | 幂等表会随请求量无限膨胀 |

### 本章自检清单

- [ ] `BE-VAL-001` 外部输入有 Bean Validation（MUST, PATTERN）
- [ ] `BE-VAL-002` 创建/更新分组校验（SHOULD, PATTERN）
- [ ] `BE-VAL-003` 嵌套对象与集合加 `@Valid`（MUST, STATIC）
- [ ] `BE-VAL-004` 自定义注解无数据库/远程副作用（MUST, CONTEXT）
- [ ] `BE-VAL-005` 业务不变量在 Service/Domain 校验（MUST, CONTEXT）
- [ ] `BE-VAL-006` 校验失败返回字段级错误（MUST, STATIC）
- [ ] `BE-VAL-007` 不信任客户端身份/范围（MUST, PATTERN）
- [ ] `BE-IDEM-001` 幂等键来自契约（MUST, STATIC）
- [ ] `BE-IDEM-002` 幂等表唯一索引兜底（MUST, STATIC）
- [ ] `BE-IDEM-003` 幂等键绑定业务指纹（MUST, PATTERN）
- [ ] `BE-IDEM-004` 重复请求返回策略符合契约（MUST, CONTEXT）
- [ ] `BE-IDEM-005` PUT/DELETE 真正幂等（MUST, PATTERN）
- [ ] `BE-IDEM-006` 幂等记录有清理策略（SHOULD, PATTERN）

---

## 6. 并发与线程（BE-CONC）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-CONC-001` | MUST | STATIC | 禁止 `new Thread()`、`Executors.newCachedThreadPool()`、无界队列；统一使用受管线程池 | 如果业务代码手工创建线程或使用无界队列，则判为不通过 | 注入 `ThreadPoolTaskExecutor` | `new Thread(task).start()` | 手工线程无法统一监控、限流与关闭 |
| `BE-CONC-002` | MUST | STATIC | 线程池核心/最大线程、队列容量、拒绝策略可配置且有界 | 如果队列容量为 `Integer.MAX_VALUE` 或参数硬编码，则判为不通过 | `queueCapacity=200`、`CallerRunsPolicy` | `new LinkedBlockingQueue<>()` 无界 | 无界队列会掩盖过载并最终 OOM |
| `BE-CONC-003` | SHOULD | PATTERN | 线程池有业务命名前缀，并暴露活跃数/队列长度指标 | 如果线程池匿名且无监控，则判为不通过 | `threadNamePrefix="order-"` | 默认 `pool-1-thread-1` | 出问题时无法定位是哪个业务池 |
| `BE-CONC-004` | MUST | PATTERN | `ThreadLocal`、MDC、数据源路由上下文在 `finally` 清理 | 如果线程池任务结束未清理上下文，则判为不通过 | `finally { ContextHolder.clear(); }` | 只在成功路径清理 | 线程复用会串用户、串租户、串数据源 |
| `BE-CONC-005` | MUST | PATTERN | `CompletableFuture` 显式指定线程池与超时；禁止默认 `ForkJoinPool.commonPool()` 跑阻塞任务 | 如果使用无执行器的 `supplyAsync` 做 IO，则判为不通过 | `CompletableFuture.supplyAsync(task, bizExecutor)` | `CompletableFuture.supplyAsync(task)` 默认池 | 公共池被阻塞会拖垮全应用 |
| `BE-CONC-006` | MUST | CONTEXT | 异步任务不依赖原事务未提交的数据；跨线程上下文用显式参数或装饰器传递 | 如果异步线程读取当前事务未提交的数据，则判为不通过 | 事务提交后再触发异步 | `@Transactional` 内 `asyncService.run()` 读未提交数据 | 新线程不在原事务中，读不到或读到旧数据 |
| `BE-CONC-007` | SHOULD | PATTERN | 并发更新使用乐观锁 + 有限重试；冲突频繁再评估悲观锁/分布式锁 | 如果更新无版本号且高并发下覆盖写，则判为不通过 | `version` 字段 + 重试 3 次 | 直接 `update ... set value=?` 无版本 | 无并发控制会静默丢失更新 |

### 本章自检清单

- [ ] `BE-CONC-001` 无手工线程/无界线程池（MUST, STATIC）
- [ ] `BE-CONC-002` 线程池有界且参数可配置（MUST, STATIC）
- [ ] `BE-CONC-003` 线程池有命名与监控（SHOULD, PATTERN）
- [ ] `BE-CONC-004` ThreadLocal/MDC 已清理（MUST, PATTERN）
- [ ] `BE-CONC-005` `CompletableFuture` 显式线程池与超时（MUST, PATTERN）
- [ ] `BE-CONC-006` 异步不依赖未提交事务（MUST, CONTEXT）
- [ ] `BE-CONC-007` 并发更新有乐观锁/重试（SHOULD, PATTERN）

---

## 7. 数据库（BE-DB）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-DB-001` | MUST | STATIC | 数据库表名/字段名小写下划线、见名知义、避免保留字；布尔用 `is_`/`has_` 前缀。**只管数据库命名**，接口字段命名按 `CT-RESP-007` | 如果出现驼峰表名、拼音缩写或保留字，则判为不通过 | `app_user`、`is_enabled` | `UserTable`、`flag` | 统一命名让 SQL、映射与评审可预测 |
| `BE-DB-002` | MUST | STATIC | 金额用 `DECIMAL`，时间用 `DATETIME/TIMESTAMP`，禁止 `FLOAT/DOUBLE` 存金额 | 如果金额字段为浮点或时间存字符串，则判为不通过 | `amount DECIMAL(18,2)` | `amount DOUBLE` | 浮点丢精度，字符串时间无法比较 |
| `BE-DB-003` | MUST | STATIC | 主键 `id`；外键 `{引用表}_id`；索引 `idx_{表}_{字段}`、唯一索引 `uk_{表}_{字段}` | 如果索引命名随意或无命名，则判为不通过 | `uk_app_user_mobile` | `index1` | 命名规范让变更与排障可读 |
| `BE-DB-004` | MUST | STATIC | 禁止 `SELECT *`；只查需要的列 | 如果 Mapper/XML/SQL 出现 `select *`，则判为不通过 | `select id, name from app_user` | `select * from app_user` | 多查字段浪费 IO，还可能泄露敏感列 |
| `BE-DB-005` | MUST | STATIC | 所有 SQL 使用参数绑定，禁止拼接用户输入 | 如果出现字符串拼接 SQL 或 `Statement` 拼接，则判为不通过 | `where id = #{id}` | `"where id = " + id` | 拼接是 SQL 注入的直接入口 |
| `BE-DB-006` | MUST | PATTERN | 动态排序字段、动态表名/列名走白名单枚举校验 | 如果客户端字段名直接进 `ORDER BY` 或表名，则判为不通过 | 白名单映射 `sort=createdAt → created_at` | `order by ${request.sort}` | 动态标识符无法参数化，必须白名单 |
| `BE-DB-007` | MUST | STATIC | 列表查询必须分页；分页字段、默认值与 `pageSize` 上限只按 `CT-PAGE-001`–`CT-PAGE-003` 执行 | 如果列表接口无分页或允许 `pageSize=100000`，则判为不通过 | 按 `CT-PAGE-002` 的默认值与上限 | 一次返回全表 | 无上限分页会拖垮数据库与网络 |
| `BE-DB-008` | SHOULD | PATTERN | 深分页超过阈值改用“最后 ID/游标”，禁止无限 `offset` | 如果 `offset` 随页码无限增大，则判为不通过 | `where id > #{lastId} order by id limit #{size}` | `offset 1000000` | 大偏移会扫描丢弃大量行 |
| `BE-DB-009` | SHOULD | STATIC | 批量写入分批（建议 500–1000 条/批），避免超大 `IN` 列表 | 如果一次提交上万条或 `IN` 超长，则判为不通过 | 每批 500 条 | 一次 `insert` 10 万条 | 大批量会撑爆事务、内存与网络 |
| `BE-DB-010` | SHOULD | PATTERN | 慢查询有阈值与记录（建议 200ms 告警、1s 必查），记录 SQL 摘要、耗时、traceId | 如果无慢查询监控，则判为不通过 | 慢 SQL 日志含 traceId 与耗时 | 慢查询无任何记录 | 没有度量就无法优化 |
| `BE-DB-011` | MUST | PATTERN | 禁止循环内逐条查询（N+1）；用批量查询、连接查询或预热缓存 | 如果在循环里调用单条查询，则判为不通过 | 一次 `in` 查询批量取回 | `for (id : ids) mapper.selectById(id)` | N+1 会把一次请求放大成数百次查询 |
| `BE-DB-012` | MUST | PATTERN | 乐观锁更新必须判断影响行数，为 0 按冲突处理 | 如果更新后不检查 `affectedRows`，则判为不通过 | `if (rows == 0) throw new BizException(...)` | `update` 后直接返回成功 | 不检查行数会丢失并发冲突 |
| `BE-DB-013` | MUST | PATTERN | 逻辑删除时查询默认带 `deleted = 0`，唯一索引把删除标记纳入设计 | 如果查询漏删除条件，或删除后无法重建同键，则判为不通过 | `uk_user_mobile_deleted (mobile, deleted)` | 唯一索引仅 `mobile`，删除后无法重建 | 逻辑删除与唯一索引天然冲突 |
| `BE-DB-014` | MUST | STATIC | 迁移脚本只追加、不修改历史；生产禁用自动建表/改表 | 如果修改已执行过的迁移脚本或生产开 `ddl-auto=update`，则判为不通过 | 新增 `V2026_11_07__add_index.sql` | 直接改历史 `V1__init.sql` | 历史脚本被改会导致环境间结构漂移 |

### 本章自检清单

- [ ] `BE-DB-001` 表/字段命名规范（MUST, STATIC）
- [ ] `BE-DB-002` 金额/时间类型正确（MUST, STATIC）
- [ ] `BE-DB-003` 主键/外键/索引命名规范（MUST, STATIC）
- [ ] `BE-DB-004` 无 `SELECT *`（MUST, STATIC）
- [ ] `BE-DB-005` SQL 参数绑定（MUST, STATIC）
- [ ] `BE-DB-006` 动态标识符走白名单（MUST, PATTERN）
- [ ] `BE-DB-007` 列表分页且有上限（MUST, STATIC）
- [ ] `BE-DB-008` 深分页使用游标（SHOULD, PATTERN）
- [ ] `BE-DB-009` 批量写入分批（SHOULD, STATIC）
- [ ] `BE-DB-010` 慢查询有阈值与记录（SHOULD, PATTERN）
- [ ] `BE-DB-011` 无 N+1 查询（MUST, PATTERN）
- [ ] `BE-DB-012` 乐观锁检查影响行数（MUST, PATTERN）
- [ ] `BE-DB-013` 逻辑删除查询与唯一索引正确（MUST, PATTERN）
- [ ] `BE-DB-014` 迁移脚本只追加、生产禁自动 DDL（MUST, STATIC）

---

## 8. 缓存（BE-CACHE）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-CACHE-001` | MUST | STATIC | 缓存 Key 使用 `{app}:{entity}:{id}` 或等价分层命名，禁止裸 ID | 如果 Key 只有 `123` 或 `userName`，则判为不通过 | `shop:order:1001` | `1001` | 裸 Key 会与其他业务/环境冲突 |
| `BE-CACHE-002` | MUST | STATIC | 所有业务缓存必须设置 TTL；禁止无过期缓存 | 如果 `set` 时无过期时间，则判为不通过 | `set(key, value, 30, MINUTES)` | `set(key, value)` 永久 | 无 TTL 会积累脏数据并占满内存 |
| `BE-CACHE-003` | MUST | PATTERN | 防穿透：缓存空值并设短 TTL，或使用布隆过滤器/参数校验 | 如果查不到就每次穿透到数据库，则判为不通过 | 空值缓存 1 分钟 | 不存在 ID 反复查库 | 恶意/异常请求会直接打穿数据库 |
| `BE-CACHE-004` | MUST | PATTERN | 防击穿：热点 Key 失效时互斥重建或逻辑过期，禁止全部线程同时回源 | 如果失效瞬间所有请求并发查库，则判为不通过 | 分布式锁 + 双重检查 | 失效后全部 `get` 未命中直接查库 | 热点失效会造成数据库瞬时冲击 |
| `BE-CACHE-005` | SHOULD | PATTERN | 防雪崩：TTL 加随机抖动、分批预热、多级缓存或限流降级 | 如果大量 Key 同一时刻过期且无保护，则判为不通过 | TTL = 30 分钟 + 随机 0–5 分钟 | 全部 Key 固定 30 分钟过期 | 同时过期会把缓存层故障放大到数据库 |
| `BE-CACHE-006` | MUST | PATTERN | 更新顺序为“先更新数据库，再删除缓存”；删除失败要重试/补偿 | 如果先删缓存再写库，或更新后不删缓存，则判为不通过 | `db.update(); cache.delete(key);` | `cache.delete(key); db.update();` | 先删缓存会让并发读回填旧值 |
| `BE-CACHE-007` | MUST | PATTERN | 禁止在事务提交前写/删缓存；提交后再操作缓存 | 如果 `@Transactional` 内刷新缓存，则判为不通过 | `TransactionSynchronization.afterCommit` | 事务内 `cache.put(...)` | 事务回滚后缓存已是脏数据 |
| `BE-CACHE-008` | MUST | STATIC | 禁止缓存大对象、全量列表、文件二进制、密码/token/隐私明文 | 如果缓存 value 超过约定大小或含敏感字段，则判为不通过 | 缓存单条 DTO，大小 ≤ 100KB | 缓存全表 JSON 或用户密码 | 大对象占内存、序列化慢且难失效 |
| `BE-CACHE-009` | MUST | CONTEXT | 缓存只做加速，数据库/权威源仍是最终事实；缓存不可作为唯一数据源 | 如果关键数据只存在缓存、丢失后无法恢复，则判为不通过 | 缓存 miss 可回源数据库 | 订单只写 Redis 不落库 | 缓存丢失/清空会造成数据不可恢复 |

### 本章自检清单

- [ ] `BE-CACHE-001` Key 命名分层且无裸 ID（MUST, STATIC）
- [ ] `BE-CACHE-002` 所有缓存有 TTL（MUST, STATIC）
- [ ] `BE-CACHE-003` 有防穿透措施（MUST, PATTERN）
- [ ] `BE-CACHE-004` 热点 Key 有互斥重建/逻辑过期（MUST, PATTERN）
- [ ] `BE-CACHE-005` 有防雪崩措施（SHOULD, PATTERN）
- [ ] `BE-CACHE-006` 先更库再删缓存（MUST, PATTERN）
- [ ] `BE-CACHE-007` 事务提交后才操作缓存（MUST, PATTERN）
- [ ] `BE-CACHE-008` 无大对象/敏感缓存（MUST, STATIC）
- [ ] `BE-CACHE-009` 缓存不是唯一数据源（MUST, CONTEXT）

---

## 9. 安全（BE-SEC）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-SEC-001` | MUST | PATTERN | 当前用户从 `SecurityContext`/认证上下文获取，不从请求参数取 | 如果身份来自 `@RequestParam userId` 或自定义头，则判为不通过 | `SecurityContextHolder.getContext().getAuthentication()` | `public User get(@RequestParam Long userId)` 直接信任 | 参数可伪造，等于没有认证 |
| `BE-SEC-002` | MUST | PATTERN | 令牌校验签名、算法、过期、签发者、受众、类型；算法固定 | 如果接受 `none` 算法或由客户端指定算法，则判为不通过 | 固定 RS256/HS256 并校验 audience | 解析 JWT 不验签 | 不验签等于任何人都能伪造身份 |
| `BE-SEC-003` | MUST | PATTERN | 接口默认拒绝；垂直权限用 URL 规则 + 方法级注解；权限码取值只来自项目层 `docs/design/permission-matrix.md`（模板见 `docs/templates/permission-matrix.md`）定义的格式与清单 | 如果新接口未配置权限且默认可访问，或使用矩阵外的权限码，则判为不通过 | `@PreAuthorize("hasAuthority('biz:order:read')")` | 只在前端隐藏按钮；自造 `order:read` | 前端隐藏不是安全边界，权限码必须有唯一定义方 |
| `BE-SEC-004` | MUST | PATTERN | 水平权限落到查询条件：当前用户/租户/组织归属与资源归属取交集 | 如果先查全量再内存过滤，则判为不通过 | `where owner_id = #{currentUserId}` | 查出后 `if (owner.equals(current))` 过滤 | 内存过滤会破坏分页且易漏 |
| `BE-SEC-005` | MUST | PATTERN | 客户端 `userId/tenantId/orgId` 只作筛选条件，必须与当前身份取交集 | 如果直接作为唯一查询条件，则判为不通过 | `and tenant_id = #{currentTenantId} and org_id = #{requestOrgId}` | `where tenant_id = #{requestTenantId}` | 可跨租户读取他人数据 |
| `BE-SEC-006` | MUST | PATTERN | SQL 注入防护：参数绑定；动态表名/列名白名单 | 如果 SQL 拼接用户输入，则判为不通过 | `#{name}` + 白名单排序 | `"where name = '" + name + "'"` | SQL 注入可直接读改库 |
| `BE-SEC-007` | MUST | STATIC | 禁止用户输入进入命令、SpEL/OGNL、模板、LDAP/XPath 表达式 | 如果 `Runtime.exec`、`SpelExpressionParser` 直接使用外部输入，则判为不通过 | 固定命令 + 参数列表 | `exec("sh -c " + input)` | 表达式/命令注入可导致远程执行 |
| `BE-SEC-008` | MUST | PATTERN | 富文本服务端白名单清洗；普通 JSON 不拼 HTML；下载加 `nosniff` | 如果用户输入原样写入 HTML 或富文本不清洗，则判为不通过 | OWASP Sanitizer 白名单 | `response.getWriter().write(input)` | 存储型 XSS 会影响所有访问者 |
| `BE-SEC-009` | MUST | PATTERN | SSRF 防护：协议/域名/端口白名单，解析后校验 IP，禁内网/回环/元数据地址，不自动跟随重定向 | 如果用户可控 URL 直接请求，则判为不通过 | 白名单域名 + IP 校验 | `httpClient.get(request.url)` 无校验 | SSRF 可探测内网与云元数据 |
| `BE-SEC-010` | MUST | STATIC | Jackson 禁止全局默认类型；多态白名单；禁止 Java 原生反序列化外部数据 | 如果开启 `activateDefaultTyping` 或使用 `ObjectInputStream`，则判为不通过 | 显式 `@JsonTypeInfo` + 白名单 | 全局默认类型 + 任意类名 | 反序列化漏洞可导致远程代码执行 |
| `BE-SEC-011` | MUST | PATTERN | 文件上传校验大小、扩展名、MIME、文件头；存储名 UUID；防路径穿越；上传目录不可执行 | 如果只用扩展名判断或使用原文件名拼路径，则判为不通过 | UUID 文件名 + magic number 校验 | `../../` 路径 + 原始文件名 | 上传是 webshell 与路径穿越入口 |
| `BE-SEC-012` | MUST | PATTERN | 文件下载鉴权并校验资源归属；不返回存储绝对路径 | 如果知道 URL 就能下载任意文件，则判为不通过 | 下载前校验 owner/权限 | `GET /files?path=/data/...` 直读 | 未鉴权下载会造成数据泄露 |
| `BE-SEC-013` | MUST | STATIC | 密码使用 bcrypt（strength 10–12）或 argon2id；禁止 MD5/SHA1/明文 | 如果密码字段用 MD5 或可逆加密，则判为不通过 | `BCryptPasswordEncoder(12)` | `DigestUtils.md5Hex(password)` | 弱哈希可被离线破解 |
| `BE-SEC-014` | MUST | STATIC | 密钥/口令来自环境变量或密钥管理，不进 Git/镜像/日志/响应 | 如果代码常量或配置文件明文存密钥，则判为不通过 | `${DB_PASSWORD}` 或 Vault | `password: "123456"` 入库 | 密钥入库等于公开 |
| `BE-SEC-015` | MUST | STATIC | HTTPS/HSTS；CORS 白名单；Cookie 会话开 CSRF；安全响应头 | 如果 CORS 为 `*` 且带凭据，或 Cookie 会话无 CSRF，则判为不通过 | 白名单 Origin + CSRF token | `allowOrigins("*").allowCredentials(true)` | 浏览器安全边界必须由服务端配置 |
| `BE-SEC-016` | MUST | STATIC | Cookie 承载令牌时由服务端写入 `Set-Cookie`，属性按 `CT-AUTH-001` 固定；退出按 `CT-AUTH-002` 清除；写操作按 `CT-AUTH-004` 做 CSRF 校验 | 如果 Cookie 缺 `HttpOnly`／`Secure`，或退出未清 Cookie，或写接口无 CSRF 校验，则判为不通过 | `Set-Cookie: ACCESS_TOKEN=...; HttpOnly; Secure; SameSite=Lax; Path=/` | Cookie 由前端脚本写入或退出只清前端内存 | 属性缺失会放大 XSS 与 CSRF 风险 |

### 本章自检清单

- [ ] `BE-SEC-001` 身份来自认证上下文（MUST, PATTERN）
- [ ] `BE-SEC-002` 令牌完整校验且算法固定（MUST, PATTERN）
- [ ] `BE-SEC-003` 接口默认拒绝且有垂直权限（MUST, PATTERN）
- [ ] `BE-SEC-004` 水平权限落到查询条件（MUST, PATTERN）
- [ ] `BE-SEC-005` 客户端范围参数与身份取交集（MUST, PATTERN）
- [ ] `BE-SEC-006` SQL 注入防护（MUST, PATTERN）
- [ ] `BE-SEC-007` 无命令/表达式注入（MUST, STATIC）
- [ ] `BE-SEC-008` XSS/富文本清洗（MUST, PATTERN）
- [ ] `BE-SEC-009` SSRF 白名单与 IP 校验（MUST, PATTERN）
- [ ] `BE-SEC-010` 反序列化白名单（MUST, STATIC）
- [ ] `BE-SEC-011` 文件上传三重校验与路径防护（MUST, PATTERN）
- [ ] `BE-SEC-012` 文件下载鉴权（MUST, PATTERN）
- [ ] `BE-SEC-013` 密码哈希参数正确（MUST, STATIC）
- [ ] `BE-SEC-014` 密钥外置且不泄漏（MUST, STATIC）
- [ ] `BE-SEC-015` 传输/CORS/CSRF 配置安全（MUST, STATIC）
- [ ] `BE-SEC-016` `Set-Cookie` 属性与清除由服务端按契约实现（MUST, STATIC）

---

## 10. 接口设计（BE-API）

> 响应结构、错误码、分页字段、时间/金额、幂等键的定义见 `BE-CON` 与 `CT-*`；本章只约束 URL、方法、版本、批量与兼容性。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-API-001` | MUST | STATIC | 资源路径使用名词复数；动作型接口用子资源 + `POST` | 如果路径出现 `getUser`、`createOrder` 等动词，则判为不通过 | `/api/v1/users/{id}` | `/api/v1/getUser?id=1` | 资源化 URL 更稳定、可缓存、易治理 |
| `BE-API-002` | MUST | PATTERN | HTTP 方法语义正确：GET 查询、POST 创建/动作、PUT 全量更新、PATCH 部分更新、DELETE 删除 | 如果 GET 写数据、POST 做查询、DELETE 带请求体做更新，则判为不通过 | `DELETE /api/v1/orders/{id}` | `GET /api/v1/orders/delete?id=1` | 方法语义决定缓存、重试与网关行为 |
| `BE-API-003` | MUST | STATIC | 版本策略按 `CT-VER-001`–`CT-VER-005` 实现，本规范不另定版本格式与升级条件 | 如果同一 URL 静默改字段含义，或无版本文档，则判为不通过 | `/api/v1/orders` | `/api/orders` 且字段含义随时改 | 客户端无法同时兼容新旧行为 |
| `BE-API-004` | MUST | STATIC | 批量接口限制单次条数，超限返回参数错误 | 如果批量接口无上限，则判为不通过 | 单次最多 100 条 | 一次提交任意数量 | 无上限会拖垮数据库与内存 |
| `BE-API-005` | MUST | CONTEXT | 长任务异步化在契约中未定义：遇到长任务需求必须停机提问并标 `RULE-GAP`，或按 `CT-HUMAN-006` 先提案扩展契约，禁止后端自定 `202` 语义 | 如果后端未经契约评审自行返回 `202` 或自造任务查询接口，则判为不通过 | 提案扩展契约后再实现 | 直接 `202 {taskId}` 并自称符合契约 | 契约外状态码会让消费方无法统一处理 |
| `BE-API-006` | MUST | STATIC | 同步接口设置明确超时，禁止无限等待 | 如果外部调用或长查询无超时，则判为不通过 | 连接/读取超时均配置 | `httpClient` 默认无超时 | 无超时会拖死调用方线程 |
| `BE-API-007` | MUST | PATTERN | 兼容性判定与版本演进只按 `CT-VER-001`–`CT-VER-003` 执行，本规范不复述；后端不得在已发布版本内绕过契约版本区分 | 如果在原版本删除字段或改变字段语义，则判为不通过 | 破坏性变更走 `CT-VER-001` 的新主版本 | `v1` 直接把 `amount` 从元改成分 | 静默变更会让旧客户端解析错误 |
| `BE-API-008` | SHOULD | PATTERN | 废弃字段/接口有过渡期、标注与下线时间 | 如果直接删除且无迁移说明，则判为不通过 | 标 `Deprecated` + 下线日期 | 今天删字段，明天发版 | 调用方没有迁移窗口 |

### 本章自检清单

- [ ] `BE-API-001` URL 名词复数、无动词路径（MUST, STATIC）
- [ ] `BE-API-002` HTTP 方法语义正确（MUST, PATTERN）
- [ ] `BE-API-003` 版本策略按 `CT-VER-*` 实现（MUST, STATIC）
- [ ] `BE-API-004` 批量接口有上限（MUST, STATIC）
- [ ] `BE-API-005` 长任务未自定 `202`，缺口已标 `RULE-GAP`／已提案（MUST, CONTEXT）
- [ ] `BE-API-006` 同步接口有超时（MUST, STATIC）
- [ ] `BE-API-007` 兼容性判定只引用 `CT-VER-001`–`CT-VER-003`，未在本文复述（MUST, PATTERN）
- [ ] `BE-API-008` 废弃项有过渡期（SHOULD, PATTERN）

---

## 11. 配置管理（BE-CFG）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-CFG-001` | MUST | STATIC | 多环境使用 profile/环境变量覆盖，环境标识显式 | 如果生产与开发共用同一份无差异配置，则判为不通过 | `application-test.yml` + 环境变量 | 本地配置直接连生产 | 环境混用会导致事故 |
| `BE-CFG-002` | MUST | STATIC | 密钥、口令、连接凭据从环境变量或密钥管理读取，不进 Git/镜像/日志 | 如果配置文件明文存密钥，则判为不通过 | `${DB_PASSWORD}` | `password: "123456"` | 明文密钥等于公开 |
| `BE-CFG-003` | MUST | STATIC | 超时、重试、线程池、批量大小、阈值、开关、外部地址全部配置化 | 如果业务代码出现魔法数字/硬编码地址，则判为不通过 | `order.payment.timeout=10s` | `if (retry < 3)` 硬编码 | 硬编码无法按环境调整 |
| `BE-CFG-004` | MUST | PATTERN | 启动时校验必需配置，缺失即失败（fail-fast），敏感值不打日志 | 如果缺配置仍能启动并运行到运行期才报错，则判为不通过 | `@ConfigurationProperties` + `@Validated` | 空配置启动，首次请求才失败 | 早失败比运行期故障便宜 |
| `BE-CFG-005` | SHOULD | PATTERN | 动态配置变更可审计、可回滚、有灰度；不靠改数据库配置表即时生效 | 如果配置变更无记录、无法回滚，则判为不通过 | 配置中心版本 + 审批 | 直接 `update config set value` | 不可回滚的变更会放大故障 |
| `BE-CFG-006` | MUST | STATIC | 配置项使用 `kebab-case` 并按模块加前缀，命名与文档一致 | 如果配置项命名随意或与文档不一致，则判为不通过 | `order.payment.timeout` | `timeout2` | 命名不一致会导致配错、查不到 |
| `BE-CFG-007` | MUST | STATIC | 每个可配置项必须有明确消费点，并按六项登记在 `docs/design/config-registry.yaml`：`key`／`owner`／`consumedBy`／`effect`／`afterChange`／`verify`；无消费点的值禁止出现在参数管理或配置表 | 如果参数管理中存在某个键但台账未登记，或代码中找不到读取该键的位置，则判为不通过 | 台账一行 `sys.upload.max-size`，`consumedBy` 指向读取该值的配置绑定类 | 后台能改 `sys.name`，备注写“显示在登录页与顶栏”，但前端文案硬编码、无读取点 | 能改不等于生效，无消费点的配置是假开关 |
| `BE-CFG-008` | MUST | STATIC | 启动期读取的配置（`effect=STARTUP`）必须写 `afterChange` 含“重启”，且参数管理界面禁止标注“保存即生效”；仅展示类配置（`DISPLAY_ONLY`）禁止宣称影响行为 | 如果 `effect=STARTUP` 但 `afterChange` 写“立即生效/刷新页面”，或 `DISPLAY_ONLY` 被当作行为开关，则判为不通过 | 台账写 `effect: STARTUP`、`afterChange: 重启应用` | 备注写“保存后生效”，实际必须重启才读到 | 启动期配置伪装可热改会让使用者误判 |
| `BE-CFG-009` | MUST | STATIC | 枚举是否进字典表，**由业务需求方显式标注决定**，不由开发或 AI 自行判断：① 未经业务标注，禁止把代码中的硬编码枚举迁入字典表；② 未经业务标注，禁止删除当前无消费方的字典数据（含字典类型及其字典项），应保留并标注“待业务确认”；③ 是否进字典的判断依据只能是业务标注记录，不是“看起来该统一” | 如果开发/AI 自行把硬编码枚举改成字典驱动、或删除了无消费方的字典类型/字典项、或以“统一治理”为由批量迁移，则判为不通过 | 代码内枚举保持硬编码，需求标注后再迁移；`yes_no` 保留并标注“待业务确认” | 巡检时顺手删除零引用的 `yes_no` 字典；把示例任务状态迁移进字典 | 迁移会让消费方与历史数据同时变更，且无需求方为结果负责 |
| `BE-CFG-010` | MUST | STATIC | 字典项 `value` 一旦被代码消费（如按 `user_status` 的 `1`／`0` 做状态判断、按枚举值分支），只允许修改 `label`；修改 `value` 属**破坏性变更**：必须先开 CR（`PRC-CR-001`）并按 `PRC-APR-001` 逐类判定是否需签核，且同步迁移已存业务数据与消费方比较逻辑。后果是本条要写明的：`value` 与代码/历史数据不再匹配，状态判断与列表筛选静默失效（不报错但结果错误） | 如果直接改 `value` 而无 CR 与数据迁移，或把该改动当作“改文案”处理，则判为不通过 | 只改 `label`“正常”→“启用”；改 `value` 前先开 CR 并列出数据迁移与消费点清单 | 把 `user_status` 的 `1` 改成 `ACTIVE`，只改字典表 | 文本映射失效不会抛异常，只会让判断走错分支且难以定位 |
| `BE-CFG-011` | MUST | CONTEXT | 下列三条为**已登记现状（已知例外）**，保留不改，禁止当作待修问题或巡检整改项：① `user_status` 已进字典，但其 `value` 被代码绑定；② `yes_no` 字典零消费；③ 示例任务状态 `TODO`／`DOING`／`DONE` 硬编码、未进字典。三者的处置一律等业务需求方标注，按 `BE-CFG-009` 执行 | 如果 AI 以“不符合规范”为由擅自迁移或删除上述对象，则判为不通过 | 巡检报告写明“已登记例外，等业务标注” | 巡检报告列为缺陷并顺手修复 | 未标注的“规范化”改动会破坏现有行为 |

### 本章自检清单

- [ ] `BE-CFG-001` 多环境隔离且标识显式（MUST, STATIC）
- [ ] `BE-CFG-002` 敏感配置外置（MUST, STATIC）
- [ ] `BE-CFG-003` 无硬编码阈值/地址（MUST, STATIC）
- [ ] `BE-CFG-004` 启动 fail-fast 校验（MUST, PATTERN）
- [ ] `BE-CFG-005` 动态配置可审计/回滚（SHOULD, PATTERN）
- [ ] `BE-CFG-006` 配置项命名规范（MUST, STATIC）
- [ ] `BE-CFG-007` 可配置项有明确消费点并登记台账（MUST, STATIC）
- [ ] `BE-CFG-008` 启动期/仅展示配置未伪装成可热改（MUST, STATIC）
- [ ] `BE-CFG-009` 枚举入字典以业务标注为准，未擅自迁移/删除（MUST, STATIC）
- [ ] `BE-CFG-010` 被消费的字典 `value` 未被直接修改（MUST, STATIC）
- [ ] `BE-CFG-011` 三条已登记例外未被当作缺陷整改（MUST, CONTEXT）

---

## 12. 测试（BE-TEST）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-TEST-001` | MUST | PATTERN | 单元测试不连真实网络、数据库、MQ；外部依赖 Mock/Fake | 如果单测启动 Spring 全上下文或连本机库做纯逻辑测试，则判为不通过 | `@Test` + Mockito 隔离依赖 | 单测里 `new RestTemplate()` 调真实服务 | 单测要快且可重复 |
| `BE-TEST-002` | MUST | PATTERN | 时间、随机、ID 生成通过 `Clock`/`IdGenerator` 注入 | 如果测试依赖 `System.currentTimeMillis()` 或随机数，则判为不通过 | 注入固定 `Clock` | 断言里写 `LocalDate.now()` | 否则测试会偶发失败 |
| `BE-TEST-003` | MUST | STATIC | 集成测试使用独立测试库/schema，不与其他环境共用 | 如果测试配置指向开发库或共享库，则判为不通过 | `application-test.yml` 指向 `app_test` | 测试连 `dev` 库 | 共用库会被清库/写脏 |
| `BE-TEST-004` | MUST | STATIC | 禁止测试连接生产库/生产中间件 | 如果测试配置出现生产地址或生产账号，则判为不通过 | 测试库地址仅本机/CI | 测试读生产订单表 | 测试写操作会造成真实事故 |
| `BE-TEST-005` | MUST | CONTEXT | 认识 `@Transactional` 测试回滚的局限：自调用、异步、MQ、缓存、DDL 不会被回滚 | 如果依赖测试事务回滚覆盖上述场景，则判为不通过 | 关键场景用清理脚本/独立库 | 用测试事务验证 MQ 消费回滚 | 会造成“假绿” |
| `BE-TEST-006` | MUST | PATTERN | 测试数据用工厂/Builder 显式构造，不依赖用例执行顺序 | 如果测试依赖上一条留下的数据，则判为不通过 | `OrderFixture.create()` | 依赖 `id=1` 已存在 | 顺序变化会导致随机失败 |
| `BE-TEST-007` | SHOULD | CONTEXT | 覆盖率按分层标准，不搞一刀切；核心领域优先 | 如果要求 DTO/配置也达到同一高阈值，则判为不通过 | 核心 80%、编排 60–70%、工具 80% | 全模块统一 90% | 会逼出无断言测试 |
| `BE-TEST-008` | MUST | STATIC | 本地可生成 Jacoco 报告，路径为 `target/site/jacoco/index.html` | 如果仓库无覆盖率插件或报告路径不可用，则判为不通过 | `mvn test jacoco:report` | 只有口头覆盖率 | 看不到报告就无法改进 |
| `BE-TEST-009` | SHOULD | PATTERN | 关键接口有契约用例，覆盖成功、参数错误、鉴权失败、业务失败 | 如果只测成功路径，则判为不通过 | 四类用例各一 | 只测 200 | 失败路径才是契约重点 |
| `BE-TEST-010` | MUST | PATTERN | 异步/消息测试用等待与超时断言，不用固定 `sleep` | 如果测试用 `Thread.sleep(5000)` 等结果，则判为不通过 | `Awaitility` 或轮询超时 | `sleep` 后直接断言 | 固定等待既慢又不稳定 |
| `BE-TEST-011` | SHOULD | STATIC | 测试命名表达“给定-当-则”，一个用例一个主题 | 如果测试名是 `test1` 或一个用例断言十几件事，则判为不通过 | `createOrder_whenAmountNegative_thenReject` | `test()` | 失败时无法定位意图 |

**覆盖率分层标准（SHOULD）**

| 模块 | 建议门槛 | 判定方式 | 说明 |
|---|---|---|---|
| 核心领域规则（金额、权限、状态机） | ≥80% 行 + 分支 | Jacoco | 关键不变量必须有正反用例 |
| Application Service 编排 | ≥60%–70% | Jacoco | 覆盖主要分支与异常路径 |
| Controller | 不按行覆盖率 | 接口/契约测试 | 主路径、参数校验、错误码 |
| 工具类/转换器 | ≥80% | Jacoco | 覆盖边界输入 |
| DTO/配置/常量 | 不设门槛 | 不统计 | 无逻辑不强制 |

**本地看覆盖率（必须）**：`mvn test jacoco:report` 后打开 `target/site/jacoco/index.html`；多模块项目在每个模块的 `target/site/jacoco/` 下。

### 本章自检清单

- [ ] `BE-TEST-001` 单测不连外部依赖（MUST, PATTERN）
- [ ] `BE-TEST-002` 时间/随机/ID 可注入（MUST, PATTERN）
- [ ] `BE-TEST-003` 集成测试使用独立测试库（MUST, STATIC）
- [ ] `BE-TEST-004` 测试不连生产/共享库（MUST, STATIC）
- [ ] `BE-TEST-005` 已识别测试事务回滚局限（MUST, CONTEXT）
- [ ] `BE-TEST-006` 测试数据独立且不依赖顺序（MUST, PATTERN）
- [ ] `BE-TEST-007` 覆盖率按分层标准（SHOULD, CONTEXT）
- [ ] `BE-TEST-008` Jacoco 报告可生成并查看（MUST, STATIC）
- [ ] `BE-TEST-009` 关键接口覆盖四类用例（SHOULD, PATTERN）
- [ ] `BE-TEST-010` 异步测试无固定 sleep（MUST, PATTERN）
- [ ] `BE-TEST-011` 测试命名表达意图（SHOULD, STATIC）

---

## 13. 工具与质量门禁（BE-TOOL）

> 定位：**AI 自检为主，工具为兜底**。本地不装任何工具，也能靠文档 + 自检清单完成检查；工具只负责可自动化的部分，不作为“必须部署”的前置条件。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-TOOL-001` | MUST | CONTEXT | 每次交付先做 AI 自检：按规则编号逐条判定并输出报告 | 如果只跑工具、不按规则自检，则判为不通过 | 报告含规则编号/结论/行号 | 只说“已检查，没问题” | 工具覆盖不了语义与架构规则 |
| `BE-TOOL-002` | MUST | CONTEXT | 工具只做兜底；未安装工具不构成跳过检查的理由 | 如果以“本机没 SonarQube/没 Docker”为由不检查，则判为不通过 | 无工具也提交规则自检 | 等平台搭好再开发 | 工具是放大器，不是门槛 |
| `BE-TOOL-003` | MUST | STATIC | L0 零依赖档可完成：JDK + Maven + IDEA + 本机 MySQL 测试库 | 如果 L0 要求 Docker、SonarQube 或任何服务端组件，则判为不通过 | 本机建 `app_test` 库即可跑集成测试 | 先装 Docker 才能跑单测 | 本机默认必须能跑起来 |
| `BE-TOOL-004` | SHOULD | PATTERN | L1 插件按改动文件/模块跑，避免每次全仓全量 | 如果每次提交都全量扫描且耗时不可接受，则判为不通过 | IDEA 分析当前文件；`mvn -pl app -am verify` | 每次全仓库跑所有规则 | 全量慢会让人放弃检查 |
| `BE-TOOL-005` | SHOULD | CONTEXT | Testcontainers 仅在触发条件下引入（见下表），个人本机不强制 | 如果个人开发环境被要求必须装 Docker，则判为不通过 | CI Linux 或团队 ≥3 人再上 | 单人项目强制 Testcontainers | 环境成本应与收益匹配 |
| `BE-TOOL-006` | SHOULD | CONTEXT | SonarQube 仅在触发条件下部署（≥10 人或 ≥5 个项目，且有维护人） | 如果单人/小项目先部署 SonarQube，则判为不通过 | 先用 SonarLint + Maven 插件 | 为 2 人项目维护 SonarQube | 平台维护成本高于收益 |
| `BE-TOOL-007` | MUST | STATIC | 没有 SonarQube 也必须做静态检查：SonarLint/SpotBugs/PMD/Checkstyle 至少一项 | 如果以“没有平台”为由不做静态检查，则判为不通过 | 本地插件 + `mvn verify` | 完全不检查代码风格与缺陷 | “没平台”不等于“不检查” |
| `BE-TOOL-008` | MUST | PATTERN | 维护“规则 → 工具”映射；新增规则时同步判断能否自动化 | 如果新增规则后映射表不更新，则判为不通过 | 映射表含规则区间与工具 | 规则与工具各说各话 | 映射失效会让检查出现盲区 |

### 13.1 三档总览

| 档位 | 定位 | 需要装什么 | 资源占用 | 首次跑通 | 能拦住什么 |
|---|---|---|---|---|---|
| **L0 零依赖（本机默认）** | 必须能跑 | 语言运行时/Maven/IDEA/本机数据库按 `AGENTS.md` §9-B | 约 2–3GB 磁盘、2–4GB 内存 | 10–20 分钟 | 逻辑错误、SQL/映射错误、基本回归 |
| **L1 本地增强** | 可选，按需 | L0 + Checkstyle/SpotBugs/PMD Maven 插件 + IDEA 插件 + SonarLint | 额外 100–300MB 磁盘 | 30–60 分钟调规则 | 风格、空指针、资源泄漏、坏味道 |
| **L2 团队/CI** | 满足触发条件才上 | Docker/Testcontainers、CI、SonarQube | Docker 4–8GB 内存；SonarQube 2–4GB + 数据库 | 1–3 天 | 环境差异、并发、契约漂移、质量趋势 |

### 13.2 L0：Windows + IDEA 本机操作路径

> 以下建库语句以 `AGENTS.md` §9-B 选定 MySQL 为前提；若选型不同，改用对应数据库的等价语句，数据库类型不在本规范内硬编码。

**第 1 步：建独立测试库与账号（必须）**

```sql
CREATE DATABASE app_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'app_test'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON app_test.* TO 'app_test'@'localhost';
```

**第 2 步：测试配置放 `src/test/resources/application-test.yml`**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app_test?useSSL=false&serverTimezone=UTC
    username: app_test
    password: ${TEST_DB_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**第 3 步：测试类指定 profile**

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderAppServiceTest {
    // ...
}
```

**第 4 步：在 IDEA 里跑**

- 打开测试类，点类名或方法左侧绿色箭头；Run Configuration 里把 `TEST_DB_PASSWORD` 设为环境变量。
- 或终端执行：`mvn -Dspring.profiles.active=test test`。

**第 5 步：避免污染开发库（必须）**

- 测试库用独立 schema（如 `app_test`）和独立账号，账号只授权该 schema。
- 禁止把测试配置指向开发库/生产库；CI 与本地使用同一套相对地址约定。
- 需要清库时用 `@Sql` 清理脚本或独立库重建；`@Transactional` 测试回滚只适用于单库、无异步/无 MQ 场景（见 `BE-TEST-005`）。

### 13.3 L1：Maven 插件 + IDEA 实时提示

| 动作 | 做法 |
|---|---|
| 引入插件 | `maven-checkstyle-plugin`、`spotbugs-maven-plugin`、`maven-pmd-plugin`、`jacoco-maven-plugin` 绑定到 `verify` |
| 一条命令 | `mvn verify`；单测与覆盖率：`mvn test jacoco:report` |
| IDEA 实时提示 | 安装 CheckStyle-IDEA、SpotBugs-IDEA、PMDPlugin、SonarLint；打开文件即提示 |
| 只跑改动文件 | IDEA 右键 `Analyze Current File`/包；Maven 用 `mvn -pl app -am verify`；插件 include 参数因版本而异，以项目 pom 为准 |
| 改动清单 | PowerShell：`git diff --name-only HEAD`，筛 `*.java` 后在 IDEA 中逐文件分析 |

**必须**：规则集进仓库并版本化；新增代码不得引入新的 Blocker/Critical 问题；存量问题单独清单，不阻塞无关改动。

### 13.4 L2：团队 / CI 档触发条件

| 能力 | 什么时候才值得上 | 不满足时的替代 |
|---|---|---|
| Testcontainers | 有 Docker；团队 ≥3 人；需要生产同版本数据库；并发/集成问题反复；CI 为 Linux | L0 本机 MySQL 测试库 |
| SonarQube | ≥10 人或 ≥5 个项目；需要历史趋势/质量门禁/统一报表；有专人维护 | L1 插件 + SonarLint |
| 契约测试 | 多个消费方或外部团队；OpenAPI 变更频繁 | CI 跑 OpenAPI diff + 人工核对 |

### 13.5 Testcontainers 降级路径

| 替代做法 | 已知短板 | 什么情况下必须补回 |
|---|---|---|
| 本机 MySQL 实例 + 独立测试库 + Flyway + 每个测试类事务回滚 | 与生产版本/排序规则可能有差异；并发测试互相干扰；CI 上不复现 | 生产数据库版本或扩展影响 SQL；测试涉及锁/并发；CI 失败本地无法复现；需要多数据库方言 |

### 13.6 SonarQube 降级路径

| 替代做法 | 能覆盖 | 不能覆盖 | 什么时候值得部署 |
|---|---|---|---|
| IDEA 内置检查 + SonarLint 离线规则 + SpotBugs/PMD Maven 插件 | 单文件/单模块的风格、缺陷、坏味道 | 历史趋势、质量门禁、团队统一报表、跨项目指标 | ≥10 人或 ≥5 个项目，且有专人维护平台 |

**必须**：**没有 SonarQube 不代表不做静态检查**；两者不能混为一谈。L1 的插件检查仍是合并门槛。

### 13.7 工具怎么用（入门）

| 工具 | 解决什么问题 | 结果在哪看 | 最小起步 | 常见误用 |
|---|---|---|---|---|
| JUnit 5 | 写单元测试 | IDEA 测试树绿色/红色；`target/surefire-reports/` | 方法加 `@Test` + `assertEquals` | 一个用例断言多个主题、依赖执行顺序 |
| Mockito | 隔离外部依赖 | 同 JUnit | `@Mock` + `when(...).thenReturn(...)` | Mock 一切、验证实现细节 |
| Spring Boot Test | 启动切片/上下文 | IDEA 测试树；日志 | `@SpringBootTest` + `@ActiveProfiles("test")` | 每个测试都启全上下文 |
| Flyway | 管理数据库结构版本 | 启动日志；`flyway_schema_history` 表 | `src/main/resources/db/migration/V1__init.sql` | 改历史脚本、生产执行 clean |
| Checkstyle | 代码风格统一 | `target/checkstyle-result.xml`；IDEA 面板 | `mvn checkstyle:check` | 规则过严、每次全量跑 |
| SpotBugs | 字节码缺陷（空指针、资源未关闭） | `target/spotbugsXml.xml`；IDEA 面板 | `mvn spotbugs:check` | 忽略高优先级、不做存量基线 |
| PMD | 代码坏味道 | `target/pmd.xml`；IDEA 面板 | `mvn pmd:check` | 规则重复、噪声多不调优 |
| SonarLint | 实时规则提示 | IDEA 侧栏 | 装插件，使用离线规则集 | 当成 SonarQube、规则与平台不同步 |
| Maven surefire / failsafe | 单测 / 集成测试 | `target/surefire-reports/`、`target/failsafe-reports/` | `mvn test` / `mvn verify` | 集成测试放 surefire、用 `-DskipTests` 常态跳过 |
| Jacoco | 覆盖率 | `target/site/jacoco/index.html` | `mvn test jacoco:report` | 追求高覆盖率、无断言测试 |

### 13.8 规则 → 工具映射

| 规则区间 | 主题 | 能否自动检查 | 可用工具 | AI 必须自判 |
|---|---|---|---|---|
| `BE-ARCH-001..006` | 依赖/跨层 | 部分 | ArchUnit、依赖分析 | 调用链语义、模块边界 |
| `BE-CON-001..010` | 契约实现 | 部分 | OpenAPI diff、生成器 | 错误码归属、契约语义 |
| `BE-ERR-001..008` | 异常 | 部分 | SpotBugs、PMD | 异常语义、可重试分类 |
| `BE-LOG-001..008` | 日志 | 部分 | PMD、自定义规则 | 脱敏是否充分、字段语义 |
| `BE-TX-001..009` | 事务 | 部分 | ArchUnit、PMD | 事务边界是否符合业务 |
| `BE-VAL-001..007` | 校验 | 部分 | Bean Validation 静态检查 | 业务不变量、校验归属 |
| `BE-IDEM-001..006` | 幂等 | 否 | 无 | 指纹设计、重复策略 |
| `BE-CONC-001..007` | 并发 | 部分 | SpotBugs、PMD | 异步与事务语义 |
| `BE-DB-001..014` | 数据库 | 大部分 | Checkstyle、PMD、SQL 扫描 | 索引合理性、慢查询判断 |
| `BE-CACHE-001..009` | 缓存 | 部分 | PMD | 一致性策略 |
| `BE-SEC-001..016` | 安全 | 部分 | SpotBugs、依赖扫描、SonarLint | 越权与权限模型 |
| `BE-API-001..008` | 接口 | 部分 | OpenAPI diff | 兼容性语义 |
| `BE-CFG-001..011` | 配置 | 部分 | 配置扫描、台账解析 | 敏感项是否外置、消费点是否真实、字典改动是否有业务标注 |
| `BE-TEST-001..011` | 测试 | 部分 | surefire、failsafe、Jacoco | 用例有效性 |
| `BE-HUMAN-001..006` | 人工确认 | 否 | 无 | 全部 |

### 本章自检清单

- [ ] `BE-TOOL-001` 交付报告基于规则自检（MUST, CONTEXT）
- [ ] `BE-TOOL-002` 未装工具不构成跳过理由（MUST, CONTEXT）
- [ ] `BE-TOOL-003` L0 无 Docker/无服务端组件可跑（MUST, STATIC）
- [ ] `BE-TOOL-004` L1 按改动文件/模块跑（SHOULD, PATTERN）
- [ ] `BE-TOOL-005` Testcontainers 仅在触发条件下引入（SHOULD, CONTEXT）
- [ ] `BE-TOOL-006` SonarQube 仅在触发条件下部署（SHOULD, CONTEXT）
- [ ] `BE-TOOL-007` 无 SonarQube 仍有静态检查（MUST, STATIC）
- [ ] `BE-TOOL-008` 规则-工具映射已更新（MUST, PATTERN）

---

## 14. 必须人工确认的规则（BE-HUMAN）

> 本章规则 AI **不得默认通过**。命中时必须把“需人工确认”写入交付报告，并说明确认点。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-HUMAN-001` | MUST | CONTEXT | 命名在业务语义上准确，且经需求方确认 | 如果 AI 只校验语法命名（大小写、前缀），就宣称命名通过，则判为不通过 | 报告写“`settleAmount` 语义需业务确认” | 报告直接写“命名符合规范，通过” | 语法正确的名字也可能与业务含义相反 |
| `BE-HUMAN-002` | MUST | CONTEXT | 事务边界与业务预期一致（哪些操作必须同成败） | 如果 AI 按“方法上有注解”判定事务通过，则判为不通过 | 列出“扣减与记账必须同一事务”待确认 | 只检查 `@Transactional` 是否存在 | 事务边界是业务决策，不是语法问题 |
| `BE-HUMAN-003` | MUST | CONTEXT | 权限模型覆盖实际角色与数据范围（含越权路径） | 如果只校验注解存在、未核对角色清单，则判为不通过 | 报告列出角色 × 操作矩阵待确认 | 看到 `@PreAuthorize` 即判通过 | 注解齐全仍可能漏角色或漏数据范围 |
| `BE-HUMAN-004` | MUST | CONTEXT | 数据保留、归档、删除策略符合合规与业务要求 | 如果 AI 自行决定保留期或删除方式，则判为不通过 | 报告标注“保留期需法务/业务确认” | 代码里写死 30 天并称符合规范 | 保留期涉及合规，不能由 AI 推断 |
| `BE-HUMAN-005` | MUST | CONTEXT | 性能与容量阈值（响应时间、批量大小、分页上限）经确认 | 如果 AI 自定阈值并宣称满足性能要求，则判为不通过 | 报告列出阈值假设待确认 | 直接写“性能达标” | 无基线时任何数字都只是猜测 |
| `BE-HUMAN-006` | MUST | CONTEXT | 接口语义、状态流转与交互习惯符合业务方预期 | 如果 AI 仅按契约字段校验就宣称语义正确，则判为不通过 | 报告列出状态流转待确认 | 字段齐全即判通过 | 字段齐全不等于流程合理 |

### 本章自检清单

- [ ] `BE-HUMAN-001` 业务语义命名已列入待确认（MUST, CONTEXT）
- [ ] `BE-HUMAN-002` 事务边界已列入待确认（MUST, CONTEXT）
- [ ] `BE-HUMAN-003` 权限与数据范围已列入待确认（MUST, CONTEXT）
- [ ] `BE-HUMAN-004` 保留/删除策略已列入待确认（MUST, CONTEXT）
- [ ] `BE-HUMAN-005` 性能容量阈值已列入待确认（MUST, CONTEXT）
- [ ] `BE-HUMAN-006` 接口语义与状态流转已列入待确认（MUST, CONTEXT）

---

## 15. AI 交付前自检流程（BE-FLOW）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `BE-FLOW-001` | MUST | PATTERN | 只读本次改动涉及的章节区间，不全量加载全部规则 | 如果自检报告引用了与改动无关章节的规则，则判为不通过 | 改 Service 只读 §4、§5、§7 | 每次把 §1–§16 全跑一遍 | 全量读会稀释注意力并拖慢交付 |
| `BE-FLOW-002` | MUST | STATIC | 逐条记录 `通过 / 不通过 / 需人工确认`，并带 `文件:行号` | 如果只给总体结论、无逐条记录，则判为不通过 | `BE-ERR-002 ／ 不通过 ／ OrderService.java:88` | “已自检，符合规范” | 无逐条记录无法复核，也无法定位 |
| `BE-FLOW-003` | MUST | CONTEXT | 不通过项自我修正，最多迭代两轮；仍不通过则如实上报 | 如果无限循环修改或隐瞒未解决项，则判为不通过 | 第二轮仍失败 → 列未解决项 | 反复改到“看起来能过” | 两轮后通常是设计问题，需要人决策 |
| `BE-FLOW-004` | MUST | STATIC | 交付报告格式固定，通过项只汇总数量 | 如果逐条罗列通过项、淹没失败项，则判为不通过 | “通过 21 项；不通过 2 项如下” | 把 30 条通过项全部展开 | 报告的目的是暴露风险 |
| `BE-FLOW-005` | MUST | CONTEXT | 不得为过检查删功能、吞异常、放宽校验、注释测试、降级规则 | 如果出现上述任一行为，则判为不通过 | 修复实现而不是改规则 | 把 MUST 改成 SHOULD 求通过 | 检查的目的是质量，不是绿灯 |
| `BE-FLOW-006` | MUST | CONTEXT | 存量文件只对改动范围负责，不做全文件重写 | 如果借自检之名重写无关代码，则判为不通过 | 只改受影响方法及其调用点 | 顺手重构整个类 | 扩大改动面会引入不可控回归 |

### 15.1 六个步骤

1. **定位规则**：按改动分层与模块，只读对应章节区间（见 §17 索引），记录本次适用的规则编号清单。
2. **生成/修改代码**：按规则写实现，不先写“待补”。
3. **逐条比对**：对每条适用规则给出 `通过 / 不通过 / 需人工确认`，`STATIC`/`PATTERN` 由 AI 判定，`CONTEXT` 无把握即转人工。
4. **自我修正**：仅对“不通过”项修正，最多两轮；第二轮仍失败则停止并上报。
5. **输出报告**：固定四列 `规则编号 | 结论 | 文件:行号 | 说明`；通过项只写总数；需人工确认项单列。
6. **禁止作弊**：不得删减功能、吞异常、放宽校验、跳过必填、注释测试、把 `MUST` 降为 `SHOULD`。

### 15.2 新建文件与存量文件的差异

| 场景 | 责任范围 | 自检要求 |
|---|---|---|
| 新建文件 | 全文件 | 全部适用规则逐条判定；结构、命名、分层一次到位 |
| 修改存量文件 | 仅改动行及其直接影响的方法/调用点 | 只判定改动范围；不因旧代码不合规而扩大重写；发现旧问题记入“存量问题”但不阻塞本次 |

### 15.3 报告模板

```text
自检报告
适用模块：`BE-TX`、`BE-VAL`、`BE-DB`
通过：18 项（汇总，不逐条展开）

| 规则编号 | 结论 | 文件:行号 | 说明 |
|---|---|---|---|
| BE-TX-004 | 不通过 | OrderAppService.java:132 | 事务内含远程调用 |
| BE-VAL-003 | 需人工确认 | OrderCreateRequest.java:24 | 字段长度上限需业务确认 |

未解决项：BE-TX-004 需拆分远程调用，涉及接口调整，待确认后再改。
```

### 本章自检清单

- [ ] `BE-FLOW-001` 只读相关章节区间（MUST, PATTERN）
- [ ] `BE-FLOW-002` 逐条记录结论与行号（MUST, STATIC）
- [ ] `BE-FLOW-003` 最多两轮迭代并如实上报（MUST, CONTEXT）
- [ ] `BE-FLOW-004` 报告格式固定、通过项只汇总（MUST, STATIC）
- [ ] `BE-FLOW-005` 未出现任何作弊式修改（MUST, CONTEXT）
- [ ] `BE-FLOW-006` 存量文件只对改动范围负责（MUST, CONTEXT）

---

## 16. 附录：AI 代码评审提示词

直接复制以下模板，替换占位内容后使用。

```text
你是后端代码评审员。规则来源：docs/specs/backend-spec.md（v1.3）。
检查范围：仅本次改动文件（清单见下），不要评审未改动代码，不要重写文件。
改动文件：
- <path>:<startLine>-<endLine>
- ...

要求：
1) 按改动所属分层定位模块，只读取相关规则区间（按 §17.1 索引定位）。
2) 逐条判定 MUST/SHOULD/MAY；STATIC、PATTERN 自行判定，CONTEXT 无把握标“需人工确认”。
3) 只输出“不通过”和“需人工确认”；通过项汇总为一句数量。
4) 输出表格：规则编号 | 级别 | 文件:行号 | 问题 | 修复建议。
5) 引用规则只写编号，不写章节号与行号；不得凭记忆编造，查不到就写“未在文档中查到，需确认”。
6) 不得建议通过删功能、吞异常、放宽校验、注释测试、降级 MUST 来消除问题。
7) 与《接口契约与错误码规范》冲突时，以契约为准。
```

### 本章自检清单

- [ ] 评审提示词包含规则来源与版本（MUST, STATIC）
- [ ] 评审提示词限定改动范围（MUST, STATIC）
- [ ] 评审提示词只报不通过与待确认项（MUST, STATIC）
- [ ] 评审提示词含防幻觉与防作弊要求（MUST, STATIC）

---

## 17. 规则编号索引与跨文档引用

### 17.1 编号索引

| 章节 | 模块前缀 | 编号区间 | 条数 | 自检清单 |
|---|---|---|---|---|
| §0 | —（约定） | 无编号 | 4 项 | 有 |
| §1 | `BE-ARCH` | 001–012 | 12 | 有 |
| §2 | `BE-CON` | 001–010 | 10 | 有 |
| §3 | `BE-ERR` / `BE-LOG` | 001–008 / 001–008 | 16 | 有 |
| §4 | `BE-TX` | 001–009 | 9 | 有 |
| §5 | `BE-VAL` / `BE-IDEM` | 001–007 / 001–006 | 13 | 有 |
| §6 | `BE-CONC` | 001–007 | 7 | 有 |
| §7 | `BE-DB` | 001–014 | 14 | 有 |
| §8 | `BE-CACHE` | 001–009 | 9 | 有 |
| §9 | `BE-SEC` | 001–016 | 16 | 有 |
| §10 | `BE-API` | 001–008 | 8 | 有 |
| §11 | `BE-CFG` | 001–011 | 11 | 有 |
| §12 | `BE-TEST` | 001–011 | 11 | 有 |
| §13 | `BE-TOOL` | 001–008 | 8 | 有 |
| §14 | `BE-HUMAN` | 001–006 | 6 | 有 |
| §15 | `BE-FLOW` | 001–006 | 6 | 有 |
| §16 | —（附录） | 无编号 | 4 项 | 有 |

**规则总数**：156 条（`BE-*`），另有 §0 与 §16 的 8 项约定型自检项。

### 17.2 跨文档引用

| 场景 | 写法 | 归属 |
|---|---|---|
| 响应结构、错误码、分页、时间与金额格式 | 引用 `CT-*`，不在本规范重复定义 | 契约文档 |
| 前端如何消费错误码与统一响应 | 前端文档引用 `CT-*` 与本规范 `BE-CON-*` | 契约 + 后端 |
| 交互与视觉判断 | 引用 `UI-PC-*` / `UI-H5-*` | UI 文档 |
| 规则冲突 | 只按 `AGENTS.md` §1 的优先级链处理 | `AGENTS.md` |

### 17.3 与其他文档的关系

| 文档 | 本规范提供 | 本规范索取 |
|---|---|---|
| 《接口契约与错误码规范》 | 契约的后端实现约束与检查项 | 字段、错误码、分页、时间格式的唯一口径 |
| 《前端编码规范》 | 可被引用的后端行为（错误映射、幂等、状态码） | 前端如何呈现错误与状态 |
| 《前端UI设计规范》 | 无 | 交互语义与状态展示的判定依据 |

### 本章自检清单

- [ ] 新增规则已登记到 §17.1 且编号连续（MUST, STATIC）
- [ ] 跨文档引用指向真实存在的编号（MUST, STATIC）
- [ ] 冲突只按 `AGENTS.md` §1 处理（MUST, CONTEXT）
- [ ] 规则总数与索引一致（MUST, STATIC）
