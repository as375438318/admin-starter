# CR-20261111-002 引入前端测试框架（Vitest + Playwright）并更新 AGENTS §9-B 测试框架行

| 项目 | 内容 |
|---|---|
| 提出人 | 用户（2026-11-11 指示「按照这个顺序执行」：先 Vitest、再 Playwright、小程序自动化等真实业务再选型） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-11 |
| 变更层级 | **通用层**（修改 `AGENTS.md` §9-B 的「测试框架」行；该文件在「只读不改」清单内）＋ **项目层**（前端依赖、测试用例、CI 工作流） |
| 关联 | `docs/decisions/ADR-006-frontend-test-framework.md`；`ADR-005`；`docs/specs/frontend-spec.md` 决策项 D15 与 §15.2 |

## 1. 背景

`docs/specs/frontend-spec.md` 决策项 D15 要求「单测 + E2E + 小程序自动化各一套，具体见 `AGENTS.md` §9-B」，而 §9-B 此前把「前端测试框架」记为「未定」，`apps/pc` 实际**没有任何测试框架依赖**：

- CI 只跑 `vue-tsc -b`（类型检查）与 `vite build`（能否打包），运行期逻辑零验证；
- 已有实例证明会漏：`apps/pc/src/api/http.ts` 曾把所有 `/auth/*` 排除在 401 静默刷新之外，导致 access 过期后刷新页面被误登出——类型与构建全绿，靠人工点浏览器才发现。

## 2. 范围

**改**：

- `AGENTS.md` §9-B「测试框架」行：由「前端：未定」改为 Vitest 3 + `@vue/test-utils`（单元/组件）+ Playwright（E2E），小程序自动化保持未定；依据 ADR 增加 `ADR-006`。
- 新增 `docs/decisions/ADR-006-frontend-test-framework.md`。
- `apps/pc`：新增 5 个 devDependency（`vitest`、`@vitest/coverage-v8`、`@vue/test-utils`、`happy-dom`、`@playwright/test`）、4 个脚本（`test:unit`／`test:unit:watch`／`test:unit:coverage`／`test:e2e`）、`vitest.config.ts`、`playwright.config.ts`、3 个单测文件、1 个 E2E 文件。
- `apps/pc/src/api/http.ts`：把 401 处置决策抽成纯函数 `planOn401`（连同 `isAuthEntry`、`isTerminalAuthCode` 导出），**行为不变**，只为可测。
- `.github/workflows/ci.yml`：前端 job 增加 `pnpm --filter @admin-starter/pc test:unit` 步骤。
- `.github/workflows/e2e.yml`：新增 `workflow_dispatch` 触发的 E2E 工作流（MySQL/Redis 服务容器 + 后端 + 前端）。
- `.gitignore`：忽略 `test-results/`、`playwright-report/`、`coverage/`。

**不改**：任何后端代码、接口、表结构、权限码、错误码、配置项；`docs/specs/` 五份规范正文。

## 3. 涉及事实

| 事实 | 变更前 | 变更后 |
|---|---|---|
| 前端测试框架 | 无（`AGENTS.md` §9-B 记「未定」，D15 无法满足） | 定义方 `AGENTS.md` §9-B：Vitest 3 + Playwright；依据 `ADR-006` |
| 小程序自动化 | 无 | **仍无**：跟随跨端框架，显式保持未定，不因"补齐 D15 三套"而提前引入 |
| 401 处置口径 | 内联在拦截器中，不可单测 | 语义不变，抽出 `planOn401` 后可单测（三类分支有 7 个用例锁定） |

## 4. 影响文档与引用点

| 文档 | 影响 |
|---|---|
| `AGENTS.md` | §9-B「测试框架」行 + 「未选型项」一段（把"前端测试框架"移出未定清单） |
| `docs/decisions/ADR-006-frontend-test-framework.md` | 新增 |
| `docs/specs/INDEX-facts.md`、`facts.yaml` | 事实 `F-TECH-STACK` owner 未变，仅内容刷新，无需改动结构；核对时间维持 2026-11-11 |
| `docs/audit/impl-notes.md` | 记录依赖、用例、验证结果与两条观察 |
| `README.md` | 变更记录 + 「版本管理」小节的本地等价命令补测试命令 |

