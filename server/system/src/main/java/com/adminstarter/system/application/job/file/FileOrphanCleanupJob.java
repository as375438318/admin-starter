package com.adminstarter.system.application.job.file;

import com.adminstarter.system.application.job.JobContext;
import com.adminstarter.system.application.job.JobResult;
import com.adminstarter.system.application.job.ScheduledJob;
import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.domain.storage.FileStorage;
import com.adminstarter.system.domain.storage.FileStorageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;

/**
 * 文件孤儿清理：清理超过 24 小时的临时分片、临时文件与无主物理文件。
 * <p>挂在既有定时任务模块，不新建调度器；多实例部署受既有单机调度事实限制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOrphanCleanupJob implements ScheduledJob {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final FileChunkUploadRepository chunkUploadRepository;
    private final FileRepository fileRepository;
    private final FileStorage fileStorage;

    @Override
    public String key() {
        return "file-orphan-cleanup";
    }

    @Override
    public String description() {
        return "文件孤儿清理：过期分片会话、临时文件与无主物理文件";
    }

    @Override
    public JobResult run(JobContext context) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minus(RETENTION);
        List<FileChunkUpload> stale = chunkUploadRepository.findStaleUploading(cutoff);
        for (FileChunkUpload session : stale) {
            fileStorage.deleteRecursively(FileStorageKeys.chunkRoot(session.getUploadId()));
            chunkUploadRepository.updateStatus(session.getUploadId(), "EXPIRED");
        }
        long tempDeleted = fileStorage.cleanupTemp(RETENTION);
        long orphanDeleted = fileStorage.cleanupOrphans(
                new HashSet<>(fileRepository.listAllStoragePaths()), RETENTION);
        long total = stale.size() + tempDeleted + orphanDeleted;
        String message = "过期分片会话 " + stale.size() + " 个，临时文件 " + tempDeleted
                + " 个，孤儿文件 " + orphanDeleted + " 个";
        log.info("file-orphan-cleanup 完成：{}", message);
        return new JobResult(total, message);
    }
}
