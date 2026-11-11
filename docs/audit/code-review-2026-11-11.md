# 代码级评审报告（后端 + 前端，只读）

| 项目 | 内容 |
|---|---|
| 日期 | 2026-11-11 |
| 范围 | `server/`（322 个 Java 文件 / 16,135 行）、`apps/pc` + `apps/shared`（67 个 ts/vue 文件 / 11,315 行） |
| 基线 | `docs/baseline/modules.md` v1.10、`docs/baseline/assertions.md` v1.16、`docs/api/openapi.yaml` v1.0.0 |
| 方法 | 全量结构扫描（grep 风险模式 + 文件行数分布）+ 关键链路精读；每条结论给出文件与行号，未读到的部分在 §6 明确标注 |
| 性质 | **只读评审**：本文件不修改任何代码，只给结论与建议；是否修复由人工按 `AGENTS.md` 变更档位决策 |
| 结论 | 骨架质量高于同规模脚手架平均水平（分层清楚、异常/分页/排序/路径/脱敏都有单点收口）。**主要风险集中在 3 处**：前端刷新令牌的排除范围过宽、文件越权返回 403 违反契约、质量门禁（测试/CI）实际上不存在 |

## 1. 缺陷清单（按严重度）

> 判定口径：P0 = 会导致数据错误或安全失守；P1 = 违反 `docs/specs` 的 MUST 或造成明显功能缺陷；P2 = 一致性与健壮性隐患；P3 = 可维护性。

### P1

| # | 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 1 | **所有 `/auth/*` 接口都跳过静默刷新**，导致 access 过期后刷新页面被直接登出，而 refresh 令牌仍有 7 天有效期 | `apps/pc/src/api/http.ts:103`（`!url.startsWith('/auth/')`）；被排除的接口见 `apps/pc/src/api/auth.ts:14-22`（`/auth/me`、`/auth/routes`、`/auth/permissions`）；`apps/pc/src/router/index.ts:122-131`（`bootstrap()` 失败即 `reset()` 跳登录） | 用户停手 15 分钟后刷新页面 = 重新登录；与设计文档「access 15 分钟 + refresh 7 天静默续期」不符，且被误判为「登录态丢失」 | 排除范围收窄为 `/auth/login`、`/auth/refresh`（logout 可保留排除）；401 时对 `/auth/me` 先尝试刷新一次再判定未登录 |
| 2 | **文件越权访问返回 403**，违反 `CT-HTTP-008`（越权访问统一 404，不暴露资源存在） | `server/system/.../application/FileAppService.java:383-392`（`requireVisible` 第二段抛 `FORBIDDEN`）；调用点 `server/web/.../controller/FileController.java:127`、`:143` | 形成存在性探测面：换 id 遍历即可区分「不存在(404)」与「存在但无权(403)」；`CT-ERR-009` 同类要求也被违反 | 二选一并留痕：①改为 `RESOURCE_NOT_FOUND`（推荐，符合 MUST）；②若业务确需 403，须开变更单并在 `docs/api/openapi.yaml` 显式声明为例外，同时补 `docs/baseline/assertions.md`（现状 `assertions.md` #36 记录的是 403，属规范与基线冲突，**需人工裁决**） |
| 3 | **质量门禁宣称有、实际没有**：无 CI、无 `mvnw`、全仓只有 1 个测试类 | `.github` 目录不存在；`server/mvnw` 不存在；`server/web/src/test/java/com/adminstarter/NotificationPublisherTransactionTest.java`（唯一测试） | `AGENTS.md` 交付门禁里「契约测试失败阻断」没有执行载体；任何重构回归只能靠人工点页面 | 最低成本：加一条 CI job 跑 `node tools/spec-lint/cli.mjs` + `mvn -q -DskipTests package` + 前端 `pnpm build`；契约测试按 `docs/api/examples/backend` 落地 1 条即可 |

### P2

