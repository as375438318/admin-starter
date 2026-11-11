# 前端编码规范（工程与代码）

| 项目 | 内容 |
|---|---|
| 版本 | **v1.9**（2026-11-10） |
| 状态 | AI 可执行规则集；规则编号 `FE-*`；每章末尾附自检清单 |
| 适用范围 | Web / H5 / 小程序 / App 前端通用规则，不绑定具体业务 |
| 配套文档 | 《前端UI设计规范》《接口契约与错误码规范》《后端编码规范》 |
| 事实源 | 接口契约由《接口契约与错误码规范》定义；视觉数值由《前端UI设计规范》定义；本文只管前端实现 |
| 最终读者 | AI 编码助手；人类仅用于评审决策与人工确认项 |

**变更记录**

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.9 | 2026-11-10 | 按 `CR-20261110-001` 修订目录相关规则：`FE-DIR-001` 改为「共享包与可运行应用平级、集中管理」，补「应用 vs 共享包」判据（有无入口与 `dev`/`build` 脚本）与 `pnpm --filter "./apps/*"` 会匹配共享包的实测事实；`FE-COMBO-003` 引用路径改 `apps/shared`；`FE-DIR-004` 随 `packages/platform` 空壳删除改为「平台差异实现放使用它的应用内」；`FE-DOC-003` 不再指向已删除的 `packages/contracts`；`D10`、`FE-DEP-001` 示例、§14 反例同步。规则总数不变（123 条） |
| v1.8 | 2026-11-09 | 新增三条规则：`FE-DIR-007`（可复用状态逻辑放 `composables/`）、`FE-CODE-013`（页面体量与职责拆分）、`FE-COMP-008`（列表页骨架统一实现）。原拟编号 `FE-DIR-006`、`FE-COMP-004` 已被占用（barrel 循环依赖、`v-model` 单向流），按现有体系顺延；同步 §14 反例清单与 §20 索引 |
| v1.7 | 2026-11-08 | `FE-ERR-005` 示例字段改为 `data.errors`；`FE-ERR-001` 错误类别改为按 `CT-ERR-001` 的码段与 `CT-HTTP-003` 的状态码派生，不再引用不存在的“类别”字段；`FE-AUTH-006` 与 D23 权限码来源改指项目层权限矩阵；§20 跨文档引用表删除第二套优先级链，改为只引用 `AGENTS.md` §1 |
| v1.6 | 2026-11-08 | 技术栈与版本改为引用 `AGENTS.md` §9-B，删除 `Vue 3.5`／`uni-app`／`pnpm 9/10` 等硬编码；冲突优先级改为引用 §1；令牌规则拆为“协议载体（`CT-AUTH-*`）”与“本地存储”；`FE-AUTH-006` 权限码改为引用权限矩阵；写操作重试改为引用 `CT-IDEM-005`；`FE-REQ-014` 示例命名对齐 `CT-RESP-007`；引用只写编号 |
| v1.5 | 2026-11-07 | 全量改为 AI 可执行规则集：`FE-*` 编号、MUST/SHOULD/MAY、STATIC/PATTERN/CONTEXT、通过/失败条件、正反例；每章自检清单；新增自检流程、人工确认、评审提示词、编号索引；前端不再重复定义契约与视觉数值 |
| v1.4 | 2026-11-07 | 跨端技术路线、组件库、令牌存储、App 打包等决策定版 |
| v1.3 | 2026-11-07 | 补齐三端能力矩阵、条件编译、安全区与发布链路 |

---

## 0. 文档定位与 AI 使用约定（FE-DOC）

### 0.1 四份文档各管什么

| 文档 | 角色 | 负责定义 | 明确不负责 |
|---|---|---|---|
| 《前端UI设计规范》 | 视觉定义方 | 颜色、字号、间距、圆角、阴影、动效、页面状态与交互反馈 | 接口字段、目录结构、请求封装 |
| 《接口契约与错误码规范》 | 接口定义方 | 响应结构、HTTP 语义、业务错误码、分页/排序/筛选、时间/金额/ID 格式、幂等与版本 | 前端目录、组件、提示位置 |
| 《前端编码规范》（本文） | 前端实现方 | 目录、依赖、请求封装、鉴权载体、组件、样式消费、跨端、工程化 | 契约数值、视觉数值、后端实现 |
| 《后端编码规范》 | 后端实现方 | 分层、事务、异常映射、校验、安全、数据库、测试 | 前端实现与视觉 |

### 0.2 冲突优先级（MUST）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-DOC-001` | MUST | CONTEXT | 冲突只按 `AGENTS.md` §1 的优先级链处理，本规范不复述；发现同一事实双定义时按 `AGENTS.md` §1 停机上报 | 如果项目约定或组件实现覆盖契约字段、错误码、分页格式，则判为不通过 | 契约定义分页，前端只消费 | 前端自定义 `rows/totalCount` | 定义方被覆盖会让多方同时漂移 |
| `FE-DOC-002` | MUST | STATIC | 视觉数值只引用 UI 规范与 `ui-tokens`，本文与代码不另存一份数值 | 如果业务代码出现 `#4F46E5`、`13px` 等未引用 token 的字面量，则判为不通过 | `var(--color-primary)` | `color: #4F46E5` | 两处数值必然不同步 |
| `FE-DOC-003` | MUST | STATIC | 契约生成物目录只读，禁止手改；生成物的落位目录在启用时登记到 `docs/baseline/modules.md` | 如果生成目录有手工修改，则判为不通过 | 改 `openapi.yaml` 后重新生成 | 直接改生成的 `*.d.ts` | 手改会在下次生成时被覆盖并掩盖契约漂移 |
| `FE-DOC-004` | MUST | STATIC | 跨文档引用只写规则编号，不写章节号与行号；不得凭记忆复述 | 如果引用了不存在的 `CT-*`、`BE-*`、`UI-*` 编号，则判为不通过 | “分页见 `CT-PAGE-001`” | “分页按契约，具体忘了哪节” | 章节号会漂移，悬空引用会让 AI 自行编造规则 |

### 本章自检清单

- [ ] `FE-DOC-001` 冲突只按 `AGENTS.md` §1 处理（MUST, CONTEXT）
- [ ] `FE-DOC-002` 无重复定义的视觉数值（MUST, STATIC）
- [ ] `FE-DOC-003` 契约生成物未被手改（MUST, STATIC）
- [ ] `FE-DOC-004` 跨文档引用编号真实存在（MUST, STATIC）

---

## 1. 决策地图（29 项）

> 本表只记录**非技术栈取舍**（平台矩阵、代码组织、错误归属、契约消费方式等）。技术栈、框架与版本唯一结论在 `AGENTS.md` §9-B；§9-B 未确认时本表对应项保持“未定”。决策已定版，AI 不得自行改选；需要变更时先改本节与变更记录。

| 编号 | 决策点 | 定版结论 |
|---|---|---|
| D01 | 目标平台矩阵 | PC + H5 + 微信小程序 + App（Android/iOS） |
| D02 | 主框架 | 见 `AGENTS.md` §9-B |
| D03 | 跨端技术路线 | 见 `AGENTS.md` §9-B；只允许一条跨端路线 |
| D04 | 构建工具 | 见 `AGENTS.md` §9-B |
| D05 | 语言 | 语言见 `AGENTS.md` §9-B；类型检查 `strict` 必开 |
| D06 | 路由 | PC 与移动各用所选框架的官方路由方案，不并行引入第二套 |
| D07 | 状态管理 | 只用一套状态库，具体见 `AGENTS.md` §9-B |
| D08 | PC 组件库 | 见 `AGENTS.md` §9-B |
| D09 | 跨端组件库 | 见 `AGENTS.md` §9-B；一个主库 + 补缺，不并行两个主库 |
| D10 | 多端代码组织 | monorepo：可运行应用 `apps/pc`、`apps/mobile`；共享包与它们平级（`apps/shared`、`packages/ui-tokens`）。共享包与应用的区分见 `FE-DIR-001` |
| D11 | 请求库 | 统一接口 + 各端适配，页面不得直连请求库 |
| D12 | 表单校验 | 契约类型驱动的 schema 校验 + 轻量 `useForm` |
| D13 | 样式方案 | SCSS + CSS 变量 |
| D14 | 移动端适配单位 | 移动 `rpx` + PC `px` |
| D15 | 测试框架 | 单测 + E2E + 小程序自动化各一套，具体见 `AGENTS.md` §9-B |
| D16 | 代码规范工具链 | ESLint + Prettier + Stylelint |
| D17 | 目录组织方式 | 类型优先 + 业务域二级 |
| D18 | 包管理器 | 见 `AGENTS.md` §9-B；只允许一份锁文件 |
| D19 | 错误提示归属 | 分层混合：请求层管网络/5xx/401/403/429/未知码，业务层管 409 与领域码 |
| D20 | 契约消费方式 | 从 `openapi.yaml` 生成类型 + 手写薄封装；不引入重量级代码生成框架 |
| D21 | Mock 方案 | Web 用 MSW，小程序用适配层 Mock |
| D22 | 令牌本地存储 | PC/H5 用 httpOnly Cookie 承载；小程序/App 用系统安全存储 + Bearer（协议载体见 `CT-AUTH-001`–`CT-AUTH-004`） |
| D23 | 权限控制粒度 | 权限码 + `can()` + 指令/组件，权限码格式与取值见项目层 `docs/design/permission-matrix.md`（模板见 `docs/templates/permission-matrix.md`） |
| D24 | 组件文档方式 | 组件文档工具（PC）+ 移动端演示页 |
| D25 | 小程序分包策略 | 按业务域分包 |
| D26 | App 打包方式 | 云打包起步；满足四条件之一转离线 |
| D27 | 分支模型 | 主干 + 短分支 + tag |
| D28 | 错误监控与埋点 | 统一上报入口 + 错误监控平台 |
| D29 | 国际化 | 使用所选框架生态的 i18n 方案 + 预编译消息 |

