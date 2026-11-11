import { api, downloadFile, downloadOrJson, type FileOrJson } from './http';
import type {
  DemoTaskExportAccepted,
  DemoTaskExportTaskItem,
  DemoTaskImportResult,
  DemoTaskItem,
  DemoTaskStatus,
  PageData,
  PageParams,
} from './types';

export interface DemoTaskQuery extends PageParams {
  keyword?: string;
  status?: DemoTaskStatus;
  deptId?: number;
}

export interface DemoTaskExportQuery {
  keyword?: string;
  status?: string;
  deptId?: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

export interface DemoTaskSavePayload {
  title: string;
  status: DemoTaskStatus;
  deptId: number;
  ownerId?: number | null;
}

export const demoTaskApi = {
  page(params: DemoTaskQuery) {
    return api.get<PageData<DemoTaskItem>>('/demo-tasks', params);
  },
  get(id: number) {
    return api.get<DemoTaskItem>(`/demo-tasks/${id}`);
  },
  create(payload: DemoTaskSavePayload) {
    return api.post<DemoTaskItem>('/demo-tasks', payload);
  },
  update(id: number, payload: DemoTaskSavePayload) {
    return api.put<DemoTaskItem>(`/demo-tasks/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/demo-tasks/${id}`);
  },
  importTemplate() {
    return downloadFile('/demo-tasks/import-template');
  },
  /** 同步导入：2 万行预计 2–5 秒，超时放宽到 120 秒。 */
  importFile(file: File) {
    return api.upload<DemoTaskImportResult>('/demo-tasks/import', file, undefined, 120000);
  },
  /** 导出：≤2000 行返回文件流；>2000 行返回异步任务受理结果。 */
  startExport(params: DemoTaskExportQuery): Promise<FileOrJson<DemoTaskExportAccepted>> {
    return downloadOrJson<DemoTaskExportAccepted>('/demo-tasks/export', params);
  },
  exportTaskPage(params: PageParams) {
    return api.get<PageData<DemoTaskExportTaskItem>>('/demo-tasks/export-tasks', params);
  },
  exportTask(id: number) {
    return api.get<DemoTaskExportTaskItem>(`/demo-tasks/export-tasks/${id}`);
  },
  cancelExportTask(id: number) {
    return api.post<DemoTaskExportTaskItem>(`/demo-tasks/export-tasks/${id}/cancel`);
  },
  downloadExportFile(id: number) {
    return downloadFile(`/demo-tasks/export-tasks/${id}/file`);
  },
};
