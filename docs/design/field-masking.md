# 字段级脱敏设计（to-be）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（定稿，2026-11-08） |
| 状态 | `APPROVED`（6 项裁决已写入 §10、§11，无未决事项） |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | **to-be**：字段级脱敏应如何设计；系统现状见 `docs/baseline/modules.md`、`docs/baseline/assertions.md` |
| 依赖 | `AGENTS.md` §6（第 0 步读基线、第 8 步回填基线）、§4.1 变更档位 |
| 本轮边界 | 本文档已定稿；实现、迁移与验证按 §12 逐条落地，交付记录写入 `docs/audit/impl-notes.md` |

## 1. 目标与非目标

**目标**：对响应体中的个人敏感字段做**字段级**、**权限码驱动**的三态（明文／掩码／隐藏）控制；默认隐藏；脱敏在**后端序列化层**完成；导出、编辑回传与审计三条链路不得出现明文回写或明文落库。

**机制通用性（MUST）**：机制是「字段上加注解 + 配权限码」，不是针对用户模块的专用分支。`sys_user.phone` / `sys_user.email` 只是第一个验证用例；后续任何业务表新增敏感字段，只加注解、补权限码与授权，框架代码不动。

**非目标**：

- 不做行级数据权限（已由 `DataPermissionTables` + `DataScopeSqlBuilder` 实现，见 `docs/design/permission-matrix.md` §3、§4.3）。
- 不用前端隐藏代替后端脱敏（前端隐藏不构成安全边界，见 `BE-SEC-003`）。
- 不改 `sys_config` 增加"脱敏开关"：本设计由权限码驱动，**不新增配置项**（避免出现无消费点的配置，见 `BE-CFG-007`）。
- 不处理密码字段：`sys_user.password` 存 BCrypt 且所有响应 DTO 均不包含该字段，无需脱敏。
- 部门联系电话 `sys_dept.phone` **不纳入**（裁决见 §2、§11）：部门电话属对外公示信息，不构成个人敏感字段。

## 2. 需要脱敏的字段清单（以数据模型与真实代码为准）

清单来源：`docs/design/data-model.md` 的 `sys_user` / `sys_dept` / `sys_oper_log` 字段，以及返回 DTO 中实际出现的字段（`server/system/src/main/java/com/adminstarter/system/application/dto/UserDtos.java`、`DeptDtos.java`、`ProfileDtos.java`）。

| # | 表.字段 | 类型 | 真实返回位置 | 现状 | 是否纳入 | 说明 |
|---|---|---|---|---|---|---|
| 1 | `sys_user.phone` | `varchar(32)` 可空 | `UserDtos.Response`（列表、详情、创建/更新返回）、`ProfileDtos.Response`（本人资料） | 明文返回 | **纳入** | 主要目标字段；掩码按 11 位手机号规则 |
| 2 | `sys_user.email` | `varchar(128)` 可空 | `UserDtos.Response`（列表、详情、创建/更新返回）、`ProfileDtos.Response`（本人资料） | 明文返回 | **纳入** | 同为可识别个人身份字段，与手机号同策略 |
| 3 | `sys_dept.phone` | `varchar(32)` 可空 | `DeptDtos.Response`（部门树、详情、保存返回） | 明文返回 | **不纳入（裁决）** | 部门电话为对外公示信息，不是个人敏感字段；不设权限码、不改 DTO、不改前端 |
| 4 | `sys_oper_log.params` | `text` | 操作日志列表（`OperLogAppService`） | 写入前仅对 `password` 做正则替换（`OperLogAspect#serializeArgs`），请求体中的 email/phone 会以明文落库 | **纳入（审计写入前脱敏）** | 不适用响应三态；规则见 §8 |
| 5 | `sys_login_log.ip` / `user_agent` | `varchar(64/255)` | 登录日志、个人中心 | 明文返回 | **不纳入** | 属日志治理范畴，非字段级个人身份信息；本设计不为它引入权限码 |
| 6 | `sys_config.config_value`（`is_sensitive=1`） | `text` | 参数管理列表、公开配置接口 | 已有独立机制：列表掩码 + 公开接口白名单 5 项 | **不纳入** | 两套机制并行会双定义"谁能看见值"；边界见 `docs/baseline/assertions.md` #7 与 `docs/design/permission-matrix.md` §4.2 |
| 7 | `sys_user.password` | `varchar(100)` | 不出现在任何响应 DTO | 哈希、从不返回 | **不纳入** | 无泄露面 |
| 8 | `sys_user.username` / `nickname`、`sys_dept.leader` | 同上 | 列表、详情、下拉 | 明文返回 | **不纳入** | 账号与显示名是业务必需标识；负责人为自由文本姓名，不列入本轮 |

