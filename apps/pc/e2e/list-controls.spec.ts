import { expect, test, type Page } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * 列表通用能力 E2E：点表头排序 + 列显示控制（`use-table-columns`）。
 *
 * <p>为什么值得单独一条：这两件事都是"错了也不报错"的类型——
 * 排序 `sortKey` 写错时后端会静默回落默认排序（页面看着正常，只是点了没反应）；
 * 列偏好写坏时 localStorage 里会残留脏数据，表现为"某列怎么点都不出来"。
 * 因此断言落在「请求真的带上了 sort/order」与「刷新后显隐仍保持」。</p>
 *
 * <p>前置：后端在 8080 运行；前端由 playwright.config 的 webServer 启动。</p>
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080/api/v1';

async function login(page: Page): Promise<void> {
  await loginAsAdmin(page);
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

async function skipWhenBackendDown(page: Page): Promise<void> {
  const health = await page.request.get(`${API}/health`).catch(() => null);
  test.skip(!health || health.status() !== 200, `后端未就绪（${API}/health 不可用）`);
}

test.describe('列表通用能力', () => {
  test('用户列表：点表头带上排序参数，切换方向回到第 1 页', async ({ page }) => {
    await skipWhenBackendDown(page);
    await login(page);
    await page.goto('/system/users');
    await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 });

    // 记录列表请求：断言排序参数真的发出去了（而不是只改了图标）
    const listRequests: string[] = [];
    page.on('request', (request) => {
      const url = request.url();
      if (url.includes('/api/v1/users') && request.method() === 'GET') {
        listRequests.push(url);
      }
    });

    // 先翻到第 2 页（若总数不足一页则跳过该步），再点表头应回到第 1 页
    const pager = page.locator('.el-pager li', { hasText: '2' });
    if (await pager.count()) {
      await pager.first().click();
      await page.waitForTimeout(300);
    }

    await page.locator('.el-table__header th', { hasText: '账号' }).first().click();
    await expect
      .poll(() => listRequests.some((url) => url.includes('sort=username') && url.includes('order=asc')), {
        timeout: 10_000,
      })
      .toBe(true);
    expect(listRequests.at(-1)).toContain('page=1');

    // 再点一次 → 降序
    await page.locator('.el-table__header th', { hasText: '账号' }).first().click();
    await expect
      .poll(() => listRequests.some((url) => url.includes('sort=username') && url.includes('order=desc')), {
        timeout: 10_000,
      })
      .toBe(true);

    // 第三次点 → 取消排序，不再带 sort 参数（回到后端默认排序）
    await page.locator('.el-table__header th', { hasText: '账号' }).first().click();
    await expect
      .poll(() => {
        const last = listRequests.at(-1);
        return Boolean(last) && !last.includes('sort=');
      }, { timeout: 10_000 })
      .toBe(true);
  });

  test('用户列表：列设置勾选后隐藏列，刷新仍保持', async ({ page }) => {
    await skipWhenBackendDown(page);
    await login(page);
    await page.goto('/system/users');
    await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 });

    // 初始可见 5 个数据列表头（姓名/账号/部门/角色/状态/创建时间 中，创建时间默认可见）
    const createdAtHeader = page.locator('.el-table__header th', { hasText: '创建时间' });
    await expect(createdAtHeader).toHaveCount(1);

    await page.locator('.column-settings-trigger').click();
    await page.locator('.el-checkbox', { hasText: '创建时间' }).click();
    await expect(page.locator('.el-table__header th', { hasText: '创建时间' })).toHaveCount(0);

    // 刷新后仍隐藏：列偏好已写入 localStorage
    await page.reload();
    await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.el-table__header th', { hasText: '创建时间' })).toHaveCount(0);

    // 恢复默认后又出现
    await page.locator('.column-settings-trigger').click();
    await page.getByRole('button', { name: '恢复默认' }).click();
    await expect(page.locator('.el-table__header th', { hasText: '创建时间' })).toHaveCount(1);
  });
});
