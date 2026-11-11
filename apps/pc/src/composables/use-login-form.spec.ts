import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/http';

const router = vi.hoisted(() => ({ replace: vi.fn(async () => undefined) }));
const route = vi.hoisted(() => ({ query: {} as Record<string, unknown> }));
const authLogin = vi.hoisted(() => vi.fn(async () => undefined));

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }));
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => route }));
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ login: authLogin }) }));

import { useLoginForm } from './use-login-form';

const REMEMBER_KEY = 'login:last-username';

function fakeInputs() {
  const username = document.createElement('input');
  const password = document.createElement('input');
  const usernameFocus = vi.spyOn(username, 'focus');
  const passwordFocus = vi.spyOn(password, 'focus');
  return { els: { username, password }, usernameFocus, passwordFocus };
}

describe('useLoginForm（登录页逻辑）', () => {
  beforeEach(() => {
    localStorage.clear();
    router.replace.mockClear();
    authLogin.mockReset();
    authLogin.mockResolvedValue(undefined);
    route.query = {};
  });

  it('空表单校验：两个字段各自报错，并聚焦账号框', async () => {
    const form = useLoginForm();
    const { els, usernameFocus, passwordFocus } = fakeInputs();

    await form.submit(els);

    expect(form.fieldErrors.username).toBe('请输入账号');
    expect(form.fieldErrors.password).toBe('请输入密码');
    expect(usernameFocus).toHaveBeenCalledTimes(1);
    expect(passwordFocus).not.toHaveBeenCalled();
    expect(authLogin).not.toHaveBeenCalled();
  });

  it('只缺密码时聚焦密码框', async () => {
    const form = useLoginForm();
    form.username.value = 'admin';
    const { els, passwordFocus } = fakeInputs();

    await form.submit(els);

    expect(form.fieldErrors.username).toBe('');
    expect(passwordFocus).toHaveBeenCalledTimes(1);
  });

  it('登录成功后记住账号（trim）并跳转工作台', async () => {
    const form = useLoginForm();
    form.username.value = '  admin  ';
    form.password.value = 'admin123';

    await form.submit();

    expect(authLogin).toHaveBeenCalledWith('admin', 'admin123');
    expect(localStorage.getItem(REMEMBER_KEY)).toBe('admin');
    expect(router.replace).toHaveBeenCalledWith('/dashboard');
    expect(form.submitting.value).toBe(false);
  });

  it('取消记住我时清掉上次账号', async () => {
    localStorage.setItem(REMEMBER_KEY, 'old');
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'admin123';
    form.remember.value = false;

    await form.submit();

    expect(localStorage.getItem(REMEMBER_KEY)).toBeNull();
  });

  it('登录页会预填上次账号', () => {
    localStorage.setItem(REMEMBER_KEY, 'demo_dept');
    const form = useLoginForm();
    expect(form.username.value).toBe('demo_dept');
    expect(form.remember.value).toBe(true);
  });

  it('query.redirect 优先于默认工作台', async () => {
    route.query = { redirect: '/system/users' };
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'admin123';

    await form.submit();

    expect(router.replace).toHaveBeenCalledWith('/system/users');
  });

  it('401 带字段错误时映射到输入框，并聚焦密码框而非整页报错', async () => {
    authLogin.mockRejectedValue(new ApiError(20101, '账号或口令错误', 401, null, [
      { field: 'password', code: 'INVALID', message: '口令错误' },
    ]));
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'wrong';
    const { els, passwordFocus } = fakeInputs();

    await form.submit(els);

    expect(form.fieldErrors.password).toBe('口令错误');
    expect(form.errorText.value).toBe('');
    expect(passwordFocus).toHaveBeenCalledTimes(1);
  });

  it('账号被停用等无字段错误时落到整段提示', async () => {
    authLogin.mockRejectedValue(new ApiError(20103, '账号已被停用', 401));
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'admin123';

    await form.submit();

    expect(form.errorText.value).toBe('账号已被停用');
  });

  it('非 ApiError 异常显示通用文案，不泄漏内部错误', async () => {
    authLogin.mockRejectedValue(new Error('boom'));
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'admin123';

    await form.submit();

    expect(form.errorText.value).toBe('登录失败，请稍后重试');
  });

  it('提交中重复点击被忽略', async () => {
    let release: () => void = () => {};
    authLogin.mockImplementation(async () => {
      await new Promise<void>((resolve) => { release = resolve; });
    });
    const form = useLoginForm();
    form.username.value = 'admin';
    form.password.value = 'admin123';

    const first = form.submit();
    const second = form.submit();
    release();
    await Promise.all([first, second]);

    expect(authLogin).toHaveBeenCalledTimes(1);
  });

  it('修改输入会清掉上一次的字段错误', async () => {
    const form = useLoginForm();
    await form.submit();
    expect(form.fieldErrors.username).not.toBe('');

    form.username.value = 'admin';
    await Promise.resolve();
    expect(form.fieldErrors.username).toBe('');
  });
});