### 本章自检清单

- [ ] 本次改动未改变 D01–D29 的定版结论（MUST, STATIC）
- [ ] 确需改选时，已先改决策地图与变更记录（MUST, PATTERN）
- [ ] 技术栈表述与 `AGENTS.md` §9-B 一致（MUST, STATIC）

---

## 2. 技术栈与版本（FE-STACK）

> 技术栈、框架与版本不在本规范硬编码；唯一定义方是 `AGENTS.md` §9-B。以下规则只约束“如何用”，不约束“用哪个版本”。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-STACK-001` | MUST | STATIC | 主框架与语言按 `AGENTS.md` §9-B 选定，且 `strict` 类型检查为 `true`；`AGENTS.md` §9-B 未确认时不得开工 | 如果在技术栈未确认前自选框架，或 `strict: false`，则判为不通过 | 按 `AGENTS.md` §9-B 锁定版本 + `"strict": true` | 自选框架并关闭 strict | 版本与语言模式决定全部后续规则 |
| `FE-STACK-002` | MUST | STATIC | 跨端只走一条路线（`AGENTS.md` §9-B 选定项），不混用另一套跨端框架 | 如果同时维护两套跨端框架，则判为不通过 | 移动端 `apps/mobile` 只用一条路线 | 同时维护 Taro 与 uni-app | 双路线会分裂组件与构建链 |
| `FE-STACK-003` | MUST | STATIC | PC 主组件库与跨端主组件库按 `AGENTS.md` §9-B 选定，各一个主库 | 如果跨端引入第二个主库，或 PC 换用未登记的主库，则判为不通过 | 主库 + 补缺库 | 两个跨端主库并存 | 主库决定交互与样式基线 |
| `FE-STACK-004` | MUST | STATIC | 包管理器按 `AGENTS.md` §9-B 选定且唯一，版本由 `packageManager` 与 `engines` 固定 | 如果仓库出现第二种包管理器的锁文件，则判为不通过 | 单一 `packageManager` 字段 | 同时存在两份锁文件 | 多包管理器会导致依赖树不一致 |
| `FE-STACK-005` | MUST | STATIC | 框架/组件库/构建链的跨大版本升级必须先写决策记录（`docs/decisions/` 的 ADR） | 如果 PR 直接升大版本且无 ADR，则判为不通过 | ADR + 迁移说明 | 顺手升级到新大版本 | 大版本升级会改变行为与构建产物 |
| `FE-STACK-006` | MUST | STATIC | 锁文件提交到仓库，CI 使用所选包管理器的冻结安装命令 | 如果锁文件被忽略或 CI 允许漂移安装，则判为不通过 | `pnpm install --frozen-lockfile`（按 §9-B 换命令） | CI 每次重新解析版本 | 版本漂移会造成“本地能跑、CI 不能跑” |

### 本章自检清单

- [ ] `FE-STACK-001` 技术栈来自 `AGENTS.md` §9-B 且 `strict` 开启（MUST, STATIC）
- [ ] `FE-STACK-002` 跨端只走 §9-B 选定的一条路线（MUST, STATIC）
- [ ] `FE-STACK-003` PC 与跨端各一个主组件库（MUST, STATIC）
- [ ] `FE-STACK-004` 只有一份锁文件（MUST, STATIC）
- [ ] `FE-STACK-005` 大版本升级有 ADR（MUST, STATIC）
- [ ] `FE-STACK-006` 锁文件入库且 CI 冻结安装（MUST, STATIC）

---

## 3. 组合约束与套件（FE-COMBO）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-COMBO-001` | MUST | PATTERN | 选型必须成套：PC 套件与移动套件各自内部自洽（主库按 `AGENTS.md` §9-B） | 如果 PC 用 Web 主库、移动却引入仅支持 Web 的库，则判为不通过 | 移动只用 `AGENTS.md` §9-B 选定的跨端主库 | 移动页面 import PC 专用组件库 | 跨端不支持的库会在小程序/App 编译失败 |
| `FE-COMBO-002` | MUST | PATTERN | 不允许同时引入两个功能重叠的主库 | 如果同时装两个请求库、两个表单库、两个日期库，则判为不通过 | 统一 request + dayjs | axios + ky + 自写 fetch 三套 | 重叠依赖会放大包体与行为差异 |
| `FE-COMBO-003` | MUST | PATTERN | Web-only 能力不得进入共享包 `apps/shared` | 如果 `apps/shared` 直接使用 `window`、`document`、`localStorage`，则判为不通过 | 能力放使用它的应用内，如 `apps/pc/src/utils/clipboard.ts` | `apps/shared` 里 `document.querySelector` | 共享包被三端复用，必须保持平台无关 |
| `FE-COMBO-004` | SHOULD | CONTEXT | 套件选择写入项目决策记录，新增依赖时核对是否仍在同一套件 | 如果新增依赖无法归入现有套件且无说明，则判为不通过 | 决策记录标注套件 A/B/C | 随手加库不记录 | 套件漂移会让约束逐条失效 |

### 本章自检清单

- [ ] `FE-COMBO-001` PC 与移动套件各自自洽（MUST, PATTERN）
- [ ] `FE-COMBO-002` 无功能重叠的重复主库（MUST, PATTERN）
- [ ] `FE-COMBO-003` 共享包无 Web-only 能力（MUST, PATTERN）
- [ ] `FE-COMBO-004` 套件选择有记录（SHOULD, CONTEXT）

---

## 4. 目录结构（FE-DIR）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-DIR-001` | MUST | STATIC | 目录按「可运行应用 + 共享包」平级组织：可运行应用在 `apps/pc`、`apps/mobile`；共享包与它们平级、集中管理（当前 `apps/shared`、`packages/ui-tokens`）。**区分判据**：可运行应用必须有应用入口与自己可执行的 `dev`/`build` 脚本；共享包无应用入口、不提供 `build` 脚本、不得 import 应用代码。工作区必须逐包显式列出，禁止用 `apps/*` 通配——`pnpm --filter "./apps/*"` 按目录匹配，会把共享包一并匹配进来，拦住它的是「没有 `build` 脚本」而不是过滤器 | 如果在 `apps` 下新建第三套应用却未登记，或让共享包 import 应用代码/依赖应用才能运行，或工作区用 `apps/*` 通配而未显式区分应用与共享包，则判为不通过 | `apps/pc` + `apps/mobile` + `apps/shared` + `packages/ui-tokens`，`pnpm-workspace.yaml` 逐条列出；`apps/shared` 无 `build` 脚本 | `apps/shared/src/api` 里 import `apps/pc/src/api/http`；`pnpm-workspace.yaml` 只写 `apps/*` | 边界靠「有无入口与 `dev`/`build` 脚本」判定；靠目录层级猜，新增包时必然反复出错 |
| `FE-DIR-002` | MUST | PATTERN | 目录先按类型、再按业务域二级拆分 | 如果同一业务域散落在 `components/`、`views/`、`utils/` 多处且无归属，则判为不通过 | `features/order/{api,components,views}` | `views/order` + `components/order` + `utils/order` 三套 | 类型优先便于约束，业务域二级便于收敛 |
| `FE-DIR-003` | MUST | STATIC | 页面只能调用 `endpoints`，不得 import 适配器或平台 API | 如果页面出现 `axios`、`uni.request`、`fetch`，则判为不通过 | `userApi.list()` | `uni.request({ url: '/users' })` | 绕过封装会丢失错误归一化与鉴权 |
| `FE-DIR-004` | MUST | STATIC | 平台差异实现（存储、剪贴板、文件选择等）放在使用它的应用内（`apps/pc/src/utils`、`apps/mobile/src/utils`），不得放进共享包；共享包只保留平台无关的纯类型与纯函数 | 如果共享包出现 `window`/`document`/`uni.*` 或按平台分支的业务判断，则判为不通过 | `apps/pc/src/utils/clipboard.ts` 封装粘贴读取 | `apps/shared/src/utils/clipboard.ts` 里用 `navigator.clipboard` | 平台层空壳包已删除，平台能力必须贴着使用方；确需三端复用先开 CR |
| `FE-DIR-005` | MUST | STATIC | 文件命名：组件 PascalCase，其余 kebab-case；composable 以 `use` 开头 | 如果出现 `Orderlist.vue`、`My_component.vue` 等不一致命名，则判为不通过 | `OrderList.vue`、`use-order.ts` | `orderList.vue`、`helper.ts` 里导出组件 | 命名不一致会破坏检索与自动导入 |
| `FE-DIR-006` | MUST | PATTERN | 禁止 barrel 文件形成循环依赖 | 如果 `index.ts` 互相 re-export 形成环，则判为不通过 | 单向下沉导出 | `a/index.ts` 与 `b/index.ts` 互引 | 循环依赖会导致初始化顺序错误 |
| `FE-DIR-007` | MUST | PATTERN | 可复用状态逻辑（分页、表单抽屉、上传、轮询、预览等）一律放 `apps/pc/src/composables/`，文件名 kebab-case 的 `use-*.ts` 且导出 `use*`；禁止在 `views/` 下新增 `use*.ts` | 如果 `views/` 下出现 `use*.ts`，或同一段状态逻辑在两个页面重复出现，则判为不通过 | `composables/use-paged-list.ts` 导出 `usePagedList` | `views/login/useLoginForm.ts` | 放在页面目录下会被当成页面私有逻辑，第二个页面只能复制 |

