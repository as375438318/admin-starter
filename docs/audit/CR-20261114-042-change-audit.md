# CR-20261114-042 配置面变更审计

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-13「配置变更审计是干嘛的」→「可以 加上」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-13 |
| 档位 | **B**：单模块（`system`）＋纯新增且向后兼容（新表 `sys_change_audit`、1 个权限码、1 个只读接口）；未命中 `PRC-APR-001` |
| 设计 | `docs/design/change-audit.md` |
| 断言 | #109（留痕三条口径）、#110（生成器迁移幂等） |

## 1. 需求

操作日志只记"请求"（谁点了保存），**没有旧值**。配置面（参数/字典/菜单/角色授权）改错的症状是"系统行为忽然变了"，没有 diff 只能靠猜。本 CR 补上对象级的 before/after 轨迹。

## 2. 影响面清单

| # | 对象 | 变化 | 验证方式 |
|---|---|---|---|
| 1 | 表 | 新增 `sys_change_audit`（Flyway **V21**），含 `(target_type,target_key,created_at)` 与 `(created_at)` 索引 | `flyway_schema_history` + 建表复核 |
| 2 | 接口 | 新增只读 `GET /api/v1/change-audits`（targetType/targetKey/action/operator/keyword/sort/order/分页）。**无任何写入口** | 集成测试：POST 返回 4xx 且不产生记录 |
| 3 | 权限码 | 新增 `system:audit:list`（70 → 71），挂在菜单「变更审计」`/system/audits` 上 | `sys_menu` 查询 + 权限矩阵 |
| 4 | 授权 | `SUPER_ADMIN`、`CROSS_AUDITOR` | SQL 复核 |
| 5 | 写入点 | `ConfigAppService`（C/U/D）、`DictAppService`（类型与项 C/U/D）、`MenuAppService`（C/U/D）、`RoleAppService.assignMenus` | 集成测试 + 运行时冒烟 |
| 6 | 前端 | 新增 `views/system/audit/index.vue`（列设置 + 排序 + 差异展开）；参数列表行新增「历史」按钮跳转带对象键 | `pnpm build`、E2E |
| 7 | 契约 | `1.3.0 → 1.4.0`（纯新增） | YAML 解析 + as-is 重新导出 |
| 8 | 既有模块 | 四个服务的写路径**行为不变**（只多一次留痕写），响应体与状态码均未变 | 全量 `mvn verify` |

## 3. 关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 表设计 | **一张泛化表** vs 每类一张 | 查询维度一致（对象 + 时间 + 人），一张表够；每类一张会让"审计页"退化成 N 个页面 |
| 快照口径 | **只记变化字段** | 整行快照会把差异埋掉，也更容易把无关字段的敏感值带进来 |
| 敏感值 | **两侧都打码** | 只掩新值等于把明文搬进旧值列 |
| 写入口 | **不开放 HTTP 写** | 审计可被写就等于可被改；写入限定在业务事务内，业务回滚则不留痕 |
| 失败处理 | 吞异常 + warn，但**同事务** | 留痕不能把业务搞挂；也不能出现"业务没成但留了痕" |
| `updated_at`/`deleted` | 保留（与 `BaseDO` 一致） | 审计表逻辑上只追加，但为对齐框架公共列保留，避免特例 DO |

## 4. 验证

| 项 | 结果 |
|---|---|
| 后端 | `mvn -pl web -am verify` **BUILD SUCCESS**：common 26 / system **164**（+5 recorder 单测）/ web **39**（+3 集成）＝ **229** |
| 集成测试覆盖 | ① 改参数 → 审计含 before/after 且只含变化字段；② 敏感参数历史值两侧打码；③ 无写入口 + 无权限 403 |
| 前端 | 构建通过（含 `vue-tsc`）、单测 **116** |
| E2E | **13/13**（+1：改参数 → 审计页查到改前/改后 → 还原） |
| 运行时冒烟 | 改 `sys.copyright` 后审计 `total=1`，`before={"configValue":"© 2026 后台管理系统"}`、`after={...·审计}` |
| 契约 | 1.4.0，as-is 已重新导出，YAML 解析通过 |
| spec-lint | error 0 / warning 49 |

## 5. 顺带修掉的一个真问题（我上一轮造成的）

`PR #41` 把 `V21__demo_customer.sql`、`V22__demo_customer.sql` 两份**重复迁移**合进了 `main`——原因是生成器每次运行都用"当前最大版本 + 1"给迁移命名，`--force` 重跑就多产一份。后果：Flyway 报 `Found more than one migration with version 21`，**后端整体起不来**（本地 test/dev 库都踩到了）。

修复内容：
1. 删除两份重复迁移（V20 的内容已覆盖其效果）；
2. 生成器改为**同表迁移复用已有版本号**（幂等），重跑不再新增文件；
3. 本地 test/dev 库清理被误记录的 `21/22` 历史行（效果由 V20 覆盖，属安全清理）；
4. 新增断言 #110：生成器必须幂等；改迁移后务必 `mvn clean`，否则 `target/classes` 里的陈旧副本会让 Flyway 继续报重复。

## 6. 未做（明确边界）

不做全量业务表的行级审计；不做留存归档与防篡改（等保 P0 剩余项，用户已搁置）；不审计读操作。
