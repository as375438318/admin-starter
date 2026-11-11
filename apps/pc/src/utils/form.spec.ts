import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { scrollToFirstError } from './form';

function formRefWith(element: HTMLElement) {
  return { $el: element } as never;
}

describe('scrollToFirstError（校验失败后定位首个错误字段）', () => {
  let scrollSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    scrollSpy = vi.fn();
    Element.prototype.scrollIntoView = scrollSpy as never;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('没有 formRef 时安全返回', async () => {
    await expect(scrollToFirstError(undefined)).resolves.toBeUndefined();
    expect(scrollSpy).not.toHaveBeenCalled();
  });

  it('没有错误项时不动滚动', async () => {
    const container = document.createElement('div');
    container.innerHTML = '<div class="el-form-item"><input /></div>';
    await scrollToFirstError(formRefWith(container));
    expect(scrollSpy).not.toHaveBeenCalled();
  });

  it('滚动到首个错误项并延迟聚焦其可输入元素', async () => {
    const container = document.createElement('div');
    container.innerHTML = `
      <div class="el-form-item is-error"><input class="first" /></div>
      <div class="el-form-item is-error"><input class="second" /></div>`;
    const first = container.querySelector<HTMLInputElement>('input.first')!;
    const second = container.querySelector<HTMLInputElement>('input.second')!;
    const focusSpy = vi.spyOn(first, 'focus');
    const secondFocus = vi.spyOn(second, 'focus');

    await scrollToFirstError(formRefWith(container));
    expect(scrollSpy).toHaveBeenCalledTimes(1);

    // 聚焦延后 240ms（等滚动开始），避免与平滑滚动抢焦点
    expect(focusSpy).not.toHaveBeenCalled();
    vi.advanceTimersByTime(240);
    expect(focusSpy).toHaveBeenCalledTimes(1);
    expect(secondFocus).not.toHaveBeenCalled();
  });

  it('错误项里只有禁用输入框时不聚焦', async () => {
    const container = document.createElement('div');
    container.innerHTML = '<div class="el-form-item is-error"><input disabled /></div>';
    const input = container.querySelector('input')!;
    const focusSpy = vi.spyOn(input, 'focus');

    await scrollToFirstError(formRefWith(container));
    vi.advanceTimersByTime(240);

    expect(scrollSpy).toHaveBeenCalledTimes(1);
    expect(focusSpy).not.toHaveBeenCalled();
  });
});
