import { ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/http';

export interface UseFieldErrorsResult {
  fieldErrors: Ref<Record<string, string>>;
  /** 取某字段的错误文案，模板直接绑定 */
  fieldError: (field: string) => string;
  clearErrors: () => void;
  /** 捕获后端 422：命中则回填字段错误并返回 true；非 422 只提示不映射 */
  captureError: (error: unknown) => boolean;
}

/**
 * 后端 422 字段级错误 → 表单字段的统一映射。
 * 非 ApiError 才就地提示；ApiError 的非 422 分支由响应拦截器统一提示。
 */
export function useFieldErrors(): UseFieldErrorsResult {
  const fieldErrors = ref<Record<string, string>>({});

  function fieldError(field: string): string {
    return fieldErrors.value[field] ?? '';
  }

  function clearErrors(): void {
    fieldErrors.value = {};
  }

  function captureError(error: unknown): boolean {
    clearErrors();
    if (error instanceof ApiError && error.status === 422) {
      for (const item of error.errors) {
        fieldErrors.value[item.field] = item.message;
      }
      return true;
    }
    if (error instanceof Error && !(error instanceof ApiError)) {
      ElMessage.error(error.message);
    }
    return false;
  }

  return { fieldErrors, fieldError, clearErrors, captureError };
}
