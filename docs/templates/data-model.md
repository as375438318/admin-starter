# 数据模型（模板 · `EXAMPLE-ONLY`）

| 项目 | 内容 |
|---|---|
| 版本 | v0.0（未填写） |
| 状态 | 空壳模板，复制到 `docs/design/data-model.md` 后使用 |
| 读者 | 后端、AI |
| 依赖 | `docs/specs/startup-change-process.md` `PRC-STG-003`；命名与类型按 `backend-spec.md` `BE-DB-001` |

> **`EXAMPLE-ONLY`**：表名、字段、枚举值与索引均为示例，项目初始化必须全量替换或清空。

## 1. 使用说明

- 一个实体一节；字段先登记后建表，禁止“先建表后补字典”。
- **方言固定为 `AGENTS.md` §9-B 选定的数据库**；本模板示例按 MySQL 8 书写（`varchar`／`bigint`／`datetime(3)`／`decimal`／`tinyint`）。若 `AGENTS.md` §9-B 未确认，本文件类型的选定保持“未定”。
- 表名、字段名按 `BE-DB-001`：小写下划线、避开保留字（订单表用 `biz_order`，不用 `order`）。
- 敏感字段必须标**敏感级别**与**访问约束**；`<!-- TODO(DATA): ... -->` 未清除的实体不得进开发。

## 2. 实体清单

| 实体 | 表名 | 说明 | 主要关系 | 是否有状态机 | 状态 |
|---|---|---|---|---|---|
| 订单 | `biz_order` | 一次购买行为的记录 | 1:N 订单明细 | 是 | APPROVED |
| 订单明细 | `biz_order_item` | 订单下的商品行 | N:1 订单 | 否 | DRAFT |
| 用户 | `app_user` | 下单主体 | 1:N 订单 | 否 | APPROVED |

## 3. 字段字典（示例：`biz_order`）

| 字段名 | 类型 | 可空 | 默认值 | 来源 | 枚举值 | 业务含义 | 敏感级别 | 访问约束 | 状态 |
|---|---|---|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | 否 | 自增 | 系统生成 | — | 主键，对外用字符串 | 无 | — | APPROVED |
| `order_no` | `varchar(32)` | 否 | 无 | 服务端生成 | — | 对外唯一编号 | 无 | — | APPROVED |
| `user_id` | `bigint unsigned` | 否 | 无 | 下单时写入 | — | 下单人 | P2（个人信息） | 仅本人与本部门可见 | APPROVED |
| `status` | `varchar(16)` | 否 | `ST-ORDER-PENDING_PAYMENT` | 状态机写入 | 取值只按 `state-machine.md` 的 `ST-ORDER-*` 登记 | 订单状态 | 无 | — | APPROVED |
| `amount` | `decimal(18,2)` | 否 | `0.00` | 计价结果 | — | 应付金额 | P2（交易） | 仅授权角色可见 | APPROVED |
| `remark` | `varchar(200)` | 是 | `NULL` | 用户输入 | — | 备注 | P1 | — | APPROVED |
| `created_at` | `datetime(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 系统生成 | — | 创建时间，UTC 存储 | 无 | — | APPROVED |
| `updated_at` | `datetime(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE` | 系统生成 | — | 最后更新时间 | 无 | — | APPROVED |
| `deleted` | `tinyint(1)` | 否 | `0` | 删除动作 | `0`／`1` | 软删除标记，`1` 表示已删除 | 无 | 查询默认带 `deleted = 0` | APPROVED |

> 敏感级别：`P0` 公开、`P1` 内部、`P2` 个人、`P3` 敏感个人信息；具体分级口径以项目安全基线为准。

## 4. 主外键与关系

| 关系 | 主实体 | 从实体 | 基数 | 约束 | 删除策略 |
|---|---|---|---|---|---|
| 订单—明细 | `biz_order` | `biz_order_item` | 1:N | `biz_order_item.order_id` → `biz_order.id` | 禁删有明细的订单；明细随订单软删 |
| 订单—用户 | `app_user` | `biz_order` | 1:N | `biz_order.user_id` → `app_user.id` | 用户停用不删订单 |

## 5. 索引说明

| 索引 | 字段 | 类型 | 用途 | 备注 |
|---|---|---|---|---|
| `uk_biz_order_order_no_deleted` | `order_no`, `deleted` | 唯一 | 未删除订单编号唯一 | 唯一索引必须含 `deleted`，否则删除后无法重建同号（`BE-DB-013`） |
| `idx_biz_order_user_id_status` | `user_id`, `status` | 普通 | 按人按状态查询 | 覆盖列表主查询 |
| `idx_biz_order_created_at` | `created_at` | 普通 | 时间区间筛选 | 注意时区统一 UTC |

## 6. 生命周期

| 动作 | 方式 | 约束 | 审计 |
|---|---|---|---|
| 创建 | 业务接口创建，`order_no` 服务端生成 | 必填字段齐全 | 记创建人与时间 |
| 变更 | 只允许白名单字段更新 | 状态字段只能走状态机 | 记前后值 |
| 删除 | **默认软删除**（`deleted=1`），查询带 `deleted=0`；物理删除仅限无审计要求且经 ADR 批准 | 有关联数据时禁止 | 记删除人与原因 |

## 7. 与状态机的关系

| 关系 | 说明 |
|---|---|
| 状态字段 | `biz_order.status` 的取值必须与 `state-machine.md` 的 `ST-*` 完全一致 |
| 迁移入口 | 只允许经状态机接口迁移，禁止直写状态字段 |
| 副作用 | 每次迁移的副作用（写记录、发通知）在状态机文档登记 |
| 一致性 | 状态机变更必须先改 `state-machine.md` 与本文，再改代码 |

## 8. 完成标准

- 每个字段有类型、可空、默认值、来源、业务含义、敏感级别、访问约束与状态。
- 每个实体有主键策略、关系、索引、生命周期；软删策略已明确。
- 状态字段与状态机文档逐值对齐；无保留字表名。

## 9. 自检清单

- [ ] 字段九项（类型/可空/默认值/来源/枚举/含义/敏感级别/访问约束/状态）无缺项（MUST, STATIC）
- [ ] 表名与字段名符合 `BE-DB-001`，无保留字（MUST, STATIC）
- [ ] 敏感字段已标级别与访问约束（MUST, STATIC）
- [ ] 唯一索引含 `deleted`（MUST, STATIC）
- [ ] 状态枚举与状态机一致（MUST, STATIC）
- [ ] `APPROVED` 字段无未解决 TODO（MUST, STATIC）
- [ ] 所有 `EXAMPLE-ONLY` 示例已替换（MUST, STATIC）
