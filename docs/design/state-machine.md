# 导出任务状态机

| 项目 | 内容 |
|---|---|
| 版本 | v1.1（2026-11-10，收敛为仅导出异步任务；导入为同步单事务，无任务状态） |
| 状态 | DRAFT |
| 适用范围 | `sys_export_task` |
| 定位 | to-be：状态、允许流转、非法流转与补偿 |

## 1. 状态

| 状态 | 含义 |
|---|---|
| `PENDING` | 已受理，尚未开始执行 |
| `RUNNING` | 执行中（查询 + 写文件） |
| `SUCCESS` | 完成，文件可下载 |
| `FAILED` | 执行失败，无文件 |
| `CANCELED` | 用户取消；已写出的部分文件被删除 |
| `EXPIRED` | 成功文件超过 24 小时保留期被清理 |

## 2. 允许流转

| 起点 | 终点 | 触发 |
|---|---|---|
| `PENDING` | `RUNNING` | 执行器领取任务 |
| `PENDING` | `CANCELED` | 用户在执行前取消 |
| `RUNNING` | `SUCCESS` | 文件写完 |
| `RUNNING` | `FAILED` | 执行异常或启动补偿发现超时 |
| `RUNNING` | `CANCELED` | 用户在检查点取消，执行器删除部分文件 |
| `SUCCESS` | `EXPIRED` | 过期清理删除文件 |

## 3. 非法流转（禁止）

- `SUCCESS` / `FAILED` / `CANCELED` / `EXPIRED` 之间互相流转。
- `EXPIRED` → 任何状态。
- `PENDING` → `SUCCESS` / `FAILED`（未执行不得有终态）。
- 任何状态回退到 `PENDING`。

## 4. 并发与补偿

- 领取任务用条件更新（`UPDATE ... SET status='RUNNING' WHERE id=? AND status='PENDING'`），影响行数为 0 视为已被领取。
- 取消用 `cancel_requested=1` 标记，执行器每 2000 行读取一次；不直接改 `status`，避免与执行器竞争。
- 启动补偿：应用启动时把 `RUNNING` 且 `started_at` 超过 2 小时的任务置 `FAILED`，删除其临时文件。
- 下载只在 `SUCCESS` 且 `expires_at` 未过时允许；其余状态返回 404 或 409。

## 5. 自检

- [x] 每个状态有进入条件与退出路径（MUST, STATIC）
- [x] 非法流转已显式列出（MUST, STATIC）
- [x] 并发领取与取消有原子方式（MUST, STATIC）
- [x] 与 `docs/design/import-export-framework.md` §4.7 一致（MUST, STATIC）