### 本章自检清单

- [ ] `FE-DIR-001` 应用与共享包平级、工作区逐包显式列出（MUST, STATIC）
- [ ] `FE-DIR-002` 类型优先 + 业务域二级（MUST, PATTERN）
- [ ] `FE-DIR-003` 页面未直连请求库（MUST, STATIC）
- [ ] `FE-DIR-004` 平台差异实现留在应用内、共享包保持平台无关（MUST, STATIC）
- [ ] `FE-DIR-005` 文件与 composable 命名一致（MUST, STATIC）
- [ ] `FE-DIR-006` 无 barrel 循环依赖（MUST, PATTERN）
- [ ] `FE-DIR-007` 可复用状态逻辑在 `composables/` 且命名合规（MUST, PATTERN）

---

## 5. 包与依赖管理（FE-DEP）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-DEP-001` | MUST | STATIC | 内部包通过 `workspace:*` 引用，不使用相对路径跨包 | 如果出现 `../../apps/shared/src/...`，则判为不通过 | `"@admin-starter/shared": "workspace:*"` | `import '../../../../apps/shared/x'` | 相对跨包会绕过包边界与版本约束 |
| `FE-DEP-002` | MUST | STATIC | 依赖分层：运行依赖、开发依赖、类型依赖各归其位 | 如果 `@types/*` 进 `dependencies`、构建工具进运行依赖，则判为不通过 | `devDependencies` 放 `typescript` | `dependencies` 放 `@types/node` | 分层错误会增大产物与部署体积 |
| `FE-DEP-003` | MUST | STATIC | 禁止使用 `latest`、`*`、Git URL 作为生产依赖版本 | 如果 `package.json` 出现浮动版本，则判为不通过 | `"zod": "^3.23.0"` | `"zod": "latest"` | 浮动版本无法复现构建 |
| `FE-DEP-004` | MUST | PATTERN | 同一依赖在 monorepo 内只保留一个版本 | 如果同一库出现两个不兼容版本且无说明，则判为不通过 | 依赖去重后单版本 | 两个包分别锁不同大版本 | 多版本会重复打包并引发行为差异 |
| `FE-DEP-005` | SHOULD | STATIC | 新增依赖前核对体积预算与许可证 | 如果新增依赖显著超出预算且未说明，则判为不通过 | 记录体积变化 | 静默新增 300KB 库 | 包体积直接影响首屏与小程序包限 |
| `FE-DEP-006` | MUST | STATIC | 新增依赖必须同步更新锁文件与依赖清单 | 如果代码引用了未声明的依赖（幽灵依赖），则判为不通过 | `package.json` 显式声明 | 靠上层包“顺带”可用 | 幽灵依赖在提升策略变化后会断裂 |

### 本章自检清单

- [ ] `FE-DEP-001` 内部包用 `workspace:*`（MUST, STATIC）
- [ ] `FE-DEP-002` 依赖类型归位（MUST, STATIC）
- [ ] `FE-DEP-003` 无浮动版本（MUST, STATIC）
- [ ] `FE-DEP-004` 单版本去重（MUST, PATTERN）
- [ ] `FE-DEP-005` 体积与许可证已核对（SHOULD, STATIC）
- [ ] `FE-DEP-006` 无幽灵依赖（MUST, STATIC）

---

## 6. 请求层与契约消费（FE-REQ）

> 响应结构、错误码、分页字段、时间格式、幂等键一律以契约文档为准（`CT-RESP-*`、`CT-ERR-*`、`CT-PAGE-*`、`CT-FMT-*`、`CT-IDEM-*`）。本章只规定前端如何消费。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-REQ-001` | MUST | PATTERN | 调用链为 页面/composable → endpoints → client → HttpAdapter | 如果页面跳过任一层直接发请求，则判为不通过 | `page → orderApi.list → client → adapter` | 页面 `fetch('/orders')` | 分层保证错误、鉴权、取消只写一次 |
| `FE-REQ-002` | MUST | STATIC | 响应类型来自生成契约，不手写 interface 复制字段 | 如果 `endpoints` 里手写与契约重复的响应类型，则判为不通过 | `paths['/orders']['get']` | `interface OrderResp { ... }` 手抄 | 手写类型不会随契约变更报错 |
| `FE-REQ-003` | MUST | STATIC | 统一响应与分页结构只引用契约类型，前端不另立包装 | 如果前端定义第二套 `Result` 或改分页字段名，则判为不通过 | `ApiResponse<T>` 对齐 `CT-RESP-001` | `{ ok, rows, count }` | 二次包装会让契约测试失效 |
| `FE-REQ-004` | MUST | PATTERN | 成功判定只看契约规定（HTTP 2xx 且业务码为成功值） | 如果以前端自定义 `success` 字段判定成功，则判为不通过 | 按 `CT-RESP-002` 判定 | `if (res.success)` | 成功语义必须由契约唯一定义 |
| `FE-REQ-005` | MUST | PATTERN | 错误归一化集中在 `client`，页面不解析原始响应 | 如果页面读取 `response.status` 或 `body.code` 自行判断，则判为不通过 | 页面只 catch `BizError` | 页面判断 `res.code === 1001` | 分散判断会漏掉统一上报与提示 |
| `FE-REQ-006` | MUST | PATTERN | 请求层已提示的错误不得在业务层重复提示 | 如果同一错误弹两次 toast，则判为不通过 | 错误对象带 `handled` 标记 | 拦截器与页面各弹一次 | 重复提示是前端最常见的体验缺陷 |
| `FE-REQ-007` | MUST | STATIC | 需要局部展示时使用 `meta.silent`，请求层只归一化不提示 | 如果通过 `try/catch` 后再次弹全局提示，则判为不通过 | `{ meta: { silent: true } }` | 页面 catch 后 `toast(error)` | 提示归属必须显式声明 |
| `FE-REQ-008` | MUST | PATTERN | 401 只静默刷新并重放一次，失败即清会话跳登录 | 如果对 401 循环重试或直接弹业务错误，则判为不通过 | 单飞刷新 + 重放一次 | 401 后无限重试 | 循环刷新会打爆登录接口 |
| `FE-REQ-009` | MUST | CONTEXT | 重试策略完全按 `CT-IDEM-005` 的条件执行：仅幂等且带幂等键的请求可重试，写操作默认不重试 | 如果对无幂等键的写请求自动重试，则判为不通过 | 幂等键 + 限定次数重试 | POST 超时后无条件自动重发 | 写重试可能造成重复数据 |
| `FE-REQ-010` | MUST | STATIC | 幂等键名称与位置严格按契约，不另起请求头 | 如果前端自造 `X-Request-Id` 当幂等键，则判为不通过 | 按 `CT-IDEM-001` 传键 | 自造 `X-Trace-Id` 复用 | 名称不一致后端无法识别 |
| `FE-REQ-011` | MUST | PATTERN | 查询类请求支持取消；翻页/切筛选取消上一笔 | 如果旧响应覆盖新结果，则判为不通过 | `AbortSignal` 或取消令牌 | 快速翻页后显示旧页数据 | 竞态会造成数据错乱 |
| `FE-REQ-012` | MUST | PATTERN | 上传下载走统一封装，页面不直调平台 API | 如果页面直接 `uni.uploadFile` 或 `a.download`，则判为不通过 | `upload()` / `download()` | 页面内联 `FormData` 逻辑 | 平台差异应收敛到 platform 层 |
| `FE-REQ-013` | MUST | PATTERN | 契约变更走“改 `openapi.yaml` → 生成 → 类型 diff → 改 endpoints” | 如果先改前端类型再补契约，则判为不通过 | 先生成后开发 | 前端先手写字段 | 顺序颠倒会让契约失真 |
| `FE-REQ-014` | SHOULD | STATIC | Mock 数据来自契约示例，禁止手写与契约不一致的字段 | 如果 mock 字段名/类型与生成类型不符，则判为不通过 | 从 `examples` 生成 | mock 里写 `user_name` 而契约是 `userName` | Mock 漂移会掩盖真实联调问题 |

### 本章自检清单

- [ ] `FE-REQ-001` 调用链未跳层（MUST, PATTERN）
- [ ] `FE-REQ-002` 响应类型来自生成契约（MUST, STATIC）
- [ ] `FE-REQ-003` 无第二套响应/分页包装（MUST, STATIC）
- [ ] `FE-REQ-004` 成功判定符合契约（MUST, PATTERN）
- [ ] `FE-REQ-005` 错误归一化只在 client（MUST, PATTERN）
- [ ] `FE-REQ-006` 无重复提示（MUST, PATTERN）
- [ ] `FE-REQ-007` 局部展示用 `meta.silent`（MUST, STATIC）
- [ ] `FE-REQ-008` 401 单飞刷新且只重放一次（MUST, PATTERN）
- [ ] `FE-REQ-009` 重试条件与 `CT-IDEM-005` 一致（MUST, CONTEXT）
- [ ] `FE-REQ-010` 幂等键符合契约（MUST, STATIC）
- [ ] `FE-REQ-011` 查询请求可取消（MUST, PATTERN）
- [ ] `FE-REQ-012` 上传下载走统一封装（MUST, PATTERN）
- [ ] `FE-REQ-013` 契约先行流程未颠倒（MUST, PATTERN）
- [ ] `FE-REQ-014` Mock 与契约一致（SHOULD, STATIC）

---

## 7. 登录与鉴权（FE-AUTH）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-AUTH-001` | MUST | STATIC | PC/H5 令牌**本地存储**为 httpOnly Cookie，前端脚本不可读；Cookie 名与属性由服务端按 `CT-AUTH-001` 写入 | 如果 PC/H5 把 token 存 `localStorage`、可读 Cookie，或前端脚本自行写 `document.cookie`，则判为不通过 | 后端 `Set-Cookie: HttpOnly; Secure; SameSite` | `localStorage.setItem('token', t)` | 可读存储会被 XSS 直接窃取 |
| `FE-AUTH-002` | MUST | STATIC | 小程序使用 Bearer + 平台本地存储，App 使用 Bearer + 系统安全存储；仅移动端允许 Bearer（`CT-AUTH-003`） | 如果 App 把 token 写普通文件或明文偏好设置，或 Web 端用 Bearer，则判为不通过 | Keychain / EncryptedSharedPreferences | Web 端把 token 放请求头；App 用 `uni.setStorageSync('token', t)` | 移动端系统存储提供硬件级保护 |
| `FE-AUTH-003` | MUST | PATTERN | 同一时刻只允许一个刷新请求，其余请求排队等待结果 | 如果并发 401 各自发起刷新，则判为不通过 | 单飞 `refreshOnce()` | 每个拦截器各刷新一次 | 并发刷新会互相作废令牌 |
| `FE-AUTH-004` | MUST | PATTERN | 重放仍 401 时清会话并跳登录，不循环 | 如果刷新失败后仍继续重放，则判为不通过 | 失败即登出并带 `redirect` | 401 → 刷新 → 401 → 刷新 | 死循环会锁死页面 |
| `FE-AUTH-005` | MUST | PATTERN | 401 由请求层处理、403 由请求层统一提示，页面不重复判断 | 如果页面自行判断 401/403，则判为不通过 | 拦截器统一处理 | 页面 `if (status === 403)` 再弹一次 | 两处处理会产生双提示与不一致跳转 |
| `FE-AUTH-006` | MUST | STATIC | 权限判断统一走 `can()` 或权限组件；权限码取值与格式只来自项目层 `docs/design/permission-matrix.md`（模板见 `docs/templates/permission-matrix.md`），禁止散落角色字符串或自造权限码 | 如果代码出现 `role === 'ADMIN'` 控制按钮，或使用矩阵外的权限码，则判为不通过 | `v-if="can('biz:order:read')"` | `v-if="user.role === 'ADMIN'"` 或 `can('ORDER_EDIT')` | 角色硬编码与自造权限码都无法表达细粒度权限 |
| `FE-AUTH-007` | MUST | PATTERN | 前端权限只控制可见/可点，写操作必须由后端二次校验 | 如果仅靠隐藏按钮就认为安全，则判为不通过 | 前端隐藏 + 后端鉴权 | 只隐藏按钮，接口无校验 | 前端可被绕过，安全边界在后端 |
| `FE-AUTH-008` | MUST | PATTERN | 路由级权限在进入前校验，菜单由后端按权限下发 | 如果前端硬编码菜单可见性，则判为不通过 | `meta.permission` + 后端菜单 | 前端写死管理员菜单 | 菜单与权限两套来源必然不同步 |
| `FE-AUTH-009` | MUST | STATIC | 第三方登录只传 `code`/凭证，敏感换取全部在后端 | 如果前端拿 `session_key` 或自行解密手机号，则判为不通过 | 只传 `code` | 前端解密 `encryptedData` | 密钥不应出现在客户端 |
| `FE-AUTH-010` | MUST | PATTERN | 退出登录同时清理本地会话、缓存与在途请求 | 如果退出后仍能用旧令牌请求，则判为不通过 | 清会话 + 取消请求 + 跳登录 | 只跳页面不清令牌 | 残留会话会造成越权 |

