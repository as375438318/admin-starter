# 定时任务（设计 v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（2026-11-09） |
| 状态 | APPROVED（按用户 2026-11-09 批准的范围实施） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | to-be 设计：调度选型、动态 cron、并发/超时/重试、失败通知、单机限制、安全红线；实现与实测见 `docs/audit/impl-notes.md` |
| 依赖 | `docs/design/notification.md` §8（`NotificationPublisher`）、`docs/baseline/assertions.md` #2/#6/#19 |

## 1. 范围

- 任务定义：名称、分组、cron 表达式、描述、状态（启用/停用）、并发策略（禁止并发/允许并发）、超时时间、失败重试次数与间隔。
- 执行能力：动态 cron 生效（改表达式不用重启）、手动执行一次、暂停/恢复、查看下次执行时间。
- 执行日志：任务名、触发方式（自动/手动）、开始/结束时间、耗时、执行结果、异常摘要与堆栈、影响条数。
- 失败通知：接已完成的通知模块 `NotificationPublisher`；单次失败或连续失败按任务配置发站内信，通知发给任务创建人或指定人。
- 权限码沿用 `system:` 前缀，菜单挂「系统管理」下。

## 2. 现状（2026-11-09 核对）

| 事实 | 结论 |
|---|---|
| 库内无任何定时任务表 | 本轮为**新增模块**，不改造既有表 |
| 代码内无 `@Scheduled` / Quartz / XXL-JOB | 本轮从零建立调度能力，不引入新依赖（`spring-context` 已随 Spring Boot 提供） |
| 当前最高迁移版本 V10 | 本轮只新增 `V11`，不改 V1–V10 |
| `DataPermissionTables` 未登记表在受控查询中直接报错 | 两张新表必须显式登记（`assertions.md` #2） |
| `NotificationPublisher.publishInNewTransaction` 为 REQUIRES_NEW | 任务失败通知直接复用，业务（任务）失败不影响通知落库 |
| 部署为单实例 | 调度只在进程内生效；多实例会重复执行，见 §8 |

## 3. 调度选型

| 方案 | 结论 |
|---|---|
| **Spring `ThreadPoolTaskScheduler` + `CronTrigger`（采用）** | 零新增依赖；每个任务持有独立 `ScheduledFuture`，改 cron 时 `cancel` 后按新表达式重建，天然支持“不重启生效”；单机场景足够 |
| `@Scheduled` 注解（否决） | cron 与代码绑定，无法在后台增删改；动态生效需要自研反射，不如直接持有 `ScheduledFuture` |
| Quartz（否决） | 为“集群/持久化调度”设计，需额外表与配置；本轮单机，收益不抵复杂度。将来上集群可作为候选，见 §8 |
| XXL-JOB 等外部调度器（否决） | 需要独立部署调度中心，超出本轮范围 |

- cron 使用 Spring 6 段格式：`秒 分 时 日 月 周`（例：`0 * * * * *` 表示每分钟第 0 秒）。
- cron 按**显式配置的时区**解析：`app.job.zone`（默认 `Asia/Shanghai`，2026-11-11 起不再取 JVM 默认时区）；库内 `next_run_at` 等时间仍按 UTC 存储，接口输出 ISO-8601。部署到 UTC 容器时不需要改代码，只改这项配置。
- 调度器与执行器的线程池由 `JobSchedulingConfig` 以 `@Bean` 装配（`app.job.scheduler-pool-size` / `runner-*` / `task-*`），由 Spring 负责 initialize 与停机；禁止在构造器里 `new` 后手工初始化。
- 不使用 Quartz 的 7 段（含年）格式；界面上给出示例，保存时用 `CronExpression.parse` 校验，非法表达式返回 `400`。

## 4. 安全红线（不得放宽）

- 任务只允许按**已注册的任务标识**（`job_key`）调度。
- 任务实现必须是代码中注册的 Spring Bean（实现 `ScheduledJob` 接口）；`job_key` 即注册标识。
- 数据库只存标识、cron、超时/重试/通知等配置；**禁止**存脚本、Shell 命令、SQL 字符串、类名、SpEL 表达式。
- 创建/修改任务时校验 `job_key` 必须存在于注册表；执行时再次校验，未注册标识直接记 `FAIL`（“任务标识未注册”），不尝试任何动态加载。
- 本轮**不提供通用参数列**：框架不解释参数是更小的攻击面。将来确有需求时，按“Bean 自声明白名单参数、框架只透传字符串”的方案扩展，仍禁止存可执行内容。
- 若实现中发现必须放宽（例如允许动态类名或脚本），先停机说明，不自行放行。

