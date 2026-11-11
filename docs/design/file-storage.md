# 文件能力设计（2026-11-09）

| 项目 | 内容 |
|---|---|
| 版本 | v1.1（2026-11-11，越权语义由 403 修订为与不存在统一 404，依据 `CT-HTTP-008`/`CT-ERR-009`）；v1.0（2026-11-09，文件能力增强） |
| 状态 | DRAFT → 已按本轮需求落地 |
| 读者 | 后端、前端、AI 编码助手 |
| 定位 | **设计（to-be）**：存储抽象、上传通道、安全策略、回收站与孤儿清理的唯一说明 |

> 接口契约见 `docs/api/openapi.yaml`；现状（as-is）见 `docs/baseline/modules.md`。本文不重复契约字段定义，只写设计与取舍。

## 1. 现状与边界

| 能力 | 本轮前（as-is） | 本轮 |
|---|---|---|
| 上传 | 单文件 multipart，扩展名白名单，上限读 `sys.upload.max-size` | 保留单文件接口；新增多文件（前端并发）、拖拽、粘贴、分片与断点续传 |
| 存储 | 本地磁盘平铺 UUID，无抽象 | 抽出 `FileStorage` 接口，本地实现按日期分片；对象存储仅留扩展点，不提供假实现 |
| 去重 | 无 | SHA-256 秒传 + 物理字节复用 |
| 下载 | 服务端鉴权、按 id、数据权限；越权返回 404 | 保持鉴权；越权统一 404（2026-11-11 由「越权改 403」修订，见 §4）；新增预览 |
| 删除 | 物理删除行 + 删磁盘 | 逻辑删除进回收站；恢复；彻底删除 |
| 孤儿 | 无 | 定时任务清理临时片与无主物理文件 |

**明确不做**：对象存储真实实现、病毒扫描、内容 MIME/文件头强校验、跨实例分片与分布式锁、业务表存 URL。

## 2. 存储抽象

### 2.1 接口

```java
public interface FileStorage {
    String store(Path source, String key);          // 写入并返回逻辑键
    InputStream open(String key);
    boolean exists(String key);
    long size(String key);
    void delete(String key);
    Path resolve(String key);                        // 仅本地实现可解析物理路径
    Stream<String> listKeys(String prefix);          // 孤儿清理用
}
```

- `storage_path` 存**逻辑键**，不是物理绝对路径；接口响应与下载 URL 均不出现逻辑键或物理路径。
- 本地实现 `LocalFileStorage`：`root = app.file.upload-dir`，逻辑键格式 `yyyy/MM/dd/{sha256}`（UTC 日期）。
- 对象存储扩展点：新增实现类实现 `FileStorage` 并声明为 Bean；`LocalFileStorage` 用 `@ConditionalOnMissingBean` 或显式条件装配。**本轮不提供任何对象存储实现**，文档不声明“已支持”。

### 2.2 目录分片

- 新文件：`yyyy/MM/dd/{sha256}`，按上传日期分片，避免单层堆叠。
- 历史文件：沿用原平铺 UUID 键，`resolve` 兼容读取；不迁移旧数据（迁移需单独评估）。
- 临时区：`.chunks/{uploadId}/{index}.part` 与 `.tmp/{uuid}`；孤儿清理任务跳过并单独清理。

## 3. 上传通道

### 3.1 单文件（既有，保留）

`POST /api/v1/files`（`system:file:upload`）。行为变化只有两点：写入时计算 SHA-256；若同一上传者已有相同 `sha256+size` 且物理文件存在，则复用物理字节、只新增记录，响应 `instant=true`，否则 `instant=false`。

### 3.2 秒传探测

`POST /api/v1/files/instant`：客户端先传 `fileName/size/contentType/sha256`。仅当**当前上传者**存在相同 hash 且物理文件存在时，创建新记录并返回 `instant=true`；否则返回 `instant=false`，客户端继续真实上传。