| # | 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 4 | CSRF 校验**无 Origin 即放行**，且不回落 Referer；同时错误响应绕过统一信封（`traceId` 硬编码空串、无 `X-Trace-Id`） | `server/web/.../filter/OriginCheckFilter.java:36-46`；未使用的注入 `:27` | 老客户端/非浏览器调用可绕过写操作来源校验；违反 `CT-TRACE-002`（响应头与响应体 `traceId` 一致） | 无 Origin 且无同源 Referer 时按失败处理（可配置放行清单）；错误体改用 `ApiResponse` + `MDC.get("traceId")` 并补响应头 |
| 5 | **登录时账号停用返回 403 + `20301`**，与「停用 = 401 + `20103`」的分码体系不一致 | `server/auth/.../application/AuthAppService.java:52-55`；对照 `ErrorCode.ACCOUNT_DISABLED(20103, 401)`（`common/error/ErrorCode.java:18`） | 前端 `TERMINAL_AUTH_CODES`（`http.ts:42`）不含 20301，提示文案与「已被停用」语义错位；同一账号状态出现两种错误码 | 登录分支改抛 `ACCOUNT_DISABLED`，或把 20301 纳入前端终止码并在契约中写明「登录阶段的停用」 |
| 6 | **`page ≤ 0` 未校验**，各仓储兜底行为不一致 | `common/api/PageQuery.java:22-24`（`resolvePage` 直接返回原值）；`UserRepositoryImpl.java:112`（交给 MyBatis-Plus `Page`，内部把非正数归零→返回首页）；`CacheAdminAppService.java:73-76`（同参数手工抛 400） | 同一 `?page=0` 在不同接口分别表现「静默返回首页」与「400」；使用自定义 `offset` 的查询会算出负偏移 | 在 `resolvePage()` 统一校验 `page >= 1`（与 `resolvePageSize` 对齐），删除各服务的重复校验 |
| 7 | **定时任务时区取 JVM 默认时区**，与库/Jackson 的 UTC 约定混用 | `server/system/.../application/job/JobScheduler.java:28`（`ZoneId.systemDefault()`）、`:87`（`LocalDateTime.now(ZONE)` 后 `toUtc`）；`application.yml:38`（`jackson.time-zone: UTC`） | 部署到 UTC 容器（Docker 默认）时 cron 整体偏移 8 小时；「下次执行时间」展示与实际触发可能相差数小时 | 增加显式配置（如 `app.job.zone`）并进配置台账；启动期打印生效时区 |
| 8 | 线程池/调度器在构造器里手工 `initialize()`，绕过 Spring 生命周期管理 | `JobScheduler.java:35-43`（`new ThreadPoolTaskScheduler()`）；`application/job/JobExecutor.java:64`（`ThreadPoolTaskExecutor`）；`export/ExportExecutorConfig.java:15` | 优雅停机顺序不可控（`@PreDestroy` 仅关调度器，`JobExecutor` 线程池未显式关闭）；线程池参数散落代码，无法配置化 | 统一改为 `@Bean` 定义线程池，参数进 `application.yml`；停机时 `setWaitForTasksToCompleteOnShutdown(true)` + 超时 |
| 9 | **分片续传凭据存 localStorage 且未按用户隔离** | `apps/pc/src/composables/use-chunk-upload.ts:70`（读 `resumeKey(sha256,size)`）、`:102`（写）、`:122`（删） | 同一浏览器换账号后可能命中他人 `uploadId`，接口按 `uploader_id` 校验后返回 403，页面表现为「续传莫名失败」；键还可能长期残留 | 键名加用户标识（如 `userId`），或在 403 时清除本地记录并重开上传 |
| 10 | 异步导出未校验导出人是否仍可用 | `server/system/.../application/export/ExportTaskExecutor.java:97-109`（`loadCurrentUser` 只判 `user == null`，异常文案写「已停用」但无 `isEnabled` 判断） | 停用/删除前受理的导出任务仍按原权限生成文件并可下载 | 加 `isEnabled` 校验：不通过则任务置 `FAILED` 并通知，避免「已停用账号仍产出数据」 |
| 11 | CSP 类响应头缺失（仅有 `nosniff`） | `FileController.java:134`、`:151` 只设 `X-Content-Type-Options` | 预览文件若被浏览器嗅探出 HTML 特征，缺少兜底策略头 | 网关或 `SecurityConfig` 统一加 `Content-Security-Policy: default-src 'none'` 于下载/预览响应 |

### P3（可维护性，不影响正确性）

