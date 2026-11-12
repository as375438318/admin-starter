# CR-20261112-023 前端框架主版本升级（Vite 8 / Vitest 5 / Vue Router 5 / Pinia 4）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「那顺序开始，没问题的 PR 就合并」（批准 `EVAL-20261112-001` 的结论） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：不改接口、表结构、权限码、错误码；改动落在构建与测试工具链 + 视图加载方式。选型决策见 `ADR-007` |
| 关联 | `ADR-007`、`EVAL-20261112-001`、`CR-20261112-018`（主版本人工发起）、断言 #88 |

## 1. 变更内容

| 依赖 | 前 | 后 |
|---|---|---|
| `vite` | 5.4.11 | **8.3.7** |
| `@vitejs/plugin-vue` | 5.2.0 | **6.0.10** |
| `vitest` | 3.2.7 | **5.1.0** |
| `@vitest/coverage-v8` | 3.2.7 | **5.1.0** |
| `vue-router` | 4.4.5 | **5.5.0** |
| `pinia` | 2.2.6 | **4.1.0** |

`AGENTS.md` 技术栈表同步更新（前端框架行、测试框架行）。

## 2. 代码改动（唯一一处）

`apps/pc/src/router/index.ts`：把静态路由里直接 import 的视图改为**懒加载**。

原因：`viewModules = import.meta.glob('../views/**/*.vue')` 已把 `views/` 下所有页面纳入动态引入范围，再对同一文件写静态 `import` 会让打包器判定"动态引入无效"：

```
[INEFFECTIVE_DYNAMIC_IMPORT] src/views/error/403.vue is dynamically imported by
src/router/index.ts but also statically imported by src/router/index.ts
```

这是**升级前就存在**的问题，旧打包器不报、Vite 8 变严格才显形。改成 `() => import(...)` 后：

- 警告清零（403/404 与登录/仪表盘/我的消息都进独立 chunk）；
- 业务入口 chunk 从 39.10 kB 降到 **32.46 kB**。

## 3. 验证（三层，全部实跑）

| 项 | 命令 | 结果 |
|---|---|---|
| 构建（删 dist + tsbuildinfo 干净重建） | `pnpm build` | 通过，**0 条 INEFFECTIVE_DYNAMIC_IMPORT** |
| 单元测试 | `pnpm test:unit` | **95/95 通过** |
| **E2E** | 起后端 + `pnpm exec playwright test` | **7/7 通过**（13.9s） |

E2E 覆盖的是升级最可能踩坏的地方：后端菜单下发的**动态路由注册**（vue-router 5 的 `addRoute`）、Pinia 鉴权 store、通知发布/文件上传下载/导出三条业务链。

## 4. 兼容性与回滚

- 接口、表结构、权限码、错误码**均未变**；前端行为对用户可见处无变化（仅加载时机）。
- 回滚：本 CR 单提交，`git revert` 后 `pnpm install` 即回旧版本线（锁文件一并回滚）。

## 5. 遗留

- `element-plus` 仍为 2.8.x（同主版本 minor 未升），建议下批与 `vue`/`typescript` 一起做，并跑一遍 E2E；
- `vitest.config` 的覆盖率阈值未在 Vitest 5 下复核（本 CR 只跑 `test:unit`，未跑 coverage 全量）；
- E2E 仅 7 条，合并后建议人工点重点页（列表、表单、导入导出、权限按钮）。