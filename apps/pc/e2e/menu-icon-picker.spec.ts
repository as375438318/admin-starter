import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * 菜单管理的图标选择器（`IconPicker`）。
 *
 * <p>为什么值得 E2E：图标是**跨组件生效**的——保存后由侧边栏的
 * `<component :is="item.meta.icon" />` 渲染。图标名拼错时 Vue 不报错、只是侧边栏空白，
 * 所以这里断言"选出来的名字能落到输入框里"，并避免真的保存改动数据库（最后取消）。</p>
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8082/api/v1';

test.describe('菜单管理 · 图标选择器', () => {
  test('点图标弹出面板，搜索并选中后回填到字段', async ({ page }) => {
    const health = await page.request.get(`${API}/health`).catch(() => null);
    test.skip(!health || health.status() !== 200, `后端未就绪（${API}/health 不可用）`);

    await loginAsAdmin(page);
    await page.goto('/system/menus');
    await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 });

    // 打开任一菜单的编辑抽屉
    await page.getByRole('button', { name: '编辑' }).first().click();
    const drawer = page.locator('.el-drawer');
    await expect(drawer).toBeVisible({ timeout: 10_000 });

    // 图标触发器（只读输入框）：点击弹出图标网格
    const trigger = drawer.locator('.icon-picker-trigger input');
    await expect(trigger).toBeVisible();
    await trigger.click();
    await expect(page.locator('.icon-grid-item').first()).toBeVisible({ timeout: 10_000 });

    // 搜索后只剩匹配项，选中第一个 → 输入框回填该图标名
    await page.locator('.icon-picker-search input').fill('setting');
    const items = page.locator('.icon-grid-item');
    await expect.poll(async () => items.count()).toBeGreaterThan(0);
    const picked = await items.first().getAttribute('title');
    await items.first().click();

    await expect(trigger).toHaveValue(String(picked));
    // el-popover 默认 persistent：内容仍留在 DOM 里，只是隐藏 —— 断言可见性而不是数量
    await expect(page.locator('.icon-grid')).toBeHidden();

    // 不保存，避免污染数据库
    await drawer.getByRole('button', { name: '取消' }).click();
  });
});
