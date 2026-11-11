# AGENTS.md · AI 开发执行入口

> 版本：v1.6（2026-11-08）
> 本文件只做索引、路由与门禁，**不复制规范正文**。规则见 `docs/specs/` 文档，流程细节见 `docs/specs/startup-change-process.md`。
> 路径一律使用项目内相对路径，不写本机绝对路径。

## 1. 文档路由

| 文档 | 项目内路径 | 管什么 | 不管什么 |
|---|---|---|---|
| UI 规范 | `docs/specs/ui-spec.md` | 视觉变量、布局、组件外观、交互反馈、无障碍 | 接口字段、错误码、代码结构 |
| 接口契约 | `docs/specs/api-contract.md` | 响应结构、HTTP 状态、业务错误码、分页/时间/金额、幂等与版本、令牌协议载体 | 前后端实现、组件与视觉 |
| 前端规范 | `docs/specs/frontend-spec.md` | 工程结构、请求消费、鉴权本地存储、组件、样式消费、跨端实现 | 契约定义、视觉数值、后端实现 |
| 后端规范 | `docs/specs/backend-spec.md` | 分层依赖、契约实现、异常事务、校验幂等、并发、数据库、缓存、日志、安全、配置、测试 | 契约定义、UI 设计、前端实现 |
| 启动与变更流程 | `docs/specs/startup-change-process.md` | 三层结构、阶段流程、异议义务、反向索引、健康检查、变更请求单、审批清单 | 具体编码规则、视觉数值 |
| 事实索引 | `docs/specs/INDEX-facts.md` | 事实 → 定义方 → 引用位置；影响面查询 | 规则正文、流程说明 |
| 配置台账（项目层） | `docs/design/config-registry.yaml` | 配置键、消费点、生效方式与验证方式 | 规则正文、业务实现 |
| 系统基线（项目层） | `docs/baseline/modules.md`、`docs/baseline/assertions.md` | 系统现状（as-is）：模块在哪、既成事实与陷阱 | 应有规范、业务细节、接口清单（as-is 取 `docs/api/openapi.generated.yaml`，契约取 `docs/api/openapi.yaml`） |

**项目层模板**：`docs/templates/`，复制到目标路径后填写；映射见 §11.1。

**基线 vs 规范（MUST）**：`docs/baseline/` 记录“系统现在是什么样（as-is）”，`docs/specs/` 规定“应该怎么写（to-be）”。基线不得被当作规范引用，规范不得被当作现状描述；两者不一致时按 §3 状态一致性核对处理，不得默认任一方。

**冲突优先级（唯一定义处，MUST）**：`api-contract.md` > `backend-spec.md` / `frontend-spec.md` > `ui-spec.md` > 项目约定。**例外：视觉问题以 `ui-spec.md` 为准。** 其他文档只写“见 `AGENTS.md` §1”，不得复述本条。发现同一事实双定义时**停机上报，不得自行取舍**。

**规则级别（MUST）**：只有 `MUST` / `SHOULD` / `MAY` 三级。否定一律写 `MUST` + “禁止……”，不使用 `MUST NOT`。

**技术栈（唯一定义处）**：`AGENTS.md` §9-B。`backend-spec.md`、`frontend-spec.md`、`README.md` 只引用，不写版本号。

## 2. 任务路由表

> 只读表中章节，不全文加载；任务跨域时按需追加相邻章节。文档改章节后必须同步本表。

