import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import IconPicker from './IconPicker.vue';

/**
 * 图标选择器。
 *
 * <p>要守住的两件事：① 选出来的名字必须是**已注册**的图标（否则侧边栏静默不显示）；
 * ② 历史遗留的自定义图标名不能被悄悄清空（早期是手输的）。</p>
 */
function factory(modelValue = '') {
  const wrapper = mount(IconPicker, {
    props: { modelValue },
    global: {
      plugins: [ElementPlus],
      components: ElementPlusIconsVue,
      stubs: { teleport: true },
    },
    attachTo: document.body,
  });
  return wrapper;
}

describe('IconPicker（图标选择器）', () => {
  it('触发器展示当前图标名', () => {
    const wrapper = factory('Setting');
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('Setting');
  });

  it('图标集合与 main.ts 注册来源一致（都是 @element-plus/icons-vue）', () => {
    const wrapper = factory();
    // 组件内网格项数量 = 包导出数量（搜索为空时全量展示）
    (wrapper.vm as unknown as { visible: boolean }).visible = true;
    return wrapper.vm.$nextTick().then(() => {
      const items = wrapper.findAll('.icon-grid-item');
      expect(items.length).toBe(Object.keys(ElementPlusIconsVue).length);
    });
  });

  it('点击图标项抛出 update:modelValue', async () => {
    const wrapper = factory();
    (wrapper.vm as unknown as { visible: boolean }).visible = true;
    await wrapper.vm.$nextTick();
    const first = wrapper.find('.icon-grid-item');
    const name = first.element.getAttribute('title');
    await first.trigger('click');
    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    expect(emitted![0][0]).toBe(name);
  });

  it('搜索按名称过滤（不区分大小写）', async () => {
    const wrapper = factory();
    (wrapper.vm as unknown as { visible: boolean }).visible = true;
    await wrapper.vm.$nextTick();
    const searchInput = wrapper.findAll('input')[1];
    await searchInput.setValue('setting');
    const items = wrapper.findAll('.icon-grid-item');
    expect(items.length).toBeGreaterThan(0);
    expect(items.every((item) => (item.element.getAttribute('title') ?? '').toLowerCase().includes('setting'))).toBe(true);
  });

  it('历史自定义值：仍显示原文并提示不在内置集合里（不清空）', async () => {
    const wrapper = factory('MyCustomIcon');
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('MyCustomIcon');
    (wrapper.vm as unknown as { visible: boolean }).visible = true;
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.icon-picker-warning').exists()).toBe(true);
    // 没有点清除，就不应该抛出任何更新
    expect(wrapper.emitted('update:modelValue')).toBeFalsy();
  });
});
