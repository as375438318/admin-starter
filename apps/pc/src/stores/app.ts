import { ref } from 'vue';
import { defineStore } from 'pinia';

const STORAGE_KEY = 'admin-starter.sidebar-collapsed';

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(localStorage.getItem(STORAGE_KEY) === '1');

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value;
    localStorage.setItem(STORAGE_KEY, sidebarCollapsed.value ? '1' : '0');
  }

  return { sidebarCollapsed, toggleSidebar };
});
