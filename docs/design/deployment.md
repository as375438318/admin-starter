# 部署与运维（一键起全栈）

> 状态：`IMPLEMENTED`（2026-11-13，`CR-20261114-044`）
> 关联：断言 #112、#113；`scripts/smoke-deployment.ps1`
> 目标：把"只能在本机跑"变成"任何人 clone 下来三分钟能起"。

## 1. 三分钟起全栈

```powershell
cp .env.example .env      # 改三个口令（MySQL root / MySQL 应用 / Redis）
docker compose up -d --build
docker compose ps         # 等 4 个服务都 healthy
```

访问 `http://localhost`（可用 `.env` 的 `WEB_PORT` 改端口），默认账号见 `README.md`。

验证实例是否真的可用：

```powershell
pwsh -File scripts/smoke-deployment.ps1 -BaseUrl http://localhost/api
```

> 冒烟脚本检查：业务健康接口、readiness/liveness、health 不泄露详情、Prometheus 指标内容。
> 它对着**真实实例**跑——`/actuator/prometheus` 在 Spring 测试上下文里不注册（见 §5），只能用真实进程验。

## 2. 服务与端口

| 服务 | 镜像 | 对外端口 | 说明 |
|---|---|---|---|
| `web` | `deploy/Dockerfile.web`（nginx 1.27） | **只有它发布端口**（默认 80） | 托管前端静态产物 + 反代 `/api` 到后端 |
| `backend` | 根 `Dockerfile`（JDK 17 JRE） | 不发布 | 容器内 8080；被 nginx 反代 |
| `mysql` | `mysql:8.0` | 不发布 | 数据卷 `mysql-data`，时区 `+00:00`（存 UTC） |
| `redis` | `redis:7-alpine` | 不发布 | 需要口令，AOF 持久化，数据卷 `redis-data` |

**为什么只有 web 对外**：`/actuator/prometheus` 与 `/actuator/health` 是**无鉴权**的（抓取端一般不便带鉴权）。
把后端端口限制在内网后，公网只能通过 nginx 访问 `/api/**`，指标端点不可达。
若要在宿主机抓取指标，用 `docker compose exec backend wget -qO- localhost:8080/actuator/prometheus`，
或另起 Prometheus 容器接同一网络。

## 3. 启动顺序与就绪门控

```
mysql(healthy) ┐
redis(healthy) ┴→ backend(healthy: readiness=UP) → web
```

- MySQL 健康检查用**业务账号** `mysqladmin ping`，root 口令改成仅本机时也能工作；
- 后端健康检查打 `/actuator/health/readiness`（含 DB/Redis 依赖），依赖没通就不算就绪；
- `web` 依赖 `backend: service_healthy`，避免前端先起来给用户一堆 502。

## 4. 优雅停机

- `server.shutdown: graceful`（原有）＋ **`spring.lifecycle.timeout-per-shutdown-phase: 30s`**（本轮新增）；
- 后端 `init: true`：让 PID 1 正确转发 SIGTERM，`docker compose down` / 滚动发布时在途请求能处理完；
- 30 秒是折中：太短会把长导出请求掐断，太长会让滚动发布卡住。

## 5. 指标与探针

| 端点 | 鉴权 | 用途 |
|---|---|---|
| `/actuator/health` | 匿名只给 `status`（`show-details: when-authorized`） | 总体健康 |
| `/actuator/health/readiness` | 匿名 | 是否接流量（含 DB/Redis） |
| `/actuator/health/liveness` | 匿名 | 是否该重启（不含外部依赖） |
| `/actuator/prometheus` | **无鉴权**（靠网络隔离） | JVM/HTTP/Hikari/Redis 指标，带 `application="admin-starter"` 标签 |

```text
# HELP jvm_memory_used_bytes ...
jvm_memory_used_bytes{application="admin-starter",area="heap",...} 1.23E8
```

**已知限制（重要）**：`/actuator/prometheus` 在 **Spring 测试上下文**里不会注册
（诊断输出 `webEndpointDiscoverer -> [health, info]`，且没有 `PrometheusMeterRegistry` bean），
真实进程正常（`scripts/smoke-deployment.ps1` 可复现）。所以指标只做**部署冒烟**验证，
不做 MockMvc 单测——不为凑绿灯写假的 mock 测试。原因未查明，已记入断言 #112 待后续排查。

## 6. 数据与备份

| 项 | 位置 | 备份建议 |
|---|---|---|
| 数据库 | 卷 `mysql-data` | `docker compose exec mysql mysqldump -u root -p admin_starter > backup.sql` |
| 上传文件 | 卷 `uploads`（`/app/data/uploads`） | 直接打包卷目录；**必须与数据库一起备份**，否则文件记录会指向不存在的字节 |
| Redis | 卷 `redis-data` | 只存缓存与令牌失效标记，丢了大不了重新登录，可不备份 |

## 7. 明确没做（别当成已有能力）

- **TLS 未包含**：没有 HTTPS/证书/强制跳转。等保 P0 剩余项之一，用户此前搁置；上线前必须由反向代理或网关补；
- 没有 Prometheus + Grafana 的可视化栈（只暴露指标端点）；
- 没有日志聚合（容器日志走默认 json-file，未配轮转上限）；
- 本机未安装 Docker，**compose 未做过实际 `up` 验证**，只做了 YAML 结构校验与 CI 的 `docker compose config`；
- 未做多实例/滚动发布编排（compose 单实例）。
