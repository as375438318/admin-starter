# CR-20261112-034 契约测试扩到高风险写接口（上传 / 分片合并 / 导入）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「契约测试补 3 个高风险写接口（文件上传/合并/导入）」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只加测试 + 一处契约描述精度修正（`ChunkSessionResponse.file` 语义明确化，实现同步不序列化 null），接口路径/字段集合/状态码/权限码/错误码均未变 |
| 关联 | `CR-20261112-027`（JSON 写接口契约测试）；契约 **1.1.0 → 1.1.1**；断言 #99 |

## 1. 为什么挑这三个

它们同时具备三个特征，是"出问题就是数据或磁盘事故"的路径：

1. **携带二进制/multipart**（此前契约测试只覆盖 JSON）；
2. **落盘**（临时分片、合并后的物理文件）；
3. **整批语义**（导入任一行错则整批不落库）。

## 2. 新增 4 条用例（契约测试 30 条中的 4 条）

| 用例 | 覆盖 | 断言要点 |
|---|---|---|
| `uploadFileMatchesContract` | `POST /files`（multipart 单文件） | 响应契约 + `originalName`/`size` 与内容一致 |
| `chunkUploadMatchesContract` | 分片全链路 `init → PUT chunk ×2 → complete` | 三步响应都过校验；合并后 `size` 与内容一致 |
| `importMatchesContract` | `POST /demo-tasks/import`（xlsx） | `totalRows=1`、`imported=1` |
| `importValidationErrorMatchesContract` | 同上，标题留空 | **422** + 30201 + `data.errors` 为数组 |

夹具原则：**不写死二进制样本**。xlsx 用 POI 现场生成（数据页名取自 `schema.dataSheetName()`），分片内容每次随机。

## 3. 逼出来的一处契约缺陷（已修）

分片初始化响应里 `data.file` 在**未命中秒传时是 null**，而契约原本写成：

```yaml
file:
  nullable: true
  allOf:
    - $ref: '#/components/schemas/FileRecord'
```

OAS 3.0 的 `nullable` **不作用于 `allOf` 分支**——校验器仍按 `object` 校验 null，报
`Instance type (null) does not match any allowed primitive type (allowed: ["object"])`。补 `type: object` 也救不了（allOf 分支依旧校验）。

最终处理：**语义明确为"只有秒传命中才出现该字段，未命中时字段缺省"**——
实现侧给 `file` 加 `@JsonInclude(NON_NULL)`，契约侧改为直接 `$ref` 且不列 `required`。调用方读取方式不变（`null` 与缺省等价）。契约版本 1.1.0 → **1.1.1**（描述精度修正，行为未变）。

## 4. 自身踩的坑（4 条，都记在测试注释里）

| 坑 | 现象 | 结论 |
|---|---|---|
| MockMvc multipart 无法被校验器看到请求体 | `validation.request.body.missing` | multipart 用例改用"只校验响应"的校验器（工具限制，不是实现违约） |
| 分片 `chunkSize` 有下限 | 422「分片大小必须在 256KB–5MB 之间」 | 夹具用 256KB（`CHUNK_MIN_SIZE`） |
| 分片初始化的字段名是 `size` + `contentType`（不是 `totalSize`），且缺省 `size=0` | 422「文件大小不能小于 0」 | 按 `FileDtos.ChunkInitRequest` 真实字段构造 |
| 分片 PUT 是 **multipart 字段 `chunk`**，不是裸字节 | 400 | 用 `multipart(HttpMethod.PUT, ...)` + `MockMultipartFile("chunk", ...)` |
| 内容固定会命中秒传 | 第二次跑 init 返回 `instant=true`、没有 `uploadId` → 后续 PUT 400 | 分片内容每次随机（与"夹具不可固定"同一教训） |
| 导入的数据页名必须是「示例任务」 | 400「缺少「示例任务」数据页」 | 页名取自 schema，不写「数据」 |
| 导入成功响应字段是 `imported`（不是 `successRows`） | JSON path 取不到值 | 按 `DemoTaskDtos.ImportResult` 真实字段断言 |

## 5. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **215 用例通过**（common 26 / system 159 / web 30） |
| 前端 | build + `test:unit` | 通过 / 97 通过 |
| E2E | `pnpm exec playwright test` | **8/8 通过** |
| 契约导出 | `export-openapi.ps1 -Password ...` | 已重新导出 |
| 文档门禁 | `spec-lint` | error 0 |

## 6. 仍未覆盖（成本更高，按需再排）

文件回收站恢复、导出任务（二进制响应只到响应头）、缓存清理、会话踢除（有"踢掉自己"的副作用）、岗位导入（与示例任务共用同一套框架，收益递减）。