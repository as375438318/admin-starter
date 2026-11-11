import { beforeEach, describe, expect, it, vi } from 'vitest';

const spies = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { error: spies.error, success: vi.fn() } }));

import { request, ApiError } from './http';

/** 用自定义 adapter 直通拦截器，不经过真实网络 */
function adapterReturning(body: unknown, status = 200) {
  return async (config: unknown) => ({ data: body, status, statusText: 'OK', headers: {}, config });
}

function adapterRejecting(body: unknown, status: number) {
  return async (config: unknown) => {
    throw Object.assign(new Error(`HTTP ${status}`), {
      response: { data: body, status, statusText: 'ERR', headers: {}, config },
      config,
      isAxiosError: true,
    });
  };
}

describe('http 响应拦截器', () => {
  beforeEach(() => {
    spies.error.mockClear();
  });

  it('code=0 时解包 data，调用方拿不到信封', async () => {
    const data = await request<{ id: number }>({
      url: '/users/1',
      adapter: adapterReturning({ code: 0, message: 'ok', data: { id: 1 }, traceId: null }) as never,
    });
    expect(data).toEqual({ id: 1 });
  });

  it('非信封响应（如 Blob）原样返回，不误判为业务错误', async () => {
    const blob = new Blob(['x']);
    const data = await request<Blob>({ url: '/files/1/download', adapter: adapterReturning(blob) as never });
    expect(data).toBe(blob);
  });

  it('code!=0 抛 ApiError，并提示带 traceId 的文案', async () => {
    await expect(request({
      url: '/users',
      adapter: adapterReturning({ code: 20501, message: '账号已存在', traceId: 'tr-1' }) as never,
    })).rejects.toMatchObject({ code: 20501, message: '账号已存在', traceId: 'tr-1' });

    expect(spies.error).toHaveBeenCalledWith('账号已存在（traceId: tr-1）');
  });

  it('422 把 errors[] 挂到 ApiError 上（表单就地回填用）', async () => {
    await expect(request({
      url: '/users',
      method: 'POST',
      adapter: adapterRejecting({
        code: 30201, message: '参数校验失败', traceId: 'tr-2',
        data: { errors: [{ field: 'username', code: 'REQUIRED', message: '账号不能为空' }] },
      }, 422) as never,
    })).rejects.toMatchObject({
      status: 422,
      code: 30201,
      errors: [{ field: 'username', code: 'REQUIRED', message: '账号不能为空' }],
    });
  });

  it('422 的导入错误载荷（totalRows 等）被整理到 importData', async () => {
    const error = await request({
      url: '/users/import',
      method: 'POST',
      adapter: adapterRejecting({
        code: 30201, message: '导入校验失败',
        data: {
          errors: [{ field: 'row2.username', code: 'DUPLICATE', message: '账号重复' }],
          totalRows: 100, errorRows: 3, errorCount: 5, remainingErrors: 2,
        },
      }, 422) as never,
    }).catch((e) => e) as ApiError;

    expect(error.importData).toEqual({
      errors: [{ field: 'row2.username', code: 'DUPLICATE', message: '账号重复' }],
      totalRows: 100,
      errorRows: 3,
      errorCount: 5,
      remainingErrors: 2,
    });
  });

  it('422 缺少 totalRows 时 importData 为 null（普通表单校验）', async () => {
    const error = await request({
      url: '/users',
      adapter: adapterRejecting({ code: 30201, message: '参数校验失败', data: { errors: [] } }, 422) as never,
    }).catch((e) => e) as ApiError;

    expect(error.importData).toBeNull();
  });

  it('413 携带服务端上限，且不弹全局提示（由文件页就地展示）', async () => {
    const error = await request({
      url: '/files',
      method: 'POST',
      adapter: adapterRejecting({ code: 10002, message: '文件过大', data: { maxSize: 10485760 } }, 413) as never,
    }).catch((e) => e) as ApiError;

    expect(error).toMatchObject({ status: 413, maxSize: 10485760 });
    expect(spies.error).not.toHaveBeenCalled();
  });

  it('其他 5xx：提示一次并抛 ApiError', async () => {
    await expect(request({
      url: '/users',
      adapter: adapterRejecting({ code: 50001, message: '系统繁忙', traceId: 'tr-3' }, 500) as never,
    })).rejects.toMatchObject({ status: 500, code: 50001 });

    expect(spies.error).toHaveBeenCalledWith('系统繁忙（traceId: tr-3）');
  });

  it('网络层错误（无 response）：兜底文案与 500 状态', async () => {
    await expect(request({
      url: '/users',
      adapter: (() => Promise.reject(Object.assign(new Error('Network Error'), { isAxiosError: true }))) as never,
    })).rejects.toMatchObject({ status: 500, message: 'Network Error' });
  });
});