**禁止扩表**：清单之外的字段不得因为"看起来敏感"自行加入；新增字段必须同步本文件并说明真实返回位置（见 §13 守卫）。

## 3. 三态语义与掩码格式

### 3.1 三态判定（权限码驱动，不绑角色）

每个纳入字段的权限码有两枚：`<前缀>:<资源>:<字段>:view`（明文）与 `<前缀>:<资源>:<字段>:mask`（掩码）。

| 权限组合 | 状态 | 响应中的值 | `maskStates[字段]` |
|---|---|---|---|
| 有 `:view`（无论是否同时有 `:mask`） | **明文** | 数据库原值 | 该键不出现 |
| 无 `:view`、有 `:mask` | **掩码** | 按 §3.2 生成的掩码串 | `"MASKED"` |
| 两者都无（含未登录、权限集合为空） | **隐藏**（默认） | `null` | `"HIDDEN"` |

规则：

- **默认隐藏（fail-closed）**：判定起点是"没有任何权限"，不是"没有任何角色"。
- **超管不豁免**：实现中禁止按角色编码（如 `SUPER_ADMIN`）做特判；`SUPER_ADMIN` 当前持有全部 42 个既有权限码，但新增的字段级权限码不会自动落入其权限集合，必须显式授予（见 §10）。
- **唯一的豁免是"读自己"**：见 §3.3，仅限个人中心读取本人资料；写路径不适用。
- 空值（`null` 或 `""`）不生成掩码，原样返回；`maskStates` 不登记该字段（避免把"本来就没有值"误报成"被隐藏"）。
- 状态标记：仅在对象内出现被掩码或被隐藏的字段时，返回对象追加 `maskStates`（`Map<String,String>`，值为字符串枚举 `MASKED` / `HIDDEN`，符合 `CT-FMT-005`）。明文不登记，避免前端把明文状态也当开关。

### 3.2 掩码格式（唯一定义处）

| 字段 | 匹配条件（对原值原样判定，不做 trim，见 `CT-FMT-008`） | 掩码输出 | 不匹配时 |
|---|---|---|---|
| 手机号（`sys_user.phone`） | 全为数字且长度恰为 11 | 前 3 位 + `****` + 后 4 位（例：`13800008000` → `138****8000`） | **隐藏**（返回 `null`，状态 `HIDDEN`） |
| 邮箱（`sys_user.email`） | 恰有一个 `@`、local 部分非空、domain 部分非空 | local 长度 ≥ 2：首字符 + `***`；local 长度 = 1：`*`；再接 `@` + 原 domain（例：`zhangsan@example.com` → `z***@example.com`） | **隐藏** |

约定：

- 掩码字符固定 ASCII `*`；手机号固定 `****` 4 位，邮箱固定 `***` 3 位；掩码串不保留中间任何字符。
- 不匹配一律**隐藏**，不输出半掩码：半掩码会泄露号段与尾号片段，且无法保证格式稳定。
- 合法手机号/邮箱字符集中不含 `*`，因此"含 `*` 的提交值"可被无歧义识别为掩码回传（见 §6）。

### 3.3 本人读取豁免（裁决 3，范围限定）

| 项 | 规则 |
|---|---|
| 适用接口 | 个人中心读取本人资料的响应（`GET /api/v1/profile` 及资料更新后的返回） |
| 判定 | 由个人中心读取路径显式开启**请求级"读自己"标记**（`MaskSelfReadContext`），该标记只对本次请求生效；同一请求内没有任何"读他人"的响应（`/profile` 只返回本人） |
| 范围边界 | **仅"读自己"**。个人资料的写路径（`PUT /api/v1/profile`）不做豁免，其回传校验与普通更新一致（§6） |
| 不扩散 | 其他 DTO（含 `UserDtos.Response`）**不启用**本人豁免；在用户管理里查看自己那一行仍按权限三态。标记只能由个人中心显式开启，禁止全局放开 |

## 4. 权限码命名与归属