| # | 问题 | 证据 |
|---|---|---|
| 12 | 单页体量偏大，`script` 逻辑与模板耦合，改一处要读全文 | `apps/pc/src/views/system/job/index.vue` 676 行、`notice` 647、`role` 575、`user` 557（同类页面结构高度重复，可抽「筛选区 + 表格 + 抽屉」三段） |
| 13 | 编辑抽屉回填方式不统一：部分页面先查详情，部分直接用列表行 | 查详情：`user/index.vue:190`、`demo/task/index.vue:125`、`role/index.vue:171,204`；直接用行数据：`menu/index.vue:161`、`config/index.vue:79`、`job/index.vue:152`、`dept/index.vue:159`、`post/index.vue:91`（一旦列表 DTO 裁剪字段就会丢值） |
| 14 | 权限码字符串在前后端各写一份 | 后端注解（如 `UserController.java:32`）与前端 `v-permission`（如 `user/index.vue:391`）各自硬编码；仪表盘快捷入口同（`system-modules.md` 已登记为已知限制） |
| 15 | `MaskResolver.permission` 用字符串拼接生成权限码 | `common/masking/MaskResolver.java:46-48`；字段名含冒号或大小写不一致时会静默匹配不到（当前 `user:phone`/`user:email` 正常） |

## 2. 已核对且判定「设计正确、勿改」的实现

这些点在评审中重点怀疑过，读代码后确认是**有意为之**，改动会引入回归（与 `docs/baseline/assertions.md` 一致）：

| 机制 | 证据 | 为什么不能按「常见做法」改 |
|---|---|---|
| 异常 → 信封与状态码的唯一映射 | `web/config/GlobalExceptionHandler.java:42-164`（业务/校验/413/404/403/未知全覆盖，校验码翻译成 `REQUIRED/INVALID_FORMAT/TOO_LONG`） | 各处自行 `try/catch` 会破坏 `CT-ERR-006` 与 `CT-HTTP-003` |
| 排序字段白名单 | `infrastructure/persistence/SortSupport.java:13-19` | 直接拼 `sort` 进 `ORDER BY` 就是注入面 |
| 数据权限条件只拼数值 ID | `DataScopeSqlBuilder.java:69-71`（`joinIds` 全为 `Long`）；空条件 `1 = 0`（`:37`、`:52`） | 改成「空条件放行」等于越权 |
| 未登记表直接报错 | `DataPermissionTables.java:77-89`；`Kind.UNKNOWN` 分支 | 改成默认放行会静默越权 |
| 本地存储路径穿越防护 | `infrastructure/storage/LocalFileStorage.java:180-189`（拒 `..` + `normalize` + `startsWith(root)`） | 少任一条件即可读任意文件 |
| Cookie 属性 | `web/auth/AuthCookieService.java:41-49`（`httpOnly` + `secure` 可配 + `sameSite` + refresh 限路径） | 令牌放 JS 可读处会放大 XSS 影响 |
| 脱敏 fail-closed | `common/masking/MaskResolver.java:15-34`（无上下文→HIDDEN，超管不豁免）；「读自己」用请求级属性而非 ThreadLocal | 超管特判或全局豁免都会泄露他人字段 |
| 导出任务越权 | `export/ExportTaskService.java:181-183`（`requireOwned`）、`:99-117`（文件按 `userId/taskId` 定位）；`DemoTaskController.java:170-171` | 去掉归属校验即可下载他人导出文件 |
| 导出线程重建上下文 | `export/ExportTaskExecutor.java:57`、`:82-84`（`DataScopeContext.set` + `finally clear`） | 不重建 → 数据范围退化为空条件、脱敏全隐藏 |
| 列表页 composable 的错误契约 | `composables/use-paged-list.ts:65-84`、`use-crud-drawer.ts:70-89`、`use-field-errors.ts:29-41` | 与 `assertions.md` #44/#45 的「422 回填 + 需 toast 时 rethrow」一致 |

## 3. 接口面与契约覆盖

| 项 | 数值 | 说明 |
|---|---|---|
| Controller | 20 个 | `web/controller/*.java` |
| 端点注解 | 122 处 | `@Get/Post/Put/DeleteMapping` |
| 权限注解覆盖 | 122 个端点中除 `/auth/*`、`/profile/*`、`/my-notices/*`、`/public/*`、`/health` 外均有 `@PreAuthorize` | 未加注解的均为「登录态即可」的接口，与 `system-modules.md` 一致 |
| 手写契约 | 53 path / 60 operation | `docs/api/openapi.yaml`（按 `AGENTS.md` §4.4 豁免，非全量） |
| 实现基线 | 93 path | `docs/api/openapi.generated.yaml` |
| **缺口** | 约 33 path 无手写契约、无 `x-status` | 不影响运行，但对外交付或第三方联调前必须补齐 |

