import { nextTick } from 'vue';
import type { FormInstance } from 'element-plus';

/**
 * 表单校验失败后滚动到第一个错误字段并聚焦（PC-6 FORM-003）。
 * 兼容本地校验错误与后端 422 映射到 el-form-item error 的情况。
 */
export async function scrollToFirstError(formRef?: FormInstance): Promise<void> {
  await nextTick();
  const container = formRef?.$el as HTMLElement | undefined;
  if (!container) {
    return;
  }
  const item = container.querySelector<HTMLElement>('.el-form-item.is-error');
  if (!item) {
    return;
  }
  item.scrollIntoView({ behavior: 'smooth', block: 'center' });
  const input = item.querySelector<HTMLElement>('input:not([disabled]), textarea:not([disabled])');
  if (input) {
    window.setTimeout(() => {
      input.focus();
    }, 240);
  }
}