| 权限码 | 含义 | 归属模块 | `sys_menu` 类型 | 挂靠菜单 |
|---|---|---|---|---|
| `system:user:phone:view` | 用户手机号明文 | 系统管理 · 用户 | `F`（按钮/接口） | 用户管理 |
| `system:user:phone:mask` | 用户手机号掩码 | 系统管理 · 用户 | `F` | 用户管理 |
| `system:user:email:view` | 用户邮箱明文 | 系统管理 · 用户 | `F` | 用户管理 |
| `system:user:email:mask` | 用户邮箱掩码 | 系统管理 · 用户 | `F` | 用户管理 |

命名规则：`<前缀>:<资源>:<字段>:<动作>`，前缀与既有 42 个权限码保持一致（`system`），动作仅 `view` / `mask` 两值，不得新增第三值。

- 这 4 枚码只用于**授权**与**后端判定**；前端不按码控制显隐，而按响应中的 `maskStates` 渲染（避免前端与后端判定漂移）。
- 授权入口沿用现有角色-菜单授权树（`sys_role_menu`），不新增授权表、不绑角色编码。
- 种子数据插入必须按 **角色 code + `permission_code` 业务键**匹配，禁止按固定菜单 id / 角色 id 硬编码（历史逻辑删除行会占 id 导致 `INSERT IGNORE` 静默跳过，见 `docs/baseline/assertions.md` #12）。
- 迁移脚本只新增（`V8`），禁止修改 `V1`–`V7`。

## 5. 实现位置与拦截方式

### 5.1 位置：后端 HTTP 响应序列化层

- 在 **web 层**用 Jackson `ContextualSerializer` 实现：响应 DTO 字段加注解 `@MaskedField`（携带字段标识与掩码格式），序列化时读当前登录用户的权限集合决定三态。
- 注解**只允许**加在响应 DTO 上：`UserDtos.Response`、`ProfileDtos.Response`。禁止标在 `CreateRequest` / `UpdateRequest` / `ProfileDtos.UpdateRequest` / DO / 领域模型上——请求体不参与掩码，DO 参与缓存与内部逻辑。
- 掩码算法与状态判定各自只保留一份实现：`MaskFormat`（§3.2 的纯函数）与 `MaskResolver`（§3.1／§3.3 的权限判定）。序列化器与未来的导出/审计都必须调用同一份，禁止复制。
- 注解必须带**字段标识**（`资源:字段`，如 `user:phone`），由通用判定器拼成权限码 `system:user:phone:view|mask`；新增业务字段只需标注 + 配码，不改判定器代码。
- 状态标记 `maskStates` 由响应基类 `MaskStatesSupport` 统一输出（反射读取同类内所有 `@MaskedField` 字段），服务层不手工填充。

### 5.2 拦截方式约束

| 约束 | 理由 |
|---|---|
| 禁止在 Controller / Service 里手工置空或打码 | 易漏、易被新接口绕过；答案应是"序列化时统一生效" |
| 禁止在 SQL / Mapper 层处理 | 同一实体被缓存、被审计复用时会串味；且无法表达"掩码"这一中间态 |
| 无认证上下文时按**无权限**处理（fail-closed），而不是原样输出 | 未知调用方最危险；匿名接口不应出现这些 DTO |
| 权限集合只从 `CurrentUser.permissions`（`DataScopeContext` / `SecurityContext`）取，一次请求解析一次 | 列表 N 行 × 字段若逐字段查库会打爆数据库 |
| **禁止**把掩码模块加进全局 `Jackson2ObjectMapperBuilderCustomizer` | 该 `ObjectMapper` 同时被 `CacheService`（`InMemoryCacheService` / `RedisCacheService`）与 `OperLogAspect` 使用；共用会让掩码值写进缓存（有权限者反而看到掩码），或在无上下文时把缓存里的值全部隐藏 |
| 专用 `ObjectMapper` 只注入 MVC 的 `MappingJackson2HttpMessageConverter`，并保持现有 `JacksonConfig` 的时间与时区设置（UTC、`JavaTimeModule`） | 隔离"对外响应"与"内部序列化"，同时不改变既有时间格式 |

### 5.3 与既有链路的边界

- 认证/JWT：不改令牌内容；权限码仍由 `sys_menu` 经 `AuthAppService` 装配进 `CurrentUser.permissions`。
- 数据权限：与字段级脱敏相互独立，先按数据权限决定"能看到哪些行"，再按字段权限决定"行内哪些字段可见"。
- 公开接口：`GET /api/v1/public/configs` 不涉及本清单字段，规则不变。

## 6. 编辑回传处理（掩码值不得写回）

