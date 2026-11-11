import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, h, withDirectives, type Directive } from 'vue';

/** 权限码白名单由每个用例决定，不依赖真实登录态 */
const state = vi.hoisted(() => ({ codes: [] as string[] }));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: (code: string) => state.codes.includes(code) }),
}));

import { setupPermissionDirective } from './permission';

/** 从注册函数里取出真实指令实现，避免测试里再抄一份逻辑 */
let directive: Directive<HTMLElement, string | string[]>;
setupPermissionDirective({ directive: (_name: string, dir: unknown) => { directive = dir as typeof directive; } } as never);

function mountProbe(code: string | string[]) {
  const Child = defineComponent({
    render() {
      // 外层 host 必须有：若 button 直接作为组件根，即使被移出 DOM，VTU 的 find 仍会把根节点算作命中
      return h('div', { class: 'host' }, [withDirectives(h('button', { class: 'probe' }, '新增'), [[directive, code]])]);
    },
  });
  return mount(Child, { attachTo: document.body });
}

describe('v-permission（无权限不渲染）', () => {
  beforeEach(() => {
    state.codes = [];
    document.body.innerHTML = '';
  });

  it('有权限时元素保留', () => {
    state.codes = ['system:user:create'];
    expect(mountProbe('system:user:create').find('.probe').exists()).toBe(true);
  });

  it('无权限时元素被移出 DOM（不是置灰禁用）', () => {
    state.codes = ['system:user:list'];
    expect(mountProbe('system:user:create').find('.probe').exists()).toBe(false);
  });

  it('数组写法任意命中即渲染', () => {
    state.codes = ['system:user:update'];
    expect(mountProbe(['system:user:create', 'system:user:update']).find('.probe').exists()).toBe(true);

    state.codes = ['system:user:list'];
    expect(mountProbe(['system:user:create', 'system:user:update']).find('.probe').exists()).toBe(false);
  });
});
