# 事实索引（INDEX-facts）

> 版本：v1.7（2026-11-11，新增「技术栈版本与选型」事实，定义方 `AGENTS.md` §9；同步修正表尾陈旧条目数）
> 作用：变更影响面查询的唯一入口。**只做事实映射，不写规则正文**（`PRC-REL-003`）。
> 格式与维护规则见 `startup-change-process.md` `PRC-IDX-001`–`PRC-IDX-005`；每条事实只有一个定义方，出现两个即缺陷。
> 编号写法：定义方写**单个规则编号**；引用位置写**规则编号列表**。仅两类例外：① 由 `AGENTS.md` 的状态、目录与模板映射定义管辖的事实，定义方写该文件的单一节号；② 由 `PRC-LAY-007` 登记的项目层资产，定义方写该资产路径。不使用通配符、区间、缩写。
> 机器可读版本：`docs/specs/facts.yaml`，**以本表为源派生**；`owner`／`refs` 与本表逐行一致，由 `spec-lint` `SL-FACT-001` 校验。

## 1. 使用方式

1. 任何变更先在此检索相关事实，逐条拿到“定义方”和“引用位置”。
2. 影响面清单必须逐条列引用点，禁止凭记忆列举（`PRC-IDX-001`）。
3. 查不到时写“索引未覆盖该事实”，并建议补入（`PRC-IDX-002`）。
4. 事实变更后同批更新本表并刷新核对时间（`PRC-IDX-003`）；新增事实必须登记（`PRC-IDX-004`）。
5. 索引与文档不一致时以文档为准并修索引（`PRC-IDX-005`）。

## 2. 索引表

