import { api } from './http';
import type { LoginResult, MenuRoute, ProfileInfo } from './types';

export const authApi = {
  login(username: string, password: string) {
    return api.post<LoginResult>('/auth/login', { username, password });
  },
  refresh() {
    return api.post<LoginResult>('/auth/refresh');
  },
  logout() {
    return api.post<void>('/auth/logout');
  },
  me() {
    return api.get<ProfileInfo>('/auth/me');
  },
  routes() {
    return api.get<MenuRoute[]>('/auth/routes');
  },
  permissions() {
    return api.get<string[]>('/auth/permissions');
  },
};
