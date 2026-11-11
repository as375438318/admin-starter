import type { App, Directive } from 'vue';
import { useAuthStore } from '@/stores/auth';

/**
 * 无权限时不渲染元素（不是置灰禁用）。
 */
const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore();
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value];
    const allowed = codes.some((code) => auth.hasPermission(code));
    if (!allowed) {
      el.parentNode?.removeChild(el);
    }
  },
};

export function setupPermissionDirective(app: App): void {
  app.directive('permission', permission);
}
