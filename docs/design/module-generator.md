# 模块生成器（`tools/gen-module`）设计

> 状态：`IMPLEMENTED`（2026-11-13）
> 关联：`CR-20261114-041`、断言 #107／#108
> 定位：**开发期脚手架**，产出源码进仓库，走 PR + CI。**不是运行时功能，不写服务器磁盘。**

## 1. 为什么做

新增一个业务模块目前要写 **15–17 个文件**（字典 15、岗位 17）：DO／DTO／Converter／Repository＋Impl／AppService／Controller／Flyway／契约片段／前端 api／页面／列定义／测试。这些文件的**形状高度一致**，但一致性靠自觉——列 key 与后端导出 schema 对不上、按钮权限挂到目录下这类错误，都是靠 E2E 才抓到的。

生成器的价值不是"少打字"，而是**把既有约束固化进模板**，让新模块"起点就合规"。

## 2. 三条红线（改生成器之前先读）

| # | 红线 | 理由 |
|---|---|---|
| 1 | **一次性生成、之后手改，不做双向同步** | 生成器若要解析既有代码，维护成本会超过它省下的成本；"起点正确"已经解决 80% 的问题 |
| 2 | **生成物与手写代码受同一套门禁**：契约测试、spec-lint、断言、单测/E2E 不区分来源 | 否则生成器会变成绕过治理的后门 |
| 3 | **不猜业务**：输入没声明的东西（数据权限、导入导出、脱敏字段）一律不生成 | 生成一个"看起来完整但语义错"的模块，比不生成危害更大 |

补充：**不做运行时菜单式生成器**。产物是源码与数据迁移，必须能 code review、能过 CI；运行时生成意味着服务器能写源码目录（准 RCE），且绕过整条门禁。

## 3. 输入

一份 JSON 描述（仓库没有 YAML 依赖，不为此新增依赖）：

```json
{
  "key": "customer",              // 类名/变量名基（Customer/customer）
  "name": "客户档案",              // 中文名（菜单、日志、错误文案）
  "table": "demo_customer",
  "path": "/demo/customers",      // REST 路径 → /api/v1/demo/customers
  "viewDir": "demo/customer",     // apps/pc/src/views/demo/customer/index.vue
  "apiFile": "demoCustomer",
  "parentMenuPath": null,         // null=挂顶层；否则给目录 path（M 型）
  "menuSort": 12,
  "icon": "Avatar",
  "permissionPrefix": "demo:customer",
  "fields": [
    { "name": "name", "label": "客户名称", "type": "string", "length": 100,
      "required": true, "keyword": true, "sortable": true }
  ]
}
```

字段可选项：`required`（必填+NOT NULL）、`keyword`（参与 keyword 模糊搜索）、`filter`（生成精确筛选参数）、`sortable`（进 `SORTS` 白名单）。

## 4. 产出

| 层 | 文件 |
|---|---|
| 领域 | `domain/model/{Cap}.java`、`domain/query/{Cap}Query.java`、`domain/repository/{Cap}Repository.java` |
| 持久化 | `infrastructure/persistence/{entity,mapper,converter,repository}/*` |
| 应用 | `application/dto/{Cap}Dtos.java`、`application/{Cap}AppService.java` |
| 接口 | `web/controller/{Cap}Controller.java`（5 个接口，带 `@PreAuthorize` + `@OperLogRecord`） |
| 数据 | `db/migration/V{n}__{table}.sql`（建表 + 页面菜单 + 按钮权限码 + 授权） |
| 前端 | `api/{apiFile}.ts`、`views/{viewDir}/index.vue`（列设置 + 排序 + 抽屉表单） |
| 契约 | `docs/api/snippets/{key}.yaml`（**生成片段，由人合并**进 `openapi.yaml`） |

**不生成**：测试（避免生成"永远绿"的假测试）、导入导出、数据权限、脱敏。这些按需人工补，并在 CR 里说明。

## 5. 命令

```powershell
node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json --dry    # 只打印将写入的文件
node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json          # 生成（已存在且不同 → 跳过并列出）
node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json --force  # 覆盖（会丢手改）
node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json --check  # 模板自检：重生成并比对
```

`--check` **故意不进 CI**：红线 1 允许手改生成物，把它接进 CI 会逼人"为了绿灯不手改"，反而扭曲设计。它只用于模板维护时的自检。

## 6. 防腐化

模板腐化的典型表现是"生成出来的代码编译不过"，半年后就没人用了。对策：

1. **每次改模板，用真实模块跑一遍**：`customer` 模块就是生成器的活样例（在仓库里、能编译、有 E2E 验收）。
2. **生成物必须过同一套门禁**：`mvn verify`（含契约测试）、`pnpm build`、`pnpm test:unit`、E2E。
3. **E2E 出厂验收**：`apps/pc/e2e/generated-module.spec.ts` —— 菜单可见、列表可开、新增/删除走通（数据自清理）。

## 7. 后续（未做，别当成已有能力）

- 生成器尚**不生成测试**，也不生成导出/导入接线；
- 只支持单表 CRUD，不支持主子表、树形（部门/菜单那种）；
- `parentMenuPath` 为空时挂顶层，不支持"目录+二级菜单"的三级结构自动化；
- 契约片段较薄（只含 list/create 骨架），更多靠人工按现有段落风格补全。
