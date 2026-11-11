# CR-20261111-004 后端测试深度补齐（数据权限 / 字段脱敏 / 契约验证）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户（2026-11-11 指示「按照顺序实行」，对应上一轮列出的 1→2→3 项） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-11 |
| 变更层级 | 项目层（测试与契约）＋ 通用层单点（`AGENTS.md` §9-B「测试框架」行补契约验证口径） |
| 关联 | `docs/baseline/assertions.md` #69、#70；`CR-20261111-003`；`docs/api/openapi.yaml` 1.0.2 |

## 1. 背景

`CR-20261111-003` 把后端测试接进了 CI，但只有 2 个测试类（通知事务 + 数据权限登记表）。两块"改错就出事"的核心逻辑仍然零测试：

- **数据权限**：条件拼错一处就等于数据越权可见（或把该看的人挡掉）；
- **字段脱敏**：掩码算法/回写保护错了等于真实手机号被掩码覆盖，**不可逆丢数据**；
- **契约**：`AGENTS.md` §7 把"契约测试失败"列为 error 级阻断，但 `docs/api/examples/backend` 只有模板，CI 无法校验实现与契约一致。

## 2. 范围

**新增测试（共 69 个用例，此前 8 个）**

| 模块 | 测试类 | 用例 | 锁定什么 |
|---|---|---|---|
| common | `MaskFormatTest` | 6 | 手机号/邮箱掩码算法；**格式不匹配返回 null**（不出半掩码）；记录实现用 `Character.isDigit`，全角数字也被接受（现状观察项） |
| common | `MaskResolverTest` | 8 | 三态判定：view→明文、mask→掩码、都没有/未登录→HIDDEN；空值原样；权限码拼装 `system:<key>:<action>` |
| common | `MaskedValueGuardTest` | 10 | 未携带字段保留原值；**原样回传掩码不覆盖真值**；提交别的掩码→422；HIDDEN 时不允许清空（防盲改）；创建路径拒绝掩码字符 |
| common | `MaskUtilsTest` | 2 | 参数掩码固定 6 星、空值透传 |
| system | `DataScopeSqlBuilderTest` | 12 | 全不限制→null；**该限制但无可条件→`1 = 0`（fail-closed）**；组织/归属各自 or、两者 and；只拼数值 ID |
| system | `RoleDataPermissionHandlerTest` | 10 | 非受控不改写；豁免表放行；**未登记表抛错**；通知公共行例外整体加括号防 OR 绕过；生成片段可被 SQL 解析器复解析 |
| system | `DataScopeProviderTest` | 10 | 多角色取并集、最宽档胜出；无角色→仅本人；CUSTOM_DEPT/USER 并入；缓存键含部门版本号；命中缓存不查库 |
| web | `ContractValidationTest` | 3 | 真实响应 vs `docs/api/openapi.yaml`：公开配置 200 结构、白名单外字段不得出现、未登录 401 结构 |

**其他改动**

- `server/common/pom.xml`：补 `spring-boot-starter-test`；`server/web/pom.xml`：补 `swagger-request-validator-mockmvc:2.44.9`（test 作用域，坐标取自 `docs/api/examples/backend/pom-snippet.xml`）。
- 新增 `AbstractIntegrationTest`：统一 `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")`，两个集成测试类改为继承。
- `docs/api/openapi.yaml` 1.0.1 → **1.0.2**：`PublicConfigResponse` 的 allOf 成员补 `additionalProperties: true`（见 §4）。

## 3. 涉及事实

| 事实 | 变更前 | 变更后 |
|---|---|---|
| 后端测试用例数 | 8 | **69** |
| 契约测试 | 未落地（`AGENTS.md` §7 的阻断项形同虚设） | 已落地，读仓库契约本体 |
| 测试基础设施口径 | `AGENTS.md` §9-B 测试框架行 | 补"契约验证用 swagger-request-validator 比对 `docs/api/openapi.yaml`" |

## 4. 契约测试首次运行发现的问题（重要）

第一次跑 `publicConfigsMatchesContract` 就**红了**，报：`Object instance has properties which are not allowed by the schema: ["code","message","traceId"]`。

用 `-Dcontract.file=<探针副本>` 做对照实验（探针只在 allOf 成员上加了 `additionalProperties: true`）后立刻变绿 → 结论：**不是实现偏离契约，而是契约精度不足**。swagger-request-validator 2.44.9 对 `allOf` 成员缺 `additionalProperties` 时按"关闭"处理，于是信封里合法的 `code/message/traceId` 被判成多余字段。

处置：在 `PublicConfigResponse` 的 allOf 成员显式声明 `additionalProperties: true`，并加注释说明原因；契约升 1.0.2 并写变更记录。**实现代码零改动。**

## 5. 兼容性

- 运行时行为无变化：只加了测试与契约文档措辞。
- 测试：新增 61 个用例，全部为纯函数/单上下文内验证，无外部依赖（除已有 MySQL/Redis）。
- 契约：1.0.2 属精度修正，字段/状态码/错误码集合不变，调用方无感。

## 6. 回滚方式

`git revert` 本 CR 对应提交即可；契约版本回 1.0.1。无数据影响。

## 7. 分级与审批

| 档位判定项 | 结论 |
|---|---|
| ① 只动 1 个模块 | 否（common/system/web 三模块 + 契约 + 通用层一行） |
| ② 不新增/删除/改名接口路径或字段 | 是 |
| ③ 不动表结构与数据迁移 | 是 |
| ④ 不新增权限码 | 是 |
| ⑤ 不新增错误码 | 是 |
| ⑥ 未命中 `PRC-APR-001` | 是 |

→ 与 CR-001/002/003 同类：拆成单模块单元 + 通用层单点留痕。**规则缺口第 4 次出现**，已在 §11 单列。

## 8. 条目状态变更

无 `DRAFT`/`APPROVED` 变化。`docs/api/openapi.yaml` 版本号按 `CT-VER-005` 升到 1.0.2 并写变更记录。

## 9. 代码改动顺序

pom（common/web）→ 掩码与数据权限单测 → 契约验证测试 → 共享上下文基类 → 契约 1.0.2 → 回填断言/impl-notes/README。

## 10. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd server && mvn -B -pl web -am verify` | **69 用例通过**（common 26 / system 38 / web 5），BUILD SUCCESS，约 21s |
| 契约对照实验 | `mvn verify -Dcontract.file=<探针>` | 探针（allOf 加 additionalProperties）通过；原契约红 → 证明是契约精度问题 |
| YAML 可解析 | PyYAML 加载 | version=1.0.2，paths=53 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 11. 异议记录与规则缺口

- **异议（自我纠正 2 处，均属我方断言错误，不是产品缺陷）**：
  1. `MaskFormat.PHONE` 用 `Character.isDigit`，我原以为全角数字会被拒；实测被接受 → 改为记录"现状 + 若要严格 ASCII 需另行决策"；
  2. `MaskedValueGuard` 清空空原值时返回 `""` 而非 `null` → 按实现修正断言。
- **规则缺口（第 4 次，`RULE-GAP`）**：`AGENTS.md` §4.1 的 A/B/C 档位仍无法容纳"跨模块工程化改动 + 通用层单点更新"。四次处置一致（拆单元 + 通用层留痕），但这是**流程缺口而非技术缺口**，应由人决定是否新增一档。
- **未解决技术债**：契约测试目前只覆盖 3 个断言点（公开配置 200、401 结构、白名单字段），因为契约只文档化 53 条路径中的一部分且多数需要登录态；要覆盖受保护接口需引入 `spring-security-test` 造登录会话，属后续工作。
