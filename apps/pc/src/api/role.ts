import { api } from './http';
import type { DataScope, OwnFilter, PageData, PageParams, RoleItem } from './types';

export interface RoleQuery extends PageParams {
  keyword?: string;
  isEnabled?: boolean;
}

export interface RoleSavePayload {
  name: string;
  code: string;
  sort?: number;
  remark?: string;
  isEnabled?: boolean;
}

export interface RoleDataScopePayload {
  dataScope: DataScope;
  ownFilter: OwnFilter;
  deptIds?: number[];
  userIds?: number[];
}

export const roleApi = {
  page(params: RoleQuery) {
    return api.get<PageData<RoleItem>>('/roles', params);
  },
  all() {
    return api.get<RoleItem[]>('/roles/all');
  },
  get(id: number) {
    return api.get<RoleItem>(`/roles/${id}`);
  },
  create(payload: RoleSavePayload) {
    return api.post<RoleItem>('/roles', payload);
  },
  update(id: number, payload: RoleSavePayload) {
    return api.put<RoleItem>(`/roles/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/roles/${id}`);
  },
  assignMenus(id: number, menuIds: number[]) {
    return api.put<void>(`/roles/${id}/menus`, { menuIds });
  },
  assignDataScope(id: number, payload: RoleDataScopePayload) {
    return api.put<void>(`/roles/${id}/data-scope`, payload);
  },
};
