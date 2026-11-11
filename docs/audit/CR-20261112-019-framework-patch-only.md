# CR-20261112-019 框架核心依赖只收 patch（Maven minor 对我们是破坏性升级）

| 项目 | 内容 |
|---|---|
| 提出人 | AI 提议（Dependabot 的 maven minor 组把后端编译搞红后），用户「小版本就升级、大版本不变」的原则下细化 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **D（工程化/治理）**：只改依赖升级策略 |
| 关联 | `CR-20261112-018`（主版本忽略）；断言 #84 |

## 1. 现象与根因（实测，不是推测）

Dependabot 的 "maven-minor-patch group"（PR #16，11 个更新）让后端 job 编译失败。从 Actions 日志读到根因：

```
RoleDataPermissionHandler.java:[27,50] cannot find symbol
  symbol: class Expression
  location: com.adminstarter.system.infrastructure.persistence.RoleDataPermissionHandler
```

MyBatis-Plus **3.5.9 起把 JSqlParser 从核心包拆成独立依赖**（`mybatis-plus-jsqlparser`），所以 `3.5.7 → 3.5.18.1` 之后 `net.sf.jsqlparser.expression.Expression` 不在类路径上。

同组里还有 Spring Boot `3.2.12 → 3.5.16`、springdoc `2.3.0 → 2.10.0`、MapStruct `1.5.5 → 1.6.3`——**这些都不是"小版本安全升级"**。

## 2. 结论：Java 生态的"minor"语义与前端不同

| 依赖 | 为什么 minor 也不能自动合 |
|---|---|
| `spring-boot-starter-parent` | SB 的 minor 会连带 Spring Framework / Spring Security 版本线，配置与安全链行为都可能变 |
| `mybatis-plus-*` | 3.5.9 起 JSqlParser 独立成件，直接升等于删掉一个传递依赖（编译失败即证据） |
| `springdoc-*` | 与 Spring 版本强绑定，跨 minor 常有 API/路径变化 |
| `mapstruct*` | 注解处理器与编译期行为，跨 minor 需重新验证生成代码 |

前端那套（Vue 3.5.x、Element Plus 2.14.x）的 patch/minor 相对温和，仍按"自动 + CI 绿即合"。

## 3. 改动

`.github/dependabot.yml` 的 maven 生态：在"忽略所有 major"之外，再对上述四类依赖**忽略 minor（只收 patch）**。

## 4. 处置

- PR #16 **关闭**，并在 PR 里写明根因与后续路径（供以后查阅）；
- 这些框架升级改为**单独迁移项**：需开 CR/ADR、更新 `AGENTS.md` §9-B 与 `ADR-005`/`ADR-006`，并补 `mybatis-plus-jsqlparser` 依赖后再验证。
- Dependabot 已自动关闭 7 个主版本 PR（`#5`–`#11`，`ignore-major` 生效），无需人工干预。

## 5. 回归范围

| 项 | 结果 |
|---|---|
| `dependabot.yml` 结构 | 6 条 ignore（1 major 通配 + 4 框架 minor + npm 1 条 major） |
| 文档门禁 `spec-lint` | error 0 |
| 运行时 | 无改动 |