import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * 生成器产出验收（`tools/gen-module`，CR-20261114-041）。
 *
 * <p>为什么单独一条 E2E：生成器的价值不在"生成了文件"，而在"生成出来的东西真的能用"。
 * 单测与编译只能证明语法正确；菜单是否挂上、权限码是否生效、列表-新增-删除链路是否通，
 * 只有点一遍才知道。这条用例就是生成物的"出厂验收"。</p>
 *
 * <p>数据自清理：新增的记录在用例末尾删除，重复执行不会积累脏数据。</p>
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8082/api/v1';

test.describe('生成器产出 · 客户档案模块', () => {
  test('菜单可见、列表可开、新增与删除走通', async ({ page }) => {
    const health = await page.request.get(`${API}/health`).catch(() => null);
    test.skip(!health || health.status() !== 200, `后端未就绪（${API}/health 不可用）`);

    await loginAsAdmin(page);

    // 菜单由 Flyway V20 写入，前端路由由菜单驱动 —— 侧边栏应出现「客户档案」
    await expect(page.getByRole('menuitem', { name: '客户档案' })).toBeVisible({ timeout: 15_000 });
    await page.goto('/demo/customers');

    const unique = `生成器验收-${Date.now()}`;
    await expect(page.getByRole('button', { name: '新增客户档案' })).toBeVisible({ timeout: 15_000 });

    // 新增
    await page.getByRole('button', { name: '新增客户档案' }).click();
    const drawer = page.locator('.el-drawer');
    await expect(drawer).toBeVisible();
    await drawer.getByLabel('客户名称').fill(unique);
    await drawer.getByRole('button', { name: '保存' }).click();
    await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 15_000 });

    // 列表里能查到（关键词搜索走的是生成的关键词字段）
    await page.locator('.filter-keyword input').fill(unique);
    await page.getByRole('button', { name: '查询' }).click();
    const row = page.locator('.el-table__row', { hasText: unique });
    await expect(row).toBeVisible({ timeout: 15_000 });

    // 删除（清理数据）：删除确认框的确认键文案是「删除」（confirmDelete 的默认值），不是「确定」
    await row.getByRole('button', { name: '删除' }).click();
    const box = page.locator('.el-message-box');
    await expect(box).toBeVisible();
    await box.getByRole('button', { name: '删除' }).click();
    await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.el-table__row', { hasText: unique })).toHaveCount(0);
  });
});
