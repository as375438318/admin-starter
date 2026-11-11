import { api } from './http';
import type { LoginLogItem, OperLogItem, PageData, PageParams } from './types';

export interface OperLogQuery extends PageParams {
  keyword?: string;
  resultCode?: number;
  from?: string;
  to?: string;
}

export const logApi = {
  page(params: OperLogQuery) {
    return api.get<PageData<OperLogItem>>('/oper-logs', params);
  },
  loginLogs(params: PageParams) {
    return api.get<PageData<LoginLogItem>>('/profile/login-logs', params);
  },
};

export const profileApi = {
  get() {
    return api.get<import('./types').ProfileInfo>('/profile');
  },
  update(payload: { nickname: string; email?: string; phone?: string }) {
    return api.put<import('./types').ProfileInfo>('/profile', payload);
  },
  changePassword(payload: { oldPassword: string; newPassword: string }) {
    return api.put<void>('/profile/password', payload);
  },
  loginLogs(params: PageParams) {
    return api.get<PageData<LoginLogItem>>('/profile/login-logs', params);
  },
};
