# 通知 / 站内信 实施计划（2026-11-09）

| 项目 | 内容 |
|---|---|
| 档位 | B（新增模块/表/权限码/接口，未命中 `PRC-APR-001`） |
| 设计 | `docs/design/notification.md` |
| 迁移 | `V10__notification_module.sql`（只新增，不改 V1–V9） |
| 状态 | IMPLEMENTED（2026-11-09 实测通过，见 `docs/audit/impl-notes.md` NOTICE-001～009） |

## 步骤

1. 文档：本设计与计划；`docs/requirements/system-modules.md` 增加“通知公告”能力条目；`docs/design/data-model.md` 由脚本导出。
2. V10：`sys_notice`、`sys_notice_scope`、`sys_notice_recipient` 三张表；5 枚权限码与菜单；按角色 code 授权。
3. 后端：
   - 领域：`Notice`、`NoticeScope`、`NoticeRecipient` 模型与仓储接口。
   - 基础设施：DO/Converter/Mapper/Repository；`DataPermissionTables` 登记（notice 受控、scope/recipient 豁免）。
   - 应用：`NotificationPublisher`（REQUIRED / REQUIRES_NEW）与 `NotificationAppService`（发布、分页、详情、撤回、删除、统计、收件箱、未读数、全部已读）。
   - Web：`NoticeController`（管理端）、`MyNoticeController`（用户端），权限注解与操作日志。
4. 前端：`api/notice.ts`、`views/system/notice/index.vue`、`views/message/index.vue`（我的消息，静态路由 `/messages`）、顶栏未读红点与轮询。
5. 验证：构建、真实登录多角色实测、`pnpm --filter @admin-starter/pc build`、`node tools/spec-lint/cli.mjs`、重跑权限矩阵与数据模型导出。
6. 回填：`docs/baseline/modules.md`、`assertions.md`（快照口径）、`system-modules.md`、`docs/audit/impl-notes.md`。
