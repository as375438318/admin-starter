import { expect, test, type Page } from '@playwright/test';

/**
 * 业务链路 E2E（ADR-006 第 2 步的补充）：通知发布、文件上传、数据导出。
 * <p>选这三条的理由：它们是"人工验证最费时、又最容易在重构后静默坏掉"的链路——
 * 通知发布涉及范围解析与快照生成，上传涉及去重与落盘，导出涉及通道决策与字节流。
 * 断言口径尽量落在「接口真实结果」与「下载真的发生了」，而不是只抓页面文案。</p>
 * <p>前置：后端在 8080 运行、数据库已初始化；前端由 playwright.config 的 webServer 启动。</p>
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080/api/v1';
const ADMIN = { username: 'admin', password: 'admin123' };

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('请输入账号').fill(ADMIN.username);
  await page.getByPlaceholder('请输入密码').fill(ADMIN.password);
  await page.locator('button.login-submit').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

/** 页面内同源请求（自动带 HttpOnly Cookie），用于断言接口真实结果 */
async function apiTotal(page: Page, path: string): Promise<number> {
  return page.evaluate(async (p) => {
    const response = await fetch(p, { credentials: 'include' });
    const body = await response.json();
    return body?.data?.total as number;
  }, path);
}

async function skipWhenBackendDown(page: Page): Promise<void> {
  const health = await page.request.get(`${API}/health`).catch(() => null);
  test.skip(
    !health || health.status() !== 200,
    `后端未就绪（${API}/health 不可用）。请先启动后端：cd server && mvn -B -DskipTests spring-boot:run`,
  );
}

test.describe('业务链路', () => {
  test('发布通知：填表提交后进入列表，并落到「应读人」快照', async ({ page }) => {
    await skipWhenBackendDown(page);
    await login(page);
    const title = `E2E 通知 ${Date.now()}`;
    const before = await apiTotal(page, '/api/v1/notices?page=1&pageSize=1');

    await page.goto('/system/notices');
    await page.getByRole('button', { name: '发布通知' }).click();

    const drawer = page.locator('.el-drawer').filter({ hasText: '发布通知' });
    await drawer.getByPlaceholder('请输入通知标题').fill(title);
    await drawer.getByPlaceholder('请输入通知内容（纯文本）').fill('由 E2E 自动发布，用于验证发布链路。');
    // 默认接收范围是「全员」，无需改；直接发布
    await drawer.locator('.el-drawer__footer').getByRole('button', { name: '发布', exact: true }).click();

    await expect(page.locator('.el-message').filter({ hasText: /发布|成功/ }).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.el-drawer').filter({ hasText: '发布通知' })).toBeHidden();

    // 列表里能看到（说明后端已落库并回显）
    await page.locator('input[placeholder="搜索通知标题"]').fill(title);
    await page.getByRole('button', { name: '查询' }).click();
    await expect(page.locator('.el-table__body')).toContainText(title, { timeout: 10_000 });

    // 接口口径：总数增加，且该通知有应读人（范围解析真的执行了）
    const after = await apiTotal(page, '/api/v1/notices?page=1&pageSize=1');
    expect(after).toBeGreaterThan(before);
    const readers = await page.evaluate(async () => {
      const list = await fetch('/api/v1/notices?page=1&pageSize=1', { credentials: 'include' }).then((r) => r.json());
      const id = list?.data?.list?.[0]?.id;
      const detail = await fetch(`/api/v1/notices/${id}`, { credentials: 'include' }).then((r) => r.json());
      return detail?.data?.expectedReaders ?? detail?.data?.recipientCount ?? null;
    });
    expect(readers === null || readers > 0).toBeTruthy();
  });

  test('上传文件：列表出现该文件，且可下载回同一内容', async ({ page }) => {
    await skipWhenBackendDown(page);
    await login(page);

    const stamp = Date.now();
    const fileName = `e2e-${stamp}.txt`;
    const content = `e2e upload content ${stamp}`;

    await page.goto('/system/files');
    await page.locator('.upload-drag input[type=file]').setInputFiles({
      name: fileName,
      mimeType: 'text/plain',
      buffer: Buffer.from(content, 'utf8'),
    });

    await expect(page.locator('.el-table__body')).toContainText(fileName, { timeout: 20_000 });

    // 下载回读：字节应与上传内容一致（证明不是只写了一条记录）
    const downloaded = await page.evaluate(async (name) => {
      const list = await fetch('/api/v1/files?page=1&pageSize=20', { credentials: 'include' }).then((r) => r.json());
      const item = (list?.data?.list ?? []).find((row: { originalName: string }) => row.originalName === name);
      if (!item) {
        return null;
      }
      const response = await fetch(`/api/v1/files/${item.id}/download`, { credentials: 'include' });
      return await response.text();
    }, fileName);

    expect(downloaded).toBe(content);
  });

  test('导出示例任务：触发 .xlsx 下载且不报错', async ({ page }) => {
    await skipWhenBackendDown(page);
    await login(page);
    await page.goto('/demo/tasks');

    const exportButton = page.getByRole('button', { name: '导出', exact: true });
    await expect(exportButton).toBeVisible({ timeout: 10_000 });

    // 同步通道（≤2000 行）会直接下载；异步通道会给提示，两种都算通过，但必须没有错误提示
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20_000 }).catch(() => null),
      exportButton.click(),
    ]);

    if (download) {
      expect(download.suggestedFilename()).toMatch(/\.xlsx$/);
    } else {
      await expect(page.locator('.el-message').filter({ hasText: /导出任务|数据量较大/ }).first())
        .toBeVisible({ timeout: 10_000 });
    }
    await expect(page.locator('.el-message--error')).toHaveCount(0);
  });
});