### 本章自检清单

- [ ] `FE-AUTH-001` PC/H5 用 httpOnly Cookie 且由后端写入（MUST, STATIC）
- [ ] `FE-AUTH-002` 移动端用 Bearer + 安全存储（MUST, STATIC）
- [ ] `FE-AUTH-003` 刷新单飞（MUST, PATTERN）
- [ ] `FE-AUTH-004` 重放失败即登出（MUST, PATTERN）
- [ ] `FE-AUTH-005` 401/403 归属请求层（MUST, PATTERN）
- [ ] `FE-AUTH-006` 权限判断走 `can()` 且权限码来自矩阵（MUST, STATIC）
- [ ] `FE-AUTH-007` 写操作有后端校验（MUST, PATTERN）
- [ ] `FE-AUTH-008` 路由与菜单权限一致（MUST, PATTERN）
- [ ] `FE-AUTH-009` 第三方登录不碰敏感凭证（MUST, STATIC）
- [ ] `FE-AUTH-010` 退出清理完整（MUST, PATTERN）

---

## 8. 错误处理与提示（FE-ERR）

> 错误码数值、分段与默认文案由契约文档定义（`CT-ERR-*`）；本章只规定前端分类、提示与上报。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-ERR-001` | MUST | PATTERN | 错误分类只从契约事实派生：按 `CT-HTTP-003` 的 HTTP 状态与 `CT-ERR-001` 的码段归入固定类别，未知码统一归 `UNKNOWN` | 如果出现自造类别、依赖契约中不存在的“类别”字段，或把未知码当成功，则判为不通过 | 由状态码/码段派生类别 | 未知码直接 `return null` | 分类决定提示与重试策略 |
| `FE-ERR-002` | MUST | STATIC | 文案覆盖表按契约符号名索引，不按数字码索引 | 如果覆盖表 key 是 `1001` 这类数字，则判为不通过 | `ORDER_STATE_CONFLICT: '...'` | `1001: '...'` | 数字码会随契约分段调整而错位 |
| `FE-ERR-003` | MUST | STATIC | 未知错误兜底文案只在一处维护，页面不自行拼接 | 如果多个页面各写“操作失败”，则判为不通过 | 统一 `unknownErrorMessage()` | 每个页面各写一句 | 兜底分散会导致文案与 traceId 缺失 |
| `FE-ERR-004` | MUST | STATIC | 提示文案不包含堆栈、请求地址、内部字段名 | 如果直接把后端 `message` 展示给用户，则判为不通过 | 友好文案 + traceId | 展示 `SQLException` | 内部信息会泄露实现细节 |
| `FE-ERR-005` | MUST | PATTERN | 字段校验错误交给表单组件，不弹全局提示 | 如果 422 也弹全局 toast，则判为不通过 | 把 `data.errors` 映射到表单 | 422 统一弹窗 | 字段错误需要就地展示 |
| `FE-ERR-006` | MUST | STATIC | 上报内容包含 traceId、路径、状态、业务码，不含请求体敏感字段 | 如果上报包含 token、手机号、密码，则判为不通过 | 脱敏后的上下文 | 上报整个 request body | 错误上报也是数据外泄渠道 |
| `FE-ERR-007` | MUST | PATTERN | 禁止 catch 后静默返回或只 `console.log` | 如果 `catch` 为空或仅打印，则判为不通过 | 归一化后抛出或显式降级 | `catch (e) { console.log(e) }` | 静默失败会让用户与监控同时失明 |

### 本章自检清单

- [ ] `FE-ERR-001` 错误分类覆盖 `CT-ERR-001` 的全部码段与 `CT-HTTP-003` 的状态码（MUST, PATTERN）
- [ ] `FE-ERR-002` 文案表按符号名索引（MUST, STATIC）
- [ ] `FE-ERR-003` 兜底文案唯一（MUST, STATIC）
- [ ] `FE-ERR-004` 展示文案无内部细节（MUST, STATIC）
- [ ] `FE-ERR-005` 422 交表单处理（MUST, PATTERN）
- [ ] `FE-ERR-006` 上报已脱敏且带 traceId（MUST, STATIC）
- [ ] `FE-ERR-007` 无静默 catch（MUST, PATTERN）

---

## 9. 代码风格（FE-CODE）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-CODE-001` | MUST | STATIC | 命名：变量/函数 camelCase，类型/组件 PascalCase，常量 UPPER_SNAKE，布尔以 `is/has/can` 开头 | 如果出现 `order_list`、`GetData()`、`flag` 这类命名，则判为不通过 | `orderList`、`OrderList`、`MAX_SIZE`、`isValid` | `order_list`、`data1` | 命名是检索与自动补全的基础 |
| `FE-CODE-002` | MUST | STATIC | TypeScript strict；禁止裸 `any` 与无理由 `@ts-ignore` | 如果出现 `any`、`as any` 或 `@ts-ignore` 无说明，则判为不通过 | 用泛型或 `unknown` + 收窄 | `const data: any = res` | 类型逃逸会让契约漂移无法在编译期暴露 |
| `FE-CODE-003` | MUST | STATIC | import 按“类型 → 三方 → 内部别名 → 相对”分组，使用路径别名 | 如果出现深层 `../../../../` 引用，则判为不通过 | `import { x } from '@shared/api'` | `import { x } from '../../../../shared/api'` | 别名让重构与迁移可控 |
| `FE-CODE-004` | MUST | STATIC | 禁止魔法值；状态、错误码、分页默认值来自常量或契约 | 如果代码出现 `if (status === 3)`、`pageSize: 20` 散落，则判为不通过 | `OrderStatus.PAID` | `if (status === 2)` | 魔法值无法语义化且易错 |
| `FE-CODE-005` | MUST | PATTERN | 异步统一 `async/await`，浮空 Promise 必须显式处理 | 如果调用异步函数不 `await` 也不 `.catch`，则判为不通过 | `await orderApi.save()` | `orderApi.save()` 后不管 | 浮空 Promise 会变成未处理拒绝 |
| `FE-CODE-006` | MUST | PATTERN | 每个异步入口有统一异常出口，禁止未处理拒绝 | 如果出现 `unhandledrejection` 或页面白屏，则判为不通过 | 顶层错误边界 + 统一上报 | 任意抛出无人接 | 未处理拒绝会导致状态不一致 |
| `FE-CODE-007` | MUST | PATTERN | 不直接修改 props 或外部对象；状态更新走显式赋值/不可变写法 | 如果组件内 `props.user.name = x`，则判为不通过 | `emit('update', { ...user, name })` | 直接改 props 对象 | 直接修改会让数据流不可追踪 |
| `FE-CODE-008` | MUST | STATIC | 日期、金额、ID 的序列化走共享工具并以契约 `CT-FMT-*` 为准；界面展示格式以 UI 规范为准（`UI-PC-COPY-005` / `UI-H5-COPY-003`） | 如果页面自行 `toLocaleString`、金额用浮点运算，或界面格式与 UI 规范不一致，则判为不通过 | 序列化按 `CT-FMT-001`，展示按 `UI-PC-COPY-005` | `new Date(v).toLocaleDateString()` | 传输格式与展示格式是两个定义方，不能混用 |
| `FE-CODE-009` | MUST | STATIC | 面向用户的文案走 i18n key，不在模板里硬编码中文/英文 | 如果模板直接写 “保存成功”，则判为不通过 | `t('common.saveSuccess')` | `<span>保存成功</span>` | 硬编码文案无法国际化与统一修改 |
| `FE-CODE-010` | MUST | STATIC | 生产代码禁止 `console.log/debug`；日志走统一 logger | 如果提交包含 `console.log`，则判为不通过 | `logger.warn(...)` | `console.log('here')` | 控制台日志会泄露数据且无法采集 |
| `FE-CODE-011` | SHOULD | STATIC | 注释解释“为什么”，不复述代码“做了什么” | 如果注释与代码重复或明显过期，则判为不通过 | `// 兼容旧版分页字段，见 CT-PAGE-002` | `// 设置 name` 后接 `name = x` | 复述型注释会随时间变成噪声 |
| `FE-CODE-012` | SHOULD | CONTEXT | 函数单一职责，嵌套不超过 3 层，超长逻辑拆分 | 如果单函数超过约 60 行且承担多个职责，则判为不通过 | 拆为校验、提交、提示三步 | 200 行巨型 `handleSubmit` | 长函数难以测试与复用 |
| `FE-CODE-013` | SHOULD | CONTEXT | 页面 `script` 段超过约 200 行，或单文件出现 ≥3 类职责（列表、表单、上传/轮询、格式化、类型定义），必须拆为 composable 或业务组件；判定基准是“能否在不复制代码的前提下被第二个页面复用” | 如果单文件 `script` 超过约 200 行且含 3 类以上职责，或同一职责代码在两个页面重复，则判为不通过 | 页面只留视图渲染与事件绑定，状态逻辑在 `composables/` | 733 行页面内含上传编排、预览、分页、类型定义 | 与 `FE-CODE-012` 互补：单函数拆开但单文件仍堆多职责时，复用与测试成本不降 |

