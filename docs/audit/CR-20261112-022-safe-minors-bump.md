# CR-20261112-022 安全与工具库 minor 升级（POI / jjwt / 契约校验器）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户「按顺序执行」第 1 项（原 Dependabot #19 里被打包的有效升级） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改依赖版本，不改业务代码 |
| 关联 | `CR-20261112-019`（框架只收 patch）；断言 #87 |

## 1. 背景

Dependabot #19 把"框架升级"与"安全/工具库 minor"打包在一个 PR 里，其中框架部分（MyBatis-Plus 3.5.18.1）会让编译失败，导致整个 PR 不可用。本 CR 把其中**独立、可验证**的三项拆出来单独升级。

## 2. 改动

| 依赖 | 变更 | 用途 |
|---|---|---|
| `org.apache.poi:poi-ooxml` | 5.4.1 → **5.5.1** | Excel 导入/导出/模板 |
| `io.jsonwebtoken:jjwt-*` | 0.12.5 → **0.13.0** | 令牌签发与解析 |
| `com.atlassian.oai:swagger-request-validator-mockmvc` | 2.44.9 → **2.46.1** | 契约测试 |

`AGENTS.md` 未收录这三项的版本（该节只锁框架/运行时等主选型）（该节只锁框架/运行时等主选型），因此本次不需要改技术栈表。

## 3. 逐项验证方式

**没有一次性全升**，而是分两步，每步跑完整后端验证（`mvn -B -pl web -am verify`），确保红灯能立刻定位到具体库：

1. 先升 POI → 170 用例通过（`ExcelStreamParserTest`/`ExcelExportWriterTest`/`ExcelTemplateWriterTest` 直接覆盖 POI 读写，是最容易受影响的）；
2. 再升 jjwt + 契约校验器 → 170 用例通过（`ContractValidationTest` 7 条直接覆盖校验器 API，`AuthAppService` 相关路径覆盖 jjwt）。

## 4. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd server && mvn -B -pl web -am verify` | **170 用例通过**（common 26 / system 135 / web 9），BUILD SUCCESS |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 5. 风险与后续

- jjwt 0.12 → 0.13 属 0.x 的 minor（语义上可能破坏），但本项目的用法（HS256 签发/解析、过期校验）已被 170 用例覆盖，风险可控。
- 仍**不建议**自动合 maven 框架核心的 minor（见 `CR-20261112-019`）：本 CR 的三项能被现有测试直接覆盖，而框架升级的破坏面（如 JSqlParser 拆包）往往编译期才暴露。