> 安全取舍：不跨用户秒传，避免“知道 hash 即复制他人文件”。跨用户物理去重只在真实上传、服务端亲自计算 hash 后发生。

### 3.3 分片上传与断点续传

| 步骤 | 接口 | 说明 |
|---|---|---|
| 初始化 | `POST /api/v1/files/chunk-sessions` | 校验文件名/扩展名/总大小/分片数；返回 `uploadId/chunkSize/totalChunks/uploadedChunks`；命中秒传则直接返回文件 |
| 上传分片 | `PUT /api/v1/files/chunk-sessions/{uploadId}/chunks/{index}` | `multipart/form-data` 字段 `chunk`；单分片 ≤ 声明值；重复上传覆盖 |
| 查询进度 | `GET /api/v1/files/chunk-sessions/{uploadId}` | 返回已上传分片序号，供续传 |
| 完成 | `POST /api/v1/files/chunk-sessions/{uploadId}/complete` | 合并临时片，校验字节数与 SHA-256，写入最终逻辑键，建文件记录，删临时目录 |
| 放弃 | `DELETE /api/v1/files/chunk-sessions/{uploadId}` | 标记 `ABORTED`，立即删临时目录 |

- 会话持久化在 `sys_file_chunk_upload`，进程重启后仍可续传。
- 会话归属校验：`uploader_id` 必须等于当前用户，否则 403。
- 临时片清理：`complete`/`abort` 立即清理；`UPLOADING` 且 `updated_at` 超过 24 小时的会话由孤儿清理任务删除临时目录并置 `EXPIRED`。
- 前端分片阈值 5MB、分片 2MB（常量）；后端只校验 `chunkSize` 在 256KB–5MB、`totalChunks ≤ 1000`，不强制前端阈值。

## 4. 下载与预览

| 接口 | 行为 |
|---|---|
| `GET /api/v1/files/{id}/download` | 鉴权 + 数据权限；`Content-Disposition: attachment`；不暴露逻辑键/物理路径 |
| `GET /api/v1/files/{id}/preview` | 同上鉴权；仅 `jpg/jpeg/png/gif/webp/pdf`；`inline` + 按扩展名确定 Content-Type + `X-Content-Type-Options: nosniff`；其他类型 422 |

- **越权语义**：记录不存在 → `404`；记录存在但不在当前用户数据范围内 → **同样 `404`，且与不存在使用同一文案**「文件不存在或无权访问」，不暴露资源是否存在。
  - 修订记录（2026-11-11，用户裁决）：本文 v1.0 原写「越权 → 403（需求硬要求）」，与接口契约 `CT-HTTP-008`（越权访问统一 404）和 `CT-ERR-009`（越权与不存在同一码）直接冲突。按 `AGENTS.md` §1 的冲突优先级（契约 > 设计），原口径作废，实现改为 404；缺功能权限（无 `system:file:download`）仍由 Spring Security 返回 `403`，两者不再混用。
