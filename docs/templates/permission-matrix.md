# 权限矩阵（模板 · `EXAMPLE-ONLY`）

| 项目 | 内容 |
|---|---|
| 版本 | v0.0（未填写） |
| 状态 | 空壳模板，复制到 `docs/design/permission-matrix.md` 后使用 |
| 读者 | 项目负责人、后端、AI |
| 依赖 | `docs/specs/startup-change-process.md` `PRC-STG-003`；错误语义按 `api-contract.md` `CT-ERR-011` 与 `CT-HTTP-008` |

> **`EXAMPLE-ONLY`**：本文件的权限码、角色与接口均为示例，项目初始化必须全量替换或清空。

## 1. 使用说明

- 功能权限与数据权限分开写：功能点决定“能不能做”，数据范围决定“能对哪些数据做”。
- **权限码格式（本文为唯一定义方）**：固定 **3 段**，`{领域}:{资源}:{动作}`，全小写，`:` 分隔，只用小写字母与数字；动作枚举固定为 `read`／`write`／`delete`／`manage`（审批类归入 `manage`）。
- 权限码先在本表登记，再写进接口与代码；`backend-spec.md` `BE-SEC-003` 与 `frontend-spec.md` `FE-AUTH-006` 只引用本文，不得自造格式。
- 越权返回基础码（`CT-ERR-011`）：缺功能权限 `403`＋`FORBIDDEN`（`20301`）；访问他人资源或不存在 `404`＋`RESOURCE_NOT_FOUND`（`20401`）；不得返回数据体。
- `<!-- TODO(PERM): ... -->` 未清除不得进入编码。

## 2. 角色 × 功能点 × 数据范围

数据范围取值：`全部` / `本部门` / `本人` / `自定义`（自定义需在备注写规则）。

| 功能点 | 权限码 | 管理员 | 主管 | 普通用户 | 只读审计 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|
| 订单查询 | `biz:order:read` | 全部 | 本部门 | 本人 | 全部 | 审计只读 | APPROVED |
| 订单创建 | `biz:order:write` | 全部 | 本部门 | 本人 | 无 |  | APPROVED |
| 订单作废 | `biz:order:write` | 全部 | 本部门 | 本人 | 无 | 仅未支付，经 `TR-ORDER-003` | APPROVED |
| 角色管理 | `sys:role:manage` | 全部 | 无 | 无 | 无 |  | APPROVED |
| <!-- TODO(PERM): 补齐其余功能点与角色 --> |  |  |  |  |  |  |  |

## 3. 接口 → 所需权限码

| 接口 | 方法 | 权限码 | 数据范围校验 | 备注 | 状态 |
|---|---|---|---|---|---|
| `/api/v1/orders` | GET | `biz:order:read` | 按角色过滤可见范围 | 分页参数见 `CT-PAGE-001` | APPROVED |
| `/api/v1/orders` | POST | `biz:order:write` | 只能为本人或授权范围创建 | 需 `Idempotency-Key` | APPROVED |
| `/api/v1/orders/{id}/void` | POST | `biz:order:write` | 本人或本部门（按角色） | 状态校验，经 `TR-ORDER-003` | APPROVED |

## 4. 越权测试要点

| 类型 | 用例 | 预期 |
|---|---|---|
| 水平越权 | 用户 A 用用户 B 的订单 ID 查询详情 | `404`＋`RESOURCE_NOT_FOUND`（`20401`），不返回数据体 |
| 水平越权 | 主管 A 作废主管 B 部门的订单 | `404`＋`RESOURCE_NOT_FOUND`（`20401`），状态不变 |
| 垂直越权 | 普通用户直接调用角色管理接口 | `403`＋`FORBIDDEN`（`20301`），不泄露角色列表 |
| 垂直越权 | 只读审计账号调用写接口 | `403`＋`FORBIDDEN`（`20301`），无写入副作用 |

## 5. 完成标准

- 每个功能点有唯一权限码，且符合 §1 的 3 段格式；每个接口在映射表中出现。
- 四个内置角色 × 全部功能点均有明确取值，无空白格。
- 每条越权用例有可执行步骤与预期状态码＋业务码（取自 `CT-ERR-011`）。

## 6. 自检清单

- [ ] 功能点无空白格（MUST, STATIC）
- [ ] 权限码全部符合 3 段格式（MUST, STATIC）
- [ ] 每个接口都有权限码（MUST, STATIC）
- [ ] 数据范围规则可执行（MUST, STATIC）
- [ ] 越权用例覆盖水平与垂直各 2 条，且预期码取自 `CT-ERR-011`（MUST, STATIC）
- [ ] `APPROVED` 条目无未解决 TODO（MUST, STATIC）
- [ ] 所有 `EXAMPLE-ONLY` 示例已替换（MUST, STATIC）
