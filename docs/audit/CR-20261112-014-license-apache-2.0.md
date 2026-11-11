# CR-20261112-014 采用 Apache-2.0 许可证；分支保护待配置

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12 选择「Apache-2.0」；分支保护选择「由我（AI）用 token 调 API 配置」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只加许可证文件与文档 |
| 关联 | `CR-20261112-013` §8（两项待定项的收尾） |

## 1. LICENSE

- 采用 **Apache License 2.0**（用户 2026-11-12 确认）。
- 文本取自 `https://www.apache.org/licenses/LICENSE-2.0.txt` 官方原文，**逐行比对通过**：本地 169 行与官方 169 行完全一致，唯一差异是版权行按模板填入 `Copyright 2026 as375438318`。
- 选择理由（用户决定，记录备查）：在 MIT 基础上多一条**修改文件需标注**与**专利授权**条款，适合本项目未来可能涉及企业内网交付的场景。

## 2. 分支保护（未完成，等 token）

需要开启的规则（GitHub → Settings → Branches → Add branch protection rule，分支 `main`）：

| 选项 | 建议值 | 作用 |
|---|---|---|
| Require a pull request before merging | 开 | 禁止直推 main |
| Require status checks to pass | 开，勾选 `文档门禁（spec-lint）`、`后端编译与集成测试（JDK 17）`、`前端测试与构建` | 三条 job 全绿才可合并 |
| Require branches to be up to date before merging | 开 | 防止基于旧 main 的"假绿"合并 |
| Do not allow bypassing the above settings | 开（对 owner 也生效） | 避免"紧急情况"绕过 |

API 调用（需 admin 权限 token，用户提供后由 AI 执行）：

```http
PUT /repos/as375438318/admin-starter/branches/main/protection
{
  "required_status_checks": { "strict": true, "contexts": ["文档门禁（spec-lint）", "后端编译与集成测试（JDK 17）", "前端测试与构建"] },
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 1 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
```

> 注意：单人仓库开启 `required_approving_review_count: 1` 后，自己的 PR 无法自我批准 → **建议单人阶段设为 0**（仍保留 PR 与 CI 门禁），或直接允许 admin 绕过。这一项等 token 到位后按用户选择填写。

## 3. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |
| LICENSE 一致性 | 与官方文本逐行比对 | 169/169 行一致（仅版权行按模板填写） |
| 代码/运行时 | 无改动 | — |