| 场景 | 处理 |
|---|---|
| 更新请求原样回传该字段当前掩码串 | **忽略该字段**（视为未修改），请求成功；不写库、不报错（表单整体回传是常态，报错会阻断对昵称等无关字段的修改） |
| 提交值含 `*` 但不等于该字段当前掩码 | `422` + `data.errors[{field, code:"INVALID_FORMAT", message:"不允许提交掩码值"}]`（见 `CT-ERR-006`） |
| 创建请求中的手机号/邮箱含 `*` | `422` + 字段级 `INVALID_FORMAT`，防止掩码串被当新值入库 |
| 字段当前为**隐藏**，且调用方无 `:view`，提交 `null` 或空串（意图清空） | `422` + 字段级 `VALIDATION_FAILED`，提示"无权查看该字段，不能清空"（清空是破坏性操作，不能由看不到值的人发起） |
| 字段当前为**明文**或调用方有 `:view` | 按 `CT-FMT-007` 正常语义：缺省 = 不改，`null` = 清空，有值 = 写入 |
| 字段当前为**掩码**，提交与掩码不同的新值 | 正常写入（用户主动改值），随后按调用方权限返回对应状态 |
| 本人更新个人资料（§3.3 豁免范围） | 读取是明文，因此按明文语义处理；掩码串仍按上面两条拦截 |

- "当前掩码"由服务端用库值与同一算法实时计算，不信任客户端传来的任何"是否掩码"标记。
- 更新成功后返回的对象仍按三态输出：改完自己看不见值属于预期，前端提示见 §9。

## 7. 与导出 / 导入的衔接

现状（已核对）：服务端未发现导出/导入实现——无 Excel 依赖（`poi` / `easyexcel`），`docs/api/openapi.generated.yaml` 中也无导出/导入路径。因此本节是**对未来实现的约束**，本轮不产生代码。

| 链路 | 约束 |
|---|---|
| 导出（列表导出、单条导出） | 必须复用 `MaskFormat` 与 `MaskResolver`，按**发起导出人**的权限决定三态；禁止在导出代码里另写一份掩码逻辑 |
| 导出中的隐藏字段 | 整列不输出（不输出空列占位）、并在导出说明中列出被省略的字段名，避免二次猜测 |
| 导出中的掩码字段 | 输出掩码串，不做"部分明文"处理 |
| 导入（模板下载） | 模板若含清单字段，表头必须标注"该列受权限控制"；模板不携带任何真实数据 |
| 导入（写库） | 写库前校验值不含 `*`；命中即整行拒绝并返回字段级 `INVALID_FORMAT`，禁止把掩码当真实值写入 |
| 导入预览/回显 | 与普通响应同三态，禁止预览接口返回明文 |
| 导出临时文件 | 与响应同等对待：掩码或省略，明文不得落到磁盘临时目录 |

## 8. 审计

| 项 | 规则 |
|---|---|
| 响应明文持久化 | 禁止。明文只允许出现在 HTTP 响应体；日志、缓存、审计表、导出临时文件均不得出现清单字段明文 |
| `sys_oper_log.params` | 写入前按**字段名**（`phone`、`email`，含嵌套对象与数组元素）统一替换为掩码串，与查看者权限无关（快照是写入时事实，不随后续授权变化）。现有实现只替换 `password`，属缺口，实现阶段补齐 |
| 操作日志读取 | 因落库即掩码，任何有 `system:log:list` 的人读到的都是掩码，无需再判字段权限 |
| 授权变更 | 授予/回收这 4 枚码属角色修改，已被既有 `@OperLogRecord` 覆盖，不新增审计点 |
| 明文查看是否逐次审计 | **裁决：不新增**。现状无读接口审计机制（`OperLogAspect` 只挂写接口）；本轮不建读审计能力，也不得声称已具备"谁看了谁的手机号"的可追溯性。若将来需要，另立需求 |

## 9. 兼容性影响与前端改造

### 9.1 响应结构（兼容）

- 每个可能被掩码的对象新增可选字段 `maskStates`（值为 `MASKED` / `HIDDEN` 的 map，明文时缺省）。属**新增可选字段**，按 `CT-VER-002` 判定兼容。
- 字段类型不变（`string` 或 `null`），仅取值被限制。

### 9.2 字段语义（裁决：不升 v2，在 `/api/v1` 内实施）

