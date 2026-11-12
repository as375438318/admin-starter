# CR-20261112-027 契约测试扩到写接口（请求体夹具）与 allOf 声明补全

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「开始4」（清单第 4 项：契约测试扩到其余受保护写接口） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只加测试 + 修契约 schema 描述精度（不动接口、字段、状态码、实现行为） |
| 关联 | `docs/api/openapi.yaml` **1.0.3 → 1.0.4**；断言 #92；`CR-20261111-004`（契约测试首版） |

## 1. 背景

原契约测试只覆盖 4 个受保护**读**接口 + 公开配置 + 401 结构。写接口一直没覆盖，原因是"写接口需要业务上合法的请求体，手写常量容易过期"。

## 2. 新增 4 条用例（契约测试 7 → 11）

| 用例 | 夹具来源 | 验证点 |
|---|---|---|
| `POST /my-notices/read-all` | 无（仅需登录态） | 响应 `data.marked` 为整数 + 契约校验 |
| `PUT /jobs/{id}` | **`GET /jobs` 取真实任务**，用其当前值原样回写 | 请求体与响应**双向**契约校验（validator 默认同时校验） |
| `PUT /jobs/{id}/status` | 同上，状态原样回写 | 同上，且不改变真实状态 |
| `PUT /jobs/{id}` 缺 `cronExpression` | 由上一条夹具删字段 | **负例**：422 的**响应**结构是否符合 `ValidationError` |

设计要点：

- **夹具从接口取，不写常量**：手写请求体必然随实现漂移（字段增删后测试还"过"是最危险的情况）。
- **原样回写**：读任务 → 用同样的值 PUT 回去，验证契约而不改真实数据。
- **写操作必须带 `Origin`**：`OriginCheckFilter` 的 CSRF 防护，缺了会先撞 20301 而不是被测分支。

## 3. 逼出来的两个真问题

### 3.1 契约缺陷：`allOf` 成员缺 `additionalProperties`（**13 处**）

负例的 422 响应被判 `additionalProperties` 违规：

```
validation.response.body.schema.additionalProperties
  Object instance has properties which are not allowed by the schema: ["code","message","traceId"]
```

原因与前一批（1.0.2 修的 26 处）同类：`allOf` 成员未声明 `additionalProperties` 时，校验器按**关闭**处理，于是 `Envelope` 里的 `code/message/traceId` 成了"多余字段"。

这次漏的是 **`components/responses` 下的 `ValidationError`** 与 **12 处路径内联的响应 schema**（1.0.2 那批只扫了 `components/schemas`）。已全部补 `additionalProperties: true` 并写了扫描脚本复核（复扫结果：仍缺声明 0 处）。

### 3.2 测试自身的缺陷：`getContentAsString()` 把中文解成乱码

第一版夹具直接把读到的任务名写回请求体，结果 422 报 `maxLength` 违规（"length: 72, maximum allowed: 64"）。真因不是数据问题，而是：

`MockHttpServletResponse.getContentAsString()` 用响应头声明的 charset；本项目响应是 `application/json`（不带 charset）→ 默认 ISO-8859-1 → **每个汉字膨胀成 3 个字符**（24 字 → 72）。

修复：显式 `getContentAsString(StandardCharsets.UTF_8)`。这条差点被我误判成"种子数据里的任务名超长"，是本轮最有价值的教训。

## 4. 契约变更（1.0.3 → 1.0.4）

仅 schema 描述宽松度变化：`ValidationError` + 12 处内联 `allOf` 成员补 `additionalProperties: true`。**字段、状态码、错误码、实现行为均未变**，调用方无需迁移。

## 5. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **193 用例通过**（common 26 / system 149 / web 18） |
| 契约测试单类 | 同上（`ContractValidationTest`） | **11 通过**（原 7 + 新增 4） |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 6. 遗留

- 仍未覆盖的写接口：文件上传/分片/合并（需 multipart 夹具与临时文件）、导入（需 xlsx 夹具）、导出（二进制响应）、缓存清理与会话踢除（有时间/会话副作用）。
- 这些都可做，但夹具成本明显更高，建议按"是否有前端消费 + 是否有副作用"排序后分批补。