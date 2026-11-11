# CR-20261111-003 后端集成测试接入 CI（profile test + 独立测试库 + 服务容器）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户（2026-11-11 指示「做1」：后端测试真正进 CI） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-11 |
| 变更层级 | **项目层**（配置、测试、CI）＋ **通用层单点**（`AGENTS.md` §9-B「测试框架」行补测测试基础设施口径） |
| 关联 | `docs/baseline/assertions.md` #68；`docs/audit/impl-notes.md`；ADR-005、ADR-006 |

## 1. 背景

CI 的后端 job 长期是 `mvn -DskipTests package`：**只编译、零测试**。全仓唯一测试 `NotificationPublisherTransactionTest` 需要真实 MySQL/Redis，且用 `@ActiveProfiles("local")` —— 只能读到本机被 gitignore 的 `application-local.yml`，因此在 CI 上必然失败，只能被跳过。结果："CI 全绿"对后端行为没有任何证明力。

本机**没有 Docker**，所以不采用 Testcontainers（那种方案只能靠 CI 验证、本地无法复现）。

## 2. 范围

**改**：

- `application.yml`：数据源 url 由硬编码改为 `${APP_DB_URL:...}`（此前端口/库名写死 3307/admin_starter，CI 无法覆盖）。其余口令类配置早已是环境变量。
- 新增 `server/web/src/test/resources/application-test.yml`：profile `test`，默认连**独立库** `admin_starter_test`，全部参数可用 `APP_TEST_DB_*` / `APP_REDIS_*` 覆盖；可选 `spring.config.import` 叠加本地 `application-local.yml`（CI 无此文件时自动跳过）。
- `NotificationPublisherTransactionTest`：`@ActiveProfiles("local")` → `test`；新增**安全阀** `@BeforeEach`：JDBC URL 不含 `test` 直接失败。
- `server/system/pom.xml`：补 `spring-boot-starter-test`（test 作用域），否则 system 模块无法编译测试。
- 新增 `DataPermissionTablesTest`（6 用例，纯函数）：受控/豁免/未登记（fail-closed）、表名归一化、受控与豁免不重叠、通知公共行例外的别名前缀。
- `.github/workflows/ci.yml`：backend job 加 MySQL 8 + Redis 7 服务容器与健康检查，注入 `APP_TEST_DB_*` / `APP_REDIS_*`，命令改为 `mvn -B -pl web -am verify`（不再 skipTests），失败时上传 surefire 报告。
- `AGENTS.md` §9-B「测试框架」行：补"集成测试走 profile `test` + 独立 `*_test` 库，CI 用服务容器"。

**不改**：任何业务代码、接口、表结构、权限码、错误码、配置项语义（`APP_DB_URL` 只是给已有 URL 套上可覆盖外壳，默认值与原值逐字相同）。

## 3. 涉及事实

| 事实 | 变更前 | 变更后 |
|---|---|---|
| 后端测试在 CI 是否执行 | 否（skipTests） | 是（`mvn verify`，8 个用例） |
| 测试连哪个库 | 本机开发库 `admin_starter`（靠 local profile 固定） | 独立库 `admin_starter_test`，可环境变量覆盖，且有"库名必须含 test"的断言 |
| 测试基础设施 | 无定义 | `AGENTS.md` §9-B 测试框架行（profile + 独立库 + CI 服务容器） |

## 4. 影响文档与引用点

| 文档 | 影响 |
|---|---|
| `AGENTS.md` | §9-B 测试框架行（通用层，需本条变更记录） |
| `docs/baseline/assertions.md` | 新增 #68：集成测试必须连 `*_test` 库且 CI 必须真的跑 |
| `docs/audit/impl-notes.md` | 记录改造过程、验证数据与两条待办 |
| `README.md` | 变更记录 + 本地跑后端测试的前置步骤（建 `admin_starter_test` 并授权） |

## 5. 兼容性

- 运行时行为零变化：`APP_DB_URL` 默认值与原硬编码 URL 完全一致。
- 本地开发：`mvn test` 现在需要 MySQL 上存在 `admin_starter_test` 库（一次性的 `create database` + `grant`）；README 已写命令。
- CI：backend job 从"编译 33s"变为"编译 + 起容器 + 集成测试"，预计 1–2 分钟；`timeout-minutes` 已调到 25。

## 6. 回滚方式

`git revert` 本 CR 对应提交；随后删掉 `admin_starter_test` 库即可（测试库无业务数据）。

## 7. 分级与审批

| 档位判定项 | 结论 |
|---|---|
| ① 只动 1 个模块 | 否（system/web 两模块 + CI + 通用层一行） |
| ② 不新增/删除/改名接口路径或字段 | 是 |
| ③ 不动表结构与数据迁移 | 是（测试库由既有 V1–V16 迁移生成，未新增迁移） |
| ④ 不新增权限码 | 是 |
| ⑤ 不新增错误码 | 是 |
| ⑥ 未命中 `PRC-APR-001` | 是（逐类未命中） |

→ 仍属"跨模块行为收紧 + 通用层单点"，与 CR-20261111-001/002 同类。**规则缺口（`RULE-GAP`，第三次出现）**：A/B/C 档位表没有容纳"跨模块工程化改动 + 通用层单点更新"的归类，本轮沿用前两次处置（拆成单模块单元 + 通用层单独留痕），未擅改档位表。建议下一次治理迭代补一条归口。

## 8. 条目状态变更

无 `DRAFT`/`APPROVED` 条目变化；`AGENTS.md` §9-B 测试框架行由"JUnit 5 + Mockito + AssertJ"补充为含集成测试口径，依据 ADR-005/ADR-006 不变。

## 9. 代码改动顺序

配置（`application.yml` + `application-test.yml`）→ 测试改造（profile + 安全阀）→ 补 system 模块测试依赖与被测覆盖 → CI 工作流 → 通用层一行 → 回填 README/断言/impl-notes。

## 10. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端（含集成测试） | `cd server && mvn -o -B -pl web -am verify` | **8 用例通过**（DataPermissionTables 6 + NotificationPublisher 事务 2），jar 正常产出 |
| 安全阀负向验证 | `APP_TEST_DB_URL=...admin_starter... mvn test` | **两个用例均失败**并提示「拒绝在非测试库上运行集成测试」 |
| 测试库 | 手工核对 | 24 张表、Flyway 到 V16、种子用户 9 个；开发库 `sys_notice` 无 `TX-%` 残留 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0（warning 数与改前持平） |
| 前端 | 未改动 | — |

## 11. 异议记录

- 用户方案无异议。**技术异议（已自行采纳替代方案）**：原计划用 Testcontainers，但本机没有 Docker，会导致"本地无法复现、只能靠 CI 验证"，故改为"环境变量注入 + CI 服务容器"——本地能真跑、CI 也能跑，且不引入 Docker-in-Docker 的隐性依赖。此判断已写入 §1 背景。
- 未解决的技术债（如实登记）：`mvn verify` 目前只覆盖 2 个测试类，业务逻辑仍主要靠 E2E 与人工验证；契约测试（`docs/api/examples/backend`）仍未落地。
