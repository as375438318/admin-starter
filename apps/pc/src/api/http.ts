import axios, { type AxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';
import type { ApiEnvelope, FieldError, ImportErrorData } from './types';

export class ApiError extends Error {
  readonly code: number;
  readonly status: number;
  readonly traceId: string | null;
  readonly errors: FieldError[];
  /** 413 上传超限时携带的服务端当前上限（字节），来自 data.maxSize；其余场景为 null */
  readonly maxSize: number | null;
  /** 导入校验失败时携带的三个数字与错误清单，来自 data；其余场景为 null */
  readonly importData: ImportErrorData | null;

  constructor(code: number, message: string, status = 200, traceId: string | null = null,
              errors: FieldError[] = [], maxSize: number | null = null,
              importData: ImportErrorData | null = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.traceId = traceId;
    this.errors = errors;
    this.maxSize = maxSize;
    this.importData = importData;
  }
}

interface RetryConfig extends AxiosRequestConfig {
  _retry?: boolean;
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
});

let refreshing: Promise<void> | null = null;

/** 401 分码：这几类不再尝试刷新令牌，直接回登录页并展示对应原因。 */
const TERMINAL_AUTH_CODES = new Set([20103, 20104, 20106, 20107, 20108]);

/**
 * 只有登录与刷新接口本身不参与"401 → 静默刷新"：
 * 登录 401 表示账号口令错误，必须由表单就地展示；刷新接口若再走刷新会自我递归。
 * 其余 `/auth/*`（me / routes / permissions / sessions）与其它业务接口一样先尝试刷新，
 * 否则 access 过期后刷新页面会被误判成未登录（refresh 仍有 7 天有效期）。
 */
export function isAuthEntry(url: string): boolean {
  return url.startsWith('/auth/login') || url.startsWith('/auth/refresh');
}

/** 401 分码是否属于"刷新也无用、必须回登录页"的终止类。 */
export function isTerminalAuthCode(code?: number): boolean {
  return code !== undefined && TERMINAL_AUTH_CODES.has(code);
}

export type UnauthorizedAction = 'ignore' | 'terminal' | 'refresh' | 'reject';

/**
 * 401 的处置决策（纯函数，便于单测；`http` 拦截器只是它的执行者）。
 * - `ignore`：不是 401，或已经重试过 → 交给后续分支
 * - `terminal`：401 且是终止分码（停用/删除/重置/改密/被踢）→ 刷新无意义，回登录页并展示原因
 * - `refresh`：普通 401 → 先静默刷新再重放
 * - `reject`：登录/刷新接口自身 401 → 不刷新，就地提示（避免登录页被整页重定向吞掉、避免递归）
 */
export function planOn401(input: {
  status?: number;
  retried?: boolean;
  url?: string;
  code?: number;
}): UnauthorizedAction {
  const { status, retried, url = '', code } = input;
  if (status !== 401 || retried) {
    return 'ignore';
  }
  if (isTerminalAuthCode(code)) {
    return 'terminal';
  }
  if (isAuthEntry(url)) {
    return 'reject';
  }
  return 'refresh';
}

export const AUTH_NOTICE_KEY = 'auth:notice';

function messageWithTrace(message: string, traceId?: string | null): string {
  return traceId ? `${message}（traceId: ${traceId}）` : message;
}

