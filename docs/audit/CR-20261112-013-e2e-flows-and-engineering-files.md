# CR-20261112-013 业务链路 E2E 扩展与工程化小件

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「1 2 3 按照顺序实行」（第 2、3 项） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：不改运行时行为（E2E 只加测试；工程化文件不改代码） |
| 关联 | 断言 #78、#79；E2E 4 → **7 条** |

## 1. 背景

- E2E 只有 4 条冒烟（登录、未登录跳转、无权限落状态页、数据权限），**三条最费人工的业务链路**（通知发布、文件上传、数据导出）没有覆盖，重构后坏了只能靠人点。
- 仓库缺 `LICENSE`、`Dockerfile`、`.dockerignore`、`.editorconfig`、`.nvmrc`、`dependabot.yml`、PR 模板，多机协作时缩进/换行/依赖升级全靠口头约定。

## 2. 范围一：E2E 扩展（4 → 7 条，`apps/pc/e2e/flows.spec.ts`）

| 用例 | 断言口径 |
|---|---|
| **发布通知** | UI 打开抽屉填标题/内容 → 发布 → 抽屉关闭 + 列表能搜到 → 接口 `total` 增加，且该通知有应读人（范围解析真的执行了） |
| **上传文件** | `setInputFiles` 上传文本 → 列表出现文件名 → **`GET /files/{id}/download` 取回的字节与上传内容完全一致**（证明不是只写了一条记录） |
| **导出示例任务** | 点「导出」→ 同步通道捕获 `download` 事件且文件名 `*.xlsx`；异步通道则出现「已转入后台导出」提示；两种都要求**无错误提示** |

跑法：后端需在 8080、前端由 playwright `webServer` 起；`pnpm --filter @admin-starter/pc test:e2e`。
**实测**：7/7 通过（12.7s）。

## 3. 范围二：工程化小件

| 文件 | 作用 | 关键决定 |
|---|---|---|
| `.editorconfig` | 统一 UTF-8/LF/缩进 | Java/XML 4 空格、前端 2 空格；**Markdown 不裁行尾空格**（两个空格是换行语义） |
| `.nvmrc` | Node 版本约定（22） | 与 `AGENTS.md` §9-B 的 `Node.js ≥ 22` 对齐 |
| `Dockerfile` | 后端多阶段镜像 | `maven:3.9-eclipse-temurin-17` 构建 + `eclipse-temurin:17-jre` 运行；**非 root 运行**；连接信息全走环境变量（镜像内无口令）；上传目录提示必须挂卷 |
| `.dockerignore` | 构建上下文瘦身 | 排除 `node_modules`、`target`、`docs`、`apps`、`**/data`、`application-local.yml` |
| `.github/dependabot.yml` | 依赖升级 PR | maven（/server）、npm（/ 根，pnpm workspace）、github-actions 三类；minor/patch 分组，避免每周十几个 PR |
| `.github/pull_request_template.md` | PR 自检清单 | 直接对应 `AGENTS.md` §7 门禁与 §6.2 文档回填项，避免"忘了回填基线" |

## 4. 过程中发现的两条事实（已入断言）

1. **前端路由由后端菜单下发**（`GET /auth/routes`）：E2E 里我第一版按视图目录猜了 `/system/notice`、`/system/file`，两条用例都落到「页面不存在」。真实路径是 `/system/notices`、`/system/files`、`/demo/tasks`。
2. **在父 pom 上跑 `spring-boot:run` 会失败**（`Unable to find a suitable main class`）：本地起后端要么 `mvn -pl web spring-boot:run`（需先 install 兄弟模块），要么直接用已构建的 `server/web/target/admin-starter.jar` 加 `--spring.config.additional-location` 指向 `application-local.yml`。

## 5. 涉及事实

| 事实 | 变更前 | 变更后 |
|---|---|---|
| E2E 用例 | 4 | **7** |
| 工程化文件 | 仅 CI workflow | +7 个文件 |

## 6. 兼容性 / 回滚

- 运行时零改动：E2E 是测试，其余是配置与文档类文件。
- `git revert` 本 CR 提交即可。

## 7. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| E2E 全量 | `pnpm --filter @admin-starter/pc test:e2e`（后端在跑） | **7/7 通过** |
| 前端构建 | `pnpm --filter @admin-starter/pc build` | 通过（`index.js` 1.32MB / gzip 428KB） |
| 前端单测 | `pnpm --filter @admin-starter/pc test:unit` | 95/95 通过 |
| 后端全量 | `mvn -B -pl web -am verify` | 170 用例通过 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 8. 未完成项（需人工决定，不做默认选择）

| 项 | 为什么不能自行决定 |
|---|---|
| **`LICENSE`** | 许可证是授权条款（对外授予使用/修改/分发权），属法律决定；仓库当前无许可证＝保留所有权利。需所有者选择 MIT / Apache-2.0 / 保持专有。 |
| **分支保护规则** | 需要在 GitHub 仓库设置里开启（require PR + require CI 通过 + 禁止直推 main），仓库文件无法表达；需要所有者操作或提供有 admin 权限的 token。 |

## 9. 遗留

- 等保二级/三级条款拆解、前端包体拆分（`index.js` 1.32MB）仍未开始。