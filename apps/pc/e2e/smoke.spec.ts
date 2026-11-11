import { expect, test, type Page } from '@playwright/test';

/**
 * 主链路冒烟（ADR-006 的第 2 步）：只保 3–5 条，覆盖此前靠人工 CDP 验证的场景。
 * 前置：后端在 8080 运行、数据库已初始化（Flyway 自动），前端由 playwright.config 的 webServer 启动。
 */
const API = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080/api/v1';
const ADMIN = { username: 'admin', password: 'admin123' };
/** 无 system:user:list 的普通员工账号，用于权限与数据范围用例 */
const EMPLOYEE = { username: 'dev1', password: 'admin123' };

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('请输入账号').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.locator('button.login-submit').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
}

/** 用页面内同源请求读接口（自动带上 HttpOnly Cookie），断言"接口口径"而不是抓页面文案 */
async function apiTotal(page: Page, path: string): Promise<number> {
  return page.evaluate(async (p) => {
    const response = await fetch(p, { credentials: 'include' });
    const body = await response.json();
    return body?.data?.total as number;
  }, path);
}

test.describe('主链路冒烟', () => {
  test.beforeAll(async ({ request }) => {
    const health = await request.get(`${API}/health`).catch(() => null);
    test.skip(
      !health || health.status() !== 200,
      `后端未就绪（${API}/health 不可用）。请先启动后端：cd server && mvn -B -DskipTests spring-boot:run`,
    );
  });

  test('未登录访问受保护页面 → 跳登录页', async ({ page }) => {
    await page.goto('/system/users');
    await expect(page).toHaveURL(/\/login/);
  });

  test('登录后进入工作台；刷新页面仍保持登录（回归：access 过期靠静默刷新）', async ({ page }) => {
    await login(page, ADMIN.username, ADMIN.password);
    await expect(page.locator('.welcome-title')).toContainText('你好');
    await expect(page.locator('.sidebar-menu')).toBeVisible();

    await page.reload();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.sidebar-menu')).toBeVisible();
  });

  test('无功能权限的账号访问用户管理 → 不可见列表，落到状态页', async ({ page }) => {
    await login(page, EMPLOYEE.username, EMPLOYEE.password);
    await page.goto('/system/users');

    // 现状：动态路由按登录时下发的菜单注册，未授权路由根本不注册 → 落到通配 404 状态页；
    // 403 页只在"路由已注册但 meta.permission 不满足"时出现（几乎不可达）。
    // 因此这里断言的是可验证的对外行为：没有用户列表、显示状态页，而不是断言某个具体状态码页。
    await expect(page.locator('.state-block')).toBeVisible();
    await expect(page.locator('.el-table')).toHaveCount(0);
  });

  test('数据权限：普通员工能看到的示例任务少于管理员', async ({ page }) => {
    await login(page, ADMIN.username, ADMIN.password);
    const adminTotal = await apiTotal(page, '/api/v1/demo-tasks?page=1&pageSize=1');

    await login(page, EMPLOYEE.username, EMPLOYEE.password);
    const employeeTotal = await apiTotal(page, '/api/v1/demo-tasks?page=1&pageSize=1');

    expect(adminTotal).toBeGreaterThan(0);
    expect(employeeTotal).toBeLessThan(adminTotal);
  });
});
