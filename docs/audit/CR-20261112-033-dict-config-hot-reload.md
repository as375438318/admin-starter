# CR-20261112-033 字典/参数热更新（改完不重启即生效，并让"要不要重启"可见）

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12「字典参数热更新做」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **B（模块级）**：单模块（system）+ 前端参数页，纯新增字段与缓存命名空间，向后兼容；未命中 `PRC-APR-001` |
| 关联 | `CR-20261112-024`（口令策略，本次把它的 `STARTUP` 语义显式化）；断言 #98 |

## 1. 先查清现状：哪些已经"热"，哪些其实不是

| 对象 | 改动前 | 结论 |
|---|---|---|
| 字典类型/字典项 | 后端**无缓存**，每次查库；前端也无字典级缓存 | 本来就热，但**每个请求都查库**（读多写少的典型可优化点） |
| 公开配置（`sys.*` 白名单） | 缓存 10 分钟 + 写操作 `evict()` | 已热，无需改 |
| 参数管理里的 `app.*` 键（口令策略、令牌、文件上传等） | 由 `@ConfigurationProperties` **启动时绑定**，改库不生效 | **改库无效但界面毫无提示**——用户以为改了就生效 |
| `sys.upload.max-size` | 运行期读 + 缓存 + 校验 | 已热 |

真正缺的两件事：**① 字典每请求查库**；**② "改完要不要重启"在界面上不可见**。

## 2. 改动

### 2.1 字典项加缓存（写操作立即失效）

- 新增缓存命名空间 `dict`（`admin:dict:v1:`，出现在缓存管理页，可手动清理）。
- `items(typeId)` 与 `bundles(types)` 读缓存，TTL 60 秒（兜底：多实例部署时别的实例改库也能自愈）。
- 所有字典写操作（类型与项的增/改/删，共 6 处）后 `evictDictCache()`。

**失效用「代际键」而不是前缀删除**：`CacheService` 只有 get/put/delete，前缀删除是 Redis 专属能力（`CacheAdminRepository` 才有）。缓存键带上代际号，失效只需动一个键，旧代际自然不可达。

> ⚠️ 第一版写的是 `delete(GENERATION_KEY)`，被集成测试当场抓到：删除计数器会让下一次自增**重新回到同一个值**，于是命中旧缓存（"新增字典项后 items 仍返回空列表"）。**必须递增**。这条已写进代码注释与断言 #98。

### 2.2 参数生效方式显式化

- 新增 `ConfigEffects`：**唯一判定处** —— `app.*` 前缀 → `STARTUP`（启动期绑定，改库无效，必须重启）；其余（`sys.*`）→ `HOT`（运行期读库，改完即生效）。
- `ConfigDtos.Response` 新增 `effect`（`HOT`/`STARTUP`）与 `hotReloadable`（布尔，便于前端直接绑定）。
- 参数管理页新增「生效方式」列：`立即生效`（绿）/ `需重启`（橙），带 tooltip 解释。

### 2.3 防漂移：测试对齐台账

`ConfigEffectsTest.matchesConfigRegistry` 会**解析 `docs/design/config-registry.yaml`**，逐条比对台账里的 `effect` 与代码判定。台账新增键写错 `effect`、或者代码判定被改坏，测试立刻红——否则前端会对着 6 个"需重启"的键显示"立即生效"。

## 3. 实测（dev 库，未重启进程）

| 项 | 结果 |
|---|---|
| 参数列表 | 5 个 `sys.*` 键全部 `effect=HOT`、`hotReloadable=true` |
| 改 `sys.name` → 立即读 `public/configs` | `后台管理系统` → `热更新验证中` →（改回）`后台管理系统`，全程未重启 |
| 字典项新增/改名 → 立即读 `items` | 集成测试覆盖（`DictConfigHotReloadTest`） |

## 4. 测试

| 用例 | 验证点 |
|---|---|
| `ConfigEffectsTest.effectByPrefix` | `app.*`→STARTUP、`sys.*`→HOT、空值→HOT |
| `ConfigEffectsTest.matchesConfigRegistry` | **台账漂移校验**（解析 YAML 逐条比对） |
| `DictConfigHotReloadTest.dictItemChangeVisibleImmediately` | 先读（填缓存）→ 新增项 → **立即**读到 1 条 → 改名 → 立即读到新值 |
| `DictConfigHotReloadTest.configRowsCarryEffect` | 每行都有 `effect`/`hotReloadable` 且与键前缀一致 |

## 5. 已知限制（如实记录）

1. **生成的 as-is 基线没体现这些字段**：`docs/api/openapi.generated.yaml` 里 `configKey` 出现 **0 次**——springdoc 对多个同名嵌套 DTO（都叫 `Response`）做了去重，`/api/v1/configs` 的行结构在生成物里不完整。这是既有缺陷，不是本次引入；本次以**集成测试**为准，未在生成物上假装覆盖。要彻底解决需给嵌套 DTO 加 `@Schema(name=...)`，属独立改造。
2. **多实例部署**：代际键与 TTL 都在缓存层（默认内存实现）。若切 Redis，多实例共享代际号；若用内存实现且多实例，别的实例的改库最多滞后 TTL（60 秒）。
3. **字典缓存没有「手动刷新」按钮**：走缓存管理页清理 `dict` 命名空间即可（已列为可清理命名空间）。

## 6. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **211 用例通过**（common 26 / system 159 / web 26） |
| 前端 | build + `test:unit` | 通过 / 97 通过 |
| E2E | `pnpm exec playwright test` | **8/8 通过** |
| 契约导出 | `export-openapi.ps1 -Password ...` | 已重新导出 |
| 文档门禁 | `spec-lint` | error 0 |