### 本章自检清单

- [ ] `FE-CODE-001` 命名规则一致（MUST, STATIC）
- [ ] `FE-CODE-002` 无裸 `any` 与无理由忽略（MUST, STATIC）
- [ ] `FE-CODE-003` import 分组与别名（MUST, STATIC）
- [ ] `FE-CODE-004` 无魔法值（MUST, STATIC）
- [ ] `FE-CODE-005` 无浮空 Promise（MUST, PATTERN）
- [ ] `FE-CODE-006` 异步异常有统一出口（MUST, PATTERN）
- [ ] `FE-CODE-007` 未直接改 props（MUST, PATTERN）
- [ ] `FE-CODE-008` 日期/金额/ID 走共享工具（MUST, STATIC）
- [ ] `FE-CODE-009` 文案走 i18n（MUST, STATIC）
- [ ] `FE-CODE-010` 生产代码无 console（MUST, STATIC）
- [ ] `FE-CODE-011` 注释解释原因（SHOULD, STATIC）
- [ ] `FE-CODE-012` 函数职责单一（SHOULD, CONTEXT）
- [ ] `FE-CODE-013` 页面体量与职责数符合拆分标准（SHOULD, CONTEXT）

---

## 10. 组件规范（FE-COMP）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-COMP-001` | MUST | PATTERN | 组件分三层：基础组件、业务组件、页面；业务组件不反向依赖页面 | 如果业务组件 import 页面或路由，则判为不通过 | `business/OrderCard` 只依赖基础组件 | 业务组件里 `router.push` | 反向依赖会让组件无法复用与测试 |
| `FE-COMP-002` | MUST | STATIC | Props 与事件有显式 TS 类型，布尔 Prop 有默认值 | 如果 `defineProps` 无类型或事件无载荷类型，则判为不通过 | `defineProps<{ orderId: string }>()` | `defineProps(['orderId'])` | 无类型无法在编译期发现误用 |
| `FE-COMP-003` | MUST | STATIC | 事件命名使用 `update:modelValue` 或语义化 `change/submit`，不暴露内部实现 | 如果事件名是 `handleClickInner`，则判为不通过 | `emit('change', value)` | `emit('innerClick')` | 事件是组件的公开契约 |
| `FE-COMP-004` | MUST | PATTERN | `v-model` 组件遵循单向数据流，子组件不直接改父值 | 如果子组件直接改 `modelValue`，则判为不通过 | `emit('update:modelValue', v)` | `props.modelValue = v` | 双向直改会破坏可预测性 |
| `FE-COMP-005` | SHOULD | PATTERN | 布局差异用 slot，不用布尔 Prop 堆叠 | 如果出现 `isLeft/isRight/isCenter` 三件套，则判为不通过 | `<slot name="actions" />` | `:show-left :show-right` | Slot 比布尔矩阵更易扩展 |
| `FE-COMP-006` | MUST | STATIC | 全局注册仅限基础组件；业务组件按需引入 | 如果批量全局注册业务组件，则判为不通过 | 基础组件统一注册 | 所有业务组件 `app.component` | 全局注册会拖慢启动并难以追踪来源 |
| `FE-COMP-007` | SHOULD | CONTEXT | 公共组件有文档或演示（Storybook / 移动演示页），变更同步更新 | 如果改公共组件 API 未更新演示，则判为不通过 | 演示 + 变更说明 | 改 Props 后演示仍用旧值 | 文档滞后会让调用方踩坑 |
| `FE-COMP-008` | MUST | PATTERN | 列表页的分页、搜索、重置、加载态、删除确认必须经统一 composable 或业务组件实现，页面不得重复定义 `page`/`filters`/`load`/`onPageSizeChange` 骨架 | 如果两个以上页面各自重复定义同一套列表骨架，则判为不通过 | `const { page, filters, rows, load } = usePagedList(...)` | 每个页面各写一份 `const page = reactive({ page: 1, pageSize: 20, total: 0 })` | 复制骨架会让分页默认值、空态与错误处理逐页漂移 |

### 本章自检清单

- [ ] `FE-COMP-001` 组件分层无反向依赖（MUST, PATTERN）
- [ ] `FE-COMP-002` Props/事件有类型（MUST, STATIC）
- [ ] `FE-COMP-003` 事件命名语义化（MUST, STATIC）
- [ ] `FE-COMP-004` `v-model` 单向数据流（MUST, PATTERN）
- [ ] `FE-COMP-005` 布局差异用 slot（SHOULD, PATTERN）
- [ ] `FE-COMP-006` 全局注册受限（MUST, STATIC）
- [ ] `FE-COMP-007` 公共组件文档同步（SHOULD, CONTEXT）
- [ ] `FE-COMP-008` 列表页骨架由 composable/业务组件统一实现（MUST, PATTERN）

---

## 11. 样式规范（FE-STYLE）

