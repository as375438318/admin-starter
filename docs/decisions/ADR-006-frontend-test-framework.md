# ADR-006 前端测试框架选型（单元/组件 + E2E）

| 项目 | 内容 |
|---|---|
| 编号 | ADR-006 |
| 版本 | v1.0（2026-11-11） |
| 状态 | 已实施 |
| AGENTS 状态 | `IMPLEMENTED` |
| 读者 | 决策人、前端、AI 编码助手 |
| 依赖 | `docs/specs/frontend-spec.md` 决策项 D15 与 §15.2；`docs/audit/CR-20261111-002-frontend-test-framework.md`；`ADR-005` |
| 日期 | 2026-11-11 |
| 决策人 | 项目负责人（用户 2026-11-11 指示「按这个顺序执行」：先 Vitest、再 Playwright、小程序自动化等真实业务开发再选型） |
| 关联条目 | `FE-ENG-004`、`FE-ENG-005`、`FE-CODE-013` |

## 1. 背景

`docs/specs/frontend-spec.md` 的决策项 D15 要求「单测 + E2E + 小程序自动化各一套」，但仓库此前**没有任何前端测试框架**：

- CI 只跑 `vue-tsc -b`（类型检查）与 `vite build`（能否打包），**不验证运行时逻辑**；
- 已发生的真实回归证明这个缺口会咬人：`apps/pc/src/api/http.ts` 曾把所有 `/auth/*` 排除在 401 静默刷新之外，导致 access 过期后刷新页面被误登出（类型与构建全绿，靠人工点浏览器才发现）；
- 前端逻辑已大量下沉到 `apps/pc/src/composables`（分页、CRUD 抽屉、字段错误、分片上传、Blob 预览）与 `api/http.ts`，正好是可单测的形态。

## 2. 约束

| 约束 | 来源 | 是否硬约束 |
|---|---|---|
| 技术栈版本只在 `AGENTS.md` §9-B 登记，本 ADR 不写死其他文档的版本号 | `AGENTS.md` §1 | 硬 |
| 包管理器唯一、锁文件唯一（`pnpm-lock.yaml`） | `FE-STACK-004` | 硬 |
| 构建命令固定且可复现 | `FE-ENG-004` | 硬 |
| 覆盖率按模块分层，不按一刀切 | `frontend-spec.md` §15.2 | SHOULD |
| 现有 Vite 5 工程不得因测试框架被迫升级 | 本项目现状 | 硬 |
| 跨端（小程序/App）框架未选型 | `AGENTS.md` §9-B、`ADR-005` | 硬（决定第三套自动化无法先做） |

## 3. 候选方案

### 3.1 单元/组件层

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **Vitest 3 + @vue/test-utils + happy-dom** | 与 Vite 同源、复用 `vite.config` 的 alias；无需浏览器；速度秒级；TS 原生 | 需要与 Vite 主版本对齐（Vitest 5 要求 Vite ≥6，会倒逼升级 Vite） | **采用** |
| Jest + ts-jest | 生态最广 | 需另配 transform，与 Vite alias/ESM 割裂，配置成本高 | 不采用 |
| Playwright Component Testing | 真浏览器渲染 | 慢、依赖浏览器二进制，与 E2E 抢同一套基建 | 不采用（组件测试用 Vitest 足够） |

**版本选择的关键约束**：`vitest@5` 的 peer 要求 `vite ^6 || ^7 || ^8`，而本工程是 Vite 5.4 → 选 **vitest 3.x**（不触发 Vite 升级），并显式记录该约束，避免后来者"顺手升级"。

### 3.2 E2E 层

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **Playwright（`channel: chrome`）** | 多浏览器；自带 trace/截图；可用本机已装 Chrome，**免下载浏览器**；`webServer` 自动起服务；`page.request` 复用登录态做接口断言 | CI 需编排后端 + MySQL + Redis | **采用** |
| Cypress | 调试体验好 | 单标签页模型限制多；与 Vite 需额外配置；同样要服务编排 | 不采用 |
| 继续手工验证 | 零成本 | 不可重复、不能进 CI；历史 bug 已证明会漏 | 不采用 |

### 3.3 小程序自动化

**本轮不选型**。它依赖跨端框架（`AGENTS.md` §9-B 仍为「未定」），而跨端框架应由真实小程序业务需求驱动。约束：跨端框架定稿前，不得为"补齐 D15 三套"而在 `apps/mobile` 落代码或引入小程序自动化工具。

## 4. 决定

