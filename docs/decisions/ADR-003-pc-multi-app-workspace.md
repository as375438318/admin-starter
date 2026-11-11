# ADR-003 前端按多端 monorepo 组织（`apps/pc` + `packages/*`）

| 项目 | 内容 |
|---|---|
| 编号 | ADR-003 |
| 版本 | v1.0 |
| 状态 | 已实施 |
| AGENTS 状态 | `IMPLEMENTED` |
| 读者 | 决策人、架构、AI |
| 依赖 | `docs/specs/startup-change-process.md` `PRC-STG-006`；`AGENTS.md` §3、§5 |
| 日期 | 2026-11-08 |
| 决策人 | 项目负责人（用户） |
| 关联条目 | `HUMAN-001`、`FE-DIR-001`、`FE-DEP-001`、`FE-COMBO-003`、`FE-STACK-001`、`FE-STACK-004` |

## 1. 背景

前端最初是单端工程 `web/`，只服务 PC 端。项目层待确认项 `HUMAN-001`（前端目录是否迁移到 `apps/pc`）此前结论为“保留 `web/`，多端时再改名”。

2026-11-08 决策人把 `HUMAN-001` 改为“**默认多端**”：脚手架交付物需要同时具备 PC 端与跨端（H5/小程序/App）的组织形态，避免后续每个项目重复搬迁目录。触发问题：`FE-DIR-001` 要求目录按 `apps/pc`、`apps/mobile`、`packages/{shared,platform,ui-tokens,contracts}` 组织，现状 `web/` 与该 MUST 规则不一致。

## 2. 约束

| 约束 | 来源 | 是否硬约束 |
|---|---|---|
| 目录必须具备 `apps/pc`、`apps/mobile`、`packages/{shared,platform,ui-tokens,contracts}` | `FE-DIR-001` | 硬 |
| 在 `apps` 下新建第三套应用判为不通过 | `FE-DIR-001` | 硬 |
| 内部包用 `workspace:*`，不跨包相对路径 | `FE-DEP-001` | 硬 |
| `packages/shared` 不得含 Web-only 能力 | `FE-COMBO-003` | 硬 |
| 功能零改动：路由、接口路径、页面行为、权限逻辑不动 | 本项目任务书 | 硬 |
| 旧 `web/` 删除，禁止两份并存 | 本项目任务书 | 硬 |
| `docs/specs/`、`docs/templates/`、`AGENTS.md`、`tools/` 不得改动 | 本项目任务书 | 硬 |
| 包管理器由 `AGENTS.md` §9-B 选定且唯一 | `FE-STACK-004` | 硬（当前 §9-B 未填，见 §5 已知风险） |
| 技术栈须在 §9-B 确认后才开工 | `FE-STACK-001` | 硬（当前 §9-B 未填，见 §5 已知风险） |

## 3. 候选方案

| 方案 | 做法 | 优点 | 缺点 | 被否理由 |
|---|---|---|---|---|
| A（选定） | 严格按 `FE-DIR-001`：`apps/pc` + `apps/mobile` + `packages/{shared,platform,ui-tokens,contracts}`；`apps/mobile` 与 `platform`、`contracts` 只放 README 占位 | 完全对齐 MUST 规则；后续复制即用；不塞空壳代码 | 目录数比现状多；跨端真正落地前 `apps/mobile` 无代码 | — |
| B | 按任务书草稿的 `apps/h5` + `apps/mini` + 仅 `packages/shared` | 直观表达 H5 与小程序两条线 | 违反 `FE-DIR-001`：`apps` 下出现第三套应用；缺 `platform`/`ui-tokens`/`contracts`；放弃复用设计令牌与契约生成 | `FE-DIR-001` 判不通过，需 ADR 例外并标 `RISK-ACCEPTED`，不符合“按规则要求迁移”前提 |
| C | 保留 `web/` 不改，仅在文档登记偏差 | 改动最小 | 与 `HUMAN-001` 新决议相反；`FE-DIR-001` 长期不通过 | 决策已变更为默认多端，方案失效 |

## 4. 决定

采用方案 A：

1. 前端改为 pnpm workspace：`apps/pc`（唯一可运行应用，原 `web/` 整体迁入）、`apps/mobile`（占位 README）、`packages/shared`（跨端纯类型）、`packages/ui-tokens`（`tokens.css` 设计令牌）、`packages/platform` 与 `packages/contracts`（占位 README，等移动端或契约生成落地再填代码）。
2. 根 `package.json` 提供 `dev` / `build` / `preview` 脚本，`packageManager: pnpm@11.7.0`、`engines.node>=22`；`pnpm-workspace.yaml` 声明 `apps/*`、`packages/*` 与 `allowBuilds`（`esbuild`、`vue-demi` 需要 postinstall）。
3. 只抽最小复用集：`api/types.ts` 移入 `packages/shared`，`apps/pc/src/api/types.ts` 保留为转发文件；`styles/tokens.css` 移入 `packages/ui-tokens`，由 `apps/pc/src/main.ts` 引入。其余请求封装、Cookie 会话、Element Plus 组件全部留在 `apps/pc`，不强行上抽。
4. 业务代码零改动：路由、接口路径、页面行为、权限逻辑均未修改，只改目录与构建配置。
5. 禁止新建 `apps/h5`、`apps/mini`：跨端只保留 `apps/mobile` 一个目录。