> 颜色、字号、间距、圆角、阴影、断点、动效时长等数值由《前端UI设计规范》定义（`UI-PC-*` / `UI-H5-*`）；本章只规定如何消费。

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-STYLE-001` | MUST | STATIC | 颜色、字号、间距、圆角、阴影只引用 `ui-tokens` 变量 | 如果样式出现十六进制色值或裸像素值，则判为不通过 | `color: var(--color-text-primary)` | `color: #333` | 字面量会让主题与暗色模式失效 |
| `FE-STYLE-002` | MUST | STATIC | 组件样式默认 scoped，全局样式只放在指定全局文件 | 如果页面内定义全局选择器污染其他页面，则判为不通过 | `<style scoped>` | 页面里 `body { ... }` | 全局污染难以定位且互相覆盖 |
| `FE-STYLE-003` | MUST | STATIC | 类名采用块级前缀，避免单字母与无语义类名 | 如果出现 `.a`、`.box1`、`.red`，则判为不通过 | `.order-card__title` | `.title2` | 语义化类名便于检索与覆写 |
| `FE-STYLE-004` | MUST | PATTERN | 组件库样式覆写集中在指定覆写层，不在业务页面零散改 | 如果每个页面都写 `:deep(.el-button)`，则判为不通过 | 统一 `styles/overrides` | 页面内多处 `:deep` | 零散覆写会随库升级失控 |
| `FE-STYLE-005` | MUST | STATIC | 响应式断点只使用 UI 规范定义的断点，不自造 | 如果出现 `@media (max-width: 777px)`，则判为不通过 | `@media (max-width: var(--bp-md))` | 自造断点 | 断点不一致会导致布局跳变 |
| `FE-STYLE-006` | MUST | STATIC | 禁止用 `!important` 掩盖优先级问题，除非注明原因 | 如果出现无说明的 `!important`，则判为不通过 | 提升选择器或调整层级 | `color: red !important` | `!important` 会制造不可预测的覆盖链 |
| `FE-STYLE-007` | MUST | PATTERN | 动效尊重“减少动态效果”偏好，并提供关闭路径 | 如果强制长动画且无 `prefers-reduced-motion` 处理，则判为不通过 | 媒体查询降级 | 全局强制 800ms 动画 | 无障碍与低端设备需要降级 |
| `FE-STYLE-008` | MUST | PATTERN | 暗色模式只通过 token 切换，不在业务组件写条件分支 | 如果组件内 `if (dark) color = '#fff'`，则判为不通过 | 切换 `data-theme` + token | 组件内硬编码暗色值 | 条件分支会漏改且无法统一 |
| `FE-STYLE-009` | MUST | STATIC | 移动端不用小程序不支持的选择器/属性；跨端样式写在小程序可编译范围 | 如果使用 `*` 选择器、`position: sticky` 等未验证能力，则判为不通过 | 用支持的选择器 | 小程序页面用 `*` | 不支持的能力会在编译或运行时报错 |
| `FE-STYLE-010` | SHOULD | STATIC | 静态样式写在样式表，不用内联 style 表达固定值 | 如果模板大量 `style="margin-top: 17px"`，则判为不通过 | class + token | 内联固定像素 | 内联样式难以统一与覆写 |

### 本章自检清单

- [ ] `FE-STYLE-001` 只用 token 变量（MUST, STATIC）
- [ ] `FE-STYLE-002` 组件样式 scoped（MUST, STATIC）
- [ ] `FE-STYLE-003` 类名语义化（MUST, STATIC）
- [ ] `FE-STYLE-004` 覆写集中管理（MUST, PATTERN）
- [ ] `FE-STYLE-005` 断点来自 UI 规范（MUST, STATIC）
- [ ] `FE-STYLE-006` 无滥用 `!important`（MUST, STATIC）
- [ ] `FE-STYLE-007` 动效可降级（MUST, PATTERN）
- [ ] `FE-STYLE-008` 暗色模式走 token（MUST, PATTERN）
- [ ] `FE-STYLE-009` 跨端样式在支持范围（MUST, STATIC）
- [ ] `FE-STYLE-010` 无固定值内联样式（SHOULD, STATIC）

---

## 12. 移动跨端（FE-MP）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-MP-001` | MUST | PATTERN | 平台差异统一经 `platform` 适配层，页面不判断 `#ifdef` 平台 | 如果页面出现 `#ifdef MP-WEIXIN` 分支业务逻辑，则判为不通过 | `platform.pickFile()` | 页面内条件编译写两套流程 | 平台判断散落会导致三端行为分叉 |
| `FE-MP-002` | MUST | STATIC | 条件编译只用于能力差异，不用于复制业务代码 | 如果同一业务逻辑被条件编译复制两遍，则判为不通过 | 能力差异封装 | `#ifdef` 内复制整套提交逻辑 | 复制代码会双倍维护成本 |
| `FE-MP-003` | MUST | PATTERN | 存储、网络、文件、权限、导航等平台能力有统一接口 | 如果业务代码直接调 `uni.*` 平台 API，则判为不通过 | `storage.get()` | 业务组件直接 `uni.getStorage` | 统一接口便于测试与替换 |
| `FE-MP-004` | MUST | STATIC | 跨端代码禁止使用 `window`、`document`、DOM 操作 | 如果共享或移动代码出现 DOM API，则判为不通过 | 用组件与平台接口 | `document.getElementById` | 小程序与 App 无 DOM |
| `FE-MP-005` | MUST | STATIC | 移动尺寸用 `rpx`，PC 用 `px`，不混用 | 如果移动页面写固定 `px` 宽度，则判为不通过 | `width: 750rpx` | `width: 375px` | 单位混用会导致不同屏宽错位 |
| `FE-MP-006` | MUST | STATIC | 刘海屏/底部安全区使用安全区变量或占位 | 如果底部按钮被系统手势条遮挡，则判为不通过 | 安全区 padding | 固定 `bottom: 0` | 安全区处理是移动端基本要求 |
| `FE-MP-007` | SHOULD | PATTERN | 自定义导航栏统一封装，避免每页重复计算状态栏高度 | 如果每页各写一套导航栏，则判为不通过 | 统一 `NavBar` 组件 | 页面内重复计算 | 重复实现会不一致 |
| `FE-MP-008` | MUST | STATIC | 小程序按业务域分包，主包只放启动必需内容 | 如果主包塞入全部页面导致超限，则判为不通过 | `subPackages` 按域拆分 | 所有页面在主包 | 主包超限会无法发布 |
| `FE-MP-009` | MUST | CONTEXT | App 打包默认云打包；满足四条件之一才转离线 | 如果无理由直接采用离线打包，则判为不通过 | 先云打包验证 | 一开始就维护原生工程 | 离线打包成本高且需原生能力 |
| `FE-MP-010` | MUST | STATIC | 权限清单与隐私政策随能力变更同步更新 | 如果新增相机/定位但未声明用途，则判为不通过 | 清单 + 隐私说明 | 用了定位但无声明 | 未声明会被平台拒审 |
| `FE-MP-011` | SHOULD | CONTEXT | `nvue` 只用于确有性能瓶颈的页面，不默认全站使用 | 如果普通列表页也用 `nvue`，则判为不通过 | 长列表/复杂滚动才用 | 全站 `nvue` | `nvue` 样式能力受限 |
| `FE-MP-012` | MUST | CONTEXT | 热更新遵守平台合规边界，不绕过审核更新业务内容 | 如果通过热更新替换审核过的功能，则判为不通过 | 仅修复紧急问题并合规申报 | 热更新上线新功能 | 违规热更新会被下架 |

### 本章自检清单

- [ ] `FE-MP-001` 平台差异走适配层（MUST, PATTERN）
- [ ] `FE-MP-002` 条件编译不复制业务（MUST, STATIC）
- [ ] `FE-MP-003` 平台能力有统一接口（MUST, PATTERN）
- [ ] `FE-MP-004` 无 DOM/window 依赖（MUST, STATIC）
- [ ] `FE-MP-005` rpx/px 不混用（MUST, STATIC）
- [ ] `FE-MP-006` 安全区已处理（MUST, STATIC）
- [ ] `FE-MP-007` 导航栏统一封装（SHOULD, PATTERN）
- [ ] `FE-MP-008` 小程序按域分包（MUST, STATIC）
- [ ] `FE-MP-009` App 打包方式符合触发条件（MUST, CONTEXT）
- [ ] `FE-MP-010` 权限与隐私同步（MUST, STATIC）
- [ ] `FE-MP-011` `nvue` 使用有边界（SHOULD, CONTEXT）
- [ ] `FE-MP-012` 热更新合规（MUST, CONTEXT）

---