- 变更前：`phone` / `email` 对所有有列表权限的人**总是明文**。
- 变更后：默认隐藏，需 `:view` 才是明文、`:mask` 才是掩码。
- 按 `CT-VER-002`，"改语义／收紧可见性"属**破坏性变更**。**裁决 6：不新建 `/api/v2`，在 `/api/v1` 内直接实施**；破坏性判定与承接措施写入 `docs/audit/impl-notes.md` 的变更记录。
- 承接措施：唯一消费方是仓库内 `apps/pc`，前后端同批发布；授权种子（§10）随迁移同批生效，`SUPER_ADMIN` / `DEPT_MANAGER` 在发布后仍能看到原有信息（明文或掩码），不会出现"上线后全部不可见"的断层。
- 不豁免版本规则的惯例：本条按裁决执行，属于显式承担破坏性变更，不得外推为其他接口也可随意破坏兼容。

### 9.3 契约与基线登记

- 真实接口基线在 `docs/api/openapi.generated.yaml`（已包含 `/api/v1/users`、`/api/v1/profile`、`/api/v1/depts`）；手写契约 `docs/api/openapi.yaml` 仍是未替换的脚手架示例骨架（version `0.0.0`），未收录这些接口。
- 实施时按 `AGENTS.md` §4.2 与 §4.4：重新导出 `docs/api/openapi.generated.yaml`，被前端消费或需评审的接口补写进手写契约并标 `x-status`，同时在 `docs/audit/impl-notes.md` 记录语义收紧。
- 本节不新增错误码；仅复用 `VALIDATION_FAILED`（`30201`／`422`）与字段级 `INVALID_FORMAT` / `VALIDATION_FAILED`。

### 9.4 前端必须跟着改（不改即出现误导 UI）

| 位置 | 必改内容 |
|---|---|
| `apps/pc/src/views/system/user/index.vue` | 编辑表单按 `maskStates` 处理：`MASKED`/`HIDDEN` 时不回填掩码（输入框留空，placeholder 提示"已脱敏/无权查看，留空保持原值"）；非明文状态下留空提交即不携带该字段，明文状态下留空提交 `null` 表示清空。列表当前不展示 phone/email 列，不新增列 |
| `apps/pc/src/views/profile/index.vue` | 本人资料页按 §3.3 恒为明文，页面逻辑不改；仅类型补 `maskStates` 以兼容后续权限收紧 |
| 类型定义 | `apps/shared/src/types.ts` 的 `UserItem`、`ProfileInfo` 补 `maskStates?: MaskStates`；`apps/pc/src/api/user.ts` 的 create/update payload 允许 `email/phone` 为 `string \| null` |
| 交互 | 字段被隐藏时不得展示"空值 = 未填写"的默认文案，避免把"无权查看"误读为"没有值" |

不改：接口路径、分页结构、现有 42 个权限码、`403`／`404` 语义、`apps/pc/src/views/system/dept/index.vue`（部门电话不纳入，见 §2）。

## 10. 角色默认授予（已裁决）

| 权限码 | 默认授予角色 | 其他角色 |
|---|---|---|
| `system:user:phone:view` | `SUPER_ADMIN` | 不授予 |
| `system:user:phone:mask` | `DEPT_MANAGER` | 不授予 |
| `system:user:email:view` | `SUPER_ADMIN` | 不授予 |
| `system:user:email:mask` | `DEPT_MANAGER` | 不授予 |

- `SUPER_ADMIN` 只授予 `view`，不额外授予 `mask`（`:view` 已覆盖明文，避免无用授权）。
- `DEPT_MANAGER` 只授予 `mask`，无 `view`：可识别"有值/脱敏形态"，不能看到完整号码与邮箱。
- 其余角色（含 `EMPLOYEE`）**一律不授予**，按 §3 默认隐藏生效。
- 授权以角色 code + `permission_code` 业务键写入迁移脚本（`V8`），不按菜单 id / 角色 id 硬编码。
- 角色编码与 `docs/design/permission-matrix.md` 一致；实施后重跑导出脚本回填矩阵。
- 授予只影响三态，不放宽任何接口访问权限（无权访问列表的用户仍先被 `403` 拦截）。

## 11. 已裁决事项（替代原待确认表）

