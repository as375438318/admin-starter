# CR-20261112-018 Dependabot 只收 minor/patch，主版本升级改为人工发起

| 项目 | 内容 |
|---|---|
| 提出人 | AI 提议（Dependabot 首日开出 11 个 PR 后），用户未反对即执行 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改依赖升级策略配置 |
| 关联 | 断言 #83；`AGENTS.md` §9-B（技术栈唯一定义处）；`ADR-005` |

## 1. 现象

`dependabot.yml` 上线当天开出 **11 个 PR**，其中 5 个是主版本跳跃：

| PR | 升级 | 风险 |
|---|---|---|
| #5 | Spring Boot 3.2.12 → **4.1.2** | 大版本，Jakarta/配置/安全链全线变化；CI 已红 |
| #6 | swagger-request-validator 2.44.9 → **3.0.0** | 契约测试 API 大改；CI 已红 |
| #8 | Vite 5.4 → **8.3** | 插件生态与配置项大改 |
| #9 | Pinia 2.3 → **4.1** | store API 变化 |
| #7 | vue-router 4.6 → **5.4** | 路由 API 变化 |
| #10 | @vitejs/plugin-vue 5.2 → **6.0** | 与 Vite 版本绑定 |
| #11 | @vitest/coverage-v8 3.2 → **5.1** | 与 Vitest 版本绑定（ADR-006 已锁 Vitest 3） |

**最危险的不是红的，是绿的**：#7/#8/#9/#10/#11 的 CI 全绿。但我们的测试覆盖不到主版本升级的破坏面（类型定义、插件生态、运行时行为），"全绿"只说明"能编译、能跑现有用例"。

## 2. 决定

`AGENTS.md` §9-B 规定技术栈版本以该节为唯一定义处，`ADR-005`/`ADR-006` 也已锁定具体版本线。因此：

- **`dependabot.yml` 增加 `ignore: version-update:semver-major`**（maven 与 npm 两个生态），只保留 minor/patch 自动 PR；
- 主版本升级改为**人工发起**：开 CR/ADR 评估兼容性、更新 §9-B 与 ADR、再改版本号。

> 这与"依赖要及时升级"不冲突：安全补丁通常在 patch 内，minor 也会自动来；主版本本来就该有人为判断。

## 3. 已开的 11 个 PR 怎么处理（待用户决定）

本 CR 只堵住增量，不处置存量。建议：

| 处理 | 对象 | 理由 |
|---|---|---|
| **关闭** | #5 Spring Boot 4、#6 validator 3.0、#8 Vite 8、#9 Pinia 4、#7 vue-router 5、#10 plugin-vue 6、#11 coverage-v8 5 | 主版本，需评估；且 #5/#6 CI 已红 |
| **可合并** | #1 setup-java 4→6、#2 checkout 4→7、#3 pnpm/action-setup 4→6、#4 maven minor-patch 组 | 动作版本与依赖小版本，检查已绿 |

## 4. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 配置可解析 | 人工核对 YAML 结构（51 行，两个 `ignore` 块） | 通过 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |
| 运行时 | 无改动 | — |