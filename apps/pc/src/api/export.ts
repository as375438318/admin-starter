import { downloadOrJson, type FileOrJson } from './http';

/** 通用导出中心（`CR-20261114-043`）：一个入口服务所有已注册模块。 */
export interface ExportModuleInfo {
  key: string;
  name: string;
  permission: string;
}

export interface ExportTaskAccepted {
  taskId: number;
  totalRows: number;
}

/** 导出：同步（≤2000 行）返回文件流，异步返回任务受理信息，由调用方分别处理 */
export function exportModule(
  moduleKey: string,
  params: Record<string, string | number | boolean | undefined> = {},
): Promise<FileOrJson<ExportTaskAccepted>> {
  return downloadOrJson<ExportTaskAccepted>(`/exports/${moduleKey}`, params);
}