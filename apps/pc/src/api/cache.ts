import { api, request } from './http';
import type {
  CacheAffect,
  CacheCleanLog,
  CacheKeyPageData,
  CacheNamespace,
  CacheOverview,
  PageData,
  PageParams,
} from './types';

export interface CacheKeyQuery extends PageParams {
  namespace: string;
  keyword?: string;
}

export const cacheApi = {
  overview() {
    return api.get<CacheOverview>('/cache/overview');
  },
  namespaces() {
    return api.get<CacheNamespace[]>('/cache/namespaces');
  },
  keys(params: CacheKeyQuery) {
    return api.get<CacheKeyPageData>('/cache/keys', params);
  },
  deleteKey(namespace: string, key: string) {
    return request<CacheAffect>({ url: '/cache/keys', method: 'DELETE', params: { namespace, key } });
  },
  clearNamespace(code: string) {
    return api.delete<CacheAffect>(`/cache/namespaces/${encodeURIComponent(code)}`);
  },
  cleanSessionCache() {
    return api.delete<CacheAffect>('/cache/session-cache');
  },
  cleanLogs(params: PageParams) {
    return api.get<PageData<CacheCleanLog>>('/cache/clean-logs', params);
  },
};
