# CR-20261111-006 受保护接口契约测试 + 契约两处精度修正

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-11「按顺序进行」（对应上一轮列出的第 1 项：给受保护接口补契约测试） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-11 |
| 变更层级 | 项目层（测试 + 契约文档） |
| 档位 | **D（工程化/治理）**：不改运行时行为；`PRC-APR-001` 七类逐项未命中（版本号从 1.0.2 → 1.0.3 属**精度修正**而非兼容性演进，与 1.0.1/1.0.2 先例一致） |
| 关联 | `CR-20261111-004`（契约测试落地）；断言 #70（修订）、#71（新增）；`docs/api/openapi.yaml` 1.0.3 |

## 1. 背景

`CR-20261111-004` 落地的契约测试只覆盖 3 个**未登录**断言点（公开配置 200、白名单字段、401 结构）。契约共 53 条路径，其中绝大多数需要登录态——也就是说，`AGENTS.md` §7 的"契约测试失败 → error 级阻断"实际只在 3 个点上有效。

## 2. 范围

**测试**：`ContractValidationTest` 增加 4 个受保护接口断言（真实登录换 Cookie，不用 mock 身份，避免"测的是假登录"）：

| 用例 | 覆盖 |
|---|---|
| `fileListMatchesContract` | `GET /api/v1/files` 200，分页四字段 + 文件项必填字段 |
| `noticeListMatchesContract` | `GET /api/v1/notices` 200 |
| `fileRecycleListMatchesContract` | `GET /api/v1/files/recycle` 200 |
| `previewMissingFileMatchesContract` | `GET /api/v1/files/{id}/preview` 不存在 → 契约声明的 404（与越权统一，不泄露存在性） |

登录走真实 `POST /api/v1/auth/login`（`admin/admin123`，测试库种子）取 `ACCESS_TOKEN` Cookie；登录接口本身不在契约交付范围内，不参与校验。

## 3. 测试首次运行抓到的问题（两处，均在契约侧）

### 3.1 `FilePageResponse` 漏声明分页字段（契约漏字段）

validator 报 `[Path '/data'] Object instance has properties which are not allowed by the schema: ["page","pageSize"]`。核对契约发现：`FilePageResponse` 的 `data` 只声明 `required: [list, total]`，而**其它 8 个分页 schema 都是四字段**（`list/total/page/pageSize`），实现 `PageResponse` 也确实返回四个字段。

结论：**契约漏写**，实现无误。处置：补 `page`/`pageSize` 并加注释。

### 3.2 `allOf` 成员缺 `additionalProperties` 是系统性问题

`CR-20261111-004` 当时只在 `PublicConfigResponse` 上补了声明，本次三个受保护接口再次同样报错 → 说明这是**普遍**问题，不是个例。校验器对 `allOf` 成员缺该声明时按"关闭"处理，会把信封合法的 `code/message/traceId` 判成多余字段。

中间尝试过"把 `validation.response.body.schema.additionalProperties` 降级为 WARN"，**无效**——父级 `allOf` 仍以 ERROR 报出（`Instance failed to match all required schemas`）。最终采用治根方案：**脚本化给契约里全部 26 处 `allOf` 成员补 `additionalProperties: true`**，并保留 `additionalProperties` 的 ERROR 级别（不降低门禁强度）。

## 4. 涉及事实

| 事实 | 变更前 | 变更后 |
|---|---|---|
| 契约测试断言点 | 3（仅未登录） | **7**（含 4 个受保护接口） |
| 契约版本 | 1.0.2 | **1.0.3** |
| `allOf` 成员声明 | 1 处显式 | **26 处全部显式** |
| 后端测试总数 | 69 | **73** |

## 5. 兼容性

- 运行时零改动：只改测试与契约文档。
- 契约变更均为精度修正：字段集合与实现既有行为对齐，状态码与错误码集合不变，调用方无感。

## 6. 回滚方式

`git revert` 本 CR 提交；契约版本回 1.0.2。无数据影响。

## 7. 影响文档与引用点

| 文档 | 影响 |
|---|---|
| `docs/api/openapi.yaml` | 1.0.3 + 变更记录（CT-VER-005）；`FilePageResponse` 补字段；26 处 allOf 成员补声明 |
| `docs/baseline/assertions.md` | #70 修订（从"单个 schema"改为"全部 allOf 成员，脚本化补齐"）、新增 #71（分页 schema 必须四字段） |
| `docs/audit/impl-notes.md` | 记录本次过程与两次失败尝试 |
| `README.md` | 变更记录 v1.21 |

## 8. 条目状态变更

无 `DRAFT`/`APPROVED` 变化。

## 9. 代码改动顺序

契约补 `FilePageResponse` 字段 + 批量补 allOf 声明 → 契约 1.0.3 → 测试补 4 个受保护断言 → 回填断言/impl-notes/README → spec-lint → 提交。

## 10. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `cd server && mvn -B -pl web -am verify` | **73 用例通过**（common 26 / system 38 / web 9），BUILD SUCCESS |
| 契约 YAML | PyYAML 加载 | version=1.0.3、paths=53、schemas=54 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 11. 异议记录与遗留

- **失败尝试（已记录，避免后来者重走）**：先试"把 additionalProperties 检查降级为 WARN"，父级 allOf 仍报 ERROR → 无效；改为批量补声明。这说明**契约里的 `allOf` 组合只要缺声明，降级单个 ruleId 是修不干净的**。
- **遗留**：受保护接口仍只覆盖 4 个（files 列表/回收站、notices 列表、preview 404）。契约里还有 ~30 个需登录的接口未做契约校验，其中写接口（POST/PUT/DELETE）需要构造请求体夹具，成本更高；建议按"前端已消费 + 有状态变更"优先级逐步补。
- **遗留**：登录夹具用的是 `admin`，只覆盖超管视角；数据权限相关的响应差异（不同角色看到不同数据）未在契约层验证（那属行为测试，不是契约测试）。
