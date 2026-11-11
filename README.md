# 项目基础框架（脚手架）

> 版本：v1.8（可运行脚手架版，2026-11-08）
> 来源：由旧版“开发规范包”迁入（旧目录只留指向本目录的说明，不留两份副本）。
> 作用：把一套“AI 可直接执行”的开发规范 + 模板 + 健康检查 + 契约测试范例 + 可运行前后端工程，做成**复制即用**的新项目起点。

## 0. 运行本仓库（可执行工程）

本仓库已包含可运行的后端与 PC 前端（语言、框架与版本见 `AGENTS.md` §9-B，本文件不重复版本号），功能覆盖登录、用户、角色、菜单、部门、岗位、字典、参数、操作日志、文件上传、个人中心，以及数据权限参考实现（示例业务任务）。

### 0.1 环境依赖

> 版本要求以 `AGENTS.md` §9-B 为唯一来源；本表只写**本机联调时的实际落点**与易错点，不写版本号。

| 依赖 | 要求 | 说明 |
|---|---|---|
| JDK | 见 `AGENTS.md` §9-B | 构建与运行必须使用该 JDK；本机 Maven 默认可能指向更低版本，会报「无效的标记: --release」，需显式设置 `JAVA_HOME` |
| Node / pnpm | 见 `AGENTS.md` §9-B | 前端为 pnpm workspace（逐包显式声明，见 `pnpm-workspace.yaml`），只保留 `pnpm-lock.yaml` 一份锁文件 |
| MySQL | 见 `AGENTS.md` §9-B；本地实例 `127.0.0.1:3307` | 库名 `admin_starter`，应用账号 `admin_app`；本机同时有 3306 的旧实例，**不要连错**（见 `docs/env/local-dev.md`） |
| Redis | 见 `AGENTS.md` §9-B；本地实例 `127.0.0.1:6379` | 缓存会话、数据权限；需设置口令 |
| 端口 | 后端 `8080`，前端 `5173` | Vite 将 `/api` 代理到 8080 |

### 0.2 首次启动准备

1. 数据库口令与 Redis 口令不写入提交配置。复制 `server/web/src/main/resources/application-local.example.yml` 为 `application-local.yml` 并填入真实口令（该文件已在 `.gitignore` 中）。
2. 也可改用环境变量注入：`APP_DB_USERNAME`、`APP_DB_PASSWORD`、`APP_REDIS_HOST`、`APP_REDIS_PORT`、`APP_REDIS_PASSWORD`、`APP_AUTH_SECRET`。
3. 数据库为空库时，Flyway 会在首次启动自动执行 `V1__init_schema.sql`、`V2__init_data.sql`、`V3__demo_business.sql`，建出 14 张系统表 + `biz_demo_task` 示例业务表与初始化数据，无需手工导入。

### 0.3 启动后端

```powershell
# Maven 默认可能指向更低版本的 JDK，先显式指向 AGENTS.md §9-B 指定的 JDK（目录按本机实际情况替换）
$env:JAVA_HOME = "<该 JDK 的安装目录>"

# 方式一：直接从源码启动（开发期推荐）
cd server
mvn -B -DskipTests spring-boot:run

# 方式二：打包为可执行 jar 后启动
cd server
mvn -B -DskipTests clean package
java -jar web\target\admin-starter.jar
```

启动后访问 `http://127.0.0.1:8080/api/v1/auth/me` 应返回未登录的 401 信封（说明服务已就绪）。

### 0.4 启动前端

前端是 pnpm workspace，**在仓库根目录**执行一次安装，再启动 PC 端：

```powershell
# 根目录安装（会链接 apps/pc、apps/shared、packages/ui-tokens）
pnpm install

# 启动 PC 端 dev server（等价于 pnpm --filter @admin-starter/pc dev）
pnpm dev

# 生产构建（输出 apps/pc/dist）
pnpm build
```

浏览器打开 `http://127.0.0.1:5173`（`localhost:5173` 同样可用）。

### 0.5 默认账号与关键说明

