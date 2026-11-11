# 文件能力增强实施计划（2026-11-09）

## 1. 档位判定（`AGENTS.md` §4.1）

**结论：B 档（模块级）**。只涉及“系统管理 · 文件”一个模块（含其后端包与前端页面）；只做纯新增且向后兼容：新增表、新增列、新增接口、新增权限码；既有上传/下载/删除接口的路径与请求字段不改。

`PRC-APR-001` 逐条核对：

| 类别 | 是否命中 | 依据 |
|---|---|---|
| 1. 契约破坏兼容或版本演进 | 未命中 | 只新增接口与响应字段（`instant`、回收站字段），既有字段不删不改语义；`openapi.yaml` 版本号不升级 |
| 2. 数据模型不可逆变更 | 未命中 | `sys_file` 只新增列（`sha256`、`deleted`、`deleted_at`、`deleted_by`）与新表 `sys_file_chunk_upload`；不删列、不改类型、不缩长度 |
| 3. 权限模型或数据范围放宽 | 未命中 | 新增 `system:file:restore`、`system:file:purge` 两枚权限码，只授予 `SUPER_ADMIN`；`sys_file` 仍按 `dept_id` + `create_by` 受控 |
| 4. 安全降级 | 未命中 | 下载/预览保持服务端鉴权；越权从 404 收敛为 403；预览限定图片/PDF 并加 `nosniff`，属于安全增强 |
| 5. 状态机新增状态或豁免 | 未命中 | 回收站使用 `deleted` 二值 + `deleted_at` 时间戳，不是多状态流转；§4.4 已豁免 `state-machine.md` |
| 6. 验收标准变更或新增业务术语定义 | 未命中 | “秒传/分片/回收站/孤儿”的含义由需求方本轮指令给定；本文只落地，不新增业务术语 |
| 7. 可选模块启用或停用 | 未命中 | 不启停任何模块；孤儿清理挂到既有定时任务模块 |

## 2. 实施步骤

| 步 | 内容 | 产出 | 完成标准 |
|---|---|---|---|
| 1 | 设计文档 | `docs/design/file-storage.md` | 存储抽象、分片生命周期、安全策略、回收站与孤儿口径齐全 |
| 2 | 能力与契约 | `docs/requirements/system-modules.md` §9、`docs/api/openapi.yaml` | 新接口与权限码可逐条核对 |
| 3 | 迁移 V13 | `server/web/src/main/resources/db/migration/V13__file_capability.sql` | 只新增；加列 + 建表 + 2 权限码 + 授权 + 定时任务种子；按业务键匹配父级，不硬编码 id |
| 4 | 存储抽象 | `FileStorage`、`LocalFileStorage`、`FileStorageKeys` | 按日期分片；`storage_path` 为逻辑键，绝不回显 |
| 5 | 上传通道 | 单文件补 `sha256`/秒传；分片 init/upload/status/complete/abort | 断点续传、完成校验、失败清理 |
| 6 | 下载与预览 | 越权 403；`/preview` 仅图片/PDF，inline + nosniff | 路径穿越与越权下载均被拒 |
| 7 | 回收站 | 逻辑删除/列表/恢复/彻底删除；物理字节按引用计数保留 | 全流程可用，重复操作幂等 |
| 8 | 孤儿清理 | `FileOrphanCleanupJob` + V13 任务种子 | 可手动执行，清理临时片与无主物理文件 |
| 9 | 前端 | 多文件拖拽/粘贴、分片进度、预览、删除、回收站 | `pnpm --filter @admin-starter/pc build` 通过 |
| 10 | 实测 | 九项功能实测 + 四条回归 | 见 §3，贴真实响应 |
| 11 | 回填与门禁 | 基线、数据模型/权限矩阵重跑、`openapi.generated.yaml` 重导、`spec-lint` | `node tools/spec-lint/cli.mjs` error 0 |

## 3. 验收口径

| 编号 | 验收项 |
|---|---|
| `AC-FILE-01` | 拖拽多个文件可一次上传多条记录 |
| `AC-FILE-02` | 超过当前上限的文件仍被 413 拦截（上限临时调为 1MB 实测） |
| `AC-FILE-03` | 大文件分片上传成功，最终记录大小与内容正确 |
| `AC-FILE-04` | 中断后重新进入可续传（只补未完成分片） |
| `AC-FILE-05` | 同一文件二次上传为秒传，物理目录不出现第二份字节 |
| `AC-FILE-06` | 换 id 越权下载返回 403，不是 200/404 |
| `AC-FILE-07` | 文件名含 `../` 的路径穿越尝试被拒 |
| `AC-FILE-08` | 图片与 PDF 可在线预览；其他类型预览被拒 |
| `AC-FILE-09` | 删除→回收站→恢复→彻底删除全流程正确 |
| `AC-FILE-10` | 孤儿清理可手动执行，临时片与无主文件被删 |
| `AC-FILE-11` | 四条回归不回退：1MB 上限、413 单条提示不踢下线、前端预校验、扩展名白名单 |

## 4. 风险与处置

| 风险 | 触发条件 | 处置 |
|---|---|---|
| 分片临时目录残留 | 上传中断/放弃/进程崩溃 | 会话行 `status=UPLOADING` 且 24h 未更新由孤儿清理任务删除；abort 接口立即删 |
| 物理去重误删共享字节 | 彻底删除一个被其他记录复用的 `storage_path` | 彻底删除前按 `storage_path` 计数，仍有引用则只删行、保留字节 |
| 秒传跨用户探测 | 他人已知 hash 试图复用 | 秒传只匹配**同一上传者**的历史记录；跨用户物理复用仅在真实上传校验字节后发生 |
| 回收站与全局逻辑删除冲突 | `sys_file` 增加 `deleted` 列 | 统一走 MyBatis-Plus 逻辑删除；彻底删除用自定义 SQL 绕过；`DataPermissionTables` 登记不变 |
| 定时任务单机调度 | 多实例部署 | 沿用既有单机调度事实（`assertions.md` #26），本轮不引入分布式锁 |

## 5. 执行记录（2026-11-09）

| 项 | 结果 |
|---|---|
| 状态 | **已完成**（V13 已应用；后端打包重启、前端构建、spec-lint 均通过） |
| 交付证据 | 见 `docs/audit/impl-notes.md` 文件能力增强章节（含真实响应表） |
| 与计划差异 | ①PDF 预览因 Spring Security 默认 `X-Frame-Options: DENY`，前端改为取 Blob 本地渲染（未改安全配置）；②文件名穿越在 multipart 解析阶段被容器/Tomcat 拒绝（400 `10001`），JSON 声明名由 `sanitizeName` 拒绝（422 `30201`）；③分片上传接口按 `uploader_id` 校验归属，非本人 403；④秒传命中时为当前用户新建记录并标 `instant=true`，不直接复用他人记录 |
| 门禁 | `mvn -DskipTests package` 通过；`pnpm --filter @admin-starter/pc build` 通过；`node tools/spec-lint/cli.mjs` error 0；基线已回填 v1.5 |