## 13. 工程化与协作（FE-ENG）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-ENG-001` | MUST | STATIC | 分支为主干 + 短分支 + tag，不长期并行大分支 | 如果存在存活数周的功能分支，则判为不通过 | `feature/order-export` 短分支 | `develop-2025` 长期分支 | 长分支会积累巨大合并冲突 |
| `FE-ENG-002` | MUST | STATIC | 提交信息说明“为什么”，一个提交只做一类改动 | 如果提交混入格式化、重构、功能三类改动，则判为不通过 | `fix: 修正分页越界` | `update` | 混合提交无法审查与回滚 |
| `FE-ENG-003` | MUST | STATIC | 环境变量按环境分文件，密钥不进仓库 | 如果 `.env` 含真实密钥并提交，则判为不通过 | `.env.example` 占位 | 提交 `.env.production` | 密钥泄漏不可逆 |
| `FE-ENG-004` | MUST | STATIC | 构建命令固定：PC 与移动各自可独立构建 | 如果构建依赖人工步骤或未文档化，则判为不通过 | 按锁定包管理器的 `build:pc`、`build:mp` 脚本 | 只在某台机器能构建 | 构建不可复现会阻塞发布 |
| `FE-ENG-005` | MUST | CONTEXT | 性能预算（首屏、包体、接口数）在发布前核对 | 如果包体显著超预算仍发布，则判为不通过 | 发布前记录指标 | 无预算概念 | 性能退化是渐进且难回溯的 |
| `FE-ENG-006` | SHOULD | STATIC | 评审清单覆盖契约、错误处理、权限、跨端、测试五项 | 如果评审只看样式，则判为不通过 | 按 `FE-FLOW-001` 起的自检报告评审 | 只截图看页面 | 视觉正常不代表逻辑正确 |
| `FE-ENG-007` | MUST | CONTEXT | 发布与回滚步骤可执行，回滚不依赖重新构建 | 如果没有回滚方案，则判为不通过 | 版本化产物 + 回滚脚本 | 出问题只能热修 | 发布必须有退路 |
| `FE-ENG-008` | MUST | STATIC | 证书、签名、密钥材料不进入仓库，由安全渠道管理 | 如果仓库含 `.p12`、`.keystore`、证书密码，则判为不通过 | 密钥库外置 + CI 注入 | 提交签名文件 | 签名泄漏可被冒充发布 |
| `FE-ENG-009` | MUST | PATTERN | CI 至少执行类型检查、单测、构建、契约生成一致性校验 | 如果 CI 只跑 lint，则判为不通过 | 四步齐全 | 只跑格式检查 | CI 是最后一道自动化门禁 |
| `FE-ENG-010` | MUST | PATTERN | 监控与埋点初始化在应用启动时完成，错误统一上报 | 如果页面各自初始化监控，则判为不通过 | 启动入口统一初始化 | 每个页面各自初始化监控 SDK | 重复初始化会丢事件 |

### 本章自检清单

- [ ] `FE-ENG-001` 分支模型符合约定（MUST, STATIC）
- [ ] `FE-ENG-002` 提交单一目的（MUST, STATIC）
- [ ] `FE-ENG-003` 密钥未入库（MUST, STATIC）
- [ ] `FE-ENG-004` 构建可复现（MUST, STATIC）
- [ ] `FE-ENG-005` 性能预算已核对（MUST, CONTEXT）
- [ ] `FE-ENG-006` 评审覆盖五项（SHOULD, STATIC）
- [ ] `FE-ENG-007` 有可执行回滚（MUST, CONTEXT）
- [ ] `FE-ENG-008` 签名材料外置（MUST, STATIC）
- [ ] `FE-ENG-009` CI 四步齐全（MUST, PATTERN）
- [ ] `FE-ENG-010` 监控统一初始化（MUST, PATTERN）

---

## 14. 反例清单（按规则编号索引）

| 反例 | 违反规则 | 后果 |
|---|---|---|
| 页面直接 `fetch`/`uni.request` | `FE-REQ-001`、`FE-DIR-003` | 绕过鉴权、错误归一化与取消 |
| 手写响应 interface | `FE-REQ-002` | 契约变更不报错 |
| 前端自定义分页字段 | `FE-REQ-003`、`CT-PAGE-001` | 与后端及契约测试不一致 |
| 请求层与页面各弹一次 | `FE-REQ-006` | 双提示 |
| 写操作自动重试 | `FE-REQ-009` | 重复提交 |
| PC 把 token 放 `localStorage` | `FE-AUTH-001` | XSS 可窃取会话 |
| 并发 401 各自刷新 | `FE-AUTH-003` | 令牌互相作废 |
| 用角色字符串控按钮 | `FE-AUTH-006` | 细粒度权限失效 |
| 展示后端原始 `message` | `FE-ERR-004` | 泄露内部实现 |
| 空 `catch` | `FE-ERR-007` | 静默失败 |
| 裸 `any` | `FE-CODE-002` | 类型系统失效 |
| 模板硬编码文案 | `FE-CODE-009` | 无法国际化 |
| 业务组件依赖页面 | `FE-COMP-001` | 无法复用 |
| 页面零散覆写组件库 | `FE-STYLE-004` | 升级即崩 |
| 移动端写固定 `px` | `FE-MP-005` | 多屏错位 |
| 页面内条件编译两套业务 | `FE-MP-002` | 双倍维护 |
| `views/` 下自带 `use*.ts` | `FE-DIR-007` | 第二个页面只能复制 |
| 页面 `script` 超 200 行且堆多职责 | `FE-CODE-013` | 复用与测试成本不降 |
| 列表页各写一份分页骨架 | `FE-COMP-008` | 分页默认值、空态与错误处理逐页漂移 |
| 工作区用 `apps/*` 通配，共享包被当成可运行应用 | `FE-DIR-001` | `--filter` 批量操作会把共享包一并带上，构建/发布范围失真 |

### 本章自检清单

- [ ] 本次改动未命中 `FE-REQ-001`–`FE-MP-002` 反例清单任一反例（MUST, STATIC）
- [ ] 命中项已按对应规则编号修正（MUST, PATTERN）

---

## 15. 决策清单（分档）

### 15.1 工具与质量门禁分档

| 档位 | 定位 | 需要装什么 | 资源占用 | 首次跑通 | 能拦住什么 |
|---|---|---|---|---|---|
| **L0 零依赖（本机默认）** | 必须能跑 | 运行时、包管理器、IDE、类型检查（版本按 `AGENTS.md` §9-B） | 约 1–2GB 磁盘 | 10 分钟内 | 类型错误、纯函数逻辑错误、构建失败 |
| **L1 本地增强** | 可选，按需 | L0 + ESLint/Prettier/Stylelint + 浏览器 | 额外 100–300MB | 30 分钟 | 风格、坏味道、可访问性、组件交互 |
| **L2 团队/CI** | 满足触发条件才上 | E2E 工具（按 `AGENTS.md` §9-B）、视觉回归、错误监控、CI 服务 | CI 资源 + 监控额度 | 1–3 天 | 端到端回归、跨端差异、线上错误趋势 |

**L2 触发条件**：多端并行且回归靠人工（≥2 端）；团队 ≥3 人；线上错误无法复现；有专职维护 CI 的人。

### 15.2 覆盖率分层标准（SHOULD）

| 模块 | 建议门槛 | 判定方式 | 说明 |
|---|---|---|---|
| 纯函数/工具/格式化 | ≥80% 行 + 分支 | 单测 coverage | 边界输入必须有正反用例 |
| composable / 业务状态逻辑 | ≥60%–70% | 单测 coverage | 覆盖主要分支与失败路径 |
| 基础组件 | 不按行覆盖率 | 组件测试/演示 | 交互与可访问性优先 |
| 页面 | 不设门槛 | 端到端冒烟 | 逻辑应下沉到 composable |
| 类型/常量/配置 | 不统计 | 不适用 | 无运行时逻辑 |

**本地看报告**：运行锁定包管理器的测试覆盖率命令（如 `test -- --coverage`）后打开 `coverage/index.html`；单包报告在该包 `coverage/` 下。

### 本章自检清单

- [ ] 已按 L0 完成类型检查与单测（MUST, STATIC）
- [ ] L1/L2 仅在触发条件下引入（SHOULD, CONTEXT）
- [ ] 覆盖率按分层标准而非一刀切（SHOULD, CONTEXT）
- [ ] 覆盖率报告可本地打开（MUST, STATIC）

---

## 16. 与其他文档的关系

| 文档 | 本文提供 | 本文索取 |
|---|---|---|
| 《接口契约与错误码规范》 | 前端消费方式、错误提示分层、取消与刷新 | 响应结构、错误码、分页、时间/金额格式、幂等键（`CT-*`） |
| 《前端UI设计规范》 | token 消费方式、样式约束、断点引用 | 颜色/字号/间距/圆角/阴影/动效数值与交互反馈（`UI-*`） |
| 《后端编码规范》 | 前端错误映射与权限消费 | 后端错误映射、状态码语义（`BE-*`） |

### 本章自检清单

- [ ] 未在前端文档重复定义契约或视觉数值（MUST, STATIC）
- [ ] 引用编号真实存在（MUST, STATIC）
- [ ] 冲突按固定优先级处理（MUST, CONTEXT）

---