- 默认管理员：`admin / admin123`（初始化数据内置；生产必须修改）。
- 数据权限演示账号（密码同为 `admin123`）：`demo_dept`（仅本部门）、`demo_owner`（全部部门但只看自己负责）、`deptmgr`（本部门及下级）、`dev1`（仅本人创建）、`auditor`（指定跨部门）、`viewer`（指定人员）。
- 本地开发 Cookie `Secure=false` 仅用于 `http://` 联调；生产必须使用 HTTPS 并设置 `APP_AUTH_COOKIE_SECURE=true`。
- 每个账号的部门、角色、数据范围与对应验证场景，见 [登录与自测说明](docs/audit/self-test-guide.md)。
- `application-local.yml`、`node_modules`、`target`、`dist` 均不提交。

## 1. 这是什么

这套脚手架有三个部分：

1. **规则层**：`docs/specs/` 五份规范 + `INDEX-facts.md` / `facts.yaml` 事实索引，全部是可判定规则（编号 + MUST/SHOULD/MAY + 通过条件 + 失败信号 + 正反例）。AI 读编号即读规则，不靠记忆。
2. **模板层**：`docs/templates/` 十三份空壳模板，覆盖需求、术语、数据模型、权限、状态机、模块清单、环境清单、ADR、错误码、OpenAPI、配置台账、基线模块索引、基线设计断言。
3. **工具层**：`tools/spec-lint/` 零依赖健康检查；`docs/api/examples/` 前后端契约测试范例；`scripts/init-project.ps1` 一键初始化。

规则之间的冲突优先级、条目状态、变更分级只在 `AGENTS.md` §1 / §3 / §4 定义一次，其余文档只引用编号，不复述。

## 2. 目录结构

```
项目基础框架/
├─ AGENTS.md                 AI 入口：路由、冲突优先级、状态、变更分级、技术栈、目录、模板映射
├─ README.md                 本文件
├─ server/                   后端 Maven 多模块（common / system / auth / web）
├─ apps/
│  ├─ pc/                    前端 PC 端（框架与版本见 `AGENTS.md` §9-B；含 src/router/api/views/composables）
│  ├─ mobile/                跨端应用占位（仅 README，按 AGENTS.md §9-B 选型后再落代码）
│  └─ shared/                跨端共享纯类型（与可运行应用平级；禁止出现 window/document/localStorage）
├─ packages/
│  └─ ui-tokens/             tokens.css 设计令牌（视觉数值唯一定义处）
├─ package.json              根脚本 dev / build / preview + packageManager
├─ pnpm-workspace.yaml       workspace 声明 + allowBuilds（esbuild、vue-demi）
├─ pnpm-lock.yaml            唯一锁文件
├─ docs/
│  ├─ specs/                 规则层（唯一事实源）
│  │  ├─ api-contract.md     接口契约与错误码（CT-*）
│  │  ├─ backend-spec.md     后端编码规范（BE-*）
│  │  ├─ frontend-spec.md    前端编码规范（FE-*）
│  │  ├─ ui-spec.md          UI 设计规范（UI-*）
│  │  ├─ startup-change-process.md  启动与变更流程（PRC-*）
│  │  ├─ INDEX-facts.md      事实索引（人读版，39 条）
│  │  └─ facts.yaml          事实注册表（机器读版，与 INDEX-facts 一一对应）
│  ├─ templates/             十三份模板（需求/术语/数据模型/权限/状态机/模块/环境/ADR/errors.yaml/openapi/配置台账/基线模块/基线断言）
│  ├─ api/
│  │  ├─ openapi.yaml        脚手架契约示例（全文件 EXAMPLE-ONLY）
│  │  └─ examples/
│  │     ├─ backend/         ContractValidationTest.java + pom-snippet.xml
│  │     └─ frontend/        generate-mocks.mjs + package-snippet.json
│  ├─ requirements/          项目层需求与术语（已随包创建，含 .gitkeep）
│  ├─ design/                项目层数据模型/权限/状态机/模块清单（同上）
│  ├─ decisions/             项目层 ADR（同上）
│  ├─ plan/                  项目层迭代计划（同上）
│  ├─ audit/                 项目层审计与健康检查报告（同上）
│  ├─ env/                   项目层环境清单（同上）
│  └─ baseline/              项目层系统基线（模块索引 + 设计断言，含 .gitkeep）
├─ tools/
│  └─ spec-lint/cli.mjs      健康检查脚本（Node 18+，零第三方依赖）
└─ scripts/
   └─ init-project.ps1       初始化脚本（Windows PowerShell）
```

