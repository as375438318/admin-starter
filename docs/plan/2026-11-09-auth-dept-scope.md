# 计划：令牌立即失效 + 部门数据权限重算（2026-11-09）

| 项目 | 内容 |
|---|---|
| 档位 | B（新增列与错误码，纯新增；未命中 `PRC-APR-001`） |
| 设计 | `docs/design/auth-and-datascope.md` |
| 迁移 | `V9__token_invalidation_and_dept_scope.sql`（只新增，不改 V1–V8） |

## 步骤

1. `sys_user` 增加 `token_version`、`token_revoke_reason`。
2. 认证：令牌携带 `ver`；过滤器校验版本 + 每请求重取启用状态/部门/角色/权限；刷新令牌同口径校验。
3. 改密/重置/停用：自增版本；本人改密后为当前设备重签。
4. 部门：增/改/移/删后自增组织版本号；成环校验改精确匹配。
5. 前端：401 分码提示、登录页通知、改密成功提示。
6. 实测：改密旧令牌/其他设备、重置、停用、部门移动前后可见范围、成环拒绝。
7. 回填 `data-model.md`、`assertions.md`、`system-modules.md`、`impl-notes.md`；跑导出脚本与 `spec-lint`。

## 验收

- 旧令牌访问受保护接口返回 401 且分码正确；前端提示不是一律“请重新登录”。
- 部门移动后同一用户数据可见范围立即变化；成环移动返回 409 明确错误。
- `pnpm --filter @admin-starter/pc build` 通过；`node tools/spec-lint/cli.mjs` error 0。