## 5. 兼容性

- 运行时：**零影响**。`http.ts` 的重构是等价改写（原有 4 类分支保持，新增单测锁定）。
- 依赖：仅 devDependencies，不进生产包（`vite build` 产物不含测试代码）；`pnpm-lock.yaml` 变化 114 个包（多为 `@playwright/test` 与 `@vitest/coverage-v8` 的传递依赖）。
- 版本约束：**vitest 锁 3.x**（vitest 5 的 peer 要求 Vite ≥6，会倒逼升级 Vite）；该约束已写入 ADR-006，避免后来者"顺手升级"。
- CI：单元测试步骤为**新增**，失败即红；E2E 工作流为独立 `workflow_dispatch`，不影响既有三条 job。

## 6. 回滚方式

`git revert` 本 CR 对应提交，并执行 `pnpm remove -D vitest @vitest/coverage-v8 @vue/test-utils happy-dom @playwright/test --filter @admin-starter/pc`；`http.ts` 恢复内联判断即可。无数据与运行时影响。

## 7. 分级与审批

| 档位判定项 | 结论 |
|---|---|
| ① 只动 1 个模块 | 否（`AGENTS.md` 通用层 + `apps/pc` + CI） |
| ② 不新增/删除/改名接口路径或字段 | 是 |
| ③ 不动表结构与数据迁移 | 是 |
| ④ 不新增权限码 | 是 |
| ⑤ 不新增错误码 | 是 |
| ⑥ 未命中 `PRC-APR-001` | 是（契约兼容/数据模型/权限放宽/安全降级/状态机/验收标准/模块启停 均未命中） |

→ 按 `AGENTS.md` §4.3：修改 `AGENTS.md` 需另开通用层流程并记变更记录，本条即该记录。**规则缺口（沿用 CR-20261111-001 已登记项）**：档位表仍未定义"通用层治理 + 项目层实现"混合改动的归口。

**人工确认**：技术栈测试框架行由用户 2026-11-11 口头确认执行顺序（先 Vitest 后 Playwright，小程序自动化待业务需求），已落 `ADR-006` 决策人栏。

## 8. 条目状态变更

`ADR-006` 状态「已实施」、`AGENTS 状态` `IMPLEMENTED`（依赖已装、用例已跑通、CI 已接入）。D15 的第三套（小程序自动化）保持未满足，已在 ADR-006 §5 与 `AGENTS.md` §9-B 显式登记为待决，**不谎称已满足**。

## 9. 代码改动顺序

ADR-006 → `AGENTS.md` §9-B → 依赖与配置（`package.json`、`vitest.config.ts`、`playwright.config.ts`）→ `http.ts` 抽纯函数 → 单测 → E2E → CI → 回填 impl-notes/README。

## 10. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 单元/组件测试 | `pnpm --filter @admin-starter/pc test:unit` | 3 文件 19 用例全通过 |
| 覆盖率 | `… test:unit:coverage` | `http.ts` 分支 100%、行 20%（仅纯函数被覆盖）；整体 1.35% 行（页面按 §15.2 不设门槛） |
| E2E | `pnpm --filter @admin-starter/pc test:e2e`（后端在 8080、本机 Chrome） | 4 用例全通过，7.8s |
| 生产构建 | `pnpm build` | 通过（vue-tsc + vite 均未受测试文件影响） |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0（warning 数与改前持平） |

## 11. 异议记录

本轮提出 1 条并已处置：**E2E 用例最初断言「无权限访问 → 403 页」，实际运行落到 404 状态页**。原因：动态路由按登录下发菜单注册，未授权路由根本不注册，落到通配 404；403 页只在"路由已注册但 meta 校验失败"时出现，几乎不可达。依据 `ui-spec.md` 只规定「三种空状态（含无权限）」，未规定未授权路由应显示 403 还是 404，故**不判为违规**；处置为把用例改为断言可验证的对外行为（无列表 + 状态页），并把该差异记入 `impl-notes.md` 作为待产品决策项，未擅自改路由策略。

另有一条自我纠正：单测初稿假设 `20105` 属终止码，运行失败后按 `docs/design/auth-and-datascope.md` 的 401 分码矩阵更正为"非终止码（仍先尝试刷新）"——**测试先于文档纠错了我的假设**。
