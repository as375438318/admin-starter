import { api } from './http';
import type { FileItem, PageData, PageParams } from './types';

export interface FileQuery extends PageParams {
  keyword?: string;
}

export interface InstantPayload {
  fileName: string;
  size: number;
  contentType?: string | null;
  sha256: string;
}

export interface ChunkInitPayload extends InstantPayload {
  chunkSize: number;
}

export interface ChunkSession {
  uploadId: string | null;
  chunkSize: number;
  totalChunks: number;
  uploadedChunks: number[];
  instant: boolean;
  file: FileItem | null;
}

export interface InstantResult {
  instant: boolean;
  file: FileItem | null;
}

export const fileApi = {
  page(params: FileQuery) {
    return api.get<PageData<FileItem>>('/files', params);
  },
  recycle(params: FileQuery) {
    return api.get<PageData<FileItem>>('/files/recycle', params);
  },
  upload(file: File, onProgress?: (percent: number) => void) {
    return api.upload<FileItem>('/files', file, onProgress);
  },
  instant(payload: InstantPayload) {
    return api.post<InstantResult>('/files/instant', payload);
  },
  chunkInit(payload: ChunkInitPayload) {
    return api.post<ChunkSession>('/files/chunk-sessions', payload);
  },
  chunkStatus(uploadId: string) {
    return api.get<ChunkSession>(`/files/chunk-sessions/${uploadId}`);
  },
  uploadChunk(uploadId: string, index: number, chunk: Blob, onProgress?: (percent: number) => void) {
    return api.uploadChunk<number[]>(uploadId, index, chunk, onProgress);
  },
  completeChunk(uploadId: string) {
    return api.post<FileItem>(`/files/chunk-sessions/${uploadId}/complete`);
  },
  abortChunk(uploadId: string) {
    return api.delete<void>(`/files/chunk-sessions/${uploadId}`);
  },
  remove(id: number) {
    return api.delete<void>(`/files/${id}`);
  },
  restore(id: number) {
    return api.post<void>(`/files/${id}/restore`);
  },
  purge(id: number) {
    return api.delete<void>(`/files/recycle/${id}`);
  },
  downloadUrl(id: number) {
    return `/api/v1/files/${id}/download`;
  },
  previewUrl(id: number) {
    return `/api/v1/files/${id}/preview`;
  },
};