## 5. 后果

| 类型 | 内容 |
|---|---|
| 正面 | 目录与 `FE-DIR-001` 对齐；设计令牌与共享类型有唯一定义处；后续项目复制即得多端骨架；PC 构建产物仍在 `apps/pc/dist` |
| 负面 | 构建与依赖管理复杂度上升（根安装、workspace 链接、`allowBuilds` 配置）；CI 必须改为在根目录执行 `pnpm install --frozen-lockfile` 与 `pnpm build`；IDE 与命令行需要进入工作区根目录 |
| 需要监控的 | `pnpm-lock.yaml` 唯一性（不得出现 `package-lock.json`）；`apps/pc/src` 不得反向引用 `packages/*` 的相对路径；共享包体积变化 |
| 已知风险 | `RISK-ACCEPTED`：`AGENTS.md` §9-B 技术选型表仍为空，`FE-STACK-001`/`FE-STACK-004` 无法完全满足。补偿措施：本次未新增框架与版本，仅沿用既有 Vue 3 + Vite + TS（`strict: true`）与既有依赖版本；该风险已登记 `docs/audit/impl-notes.md`，§9-B 补齐后需复核本 ADR |

## 6. 已否决的建议

| 建议 | 提出方 | 否决理由 | 复核触发条件 |
|---|---|---|---|
| 建 `apps/h5` 与 `apps/mini` 两个独立应用 | 任务书草稿 | 构成 `apps` 下第三套应用，`FE-DIR-001` 判不通过 | 若 `FE-DIR-001` 被正式修订为允许按端拆分，则重开 |
| 把请求封装、Cookie 会话一并抽到 `packages/shared` | 迁移初稿 | 违反 `FE-COMBO-003`（Web-only 能力不得进入 `shared`）；本次以跑通为第一优先 | 移动端落地且需要复用请求层时，改为抽到 `packages/platform` |
| 为“看起来完整”给 `apps/mobile`、`packages/platform` 塞空壳组件 | 迁移初稿 | 空壳代码无法运行，反而制造噪声与维护成本 | `apps/mobile` 真正初始化后重开 |

## 7. 关联与变更

| 项 | 内容 |
|---|---|
| 影响文档 | 索引未覆盖“前端目录结构”事实，建议补入 `docs/specs/INDEX-facts.md`；已更新 `README.md`、`docs/audit/impl-notes.md`、`docs/audit/final-report.md` |
| 影响代码 | `apps/pc`（原 `web/`）、`packages/*`、根 `package.json`、`pnpm-workspace.yaml`、`pnpm-lock.yaml`、`.gitignore`；后端 `server/` 未改动 |
| 回滚方式 | 将 `apps/pc` 整体移回 `web/`，删除 `packages/*` 与根工作区配置，恢复 `web/src/api/types.ts` 与 `web/src/styles/tokens.css` 原位文件；无数据影响 |

## 8. 完成标准

- 候选方案至少两个，且每个被否方案都有具体理由。
- 后果必须四段齐全（正面、负面、需要监控、已知风险）；已知风险无内容时写“无”。
- 选型类 ADR 必须能被 `AGENTS.md` §9-B 的对应行引用。
- 状态为“已接受”／`APPROVED` 时，决策人与日期齐全、无未解决 TODO；关联条目编号真实存在。

## 9. 自检清单

- [x] 背景只写事实与问题（MUST, STATIC）
- [x] 候选方案 ≥ 2，被否理由具体（MUST, STATIC）
- [x] 后果四段齐全（MUST, STATIC）
- [x] ADR 状态与 `AGENTS.md` 状态按 §0 映射一致（MUST, STATIC）
- [x] 影响面来自索引（MUST, CONTEXT）
- [x] `APPROVED` 时无未解决 TODO（MUST, STATIC）
- [x] 所有示例占位内容已替换（MUST, STATIC）

> 现状备注（2026-11-10）：共享包已由 `packages/shared` 迁至 `apps/shared`；`packages/platform`、`packages/contracts` 空壳已删除。对应规范修订见 `CR-20261110-001`。本 ADR 正文为历史决策记录，保持原样不改。

> 编号说明：本 ADR 曾于 2026-11-10 因 `SL-ID-002` 编号校验被误重排为 001 号，同日已回退为 ADR-003；001 号作为历史重排编号作废，不得再被引用。


