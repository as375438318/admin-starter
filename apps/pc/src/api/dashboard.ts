import { api } from './http';
import type { DashboardOverview } from './types';

/**
 * 工作台统计。后端用权限码 system:dashboard:view 把关：无权限时返回 403，
 * 页面据此隐藏统计区（而不是显示一堆 0，让人以为"系统是空的"）。
 */
export const dashboardApi = {
  overview() {
    return api.get<DashboardOverview>('/dashboard/overview');
  },
};