# 设计 · 列表通用排序 + 列显示控制 + 按勾选列导出

| 项目 | 内容 |
|---|---|
| 版本 | **APPROVED v1.0**（2026-11-12） |
| 提出人 | 用户 2026-11-12：「列表加点表头排序，应该是通用的一个方法」「每个列表可勾选显示哪些列，导出按勾选的字段来」 |
| 状态 | **APPROVED**（用户 2026-11-12「都按你的建议」确认 §8 的 D1–D6） |
| 预期档位 | P1/P2 落 **B**（多页面 + 新通用组件，不改接口字段）；P3 落 **B**（导出接口新增可选 `fields`，纯新增向后兼容） |
| 关联 | `AGENTS.md` v1.7 §4.1「跨模块行为变更的归口」；`CR-20261112-032`（后端排序已统一）；`CR-20261112-027/034`（契约测试） |

## 1. 目标

1. **一套通用机制**让任意列表页支持「点表头排序」，页面只需声明列，不各自写排序逻辑。
2. **每个列表可勾选显示哪些列**，选择在本地持久化（刷新不丢）。
3. **导出按勾选的列裁剪**——勾了哪些列，导出文件就只有哪些列（含同步导出与异步导出任务两条通道）。

## 2. 现状盘点（先说清已有的，避免重复造）

| 事实 | 证据 | 对设计的影响 |
|---|---|---|
| 后端**所有分页接口已支持** `?sort=&order=` | `PageQuery` 内置两字段 | 排序**不需要改接口契约** |
| 9 个仓储有 `SORTS` 白名单，字段名是 camelCase（`createdAt`/`title`/`status`/`name`/`code`/`sort`/`username`/`nickname`/`elapsedMs`/`nextRunAt`） | 各 `*RepositoryImpl` | 前端表头 `prop` 与白名单**不是一一对应**（如 `deptName`、`roleNames` 不在白名单）→ 必须显式声明可排序键 |
| 7 处列表**排序写死在 SQL/仓储**（文件、回收站、通知收件箱、会话、导出任务、登录日志、字典项） | `FileMapper`、`NoticeMapper`、`UserSessionMapper`、`ExportTaskRepositoryImpl` 等 | 这些页面**点表头不会有反应**，要么补白名单、要么明确标"不可排序" |
| 默认排序已在 `CR-20261112-032` 统一（流水类 DESC、参考数据按 `sort` ASC），且都有同方向 `id` 次级键 | 断言 #97 | 排序机制稳定，前端只需切换方向 |
| 前端 `usePagedList` 已统一分页/筛选/加载态，但**不含排序** | `apps/pc/src/composables/use-paged-list.ts` | 排序作为它的扩展点，不新造骨架 |
| 前端 **16 个页面**用了 `el-table`，**没有任何一处**用过 `sortable` / `sort-change` | 全仓搜索 0 命中 | 从零统一，没有历史包袱 |
| 导出是**服务端**生成 xlsx，列由 `ExcelSchema.exportFields()` 决定，且已按字段级脱敏过滤（`visibleColumns`） | `ExcelExportWriter.visibleColumns` | 导出裁剪**必须后端支持**；且 `fields` 只能**减少**列，不能绕过脱敏 |
| 异步导出任务表已有 `query_params`（JSON/2000 字符）存过滤条件快照 | `sys_export_task`（V15） | 勾选列可随任务一起快照，**无需新表** |

## 3. 方案总览

```
                     ┌──────────────────────────────────────────┐
  页面只写列定义  →   │ useTableColumns（列模型 + 显示控制 + 持久化）│
                     │   ↳ 可见列 → el-table                    │
                     │   ↳ 可排序列 → 点表头 → sort/order        │
                     └───────────────┬──────────────────────────┘
                                     │ usePagedList 扩展：把 sort/order 并入请求
                                     ▼
                     GET /api/v1/xxx?sort=createdAt&order=desc&page=1
                                     │
                     导出：GET /api/v1/xxx/export?...&fields=title,status,dept
                                     ▼
                     ExcelSchema.exportFields() ∩ fields ∩ 脱敏可见 → xlsx
```

