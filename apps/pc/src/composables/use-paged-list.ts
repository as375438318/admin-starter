import { computed, reactive, ref, type Ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { ApiError } from '@/api/http';
import type { PageData, PageParams } from '@/api/types';
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination';
import { useDelayedSkeleton } from '@/utils/skeleton';

/** 分页状态：页面模板直接绑定 `page.page` / `page.pageSize` / `page.total` */
export interface PagedState {
  page: number;
  pageSize: number;
  total: number;
}

export interface UsePagedListOptions<T, F extends object> {
  /** 请求函数，入参为「筛选条件 + 分页参数」 */
  fetcher: (params: F & PageParams) => Promise<PageData<T>>;
  /** 筛选条件初始值，同时作为重置与「是否存在筛选」的基准 */
  initialFilters: F;
  /** 默认每页条数，缺省取全局常量 */
  pageSize?: number;
  /** 非 ApiError 时的兜底错误文案 */
  errorMessage?: string;
  /** 自定义错误处理：提供后不再写 loadError，由页面自行提示 */
  onError?: (error: unknown) => void;
}

export interface UsePagedListResult<T, F extends object> {
  page: PagedState;
  filters: F;
  rows: Ref<T[]>;
  loading: Ref<boolean>;
  loadError: Ref<string>;
  skeletonVisible: Ref<boolean>;
  hasFilter: Ref<boolean>;
  load: () => Promise<void>;
  search: () => void;
  resetFilters: () => void;
  onPageSizeChange: (size: number) => void;
  onPageChange: (current: number) => void;
}

/**
 * 列表页统一骨架：分页、筛选、加载态、空态与重置。
 * 页面只保留列定义、操作按钮与业务文案，不再各自声明 page/filters/load。
 */
export function usePagedList<T, F extends object>(options: UsePagedListOptions<T, F>): UsePagedListResult<T, F> {
  const page = reactive<PagedState>({
    page: 1,
    pageSize: options.pageSize ?? DEFAULT_PAGE_SIZE,
    total: 0,
  });
  const initial = { ...options.initialFilters } as F;
  const filters = reactive({ ...options.initialFilters }) as unknown as F;
  const rows = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const loadError = ref('');
  const skeletonVisible = useDelayedSkeleton(loading);
  const hasFilter = computed(() => {
    const current = filters as unknown as Record<string, unknown>;
    const base = initial as unknown as Record<string, unknown>;
    return Object.keys(base).some((key) => current[key] !== base[key]);
  });

  async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
      const params = { ...filters, page: page.page, pageSize: page.pageSize } as F & PageParams;
      const data = await options.fetcher(params);
      rows.value = data.list;
      page.total = data.total;
    } catch (error) {
      rows.value = [];
      page.total = 0;
      if (options.onError) {
        options.onError(error);
      } else {
        loadError.value = error instanceof ApiError ? error.message : (options.errorMessage ?? '列表加载失败');
      }
    } finally {
      loading.value = false;
    }
  }

  function search(): void {
    page.page = 1;
    void load();
  }

  function resetFilters(): void {
    Object.assign(filters, { ...initial });
    page.page = 1;
    void load();
  }

  function onPageSizeChange(size: number): void {
    page.pageSize = size;
    page.page = 1;
    void load();
  }

  function onPageChange(current: number): void {
    page.page = current;
    void load();
  }

  return {
    page,
    filters,
    rows,
    loading,
    loadError,
    skeletonVisible,
    hasFilter,
    load,
    search,
    resetFilters,
    onPageSizeChange,
    onPageChange,
  };
}

export interface ConfirmDeleteOptions {
  message: string;
  title: string;
  confirmText?: string;
  customClass?: string;
}

/** 删除确认统一实现：取消不抛错，返回 false 由调用方直接返回 */
export async function confirmDelete(options: ConfirmDeleteOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      type: 'warning',
      confirmButtonText: options.confirmText ?? '删除',
      cancelButtonText: '取消',
      closeOnClickModal: false,
      customClass: options.customClass,
    });
    return true;
  } catch {
    return false;
  }
}