- **路径穿越**：原始文件名先按 `/` 与 `\` 取最后一段，拒绝含 `..`、控制字符或超长；最终逻辑键由服务端按 hash 生成；`resolve` 归一化后必须 `startsWith(root)`。
- **预览限定**：只按扩展名放行；`txt/csv/html` 等一律不允许 inline，避免 XSS。

## 5. 权限与归属

- `sys_file` 保持受控：组织范围 `dept_id`，归属 `create_by`；文件上传人 = `create_by`，部门 = 上传时快照。
- 新增权限码：`system:file:restore`（恢复）、`system:file:purge`（彻底删除），只授予 `SUPER_ADMIN`。
- **业务表只存 `file_id`**：不存 URL、不存文件名、不存逻辑键。URL 会随域名/网关变化，逻辑键会随存储迁移变化；`file_id` 是唯一稳定引用。需要展示时由前端按 `file_id` 拼 `/api/v1/files/{id}/download|preview`。

## 6. 回收站

- `sys_file` 增加 `deleted`（0/1）、`deleted_at`、`deleted_by`；全局逻辑删除字段为 `deleted`，`selectById/page` 自动过滤回收站。
- `DELETE /api/v1/files/{id}`：未命中 → 200（幂等）；命中 → 置 `deleted=1`、`deleted_at`、`deleted_by`，**不删物理字节**。
- `GET /api/v1/files/recycle`：自定义 SQL 查 `deleted=1`，仍受数据权限约束。
- `POST /api/v1/files/{id}/restore`：`deleted=1` → `0`，清空删除标记；已不在回收站 → 200 幂等。
- `DELETE /api/v1/files/recycle/{id}`：真正删除行；随后按 `storage_path` 统计**仍存在的所有行**（含其他回收站行）：为 0 才删物理字节，否则保留（共享去重字节）。
- 回收站行与正常行一样参与下载鉴权；正常下载接口对 `deleted=1` 返回 404。

## 7. 孤儿文件清理

- 实现为 `ScheduledJob`（`key=file-orphan-cleanup`），V13 在 `sys_job` 写入每日 02:00 的任务定义；不新建调度器。
- 清理范围（超过 24 小时才动，避免误删正在写入的文件）：
  1. `sys_file_chunk_upload` 中 `UPLOADING` 且 `updated_at < now-24h` 的会话：删临时目录、置 `EXPIRED`；
  2. `/ .tmp` 下超过 24 小时的文件；
  3. 存储根下（跳过 `.chunks`、`.tmp`）不在 `sys_file.storage_path` 集合（含回收站行）中的物理文件。
- 返回影响条数（删除的会话 + 临时文件 + 孤儿文件）。
- 单机调度事实沿用 `assertions.md` #26，多实例部署前需先完成调度选型改造。

## 8. 表结构变更（V13）

| 变更 | 内容 |
|---|---|
| `sys_file` 加列 | `sha256 CHAR(64) NULL`、`deleted TINYINT NOT NULL DEFAULT 0`、`deleted_at DATETIME NULL`、`deleted_by BIGINT NULL` |
| `sys_file` 加索引 | `idx_file_sha256 (sha256)`、`idx_file_deleted (deleted, created_at)` |
| 新表 | `sys_file_chunk_upload`（`upload_id` 唯一、上传者、原始名、大小、sha256、分片大小、总分片、已传分片、状态、时间） |
| 数据权限 | `sys_file_chunk_upload` 登记为 `EXEMPT`（会话表不做数据权限；靠 uploader_id 归属校验） |

## 9. 成本与限制

- 下载/预览：每次 2 次查询（一次原生存在性、一次受控可见性）；单文件上传额外一次 SHA-256 流式计算（按字节线性，10MB 级可接受）。
- 分片合并：顺序读临时片写入最终文件，峰值内存不随文件大小增长；磁盘临时空间约为文件大小 1 倍。
- 物理去重后彻底删除需要按 `storage_path` 计数，避免删掉他人正在引用的字节。
- 未做：对象存储实现、断点续传的服务端分片校验（仅校验最终 hash）、回收站自动过期清理（保留人工彻底删除）。

## 10. 自检清单

- [x] 存储抽象接口与本地实现分离，对象存储未假装支持（MUST, STATIC）
- [x] 目录按日期分片，历史平铺键兼容（MUST, STATIC）
- [x] 分片临时片有明确清理路径与任务（MUST, CONTEXT）
- [x] 下载/预览服务端鉴权，越权与不存在统一 404（2026-11-11 修订，原为 403），路径穿越拒绝（MUST, STATIC）
- [x] 秒传只限同一上传者，跨用户仅真实上传后物理复用（MUST, STATIC）
- [x] 业务表只存 `file_id`（MUST, STATIC）
- [x] 回收站与彻底删除的引用计数规则写明（MUST, STATIC）