| 任务类型 | 必读章节 |
|---|---|
| 项目初始化/接入规范 | `startup-change-process.md` §1、§2.1、§4；本文件 §9、§11 |
| 需求条目化与验收 | `startup-change-process.md` §2.2、§2.8、§5；`docs/templates/requirement-spec.md`、`glossary.md` |
| 设计（模型/权限/状态机/接口/ADR） | `startup-change-process.md` §2.3、§2.8；`docs/templates/data-model.md`、`permission-matrix.md`、`state-machine.md`、`openapi.template.yaml`、`errors.yaml`、`adr-000-template.md` |
| 新增/修改后端接口 | `api-contract.md` §1–§9、§15；`backend-spec.md` §0、§2–§5、§9、§10、§12、§15、§16 |
| 修改前端页面/组件 | `ui-spec.md` PC-1–PC-16 或 H5-1–H5-16、UI-FLOW、UI-HUMAN；`frontend-spec.md` §6、§8、§10、§11、§12、§17 |
| 修改数据结构/数据库 | `backend-spec.md` §2、§7、§12、§17；`api-contract.md` §4–§5、§8、§9；`startup-change-process.md` §3 |
| 修改交互/视觉样式 | `ui-spec.md` 对应 PC/H5 章节、UI-FLOW、UI-HUMAN；`frontend-spec.md` §11、§17 |
| 新增/修改配置项（参数、字典、菜单、开关） | `backend-spec.md` §11（`BE-CFG-007`、`BE-CFG-008`）；`startup-change-process.md` §2.9（`PRC-DOD-001`）；`docs/design/config-registry.yaml` |
| 新建/更新系统基线 | 本文件 §6 第 0 步与第 8 步；`docs/templates/baseline-modules.md`、`baseline-assertions.md` |
| 纯 bug 修复 | `backend-spec.md` §3、§4、§7 或 `frontend-spec.md` §6、§8；涉及响应/状态码加 `api-contract.md` §1–§3 |
| 重构 | `backend-spec.md` §1、§3、§12、§17；`frontend-spec.md` §4、§9、§10、§12、§17；`api-contract.md` §8–§9 |
| 新增依赖 | `frontend-spec.md` §2、§5、§13；`backend-spec.md` §1、§13；选型理由记入 `docs/decisions/` |
| 新增/修改错误码 | `api-contract.md` §3、§9、§11、§12；`backend-spec.md` §2–§3；`frontend-spec.md` §6、§8；`docs/templates/errors.yaml` |
| 认证/权限 | `api-contract.md` §2–§3、§15；`frontend-spec.md` §7、§18；`backend-spec.md` §9；`docs/design/permission-matrix.md` |
| 发布/CI | `frontend-spec.md` §13、§15；`backend-spec.md` §12–§13、§15 |
| 变更影响面评估 | `startup-change-process.md` §3、§5、§2.8；`INDEX-facts.md` |
| 任何改动（先定档再动手） | 本文件 §4（档位判定、文档产出对照表、显式豁免清单） |
| 文档健康检查 | `startup-change-process.md` §4 |
| 交付前自检 | `backend-spec.md` §15、`frontend-spec.md` §17、`api-contract.md` §12、`ui-spec.md` UI-FLOW；再按所改章节末尾自检清单逐条比对；最后按本文件 §7 核对状态与实现 |
| 修改规范文档 | 本文件 §4、§5、§6；`startup-change-process.md` §1、§3、§4；各文档索引章节 |

## 3. 条目状态与 TODO（MUST）

| 状态 | 含义 | 流转条件 |
|---|---|---|
| `DRAFT` | 草案，可修改；**允许含未解决 TODO** | → `APPROVED` 需人工签核（清单见 `PRC-APR-001`） |
| `APPROVED` | 已签核，允许开发；**不得含未解决 TODO** | → `IMPLEMENTED` 需交付记录（提交号或文件路径 + 自检报告） |
| `IMPLEMENTED` | 已交付并有记录 | → `DEPRECATED` 需变更单并写明替代项 |
| `DEPRECATED` | 停用 | 不得被代码或文档引用；复用须开新编号 |

- **`APPROVED` 条目不得含未解决 TODO**；含 TODO 的条目只能停留在 `DRAFT`。违反即状态非法。
- 只允许上表相邻正向流转；回退必须开变更单并记录原因。
- **状态与代码不一致时必须显式核对“哪个是真的”**，不得默认任何一方；结论写入交付报告。
- 只文档与代码不一致、无法判定时，按“文档为准、代码为缺陷”登记，除非 `INDEX-facts.md` 另有定义。