## 5. 数据模型

### 5.1 `sys_job`（任务定义）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(64) NOT NULL | 任务名称 |
| `group_name` | VARCHAR(64) NOT NULL DEFAULT 'DEFAULT' | 分组（自由文本，用于筛选） |
| `job_key` | VARCHAR(128) NOT NULL | **已注册任务标识**，与 `ScheduledJob.key()` 对应 |
| `cron_expression` | VARCHAR(64) NOT NULL | Spring 6 段 cron |
| `description` | VARCHAR(255) NULL | 描述 |
| `status` | VARCHAR(16) NOT NULL DEFAULT 'DISABLED' | `ENABLED` 启用 / `DISABLED` 停用 |
| `concurrency_policy` | VARCHAR(16) NOT NULL DEFAULT 'DISALLOW' | `DISALLOW` 禁止并发 / `ALLOW` 允许并发 |
| `timeout_seconds` | INT NOT NULL DEFAULT 60 | 单次执行超时（1–3600） |
| `retry_times` | INT NOT NULL DEFAULT 0 | 失败后的重试次数（0–10，不含首次执行） |
| `retry_interval_seconds` | INT NOT NULL DEFAULT 10 | 重试间隔（0–3600） |
| `notify_policy` | VARCHAR(32) NOT NULL DEFAULT 'NEVER' | `NEVER` / `ON_FAILURE` 单次失败 / `ON_CONSECUTIVE` 连续失败 |
| `notify_threshold` | INT NOT NULL DEFAULT 3 | 连续失败阈值（1–10），仅 `ON_CONSECUTIVE` 生效 |
| `notify_targets` | VARCHAR(255) NULL | 指定接收人 id，逗号分隔；为空表示任务创建人 |
| `last_run_at` | DATETIME NULL | 最近一次执行开始时间（UTC） |
| `last_run_result` | VARCHAR(16) NULL | `SUCCESS` / `FAIL` / `TIMEOUT` / `SKIPPED` |
| `next_run_at` | DATETIME NULL | 下次自动执行时间（UTC）；停用时为 NULL |
| `create_by` | BIGINT NULL | 创建人（失败通知默认接收人） |
| `created_at` / `updated_at` / `deleted` | | 与既有表一致 |

- 唯一约束：`uk_job_key (uk_job_key)`，其中 `uk_job_key` 为 STORED 生成列 `IF(deleted = 0, job_key, NULL)`，与 V7 的软删除重建方案一致（`assertions.md` #3）。
- 索引：`idx_job_status (status)`、`idx_job_group (group_name)`。
- 数据权限：`sys_job` 登记为 `EXEMPT`（系统级调度配置，无部门归属语义；进入页面已由 `system:job:list` 拦截）。

### 5.2 `sys_job_log`（执行日志）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `job_id` | BIGINT NOT NULL | 任务 id |
| `job_name` | VARCHAR(64) NOT NULL | 任务名快照（任务改名/删除后日志仍可读） |
| `job_key` | VARCHAR(128) NOT NULL | 任务标识快照 |
| `trigger_type` | VARCHAR(16) NOT NULL | `AUTO` 自动 / `MANUAL` 手动 |
| `started_at` | DATETIME NOT NULL | 开始时间（UTC） |
| `finished_at` | DATETIME NOT NULL | 结束时间（UTC） |
| `elapsed_ms` | BIGINT NOT NULL | 耗时（毫秒，含重试间隔） |
| `result` | VARCHAR(16) NOT NULL | `SUCCESS` / `FAIL` / `TIMEOUT` / `SKIPPED` |
| `attempt_count` | INT NOT NULL DEFAULT 1 | 实际执行次数（首次 + 重试） |
| `error_summary` | VARCHAR(255) NULL | 异常摘要（类名 + 消息，截断） |
| `error_stack` | TEXT NULL | 异常堆栈（截断至 8000 字符） |
| `affected_rows` | BIGINT NULL | 任务返回的影响条数 |
| `created_at` | DATETIME NOT NULL | 落库时间（UTC） |

- 审计数据不做软删除，无 `deleted` / `updated_at`，与 `sys_login_log` 同口径。
- 索引：`idx_job_log_job_started (job_id, started_at)`、`idx_job_log_started (started_at)`、`idx_job_log_result (result)`。
- 数据权限：`sys_job_log` 登记为 `EXEMPT`。

## 6. 执行语义

### 6.1 调度与动态 cron