| # | 事项 | 裁决结果 |
|---|---|---|
| 1 | 权限码前缀 | `system:`，例 `system:user:phone:view`；与既有 42 枚码同体系 |
| 2 | 默认授权角色 | `SUPER_ADMIN` → `view`；`DEPT_MANAGER` → `mask`；其他角色不授予（§10） |
| 3 | 个人中心是否豁免 | 豁免，限"读自己"：仅 `GET /api/v1/profile` 且归属人为当前用户（§3.3） |
| 4 | 是否新增读取审计 | 不新增（§8） |
| 5 | `sys_dept.phone` | 移出脱敏清单，部门电话是对外公示信息（§2） |
| 6 | 是否升 `/api/v2` | 不升，在 `/api/v1` 内实施并同步改前端（§9.2） |

## 12. 验收用例（实现阶段按此逐条实测）

| # | 身份 / 前置权限 | 操作 | 期望 |
|---|---|---|---|
| 1 | 其他角色（如 `auditor`，有列表权限、无字段码） | 用户列表、用户详情 | `phone`、`email` 为 `null`，`maskStates` 标注 `HIDDEN`（`EMPLOYEE` 因 V7 已无 `system:user:list`，访问列表为 `403`，不用于本用例） |
| 2 | 部门管理员（`DEPT_MANAGER`，仅 `mask`） | 用户列表、用户详情 | 手机号 `138****8000` 形态；邮箱 `z***@example.com`；`maskStates` 标注 `MASKED` |
| 3 | 超管（`SUPER_ADMIN`，有 `view`） | 用户列表、用户详情 | 返回数据库原值；`maskStates` 无该键 |
| 4 | 普通员工 | `GET /api/v1/profile` | 本人 `phone`、`email` 为明文（§3.3 豁免） |
| 5 | 部门管理员 | 编辑用户，仅改昵称、表单原样回传当前掩码串 | 成功；库中 phone/email 不变；响应仍为掩码 |
| 6 | 部门管理员 | 编辑用户，提交含 `*` 且不等于当前掩码 | `422` + 字段级 `INVALID_FORMAT`，库值不变 |
| 7 | 部门管理员 | 目标值格式不匹配导致隐藏（如 `010-12345678`）、显式提交 `"phone": null` | `422` + 字段级 `VALIDATION_FAILED`，库值不变 |
| 8 | 部门管理员 | 创建用户，提交含 `*` 的手机号 | `422` + 字段级 `INVALID_FORMAT`，库里无数据 |
| 9 | 超管 | 将手机号改为另一 11 位号码 | 写入成功，响应为明文 |
| 10 | — | 修改用户后查操作日志 | `params` 快照中 phone/email 为掩码，无明文 |
| 11 | 超管与部门管理员交替请求 | 命中缓存路径后各自再查 | 不出现"缓存把掩码值发给有 `:view` 的人"；缓存中无明文 |
| 12 | 授权变更 | 回收/授予 `view` 后重新登录 | 权限集合随登录态刷新，响应三态随之变化 |

## 13. 自检清单

- [x] 字段清单每个条目都能在 `docs/design/data-model.md` 与真实 DTO 中找到；部门电话按裁决标"不纳入"（MUST, STATIC）
- [x] 三态判定、默认隐藏、超管不豁免写成可判定规则；本人豁免限定为"读自己"（MUST, STATIC）
- [x] 掩码格式给出唯一算法与不匹配时的降级行为（MUST, STATIC）
- [x] 实现位置明确为后端序列化层，并列出禁止位置与禁用全局 `ObjectMapper` 的理由（MUST, STATIC）
- [x] 编辑回传、导出/导入、审计三条链路的明文回写/落库均被禁止（MUST, STATIC）
- [x] 兼容性给出 breaking 判定依据与"不升 v2"的显式承接（MUST, STATIC）
- [x] 默认授予已按裁决填写，无未决事项；文档状态可为 `APPROVED`（MUST, STATIC）
- [x] 权限码前缀与既有体系一致，迁移只新增 `V8`，不改 `V1`–`V7`（MUST, STATIC）
- [x] 机制为"注解 + 权限码"，未为 `sys_user` 写特判（MUST, STATIC）

## 14. 本文档的落地边界

- 本轮已做：定稿本文档（6 项裁决全部写入），未改 `docs/specs/`、未改代码、未改表结构。
- 实施阶段承接：注解与序列化器、`V8` 迁移、审计快照脱敏、前端页面与 TS 类型、真实登录三身份验证、`docs/audit/impl-notes.md` 与基线回填、`docs/design/permission-matrix.md` 重导出。
- 未包含：导出/导入实现（只留 §7 约束）、读审计（§8 裁决不新增）、部门联系电话（§2 裁决不纳入）。
