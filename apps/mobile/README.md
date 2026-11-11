# apps/mobile（占位）

按 `FE-DIR-001` 保留的跨端应用目录。**本仓库当前只交付 PC 端**，此目录不含代码，避免出现无法运行的壳工程。

新建移动端时：

1. 按 `AGENTS.md` §9-B 选定的跨端路线（`uni-app` 或 `Taro`，二选一）在此初始化工程。
2. 只允许一条跨端路线（`FE-STACK-002`），不得同时引入另一套跨端框架。
3. 复用 `apps/shared` 的类型与 `packages/ui-tokens` 的设计令牌；平台差异实现放本应用内（`apps/mobile/src/utils`），不进共享包（`FE-DIR-004`）。
4. 搬入代码后同步更新 `README.md` 的启动命令与 CI。
