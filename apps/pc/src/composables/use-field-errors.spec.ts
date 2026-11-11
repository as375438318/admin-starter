import { beforeEach, describe, expect, it, vi } from 'vitest';

const { errorSpy } = vi.hoisted(() => ({ errorSpy: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { error: errorSpy } }));

// 必须在 mock 之后再 import 被测模块，避免真实 element-plus 被加载
import { ApiError } from '@/api/http';
import { useFieldErrors } from './use-field-errors';

describe('useFieldErrors（422 字段错误映射）', () => {
  beforeEach(() => {
    errorSpy.mockClear();
  });

  it('422 把 errors[] 映射到字段，返回 true，且不弹 toast（由拦截器统一提示）', () => {
    const { fieldErrors, fieldError, captureError } = useFieldErrors();
    const error = new ApiError(30201, '参数校验失败', 422, null, [
      { field: 'username', code: 'REQUIRED', message: '账号不能为空' },
      { field: 'email', code: 'INVALID_FORMAT', message: '邮箱格式不正确' },
    ]);

    expect(captureError(error)).toBe(true);
    expect(fieldErrors.value).toEqual({ username: '账号不能为空', email: '邮箱格式不正确' });
    expect(fieldError('username')).toBe('账号不能为空');
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('非 422 的 ApiError 不映射字段、不弹 toast（交给响应拦截器）', () => {
    const { captureError, fieldError } = useFieldErrors();
    const error = new ApiError(20301, '没有操作权限', 403);

    expect(captureError(error)).toBe(false);
    expect(fieldError('username')).toBe('');
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('非 ApiError 异常：提示一次运行时错误', () => {
    const { captureError } = useFieldErrors();

    expect(captureError(new Error('网络断开'))).toBe(false);
    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy).toHaveBeenCalledWith('网络断开');
  });

  it('再次捕获前清空上次的字段错误', () => {
    const { captureError, fieldErrors } = useFieldErrors();
    captureError(new ApiError(30201, '校验失败', 422, null, [
      { field: 'username', code: 'REQUIRED', message: '账号不能为空' },
    ]));
    expect(Object.keys(fieldErrors.value)).toHaveLength(1);

    captureError(new ApiError(30201, '校验失败', 422, null, [
      { field: 'nickname', code: 'REQUIRED', message: '姓名不能为空' },
    ]));
    expect(Object.keys(fieldErrors.value)).toEqual(['nickname']);
  });
});
