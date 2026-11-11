import { api, downloadFile, downloadOrJson, type FileOrJson } from './http';
import type {
  ExportAccepted,
  ExportTaskItem,
  ImportResult,
  PageData,
  PageParams,
  PostItem,
} from './types';

export interface PostQuery extends PageParams {
  keyword?: string;
  deptId?: number;
  isEnabled?: boolean;
}

export interface PostExportQuery {
  keyword?: string;
  deptId?: number;
  isEnabled?: boolean;
  sort?: string;
  order?: 'asc' | 'desc';
}

export interface PostSavePayload {
  deptId: number;
  code: string;
  name: string;
  sort?: number;
  isEnabled?: boolean;
}

export const postApi = {
  page(params: PostQuery) {
    return api.get<PageData<PostItem>>('/posts', params);
  },
  create(payload: PostSavePayload) {
    return api.post<PostItem>('/posts', payload);
  },
  update(id: number, payload: PostSavePayload) {
    return api.put<PostItem>(`/posts/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/posts/${id}`);
  },
  importTemplate() {
    return downloadFile('/posts/import-template');
  },
  /** 同步导入：2 万行预计 2–5 秒，超时放宽到 120 秒。 */
  importFile(file: File) {
    return api.upload<ImportResult>('/posts/import', file, undefined, 120000);
  },
  /** 导出：≤2000 行返回文件流；>2000 行返回异步任务受理结果。 */
  startExport(params: PostExportQuery): Promise<FileOrJson<ExportAccepted>> {
    return downloadOrJson<ExportAccepted>('/posts/export', params);
  },
  exportTaskPage(params: PageParams) {
    return api.get<PageData<ExportTaskItem>>('/posts/export-tasks', params);
  },
  exportTask(id: number) {
    return api.get<ExportTaskItem>(`/posts/export-tasks/${id}`);
  },
  cancelExportTask(id: number) {
    return api.post<ExportTaskItem>(`/posts/export-tasks/${id}/cancel`);
  },
  downloadExportFile(id: number) {
    return downloadFile(`/posts/export-tasks/${id}/file`);
  },
};
