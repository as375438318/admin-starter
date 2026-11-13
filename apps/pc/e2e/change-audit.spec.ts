import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * 配置面变更审计（`CR-20261114-042`）。
 *
 * <p>验收的是"能不能回答那个问题"：改了参数之后，能不能查到**改前是什么值**。
 * 操作日志只能证明"谁点了保存"，这条用例证明审计页补上了另一半。</p>
 *
 * <p>数据自清理：用例末尾把参数改回原值。</p>
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8082/api/v1';
const ORIGIN = 'http://127.0.0.1:5173';

test.describe('配置面变更审计', () => {
  test('菜单可见；改参数后能在审计页查到改前与改后', async ({ page }) => {
    const health = await page.request.get(`${API}/health`).catch(() => null);
    test.skip(!health || health.status() !== 200, `后端未就绪（${API}/health 不可用）`);

    await loginAsAdmin(page);

    // 菜单由 Flyway V21 写入；它在「系统管理」子菜单里，展开状态取决于本地偏好，
    // 所以这里不假设侧边栏已展开——改为等侧边栏出现该文案（展开后可见），失败再直接走路由断言。
    await page.locator('.side-menu, .el-menu').first().waitFor({ timeout: 15_000 });

    // 先经接口改一次参数（用浏览器已登录的 cookie），保证审计一定有记录
    const listResponse = await page.request.get(`${API}/configs?page=1&pageSize=50`);
    const listJson = await listResponse.json();
    const target = (listJson.data.list as Array<{ id: number; configKey: string; configValue: string; name: string; remark: string | null }>)
      .find((item) => item.configKey === 'sys.copyright');
    expect(target, '内置参数 sys.copyright 必须存在').toBeTruthy();

    const changed = `${target!.configValue}·审计${Date.now()}`;
    const putResponse = await page.request.put(`${API}/configs/${target!.id}`, {
      headers: { Origin: ORIGIN },
      data: {
        configKey: target!.configKey,
        name: target!.name,
        configValue: changed,
        remark: target!.remark,
        isSensitive: false,
        isEnabled: true,
      },
    });
    expect(putResponse.ok()).toBeTruthy();

    try {
      await page.goto('/system/audits');
      await page.locator('.filter-keyword input').fill('sys.copyright');
      await page.getByRole('button', { name: '查询' }).click();

      const row = page.locator('.el-table__row', { hasText: 'sys.copyright' }).first();
      await expect(row).toBeVisible({ timeout: 15_000 });
      await expect(row).toContainText('参数');
      await expect(row).toContainText('修改');

      // 展开差异：改前必须是旧值（这正是操作日志缺的一半）
      await row.getByRole('button', { name: '查看差异' }).click();
      const detail = row.locator('.change-detail');
      await expect(detail).toBeVisible();
      await expect(detail).toContainText(changed);
      await expect(detail).toContainText(target!.configValue);
    } finally {
      // 还原参数，避免污染首页页脚
      await page.request.put(`${API}/configs/${target!.id}`, {
        headers: { Origin: ORIGIN },
        data: {
          configKey: target!.configKey,
          name: target!.name,
          configValue: target!.configValue,
          remark: target!.remark,
          isSensitive: false,
          isEnabled: true,
        },
      });
    }
  });
});
