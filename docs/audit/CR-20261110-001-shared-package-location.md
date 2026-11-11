# CR-20261110-001 共享包位置与空壳包清理

## 1. 背景

- 前端共享包已由 `packages/shared` 迁至 `apps/shared`（与 `apps/pc`、`apps/mobile` 平级），`pnpm-workspace.yaml` 由 `apps/*` 改为显式列包。迁移本身构建与回归均通过，但规范仍停留在「共享代码放 `apps` 内判为不通过」，现状与 `FE-DIR-001`（MUST）直接矛盾。
- 不改的后果：后续 AI 按规范第 0 步读规范，会把现状判成违规并要求「迁回 `packages/shared`」，形成规范与代码互相打回的循环；`FE-COMBO-003` 的引用路径也指向已不存在的目录。
- 同时，`packages/platform`、`packages/contracts` 只有 `README.md`、无 `package.json`、全仓 0 引用，已删除；`D10`、`FE-DIR-004`、`FE-DOC-003` 因此出现悬空引用，需一并纠正（同一事实，不另开 CR）。

## 2. 范围

- 涉及规则：`FE-DIR-001`、`FE-DIR-004`、`FE-COMBO-003`、`FE-DOC-003`、`FE-DEP-001`（仅示例路径）、决策地图 `D10`、§14 反例清单（新增 1 条）。
- 不涉及：接口契约（`CT-*`）、数据模型、权限矩阵、状态机、验收标准、术语、模块启停；不新增/删除规则编号，规则总数仍为 123 条。
- 不涉及代码行为：共享包仍以包名 `@admin-starter/shared` 引用，`apps/mobile` 与 `packages/ui-tokens` 保留不动。

## 3. 涉及事实

| 事实 | 现状（as-is） | 定义方 |
|---|---|---|
| 共享包位置 | `apps/shared`（`@admin-starter/shared`，`main/types/exports` 指向 `./src/index.ts`） | `docs/baseline/modules.md`（v1.7 §2「共享包位置」） |
| 应用与共享包的区分 | 可运行应用有自己的 `dev`/`build` 脚本与入口；`apps/shared` 无 `build` 脚本 | `docs/baseline/modules.md`（v1.7 §2） |
| 工作区过滤行为 | `pnpm --filter "./apps/*"` 仍会匹配 `apps/shared`（按目录匹配）；它不参与批量构建的真实原因是「没有 `build` 脚本」 | `docs/baseline/assertions.md` #46（2026-11-09 实测） |
| 平台层与契约生成目录 | `packages/platform`、`packages/contracts` 已删除（原为仅 README 的空壳） | `docs/baseline/modules.md`（v1.8） |

## 4. 影响文档与引用点

来自 `docs/specs/INDEX-facts.md` 的命中：**无**。

**索引未覆盖该事实**（「前端共享包位置」未被登记为索引事实），建议补入 `F-DIR-SHARED-LOC`（owner：`docs/baseline/modules.md` §2；refs：`FE-DIR-001`、`FE-COMBO-003`），本条不在本 CR 内执行，避免与 `SL-FACT-001` 的 md/yaml 逐行一致性校验互相牵制。

逐条引用点（本次实际改动）：

| 文件 | 位置 | 改动 |
|---|---|---|
| `docs/specs/frontend-spec.md` | 决策地图 `D10` | 目录结构改为 `apps/{pc,mobile,shared}` + `packages/ui-tokens` |
| `docs/specs/frontend-spec.md` | `FE-DOC-003` | 契约生成物目录不再指向 `packages/contracts` |
| `docs/specs/frontend-spec.md` | `FE-COMBO-003` | 引用路径 `packages/shared` → `apps/shared`，Web-only 能力去向改为应用自身 |
| `docs/specs/frontend-spec.md` | `FE-DIR-001` | 改为「共享包与可运行应用平级、集中管理」，补应用/共享包判据与 `--filter` 事实 |
| `docs/specs/frontend-spec.md` | `FE-DIR-004` | 平台层已删除，改为「平台差异实现放使用它的应用内，不进共享包」 |
| `docs/specs/frontend-spec.md` | `FE-DEP-001` | 仅正反例路径改为 `apps/shared` |
| `docs/specs/frontend-spec.md` | §14 反例清单 | 新增「把共享包当可运行应用参与批量构建」1 条 |
| `docs/decisions/ADR-003-pc-multi-app-workspace.md` | 文末 | 只加一行现状备注，正文不改 |
| `docs/design/field-masking.md`、`docs/audit/final-report.md`、`apps/mobile/README.md` | 路径字面量 | `packages/shared` → `apps/shared` |

