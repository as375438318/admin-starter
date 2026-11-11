import { beforeEach, describe, expect, it, vi } from 'vitest';

const spies = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { error: spies.error } }));

const dictApiMock = vi.hoisted(() => ({ bundles: vi.fn() }));
vi.mock('@/api/dict', () => ({ dictApi: dictApiMock }));

import { ApiError } from '@/api/http';
import { createOptionReporter } from './option-load';
import { loadStatusOptions } from './status-options';

describe('createOptionReporter（选项加载失败提示）', () => {
  beforeEach(() => spies.error.mockClear());

  it('同一标签只提示一次，避免重复打开抽屉刷屏', () => {
    const report = createOptionReporter();
    report('部门', new ApiError(50001, '系统繁忙', 500));
    report('部门', new ApiError(50001, '系统繁忙', 500));

    expect(spies.error).toHaveBeenCalledTimes(1);
    expect(spies.error).toHaveBeenCalledWith('部门加载失败：系统繁忙，相关内容暂不可用');
  });

  it('不同标签各自提示一次', () => {
    const report = createOptionReporter();
    report('部门', new ApiError(50001, '系统繁忙', 500));
    report('角色', new ApiError(50001, '系统繁忙', 500));

    expect(spies.error).toHaveBeenCalledTimes(2);
  });

  it('空 message 回落到「请求失败」', () => {
    const report = createOptionReporter();
    report('字典', new ApiError(50001, '', 500));
    expect(spies.error).toHaveBeenCalledWith('字典加载失败：请求失败，相关内容暂不可用');
  });

  it('普通 Error 用其 message，非 Error 用「未知错误」', () => {
    const report = createOptionReporter();
    report('菜单', new Error('超时'));
    report('岗位', 'oops');

    expect(spies.error).toHaveBeenNthCalledWith(1, '菜单加载失败：超时，相关内容暂不可用');
    expect(spies.error).toHaveBeenNthCalledWith(2, '岗位加载失败：未知错误，相关内容暂不可用');
  });
});

describe('loadStatusOptions（启用/停用下拉）', () => {
  // 不在 beforeEach 里重置这个 mock：Vitest 3.2 下"beforeEach 中调用 mockReset/mockClear + 用例内抛错"会被记成
  // 未处理拒绝（回落结果本身是正确的，见用例输出）。每个用例自带实现，无需重置即可隔离。

  it('把字典项映射成布尔值选项', async () => {
    dictApiMock.bundles.mockResolvedValue([
      { type: 'user_status', items: [{ label: '正常', value: '1' }, { label: '封禁', value: '0' }] },
    ]);
    expect(await loadStatusOptions()).toEqual([
      { label: '正常', value: true },
      { label: '封禁', value: false },
    ]);
  });

  it('字典为空时回落到内置两项（下拉不能空）', async () => {
    dictApiMock.bundles.mockResolvedValue([{ type: 'user_status', items: [] }]);
    expect(await loadStatusOptions()).toEqual([
      { label: '启用', value: true },
      { label: '停用', value: false },
    ]);
  });

  it('接口失败时回落到内置两项，不把异常抛给页面', async () => {
    // 用同步抛出：async 实现会造出"被拒绝的 promise"，即使调用方已 catch，仍会被 vitest 记为未处理拒绝
    dictApiMock.bundles.mockImplementation(() => {
      throw new ApiError(50001, '系统繁忙', 500);
    });
    expect(await loadStatusOptions()).toHaveLength(2);
  });
});
