# ADR-005 技术栈基线（后端 / 前端 / 数据 / 工具链）

| 项目 | 内容 |
|---|---|
| 编号 | ADR-005 |
| 版本 | v1.0（2026-11-11） |
| 状态 | 已实施 |
| AGENTS 状态 | `IMPLEMENTED` |
| 读者 | 决策人、架构、AI 编码助手 |
| 依赖 | `AGENTS.md` §9-A/§9-B；`docs/audit/CR-20261111-001-agents-tech-stack-baseline.md`；`docs/decisions/ADR-003-pc-multi-app-workspace.md`；`docs/decisions/ADR-004-excel-library-selection.md` |
| 日期 | 2026-11-11 |
| 决策人 | 项目负责人（用户，2026-11-11 逐项确认） |
| 关联条目 | `FE-STACK-001`、`FE-STACK-004`、`FE-DIR-001`、`BE-ARCH-010` |

## 1. 背景

项目已实现到 Flyway V16 并稳定运行，但 `AGENTS.md` §9 的两张表一直为空：技术栈在代码里客观存在，在**规范里没有定义方**。由此产生两个问题：

1. `AGENTS.md` §9 自称「本节是技术栈唯一定义方」，但表为空 → 其他文档只能"引用一个空节"；
2. `FE-STACK-001`（技术栈须经 `AGENTS.md` §9-B 确认后才开工）与 `FE-STACK-004`（包管理器由 `AGENTS.md` §9-B 选定且唯一）长期无法满足，只能以 `RISK-ACCEPTED` / `RULE-GAP` 挂在 `impl-notes.md`。

本 ADR 的职责是**把已经存在并被验证的技术栈追认为正式决策**，而不是引入新选型。版本号全部取自仓库事实（`server/pom.xml`、各模块 `pom.xml`、`apps/pc/package.json`、`apps/shared/package.json`、构建产物 `admin-starter.jar` 的 `BOOT-INF/lib`），不写"常见版本"。

## 2. 约束

| 约束 | 来源 | 是否硬约束 |
|---|---|---|
| 后端多模块：`common` / `system` / `auth` / `web` | `BE-ARCH-010` | 硬 |
| 前端按多端组织：`apps/pc`、`apps/mobile`、`apps/shared`、`packages/ui-tokens` | `FE-DIR-001`、`ADR-003` | 硬 |
| 包管理器唯一、锁文件唯一 | `FE-STACK-004` | 硬 |
| 内部包用 `workspace:*`，不跨包相对路径 | `FE-DEP-001` | 硬 |
| `apps/shared` 不得含 Web-only 能力 | `FE-COMBO-003` | 硬 |
| 团队技能栈为 Java + Vue 全栈 | `AGENTS.md` §9-A（用户 2026-11-11 确认） | 硬 |
| 需满足等保二级/三级合规 | `AGENTS.md` §9-A（用户 2026-11-11 确认） | 硬（对日志留存、脱敏、口令策略有后续要求） |

## 3. 候选方案