**TODO 标记**：`<!-- TODO(类型): 待确认内容 -->`；类型只允许 `REQ`／`DATA`／`PERM`／`API`／`UI`／`OPS`。合法载体：Markdown 注释（`<!-- ... -->`）与 YAML 注释（`# TODO(类型): ...`）；其他格式不计为有效 TODO。

- **遇到未解决的 TODO 必须停止并提问，禁止自行填充或按“常见做法”推测。**
- 模板中的 TODO 未清除时不得进入编码；交付仍有 TODO 时必须在报告中单列（编号、类型、卡点、需谁答复）。

## 4. 变更分级与影响面

- **必须人工审批清单**：唯一定义为 `startup-change-process.md` `PRC-APR-001`，本文件不复述。
- **可自动处理**：措辞、示例、格式、注释、编号调整、文档内链接；可直接改，但必须记入变更记录。
- **影响面必须走反向索引**：禁止凭记忆列举；必须查 `docs/specs/INDEX-facts.md` 逐条定位引用点。索引未覆盖时写“索引未覆盖该事实”，并建议补入。
- **通用层上升**：同一问题**两个以上项目**都遇到，才允许从项目层上升到通用规范；单项目特例只留项目层。越级上升必须在变更记录写明缘由与反对意见。

### 4.1 变更档位（A／B／C，唯一定义处）

档位只由**结构影响**判定，用于决定文档产出量；审批与 CR 另按 `PRC-APR-001`、`PRC-CR-001` 判定，档位不得豁免也不得扩大它们。

| 档位 | 判据（六条全部满足才算该档，逐条可判定） | 典型落点 |
|---|---|---|
| **A · 微改** | ① 只动 1 个模块；② 不新增/删除/改名接口路径或请求字段；③ 不动表结构与数据迁移；④ 不新增权限码；⑤ 不新增错误码；⑥ 未命中 `PRC-APR-001` | 改文案、调样式、加可选展示字段、修不涉契约的 bug |
| **B · 模块级** | ① 只涉及 1 个模块（含其前端页面与后端包）；② 只做**纯新增且向后兼容**：接口、表/列、权限码、错误码、配置项；③ 未命中 `PRC-APR-001` | 新增一个 CRUD 模块、新增一张表、新增一组权限码 |
| **C · 结构性** | 命中 `PRC-APR-001` 任一类别（逐类比对；本节不复述其类别与数量） | 契约版本演进、删列/改类型、权限放宽、安全降级、状态机变更、模块启停 |

- **跨模块联动本身不升档**：是否落 C 只看是否命中 `PRC-APR-001`；未命中的跨模块纯新增落 B，禁止自行升为 C 另开审批。
- **档位不豁免 CR**：任何档位只要命中 `PRC-CR-001` 的范围，先开 CR 再动手；A 档的 CR 同样按 `PRC-CR-002` 六要素齐全，只是不写需求条目与计划。
- C 档在取到 `PRC-APR-001` 签核前**禁止**改代码或改文档（`PRC-CR-004`）。

### 4.2 文档产出对照表（按档位）

> 「新建」＝该档位要求新增的文件或小节，文件不存在时**先建再动代码**；「更新」＝已有文件必须回填；「不产出」＝明确不要写。
> 模板与落位路径见本文件 §11.1。项目层目录未初始化时 `SL-PATH-001` 只报 warning，但禁止据此跳过产出。

