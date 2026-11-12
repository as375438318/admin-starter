import { expect, type Page } from '@playwright/test';

/**
 * E2E 登录助手（等保 P0-1 之后必须自适应）。
 *
 * 背景：`V17__password_policy.sql` 会把存量账号全部置为 `must_change_password = 1`，
 * 于是内置账号首次登录后会被**服务端拦在改密页**（业务接口返回 403 + 20302）。
 * 这既是真实用户体验，也是要验证的路径——但每条用例都手动处理会把用例写脏。
 *
 * 统一策略：
 * 1. 依次尝试候选口令（环境变量 > 已改过的强口令 > 初始口令）；
 * 2. 若落在改密页，用接口把口令改成该账号的固定强口令，然后重新登录；
 * 3. 之后的用例直接用强口令进系统。
 *
 * 这样**第一次跑**会体验一遍"被强制改密"，之后走已改密状态，两个状态都有覆盖。
 *
 * ⚠️ 强口令**不能含账号名**：策略里有一条「密码不能包含账号名」。
 * 第一版我把管理员口令写成 `Admin#Starter2026`（含 `admin`），改密接口直接 422 + 30202。
 */

/** 管理员改密后的固定强口令（含大小写/数字/特殊，且不含 `admin`）。 */
export const ADMIN_STRONG_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Starter#2026Pw';

/** 普通员工账号改密后的固定强口令。 */
export const EMPLOYEE_STRONG_PASSWORD = process.env.E2E_EMPLOYEE_PASSWORD ?? 'Staff#2026Pw9';

const INITIAL_PASSWORD = 'admin123';
const MUST_CHANGE_BANNER = '必须先修改登录密码';

type LoginOutcome = 'ok' | 'must-change' | 'failed';

async function tryLogin(
  page: Page,
  username: string,
  password: string,
): Promise<LoginOutcome> {
  await page.goto('/login');
  await page.getByPlaceholder('请输入账号').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.locator('button.login-submit').click();
  await page.waitForLoadState('networkidle');

  if (page.url().includes('/login')) {
    return 'failed';
  }
  if (page.url().includes('/profile') || (await page.getByText(MUST_CHANGE_BANNER).count()) > 0) {
    return 'must-change';
  }
  return 'ok';
}

/**
 * 以指定账号登录，并保证最终处于"可正常使用其他功能"的状态。
 * @returns 实际生效的口令（供需要重新登录的用例复用）
 */
export async function loginExpectingUsable(
  page: Page,
  username: string,
  strongPassword: string,
): Promise<string> {
  const candidates = Array.from(new Set([strongPassword, INITIAL_PASSWORD]));

  for (const password of candidates) {
    const outcome = await tryLogin(page, username, password);
    if (outcome === 'ok') {
      return password;
    }
    if (outcome === 'must-change') {
      const response = await page.request.put('/api/v1/profile/password', {
        data: { oldPassword: password, newPassword: strongPassword },
        headers: { Origin: new URL(page.url()).origin },
      });
      expect(response.ok(), `改密接口应成功，实际 ${response.status()}`).toBeTruthy();

      expect(await tryLogin(page, username, strongPassword), '改密后应能正常进入系统').toBe('ok');
      return strongPassword;
    }
  }
  throw new Error(`${username} 登录失败：候选口令全部不可用（是否改动过内置账号？）`);
}

/** 以管理员登录（大多数用例用这个）。 */
export function loginAsAdmin(page: Page): Promise<string> {
  return loginExpectingUsable(page, 'admin', ADMIN_STRONG_PASSWORD);
}

/** 以普通员工登录（权限/数据范围用例用这个）。 */
export function loginAsEmployee(page: Page): Promise<string> {
  return loginExpectingUsable(page, 'dev1', EMPLOYEE_STRONG_PASSWORD);
}
