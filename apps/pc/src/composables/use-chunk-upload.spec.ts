import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/http';

const spies = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}));

vi.mock('element-plus', () => ({ ElMessage: { success: spies.success, error: spies.error } }));
vi.mock('@/utils/sha256', () => ({ sha256Of: async () => 'sha256-fixed' }));
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ profile: { userId: 7 } }) }));

const fileApiMock = vi.hoisted(() => ({
  instant: vi.fn(),
  upload: vi.fn(),
  chunkInit: vi.fn(),
  chunkStatus: vi.fn(),
  uploadChunk: vi.fn(),
  completeChunk: vi.fn(),
}));
vi.mock('@/api/file', () => ({ fileApi: fileApiMock }));

import { useChunkUpload } from './use-chunk-upload';

const CHUNK_THRESHOLD = 5 * 1024 * 1024;

function makeFile(size: number, name = 'demo.bin'): File {
  return new File([new Uint8Array(size)], name, { type: 'application/octet-stream' });
}

function mountComposable(overrides: Partial<Parameters<typeof useChunkUpload>[0]> = {}) {
  return useChunkUpload({
    maxSize: () => 10 * 1024 * 1024,
    limitText: () => '10MB',
    onUploaded: vi.fn(async () => undefined),
    onMaxSizeUpdate: vi.fn(),
    ...overrides,
  });
}

