import { api } from './http';
import type { PageData, PageParams } from './types';

export interface CustomerItem {
  id: number;
  name?: string;
  level?: string;
  contact?: string;
  phone?: string;
  isEnabled?: boolean;
  remark?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerQuery extends PageParams {
  keyword?: string;
}

export interface CustomerSavePayload {
  name: string;
  level?: string;
  contact?: string;
  phone?: string;
  isEnabled?: boolean;
  remark?: string;
}

export const customerApi = {
  page(params: CustomerQuery) {
    return api.get<PageData<CustomerItem>>('/demo/customers', params);
  },
  create(payload: CustomerSavePayload) {
    return api.post<CustomerItem>('/demo/customers', payload);
  },
  update(id: number, payload: CustomerSavePayload) {
    return api.put<CustomerItem>(`/demo/customers/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/demo/customers/${id}`);
  },
};