1. 单元/组件：`vitest@3.x` + `@vue/test-utils` + `happy-dom`，配置在 `apps/pc/vitest.config.ts`，用例放 `apps/pc/src/**/*.spec.ts`，排除 `e2e/**`。
2. 覆盖率：`@vitest/coverage-v8`，按 `frontend-spec.md` §15.2 分层（纯函数 ≥80%、composable 60–70%、页面不设门槛）。
3. E2E：`@playwright/test`，配置在 `apps/pc/playwright.config.ts`，用例放 `apps/pc/e2e/*.spec.ts`，默认 `channel: 'chrome'`（用本机 Chrome），可用 `E2E_CHANNEL=` 覆盖。
4. 脚本：`test:unit` / `test:unit:watch` / `test:unit:coverage` / `test:e2e`，互不复用 `test` 名。
5. CI：单元测试并入现有 `frontend` job（秒级、无服务依赖）；E2E 单独 `workflow_dispatch` 工作流（需 MySQL/Redis 服务容器），不进每次提交的必跑集。
6. 为可测性做的最小重构：把 `http.ts` 的 401 决策抽成纯函数 `planOn401`（行为不变），使"刷新/终止/拒绝"三类分支可单测。

## 5. 后果

| 类型 | 内容 |
|---|---|
| 正面 | 请求层与 composables 的失败路径可回归，CI 从"只验类型"升级为"验行为"；E2E 把此前手工 CDP 验证的主链路固化下来 |
| 负面 | 新增 5 个 devDependency 与 2 份配置；`pnpm-lock.yaml` 变化；后续写测试有维护成本；Vitest 与 Vite 主版本被锁在兼容区间（Vitest 5 会要求 Vite ≥6） |
| 需要监控的 | ① Vitest / Playwright 大版本升级需与 Vite、Node 版本一起评估；② E2E 的 flaky 率（只保 3–5 条主链路，失败先看 trace）；③ 覆盖率是否真按分层推进，而不是刷数字 |
| 已知风险 | E2E 工作流未在 GitHub Actions 上实测（本机可跑，CI 编排需首次手动触发验证）；小程序自动化缺位属规范 D15 未满足项，已登记为待决 |

## 6. 已否决的建议

| 建议 | 提出方 | 否决理由 | 复核触发条件 |
|---|---|---|---|
| 升到 Vitest 5 并同步升 Vite 6/7 | 默认"用最新版" | 会连带升级构建链，属独立变更；本次目标只是补测试能力 | 有明确升级收益（新特性/安全修复）时另开 ADR |
| 用 Jest 以兼容老项目习惯 | 通用经验 | 与 Vite/ESM 割裂，配置成本高、启动慢 | 若将来引入非 Vite 工程再评估 |
| 只上 E2E、不上单测 | "E2E 更接近真实" | E2E 慢且要服务，失败定位差；本次的 bug 属纯函数分支，单测成本最低 | — |
| 现在就把小程序自动化搭起来 | 规范 D15 字面要求 | 跨端框架未定，工具与用例都会返工 | 跨端框架选型完成且有小程序真实业务需求时 |

## 7. 关联与变更

| 项 | 内容 |
|---|---|
| 影响文档 | `AGENTS.md` §9-B「测试框架」行（改由 CR-20261111-002 记录）；`docs/audit/impl-notes.md`；`README.md` 变更记录 |
| 影响代码 | `apps/pc/package.json`（devDependencies + scripts）、`vitest.config.ts`、`playwright.config.ts`、`src/**/*.spec.ts`、`e2e/*.spec.ts`、`src/api/http.ts`（抽纯函数，行为不变）、`.github/workflows/ci.yml`、新增 `.github/workflows/e2e.yml` |
| 回滚方式 | 删除两份配置、`src/**/*.spec.ts`、`e2e/`、`.github/workflows/e2e.yml`，`pnpm remove -D` 五个包并还原 `http.ts` 的内联判断；无运行时影响 |
| 后续动作 | ① 按 §15.2 逐模块补覆盖；② E2E 首次在 CI 手动触发验证；③ 跨端框架选型后再补第三套 |

## 8. 完成标准

- 候选方案 ≥ 2 且被否方案有具体理由（本 ADR 三层各给替代项）。
- 后果四段齐全。
- 结论可被 `AGENTS.md` §9-B 引用，且不在别处重复版本号。
- 决策人、日期齐全，无未解决 TODO。

## 9. 自检清单

- [x] 背景只写事实与问题（MUST, STATIC）
- [x] 候选方案 ≥ 2，被否理由具体（MUST, STATIC）
- [x] 后果四段齐全（MUST, STATIC）
- [x] ADR 状态与 `AGENTS.md` 状态映射一致（MUST, STATIC）
- [x] 版本约束（Vitest 3 而非 5）写明原因，避免后来者误升级（MUST, STATIC）
- [x] 未选型项（小程序自动化）显式写"不选型"及前置条件（MUST, CONTEXT）
