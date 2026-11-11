# CR-20261112-016 等保 2.0 条款拆解（自评估清单）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「继续」（按清单第 1 项） |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只加文档 |
| 关联 | `AGENTS.md` §9-A 合规行；断言 #81；新增目录 `docs/compliance/` |

## 1. 背景

`AGENTS.md` §9-A 写的是「需过等保二级／三级；具体条款待拆解为可验收条目，**未拆解前不得以"已合规"表述交付**」。这条约束一直悬着：既不能声称合规，也没有落地路径。本 CR 把它拆成可指派、可验证的清单。

## 2. 产出

[docs/compliance/dengbao-breakdown.md](../compliance/dengbao-breakdown.md)，结构：

1. **三条前置事实**：未定级/未备案、未测评、**不引用具体条款号**（凭记忆写编号极易出错，宁可不写，标注"须对照标准原文补齐"）；
2. **安全计算环境**（应用侧主战场）：15 个控制点逐条给"现状 + 证据位置 + 判定 + 差距优先级"；
3. **安全管理中心与其它技术类**：标注"由运维/基础设施承担"；
4. **管理类**：明确属组织责任，代码解决不了；
5. **差距优先级**：P0 四项（密码策略、审计留存与防篡改、生产强制 TLS、备份与恢复演练）、P1 五项、P2 四项，每条给出验收口径；
6. **业主方待确认事项**：定级、部署形态、测评机构、运维责任方（这四项阻塞后续整改）。

## 3. 核实过的现状（不是凭印象）

写文档前逐条核对了代码，结论与证据：

| 控制点 | 核实结果 | 证据 |
|---|---|---|
| 登录失败锁定 | **有**：`max-login-failures=5`、`lock-duration=15m`，`AuthAppService` 用缓存原子计数 | `application.yml`、`AuthAppService:204-208`、`AuthProperties` |
| 会话超时 | **有**：`access-ttl=15m`、`refresh-ttl=7d` | `application.yml` |
| 密码复杂度/过期/首登改密 | **没有**：全仓搜不到任何密码策略校验，只有启动日志提示改密 | 搜索 `PasswordPolicy|validatePassword|PASSWORD_PATTERN` 无结果 |
| 审计 | **有表**：`sys_oper_log`（受数据权限）、`sys_login_log`；留存策略未定义 | `docs/design/data-model.md` |
| 传输加密 | **可开未强制**：`cookie-secure` 默认 false，无 HTTP 拒绝逻辑 | `application.yml` |
| 上传恶意代码检测 | **没有**：仅扩展名白名单 + 大小上限 | `FileAppService` |
| 数据脱敏 | **有**：字段三态（明文/掩码/隐藏）+ 导出时无权限列整列不输出 | `MaskResolver`、`ExcelExportWriter` |

## 4. 兼容性与回滚

仅新增文档，无代码改动；`git revert` 即回滚。

## 5. 遗留与提请确认

- **RULE-GAP（目录归口）**：`AGENTS.md` §11.1 的模板→目标路径映射里没有"合规/审计证据"类目录。本 CR 采用 `docs/compliance/`，并把该文件登记进 `docs/specs/INDEX-facts.md` 的引用范围（如需正式归口，要另开通用层变更改 §11.1，本 CR 不自作主张）。
- P0 四项里，**密码策略**与**强制 TLS** 会引入新配置项，落地时必须登记 `docs/design/config-registry.yaml`（`SL-CFG-001`）。
- 定级与测评属业主方决策，技术侧先把清单备好。