三层职责：

| 层 | 新增/改动 | 说明 |
|---|---|---|
| 前端通用层 | 新增 `useTableColumns`；`usePagedList` 加 `sort` 支持 | 一套机制覆盖 16 个页面 |
| 后端导出 | 两个导出接口加可选 `fields`；`ExcelExportWriter` 按白名单裁剪 | 唯一需要改接口的地方 |
| 契约/文档 | 契约小版本（1.1.1 → 1.2.0）；断言回填 | 纯新增可选参数，向后兼容 |

## 4. 前端设计

### 4.1 列模型（唯一的列声明处）

页面不再直接写 `<el-table-column>`，而是声明列对象：

```ts
// 页面里：一份列定义，同时驱动「表格渲染 / 显示控制 / 排序 / 导出字段」
const columns = defineColumns<UserItem>([
  { key: 'nickname', label: '姓名', width: 170, fixed: 'left', sortKey: 'nickname' },
  { key: 'username', label: '账号', width: 130, sortKey: 'username' },
  { key: 'deptName', label: '部门', width: 150 },                    // 无 sortKey → 不可排序
  { key: 'roles',    label: '角色', width: 180, render: 'tags' },
  { key: 'isEnabled',label: '状态', width: 90,  render: 'tag', sortKey: 'id' },
  { key: 'createdAt',label: '创建时间', width: 170, sortKey: 'createdAt', defaultVisible: false },
  { key: 'ops',      label: '操作', width: 200, fixed: 'right', alwaysVisible: true, exportable: false },
]);
```

字段含义：

| 字段 | 作用 | 备注 |
|---|---|---|
| `key` | 列标识 + **导出字段名**（与后端 `ExcelField.key` 对齐） | 前端 `fields` 参数就是这些 key |
| `label` / `width` / `fixed` / `render` | 渲染 | `render` 为表格渲染类型，避免每页写 `#default` |
| `sortKey` | **后端 `sort` 白名单字段名**；缺省 = 该列不可排序 | 关键设计：不靠 `prop` 猜，避免"点了没反应" |
| `defaultVisible` | 初始是否显示 | 缺省 `true` |
| `alwaysVisible` | 不可取消勾选（如操作列、主键列） | 缺省 `false` |
| `exportable` | 是否参与导出勾选 | 缺省 `true`；操作列必须 `false` |
| `permission` | 该列需要的权限码（可选） | 无权限则**不渲染也不导出** |

### 4.2 `useTableColumns(columns, { storageKey })`

返回：

```ts
{ visibleColumns, hiddenCount, sortKey, sortOrder, onSortChange, exportedFields, resetColumns, toggleColumn }
```

行为约定：

1. **勾选面板**：表格右上角「列设置」按钮 → `el-popover` 内勾选列表（`alwaysVisible` 禁用勾选）。勾选变化立即生效。
2. **持久化**：`localStorage` 存 `{hidden:[...], order?}`，键为 `list-columns:{storageKey}`（如 `system-user`）。**只存 key，不存列定义**——列定义改动后老数据自动失效无副作用。
3. **排序交互**：点表头 → 升序 → 降序 → 取消（恢复默认排序）。切换排序时**重置到第 1 页**（否则用户会看到"第 3 页的空结果"）。
4. **防误用**：`sortKey` 不在后端白名单时前端无法感知（白名单在后端），因此约定**后端白名单字段名由页面作者显式写**；提供 `sortWhitelist` 常量文件按模块集中维护，页面从这里引用，避免手写错拼。
5. **无权限列**：`permission` 不满足 → 从 `visibleColumns` 与 `exportedFields` 中双重剔除（不从 UI 藏一下了事）。

### 4.3 `usePagedList` 扩展（向后兼容）

```ts
usePagedList({
  initialFilters, fetcher,
  // 新增可选：
  defaultSort: { sort: 'createdAt', order: 'desc' },  // 不传则用后端默认（CR-032 已统一）
})
// 返回值新增：sort, order, onSortChange, resetSort
```

`usePagedList` 把它并入请求参数；`onSortChange` 由 `useTableColumns` 的排序事件驱动。**未改造的页面完全不受影响**。

