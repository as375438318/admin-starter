# CR-20261112-017 前端包体拆分（vendor 分包 + 业务 chunk 体积门禁）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「继续」（按清单第 2 项：`index.js` 1.29MB） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改构建配置与 CI，不改业务行为 |
| 关联 | 断言 #82；`apps/pc/vite.config.ts`；`.github/workflows/ci.yml` |

## 1. 背景

构建产物 `index-*.js` **1,318 kB / gzip 428 kB**，Vite 每次构建都报"chunk 大于 500 kB"。原因是 `main.ts` 里 `import ElementPlus from 'element-plus'` + `import * as ElementPlusIconsVue` 全量引入，全部被打进主包，业务代码一起变、一起失效缓存。

## 2. 改动

**`apps/pc/vite.config.ts`**：按"变更频率"分包，而不是按体积硬切：

| chunk | 内容 | 理由 |
|---|---|---|
| `vendor-element-plus` | Element Plus + 图标 | 体积最大、升级频率最低 → 长期缓存 |
| `vendor-vue` | Vue 运行时 + Router + Pinia | 与业务一起变；独立后业务发版不必让浏览器重下框架 |
| `vendor` | 其余第三方（axios 等） | 零散但稳定 |
| `index` | 业务入口 | 期望保持很小 |

**`.github/workflows/ci.yml`**：新增"业务 chunk 体积门禁（≤ 400KB）"步骤——分包后如果又有人把大依赖静态引进主包，构建期直接失败，而不是等线上变慢。

`chunkSizeWarningLimit` 由默认 500 调到 1000：`vendor-element-plus` 必然超过 500，警告会变成噪音；把闸门放在 CI 的业务 chunk 检查上，比看警告更硬。

## 3. 效果（本地实测）

| 文件 | 改前 | 改后 |
|---|---|---|
| `index-*.js`（业务入口） | **1,318.7 kB**（gzip 428.0 kB） | **39.1 kB**（gzip 14.8 kB） |
| `vendor-element-plus-*.js` | — | 967.1 kB（gzip 297.9 kB） |
| `vendor-*.js` | — | 193.1 kB（gzip 70.2 kB） |
| `vendor-vue-*.js` | — | 116.6 kB（gzip 45.2 kB） |

**诚实说明**：这是**缓存与并行下载**的改善，**总下载量基本不变**（仍要下载 967 kB 的 Element Plus）。真正的瘦身要靠 Element Plus / 图标**按需引入**（`unplugin-vue-components` + `unplugin-auto-import`），那会改变组件注册方式并新增依赖，按 `AGENTS.md` §4.1 需要单独评估（属引入新依赖 + 改变启动路径），不在本 CR 范围。

## 4. 兼容性与回滚

- 运行时行为不变；仅构建产物切分方式变化。
- 回滚：`git revert` 本 CR，构建自动回到单包。

## 5. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 构建 | `pnpm --filter @admin-starter/pc build` | 通过（6.1s），无 >500KB 警告 |
| 单元测试 | `pnpm --filter @admin-starter/pc test:unit` | 95/95 通过 |
| 体积门禁 | CI 新增步骤（本地等价脚本） | 40,315 字节 ≤ 409,600 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 6. 遗留

- Element Plus / 图标按需引入（预计可砍掉 60–70% 的 vendor-element-plus 体积与大部分 CSS），需单独 ADR 评估。
- `element-plus/dist/index.css` 387 kB 为全量样式，按需引入后同样可大幅下降。