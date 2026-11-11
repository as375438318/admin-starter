import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/http';

function reasonOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message || '请求失败';
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return '未知错误';
}

/**
 * 选项类数据（下拉、树、抽屉初始化）加载失败的统一处理。
 * 失败时不能静默变成空列表或静默关闭，否则用户无法区分「没有数据」和「加载失败/无权限」。
 * 按页面实例去重：同一标签在本页只提示一次，避免重复打开抽屉时刷屏。
 */
export function createOptionReporter(): (label: string, error: unknown) => void {
  const reported = new Set<string>();
  return (label: string, error: unknown) => {
    if (reported.has(label)) {
      return;
    }
    reported.add(label);
    ElMessage.error(`${label}加载失败：${reasonOf(error)}，相关内容暂不可用`);
  };
}
