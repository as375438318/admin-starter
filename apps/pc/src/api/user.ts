import { api } from './http';
import type { PageData, PageParams, UserItem, UserOption } from './types';

export interface UserQuery extends PageParams {
  keyword?: string;
  isEnabled?: boolean;
  deptId?: number;
}

export interface UserCreatePayload {
  username: string;
  nickname: string;
  password: string;
  deptId?: number | null;
  email?: string | null;
  phone?: string | null;
  roleIds?: number[];
}

export interface UserUpdatePayload {
  nickname: string;
  deptId?: number | null;
  email?: string | null;
  phone?: string | null;
  roleIds?: number[];
}

export const userApi = {
  page(params: UserQuery) {
    return api.get<PageData<UserItem>>('/users', params);
  },
  get(id: number) {
    return api.get<UserItem>(`/users/${id}`);
  },
  create(payload: UserCreatePayload) {
    return api.post<UserItem>('/users', payload);
  },
  update(id: number, payload: UserUpdatePayload) {
    return api.put<UserItem>(`/users/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/users/${id}`);
  },
  resetPassword(id: number, password: string) {
    return api.put<void>(`/users/${id}/password`, { password });
  },
  updateStatus(id: number, isEnabled: boolean) {
    return api.put<void>(`/users/${id}/status`, { isEnabled });
  },
  assignRoles(id: number, roleIds: number[]) {
    return api.put<void>(`/users/${id}/roles`, { roleIds });
  },
  options(keyword?: string) {
    return api.get<UserOption[]>('/users/options', keyword ? { keyword } : undefined);
  },
};