describe('useChunkUpload（分片/秒传/续传）', () => {
  beforeEach(() => {
    localStorage.clear();
    Object.values(fileApiMock).forEach((fn) => fn.mockReset());
    spies.success.mockClear();
    spies.error.mockClear();
  });

  it('超过上限的文件在校验阶段被拒绝，不发请求', async () => {
    const upload = mountComposable({ maxSize: () => 10 });
    const ok = upload.beforeUpload(makeFile(20));

    expect(ok).toBe(false);
    expect(spies.error).toHaveBeenCalledTimes(1);
    expect(fileApiMock.instant).not.toHaveBeenCalled();
  });

  it('小文件直传成功：任务完成、回调触发、并发计数归零', async () => {
    fileApiMock.instant.mockResolvedValue({ instant: false });
    fileApiMock.upload.mockImplementation(async (_file: File, onProgress: (p: number) => void) => {
      onProgress(60);
      return { id: 1, originalName: 'demo.bin' };
    });
    const onUploaded = vi.fn(async () => undefined);
    const upload = mountComposable({ onUploaded });

    await upload.uploadFiles([makeFile(1024)]);

    expect(upload.tasks.value[0].status).toBe('success');
    expect(upload.tasks.value[0].hint).toBe('上传完成');
    expect(upload.activeUploads.value).toBe(0);
    expect(onUploaded).toHaveBeenCalledTimes(1);
  });

  it('秒传命中不发上传请求', async () => {
    fileApiMock.instant.mockResolvedValue({ instant: true, file: { id: 2, originalName: 'demo.bin' } });
    const upload = mountComposable();

    await upload.uploadFiles([makeFile(1024)]);

    expect(upload.tasks.value[0].hint).toBe('秒传完成');
    expect(fileApiMock.upload).not.toHaveBeenCalled();
  });

  it('413 兜底：把服务端上限回灌给配置，并只提示一次', async () => {
    fileApiMock.instant.mockResolvedValue({ instant: false });
    fileApiMock.upload.mockRejectedValue(new ApiError(41301, '文件过大 (20MB)', 413, null, [], 20));
    const onMaxSizeUpdate = vi.fn();
    const upload = mountComposable({ onMaxSizeUpdate });

    await upload.uploadFiles([makeFile(1024)]);

    expect(onMaxSizeUpdate).toHaveBeenCalledWith(20);
    expect(spies.error).toHaveBeenCalledWith('文件过大 (20MB)');
    expect(spies.error).toHaveBeenCalledTimes(1);
    expect(upload.tasks.value[0].status).toBe('exception');
  });

  it('非 ApiError 失败：任务标记 exception 并提示通用文案', async () => {
    fileApiMock.instant.mockRejectedValue(new Error('网络断开'));
    const upload = mountComposable();

    await upload.uploadFiles([makeFile(1024)]);

    expect(upload.tasks.value[0].hint).toBe('上传失败');
    expect(spies.error).toHaveBeenCalledWith('「demo.bin」上传失败');
    expect(upload.activeUploads.value).toBe(0);
  });

  it('大文件走分片：续传键带用户维度，完成后清除本地记录', async () => {
    fileApiMock.instant.mockResolvedValue({ instant: false });
    fileApiMock.chunkInit.mockResolvedValue({
      uploadId: 'up-1', chunkSize: 2 * 1024 * 1024, totalChunks: 3, uploadedChunks: [], instant: false,
    });
    fileApiMock.uploadChunk.mockResolvedValue(undefined);
    fileApiMock.completeChunk.mockResolvedValue({ id: 3, originalName: 'demo.bin' });
    const upload = mountComposable();

    await upload.uploadFiles([makeFile(CHUNK_THRESHOLD + 1)]);

    const keys = Object.keys(localStorage);
    expect(keys).toHaveLength(0); // 完成后已清除
    expect(fileApiMock.uploadChunk).toHaveBeenCalledTimes(3);
    expect(upload.tasks.value[0].status).toBe('success');
  });

  it('续传：本地存有会话时跳过已上传分片；键格式含 userId 与文件指纹', async () => {
    // 断点信息由前一次上传写入，键必须带 userId，否则换账号会命中别人的 uploadId
    const key = `file-upload:7:${CHUNK_THRESHOLD + 1}:sha256-fixed`;
    localStorage.setItem(key, 'up-9');
    fileApiMock.instant.mockResolvedValue({ instant: false });
    fileApiMock.chunkStatus.mockResolvedValue({
      uploadId: 'up-9', chunkSize: 2 * 1024 * 1024, totalChunks: 3, uploadedChunks: [0], instant: false,
    });
    fileApiMock.uploadChunk.mockResolvedValue(undefined);
    fileApiMock.completeChunk.mockResolvedValue({ id: 4, originalName: 'demo.bin' });
    const upload = mountComposable();

    await upload.uploadFiles([makeFile(CHUNK_THRESHOLD + 1)]);

    expect(fileApiMock.chunkInit).not.toHaveBeenCalled();
    expect(fileApiMock.uploadChunk).toHaveBeenCalledTimes(2); // 分片 0 已在服务端
    expect(localStorage.getItem(key)).toBeNull();
  });

  it('续传会话不可用（过期/不属于当前用户）时清掉本地键并重新开会话', async () => {
    const staleKey = `file-upload:7:${CHUNK_THRESHOLD + 1}:sha256-fixed`;
    localStorage.setItem(staleKey, 'up-stale');
    fileApiMock.instant.mockResolvedValue({ instant: false });
    fileApiMock.chunkStatus.mockRejectedValue(new ApiError(20401, '文件不存在', 404));
    fileApiMock.chunkInit.mockResolvedValue({
      uploadId: 'up-new', chunkSize: 2 * 1024 * 1024, totalChunks: 3, uploadedChunks: [], instant: false,
    });
    fileApiMock.uploadChunk.mockResolvedValue(undefined);
    fileApiMock.completeChunk.mockResolvedValue({ id: 5, originalName: 'demo.bin' });
    const upload = mountComposable();

    await upload.uploadFiles([makeFile(CHUNK_THRESHOLD + 1)]);

    expect(fileApiMock.chunkInit).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem(staleKey)).toBeNull();
  });

  it('clearTasks 清空可见任务，不影响并发计数', () => {
    const upload = mountComposable();
    upload.tasks.value = [{ id: 1, name: 'a', percent: 100, status: 'success', hint: '完成' }];

    upload.clearTasks();

    expect(upload.tasks.value).toHaveLength(0);
  });
});
