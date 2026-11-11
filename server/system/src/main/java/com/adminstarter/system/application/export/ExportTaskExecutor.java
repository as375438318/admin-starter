package com.adminstarter.system.application.export;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.AuthUserQueryService;
import com.adminstarter.system.application.NotificationCommand;
import com.adminstarter.system.application.NotificationPublisher;
import com.adminstarter.system.domain.model.ExportTask;
import com.adminstarter.system.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 异步导出执行器：领取任务、恢复导出人权限上下文、分页流式写文件、上报进度、通知与清理。
 * <p>执行线程没有请求上下文，必须按 {@code userId} 重新加载权限并在 finally 清理 ThreadLocal。</p>
 */
@Component
public class ExportTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskExecutor.class);

    private final ExportTaskService exportTaskService;
    private final List<ExportFileWriter> fileWriters;
    private final AuthUserQueryService authUserQueryService;
    private final NotificationPublisher notificationPublisher;
    private final ThreadPoolTaskExecutor exportTaskExecutor;

    /** 显式构造器：容器里现在有多个 {@link ThreadPoolTaskExecutor}，必须按名字取导出专用池。 */
    public ExportTaskExecutor(ExportTaskService exportTaskService,
                              List<ExportFileWriter> fileWriters,
                              AuthUserQueryService authUserQueryService,
                              NotificationPublisher notificationPublisher,
                              @Qualifier("exportTaskThreadPool") ThreadPoolTaskExecutor exportTaskExecutor) {
        this.exportTaskService = exportTaskService;
        this.fileWriters = fileWriters;
        this.authUserQueryService = authUserQueryService;
        this.notificationPublisher = notificationPublisher;
        this.exportTaskExecutor = exportTaskExecutor;
    }

    public void submit(Long taskId) {
        try {
            exportTaskExecutor.execute(() -> run(taskId));
        } catch (TaskRejectedException exception) {
            exportTaskService.finishFailed(taskId, "导出任务排队已满，请稍后重试");
        }
    }

    public void run(Long taskId) {
        if (!exportTaskService.tryMarkRunning(taskId)) {
            return;
        }
        Path file = null;
        try {
            ExportTask task = exportTaskService.find(taskId);
            ExportFileWriter fileWriter = writerFor(task.getModule());
            DataScopeContext.set(loadCurrentUser(task.getUserId()));
            file = exportTaskService.tempPath(task.getUserId(), taskId);
            exportTaskService.updateProgress(taskId, 5);
            long written = fileWriter.write(file, task.getQueryParams(), task.getTotalRows(),
                    new ExportFileWriter.ProgressReporter() {
                        @Override
                        public void onProgress(long done, long total) {
                            int percent = 5 + (int) (done * 90 / Math.max(1, total));
                            exportTaskService.updateProgress(taskId, Math.min(percent, 95));
                        }

                        @Override
                        public boolean canceled() {
                            return exportTaskService.isCanceled(taskId);
                        }
                    });
            exportTaskService.finishSuccess(taskId, file, Files.size(file));
            notifySuccess(task, written);
        } catch (ExportCanceledException exception) {
            deleteQuietly(file);
            exportTaskService.finishCanceled(taskId);
        } catch (Exception exception) {
            log.warn("导出任务执行失败 taskId={}", taskId, exception);
            deleteQuietly(file);
            exportTaskService.finishFailed(taskId, exception.getMessage());
        } finally {
            DataScopeContext.clear();
        }
    }

    private ExportFileWriter writerFor(String module) {
        Map<String, ExportFileWriter> writers = fileWriters.stream()
                .collect(Collectors.toMap(ExportFileWriter::moduleKey, Function.identity()));
        ExportFileWriter writer = writers.get(module);
        if (writer == null) {
            throw new IllegalStateException("未注册的导出模块：" + module);
        }
        return writer;
    }

    /**
     * 按导出人重建权限上下文。
     * <p>导出人可能已停用/删除（任务在受理后才发生），此时必须失败退出而不是继续按旧权限产出数据：
     * 任务置 `FAILED`，不生成文件、不发完成通知。</p>
     */
    private CurrentUser loadCurrentUser(Long userId) {
        User user = authUserQueryService.findById(userId);
        if (user == null) {
            throw new IllegalStateException("导出人不存在或已删除");
        }
        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new IllegalStateException("导出人已停用");
        }
        return CurrentUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .deptId(user.getDeptId())
                .roles(new LinkedHashSet<>(authUserQueryService.roleCodes(userId)))
                .permissions(new LinkedHashSet<>(authUserQueryService.permissionCodes(userId)))
                .build();
    }

    private void notifySuccess(ExportTask task, long written) {
        try {
            notificationPublisher.publishInNewTransaction(NotificationCommand.builder()
                    .title("导出完成")
                    .content("「" + task.getFileName() + "」已生成，共 " + written + " 行，请到「我的导出任务」下载（24 小时内有效）。")
                    .level("NORMAL")
                    .type("TASK")
                    .revocable(false)
                    .scopes(java.util.List.of(NotificationCommand.Scope.ofUser(task.getUserId())))
                    .build());
        } catch (RuntimeException exception) {
            log.warn("导出完成通知发送失败 taskId={}", task.getId(), exception);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 下次过期清理继续尝试
        }
    }
}
