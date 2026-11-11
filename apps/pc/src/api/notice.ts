import { api } from './http';
import type {
  InboxNoticeDetail,
  InboxNoticeItem,
  NoticeItem,
  NoticeReaderItem,
  NoticeScopeType,
  PageData,
  PageParams,
} from './types';

export interface NoticeQuery extends PageParams {
  keyword?: string;
  level?: string;
  type?: string;
  status?: string;
}

export interface NoticeScopePayload {
  scopeType: NoticeScopeType;
  targetId?: number | null;
}

export interface NoticePublishPayload {
  title: string;
  content: string;
  level: string;
  type: string;
  revocable: boolean;
  scopes: NoticeScopePayload[];
}

export const noticeApi = {
  page(params: NoticeQuery) {
    return api.get<PageData<NoticeItem>>('/notices', params);
  },
  detail(id: number) {
    return api.get<NoticeItem>(`/notices/${id}`);
  },
  publish(payload: NoticePublishPayload) {
    return api.post<NoticeItem>('/notices', payload);
  },
  revoke(id: number) {
    return api.post<NoticeItem>(`/notices/${id}/revoke`);
  },
  remove(id: number) {
    return api.delete<void>(`/notices/${id}`);
  },
  readers(id: number, params: PageParams & { readStatus?: string; keyword?: string }) {
    return api.get<PageData<NoticeReaderItem>>(`/notices/${id}/readers`, params);
  },
};

export const inboxApi = {
  page(params: PageParams & { readStatus?: string }) {
    return api.get<PageData<InboxNoticeItem>>('/my-notices', params);
  },
  unreadCount() {
    return api.get<{ count: number }>('/my-notices/unread-count');
  },
  detail(id: number) {
    return api.get<InboxNoticeDetail>(`/my-notices/${id}`);
  },
  readAll() {
    return api.post<{ marked: number }>('/my-notices/read-all');
  },
};
