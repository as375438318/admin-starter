package com.adminstarter.system.application.export;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.domain.model.ExportTask;
import com.adminstarter.system.domain.model.ExportTaskStatus;
import com.adminstarter.system.domain.repository.ExportTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 导出任务管理：创建、列表、取消、下载、过期清理与启动补偿。
 * <p>临时文件根目录固定为 {@code java.io.tmpdir/admin-starter/exports}，按用户隔离，
 * 不写入 sys_file，因此不会出现在文件管理列表或回收站。</p>
 */
@Service
@RequiredArgsConstructor
public class ExportTaskService {

    public static final Duration FILE_TTL = Duration.ofHours(24);
    /** RUNNING 超过该时长视为进程中断（正常 10 万行远小于此值）。 */
    public static final Duration RUNNING_TIMEOUT = Duration.ofHours(2);
    public static final Path TEMP_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "admin-starter", "exports");

    private final ExportTaskRepository exportTaskRepository;

    @Transactional
    public ExportTask create(Long userId, String username, String module, String fileName,
                             String queryParams, long totalRows) {
        ExportTask task = new ExportTask();
        task.setUserId(userId);
        task.setUsername(username);
        task.setModule(module);
        task.setFileName(fileName);
        task.setQueryParams(queryParams);
        task.setTotalRows(totalRows);
        task.setProgress(0);
        task.setStatus(ExportTaskStatus.PENDING);
        task.setCancelRequested(false);
        exportTaskRepository.insert(task);
        return task;
    }

    public ExportTask find(Long taskId) {
        ExportTask task = exportTaskRepository.findById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        return task;
    }

    public boolean tryMarkRunning(Long taskId) {
        return exportTaskRepository.markRunning(taskId);
    }

    public void updateProgress(Long taskId, int progress) {
        exportTaskRepository.updateProgress(taskId, progress);
    }

    public boolean isCanceled(Long taskId) {
        return find(taskId).isCancelRequested();
    }

    public ExportTaskItem getOwned(Long userId, Long taskId) {
        return toItem(requireOwned(userId, taskId));
    }

    public PageResult<ExportTaskItem> page(Long userId, int page, int pageSize) {
        PageResult<ExportTask> result = exportTaskRepository.pageByUser(userId, page, pageSize);
        List<ExportTaskItem> items = result.getList().stream().map(this::toItem).toList();
        return PageResult.of(items, result.getTotal());
    }

    @Transactional
    public ExportTaskItem cancel(Long userId, Long taskId) {
        ExportTask task = requireOwned(userId, taskId);
        if (task.getStatus() == ExportTaskStatus.PENDING) {
            exportTaskRepository.markCanceledIfPending(taskId);
        } else if (task.getStatus() == ExportTaskStatus.RUNNING) {
            exportTaskRepository.markCancelRequested(taskId);
        } else {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务已结束，无法取消");
        }
        return toItem(find(taskId));
    }

    public Path file(Long userId, Long taskId) {
        ExportTask task = requireOwned(userId, taskId);
        if (task.getStatus() == ExportTaskStatus.SUCCESS
                && task.getExpiresAt() != null && task.getExpiresAt().isBefore(utcNow())) {
            expire(task);
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "导出文件已过期，请重新导出");
        }
        if (task.getStatus() != ExportTaskStatus.SUCCESS) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务尚未成功，无法下载");
        }
        Path path = task.getFilePath() == null ? null : Path.of(task.getFilePath());
        if (path == null || !Files.exists(path)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "导出文件不存在，请重新导出");
        }
        return path;
    }

    public Path tempPath(Long userId, Long taskId) {
        return TEMP_ROOT.resolve(String.valueOf(userId)).resolve(taskId + ".xlsx");
    }

    public void finishSuccess(Long taskId, Path file, long size) {
        ExportTask task = find(taskId);
        task.setStatus(ExportTaskStatus.SUCCESS);
        task.setProgress(100);
        task.setFilePath(file.toString());
        task.setFileSize(size);
        task.setExpiresAt(utcNow().plus(FILE_TTL));
        task.setErrorMessage(null);
        exportTaskRepository.update(task);
    }

    public void finishFailed(Long taskId, String message) {
        ExportTask task = find(taskId);
        task.setStatus(ExportTaskStatus.FAILED);
        task.setErrorMessage(message == null ? "导出失败" : message.substring(0, Math.min(message.length(), 500)));
        exportTaskRepository.update(task);
    }

    public void finishCanceled(Long taskId) {
        ExportTask task = find(taskId);
        task.setStatus(ExportTaskStatus.CANCELED);
        exportTaskRepository.update(task);
    }

    @Transactional
    public int cleanupExpired(int limit) {
        List<ExportTask> expired = exportTaskRepository.findExpiredSuccess(Instant.now(), limit);
        expired.forEach(this::expire);
        return expired.size();
    }

    @Transactional
    public int compensateInterrupted() {
        List<ExportTask> unfinished = exportTaskRepository.findUnfinishedBefore(
                Instant.now().minus(RUNNING_TIMEOUT));
        for (ExportTask task : unfinished) {
            if (task.getFilePath() != null) {
                deleteQuietly(Path.of(task.getFilePath()));
            }
            task.setStatus(ExportTaskStatus.FAILED);
            task.setErrorMessage("服务重启，导出任务已中断，请重新导出");
            exportTaskRepository.update(task);
        }
        return unfinished.size();
    }

    public ExportTaskItem toItem(ExportTask task) {
        ExportTaskItem item = new ExportTaskItem();
        item.setId(task.getId());
        item.setFileName(task.getFileName());
        item.setTotalRows(task.getTotalRows());
        item.setProgress(task.getProgress());
        item.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        item.setCancelRequested(task.isCancelRequested());
        item.setErrorMessage(task.getErrorMessage());
        item.setFileSize(task.getFileSize());
        item.setCreatedAt(toInstant(task.getCreatedAt()));
        item.setExpiresAt(toInstant(task.getExpiresAt()));
        return item;
    }

    private ExportTask requireOwned(Long userId, Long taskId) {
        ExportTask task = find(taskId);
        if (!task.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        return task;
    }

    private void expire(ExportTask task) {
        exportTaskRepository.markExpired(task.getId());
        if (task.getFilePath() != null) {
            deleteQuietly(Path.of(task.getFilePath()));
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不阻断，下次任务继续尝试
        }
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