> 前端为 pnpm workspace：可运行应用 `apps/pc`、`apps/mobile`（占位）；共享包与它们平级，`apps/shared`（跨端纯类型）、`packages/ui-tokens`（设计令牌）。应用与共享包的区分见 `FE-DIR-001`（有无入口与 `dev`/`build` 脚本），工作区逐包显式列出，不用 `apps/*` 通配。

## 3. 初始化一个新项目

在脚手架根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-project.ps1 -Target "..\新项目"
```

脚本四步，每步都会停下来等你确认：检查目标目录是否为空 → 确认后复制（排除 `node_modules`/`.git`）并按 `AGENTS.md` §11.1 把模板复制到项目层 → 打印人工事项 → 跑一次 `spec-lint`。

**人工确认（脚本不代填）**：

| 步骤 | 做什么 | 不做的后果 |
|---|---|---|
| 1 | 填 `AGENTS.md` §9-A 项目约束（项目名称、目标平台、团队技能栈、部署环境与预算限制、合规要求、交付时间与里程碑） | 未填不得进入方案讨论（`PRC-STG-001`） |
| 2 | 确认 `AGENTS.md` §9-B 技术栈，逐行确认或改，并补依据 ADR | 技术栈保持“未定”，任何版本号都不得被当作事实引用 |
| 3 | 替换项目层副本的 `EXAMPLE-ONLY`：`docs/api/openapi.yaml` 的 `info`，以及 `docs/requirements`、`docs/design`、`docs/env`、`docs/api/errors.yaml`、`docs/baseline` 中的示例内容 | 示例业务字段会混进真实契约，后续契约测试与类型生成都会错；基线里的示例断言会被当成事实 |
| 4 | 填 `docs/design/config-registry.yaml`（每个配置项的消费点与生效方式）与 `docs/baseline/modules.md`、`assertions.md`（系统现状） | 无台账则无法判断配置是否真生效；无基线则后续每次改动都要重读全部代码 |

## 4. spec-lint 怎么用

零依赖，只要 Node 18+（用到 `node:fs`/`node:path`/`node:child_process`，无 npm install）。

```powershell
# 在当前目录（脚手架根或项目根）跑全部检查
node tools/spec-lint/cli.mjs

# 机器可读输出，给 AI / CI 消费
node tools/spec-lint/cli.mjs --json

# 只检查 Git 暂存文件（适合 pre-commit；非 Git 目录会直接退出码 2）
node tools/spec-lint/cli.mjs --staged