| 决策点 | 候选 | 结论 | 理由 |
|---|---|---|---|
| 语言/运行时 | Java 17 LTS / Java 21 LTS | **Java 17** | 现状已稳定；21 升级属独立变更，需要回归虚拟线程与新 GC 行为 |
| 后端框架 | Spring Boot 3.2.12 / Spring Boot 2.7.x | **3.2.12（已采用）** | 2.7 已 EOL；3.x 是 JDK 17 与 Spring Security 6 的唯一主线 |
| 数据访问 | MyBatis-Plus 3.5.7 / Spring Data JPA | **MyBatis-Plus（已采用）** | 本项目核心是"数据权限 SQL 拼装 + 复杂列表查询"，JPA 的 Criteria 反而更难控；团队栈亦为 MyBatis |
| 数据库 | MySQL 8.0 / PostgreSQL | **MySQL 8.0（已采用）** | 现状；且已依赖 MySQL 特性（STORED 生成列做"删后可重建"唯一键、`FIND_IN_SET`） |
| 迁移工具 | Flyway 9.22.3 / Liquibase | **Flyway（已采用）** | SQL 即事实、与 `V1..V16` 现状一致；Liquibase 的 XML/YAML changelog 会引入第二套描述 |
| 缓存 | Redis 5.0.14 / 无缓存 / 本地缓存 | **Redis（已采用）** | 令牌黑名单、数据权限缓存、会话节流都依赖跨请求共享状态 |
| 消息队列 | 不引入 / RabbitMQ / Kafka | **不引入（已采用无 MQ）** | 现有场景（通知、导出、定时任务）均为进程内事件 + 定时任务，引 MQ 属过度设计；接入前需另开 ADR |
| 鉴权 | JWT（jjwt 0.12.5）+ HttpOnly Cookie / Session | **JWT + Cookie（已采用）** | 无状态水平扩展友好；`sid` + `token_version` 双通道失效已解决吊销问题（见 `docs/design/session-management.md`） |
| 前端框架 | Vue 3.5 + Vite 5 / React + Ant Design | **Vue 3 + Vite（已采用）** | 团队栈 + 既有 15 个页面资产；React 迁移无业务收益 |
| UI 组件库 | Element Plus 2.8 / Ant Design Vue | **Element Plus（已采用）** | 现状；换库等于重做全部页面 |
| 跨端框架 | uni-app / Taro / 原生 H5+App / 暂不引入 | **未定（本轮不决策）** | 目标平台含 H5/小程序/App（用户确认），但 `apps/mobile` 仅有 README；选型需要另行评估包体积、小程序能力边界、与 Element Plus 的兼容性 → **未选型前禁止在 `apps/mobile` 落业务代码** |
| 状态管理/请求 | Pinia 2.2 / Vuex；axios 1.7 | **Pinia + axios（已采用）** | 现状；axios 拦截器已承载信封解包、401 单飞刷新、413/422 特例 |
| 包管理器 | pnpm 11.7 workspace / npm / yarn | **pnpm（已采用）** | 单一 `pnpm-lock.yaml`，`allowBuilds` 精确控制 postinstall |
| 后端构建 | Maven 3.6.3 / Gradle | **Maven（已采用）** | 现状；多模块 + `-pl web -am` 的增量构建已写入 CI |
| 测试 | JUnit 5 + Mockito + AssertJ（后端）/ 前端测试框架 | **后端已采用；前端未定** | 前端当前无测试框架（`apps/pc` 无 vitest/jest 依赖），补测试框架属独立变更 |
| Excel | Apache POI 5.4.1 / EasyExcel | **POI（已采用）** | 见 `ADR-004`，此处不重复论证 |
| API 文档 | springdoc 2.3.0 | **springdoc（已采用）** | `/v3/api-docs` + `openapi.generated.yaml` 的导出链路依赖它 |

## 4. 决定

以 `AGENTS.md` §9-B 的九行表格为**唯一登记处**，逐行填写结论与依据 ADR；本 ADR 只记录"为什么是这些候选、为什么否掉其他"，不复述版本号（避免双定义）。

- 后端：Java 17 + Spring Boot 3.2.12 + MyBatis-Plus 3.5.7（MyBatis 3.5.16）+ MapStruct 1.5.5 + Flyway 9.22.3 + MySQL 8.0 + Redis（Spring Data Redis 3.2.12）+ jjwt 0.12.5 + Apache POI 5.4.1 + springdoc 2.3.0。
- 前端：Vue 3.5.13 + Vite 5.4.11 + TypeScript 5.6.3 + Element Plus 2.8.8 + Pinia 2.2.6 + vue-router 4.4.5 + axios 1.7.9；跨端框架**未定**。
- 工具链：Node ≥ 22、pnpm 11.7.0（workspace 显式列包）、Maven 3.6.3；测试仅后端（`spring-boot-starter-test`）。
- 目录约定：`server/{common,system,auth,web}` + `apps/{pc,mobile,shared}` + `packages/ui-tokens`，详见 `ADR-003`。

## 5. 后果

