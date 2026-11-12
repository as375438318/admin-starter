import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

const api = vi.hoisted(() => ({
  me: vi.fn(),
  routes: vi.fn(),
  permissions: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));
vi.mock('@/api/auth', () => ({ authApi: api }));

import { useAuthStore } from './auth';

const PROFILE = { userId: 1, username: 'admin', nickname: '管理员', deptId: 1, deptName: '总部' };

const LOGIN_RESULT = {
  userId: 1,
  username: 'admin',
  nickname: '管理员',
  accessExpiresIn: 900,
  refreshExpiresIn: 604800,
  mustChangePassword: false,
  passwordExpired: false,
};

describe('useAuthStore（登录态与权限集合）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    Object.values(api).forEach((fn) => fn.mockReset());
    api.me.mockResolvedValue(PROFILE);
    api.routes.mockResolvedValue([]);
    api.permissions.mockResolvedValue([]);
    api.login.mockResolvedValue({ ...LOGIN_RESULT });
    api.logout.mockResolvedValue(undefined);
  });

  it('bootstrap 一次拉齐 me / routes / permissions，并置 loaded', async () => {
    api.routes.mockResolvedValue([{ name: 'Dashboard', path: '/dashboard' }]);
    api.permissions.mockResolvedValue(['system:user:list', 'system:user:create']);
    const auth = useAuthStore();

    await auth.bootstrap();

    expect(auth.profile).toEqual(PROFILE);
    expect(auth.menuRoutes).toHaveLength(1);
    expect(auth.loaded).toBe(true);
    expect(auth.hasPermission('system:user:list')).toBe(true);
    expect(auth.mustChangePassword).toBe(false);
  });

  it('hasPermission：未传权限码视为放行，传入但未授权则拒绝', async () => {
    api.permissions.mockResolvedValue(['system:user:list']);
    const auth = useAuthStore();
    await auth.bootstrap();

    expect(auth.hasPermission()).toBe(true);
    expect(auth.hasPermission(null)).toBe(true);
    expect(auth.hasPermission('system:user:list')).toBe(true);
    expect(auth.hasPermission('system:user:delete')).toBe(false);
  });

  it('isLoggedIn 依据 profile 是否存在（不是依据 loaded）', async () => {
    const auth = useAuthStore();
    expect(auth.isLoggedIn).toBe(false);

    await auth.bootstrap();
    expect(auth.isLoggedIn).toBe(true);

    auth.reset();
    expect(auth.isLoggedIn).toBe(false);
  });

  it('displayName 优先昵称，缺失时回落账号名；initial 取其首字母大写', async () => {
    const auth = useAuthStore();
    api.me.mockResolvedValueOnce({ ...PROFILE, nickname: null });
    await auth.bootstrap();
    expect(auth.displayName).toBe('admin');
    expect(auth.initial).toBe('A');

    api.me.mockResolvedValueOnce({ ...PROFILE, nickname: '管理员' });
    await auth.bootstrap();
    expect(auth.displayName).toBe('管理员');
  });

  it('login 成功后立即 bootstrap，保证权限码在同一流程内就绪', async () => {
    api.permissions.mockResolvedValue(['system:user:list']);
    const auth = useAuthStore();

    await auth.login('admin', 'admin123');

    expect(api.login).toHaveBeenCalledWith('admin', 'admin123');
    expect(api.me).toHaveBeenCalledTimes(1);
    expect(auth.hasPermission('system:user:list')).toBe(true);
    expect(auth.mustChangePassword).toBe(false);
  });

  it('登录响应带 mustChangePassword/passwordExpired 时置位（等保 P0-1 前端引导的依据）', async () => {
    api.login.mockResolvedValue({ ...LOGIN_RESULT, mustChangePassword: true, passwordExpired: true });
    const auth = useAuthStore();

    await auth.login('admin', 'admin123');

    expect(auth.mustChangePassword).toBe(true);
    expect(auth.passwordExpired).toBe(true);
  });

  it('仅 passwordExpired 为 true 时也视为必须改密（过期与首登都要先改密）', async () => {
    api.login.mockResolvedValue({ ...LOGIN_RESULT, mustChangePassword: false, passwordExpired: true });
    const auth = useAuthStore();

    await auth.login('admin', 'admin123');

    expect(auth.mustChangePassword).toBe(true);
    expect(auth.passwordExpired).toBe(true);
  });

  it('logout 即使接口失败也必须清空本地登录态（否则前端假装已登录）', async () => {
    const auth = useAuthStore();
    await auth.bootstrap();
    api.logout.mockRejectedValueOnce(new Error('网络断开'));

    await expect(auth.logout()).rejects.toThrow('网络断开');

    expect(auth.profile).toBeNull();
    expect(auth.menuRoutes).toEqual([]);
    expect(auth.loaded).toBe(false);
    expect(auth.hasPermission('system:user:list')).toBe(false);
  });

  it('reset 清空 profile / 菜单 / 权限集合与 loaded', async () => {
    api.permissions.mockResolvedValue(['system:user:list']);
    api.routes.mockResolvedValue([{ name: 'Dashboard', path: '/dashboard' }]);
    const auth = useAuthStore();
    await auth.bootstrap();

    auth.reset();

    expect(auth.profile).toBeNull();
    expect(auth.menuRoutes).toEqual([]);
    expect(auth.permissions.size).toBe(0);
    expect(auth.loaded).toBe(false);
  });
});
