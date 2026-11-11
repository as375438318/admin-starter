## 这个 PR 做了什么

<!-- 一句话说明改动与动机；关联的 CR 编号（docs/audit/CR-*.md） -->

- 关联 CR：
- 变更档位（A/B/C/D，见 `AGENTS.md` §4.1）：

## 自检清单（合并前逐项确认）

### 门禁

- [ ] `node tools/spec-lint/cli.mjs` → error 0
- [ ] 后端：`cd server && mvn -B -pl web -am verify` 通过
- [ ] 前端（改动了 `apps/` 或 `packages/`）：`pnpm --filter @admin-starter/pc test:unit` 与 `pnpm build` 通过
- [ ] 涉及页面/接口链路：`pnpm --filter @admin-starter/pc test:e2e`（需后端在跑）

### 文档回填（`AGENTS.md` §6.2，MUST）

- [ ] `docs/audit/impl-notes.md` 已记录
- [ ] 涉接口 → `docs/api/openapi.yaml` 已同步（并按需重新导出 `openapi.generated.yaml`）
- [ ] 涉表/字段 → `docs/design/data-model.md`
- [ ] 涉权限码 → `docs/design/permission-matrix.md`
- [ ] 涉新模块/表/接口前缀 → `docs/baseline/modules.md`
- [ ] 出现"改错就出事"的新事实 → `docs/baseline/assertions.md`
- [ ] 涉新配置项 → `docs/design/config-registry.yaml`

### 代码质量

- [ ] 未新增静默 catch、未放宽校验、未注释/跳过测试（`AGENTS.md` §7 禁止作弊）
- [ ] 新增测试覆盖了"改错就出事"的分支，而不是只覆盖 happy path
- [ ] 契约变更写进 `docs/api/openapi.yaml` 的变更记录（`CT-VER-005`）并升版本号

## 需要人工确认的点

<!-- AGENTS.md §7：业务语义命名、事务边界、权限模型、交互是否符合业务习惯；无则写「无」 -->