| 类型 | 内容 |
|---|---|
| 正面 | 技术栈从"无定义方"变为可引用的单一事实源；`FE-STACK-001`/`FE-STACK-004` 由"无法满足"转为"已满足"；后续新增依赖有明确比对基线（先看是否落在既有栈内） |
| 负面 | 版本一旦写进 ADR 就产生维护责任：升级需新 ADR 或变更记录，不能"顺手升"。已存在的两处版本分散（`AGENTS.md` §9-B 与各 `pom.xml`）必须靠"只在此处写版本、代码处写实现"来维持 |
| 需要监控的 | ① 跨端框架选型（阻塞 H5/小程序/App 落地）；② 等保二级/三级的日志留存与脱敏基线（当前操作日志无归档/留存策略、无留存期配置）；③ 前端无测试框架，CI 只能做类型检查与构建；④ MySQL 8.0.46 与 Flyway 9.22.3 的版本绑定（升级需同步验证生成列与 `FIND_IN_SET` 行为） |
| 已知风险 | Java 17 与 Node 22 的上游支持周期有限，需在 EOL 前规划升级；`RISK-ACCEPTED`：等保要求尚未拆解为可验收条目，属合规待办而非技术栈缺陷 |

## 6. 已否决的建议

| 建议 | 提出方 | 否决理由 | 复核触发条件 |
|---|---|---|---|
| 直接升 Java 21 / Spring Boot 3.3+ | 通用最佳实践 | 属独立变更，需回归 JDK/框架/依赖三处，与本 ADR"追认现状"目标无关 | 出现性能或安全诉求时另开 ADR |
| 用 JPA 替换 MyBatis-Plus | 通用最佳实践 | 数据权限是 SQL 级行过滤 + 公共行例外，MyBatis-Plus 的 Wrapper 拼装更直接；换 ORM 等于重写全部仓储层 | 若将来要求跨库移植（PG/Oracle）再评估 |
| 引入 MQ 承载通知与导出 | 架构讨论 | 当前并发与延迟要求已由进程内事件 + 定时任务满足，引 MQ 增加部署与一致性成本 | 需要多实例广播、削峰、或跨系统解耦时重开 |
| 前端改用 React + Ant Design | 团队外建议 | 团队技能栈为 Vue；15 个页面与 composables 需重写，无业务收益 | 团队栈发生结构性变化时 |
| 先给 `apps/mobile` 定框架再补 §9-B | 执行顺序建议 | 会重演"实现先行、定义缺失"的问题；应先在 §9-B 留"未定"，选型后再落代码 | 跨端需求进入迭代计划时 |

## 7. 关联与变更

| 项 | 内容 |
|---|---|
| 影响文档 | `AGENTS.md` §9-A/§9-B；`docs/specs/INDEX-facts.md`、`docs/specs/facts.yaml`（新增"技术栈版本与选型"事实）；`docs/audit/impl-notes.md`；`README.md` 变更记录 |
| 影响代码 | 无 |
| 回滚方式 | 回滚 `AGENTS.md` §9 与索引条目即可，本 ADR 标记为 `DEPRECATED`；无运行时影响 |
| 后续动作 | ① 跨端框架选型 ADR（阻塞项）；② 等保要求拆解为条目；③ 前端测试框架选型（可与 ① 合并） |

## 8. 完成标准

- 候选方案 ≥ 2 且被否方案有具体理由（本 ADR 每个决策点均给出替代项与否决理由）。
- 后果四段齐全（正面 / 负面 / 需要监控的 / 已知风险）。
- 选型结论能被 `AGENTS.md` §9-B 对应行引用，且 §9-B 不在别处重复版本号。
- 决策人、日期齐全，无未解决 TODO。

## 9. 自检清单

- [x] 背景只写事实与问题（MUST, STATIC）
- [x] 候选方案 ≥ 2，被否理由具体（MUST, STATIC）
- [x] 后果四段齐全（MUST, STATIC）
- [x] ADR 状态与 `AGENTS.md` 状态按 §0 映射一致（MUST, STATIC）
- [x] 版本号来自仓库事实（`pom.xml` / `package.json` / 构建产物），未使用"常见版本"（MUST, STATIC）
- [x] 未选型项（跨端框架）显式写"未定"，未代用户决策（MUST, CONTEXT）
