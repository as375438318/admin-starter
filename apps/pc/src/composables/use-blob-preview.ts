import { computed, onBeforeUnmount, ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FileItem } from '@/api/types';

export interface UseBlobPreviewOptions {
  /** 预览地址（返回同源 URL，由调用方从 api 层取） */
  previewUrl: (id: number) => string;
}

export interface UseBlobPreviewResult {
  visible: Ref<boolean>;
  row: Ref<FileItem | null>;
  blobUrl: Ref<string>;
  loading: Ref<boolean>;
  isImage: Ref<boolean>;
  open: (item: FileItem) => Promise<void>;
  close: () => void;
  revoke: () => void;
}

/**
 * Blob 预览：预览响应带 X-Frame-Options: DENY，iframe 直连会被浏览器拒绝，
 * 因此取 Blob 后本地渲染；objectURL 在关闭与卸载时回收，避免内存泄漏。
 */
export function useBlobPreview(options: UseBlobPreviewOptions): UseBlobPreviewResult {
  const visible = ref(false);
  const row = ref<FileItem | null>(null);
  const blobUrl = ref('');
  const loading = ref(false);
  const isImage = computed(() => {
    const name = row.value?.originalName ?? '';
    const extension = name.includes('.') ? name.split('.').pop()!.toLowerCase() : '';
    if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(extension)) {
      return true;
    }
    return (row.value?.contentType ?? '').startsWith('image/');
  });

  function revoke(): void {
    if (blobUrl.value) {
      URL.revokeObjectURL(blobUrl.value);
      blobUrl.value = '';
    }
  }

  async function open(item: FileItem): Promise<void> {
    revoke();
    row.value = item;
    visible.value = true;
    if (isImage.value) {
      return;
    }
    loading.value = true;
    try {
      const response = await fetch(options.previewUrl(item.id), { credentials: 'include' });
      if (!response.ok) {
        throw new Error(`预览失败（HTTP ${response.status}）`);
      }
      blobUrl.value = URL.createObjectURL(await response.blob());
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '预览失败');
      visible.value = false;
    } finally {
      loading.value = false;
    }
  }

  function close(): void {
    visible.value = false;
  }

  onBeforeUnmount(revoke);

  return { visible, row, blobUrl, loading, isImage, open, close, revoke };
}