- 启动时（`ApplicationReadyEvent`）加载所有 `status=ENABLED` 的任务，逐个注册 `CronTrigger` 并写回 `next_run_at`。
- 新增/修改/启用：先取消旧 `ScheduledFuture`，再按当前 cron 注册新 future；停用/删除：取消 future 并将 `next_run_at` 置空。
- 修改 cron 不重启即生效；失败重试与超时不改变 cron 注册。
- 自动触发写入 `trigger_type=AUTO`；手动执行写入 `trigger_type=MANUAL`、不改变 `next_run_at`。

### 6.2 并发策略

| 策略 | 行为 |
|---|---|
| `DISALLOW`（默认） | 同一任务同一时刻只允许一个执行；已在执行时，自动触发记 `SKIPPED`，手动触发返回 `409`「任务正在执行中」 |
| `ALLOW` | 允许同一任务并发执行；互不影响，各自写日志 |

- 并发判断是**进程内** `ConcurrentHashMap<Long, AtomicInteger>` 计数（`getAndIncrement` 判重），见 §8 的单机限制。

### 6.3 超时

- 任务实现提交到独立线程池，调用方最多等待 `timeout_seconds`；超时后 `Future.cancel(true)` 并记 `TIMEOUT`。
- **限制**：Java 线程中断是协作式的。任务若不响应中断（死循环、阻塞在不可中断 IO），线程可能继续运行，日志只能标记“已超时”，不能强制杀死。任务实现应避免不可中断阻塞。
- 超时不参与重试（超时视为一次失败，若配置了重试仍会重试，语义上“该次尝试失败”）。

### 6.4 重试

- `retry_times` 为**首次执行之外**的重试次数；实际执行次数上限 = `1 + retry_times`。
- 每次失败后等待 `retry_interval_seconds` 再重试；任一次成功即停止并记 `SUCCESS`。
- 一次触发只写**一行**日志，`attempt_count` 记录实际执行次数；`error_summary` / `error_stack` 取最后一次失败。
- 重试间隔不计入“单次执行”超时，但计入日志 `elapsed_ms`。

### 6.5 失败通知

- 使用 `NotificationPublisher.publishInNewTransaction`（`REQUIRES_NEW`）：通知落库与任务执行无事务耦合，任务失败/超时也保证通知发出。
- 策略：
  - `NEVER`：不发。
  - `ON_FAILURE`：`result=FAIL` 或 `TIMEOUT` 时每次都发。
  - `ON_CONSECUTIVE`：从最近日志倒序统计连续非成功次数（含本次），达到 `notify_threshold` 才发。
- 接收人：`notify_targets` 非空时逐个 `Scope.ofUser(id)`；为空时发给 `create_by`。两者都没有则跳过并记 warn 日志，不阻断任务收尾。
- 通知内容：标题「定时任务执行失败：{任务名}」，正文含任务标识、触发方式、执行次数、异常摘要、时间；`type=TASK`，级别单次失败 `IMPORTANT`、达到连续阈值 `URGENT`。
- 通知发送失败（例如接收人已被删除/停用）只记应用日志，不影响任务结果与日志落库。

### 6.6 日志落库

- 执行结束后立即写 `sys_job_log`，写库使用 `REQUIRES_NEW` 独立事务；日志写失败不回滚任务（任务已执行完），但会记 error。
- `SKIPPED` 同样写日志（`trigger_type=AUTO`），便于观察并发策略生效。

## 7. 接口清单

### 7.1 任务管理（`/api/v1/jobs`）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/v1/jobs` | `system:job:list` | 分页列表（关键词、分组、状态） |
| GET | `/api/v1/jobs/keys` | `system:job:list` | 已注册任务标识与说明（新建表单下拉） |
| POST | `/api/v1/jobs` | `system:job:create` | 新建任务 |
| PUT | `/api/v1/jobs/{id}` | `system:job:update` | 修改任务（含 cron 热生效） |
| PUT | `/api/v1/jobs/{id}/status` | `system:job:status` | 启用/停用（暂停/恢复） |
| POST | `/api/v1/jobs/{id}/run` | `system:job:run` | 手动执行一次（同步等待，返回本次结果） |
| DELETE | `/api/v1/jobs/{id}` | `system:job:delete` | 删除任务（取消调度，逻辑删除） |

### 7.2 执行日志（`/api/v1/job-logs`）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/v1/job-logs` | `system:job:log` | 分页列表（任务、结果、触发方式、关键词） |
| GET | `/api/v1/job-logs/{id}` | `system:job:log` | 日志详情（含完整堆栈） |

