# CR-20261112-028 通知公告列表显式降序（前端传 sort/order，不动全局默认）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「通知公告 发布的通知 是怎么排序的」→ 选择方案 C |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **A（微改）**：只改 1 个前端页面；不动接口路径/请求字段/表结构/权限码/错误码 |
| 关联 | 断言 #93；`SortSupport`（排序白名单） |

## 1. 现状（实测，不是读代码推测）

`GET /api/v1/notices` 的排序是 `order by created_at <方向>, id DESC`，方向来自 `SortSupport.isAsc(query.getOrder())`，而该方法**对 null 返回 true** → **不传 order 就是升序**。

实测对照（同一份数据、同一账号）：

| 请求 | 前三条 |
|---|---|
| `?page=1&pageSize=3`（不传） | id=1（11/09 03:26）、id=2（11/09 03:26）、id=4（11/09 03:27）→ **最早的在前** |
| `?sort=createdAt&order=desc` | id=30（11/12 04:37）、id=29（11/12 04:37）、id=28（11/12 03:41）→ 最新的在前 |
| 对照 `GET /my-notices`（收件箱，SQL 写死） | 最新在前 |

所以：**同一批通知，后台管理页与"我的通知"顺序相反**，而后台页前端此前**根本没传** `sort`/`order`。

## 2. 后端不需要改（关键核实）

一度担心"前端传了也没用"，核实结果是后端**已经支持**：

- `PageQuery`（公共分页基类）本身就有 `sort` / `order` 字段；
- `NoticeController.page(PageQuery pageQuery, ...)` 直接绑定；
- `NotificationAppService.page` 把 `pageQuery.getSort()/getOrder()` 写进 `NoticeQuery`；
- `NoticeRepositoryImpl` 用 `SORTS` 白名单（`createdAt`/`title`/`id`）解析，白名单外的值**静默回落**到 `created_at`。

因此本 CR 是纯前端改动。

## 3. 改动

`apps/pc/src/views/system/notice/index.vue` 的 `fetcher` 增加：

```ts
sort: 'createdAt',
order: 'desc',
```

并加注释说明"为什么不改全局默认"：`SortSupport.isAsc(null) === true` 影响**全项目所有列表**，改它会把十几个页面的顺序一起翻过来，属跨模块行为变更（C 档），不该夹在"通知排序"这种小修里。

## 4. 验证

| 项 | 命令/方式 | 结果 |
|---|---|---|
| 构建 | `pnpm --filter @admin-starter/pc build` | 通过 |
| 单元测试 | 同上 `test:unit` | **97 通过** |
| E2E | 起后端 + 前端，`pnpm exec playwright test` | **7/7 通过** |
| 接口实测 | 上述三组对照请求 | 默认升序 / 显式降序 / 收件箱降序，均符合预期 |

## 5. 遗留（值得单独排期）

`SortSupport.isAsc(null) → true`（"不传就升序"）是全局默认，本项目**所有列表**都受它影响。目前至少有两类入口顺序相反（后台管理 vs 用户收件箱）。建议单独开一 CR：要么统一"默认降序 + 前端可传 asc"，要么在每个列表的 fetcher 里显式声明排序。这属跨模块行为变更，需逐页确认后再动。