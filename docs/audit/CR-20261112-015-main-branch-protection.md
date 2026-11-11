# CR-20261112-015 main 分支保护启用与协作流程（首个走 PR 的改动）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「用A」→「你全自动」（严格保护 + 由 AI 走 PR 流程） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改文档；不改运行时行为 |
| 关联 | 断言 #80；`CR-20261112-013` §8（分支保护待配置项的收尾） |

## 1. 规则集最终配置（API 权威读取，非截图推断）

规则集 `校验规则`（id 27032663），`enforcement = active`，目标 `~DEFAULT_BRANCH`（main），**bypass 名单 0 条**：

| 规则 | 含义 |
|---|---|
| `deletion` | 禁止删除 main |
| `non_fast_forward` | 禁止强推（`--force` 无效） |
| `pull_request`（`required_approving_review_count = 0`） | 必须经 PR 合并；因是单人仓库，批准数 0，作者可自合 |
| `required_status_checks`（`strict = true`） | 三个检查必须全绿：`文档门禁（spec-lint）`、`后端编译与集成测试（JDK 17）`、`前端测试与构建`；且 PR 分支必须基于最新 main |

核验命令（`GET /repos/{owner}/{repo}/rules/branches/main`）返回上述 4 条规则，即规则真正生效；仅看规则集列表页的 `targeting N branches` 不足以确认（配置过程中曾出现 `targeting 0 branches` 与 `enforcement=disabled` 两种"看起来配好了其实没生效"的状态）。

## 2. 协作流程（自本 CR 起生效）

```
本地改 → git push origin <分支> → 开 PR → 三检查全绿 → Merge → main
```

- **禁止**直推 main：`git push origin main` 会被拒（`protected branch hook declined`）。
- 分支命名沿用项目习惯：文档类用 `docs/` 前缀、测试类 `test/`、构建与依赖类 `chore/`、功能类 `feat/`、修复类 `fix/`，后接简短英文主题。
- 合并前必须三个检查全绿，且 PR 分支已包含最新 main（`strict`）。

## 3. 自动化方式（AI 侧）

- 推分支用**本机 git 凭据**（GCM），与 PAT 无关；
- 开 PR / 合并用 GitHub API，需要 PAT 具备 **Contents: Read and write** 与 **Pull requests: Read and write**；
- 若 PAT 权限不足，退化为"推分支 + 给出 compare 链接，由人点合并"（本轮即先按此路径落地）。

## 4. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |
| 规则生效 | `GET /rules/branches/main` | 4 条规则 active |
| 分支推送 | 实测 `chore/probe-branch-push` | 成功（随后删除） |
| 代码/运行时 | 无改动 | — |