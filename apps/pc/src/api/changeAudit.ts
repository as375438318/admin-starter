import { api } from './http';
import type { PageData, PageParams } from './types';

/** 变更审计（只读）：写入只能由后端业务事务产生，前端没有写入口。 */
export type ChangeTargetType = 'CONFIG' | 'DICT_TYPE' | 'DICT_ITEM' | 'MENU' | 'ROLE_GRANT';
export type ChangeAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'GRANT';

export interface ChangeAuditItem {
  id: number;
  targetType: ChangeTargetType;
  targetKey: string;
  targetName: string | null;
  action: ChangeAction;
  /** 变更前快照（JSON 字符串，敏感对象已打码） */
  beforeValue: string | null;
  afterValue: string | null;
  isSensitive: boolean;
  operatorId: number | null;
  operatorName: string | null;
  ip: string | null;
  traceId: string | null;
  createdAt: string;
}

export interface ChangeAuditQuery extends PageParams {
  targetType?: string;
  targetKey?: string;
  operator?: string;
  action?: string;
  keyword?: string;
}

export const changeAuditApi = {
  page(params: ChangeAuditQuery) {
    return api.get<PageData<ChangeAuditItem>>('/change-audits', params);
  },
};