### 4.4 导出调用

```ts
// 同步导出（≤2000 行）
await exportApi.download({ ...filters, sort, order, fields: exportedFields.value.join(',' ) });

// 异步导出任务
await exportApi.createTask({ ...filters, sort, order, fields: exportedFields.value.join(',') });
```

`exportedFields` 为空（用户把可导出列全关了）→ **前端拦截并提示**「至少保留一列可导出字段」，不发请求（比让后端报错体验更好）。

## 5. 后端设计

### 5.1 导出接口新增可选 `fields`

| 接口 | 现状参数 | 变更 |
|---|---|---|
| `GET /api/v1/demo-tasks/export`（同步/异步自适应） | `keyword,status,deptId,sort,order` | **+`fields`** |
| `GET /api/v1/posts/export` | `keyword,deptId,isEnabled,sort,order` | **+`fields`** |

- 语义：`fields` 为逗号分隔的列 key 白名单；**空/缺省 = 现状**（`exportFields()` 全量）。
- **只能减少，不能增加**：最终列 = `exportFields()` ∩ `fields` ∩ 脱敏可见性（`visibleColumns` 现有逻辑不变）。
- **顺序以 schema 为准**，不按 `fields` 顺序——避免不同用户导出列序不一致。
- 非法 key（含未知 key / 全被过滤后为空）→ **400 + 10001**，message 指出未知 key。**不新增错误码**。

### 5.2 实现落点

| 文件 | 改动 |
|---|---|
| `ExcelExportWriter.visibleColumns(schema)` → `visibleColumns(schema, fieldKeys)` | 增加 key 过滤，保留脱敏过滤 |
| `ExcelExportPlanner.plan(...)` | 透传 `fieldKeys` |
| `ExportTaskService` / 导出 AppService | 把 `fields` 写进 `query_params` 快照；异步执行时读回 |
| 两个 Controller | 各加一个 `@RequestParam(required=false) String fields` |

### 5.3 排序侧：补白名单（P1 的一小部分）

下列列表当前**写死排序**，点表头不会生效。二选一，按页面价值决定：

| 列表 | 现状 | 建议 |
|---|---|---|
| 文件 / 回收站 | `orderByDesc(created_at, id)` | 补 `SORTS`（`createdAt`/`originalName`/`size`） |
| 会话 | `last_active_at desc` | 补 `SORTS`（`lastActiveAt`/`loginAt`） |
| 通知收件箱 / 已读人 | SQL 写死 | **不补**（语义特殊：未读优先 + 已读时间），标"不可排序" |
| 导出任务 | `created_at desc` | 补 `SORTS`（`createdAt`/`status`） |
| 字典项 / 部门 / 菜单 | `sort asc, id asc` | **不补**（参考数据按人工排序号） |

## 6. 契约与文档产出（B 档 + 跨模块行为变更）

| 文档 | 动作 |
|---|---|
| `docs/api/openapi.yaml` | 两个导出接口加 `fields` 参数；版本 **1.1.1 → 1.2.0**；变更记录一条 |
| `docs/api/openapi.generated.yaml` | 重新导出 |
| `docs/baseline/assertions.md` | 新增 2 条：#101 列 key 与 `ExcelField.key` 必须对齐（否则导出裁剪静默丢列）；#102 `fields` 只能减少列、不得绕过脱敏 |
| `docs/audit/impl-notes.md` | 实现记录 |
| `docs/audit/CR-*.md` | 本次 CR（含**影响面清单**，逐条到接口/页面） |
| `frontend-spec` / `ui-spec` | **只读不改**：列设置交互属"通用组件约定"，写进本项目 CR 与组件注释，不上升规范（单项目特例） |

## 7. 影响面清单（按 `AGENTS.md` v1.7 §4.1 要求，列到接口/页面粒度）

**接口（2 个，纯新增可选参数）**

| 接口 | 变化 | 兼容性 |
|---|---|---|
| `GET /api/v1/demo-tasks/export` | +`fields` | 缺省行为不变 |
| `GET /api/v1/posts/export` | +`fields` | 缺省行为不变 |

