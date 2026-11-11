import { ref, type Ref } from 'vue';
import { ElMessage, type UploadRequestOptions } from 'element-plus';
import { fileApi, type ChunkSession } from '@/api/file';
import { ApiError } from '@/api/http';
import type { FileItem } from '@/api/types';
import { sha256Of } from '@/utils/sha256';
import { useAuthStore } from '@/stores/auth';

const CHUNK_THRESHOLD = 5 * 1024 * 1024;
const CHUNK_SIZE = 2 * 1024 * 1024;
const RESUME_PREFIX = 'file-upload:';
const MAX_VISIBLE_TASKS = 6;

export interface UploadTask {
  id: number;
  name: string;
  percent: number;
  status: '' | 'success' | 'exception';
  hint: string;
}

export interface UseChunkUploadOptions {
  /** 当前业务上传上限（字节） */
  maxSize: () => number;
  /** 上限文案，用于超限提示 */
  limitText: () => string;
  /** 服务端 413 返回新上限时回写本地配置 */
  onMaxSizeUpdate?: (maxSize: number) => void;
  /** 单个文件上传成功后的刷新动作 */
  onUploaded: () => Promise<void>;
}

export interface UseChunkUploadResult {
  tasks: Ref<UploadTask[]>;
  activeUploads: Ref<number>;
  beforeUpload: (file: File) => boolean;
  handleUpload: (options: UploadRequestOptions) => Promise<void>;
  uploadFiles: (files: File[]) => Promise<void>;
  clearTasks: () => void;
}

/**
 * 分片上传 / 断点续传 / 秒传 / 并发计数。
 * 断点续传靠 localStorage 记录 uploadId，刷新页面后按 sha256+size 续传。
 */
export function useChunkUpload(options: UseChunkUploadOptions): UseChunkUploadResult {
  const tasks = ref<UploadTask[]>([]);
  const activeUploads = ref(0);
  let taskSeq = 0;

  function beforeUpload(file: File): boolean {
    if (file.size > options.maxSize()) {
      ElMessage.error(`单个文件最大 ${options.limitText()}`);
      return false;
    }
    return true;
  }

  function createTask(name: string): UploadTask {
    const task: UploadTask = { id: (taskSeq += 1), name, percent: 0, status: '', hint: '准备中' };
    tasks.value = [task, ...tasks.value].slice(0, MAX_VISIBLE_TASKS);
    activeUploads.value += 1;
    return task;
  }

  /**
   * 续传键必须带用户标识：localStorage 是浏览器级共享的，不带 userId 时换账号后会命中
   * 上一个账号的 uploadId，后端按 uploader_id 校验后直接 403，表现为「续传莫名失败」。
   */
  function resumeKey(sha256: string, size: number): string {
    const userId = useAuthStore().profile?.userId ?? 'anonymous';
    return `${RESUME_PREFIX}${userId}:${size}:${sha256}`;
  }

  async function uploadChunked(file: File, sha256: string, task: UploadTask): Promise<FileItem> {
    let uploadId = localStorage.getItem(resumeKey(sha256, file.size));
    let session: ChunkSession | null = null;
    if (uploadId) {
      try {
        session = await fileApi.chunkStatus(uploadId);
        if (session.instant || !session.uploadId) {
          session = null;
          uploadId = null;
        }
      } catch {
        // 会话不可用（过期/不属于当前用户/已合并）：清掉本地记录，重新开一条
        localStorage.removeItem(resumeKey(sha256, file.size));
        uploadId = null;
      }
    }
    if (!session || !uploadId) {
      const init = await fileApi.chunkInit({
        fileName: file.name,
        size: file.size,
        contentType: file.type || null,
        sha256,
        chunkSize: CHUNK_SIZE,
      });
      if (init.instant && init.file) {
        task.percent = 100;
        task.status = 'success';
        task.hint = '秒传完成';
        return init.file;
      }
      if (!init.uploadId) {
        throw new ApiError(50001, '分片会话创建失败', 500);
      }
      uploadId = init.uploadId;
      session = init;
      localStorage.setItem(resumeKey(sha256, file.size), uploadId);
    }
    const uploaded = new Set(session.uploadedChunks);
    const done = uploaded.size;
    const totalChunks = session.totalChunks;
    task.percent = Math.min(90, 5 + Math.round((done / totalChunks) * 85));
    task.hint = done > 0 ? `续传（已完成 ${done}/${totalChunks} 片）` : '分片上传中';
    for (let index = 0; index < totalChunks; index += 1) {
      if (uploaded.has(index)) {
        continue;
      }
      const start = index * session.chunkSize;
      const end = Math.min(start + session.chunkSize, file.size);
      await fileApi.uploadChunk(uploadId, index, file.slice(start, end), (percent) => {
        task.percent = Math.min(90, 5 + Math.round(((index + percent / 100) / totalChunks) * 85));
      });
    }
    task.percent = 95;
    task.hint = '校验合并中';
    const item = await fileApi.completeChunk(uploadId);
    localStorage.removeItem(resumeKey(sha256, file.size));
    task.percent = 100;
    task.status = 'success';
    task.hint = '分片上传完成';
    return item;
  }

  async function uploadOne(file: File, task: UploadTask): Promise<FileItem> {
    const sha256 = await sha256Of(file);
    task.percent = 5;
    const instant = await fileApi.instant({
      fileName: file.name,
      size: file.size,
      contentType: file.type || null,
      sha256,
    });
    if (instant.instant && instant.file) {
      task.percent = 100;
      task.status = 'success';
      task.hint = '秒传完成';
      return instant.file;
    }
    if (file.size > CHUNK_THRESHOLD) {
      return uploadChunked(file, sha256, task);
    }
    const item = await fileApi.upload(file, (percent) => {
      task.percent = Math.max(5, percent);
      task.hint = '上传中';
    });
    task.percent = 100;
    task.status = 'success';
    task.hint = '上传完成';
    return item;
  }

  async function startUpload(file: File): Promise<void> {
    const task = createTask(file.name);
    try {
      await uploadOne(file, task);
      ElMessage.success(`「${file.name}」${task.hint}`);
      await options.onUploaded();
    } catch (error) {
      task.status = 'exception';
      task.hint = '上传失败';
      if (error instanceof ApiError) {
        // 413 兜底：按服务端返回的上限刷新本地配置，只提示一次，不触发登出。
        if (error.maxSize) {
          options.onMaxSizeUpdate?.(error.maxSize);
        }
        task.hint = error.message;
        ElMessage.error(error.message);
      } else {
        task.hint = '上传失败';
        ElMessage.error(`「${file.name}」上传失败`);
      }
    } finally {
      activeUploads.value = Math.max(0, activeUploads.value - 1);
    }
  }

  async function handleUpload(request: UploadRequestOptions): Promise<void> {
    await startUpload(request.file);
  }

  async function uploadFiles(files: File[]): Promise<void> {
    const accepted = files.filter((file) => beforeUpload(file));
    for (const file of accepted) {
      await startUpload(file);
    }
  }

  function clearTasks(): void {
    tasks.value = [];
  }

  return { tasks, activeUploads, beforeUpload, handleUpload, uploadFiles, clearTasks };
}
