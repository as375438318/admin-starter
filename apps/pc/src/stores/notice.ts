import { ref } from 'vue';
import { defineStore } from 'pinia';
import { inboxApi } from '@/api/notice';

/** 未读数共享状态：顶栏红点与“我的消息”页共用，轮询在 Layout 中驱动。 */
export const useNoticeStore = defineStore('notice', () => {
  const unreadCount = ref(0);

  async function refresh(): Promise<void> {
    try {
      const data = await inboxApi.unreadCount();
      unreadCount.value = data.count;
    } catch {
      // 未读数失败不影响主流程，保留旧值
    }
  }

  function reset(): void {
    unreadCount.value = 0;
  }

  return { unreadCount, refresh, reset };
});