## 4. 与规范/基线的一致性核对

| 结论 | 条目 | 依据 |
|---|---|---|
| **违反（需裁决）** | `CT-HTTP-008` | 文件越权返回 403，见缺陷 #2 |
| **违反（轻微）** | `CT-ERR-009` | 同上：越权与不存在返回不同码，构成存在性探测 |
| **违反（轻微）** | `CT-TRACE-002` | `OriginCheckFilter` 手工写响应体，`traceId` 为空且无 `X-Trace-Id`，见缺陷 #4 |
| 一致 | `CT-PAGE-002` / `CT-PAGE-003` | `PageQuery.java:26-37` 上限 100、超限 400 不截断 |
| 一致 | `CT-RESP-001` / `CT-RESP-009` | `common/api/ApiResponse.java`；无 204 |
| 一致 | `BE-DB-006` | `SortSupport` 白名单 |
| 一致 | `BE-SEC-003` | 越权判定与功能权限分离（除文件 403 一处） |
| 一致 | `docs/baseline/assertions.md` #14/#16/#19/#26/#28/#36/#40/#53–#58/#63/#64 | 逐条对照代码，行为与断言描述一致 |

## 5. 建议的修复顺序（按性价比）

1. 缺陷 #1（前端刷新排除范围）—— 改 2 行，收益最大。
2. 缺陷 #3（CI 三件套）—— 一次性投入，之后所有改动都有兜底。
3. 缺陷 #2（文件 403→404）—— 需先裁决契约与基线冲突，再改 3 行 + 回归下载/预览/回收站。
4. 缺陷 #4、#5（信封与登录分码）—— 影响面小，可合并一次提交。
5. 缺陷 #6–#11 —— 排入下一个迭代，逐条配断言回填。

## 6. 本次未覆盖（诚实声明）

- 未逐行通读全部 322 个 Java 文件与 67 个前端文件：精读集中在鉴权链路、数据权限、脱敏、文件、导入导出、定时任务、缓存、异常与请求层；业务 CRUD 服务（字典/菜单/岗位/角色/通知）按抽样 + 结构扫描校验，未逐方法评审 SQL 与索引命中。
- 未做运行时验证（本轮不启服务、不连库）：所有结论来自静态代码与基线文档比对；涉及「实测才可知」的行为（如 403/404 的真实响应体、时区偏移的实际表现）已在建议中标注需回归。
- 未评估 UI 视觉合规（`docs/specs/ui-spec.md`）与无障碍：需浏览器实测，属另一类评审。
- 未评估性能与容量：仅引用既有实测（`docs/audit/perf-2w-2026-11-10.md`），未新做压测。

## 7. 自检

- [x] 每条缺陷可定位到文件与行号（MUST, STATIC）
- [x] 区分「缺陷」与「有意设计」（MUST, STATIC）
- [x] 未在本轮修改任何代码或规范（MUST, CONTEXT）
- [x] 未覆盖范围已显式声明，未用「已全面评审」表述（MUST, STATIC）

## 8. 处理记录（2026-11-11）

用户裁决：缺陷 #2 按方案 A 处理（实现向 CT-HTTP-008 回归，越权与不存在统一 404）。本轮落地的三项：

| 缺陷 | 处理 | 落地位置 |
|---|---|---|
| #1 前端刷新范围过宽 | 401 静默刷新的排除范围由「所有 /auth/*」收窄为「/auth/login、/auth/refresh」；其余 /auth/*（me/routes/permissions/sessions）先尝试刷新 | `apps/pc/src/api/http.ts`（`isAuthEntry`） |
| #2 文件越权 403 | `requireVisible` 两处改为 `RESOURCE_NOT_FOUND` + 统一文案「文件不存在或无权访问」；同步修正断言 #36、`docs/design/file-storage.md`（原「403 需求硬要求」作废）、契约预览描述并升 1.0.1 | `server/system/.../application/FileAppService.java` |
| #3 无质量门禁 | 新增 CI 三 job（spec-lint / 后端编译 / 前端构建）；后端暂 `-DskipTests`，因唯一测试依赖真实 MySQL/Redis | `.github/workflows/ci.yml` |

未处理（保留在清单中）：P2 的 #4–#11 与 P3 全部，按报告 §5 的修复顺序排入后续迭代。