| 档位 | 新建 | 更新 | 不产出 |
|---|---|---|---|
| **A** | 无 | **MUST**：`docs/audit/impl-notes.md`；涉接口 → `docs/api/openapi.yaml` ＋重新导出 `docs/api/openapi.generated.yaml`；涉配置项 → `docs/design/config-registry.yaml`。**按需**：`docs/baseline/assertions.md`（出现“改错就出事”的新事实） | 需求条目、计划、ADR |
| **B** | **MUST（缺失时先建）**：`docs/requirements/requirement-spec.md` 的能力条目、`docs/plan/` 计划、`docs/design/data-model.md`（涉表）、`docs/design/permission-matrix.md`（涉权限码）、`docs/api/openapi.yaml` 的接口定义 | **MUST**：上列文件对应条目；重新导出 `docs/api/openapi.generated.yaml`；`docs/baseline/modules.md`（模块/主表/接口前缀/权限码前缀）；`docs/audit/impl-notes.md`。**按需**：`docs/baseline/assertions.md`、`docs/design/config-registry.yaml`（新配置项） | ADR、契约版本号升级 |
| **C** | B 档全部 ＋ **MUST**：`docs/decisions/ADR-*.md`（决策与备选方案）、影响面分析成文（`docs/audit/` 下变更记录，六要素按 `PRC-CR-002`） | B 档全部 ＋ **MUST**：`docs/api/openapi.yaml` 版本号与变更记录、`docs/design/state-machine.md`（涉状态）、`docs/baseline/assertions.md`（结构性结论） | — |

### 4.3 只读不改与文档缺失

- **只读不改**（业务改动一律不改，需另开通用层流程并记变更记录）：`AGENTS.md`、`docs/specs/` 五份规范（`ui-spec.md`、`api-contract.md`、`frontend-spec.md`、`backend-spec.md`、`startup-change-process.md`）、`docs/env/local-dev.md`。
- **文档缺失**：本档位要求的文件不存在时，先按本文件 §11.1 从 `docs/templates/` 复制并填写，再动代码；禁止以“没有该文件”为由跳过分析。
- **豁免例外**：§4.4 列出的文档不建，不适用“缺失即先建”。

### 4.4 本项目显式豁免清单

| 文档 | 不建立的理由 | 替代物 / 何时补建 |
|---|---|---|
| `docs/requirements/requirement-spec.md` 的逐条需求（`REQ`） | 通用模块阶段没有真实需求方与验收人，凭空写 `REQ` 会产生无主条目 | 能力清单 `docs/requirements/system-modules.md`（模块＋能力＋权限码）；出现真实需求方与验收标准时补建 |
| `docs/design/state-machine.md` | 当前模块只有启用/停用两个值，无跨状态流转约束，该事实不存在 | `docs/design/data-model.md` 的状态字段枚举说明；出现 ≥2 状态且存在非法流转约束时补建 |
| `docs/env/env-inventory.md` | 只有本机联调一种环境，多环境差异尚不存在 | `docs/env/local-dev.md`（启动、依赖、验证命令）；出现测试/预发/生产差异需登记者补建 |
| `docs/api/openapi.yaml` 全接口手写补齐 | as-is 接口由 `docs/api/openapi.generated.yaml` 自动导出，手抄全部必然漂移 | 手写契约只写被前端消费、需评审或对外交付的接口并标 `x-status`；对外交付或第三方联调时按需补齐 |

## 5. 异议义务（不得盲从）

完整规则见 `startup-change-process.md` `PRC-OBJ-001`–`PRC-OBJ-005`；要点见下。

**必须提异议（六种）**：与 `docs/specs` 的 `MUST` 冲突；与 `INDEX-facts.md` 已登记事实不一致；破坏性变更（数据不可逆、契约破坏兼容、权限放宽、安全降级、不可回滚部署）；与已接受 ADR 冲突；用户方案与其先前决定矛盾；指令依赖未确认假设（此时形式是提问，不是否定）。

**不反驳**：纯偏好（命名、措辞、布局、颜色，且不违反 `MUST`）；已由 ADR 拍板的方向且无新事实；用户已声明“知道风险、就这么做”并留了理由——改为降低风险；事实不足时先提问。

**固定四段，缺一不成立**：`依据`（规则编号／索引事实／ADR 编号）→ `风险`（具体后果与触发条件）→ `替代方案`（至少一个可执行方案）→ `若坚持执行的后果承接`（补偿措施）。无依据异议、把 `SHOULD` 偏好包装成 `MUST` 冲突，均判为不通过。

