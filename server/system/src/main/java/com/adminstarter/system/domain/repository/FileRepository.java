package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.FileRecord;

import java.util.List;

public interface FileRepository {

    void insert(FileRecord record);

    FileRecord findById(Long id);

    /** 秒传：只匹配同一上传者，包含回收站行（物理字节仍在）。 */
    FileRecord findByShaAndUploader(String sha256, long size, Long uploaderId);

    /** 物理去重：匹配任意上传者的相同内容，包含回收站行。 */
    FileRecord findBySha(String sha256, long size);

    void moveToRecycle(Long id, Long userId);

    FileRecord findDeletedById(Long id);

    PageResult<FileRecord> page(String keyword, int page, int pageSize);

    PageResult<FileRecord> pageDeleted(String keyword, int page, int pageSize);

    boolean restore(Long id);

    boolean purge(Long id);

    long countByStoragePath(String storagePath);

    List<String> listAllStoragePaths();
}
