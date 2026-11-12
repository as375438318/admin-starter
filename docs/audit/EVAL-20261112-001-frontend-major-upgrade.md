# EVAL-20261112-001 前端主版本成套升级评估（真实执行，非纸面推演）

> 结论先说：**技术上可升，零源码改动，构建 + 95 个单测 + 7 条 E2E 全通过**。
> 本文件是评估记录，**不构成合并决定**；正式升级需开 ADR 并更新 `AGENTS.md` 技术栈表。

## 1. 为什么做这次评估

Dependabot 报出前端 5 个主版本跳跃，之前的策略是"主版本不自动升、每年评估一次"。本次按用户指令**真升一遍**，用事实回答"到底能不能升、要改什么"，而不是凭版本号猜风险。

## 2. 升级内容（实际安装版本）

| 依赖 | 升级前 | 升级后 |
|---|---|---|
| `vite` | 5.4.11 | **8.3.7** |
| `@vitejs/plugin-vue` | 5.2.0 | **6.0.10** |
| `vitest` | 3.2.7 | **5.1.0** |
| `@vitest/coverage-v8` | 3.2.7 | **5.1.0** |
| `vue-router` | 4.4.5 | **5.5.0** |
| `pinia` | 2.2.6 | **4.1.0** |

未升（同主版本，可另批处理）：`vue 3.5.13`、`element-plus 2.8.8`、`typescript 5.6.3`、`axios 1.7.9`。

## 3. 验证结果（逐步升级，每步都跑）

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 升 `vite@8` + `@vitejs/plugin-vue@6` | 构建通过（5.2s） |
| 2 | 仅升 vite 时跑单测（vitest 仍是 3） | **95/95 通过**（Vitest 3 与 Vite 8 未冲突） |
| 3 | 升 `vitest@5` + `coverage-v8@5` | **95/95 通过**（2.86s，比之前更快） |
| 4 | 升 `vue-router@5` + `pinia@4` | 构建通过、单测 95/95 |
| 5 | 删 `dist` 与 tsbuildinfo 后干净重建 | 构建通过，产物正常 |
| 6 | **起后端 + Playwright E2E** | **7/7 通过**（16.7s） |

**源码改动量：0 行**。唯一变更文件是 `apps/pc/package.json` 与 `pnpm-lock.yaml`。

第 6 步是关键：前 5 步只证明"能编译、单测过"；E2E 才验证了真实运行时——包括**后端菜单下发的动态路由注册**（vue-router 5 的 addRoute 行为）、Pinia 鉴权 store、通知发布/文件上传下载/导出三条业务链路。

## 4. 产物体积变化（分包后的对照）

| chunk | 升级前 | 升级后 |
|---|---|---|
| 业务 `index-*.js` | 39.10 kB | **33.31 kB** |
| `vendor-vue-*.js` | 116.56 kB | **29.31 kB** |
| `vendor-*.js` | 193.14 kB | 257.42 kB |
| `vendor-element-plus-*.js` | 967.06 kB | 945.29 kB |

总体略降；体积结构变化属打包器版本差异，不影响门禁（业务 chunk ≤ 400KB 门禁仍远未触顶）。

## 5. 过程中发现的既有问题（与本次升级无关）

```
[INEFFECTIVE_DYNAMIC_IMPORT] src/views/error/403.vue is dynamically imported by
src/router/index.ts but also statically imported by src/router/index.ts
```

`403.vue` / `404.vue` 在路由里同时被**静态与动态**引入，动态导入无法分包。这是**升级前就存在**的问题（旧版构建不报此警告，是打包器变严格了才显形）。修法：路由里统一用动态 `() => import(...)`，静态引用只用于类型或直接移除。

## 6. 风险与未覆盖项

| 项 | 说明 |
|---|---|
| E2E 覆盖面 | 只有 7 条，未覆盖全部页面；升级后建议人工点一遍重点页（列表、表单、导入导出、权限按钮） |
| `element-plus` 未升 | 2.8.8 → 2.14.8 属同主版本 minor，但组件行为有小改（如表格/日期选择器），建议与主版本升级**同批**做，一次回归 |
| `typescript` 未升 | 5.6.3 保持；升级会带来类型检查严格化，属另一件事 |
| 覆盖率阈值 | `vitest.config` 里的 coverage 阈值在 Vitest 5 下的默认行为需复核（本次未跑 coverage 全量） |
| Node 版本 | Vite 8 要求 Node ≥ 20（本项目 ≥ 22，满足） |
| 回滚 | 单分支单提交，`git revert` 即回净 |

## 7. 建议路径（需人工拍板）

1. **开一份新 ADR**（`docs/decisions/` 下按现有编号顺序新增，不预先占号）：记录本次评估结论与备选方案（不升 / 只升打包器 / 全套升 / 分两年升）；**已于 2026-11-12 落地为 `ADR-007`（方案 C 成套升级，已接受）**；
2. **更新 `AGENTS.md` 技术栈表**（该节是技术栈唯一定义处）到新版本线；
3. **同批升 `element-plus` 到 2.14.x**，跑全量 + E2E + 人工点关键页；
4. **顺手修 `403/404` 的动静双引入**（独立小改动）；
5. 合并后删除 `dependabot.yml` 里针对前端的 major 忽略？**不删**——仍应保持"主版本人工发起"。

## 8. 复现步骤

```powershell
cd apps/pc
pnpm add -D vite@8 @vitejs/plugin-vue@6 vitest@5 @vitest/coverage-v8@5
pnpm add vue-router@5 pinia@4
pnpm build
pnpm test:unit
# 后端需在 8080（java -jar server/web/target/admin-starter.jar --spring.profiles.active=dev ...）
pnpm exec playwright test
```