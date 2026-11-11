# 状态机（模板 · `EXAMPLE-ONLY`）

| 项目 | 内容 |
|---|---|
| 版本 | v0.0（未填写） |
| 状态 | 空壳模板，复制到 `docs/design/state-machine.md` 后使用 |
| 读者 | 需求方、后端、前端、测试、AI |
| 依赖 | `docs/specs/startup-change-process.md` `PRC-STG-003`；`AGENTS.md` §3 |

> **`EXAMPLE-ONLY`**：状态、事件、角色与守卫均为示例，项目初始化必须全量替换或清空。
> 本文是**状态与流转的唯一定义方**；`data-model.md` 与 `openapi.yaml` 只引用 `ST-*` 编号，不得另写一套枚举。

## 1. 使用说明

- 每个有状态的实体一节；状态编号 `ST-{实体缩写}-{动作/含义}`，只追加、不复用。
- 每个转移必须写：事件、触发角色、前置守卫、副作用、失败返回。
- 非法流转一律返回 `409`＋`STATE_CONFLICT`（`40901`，`CT-ERR-011`）；不得静默忽略。
- 前端状态文案与颜色映射另表登记，只引用 `ST-*`，不自造状态。

## 2. 状态清单（实体：Order）

| 状态编号 | 中文名 | 是否终态 | 说明 | 状态 |
|---|---|---|---|---|
| `ST-ORDER-PENDING_PAYMENT` | 待支付 | 否 | 已创建未支付 | APPROVED |
| `ST-ORDER-PAID` | 已支付 | 否 | 支付成功 | APPROVED |
| `ST-ORDER-SHIPPED` | 已发货 | 否 | 已出库 | APPROVED |
| `ST-ORDER-VOIDED` | 已作废 | 是 | 未支付被终止 | APPROVED |
| <!-- TODO(DATA): 补齐其余状态 --> |  |  |  |  |

## 3. 允许转移

| 编号 | 从 | 事件 | 到 | 触发角色 | 前置守卫 | 副作用 | 失败返回 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `TR-ORDER-001` | `ST-ORDER-PENDING_PAYMENT` | `pay`（支付成功回调） | `ST-ORDER-PAID` | 系统 | 支付单已成功且金额一致 | 写支付记录、发通知 | `409`／`40901` | APPROVED |
| `TR-ORDER-002` | `ST-ORDER-PAID` | `ship`（发货） | `ST-ORDER-SHIPPED` | 仓储 | 库存已扣减 | 写物流单 | `409`／`40901` | APPROVED |
| `TR-ORDER-003` | `ST-ORDER-PENDING_PAYMENT` | `void`（作废） | `ST-ORDER-VOIDED` | 普通用户（本人）／主管（本部门） | 未支付且无进行中支付 | 写操作记录 | `409`／`40901` | APPROVED |
| <!-- TODO(DATA): 补齐其余转移 --> |  |  |  |  |  |  |  |  |

## 4. 禁止转移

| 从 | 事件 | 结果 | 说明 |
|---|---|---|---|
| `ST-ORDER-PAID` | `void` | `409`／`40901` | 已支付走退款，不走作废 |
| `ST-ORDER-VOIDED` | 任意 | `409`／`40901` | 终态不可再流转 |
| `ST-ORDER-SHIPPED` | `void` | `409`／`40901` | 已履约不可作废 |

## 5. 前端状态映射

| 状态编号 | 文案 | 颜色语义 token | 图标 | 备注 |
|---|---|---|---|---|
| `ST-ORDER-PENDING_PAYMENT` | 待支付 | `--color-warning` | 时钟 | 可发起作废 |
| `ST-ORDER-PAID` | 已支付 | `--color-success` | 对勾 |  |
| `ST-ORDER-SHIPPED` | 已发货 | `--color-info` | 卡车 |  |
| `ST-ORDER-VOIDED` | 已作废 | `--color-neutral` | 禁止 | 终态 |

## 6. 完成标准

- 每个状态有编号、中文名、是否终态、说明与状态。
- 每个允许转移有事件、触发角色、守卫、副作用与失败返回；禁止转移显式列出。
- 非法流转统一返回 `409`＋`STATE_CONFLICT`。
- 字段与接口文档只引用 `ST-*`，无第二套枚举。

## 7. 自检清单

- [ ] 状态编号唯一且不复用（MUST, STATIC）
- [ ] 每个转移的守卫与失败返回可执行（MUST, STATIC）
- [ ] 禁止转移已显式列出（MUST, STATIC）
- [ ] 前端只引用 `ST-*`，无自造状态（MUST, STATIC）
- [ ] `APPROVED` 状态无未解决 TODO（MUST, STATIC）
- [ ] 所有 `EXAMPLE-ONLY` 示例已替换（MUST, STATIC）