| 事实 | 定义方（文档 + 编号） | 引用位置（文档 + 编号） | 最后核对时间 |
|---|---|---|---|
| 统一响应信封 `code/message/data/traceId` | `api-contract.md` `CT-RESP-001` | `backend-spec.md` `BE-CON-001`；`frontend-spec.md` `FE-REQ-003` | 2026-11-08 |
| 成功响应不得无响应体（禁止 204） | `api-contract.md` `CT-RESP-009` | `backend-spec.md` `BE-CON-001`；`frontend-spec.md` `FE-REQ-003` | 2026-11-08 |
| 成功码 `0` | `api-contract.md` `CT-RESP-002` | `frontend-spec.md` `FE-REQ-004` | 2026-11-08 |
| 业务错误码分段与符号名 | `api-contract.md` `CT-ERR-001` | `api-contract.md` `CT-ERR-002`；`backend-spec.md` `BE-CON-002`、`BE-ERR-001`；`frontend-spec.md` `FE-ERR-002` | 2026-11-08 |
| 基础错误码固定值（10 个码与 HTTP 状态） | `api-contract.md` `CT-ERR-011` | `api-contract.md` `CT-ERR-006`、`CT-ERR-012`；`backend-spec.md` `BE-ERR-001` | 2026-11-08 |
| 体积超限 `413` 响应体（`data.maxSize`＋真实上限文案） | `api-contract.md` `CT-ERR-012` | `api-contract.md` `CT-HTTP-003`、`CT-ERR-011` | 2026-11-08 |
| 枚举是否进字典以业务需求方标注为准 | `backend-spec.md` `BE-CFG-009` | `backend-spec.md` `BE-CFG-011` | 2026-11-08 |
| 字典项 `value` 被代码消费后只可改 `label` | `backend-spec.md` `BE-CFG-010` | `backend-spec.md` `BE-CFG-009` | 2026-11-08 |
| 字段校验错误结构 `data.errors[{field,code,message}]` | `api-contract.md` `CT-ERR-006` | `backend-spec.md` `BE-VAL-006`；`frontend-spec.md` `FE-ERR-005` | 2026-11-08 |
| 字段级错误码枚举（`REQUIRED`／`INVALID_FORMAT`／`TOO_LONG`／`VALIDATION_FAILED`） | `api-contract.md` `CT-ERR-006` | `api-contract.md` `CT-ERR-005`、`CT-ERR-011`；`backend-spec.md` `BE-VAL-006`；`frontend-spec.md` `FE-ERR-005` | 2026-11-08 |
| HTTP 状态码与业务语义映射 | `api-contract.md` `CT-HTTP-003` | `backend-spec.md` `BE-CON-010`；`frontend-spec.md` `FE-ERR-002` | 2026-11-08 |
| 校验失败状态码 `422` | `api-contract.md` `CT-HTTP-003` | `backend-spec.md` `BE-VAL-006`；`frontend-spec.md` `FE-ERR-005` | 2026-11-08 |
| 请求不可解析统一 `400`＋`INVALID_PARAMETER` | `api-contract.md` `CT-HTTP-009` | `backend-spec.md` `BE-VAL-006`；`frontend-spec.md` `FE-ERR-002` | 2026-11-08 |
| 登录与用户字段长度上限 | `api-contract.md` `CT-FMT-009` | `backend-spec.md` `BE-VAL-001`、`BE-VAL-006`；`frontend-spec.md` `FE-ERR-005` | 2026-11-08 |
| 删除语义（首次 200／重复 200／从未存在 404） | `api-contract.md` `CT-HTTP-007` | `backend-spec.md` `BE-IDEM-005`；`frontend-spec.md` `FE-REQ-009` | 2026-11-08 |
| 越权访问 404／缺功能权限 403 判定 | `api-contract.md` `CT-HTTP-008` | `backend-spec.md` `BE-SEC-003`；`api-contract.md` `CT-ERR-009` | 2026-11-08 |
| 分页字段 `page/pageSize/list/total` | `api-contract.md` `CT-PAGE-001` | `backend-spec.md` `BE-CON-004`；`frontend-spec.md` `FE-REQ-003` | 2026-11-08 |
| 分页默认值 `page=1`、`pageSize=20` | `api-contract.md` `CT-PAGE-002` | `frontend-spec.md` `FE-REQ-003`；`ui-spec.md` `UI-PC-TBL-007` | 2026-11-08 |
| `pageSize` 硬上限 `100` | `api-contract.md` `CT-PAGE-002` | `api-contract.md` `CT-PAGE-003`；`backend-spec.md` `BE-DB-007` | 2026-11-08 |
| 时间格式 | `api-contract.md` `CT-FMT-001` | `backend-spec.md` `BE-CON-005`；`frontend-spec.md` `FE-CODE-008` | 2026-11-08 |
| 金额格式 | `api-contract.md` `CT-FMT-003` | `backend-spec.md` `BE-CON-006` | 2026-11-08 |
| 大整数 ID 以字符串传输 | `api-contract.md` `CT-FMT-004` | `backend-spec.md` `BE-CON-001`；`frontend-spec.md` `FE-REQ-014` | 2026-11-08 |
| 幂等键 `Idempotency-Key` | `api-contract.md` `CT-IDEM-001` | `backend-spec.md` `BE-CON-007`；`frontend-spec.md` `FE-REQ-010` | 2026-11-08 |
| `traceId` 生成与透传 | `api-contract.md` `CT-TRACE-001` | `backend-spec.md` `BE-LOG-001`；`frontend-spec.md` `FE-ERR-006` | 2026-11-08 |
| 响应头 `X-Trace-Id` 与响应体一致 | `api-contract.md` `CT-TRACE-002` | `backend-spec.md` `BE-LOG-001`；`frontend-spec.md` `FE-ERR-006` | 2026-11-08 |
| 令牌**协议载体**（Cookie 名/属性/Header 许可） | `api-contract.md` `CT-AUTH-001` | `api-contract.md` `CT-AUTH-002`、`CT-AUTH-003`、`CT-AUTH-004`；`backend-spec.md` `BE-SEC-016`；`frontend-spec.md` `FE-AUTH-001` | 2026-11-08 |
| 令牌**本地存储**（PC/H5 Cookie、移动安全存储） | `frontend-spec.md` `FE-AUTH-001` | `frontend-spec.md` `FE-AUTH-002` | 2026-11-08 |
| 接口字段命名 `lowerCamelCase` | `api-contract.md` `CT-RESP-007` | `frontend-spec.md` `FE-REQ-014`；`backend-spec.md` `BE-CON-001` | 2026-11-08 |
| 数据库字段命名 `snake_case` | `backend-spec.md` `BE-DB-001` | `backend-spec.md` `BE-DB-003` | 2026-11-08 |
| 权限码格式与取值清单 | `docs/design/permission-matrix.md` | `backend-spec.md` `BE-SEC-003`；`frontend-spec.md` `FE-AUTH-006` | 2026-11-08 |
| 软删除表示 `deleted` 与唯一索引含删除列 | `backend-spec.md` `BE-DB-013` | `backend-spec.md` `BE-DB-003` | 2026-11-08 |
| 颜色／字号／间距数值 token | `ui-spec.md` `UI-PC-TOKEN-001` | `ui-spec.md` `UI-PC-TOKEN-002`；`ui-spec.md` `UI-H5-TOKEN-001`；`ui-spec.md` `UI-H5-TOKEN-002` | 2026-11-08 |
| 条目状态与 TODO 定义 | `AGENTS.md` §3 | `startup-change-process.md` `PRC-STG-005`、`PRC-STG-007` | 2026-11-08 |
| 变更分级（必须人工／可自动） | `AGENTS.md` §4 | `startup-change-process.md` `PRC-STG-006`、`PRC-CR-001` | 2026-11-08 |
| 人工审批范围（七类） | `startup-change-process.md` `PRC-APR-001` | `startup-change-process.md` `PRC-STG-002`、`PRC-CR-004` | 2026-11-08 |
| 变更流程与阶段门 | `startup-change-process.md` `PRC-STG-006` | `startup-change-process.md` `PRC-CR-001` | 2026-11-08 |
| 可选模块启用清单 | `startup-change-process.md` `PRC-LAY-004` | `startup-change-process.md` `PRC-LAY-003` | 2026-11-08 |
| 模板 → 目标路径映射 | `AGENTS.md` §11.1 | `startup-change-process.md` `PRC-LAY-006` | 2026-11-08 |
| 项目文档目录结构 | `AGENTS.md` §11 | `startup-change-process.md` `PRC-STG-001` | 2026-11-08 |
| 配置项消费点与生效方式（`consumedBy`／`effect`／`afterChange`） | `backend-spec.md` `BE-CFG-007` | `backend-spec.md` `BE-CFG-008`；`startup-change-process.md` `PRC-DOD-001` | 2026-11-08 |
| 启动期配置不得伪装成可热改 | `backend-spec.md` `BE-CFG-008` | `backend-spec.md` `BE-CFG-007`；`startup-change-process.md` `PRC-DOD-001` | 2026-11-08 |
| 系统基线（as-is）与规范（to-be）的定位 | `AGENTS.md` §1 | — | 2026-11-08 |
| 技术栈版本与选型（语言/框架/数据库/ORM/缓存/测试/包管理器/目录约定） | `AGENTS.md` §9 | `frontend-spec.md` `FE-STACK-001`；`frontend-spec.md` `FE-STACK-004` | 2026-11-11 |

**条目数**：43 条（md 本表与 `facts.yaml` 逐条对应）。

## 3. 本表自检清单

- [ ] 每条事实只有一个定义方（MUST, STATIC）
- [ ] 定义方与引用位置为单个真实编号；例外仅为 `AGENTS.md` 单一节号与 `PRC-LAY-007` 登记的项目层资产路径，无通配符、区间（MUST, STATIC）
- [ ] 每条都有最后核对时间（MUST, STATIC）
- [ ] 本次改动涉及的事实已更新索引（MUST, CONTEXT）