**异议之后**：用户坚持且有理由 → 入 ADR／变更记录（含“已否决的建议”）；无理由 → 标 `RISK-ACCEPTED` + 风险描述并复核；禁止静默改规则文档。同一问题最多提两次。

**与 `*-HUMAN-*` 的区别**：HUMAN 是“AI 判不了、找人工确认”；异议是“AI 认为用户错了、要说明依据”，互不替代。

## 6. 文档先行与会话启动

**文档先行（MUST）**：通用性变更先改 `docs/specs/`，再改代码，同一次提交或相邻提交；禁止“先写代码、文档以后补”。

**会话流程（0–8 步，MUST）**

| 步 | 动作 | 硬性要求 |
|---|---|---|
| 0 | 读基线 | 见 §6.1；未完成不得输出方案或改代码 |
| 1 | 读本文件 | 只读路由与门禁章节，不全文加载 |
| 2 | 判定任务类型，按 §2 定位章节号 | 跨域任务追加相邻章节 |
| 3 | 查 `INDEX-facts.md` 定位事实与引用点；查 `docs/design/config-registry.yaml` 确认涉及配置项的 `consumedBy`／`effect`；查相关 TODO 与条目状态 | 影响面来自索引，不凭记忆 |
| 4 | 只读相关章节 | 禁止全文加载 |
| 5 | 编码或改文档 | 遇未解决 TODO 停止提问；遇规则缺口停机提问；发现用户指令与 §5 冲突先提异议 |
| 6 | 按各章自检清单逐条比对 | 输出自检报告后才允许声称完成 |
| 7 | 按 §7 交付门禁自检 | 不通过项必修 |
| 8 | 回填基线 | 见 §6.2；未回填不得标记完成 |

### 6.1 第 0 步 · 读基线（MUST）

任何需求、方案或改动开始前，必须按序读完下列文件，再输出影响面分析：

1. `docs/baseline/modules.md`：模块在哪（后端包、前端目录、主表、接口前缀、权限码前缀）。
2. `docs/baseline/assertions.md`：既成事实与陷阱（改错就出事的地方）。
3. `docs/api/openapi.generated.yaml`（as-is 实现基线，由 `scripts/export-openapi.ps1` 导出）与 `docs/api/openapi.yaml`（契约，to-be）：接口清单不手写；生成产物尚未导出时，记录“无 as-is 接口基线”，改读手写契约。
4. `docs/design/permission-matrix.md`：角色与权限码。
5. `docs/design/data-model.md`：表与字段。
6. 涉及配置类改动时，加读 `docs/design/config-registry.yaml`。

**基线不存在时的降级（MUST，二选一）**：

- (a) 先按 `docs/templates/baseline-modules.md`、`docs/templates/baseline-assertions.md` 建立基线，再动手；
- (b) 在开场自证中明确写“无基线，本次靠读代码”，并在第 8 步补齐。

禁止以“无基线”为由跳过影响面分析。

**开场自证（MUST，可检查）**：任何改动前，AI 必须在第一条回复里给出下列三行；缺任一项视为流程未启动，不得继续输出方案或改代码：

```text
开场自证
- 已读：docs/baseline/modules.md（核对 2026-xx-xx）；docs/baseline/assertions.md（核对 2026-xx-xx）；docs/api/openapi.yaml（版本 x.y）；docs/design/permission-matrix.md；docs/design/data-model.md
- 基线版本：vX / 无基线（本次靠读代码，第 8 步补齐）
- 初步影响面：模块 M；接口 GET /api/v1/...；表 T；权限码 p:r:a；配置项 key（consumedBy / effect / afterChange）
```

检查口径：三行齐全，且“已读”“初步影响面”两行含具体路径或编号；只写“已阅读相关文档”这类没有路径、没有编号的表述，视为未完成。

