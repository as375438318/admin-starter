package com.adminstarter.system.application.export;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.log.OperLogContext;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.excel.ExcelExportWriter;
import com.adminstarter.system.application.excel.ExcelField;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.domain.model.ExportTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导出编排：按行数选通道（同步 / 异步 / 拒绝）。
 * <p>阈值错了的后果都是用户可见且难排查的：同步上限写错会把几万行塞进一次 HTTP 响应，
 * 异步阈值写错会让用户点下载等一个不存在的任务，上限写错会打爆内存。</p>
 */
class ExcelExportPlannerTest {

    private final ExcelExportWriter exportWriter = mock(ExcelExportWriter.class);
    private final ExportTaskService exportTaskService = mock(ExportTaskService.class);
    private final ExportTaskExecutor exportTaskExecutor = mock(ExportTaskExecutor.class);

    private final ExcelExportPlanner planner =
            new ExcelExportPlanner(exportWriter, exportTaskService, exportTaskExecutor);

    private static final ExcelSchema<String> SCHEMA = new ExcelSchema<>() {
        @Override
        public String key() {
            return "test";
        }

        @Override
        public String title() {
            return "测试导出";
        }

        @Override
        public String templateFileName() {
            return "测试导出.xlsx";
        }

        @Override
        public String dataSheetName() {
            return "数据";
        }

        @Override
        public List<ExcelField<String>> fields() {
            return List.of(ExcelField.<String>builder().key("name").importHeader("名称").exportHeader("名称").build());
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        DataScopeContext.set(CurrentUser.builder()
                .userId(7L).username("tester").permissions(Set.of()).build());
        doNothing().when(exportWriter).writeSync(any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        OperLogContext.clear();
    }

    @Test
    @DisplayName("未登录 → UNAUTHORIZED（导出含数据权限，必须登录）")
    void anonymousIsRejected() {
        DataScopeContext.clear();

        BizException error = assertThrows(BizException.class,
                () -> planner.plan(SCHEMA, "测试", 10, null, List::of));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
    }

    @Test
    @DisplayName("超过 10 万行 → 拒绝并提示缩小范围（不建任务、不写文件）")
    void beyondMaxRowsIsRejected() {
        BizException error = assertThrows(BizException.class,
                () -> planner.plan(SCHEMA, "测试", ExcelExportPlanner.MAX_EXPORT_ROWS + 1, null, List::of));

        assertEquals(ErrorCode.INVALID_PARAMETER, error.getErrorCode());
        assertTrue(error.getMessage().contains("缩小筛选范围"));
        verify(exportTaskService, never()).create(anyLong(), anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("恰好等于同步上限 → 走同步通道，直接返回文件字节")
    void atSyncLimitStaysSync() {
        ExcelExportPlanner.ExportPlan plan = planner.plan(SCHEMA, "测试",
                ExcelExportPlanner.SYNC_MAX_ROWS, null, () -> List.of("a", "b"));

        assertTrue(plan.sync());
        assertNotNull(plan.content());
        assertNull(plan.taskId(), "同步通道不应产生任务 ID");
        assertEquals(ExcelExportPlanner.SYNC_MAX_ROWS, plan.totalRows());
        verify(exportTaskService, never()).create(anyLong(), anyString(), anyString(), anyString(), any(), anyLong());
        verify(exportTaskExecutor, never()).submit(anyLong());
    }

    @Test
    @DisplayName("超过同步上限一行 → 走异步通道，返回任务 ID 而不是文件内容")
    void justOverSyncLimitGoesAsync() throws Exception {
        ExportTask task = new ExportTask();
        task.setId(123L);
        when(exportTaskService.create(anyLong(), anyString(), anyString(), anyString(), any(), anyLong()))
                .thenReturn(task);

        ExcelExportPlanner.ExportPlan plan = planner.plan(SCHEMA, "测试",
                ExcelExportPlanner.SYNC_MAX_ROWS + 1L, null, List::of);

        assertTrue(!plan.sync());
        assertNull(plan.content());
        assertEquals(123L, plan.taskId());
        verify(exportTaskExecutor).submit(123L);
        verify(exportWriter, never()).writeSync(any(), any(), any());
    }

    @Test
    @DisplayName("异步通道把当前用户与模块信息交给任务表（审计与权限回溯依据）")
    void asyncTaskReceivesOwnerAndModule() {
        ExportTask task = new ExportTask();
        task.setId(9L);
        when(exportTaskService.create(anyLong(), anyString(), anyString(), anyString(), any(), anyLong()))
                .thenReturn(task);

        planner.plan(SCHEMA, "测试", ExcelExportPlanner.SYNC_MAX_ROWS + 5L, "{\"keyword\":\"x\"}", List::of);

        verify(exportTaskService).create(eq(7L), eq("tester"), eq("test"), anyString(),
                eq("{\"keyword\":\"x\"}"), eq(ExcelExportPlanner.SYNC_MAX_ROWS + 5L));
    }

    @Test
    @DisplayName("文件名 = 前缀_时间戳.xlsx，并写入操作日志上下文")
    void fileNameAndOperLogAreRecorded() {
        ExcelExportPlanner.ExportPlan plan = planner.plan(SCHEMA, "示例任务", 10, null, List::of);

        assertTrue(plan.fileName().startsWith("示例任务_"), "实际：" + plan.fileName());
        assertTrue(plan.fileName().endsWith(".xlsx"), "实际：" + plan.fileName());
        assertTrue(plan.fileName().matches("示例任务_\\d{8}_\\d{6}\\.xlsx"), "时间戳格式不符：" + plan.fileName());

        assertEquals("SYNC", OperLogContext.snapshot().get("mode"));
        assertEquals(10L, OperLogContext.snapshot().get("totalRows"));
        assertEquals(plan.fileName(), OperLogContext.snapshot().get("fileName"));
    }

    @Test
    @DisplayName("同步通道取数只取一次（重复取数会拉长事务并可能两次数不一致）")
    void syncRowsFetchedOnce() {
        int[] calls = {0};

        planner.plan(SCHEMA, "测试", 5, null, () -> {
            calls[0]++;
            return List.of("a");
        });

        assertEquals(1, calls[0]);
    }
}
