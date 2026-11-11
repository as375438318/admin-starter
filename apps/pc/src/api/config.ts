import { api } from './http';
import type { ConfigItem, PageData, PageParams } from './types';

export interface ConfigQuery extends PageParams {
  keyword?: string;
  isSensitive?: boolean;
}

export interface ConfigSavePayload {
  configKey: string;
  configValue: string;
  name: string;
  remark?: string;
  isSensitive?: boolean;
  isEnabled?: boolean;
}

export const configApi = {
  page(params: ConfigQuery) {
    return api.get<PageData<ConfigItem>>('/configs', params);
  },
  get(id: number) {
    return api.get<ConfigItem>(`/configs/${id}`);
  },
  create(payload: ConfigSavePayload) {
    return api.post<ConfigItem>('/configs', payload);
  },
  update(id: number, payload: ConfigSavePayload) {
    return api.put<ConfigItem>(`/configs/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/configs/${id}`);
  },
};