### 6.2 第 8 步 · 回填基线（MUST）

改动完成后必须同批回填，否则不得标记完成、不得提交：

| 改了什么 | 回填到哪 |
|---|---|
| 新增／改名／迁移模块，或后端包、前端目录、主表、接口前缀、权限码前缀变化 | `docs/baseline/modules.md` |
| 发现或改变了“看代码半天才发现、改错就出事”的既成事实 | `docs/baseline/assertions.md` |
| 新增／修改接口 | 重新导出 `docs/api/openapi.generated.yaml`，并同步手写契约 `docs/api/openapi.yaml` |
| 新增／修改配置项（参数、字典、菜单、开关） | `docs/design/config-registry.yaml` |

回填只写现状（as-is），不写“应该怎样”；后者属 `docs/specs/`。

## 7. 交付门禁

| 规则编号 | 结论 | 文件:行号 | 说明 |
|---|---|---|---|
| `BE-ARCH-001` | 通过 / 不通过 / 需人工确认 | `src/...:12` | 一句话说明 |

**分工（MUST）**：文档负责告知，脚本负责拦截。文档写清“应该怎样”与失败信号；可判定的部分由工具阻断，不靠提醒。

**error 级阻断（出现即不通过，不得带病提交）**

| 检查 | 阻断条件 | 执行者 |
|---|---|---|
| `spec-lint` error | 任一 ruleId 报 error：`SL-REF-001`、`SL-ID-001`、`SL-ID-002`、`SL-TODO-001`、`SL-TODO-002`、`SL-PATH-001`（路径不存在分支）、`SL-EXAMPLE-001`（项目层残留）、`SL-TPL-001`（模板与目标都缺失）、`SL-FACT-001`、`SL-CFG-001` | `node tools/spec-lint/cli.mjs` |
| 契约测试失败 | 实际响应与 `docs/api/openapi.yaml` 不一致 | 后端契约测试（`docs/api/examples/backend`） |
| 基线未登记新增接口 | 新增／修改接口后未重新导出 `docs/api/openapi.generated.yaml`（或未同步 `docs/api/openapi.yaml`），或新模块未登记 `docs/baseline/modules.md` | §6.2 第 8 步 + 评审 |
| 配置项未登记或无消费点 | 参数管理中存在未登记台账的键，或台账 `consumedBy` 指不到读取点 | `SL-CFG-001` + `BE-CFG-007` |
| 开场自证缺失 | 第一条回复没有“开场自证”三行 | §6.1 第 0 步 |
| 基线未回填 | §6.2 要求的文件未同批更新 | §6.2 第 8 步 |

`SL-REFMT-001`、`SL-PATH-001` 的项目层缺失分支等 warning 不阻断，但必须在交付报告中单列。

- **引用只写规则编号**，不写章节号、行号或文档页码；位置由索引与脚本解析，手写易漂移与编造。
- 只列**不通过**与**需人工确认**项；通过项汇总成一句数量。
- 不通过项必修；确需放弃时写明理由并由人确认；禁止静默略过。
- 需人工确认项必须显式列出：业务语义命名、事务边界、权限模型、交互是否符合业务习惯。
- **状态一致性核对（MUST）**：核对涉及条目状态与实现是否一致；不一致项单列。`APPROVED` 含 TODO 属不一致。
- **未解决 TODO 单列（MUST）**。
- **异议记录（MUST）**：提出条数、采纳与否决、否决记录位置。
- **规则缺口（MUST）**：遇文档未覆盖的问题，标 `RULE-GAP`，停机提问；禁止以“最佳实践”自行补规则。
- **禁止作弊**：不得删减功能、吞异常、放宽校验、跳过必填、注释测试、把 `MUST` 降为 `SHOULD`。

## 8. 防幻觉条款

- 引用规则只写编号；不确定时写“未在文档中查到，需确认”，禁止编造编号或条款。
- 不得凭记忆复述规则或列举影响面。
- 文档未覆盖的问题：**停机提问并标 `RULE-GAP`**，不得按“通用最佳实践”自行实现。
- 发现规则冲突：按 §1 优先级处理；同一事实双定义时停机上报，不自行取舍。

