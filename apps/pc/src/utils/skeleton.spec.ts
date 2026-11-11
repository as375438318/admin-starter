import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h, nextTick, ref, type Ref } from 'vue';
import { mount } from '@vue/test-utils';
import { useDelayedSkeleton } from './skeleton';

/** 组合式函数依赖组件生命周期（onBeforeUnmount），必须在组件里跑 */
function withLoading(delay?: number) {
  const loading = ref(false);
  let visible!: Ref<boolean>;
  const wrapper = mount(defineComponent({
    setup() {
      visible = useDelayedSkeleton(loading, delay);
      return () => h('div', visible.value ? 'skeleton' : 'content');
    },
  }));
  return { loading, visible: () => visible, wrapper };
}

describe('useDelayedSkeleton（骨架屏延迟显示）', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('初始不加载时不显示骨架', () => {
    const { wrapper } = withLoading();
    expect(wrapper.text()).toBe('content');
  });

  it('加载超过延迟才显示骨架（避免快速请求闪烁）', async () => {
    const { loading, wrapper } = withLoading(300);
    loading.value = true;
    await nextTick();

    vi.advanceTimersByTime(299);
    await nextTick();
    expect(wrapper.text()).toBe('content');

    vi.advanceTimersByTime(1);
    await nextTick();
    expect(wrapper.text()).toBe('skeleton');
  });

  it('请求在延迟内结束：骨架从不出现', async () => {
    const { loading, wrapper } = withLoading(300);
    loading.value = true;
    await nextTick();
    vi.advanceTimersByTime(100);

    loading.value = false;
    await nextTick();
    vi.advanceTimersByTime(1000);
    await nextTick();

    expect(wrapper.text()).toBe('content');
  });

  it('结束加载立即隐藏已显示的骨架', async () => {
    const { loading, wrapper } = withLoading(10);
    loading.value = true;
    await nextTick();
    vi.advanceTimersByTime(10);
    await nextTick();
    expect(wrapper.text()).toBe('skeleton');

    loading.value = false;
    await nextTick();
    expect(wrapper.text()).toBe('content');
  });

  it('卸载时清掉未触发的定时器', async () => {
    const { loading, wrapper } = withLoading(300);
    loading.value = true;
    await nextTick();

    wrapper.unmount();
    vi.advanceTimersByTime(1000);

    expect(vi.getTimerCount()).toBe(0);
  });
});