## 17. AI 交付前自检流程（FE-FLOW）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-FLOW-001` | MUST | PATTERN | 只读本次改动涉及的规则模块 | 如果自检引用无关模块规则，则判为不通过 | 改请求层只读 `FE-REQ`、`FE-ERR` | 每次全量读全部规则 | 全量读会稀释注意力 |
| `FE-FLOW-002` | MUST | STATIC | 逐条记录 `通过 / 不通过 / 需人工确认` 并带 `文件:行号` | 如果只给总体结论，则判为不通过 | `FE-REQ-006 ／ 不通过 ／ order.ts:41` | “已检查，无问题” | 无记录无法复核 |
| `FE-FLOW-003` | MUST | CONTEXT | 不通过项最多迭代两轮，仍失败如实上报 | 如果无限修改或隐瞒，则判为不通过 | 两轮后列未解决项 | 反复改到“看起来能过” | 两轮后通常是设计问题 |
| `FE-FLOW-004` | MUST | STATIC | 报告格式固定，通过项只汇总数量 | 如果逐条罗列通过项，则判为不通过 | “通过 15 项；不通过 2 项” | 展开全部通过项 | 报告目的是暴露风险 |
| `FE-FLOW-005` | MUST | CONTEXT | 不得为过检查删功能、吞异常、放宽校验、注释测试、降级规则 | 如果出现任一行为，则判为不通过 | 修实现不改规则 | 把 MUST 改 SHOULD | 检查目的是质量 |
| `FE-FLOW-006` | MUST | CONTEXT | 存量文件只对改动范围负责，不做全文件重写 | 如果借自检重写无关代码，则判为不通过 | 只改受影响方法与调用点 | 顺手重构整个页面 | 扩大改动面会引入回归 |

### 17.1 六个步骤

1. **定位规则**：按改动所属层（请求/鉴权/组件/样式/跨端）读对应章节，列出适用编号。
2. **生成/修改代码**：按规则实现，不留“待补”。
3. **逐条比对**：`STATIC`/`PATTERN` 自行判定；`CONTEXT` 无把握转人工确认。
4. **自我修正**：只修“不通过”，最多两轮；仍失败即停止上报。
5. **输出报告**：四列 `规则编号 | 结论 | 文件:行号 | 说明`；通过项写总数。
6. **禁止作弊**：不删功能、不吞异常、不放宽校验、不注释测试、不降级 `MUST`。

### 17.2 新建文件与存量文件

| 场景 | 责任范围 | 自检要求 |
|---|---|---|
| 新建文件 | 全文件 | 全部适用规则逐条判定 |
| 修改存量文件 | 仅改动行及直接影响的方法/组件 | 只判定改动范围；旧问题记入“存量问题”，不阻塞本次 |

### 17.3 报告模板

```text
自检报告
适用模块：`FE-REQ`、`FE-ERR`
通过：12 项（汇总）

| 规则编号 | 结论 | 文件:行号 | 说明 |
|---|---|---|---|
| FE-REQ-006 | 不通过 | order.ts:41 | 请求层与页面重复提示 |
| FE-ERR-002 | 需人工确认 | error-messages.ts:18 | 契约符号名需核对 |
```

### 本章自检清单

- [ ] `FE-FLOW-001` 只读相关章节（MUST, PATTERN）
- [ ] `FE-FLOW-002` 逐条记录结论与行号（MUST, STATIC）
- [ ] `FE-FLOW-003` 最多两轮迭代（MUST, CONTEXT）
- [ ] `FE-FLOW-004` 报告格式固定（MUST, STATIC）
- [ ] `FE-FLOW-005` 无作弊式修改（MUST, CONTEXT）
- [ ] `FE-FLOW-006` 存量文件只对改动范围负责（MUST, CONTEXT）

---

## 18. 必须人工确认的规则（FE-HUMAN）

| 编号 | 级别 | 判定 | 通过条件 | 失败信号 | ✅ 合格示例 | ❌ 不合格示例 | 为什么 |
|---|---|---|---|---|---|---|---|
| `FE-HUMAN-001` | MUST | CONTEXT | 交互流程符合业务习惯，并经需求方确认 | 如果 AI 只按页面能点通就判通过，则判为不通过 | 报告列出流程待确认 | “能操作，通过” | 能操作不等于流程合理 |
| `FE-HUMAN-002` | MUST | CONTEXT | 视觉还原属于审美判断，需设计师确认 | 如果 AI 以“用了 token”判定视觉通过，则判为不通过 | 标注“视觉需设计确认” | 用了变量即判通过 | 数值正确仍可能视觉不协调 |
| `FE-HUMAN-003` | MUST | CONTEXT | 权限码与实际角色覆盖关系经确认 | 如果只检查 `can()` 调用存在，则判为不通过 | 角色 × 操作清单待确认 | 有 `can()` 即通过 | 权限码齐全仍可能漏角色 |
| `FE-HUMAN-004` | MUST | CONTEXT | 面向用户的文案语气与业务表述经确认 | 如果 AI 自行生成业务文案并宣称通过，则判为不通过 | 文案待业务确认 | 自造术语上线 | 文案涉及业务理解 |
| `FE-HUMAN-005` | MUST | CONTEXT | 目标平台与版本支持范围经确认 | 如果假定所有平台都支持某能力，则判为不通过 | 列出平台支持矩阵 | 默认小程序支持 | 平台差异会导致功能不可用 |
| `FE-HUMAN-006` | MUST | CONTEXT | 性能预算与体验阈值经确认 | 如果 AI 自定阈值并宣称达标，则判为不通过 | 标注阈值待确认 | 直接写“性能达标” | 无基线时数字只是猜测 |

### 本章自检清单

- [ ] `FE-HUMAN-001` 交互流程待确认（MUST, CONTEXT）
- [ ] `FE-HUMAN-002` 视觉审美待确认（MUST, CONTEXT）
- [ ] `FE-HUMAN-003` 权限覆盖待确认（MUST, CONTEXT）
- [ ] `FE-HUMAN-004` 业务文案待确认（MUST, CONTEXT）
- [ ] `FE-HUMAN-005` 平台支持范围待确认（MUST, CONTEXT）
- [ ] `FE-HUMAN-006` 性能阈值待确认（MUST, CONTEXT）

---

## 19. 附录：AI 代码评审提示词

```text
你是前端代码评审员。规则来源：docs/specs/frontend-spec.md（v1.5）。
检查范围：仅本次改动文件（清单见下），不评审未改动代码，不重写文件。
改动文件：
- <path>:<startLine>-<endLine>

要求：
1) 按改动所属层定位章节，只读取相关规则区间。
2) 区分 STATIC/PATTERN/CONTEXT；CONTEXT 无把握标“需人工确认”。
3) 只输出“不通过”和“需人工确认”；通过项汇总为一句数量。
4) 输出表格：规则编号 | 级别 | 文件:行号 | 问题 | 修复建议。
5) 引用规则只写编号，不写章节号与行号；查不到写“未在文档中查到，需确认”。
6) 不得建议通过删功能、吞异常、放宽校验、注释测试、降级 MUST 消除问题。
7) 契约问题以《接口契约与错误码规范》为准，视觉数值以《前端UI设计规范》为准。
```

### 本章自检清单

- [ ] 提示词含规则来源与版本（MUST, STATIC）
- [ ] 提示词限定改动范围（MUST, STATIC）
- [ ] 提示词只报不通过与待确认项（MUST, STATIC）
- [ ] 提示词含防幻觉与防作弊要求（MUST, STATIC）

---

## 20. 规则编号索引

| 章节 | 模块前缀 | 编号区间 | 条数 | 自检清单 |
|---|---|---|---|---|
| §0 | `FE-DOC` | 001–004 | 4 | 有 |
| §1 | —（决策） | 无编号 | 29 项决策 | 有 |
| §2 | `FE-STACK` | 001–006 | 6 | 有 |
| §3 | `FE-COMBO` | 001–004 | 4 | 有 |
| §4 | `FE-DIR` | 001–007 | 7 | 有 |
| §5 | `FE-DEP` | 001–006 | 6 | 有 |
| §6 | `FE-REQ` | 001–014 | 14 | 有 |
| §7 | `FE-AUTH` | 001–010 | 10 | 有 |
| §8 | `FE-ERR` | 001–007 | 7 | 有 |
| §9 | `FE-CODE` | 001–013 | 13 | 有 |
| §10 | `FE-COMP` | 001–008 | 8 | 有 |
| §11 | `FE-STYLE` | 001–010 | 10 | 有 |
| §12 | `FE-MP` | 001–012 | 12 | 有 |
| §13 | `FE-ENG` | 001–010 | 10 | 有 |
| §14 | —（反例） | 引用现有编号 | 19 条 | 有 |
| §15 | —（决策分档） | 无编号 | 4 项 | 有 |
| §16 | —（关系） | 无编号 | 3 条 | 有 |
| §17 | `FE-FLOW` | 001–006 | 6 | 有 |
| §18 | `FE-HUMAN` | 001–006 | 6 | 有 |
| §19 | —（附录） | 无编号 | 4 项 | 有 |
| §20 | —（索引） | 无编号 | 4 项 | 有 |

**规则总数**：123 条（`FE-*`）。

### 跨文档引用

| 场景 | 写法 | 归属 |
|---|---|---|
| 响应结构/错误码/分页/时间与金额 | 引用 `CT-*` | 契约文档 |
| 颜色/字号/间距/圆角/阴影/断点 | 引用 `UI-PC-*` / `UI-H5-*` | UI 文档 |
| 错误映射与状态码语义 | 引用 `BE-*` | 后端文档 |
| 冲突 | 见 `AGENTS.md` §1 | 唯一定义处，本文件不复述 |

### 本章自检清单

- [ ] 新增规则已登记且编号连续（MUST, STATIC）
- [ ] 跨文档引用真实存在（MUST, STATIC）
- [ ] 规则总数与索引一致（MUST, STATIC）
- [ ] 冲突优先级与其余文档一致（MUST, CONTEXT）
