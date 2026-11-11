import { api } from './http';
import type {
  JobItem,
  JobKeyOption,
  JobLogDetail,
  JobLogItem,
  JobRunResultItem,
  PageData,
  PageParams,
} from './types';

export interface JobPayload {
  name: string;
  groupName?: string;
  jobKey: string;
  cronExpression: string;
  description?: string | null;
  status: 'ENABLED' | 'DISABLED';
  concurrencyPolicy: 'DISALLOW' | 'ALLOW';
  timeoutSeconds: number;
  retryTimes: number;
  retryIntervalSeconds: number;
  notifyPolicy: 'NEVER' | 'ON_FAILURE' | 'ON_CONSECUTIVE';
  notifyThreshold: number;
  notifyTargets?: string | null;
}

export const jobApi = {
  page(params: PageParams & { keyword?: string; status?: string }) {
    return api.get<PageData<JobItem>>('/jobs', params);
  },
  keys() {
    return api.get<JobKeyOption[]>('/jobs/keys');
  },
  create(payload: JobPayload) {
    return api.post<JobItem>('/jobs', payload);
  },
  update(id: number, payload: JobPayload) {
    return api.put<JobItem>(`/jobs/${id}`, payload);
  },
  changeStatus(id: number, status: 'ENABLED' | 'DISABLED') {
    return api.put<JobItem>(`/jobs/${id}/status`, { status });
  },
  run(id: number) {
    return api.post<JobRunResultItem>(`/jobs/${id}/run`);
  },
  remove(id: number) {
    return api.delete<void>(`/jobs/${id}`);
  },
};

export const jobLogApi = {
  page(params: PageParams & { jobId?: number; keyword?: string; result?: string; triggerType?: string }) {
    return api.get<PageData<JobLogItem>>('/job-logs', params);
  },
  detail(id: number) {
    return api.get<JobLogDetail>(`/job-logs/${id}`);
  },
};