async function refreshAccessToken(): Promise<void> {
  if (!refreshing) {
    refreshing = axios
      .post<ApiEnvelope<unknown>>('/api/v1/auth/refresh', null, { withCredentials: true })
      .then((response) => {
        if (response.data?.code !== 0) {
          throw new ApiError(response.data?.code ?? 20101, response.data?.message ?? '刷新令牌失败', 200);
        }
      })
      .catch((error: { response?: { status: number; data?: ApiEnvelope<unknown> } }) => {
        const envelope = error.response?.data;
        throw new ApiError(envelope?.code ?? 20101, envelope?.message ?? '登录已过期，请重新登录',
          error.response?.status ?? 401, envelope?.traceId ?? null);
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

function redirectToLogin(notice?: string): void {
  if (notice) {
    try {
      sessionStorage.setItem(AUTH_NOTICE_KEY, notice);
    } catch {
      // 隐私模式下 sessionStorage 不可用，忽略即可
    }
  }
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

http.interceptors.response.use(
  (response) => {
    const envelope = response.data as ApiEnvelope<unknown>;
    if (envelope && typeof envelope === 'object' && typeof envelope.code === 'number') {
      if (envelope.code === 0) {
        return envelope.data;
      }
      const error = new ApiError(envelope.code, envelope.message, response.status, envelope.traceId);
      ElMessage.error(messageWithTrace(envelope.message, envelope.traceId));
      throw error;
    }
    return response.data;
  },
  async (error) => {
    const status = error.response?.status as number | undefined;
    const envelope = error.response?.data as ApiEnvelope<unknown> | undefined;
    const config = error.config as RetryConfig | undefined;
    const url = config?.url ?? '';

    const plan = planOn401({ status, retried: config?._retry, url, code: envelope?.code });

    if (plan === 'terminal') {
      // 账号被停用/删除、密码被重置或修改：刷新也无效，直接回登录页并展示具体原因
      redirectToLogin(envelope?.message);
      throw new ApiError(envelope?.code ?? 20101, envelope?.message ?? '登录状态已失效', 401,
        envelope?.traceId ?? null);
    }

    if (plan === 'refresh' && config) {
      config._retry = true;
      try {
        await refreshAccessToken();
        return http(config);
      } catch (refreshError) {
        const message = refreshError instanceof ApiError ? refreshError.message : '登录已过期，请重新登录';
        redirectToLogin(message);
        throw refreshError instanceof ApiError
          ? refreshError
          : new ApiError(20101, message, 401, envelope?.traceId ?? null);
      }
    }

    if (status === 401) {
      // 登录接口返回 401 表示账号或口令错误，必须由表单就地展示；
      // 其余 401 才跳登录页，否则登录页上的错误提示会被整页重定向吞掉。
      const message = envelope?.message ?? '未登录或登录已过期';
      if (!url.startsWith('/auth/login')) {
        redirectToLogin(message);
      }
      throw new ApiError(envelope?.code ?? 20101, message, 401, envelope?.traceId ?? null);
    }

    if (status === 403 && envelope?.code === 20302) {
      // 必须改密：服务端拦下了业务请求。置位标记并跳到改密页（不弹全局错误，避免首屏一串红条）
      const { useAuthStore } = await import('@/stores/auth');
      useAuthStore().setMustChangePassword(true);
      if (!window.location.pathname.startsWith('/profile')) {
        window.location.assign('/profile?mustChange=1');
      }
      throw new ApiError(20302, envelope?.message ?? '请先修改密码后再使用其他功能', 403,
        envelope?.traceId ?? null);
    }

    if (status === 422) {
      const data = envelope?.data as {
        errors?: FieldError[];
        totalRows?: number;
        errorRows?: number;
        errorCount?: number;
        remainingErrors?: number;
      } | undefined;
      const importData = data && typeof data.totalRows === 'number'
        ? {
            errors: data.errors ?? [],
            totalRows: data.totalRows,
            errorRows: data.errorRows ?? 0,
            errorCount: data.errorCount ?? 0,
            remainingErrors: data.remainingErrors ?? 0,
          }
        : null;
      throw new ApiError(envelope?.code ?? 30201, envelope?.message ?? '参数校验失败', 422,
        envelope?.traceId ?? null, data?.errors ?? [], null, importData);
    }

    if (status === 413) {
      // 上传超限：不弹全局提示、不触发登出，把 message 与 data.maxSize 交给文件页就地展示与刷新上限。
      const data = envelope?.data as { maxSize?: number } | null | undefined;
      throw new ApiError(envelope?.code ?? 10002, envelope?.message ?? '文件大小超过上限', 413,
        envelope?.traceId ?? null, [], typeof data?.maxSize === 'number' ? data.maxSize : null);
    }

    const message = envelope?.message ?? error.message ?? '请求失败';
    const traceId = envelope?.traceId ?? null;
    ElMessage.error(messageWithTrace(message, traceId));
    throw new ApiError(envelope?.code ?? 50001, message, status ?? 500, traceId);
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  return (await http.request(config)) as unknown as T;
}

export const api = {
  get<T>(url: string, params?: object): Promise<T> {
    return request<T>({ url, method: 'GET', params });
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return request<T>({ url, method: 'POST', data });
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return request<T>({ url, method: 'PUT', data });
  },
  delete<T>(url: string): Promise<T> {
    return request<T>({ url, method: 'DELETE' });
  },
  upload<T>(url: string, file: File, onProgress?: (percent: number) => void,
            timeout?: number): Promise<T> {
    const form = new FormData();
    form.append('file', file);
    return request<T>({
      url,
      method: 'POST',
      data: form,
      timeout,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
        ? (event) => {
            if (event.total) {
              onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
            }
          }
        : undefined,
    });
  },
  uploadChunk<T>(uploadId: string, index: number, chunk: Blob,
                 onProgress?: (percent: number) => void): Promise<T> {
    const form = new FormData();
    form.append('chunk', chunk);
    return request<T>({
      url: `/files/chunk-sessions/${uploadId}/chunks/${index}`,
      method: 'PUT',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
        ? (event) => {
            if (event.total) {
              onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
            }
          }
        : undefined,
    });
  },
};

export interface FileDownload {
  blob: Blob;
  fileName: string | null;
}

/** 同一请求两种返回体：xlsx 文件流或 JSON（异步任务受理）。 */
export type FileOrJson<T> =
  | { kind: 'file'; blob: Blob; fileName: string | null }
  | { kind: 'json'; data: T };

function buildQuery(params?: object): string {
  if (!params) {
    return '';
  }
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    search.append(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 文件类请求不走 axios 拦截器（需读取 Content-Type / Content-Disposition），401 时手动刷新一次。 */
async function authorizedFetch(url: string, params?: object): Promise<Response> {
  const target = `/api/v1${url}${buildQuery(params)}`;
  let response = await fetch(target, { credentials: 'include' });
  if (response.status === 401) {
    try {
      await refreshAccessToken();
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '登录已过期，请重新登录';
      redirectToLogin(message);
      throw error instanceof ApiError ? error : new ApiError(20101, message, 401);
    }
    response = await fetch(target, { credentials: 'include' });
  }
  return response;
}

async function readEnvelope(response: Response): Promise<ApiEnvelope<unknown> | null> {
  try {
    return (await response.json()) as ApiEnvelope<unknown>;
  } catch {
    return null;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const envelope = await readEnvelope(response);
  const message = envelope?.message ?? `请求失败（HTTP ${response.status}）`;
  if (response.status === 401) {
    redirectToLogin(message);
  }
  return new ApiError(envelope?.code ?? 50001, message, response.status, envelope?.traceId ?? null);
}

function parseFileName(disposition: string | null): string | null {
  if (!disposition) {
    return null;
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return encoded[1];
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? null;
}

function isXlsx(contentType: string): boolean {
  return contentType.toLowerCase().includes('spreadsheetml');
}

/** 只返回文件的下载（模板、导出任务文件）。 */
export async function downloadFile(url: string, params?: object): Promise<FileDownload> {
  const response = await authorizedFetch(url, params);
  if (!response.ok) {
    throw await toApiError(response);
  }
  return {
    blob: await response.blob(),
    fileName: parseFileName(response.headers.get('Content-Disposition')),
  };
}

/**
 * 导出入口：先看 Content-Type 判定返回体。
 * xlsx → 同步文件流；JSON → 异步任务受理（{taskId,totalRows}）。
 */
export async function downloadOrJson<T>(url: string, params?: object): Promise<FileOrJson<T>> {
  const response = await authorizedFetch(url, params);
  const contentType = response.headers.get('Content-Type') ?? '';
  if (response.ok && isXlsx(contentType)) {
    return {
      kind: 'file',
      blob: await response.blob(),
      fileName: parseFileName(response.headers.get('Content-Disposition')),
    };
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  const envelope = await readEnvelope(response);
  if (!envelope || envelope.code !== 0) {
    throw new ApiError(envelope?.code ?? 50001, envelope?.message ?? '导出失败',
      response.status, envelope?.traceId ?? null);
  }
  return { kind: 'json', data: envelope.data as T };
}

export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