# 指定检查根目录（在任意位置调用）
node tools/spec-lint/cli.mjs --root "<项目根目录>"
```

退出码：`0` 全部通过；`1` 存在 error；`2` 脚本自身异常（未知参数、根目录不存在、`--staged` 不在 Git 仓库）。

`--json` 字段：`root`、`scannedFiles`、`stats`（每条规则检查了多少条）、`summary`（error/warning 数）、`findings[]`（`ruleId`／`severity`／`file`／`line`／`message`）。

### 4.1 已实现的检查（11 条）

| ruleId | 查什么 | 通过条件 | 判定口径（防误报） |
|---|---|---|---|
| `SL-REF-001` | 悬空引用 | 全库引用的受校验编号都有定义。受校验前缀：`CT/BE/FE/UI/PRC/TR/REQ/AC/ADR`（数字编号）与 `ST`（状态编号） | 定义来源：`docs/specs/*.md` 表格首列；状态机 / 需求 / ADR 以项目层文件为准，项目层缺失时回退到同名模板；模板中非定义来源的编号按引用处理；代码围栏内不计 |
| `SL-ID-001` | 编号重复 | 每个编号只定义一次 | 同一编号出现在两处定义行即报，指出首次位置 |
| `SL-ID-002` | 编号跳号 | 按模块分组后编号连续 `001..max` | 分组粒度是“前缀+模块”（如 `UI-PC-TBL`），不跨模块比较 |
| `SL-TODO-001` | TODO 类型 | 类型只能是 `REQ/DATA/PERM/API/UI/OPS` | 只认 ASCII 类型名的 `TODO(REQ):`；中文占位 `TODO(类型)` 视为格式说明；围栏内视为示例 |
| `SL-TODO-002` | 状态与 TODO 冲突 | 同一章节不能既出现 `APPROVED` 又有未解决 TODO | 按原文匹配（不先剥离反引号），只排除代码围栏内内容；查 `docs/` 下的项目层 Markdown，`docs/templates/` 空壳豁免 |
| `SL-PATH-001` | 路径存在性 | 文中 `docs/...`、`tools/...`、`scripts/...` 路径真实存在 | 跳过通配符/占位符；规则表格 ✅/❌ 示例列的路径不校验；项目层目录（`docs/requirements` 等）缺失按 warning |
| `SL-EXAMPLE-001` | `EXAMPLE-ONLY` 残留 | 内容产物文件不得残留 `EXAMPLE-ONLY` | 只查 `docs/api`、`docs/requirements`、`docs/design`、`docs/env`、`docs/decisions`、`docs/baseline`、`docs/plan`、`docs/audit`；这些目录都是项目层内容产物，命中即 **error**（含契约源 `docs/api/openapi.yaml`）；`docs/templates/**` 豁免；`AGENTS.md`/`README.md`/`docs/specs` 中说明规则本身的句子不算残留；项目层目录里同一行带规则编号 `SL-EXAMPLE-001` 的视为“说明规则”，同样不计 |
| `SL-REFMT-001` | 引用格式 | 跨文档引用只写编号，不写章节号/行号 | 只报同一行出现其他 `.md` 文件名、但 `§数字` 前后 30 字符内没有该文件名的跨文档引用；同文档自引用（含“本文件 §N”）允许 |
| `SL-TPL-001` | 模板落位 | `AGENTS.md` §11.1 的 13 个目标存在（ADR 目标按 `docs/decisions/ADR-*.md` 通配匹配，至少一个即通过） | 脚手架阶段按 warning；初始化后应为通过 |
| `SL-FACT-001` | 索引一致性 | `facts.yaml` 的 `owner`/`refs` 与 `INDEX-facts.md` 的同一事实一致 | 按“定义方 + 引用集合”多重集合比对，允许两份文件行顺序不同；`AGENTS.md#3` 与 `AGENTS.md` §3 归一化后比较；`refs` 按集合比较 |
| `SL-CFG-001` | 配置项台账 | `docs/design/config-registry.yaml`（缺失时回退 `docs/templates/config-registry.yaml`）中每个配置项六项必填齐全、`key` 唯一、`effect` 只能是 `STARTUP/HOT/DISPLAY_ONLY`、`effect=STARTUP` 的 `afterChange` 含“重启” | 按最小 YAML 子集解析（`- key:` 起一条，缩进 `字段: 值`）；不校验 `consumedBy` 是否真实指向代码（属评审） |

`warning` 不算不通过：脚手架自身没有项目层文件，所以 `docs/design/*` 这类引用与模板落位会以 warning 汇总出现，初始化后自然消失。`SL-EXAMPLE-001` 对 `docs/api` 等 8 个项目层内容目录一律按 **error** 判定——契约源 `docs/api/openapi.yaml` 里残留脚手架示例字段同样阻断，替换后清零。明细用 `--json` 看。

### 4.2 不做什么（本轮边界）

- 不做自动修复：只报位置和原因，语义修改由人或 AI 按规则改。
- 不做“双定义”启发式检测（同一事实出现两个定义方）：那是 P1，靠 `INDEX-facts.md` / `facts.yaml` 人工维护 + 评审。
- 不做表格列数与代码围栏配对、版本号与变更记录同步：P1，脚本不覆盖，见 `startup-change-process.md` §4.1。
- 不检查代码实现，只检查文档与契约文件的一致性。
- 不校验配置台账的 `consumedBy` 是否真实指向读取点：`SL-CFG-001` 只查结构与一致性，消费点真实性靠评审与 `BE-CFG-007` 人工确认。

## 5. 契约测试怎么接入

两块示例都在 `docs/api/examples/`，都不在脚手架里跑（本机没有对应工程），复制到项目后按注释改几行即可。

### 5.1 后端（Spring Boot + MockMvc）

- 文件：`examples/backend/ContractValidationTest.java`、`examples/backend/pom-snippet.xml`
- 依赖：`com.atlassian.oai:swagger-request-validator-mockmvc:2.44.9`（Maven Central 已核对存在）
- 做法：把 `docs/api/openapi.yaml` 复制到 `src/test/resources/openapi.yaml`，测试里用 `OpenApiValidationMatchers.openApi().isValid("classpath:openapi.yaml")` 让真实响应与契约逐字段比对；再补几条关键断言（信封、分页四字段、`X-Trace-Id`）。
- 复制后要改：包名、`@SpringBootTest(classes=...)` 的启动类、接口路径与请求体、契约文件位置。

### 5.2 前端（类型 + mock）

- 文件：`examples/frontend/generate-mocks.mjs`、`examples/frontend/package-snippet.json`
- 依赖：`openapi-typescript ^7.13.0`、`msw ^3.0.4`（要求 Node ≥ 22.12）、`js-yaml ^4.1.0`
- 做法：脚本先调 `openapi-typescript` 生成 `src/api/schema.d.ts`，再从 `openapi.yaml` 的 path 生成 MSW handler（返回标准信封 + `X-Trace-Id` 头）。
- 复制后要改：三个路径常量、handler 里 `data` 的业务示例、浏览器端 `setupWorker`／测试端 `setupServer` 的装配。

### 5.3 契约的硬约束（来自规则层，不在示例里重复定义）

信封 `code/message/data/traceId` 且 `data` 必存在见 `CT-RESP-001`；禁止 204/空 body 见 `CT-RESP-009`；分页四字段见 `CT-PAGE-001`、默认值与上限见 `CT-PAGE-002`；`X-Trace-Id` 与响应体 `traceId` 一致见 `CT-TRACE-002`；校验错误结构见 `CT-ERR-006`；基础错误码见 `CT-ERR-011`；删除语义见 `CT-HTTP-007`。

## 6. facts.yaml（事实注册表）

`docs/specs/facts.yaml` 是 `INDEX-facts.md` 的机器可读派生副本，字段：`id / name / owner / fields / refs / keywords`。

- **`INDEX-facts.md` 是源**，先改 md 再同步 yaml；`id` 只追加不复用（`PRC-IDX-003`）。
- `owner` 是唯一定义方：规则编号，或 `AGENTS.md#3` 这类入口文件节号，或 `PRC-LAY-007` 登记的项目层资产路径（如 `docs/design/permission-matrix.md`）。
- 一致性由 `SL-FACT-001` 校验；文件内出现的规则编号由 `SL-REF-001` 校验存在性。
- 本轮**不做**“双定义”启发式检测；事实变更仍按 `PRC-IDX-003` 同批更新 md 与 yaml。

## 7. 自动与人工的边界

| 项 | 自动 | 仍需人工 |
|---|---|---|
| 悬空引用 / 重复 / 跳号 | spec-lint | 编号语义是否该拆或合 |
| TODO 残留与类型 | spec-lint | TODO 的结论、责任人、是否可解除 |
| `APPROVED` 与 TODO 冲突 | spec-lint | 条目是否真已签核 |
| 路径存在性 | spec-lint | 路径该不该新建、该放哪 |
| `EXAMPLE-ONLY` 残留 | spec-lint（`SL-EXAMPLE-001`） | 示例业务字段替换成什么 |
| 跨文档引用格式 | spec-lint（`SL-REFMT-001`，warning 级） | 该不该跨文档引用、引用哪条 |
| 模板落位 | spec-lint（`SL-TPL-001`） | 模板内容是否已按项目改写 |
| 索引 md/yaml 一致 | spec-lint（`SL-FACT-001`） | 事实本身是否需要改定义方 |
| 配置项是否真的生效 | spec-lint（`SL-CFG-001`，结构与 STARTUP/afterChange 一致性） | `consumedBy` 是否真实指向代码、改值后副作用是否出现（`BE-CFG-007`、`PRC-DOD-001`） |
| 契约与实现一致 | 后端契约测试示例 | 契约本身是否该改（走 `PRC-STG-006` 变更） |
| 规则冲突 | 无（靠单一事实源 + 引用编号） | 冲突裁决按 `AGENTS.md` §1，双定义必须停机上报 |
| 技术栈版本 | 无 | 逐行确认并写依据 ADR，未确认保持“未定” |
| 事实双定义 | 无（P1 才做） | 评审 + `INDEX-facts.md` 维护 |

## 8. 维护与变更

- 规则变更顺序：`docs/specs` → 模板 → 本 README → 代码（详见 `startup-change-process.md` `PRC-STG-006`）。
- 模板 → 目标路径的唯一映射表在 `AGENTS.md` §11.1，本文件不复述路径。
- 脚手架自身升级：改完在下方变更记录写一条，再把新版本复制到已初始化的项目（只覆盖 `docs/specs`、`docs/templates`、`tools`、`scripts`，不碰项目层文件）。

### 8.1 版本管理（Git）

- 仓库已 `git init`，默认分支 `main`；首个提交是现有工程与文档基线的整体导入，此后按 `AGENTS.md` 的变更档位分批提交。
- `.gitattributes` 统一 **LF**（`* text=auto eol=lf`，`.bat`/`.cmd` 例外为 CRLF），避免 Windows 上 `core.autocrlf=true` 造成整文件假改动；本仓库已显式设置 `core.autocrlf=false`。
- `.gitignore` 排除三类内容：**凭据**（`docs/env/local-dev.md`、`application-local.yml`）、**运行期数据**（`.local/` 含 MySQL 数据目录、`logs/`、`data/uploads`、`server/web/data/`）、**构建产物**（`node_modules`、`target`、`dist`、`.trae-html-share-packages`）。
- 克隆后需自行创建 `server/web/src/main/resources/application-local.yml`（照 `application-local.example.yml`）与 `docs/env/local-dev.md`，否则 `spec-lint` 的路径检查会多出对应 warning（这两份被忽略是有意的，不要为“文档完整”把口令提交进去）。
- CI（`.github/workflows/ci.yml`）在**推送后**才生效；本地等价命令：`node tools/spec-lint/cli.mjs`、`cd server && mvn -B -pl web -am -DskipTests package`、`pnpm build`。

## 9. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.14 | 2026-11-11 | 填实 `AGENTS.md` §9：§9-A 项目约束（平台/团队/部署/合规/里程碑）与 §9-B 技术选型九行由用户逐项确认后落笔，技术栈从「无定义方」转为 `AGENTS.md` §9-B 唯一定义（版本取自 pom/package.json/产物 jar，非猜测）；新增 `ADR-005` 记录选型理由与否决方案、`CR-20261111-001` 作为通用层变更记录；事实索引新增 `F-TECH-STACK`（43 条）；跨端框架与前端测试框架保持「未定」。同时按 §1「README 只引用 §9-B、不写版本号」删除本文件 §0.1 与环境说明中的版本号，并修正该表「Redis 6+」的错误描述（实为 5.0） |
| v1.13 | 2026-11-11 | 建立版本库：`git init`（分支 `main`）+ `.gitattributes`（统一 LF）+ `.gitignore` 补齐（`server/web/data/`、根 `data/`、`.trae-html-share-packages/`、`.screenshots/`）；README 新增「版本管理」小节，说明凭据与运行期数据的忽略口径及克隆后的补齐动作 |
| v1.12 | 2026-11-11 | 评审 P2 #4–#11 批量修复：CSRF 无来源但有凭据一律 403（含 Referer 回落与统一信封）、登录停用改 401/20103、`page<1` 统一 400、任务 cron 时区改配置项 `app.job.zone`、任务线程池改为 `@Bean` 装配（新增 `JobSchedulingConfig`）、分片续传键加用户维度、异步导出校验导出人状态、下载/预览补 CSP 头；回填断言 #65–#67 与定时任务设计说明 |
| v1.11 | 2026-11-11 | 新增 CI 门禁 `.github/workflows/ci.yml`（三条 job：spec-lint / 后端 `mvn -pl web -am -DskipTests package` / 前端 `pnpm build`）；修复前端「所有 `/auth/*` 都跳过 401 静默刷新」导致 access 过期后刷新页面被误登出的缺陷；文件下载/预览的越权响应由 `403` 改为与不存在统一 `404`（回归 `CT-HTTP-008`/`CT-ERR-009`，契约升至 1.0.1）；同步回填断言 #36、文件能力设计与评审报告处理记录 |
| v1.10 | 2026-11-11 | `SL-EXAMPLE-001` 新增 `PROJECT_CONTENT_DIRS`：`docs/api`（含契约源 `openapi.yaml`）与 `docs/plan`、`docs/audit` 改为**项目层 error** 判定，不再只报 warning；`docs/api/openapi.yaml` 去掉脚手架示例路径/schema/EXAMPLE-ONLY 文案并补契约变更记录（CT-VER-005），同时修复其 `Content-Disposition: inline` 未加引号导致整份契约无法解析的语法错误；同步回填 `docs/baseline/assertions.md` #40（缓存命名空间 7→8）、`docs/requirements/system-modules.md`（岗位/示例业务导入导出、命名空间 8 类）、`docs/audit/final-report.md`（标注为历史快照） |
| v1.9 | 2026-11-10 | 共享包由 `packages/shared` 迁至 `apps/shared`（与可运行应用平级），`pnpm-workspace.yaml` 改为逐包显式声明；删除 `packages/platform`、`packages/contracts` 空壳；`FE-DIR-001`/`FE-COMBO-003`/`FE-DIR-004`/`FE-DOC-003` 与目录树同步（`CR-20261110-001`） |
| v1.8 | 2026-11-08 | `SL-TPL-001` 纳入 ADR 模板落位（目标按 `docs/decisions/ADR-*.md` 通配匹配），覆盖 13 个模板，与 `AGENTS.md` §11.1 口径一致 |
| v1.7 | 2026-11-08 | 补“配置是否真生效”与“系统现状基线”两层：新增 `docs/templates/config-registry.yaml`（台账模板，落位 `docs/design/config-registry.yaml`）、`docs/templates/baseline-modules.md`、`docs/templates/baseline-assertions.md`（落位 `docs/baseline/`）+ `docs/baseline/.gitkeep`；`spec-lint` 新增 `SL-CFG-001`（11 条）；`AGENTS.md` 补第 0/8 步、开场自证、多工具入口、门禁分工；索引 39 条 |
| v1.6 | 2026-11-08 | 前端由单端 `web/` 迁为 pnpm workspace：`apps/pc` + `packages/{shared,ui-tokens}`（`apps/mobile`、`packages/{platform,contracts}` 为占位）；根脚本 `pnpm dev` / `pnpm build`；目录结构与启动命令同步更新 |
| v1.5 | 2026-11-08 | 运行说明补充 `mvn spring-boot:run`、修正 jar 路径；新增 `V3__demo_business.sql`（示例业务表与数据权限演示账号）；补充数据权限演示账号说明 |
| v1.4 | 2026-11-08 | 新增“运行本仓库”章节（依赖、首次配置、后端/前端启动、默认账号、端口、`JAVA_HOME`、本地 Cookie `Secure` 说明）；目录树补入 `server/`、`web/` |
| v1.3 | 2026-11-08 | spec-lint 修复 `--staged` 定义范围、`SL-TODO-002` 失效并把范围改为项目层 Markdown、定义行跳过引用检查、编号前缀覆盖不足；新增 `SL-EXAMPLE-001`、`SL-REFMT-001`、`SL-TPL-001`、`SL-FACT-001`；README 去掉本机绝对路径并修正检查数量、定义来源与元数据口径 |
| v1.2 | 2026-11-08 | 从“开发规范包”改为“项目脚手架”：新增 `tools/spec-lint`（四条 P0 检查 + JSON + `--staged`）、`docs/specs/facts.yaml`、`docs/api/openapi.yaml` 与前后端契约测试示例、`scripts/init-project.ps1`；补齐项目层空目录；本 README 重写为脚手架使用说明 |
| v1.1 | 2026-11-08 | 补齐十份模板与 `AGENTS.md` §11.1 模板映射、`EXAMPLE-ONLY` 约定、初始化三步人工确认、变更记录 |
