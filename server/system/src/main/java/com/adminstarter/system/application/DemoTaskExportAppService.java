package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.application.excel.demo.DemoTaskExcelSchema;
import com.adminstarter.system.application.excel.demo.DemoTaskExportRow;
import com.adminstarter.system.application.excel.demo.DemoTaskExportRowAssembler;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.ExportFieldKeys;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.query.DemoTaskQuery;
import com.adminstarter.system.domain.repository.DemoTaskRepository;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 示例任务导出查询钩子：只负责构造查询、计数与同步取数，通道选择与写表由
 * {@link ExcelExportPlanner} 统一完成。
 */
@Service
@RequiredArgsConstructor
public class DemoTaskExportAppService {

    private final DemoTaskRepository demoTaskRepository;
    private final DemoTaskExportRowAssembler rowAssembler;
    private final DemoTaskExcelSchema schema;
    private final ExcelExportPlanner exportPlanner;
    private final ObjectMapper objectMapper;

    public ExcelExportPlanner.ExportPlan plan(String keyword, String status, Long deptId, String sort, String order,
                                              String fields) {
        DemoTaskQuery query = new DemoTaskQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setDeptId(deptId);
        query.setSort(sort);
        query.setOrder(order);
        // 列白名单随任务快照一起落库：异步 worker 从 query_params 读回并裁剪列
        query.setFields(fields);
        query.setPage(1);
        query.setPageSize(ExcelExportPlanner.SYNC_MAX_ROWS);

        long totalRows = DataPermissionContext.controlled(() -> demoTaskRepository.count(query));
        String queryParams;
        try {
            queryParams = objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出条件序列化失败");
        }
        return exportPlanner.plan(schema, "示例任务", totalRows, queryParams,
                () -> assembleSyncRows(query), ExportFieldKeys.parse(fields));
    }

    private List<DemoTaskExportRow> assembleSyncRows(DemoTaskQuery query) {
        List<DemoTask> tasks = DataPermissionContext.controlled(
                () -> demoTaskRepository.findForExport(query, 0, ExcelExportPlanner.SYNC_MAX_ROWS));
        return rowAssembler.assemble(tasks);
    }
}
