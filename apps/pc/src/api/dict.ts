import { api } from './http';
import type { DictBundle, DictItemRecord, DictTypeItem, PageData, PageParams } from './types';

export interface DictTypeQuery extends PageParams {
  keyword?: string;
  isEnabled?: boolean;
}

export interface DictTypeSavePayload {
  name: string;
  type: string;
  remark?: string;
  isEnabled?: boolean;
}

export interface DictItemSavePayload {
  dictTypeId: number;
  label: string;
  value: string;
  sort?: number;
  isEnabled?: boolean;
  remark?: string;
}

export const dictApi = {
  pageTypes(params: DictTypeQuery) {
    return api.get<PageData<DictTypeItem>>('/dicts/types', params);
  },
  items(typeId: number) {
    return api.get<DictItemRecord[]>(`/dicts/types/${typeId}/items`);
  },
  bundles(types: string[]) {
    return api.get<DictBundle[]>('/dicts/bundles', { types: types.join(',') });
  },
  createType(payload: DictTypeSavePayload) {
    return api.post<DictTypeItem>('/dicts/types', payload);
  },
  updateType(id: number, payload: DictTypeSavePayload) {
    return api.put<DictTypeItem>(`/dicts/types/${id}`, payload);
  },
  removeType(id: number) {
    return api.delete<void>(`/dicts/types/${id}`);
  },
  createItem(payload: DictItemSavePayload) {
    return api.post<DictItemRecord>('/dicts/items', payload);
  },
  updateItem(id: number, payload: DictItemSavePayload) {
    return api.put<DictItemRecord>(`/dicts/items/${id}`, payload);
  },
  removeItem(id: number) {
    return api.delete<void>(`/dicts/items/${id}`);
  },
};