## 5. 兼容性

- 不破坏兼容：包名、导出、`workspace:*` 声明均未变，`apps/pc` 的 import 语句零改动，构建产物无变化。
- 无需接口版本演进，无存量数据迁移。
- 目录删除仅涉及无 `package.json`、0 引用的空壳，删除后 `pnpm install` 与构建需保持通过（见 §10）。

## 6. 回滚方式

- 文档：把 `frontend-spec.md` 回退到 v1.8、删除本 CR 的 3 处路径替换即可；`ADR-003` 删掉文末备注行。
- 代码/目录：仓库无 `.git`（`git rev-parse` 报 not a git repository），**无法用版本控制回滚**；恢复 `packages/platform`、`packages/contracts` 需按原 README 文本手工重建（内容已在 `docs/audit/final-report.md` §交付清单中留痕）。
- 数据：无数据库影响，无需回滚。
- 回滚后状态：回到「共享包在 `apps/shared`、但规范要求 `packages/shared`」的自相矛盾状态，需重新开 CR 才能消除。

## 7. 分级与审批

- 档位判定：未新增/删除/改名接口、未动表结构与迁移、未新增权限码与错误码、未命中 `PRC-APR-001` 七类 → **B 档（模块级）**；跨目录纯删除不升档。
- 必须人工项（`PRC-APR-001`）：七类逐条判定均为「未命中」。
  - 契约破坏兼容或版本演进：未命中；数据模型不可逆变更：未命中；权限放宽：未命中；安全降级：未命中；状态机变更：未命中；模块启停：未命中；验收标准变更：未命中。
- 可自动项：措辞、示例路径、反例条目、索引建议 → 记入本 CR 与 `docs/audit/impl-notes.md`。
- 签核：用户于 2026-11-10 明确批准「开 CR 修掉规范与现状的自相矛盾」及其四项具体要求，签核日期早于本次文档改动（`PRC-CR-004`）。

## 8. 条目状态变更

无。本次改的是规范规则正文，不涉及 `REQ`/`AC` 条目状态流转。

## 9. 代码改动顺序

1. 本 CR 与本 CR 涉及的规范/文档改动；
2. 删除 `packages/platform`、`packages/contracts`（先校验 0 引用与配置无残留）；
3. `pnpm install` + `pnpm --filter @admin-starter/pc build`；
4. 回归：12 页走查、文件页 413、通知撤回、脱敏 422、401 分码；
5. 回填基线（`modules.md`、`assertions.md`、`impl-notes.md`）+ `spec-lint`。

## 10. 回归范围

- `docs/specs/frontend-spec.md` 全部被改规则的失败信号逐条自检（目录、共享包 Web-only、依赖引用方式）。
- `pnpm install` 与 `pnpm --filter @admin-starter/pc build` 通过。
- `node tools/spec-lint/cli.mjs` error 0。
- 前端功能回归：12 个列表页；文件页 413 与预校验；通知撤回；用户页掩码值 422；401 四条分码。
- 配置/数据恢复：临时改动的 `sys.upload.max-size`、造出的测试数据与账号状态全部复原。

## 11. 异议记录

- 本轮新增异议 0 条（用户已明确批准搬迁与规范修订）。
- 上一轮记录的 `RISK-ACCEPTED`（`FE-DIR-001` 与 `apps/shared` 冲突，见 `docs/audit/impl-notes.md`「与规范不一致」）**由本 CR 解除**；风险承接方式即本 CR 的规范修订，不保留双定义。
