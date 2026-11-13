# CR-20261114-044 可交付性三件套（compose / 指标 / 健康检查）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-13「做」（承接 P1-1） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-13 |
| 档位 | **B**：涉及部署产物与运行时行为（探针、指标暴露、停机相位超时），跨构建/配置/文档，未命中 `PRC-APR-001` |
| 断言 | #112（探针与指标）、#113（前端容器形态） |
| 设计 | `docs/design/deployment.md` |

## 1. 目标

底座现在只能"本机两个终端起服务"：Dockerfile 只有后端、没有 compose、actuator 只暴露 `health,info`、上传目录没挂卷。本轮补齐成"clone 下来三分钟能起、能看指标、能优雅停机"。

## 2. 影响面清单

| # | 对象 | 变化 | 验证 |
|---|---|---|---|
| 1 | 部署产物 | 新增 `docker-compose.yml`、`deploy/Dockerfile.web`、`deploy/nginx.conf`、`.env.example`、`.dockerignore` | YAML 解析 + CI `docker compose config -q` |
| 2 | 运行时行为 | actuator 暴露 `prometheus`；启用 `health/readiness`、`health/liveness`；`show-details: when-authorized`；metrics 带 `application` 标签 | 集成测试 2 条 + 部署冒烟脚本 |
| 3 | 安全配置 | `SecurityConfig` 放行 `/actuator/health/**` 与 `/actuator/prometheus`（**无鉴权**，靠部署层网络隔离兜底） | 集成测试：匿名 health 不泄露详情 |
| 4 | 停机 | `spring.lifecycle.timeout-per-shutdown-phase: 30s`（`server.shutdown: graceful` 原已存在）；容器 `init: true` | 配置复核；容器级验证留待有 Docker 环境 |
| 5 | 依赖 | `micrometer-registry-prometheus` | 打包后 `BOOT-INF/lib/micrometer-registry-prometheus-1.12.13.jar` |
| 6 | CI | 新增 job「部署产物校验」：compose config + 前端镜像 build 阶段 | 本次 PR 的 CI |
| 7 | 脚本 | 新增 `scripts/smoke-deployment.ps1`（对真实例验 5 项） | 本地实跑全绿 |

## 3. 关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 端口暴露 | **只有 web 发布端口**，后端不发布 | `/actuator/prometheus` 无鉴权；网络隔离比"给 actuator 加鉴权"更简单可靠（Prometheus 抓取端带口令很别扭） |
| 前端形态 | nginx 静态 + `/api` 反代 | Vite dev server 的代理指向 `127.0.0.1:8082`，容器里连不到后端；且 dev server 带 HMR/源码映射不适合交付 |
| 探针分层 | readiness 含依赖、liveness 不含 | 数据库抖动不应触发容器重启风暴 |
| 停机 | 相位超时 30s + `init: true` | 折中：太短掐断长导出，太长卡住滚动发布 |
| 指标验证层 | 部署冒烟脚本，**不进单测** | 该端点在 Spring 测试上下文里不注册（诊断已确认），写 mock 单测等于自欺 |

## 4. 已知限制（明确写出来，避免被当成已解决）

1. **未做实际 `docker compose up`**：本机没有 Docker。只做了 YAML 结构校验、CI 的 `config -q` 与前端镜像 build 阶段校验。首次真实启动仍需在目标环境验证。
2. **TLS 未包含**：等保 P0 剩余项，用户此前搁置。上线前必须由反向代理/网关补 HTTPS 与跳转。
3. **`/actuator/prometheus` 在测试上下文不注册**：真实进程正常（冒烟脚本覆盖）。原因未查明，已记入断言 #112。
4. 没有 Prometheus/Grafana 可视化栈、没有日志聚合与轮转上限、单实例无滚动发布。

## 5. 验证

| 项 | 结果 |
|---|---|
| 后端 | `mvn -pl web -am verify` BUILD SUCCESS：common 26 / system 164 / web 42 ＝ **232** |
| 前端 | 构建通过、单测 116 |
| E2E | 13/13 |
| 部署冒烟 | `scripts/smoke-deployment.ps1` 对本地实例 5 项全绿（prometheus 40.6 KB，含 `jvm_memory_used_bytes` 与 `application="admin-starter"`） |
| compose | YAML 解析通过；4 个服务，仅 web 有 `ports` |
| spec-lint | error 0 / warning 49 |