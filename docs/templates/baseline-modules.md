# 系统基线 · 模块索引（模板 · `EXAMPLE-ONLY`）

| 项目 | 内容 |
|---|---|
| 版本 | v0.0（未填写） |
| 状态 | 空壳模板，复制到 `docs/baseline/modules.md` 后使用 |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is**：系统现在“在哪”，不是规范（to-be）；不写业务细节 |
| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |

> **`EXAMPLE-ONLY`**：模块名与路径均为示例，项目初始化必须按本项目全量替换。
>
> **接口清单不写在这里**：as-is 接口事实源是导出的 `docs/api/openapi.generated.yaml`，契约是 `docs/api/openapi.yaml`。本表只写接口前缀，用于快速定位。

## 1. 使用说明

- 一行一个模块；只写“在哪”，不写“做什么”和“应该怎样”。
- 路径一律用项目内相对路径；某个字段本模块没有时写“无”，不留空。
- 新模块未登记即为缺陷；模块改名、包/目录迁移、主表或接口前缀变化，必须同批更新。
- 接口与权限码的取值分别以 `docs/api/openapi.yaml` 与 `docs/design/permission-matrix.md` 为准，本表只写前缀。
- 回填时机见 `AGENTS.md` §6 第 8 步；未回填不得标记完成。

## 2. 字段说明

| 字段 | 写什么 | 判据 |
|---|---|---|
| 模块 | 业务或系统模块名，与菜单/需求条目一致 | 能与 `docs/requirements/` 或菜单名称对上 |
| 后端包 | 该模块后端代码根包 | 能在该包下找到 Controller/Service |
| 前端目录 | 该模块前端页面目录 | 能在该目录下找到页面组件 |
| 主表 | 该模块的主数据表（多表写主表） | 与 `docs/design/data-model.md` 中的表名一致 |
| 接口前缀 | 该模块接口 URL 前缀 | 能在 `docs/api/openapi.yaml` 或 `openapi.generated.yaml` 的 path 中匹配到 |
| 权限码前缀 | 该模块权限码前缀 | 能在 `docs/design/permission-matrix.md` 中匹配到 |

## 3. 模块索引

| 模块 | 后端包 | 前端目录 | 主表 | 接口前缀 | 权限码前缀 |
|---|---|---|---|---|---|
| 系统管理 · 用户 | `com.example.system` | `apps/pc/src/views/system/user` | `sys_user` | `/api/v1/system/users` | `system:user:` |
| 示例业务 | `com.example.biz` | `apps/pc/src/views/biz/task` | `biz_demo_task` | `/api/v1/biz/tasks` | `biz:task:` |
| <!-- TODO(DATA): 补齐本项目其余模块，一行一个 --> |  |  |  |  |  |

## 4. 完成标准

- 每个模块六个字段齐全，无空白格。
- 主表、接口前缀、权限码前缀能在对应事实源中找到。
- 代码中存在的模块全部登记，无遗漏。

## 5. 自检清单

- [ ] 每行只写位置，未写业务规则（MUST, STATIC）
- [ ] 主表 / 接口前缀 / 权限码前缀与各自事实源一致（MUST, STATIC）
- [ ] 新增或迁移模块已回填（MUST, CONTEXT）
- [ ] 未手写接口清单（as-is 取 `docs/api/openapi.generated.yaml`，契约取 `docs/api/openapi.yaml`）（MUST, STATIC）