## 9. 项目约束与技术选型（分阶段填写）

**A. 项目约束（阶段 0 填写）**：没有 A 不得进入方案讨论。

| 项 | 填写 |
|---|---|
| 项目名称 | admin-starter 后台管理底座 |
| 目标平台（Web／H5／小程序／App／桌面） | PC Web（当前唯一可运行）；H5、微信小程序、App（iOS/Android）已列入规划，**跨端框架未选型** |
| 团队技能栈 | Java + Vue 全栈（自有团队） |
| 部署环境与预算限制 | 部署环境：单机内网 Windows（现状，仅本机联调）；预算限制：未确认（不得引入需额外采购的中间件） |
| 合规要求（数据能否出境、能否用境外 SaaS） | 需过等保二级／三级；具体条款待拆解为可验收条目，未拆解前不得以"已合规"表述交付 |
| 交付时间与里程碑 | 长期迭代，无固定节点；按变更档位（§4.1）分批推进 |

**B. 技术选型结论（阶段 2 填写，来源于 ADR）**：没有 B 不得进入编码。

| 项 | 结论 | 依据 ADR |
|---|---|---|
| 语言与运行时版本 | Java 17（LTS）；Node.js ≥ 22 | `ADR-005` |
| 后端框架 | Spring Boot 3.2.12（含 Spring Security 6、Spring Data Redis 3.2.12） | `ADR-005` |
| 前端框架/跨端 | PC：Vue 3.5 + Vite 5 + TypeScript 5.6 + Element Plus 2.8 + Pinia 2.2；跨端（H5／小程序／App）：**未定** | `ADR-005`、`ADR-003` |
| 数据库 | MySQL 8.0（迁移工具 Flyway 9.22.3） | `ADR-005` |
| ORM/数据访问 | MyBatis-Plus 3.5.7（MyBatis 3.5.16）+ MapStruct 1.5.5 | `ADR-005` |
| 缓存/消息 | Redis 5.0（Spring Data Redis）；消息队列：不引入 | `ADR-005` |
| 测试框架 | 后端：JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test`）；后端集成测试走 profile `test`，连**独立库 `*_test`**（测试类含"库名必须含 test"的安全断言），CI 用 MySQL/Redis 服务容器注入连接；契约验证：swagger-request-validator 比对 `docs/api/openapi.yaml` 本体（不复制副本）；前端：Vitest 3 + `@vue/test-utils`（单元/组件）+ Playwright（E2E）；小程序自动化：**未定**（待跨端框架选型） | `ADR-005`、`ADR-006` |
| 包管理器 | pnpm 11.7.0（workspace，锁文件唯一）+ Maven 3.6.3 | `ADR-005`、`ADR-003` |
| 目录约定 | `server/{common,system,auth,web}`；`apps/{pc,mobile,shared}`；`packages/ui-tokens` | `ADR-003`、`ADR-005` |

- **本节是技术栈唯一定义方**；其他文档引用本节，不得写死版本号。
- **项目初始化时由用户确认后填写；未经确认保持“未定”，不得默认填入常见版本。**
- **未选型项**（跨端框架、小程序自动化）保持“未定”：跨端框架选型完成前，禁止在 `apps/mobile` 落业务代码（选型需另开 ADR，评估包体积、小程序能力边界与 Element Plus 兼容性）；小程序自动化跟随跨端框架，不得先于业务需求引入。
- 本节 2026-11-11 由用户逐项确认后填写，过程见 `docs/audit/CR-20261111-001-agents-tech-stack-baseline.md`（§9-A/§9-B 首版）与 `docs/audit/CR-20261111-002-frontend-test-framework.md`（测试框架行），选型理由与备选方案见 `ADR-005`、`ADR-006`。

## 10. 项目约定（仅本项目）

- 一次性约定、环境差异、临时豁免写这里或 `docs/decisions/`，注明到期时间与责任人。
- 不得覆盖 `docs/specs/` 的 `MUST` 项；确需例外要记录理由并由人确认。

## 11. 安装与验证（附录）

1. 本目录已是可用形态：`AGENTS.md`、`README.md`、`docs/specs/`、`docs/templates/`。
2. 开新项目：整目录复制到项目根，保持相对路径；不要只复制部分文件。
3. 以下项目层目录已随包提供（仅含 `.gitkeep`），无需新建：`docs/api/`、`docs/requirements/`、`docs/design/`、`docs/decisions/`、`docs/plan/`、`docs/audit/`、`docs/env/`、`docs/baseline/`；`scripts/init-project.ps1` 会按 §11.1 把模板复制到位。
4. 从 `docs/templates/` 按 §11.1 复制并改名；清空或替换所有 `EXAMPLE-ONLY` 示例；填 §9-A。
5. 验证：`docs/specs/` 七个文件可读（五份规范 + `INDEX-facts.md` + `facts.yaml`）；`docs/templates/` 十三个模板齐全；包内无本机绝对路径；项目层文件的 `EXAMPLE-ONLY` 已全部替换（`docs/templates/` 保留空壳示例）。

### 11.1 模板 → 目标路径

| 模板 | 目标路径 |
|---|---|
| `docs/templates/requirement-spec.md` | `docs/requirements/requirement-spec.md` |
| `docs/templates/glossary.md` | `docs/requirements/glossary.md` |
| `docs/templates/data-model.md` | `docs/design/data-model.md` |
| `docs/templates/permission-matrix.md` | `docs/design/permission-matrix.md` |
| `docs/templates/state-machine.md` | `docs/design/state-machine.md` |
| `docs/templates/modules.md` | `docs/design/modules.md` |
| `docs/templates/env-inventory.md` | `docs/env/env-inventory.md` |
| `docs/templates/openapi.template.yaml` | `docs/api/openapi.yaml` |
| `docs/templates/errors.yaml` | `docs/api/errors.yaml` |
| `docs/templates/config-registry.yaml` | `docs/design/config-registry.yaml` |
| `docs/templates/baseline-modules.md` | `docs/baseline/modules.md` |
| `docs/templates/baseline-assertions.md` | `docs/baseline/assertions.md` |
| `docs/templates/adr-000-template.md` | `docs/decisions/ADR-*.md` |

> ADR 目标为 `docs/decisions/ADR-*.md`（任意编号文件名）；`SL-TPL-001` 按通配匹配，至少存在一个即视为已落位。

## 12. 多工具入口兼容

不同 AI 工具认的入口文件名不同，但规则只有一份。

| 工具 | 入口文件名 |
|---|---|
| TraeCode / 通用约定 | `AGENTS.md` |
| Claude Code | `CLAUDE.md` |
| Cursor | `.cursorrules` 或 `.cursor/rules/*.mdc` |
| Gemini CLI | `GEMINI.md` |
| GitHub Copilot | `.github/copilot-instructions.md` |

**单一来源（MUST）**：唯一事实源是 `AGENTS.md`；其他入口只能是它的**副本或软链**，禁止各自演化。

- **副本**：首行加 `<!-- 本文件由 AGENTS.md 同步生成，禁止直接编辑；改动请改 AGENTS.md 后重新同步 -->`，其余内容与 `AGENTS.md` 逐字一致。
- **软链**：Windows 用 `mklink`（需管理员或开发者模式），Linux/macOS 用 `ln -s`；软链不产生第二份内容，优先采用。
- **同步后校验**：`Get-FileHash AGENTS.md, CLAUDE.md`（或 `sha256sum`）哈希一致；不一致即视为出现两份事实源，按本文件 §1 停机上报。
- **改动路径**：只在 `AGENTS.md` 落地，再重新生成副本；直接编辑副本的提交判为不通过。