## 8. 单机限制与集群前置条件（必须明确）

**现状：单实例部署，调度与并发控制全部在进程内。**

- 现在**没有**做跨节点防重：没有分布式锁、没有数据库抢占、没有 leader 选举。
- 因此将来直接部署多实例时，**同一任务会被每个实例各执行一次**（N 个实例 = N 份执行与 N 条日志），失败通知也会重复发送。
- 上集群前的**前置条件**（缺一不可）：
  1. 选型：Quartz JDBC 集群模式、XXL-JOB 等外部调度中心，或自研基于 Redis 的分布式锁/leader 选举。
  2. 触发点唯一：同一 cron 触发只能由一个实例持有并执行（锁需带 TTL 与续租，避免实例崩溃后死锁）。
  3. 日志去重：`sys_job_log` 增加 `instance_id` 与 `fire_time`，并建唯一键（或至少可查询去重），避免同一次触发写多行。
  4. 任务幂等：任务实现自身必须可重复执行（或由调度层保证只执行一次）。
  5. 验证：至少两实例联调，确认同一触发只执行一次、通知只发一次。
- 在这些条件完成前，**不得声称支持集群**；本轮文档与界面不提供任何“集群/分布式”开关。

## 9. 权限码与菜单

| 权限码 | 类型 | 挂靠 | 授予 |
|---|---|---|---|
| `system:job:list` | 菜单 `/system/jobs`，组件 `system/job/index` | 系统管理目录 | SUPER_ADMIN |
| `system:job:create` | 按钮 | 定时任务菜单 | SUPER_ADMIN |
| `system:job:update` | 按钮 | 定时任务菜单 | SUPER_ADMIN |
| `system:job:delete` | 按钮 | 定时任务菜单 | SUPER_ADMIN |
| `system:job:status` | 按钮 | 定时任务菜单 | SUPER_ADMIN |
| `system:job:run` | 按钮 | 定时任务菜单 | SUPER_ADMIN |
| `system:job:log` | 按钮 | 定时任务菜单 | SUPER_ADMIN |

- 只授予 `SUPER_ADMIN`：调度可执行代码注册的任务，属系统级高危操作；部门管理员不授予。
- 用户端无接口。

## 10. 审计

- 新建/修改/启停/手动执行/删除使用 `@OperLogRecord(module = "定时任务", action = ...)`。
- 自动触发不产生 HTTP 操作日志，由 `sys_job_log` 记录。

## 11. 已知限制

| # | 限制 | 处置 |
|---|---|---|
| 1 | 单机调度，多实例重复执行 | §8；不做假装支持 |
| 2 | 超时依赖线程中断，任务可能不响应 | 文档写明；任务实现避免不可中断阻塞 |
| 3 | cron 按服务器时区解析，非用户时区 | 界面提示；如多时区部署需统一约定 |
| 4 | 一次触发只保留一行聚合日志 | 不保留每次重试的独立堆栈；需要逐次排障时从 `attempt_count` 与摘要判断 |
| 5 | 应用重启后已注册 future 丢失 | 启动时按库重建；重启期间错过的触发不补跑 |
| 6 | 任务标识唯一，一个 Bean 只能有一个任务定义 | 若需同一 Bean 多套 cron，需扩展 `job_key` 唯一约束，本轮不做 |

## 12. 验证计划

1. 新建 `demo-heartbeat`，cron `0 * * * * *`（每分钟），启用；等待自动触发，确认 `sys_job_log` 出现 `triggerType=AUTO` 且 `next_run_at` 滚动。
2. 修改 cron 为 `*/10 * * * * *`（每 10 秒），不重启；确认下一次触发间隔变化。
3. 停用任务，确认不再新增自动日志、`next_run_at` 为空。
4. 手动执行一次，确认日志 `triggerType=MANUAL`，且 `next_run_at` 不变（仍按 cron）。
5. `demo-fail` 配置重试 2 次、间隔 1 秒；确认 `attempt_count=3`、`result=FAIL`、堆栈落库，并按 `ON_FAILURE` 收到站内信。
6. `ON_CONSECUTIVE` 阈值 2：连续两次失败后只发一次（或按定义在达到阈值时发），验证通知条数。
7. 并发策略 `DISALLOW`：手动连续触发两次，第二次返回 `409` 或记 `SKIPPED`。
8. `NotificationPublisher` 事务验证：任务失败通知走 `REQUIRES_NEW`（真实调用方）；另补一条事务内 `publish` 的真实调用方验证。
