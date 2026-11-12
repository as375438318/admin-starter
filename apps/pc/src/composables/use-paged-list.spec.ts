import { describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

vi.mock('element-plus', () => ({ ElMessageBox: { confirm: vi.fn() } }));
vi.mock('@/utils/skeleton', () => ({ useDelayedSkeleton: () => ref(false) }));

import { ApiError } from '@/api/http';
import { usePagedList } from './use-paged-list';

interface Row {
  id: number;
  name: string;
}

describe('usePagedList（列表页统一骨架）', () => {
  it('加载成功写入行与总数', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [{ id: 1, name: 'a' }], total: 42 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });

    await list.load();

    expect(list.rows.value).toEqual([{ id: 1, name: 'a' }]);
    expect(list.page.total).toBe(42);
    expect(list.loadError.value).toBe('');
    expect(list.loading.value).toBe(false);
  });

  it('请求参数合并筛选与分页，且不回传 total', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({
      fetcher,
      initialFilters: { keyword: 'abc' },
      pageSize: 50,
    });

    await list.load();

    expect(fetcher).toHaveBeenCalledWith({ keyword: 'abc', page: 1, pageSize: 50 });
  });

  it('ApiError 失败时清空数据并把文案落到 loadError', async () => {
    const fetcher = vi.fn().mockRejectedValue(new ApiError(50001, '系统繁忙', 500));
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });

    await list.load();

    expect(list.rows.value).toEqual([]);
    expect(list.page.total).toBe(0);
    expect(list.loadError.value).toBe('系统繁忙');
  });

  it('提供 onError 时不再写 loadError（由页面自行提示）', async () => {
    const onError = vi.fn();
    const fetcher = vi.fn().mockRejectedValue(new ApiError(50001, '系统繁忙', 500));
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' }, onError });

    await list.load();

    expect(onError).toHaveBeenCalledTimes(1);
    expect(list.loadError.value).toBe('');
  });

  it('search 重置到第一页；翻页/改每页条数也会触发加载', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });

    list.page.page = 3;
    list.search();
    await Promise.resolve();
    expect(list.page.page).toBe(1);

    list.onPageChange(2);
    await Promise.resolve();
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '', page: 2, pageSize: 20 });

    list.onPageSizeChange(50);
    await Promise.resolve();
    expect(list.page.page).toBe(1);
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '', page: 1, pageSize: 50 });
  });

  it('hasFilter 只在筛选偏离初始值时变为 true，重置后恢复 false', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });

    expect(list.hasFilter.value).toBe(false);
    (list.filters as { keyword: string }).keyword = 'abc';
    expect(list.hasFilter.value).toBe(true);

    list.resetFilters();
    await Promise.resolve();
    expect(list.hasFilter.value).toBe(false);
  });
  it('排序：未设置时不带 sort/order（让后端走默认排序）', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });

    await list.load();

    const params = fetcher.mock.calls[0][0] as Record<string, unknown>;
    expect(params.sort).toBeUndefined();
    expect(params.order).toBeUndefined();
  });

  it('排序：onSortChange 写入 sort/order、回到第 1 页并重新加载', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });
    list.page.page = 3;

    list.onSortChange('createdAt', 'desc');
    await Promise.resolve();

    expect(list.sort.value).toBe('createdAt');
    expect(list.order.value).toBe('desc');
    // 排序变了还停在第 3 页会看到空结果
    expect(list.page.page).toBe(1);
    const params = fetcher.mock.calls[0][0] as Record<string, unknown>;
    expect(params.sort).toBe('createdAt');
    expect(params.order).toBe('desc');
  });

  it('排序：取消排序（undefined）不再回传 sort/order', async () => {
    const fetcher = vi.fn().mockResolvedValue({ list: [], total: 0 });
    const list = usePagedList<Row, { keyword: string }>({ fetcher, initialFilters: { keyword: '' } });
    list.onSortChange('nickname', 'asc');
    await Promise.resolve();
    list.onSortChange(undefined, undefined);
    await Promise.resolve();

    const lastParams = fetcher.mock.calls.at(-1)![0] as Record<string, unknown>;
    expect(lastParams.sort).toBeUndefined();
    expect(lastParams.order).toBeUndefined();
  });
});