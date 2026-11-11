import { describe, expect, it } from 'vitest';
import { isAuthEntry, isTerminalAuthCode, planOn401 } from './http';

describe('planOn401（401 处置决策）', () => {
  it('非 401 不介入', () => {
    expect(planOn401({ status: 500, url: '/users' })).toBe('ignore');
    expect(planOn401({ status: 403, url: '/users' })).toBe('ignore');
  });

  it('已经重试过的 401 不再刷新，避免死循环', () => {
    expect(planOn401({ status: 401, retried: true, url: '/users' })).toBe('ignore');
  });

  it('终止分码 → terminal（刷新无意义，直接回登录页）', () => {
    for (const code of [20103, 20104, 20106, 20107, 20108]) {
      expect(planOn401({ status: 401, url: '/users', code })).toBe('terminal');
    }
    // 即使是 /auth/* 接口，终止分码也不该被当成"可刷新"
    expect(planOn401({ status: 401, url: '/auth/me', code: 20103 })).toBe('terminal');
  });

  it('登录/刷新接口自身的 401 → reject（就地提示，不刷新、不跳整页）', () => {
    expect(planOn401({ status: 401, url: '/auth/login' })).toBe('reject');
    expect(planOn401({ status: 401, url: '/auth/refresh' })).toBe('reject');
  });

  it('普通业务接口 401 → refresh', () => {
    expect(planOn401({ status: 401, url: '/users' })).toBe('refresh');
    expect(planOn401({ status: 401, url: '/files/1/download' })).toBe('refresh');
  });

  it('回归：/auth/me 等非登录接口必须是 refresh，不能整段排除 /auth/*', () => {
    // 曾因把整个 /auth/* 排除在刷新之外，access 过期后刷新页面会被误判为未登录而被登出
    expect(planOn401({ status: 401, url: '/auth/me' })).toBe('refresh');
    expect(planOn401({ status: 401, url: '/auth/routes' })).toBe('refresh');
    expect(planOn401({ status: 401, url: '/auth/permissions' })).toBe('refresh');
    expect(planOn401({ status: 401, url: '/auth/sessions' })).toBe('refresh');
  });
});

describe('isAuthEntry', () => {
  it('只把登录与刷新当入口接口', () => {
    expect(isAuthEntry('/auth/login')).toBe(true);
    expect(isAuthEntry('/auth/refresh')).toBe(true);
    expect(isAuthEntry('/auth/me')).toBe(false);
    expect(isAuthEntry('/auth/logout')).toBe(false);
    expect(isAuthEntry('/users')).toBe(false);
  });
});

describe('isTerminalAuthCode', () => {
  it('只认 20103／20104／20106／20107／20108 这类终止码', () => {
    for (const code of [20103, 20104, 20106, 20107, 20108]) {
      expect(isTerminalAuthCode(code)).toBe(true);
    }
  });

  it('20101／20102／20105 仍应先尝试刷新（设计口径：登出与自然过期可续期）', () => {
    // 依据 docs/design/auth-and-datascope.md 的 401 分码矩阵：这三类仍先刷新再判定
    for (const code of [20101, 20102, 20105, undefined]) {
      expect(isTerminalAuthCode(code)).toBe(false);
    }
  });
});
