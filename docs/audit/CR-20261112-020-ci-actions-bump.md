# CR-20261112-020 CI 动作手动升级（setup-java@v6、pnpm/action-setup@v6）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户「小版本就升级」原则；Dependabot #1/#3 反复与 main 冲突 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改 CI 动作版本 |
| 关联 | `CR-20261112-018`；断言 #85 |

## 1. 背景

`dependabot.yml` 首次扫描开出三个 GitHub Actions 升级 PR：

| PR | 升级 | 结果 |
|---|---|---|
| #2 | `actions/checkout` 4 → 7 | **已合并** |
| #1 | `actions/setup-java` 4 → 6 | 与 main 冲突（`ci.yml` 同行被 #2 改过） |
| #3 | `pnpm/action-setup` 4 → 6 | 同上 |

反复 `@dependabot rebase` 的等待成本高于直接改，且改动本身是单行替换、无逻辑风险。

## 2. 改动

`ci.yml` 与 `e2e.yml` 中：

- `actions/setup-java@v4` → **@v6**
- `pnpm/action-setup@v4` → **@v6**

`actions/checkout@v7` 已由 #2 合入。

## 3. 一个自我纠正（过程记录）

第一版我"顺手"把 `actions/setup-node@v4 → v6`、`actions/upload-artifact@v4 → v5` 也改了——但 **Dependabot 并没有提议这两个**，说明它们已经是当前最新（或未到升级条件）。那两个版本号是我凭印象猜的，已回退。

**教训**：升级类改动只接受"有依据的目标版本"（Dependabot 提议 / 官方发布说明），不凭印象写版本号——猜错的后果是 CI 全挂在这些步骤上。

## 4. 验证方式

本 PR 自身的 CI 就会使用新版本动作运行 → **PR 三条检查全绿即等于"新动作版本可用"的实测证据**（比本地 YAML 语法检查强得多）。

## 5. 后续

合并后关闭 #1、#3，说明改动已并入 main。