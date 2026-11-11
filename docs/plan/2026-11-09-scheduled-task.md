# 定时任务 实施计划（2026-11-09）

| 项目 | 内容 |
|---|---|
| 档位 | B（新增模块/表/权限码/接口，未命中 `PRC-APR-001`） |
| 设计 | `docs/design/scheduled-task.md` |
| 迁移 | `V11__scheduled_task_module.sql`（只新增，不改 V1–V10） |
| 同批 | 通知管理端「全员通知对所有 `system:notice:list` 持有者可见」口径放开 |
| 状态 | IMPLEMENTED（2026-11-09 实测通过，见 `docs/audit/impl-notes.md` JOB-001～013 与实测表） |

## 步骤

1. 文档：本设计与计划；`docs/design/notification.md` §7 补全员通知可见性；`docs/requirements/system-modules.md` 增加“定时任务”能力条目；`docs/design/data-model.md` 由脚本导出。
2. V11：`sys_job`、`sys_job_log` 两张表；7 枚权限码与菜单；按角色 code 授权（仅 `SUPER_ADMIN`）。
3. 后端：
   - 领域：`JobDefinition`、`JobLog` 模型与仓储接口。
   - 基础设施：DO/Converter/Mapper/Repository；`DataPermissionTables` 登记两张表为 `EXEMPT`。
   - 应用：`ScheduledJob` 注册接口、`JobRegistry`、`JobScheduler`（`ThreadPoolTaskScheduler` + `CronTrigger`）、`JobExecutor`（并发/超时/重试/日志/通知）、`JobAppService`、`JobLogAppService`。
   - Web：`JobController`、`JobLogController`，权限注解与操作日志。
   - 演示任务：`demo-heartbeat`（成功）、`demo-fail`（固定抛异常）、`demo-sleep`（固定休眠，用于超时验证）。
4. 前端：`api/job.ts`、`views/system/job/index.vue`（列表/新建编辑/启停/手动执行/日志抽屉）、共享类型。
5. 验证：构建、真实登录实测（自动/热 cron/停用/手动/异常/重试/通知）、`pnpm --filter @admin-starter/pc build`、`node tools/spec-lint/cli.mjs`、重跑权限矩阵与数据模型导出。
6. 回填：`docs/baseline/modules.md`、`assertions.md`、`system-modules.md`、`docs/audit/impl-notes.md`、`docs/api/openapi.yaml` 与重新导出实现基线。