**页面（16 个，分 3 批改造）**

| 批次 | 页面 | 说明 |
|---|---|---|
| 第 1 批（3 个，验证机制） | `system/user`、`system/log`、`demo/task` | 有排序白名单 + 有导出（示例任务）→ 覆盖两条链路 |
| 第 2 批（8 个） | `system/role`、`system/dept`、`system/post`、`system/dict`、`system/config`、`system/job`、`system/notice`、`system/file` | 逐个声明 `sortKey`，无白名单的列标不可排序 |
| 第 3 批（5 个） | `system/menu`、`system/session`、`system/cache`、`message`、`profile` | 多为树表/特殊表，仅接列显示控制，不接排序 |

**表 / 权限码 / 配置项**：均**无新增**（`sys_export_task.query_params` 复用；`system:*` 权限码不变；无新配置项）。

## 8. 决策记录（已确认，用户 2026-11-12 全部采纳建议）

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| D1 | 列偏好存哪 | (a) `localStorage` (b) 落库跨设备同步 | **(a) 已采纳** — 落库要新表新接口，收益与成本不匹配；先本地，真有需求再上 |
| D2 | 排序三态 | (a) 升→降→取消 (b) 两态 | **(a) 已采纳** — 用户需要回到"默认最新在前"的路径；由 el-table 自带 sort-orders 实现 |
| D3 | 导出列顺序 | (a) 按 schema 固定 (b) 按勾选顺序 | **(a) 已采纳** — 同一份数据不同人导出结构一致 |
| D4 | 不接排序的页面 | 收件箱、已读人、字典项、部门、菜单 | **已采纳** — 这些页面表头不加排序箭头，避免"点了没反应" |
| D5 | 列设置面板按钮 | (a) 全选/反选/恢复默认 (b) 只要勾选 | **(a) 已采纳** |
| D6 | 全关列时 | (a) 前端拦截提示 (b) 后端 400 | **(a) 已采纳**（P3 实现） |

## 9. 实施分期（确认后按此推进，每期一个 PR）

| 期 | 内容 | 验证 |
|---|---|---|
| **P1** | `useTableColumns`（含排序+列显隐+持久化）+ `usePagedList` 扩展 + 第 1 批 3 个页面 + 补 3 处后端白名单 | 单元测试（composable）+ E2E：点表头三次循环、切换排序回第 1 页、刷新后列显隐保持 |
| **P2** | 第 2/3 批页面接入（13 个） | E2E 抽查 2 个页面；人工点检树表 |
| **P3** | 后端 `fields` + 契约 1.2.0 + 前端导出带 `fields` | 后端单测（裁剪/非法 key/脱敏不可绕过）+ 契约测试 + E2E 导出比对列数 |

## 10. 风险

| 风险 | 处置 |
|---|---|
| `sortKey` 写错 → 点表头静默无效（后端白名单回落默认列） | 集中维护 `sortWhitelist` 常量；P1 加一条**前端单测**断言每个 `sortKey` 都在白名单常量里 |
| `fields` 的 key 与后端 `ExcelField.key` 不一致 → 导出静默少列 | 断言 #101 + P3 加集成测试：断言 `fields=title,status` 导出的表头恰为两列 |
| 16 个页面改造量大、易回归 | 分 3 批，每批独立 PR；E2E 现有 8 条覆盖主链路 |
| 列显隐与"固定列（fixed）"冲突（隐藏左固定列会错位） | `alwaysVisible` 用于主键/操作列；隐藏固定列时同步移除 `fixed` |
| 后端 `query_params` 2000 字符上限 | `fields` 只有几十个 key，远低于上限；超长时截断并在日志告警（沿用现有截断策略） |

## 11. 明确不做

- 列**顺序拖拽**（成本高、与固定列/宽度协同复杂）
- 列**宽记忆**（Element Plus 已有，且与本次目标无关）
- 导出格式扩展（CSV / PDF）
- 列偏好**服务端同步**（见 D1）
- 通用"表格组件"重构（现在是 16 个页面各自写 `el-table`，全量抽象组件风险大于收益；本次只抽 composable + 列模型）
