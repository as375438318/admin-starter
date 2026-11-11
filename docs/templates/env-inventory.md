# 环境清单（模板 · `EXAMPLE-ONLY`）

| 项目 | 内容 |
|---|---|
| 版本 | v0.0（未填写） |
| 状态 | 空壳模板，复制到 `docs/env/env-inventory.md` 后使用 |
| 读者 | 运维、后端、前端、AI |
| 依赖 | `AGENTS.md` §9-A；`docs/specs/startup-change-process.md` `PRC-STG-001` |

> **`EXAMPLE-ONLY`**：地址、账号归属与差异项均为示例，项目初始化必须全量替换；**不得写入真实密码、密钥或令牌**。

## 1. 使用说明

- 每个环境一行；没有的环境写“未使用”，不删行。
- 账号只写“归属角色”，不写真实账号与口令；口令存放位置单独登记。
- 环境差异必须写清，避免“本地能跑、测试不能跑”。
- 新增环境或改地址须同步 CI、部署脚本与前端 `VITE_*` 配置。

## 2. 环境矩阵

| 环境 | 用途 | 前端地址 | 后端地址 | 数据库 | 缓存 | 对象存储 | 账号归属 | 状态 |
|---|---|---|---|---|---|---|---|---|
| local | 本机开发 | `http://localhost:5173` | `http://localhost:8080` | 本机实例 | 本机实例 | 不使用 | 开发者本人 | APPROVED |
| dev | 联调 | `https://dev-web.example.com` | `https://dev-api.example.com` | 独立实例 | 独立实例 | 可选 | 后端负责人 | APPROVED |
| test | 测试 | `https://test-web.example.com` | `https://test-api.example.com` | 独立实例 | 独立实例 | 可选 | 测试负责人 | APPROVED |
| staging | 预发 | `https://stg-web.example.com` | `https://stg-api.example.com` | 独立实例 | 独立实例 | 可选 | 运维 | DRAFT |
| prod | 生产 | `https://www.example.com` | `https://api.example.com` | 生产实例 | 生产实例 | 按需 | 运维 | DRAFT |
| <!-- TODO(OPS): 补齐本项目其余环境；无此环境写“未使用”，不删行 --> |  |  |  |  |  |  |  | DRAFT |

## 3. 环境差异

| 项 | local | dev | test | staging | prod |
|---|---|---|---|---|---|
| 鉴权方式 | 本地账号 | 统一登录 | 统一登录 | 统一登录 | 统一登录 |
| 外部依赖 | 全部 Mock | 部分 Mock | 真实 | 真实 | 真实 |
| 数据 | 造数脚本 | 造数脚本 | 脱敏数据 | 脱敏数据 | 真实 |
| 日志级别 | DEBUG | DEBUG | INFO | INFO | WARN |

## 4. 凭据与密钥存放

| 名称 | 用途 | 存放位置 | 轮换责任 | 备注 |
|---|---|---|---|---|
| 数据库口令 | 连接 | 环境变量／密钥管理 | 运维 | 不入库、不入文档 |
| 第三方密钥 | 调用外部服务 | 密钥管理 | 后端负责人 | 按环境隔离 |

## 5. 完成标准

- 每个环境有地址、账号归属与状态；无空值。
- 凭据只登记存放位置与责任人，文档内无真实口令。
- 环境差异表覆盖鉴权、外部依赖、数据与日志四项。

## 6. 自检清单

- [ ] 环境矩阵无空白格（MUST, STATIC）
- [ ] 文档内无真实口令/密钥/令牌（MUST, STATIC）
- [ ] 环境差异四项齐全（MUST, STATIC）
- [ ] `APPROVED` 环境无未解决 TODO（MUST, STATIC）
- [ ] 所有 `EXAMPLE-ONLY` 示例已替换（MUST, STATIC）
