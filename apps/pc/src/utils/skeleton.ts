import { onBeforeUnmount, ref, watch, type Ref } from 'vue';

/**
 * 骨架屏延迟显示：请求发起后先等 delay 毫秒，仍在加载才显示骨架。
 * 快速请求（< delay）不会出现骨架闪烁；请求结束后立即隐藏。
 */
export function useDelayedSkeleton(loading: Ref<boolean>, delay = 300) {
  const visible = ref(false);
  let timer: number | undefined;

  function clearTimer(): void {
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timer = undefined;
    }
  }

  watch(
    loading,
    (value) => {
      clearTimer();
      if (value) {
        timer = window.setTimeout(() => {
          visible.value = true;
        }, delay);
      } else {
        visible.value = false;
      }
    },
    { immediate: true },
  );

  onBeforeUnmount(clearTimer);

  return visible;
}
