import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h, nextTick } from 'vue';
import { mount } from '@vue/test-utils';

const spies = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { error: spies.error } }));

import type { FileItem } from '@/api/types';
import { useBlobPreview } from './use-blob-preview';

const IMAGE = { id: 1, originalName: 'photo.PNG', contentType: 'image/png' } as FileItem;
const PDF_BY_NAME = { id: 2, originalName: '合同.pdf', contentType: null } as FileItem;
const PDF_BY_TYPE = { id: 3, originalName: 'noext', contentType: 'application/pdf' } as FileItem;

function mountPreview() {
  let api!: ReturnType<typeof useBlobPreview>;
  const wrapper = mount(defineComponent({
    setup() {
      api = useBlobPreview({ previewUrl: (id) => `/api/v1/files/${id}/preview` });
      return () => h('div');
    },
  }));
  return { api, wrapper };
}

describe('useBlobPreview（Blob 预览与 objectURL 回收）', () => {
  let createSpy: ReturnType<typeof vi.fn>;
  let revokeSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    spies.error.mockClear();
    let seq = 0;
    createSpy = vi.fn(() => `blob:mock-${seq += 1}`);
    revokeSpy = vi.fn();
    URL.createObjectURL = createSpy as never;
    URL.revokeObjectURL = revokeSpy as never;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('图片按扩展名判定，直接展示不回源（省一次请求）', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const { api } = mountPreview();

    await api.open(IMAGE);

    expect(api.isImage.value).toBe(true);
    expect(api.visible.value).toBe(true);
    expect(api.blobUrl.value).toBe('');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('非图片回源取 Blob 并生成本地 URL', async () => {
    const blob = new Blob(['pdf']);
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, blob: async () => blob })));
    const { api } = mountPreview();

    await api.open(PDF_BY_NAME);

    expect(api.isImage.value).toBe(false);
    expect(fetch).toHaveBeenCalledWith('/api/v1/files/2/preview', { credentials: 'include' });
    expect(createSpy).toHaveBeenCalledWith(blob);
    expect(api.blobUrl.value).toBe('blob:mock-1');
    expect(api.loading.value).toBe(false);
  });

  it('无扩展名时按 contentType 判断图片', () => {
    const { api } = mountPreview();
    api.row.value = { id: 9, originalName: 'noext', contentType: 'image/jpeg' } as FileItem;
    expect(api.isImage.value).toBe(true);

    api.row.value = PDF_BY_TYPE;
    expect(api.isImage.value).toBe(false);
  });

  it('HTTP 失败提示状态码、关闭预览并停止 loading', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));
    const { api } = mountPreview();

    await api.open(PDF_BY_TYPE);

    expect(spies.error).toHaveBeenCalledWith('预览失败（HTTP 404）');
    expect(api.visible.value).toBe(false);
    expect(api.loading.value).toBe(false);
  });

  it('打开新文件前回收上一个 objectURL，避免内存泄漏', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, blob: async () => new Blob(['x']) })));
    const { api } = mountPreview();
    await api.open(PDF_BY_NAME);
    expect(api.blobUrl.value).toBe('blob:mock-1');

    await api.open({ ...PDF_BY_NAME, id: 4 } as FileItem);

    expect(revokeSpy).toHaveBeenCalledWith('blob:mock-1');
    expect(api.blobUrl.value).toBe('blob:mock-2');
  });

  it('关闭抽屉与组件卸载都会回收 objectURL', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, blob: async () => new Blob(['x']) })));
    const { api, wrapper } = mountPreview();
    await api.open(PDF_BY_NAME);

    api.close();
    expect(api.visible.value).toBe(false);

    await api.open({ ...PDF_BY_NAME, id: 5 } as FileItem);
    wrapper.unmount();
    await nextTick();

    // 卸载时回收的是"当前"这个 URL（第二次打开生成的 blob:mock-2）
    expect(revokeSpy).toHaveBeenLastCalledWith('blob:mock-2');
    expect(api.blobUrl.value).toBe('');
  });
});
