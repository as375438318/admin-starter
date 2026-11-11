import { api } from './http';
import type { PageData, PageParams, SessionItem } from './types';

export interface SessionQuery extends PageParams {
  userId?: number;
  keyword?: string;
  online?: boolean;
}

export interface SessionAffect {
  affected: number;
}

export const sessionApi = {
  page(params: SessionQuery) {
    return api.get<PageData<SessionItem>>('/sessions', params);
  },
  kick(id: number) {
    return api.delete<SessionAffect>(`/sessions/${id}`);
  },
  kickUser(userId: number) {
    return api.delete<SessionAffect>(`/sessions/user/${userId}`);
  },
  cleanInvalidated() {
    return api.delete<SessionAffect>('/sessions/invalidated');
  },
};

export const mySessionApi = {
  list() {
    return api.get<SessionItem[]>('/auth/sessions');
  },
  kick(id: number) {
    return api.delete<SessionAffect>(`/auth/sessions/${id}`);
  },
};
