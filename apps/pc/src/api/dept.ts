import { api } from './http';
import type { DeptItem, DeptOption } from './types';

export interface DeptSavePayload {
  parentId: number;
  name: string;
  sort?: number;
  leader?: string;
  phone?: string;
  isEnabled?: boolean;
}

export const deptApi = {
  tree() {
    return api.get<DeptItem[]>('/depts');
  },
  options() {
    return api.get<DeptOption[]>('/depts/options');
  },
  create(payload: DeptSavePayload) {
    return api.post<DeptItem>('/depts', payload);
  },
  update(id: number, payload: DeptSavePayload) {
    return api.put<DeptItem>(`/depts/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/depts/${id}`);
  },
};
