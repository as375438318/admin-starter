package com.adminstarter.system.application.export.module;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.application.excel.customer.CustomerExcelSchema;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.application.export.ExportFieldKeys;
import com.adminstarter.system.application.export.ExportModule;
import com.adminstarter.system.domain.model.Customer;
import com.adminstarter.system.domain.query.CustomerQuery;
import com.adminstarter.system.domain.repository.CustomerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户档案导出（通用导出中心的一个模块）。 */
@Component
@RequiredArgsConstructor
public class CustomerExportModule implements ExportModule {

    private final CustomerRepository customerRepository;
    private final CustomerExcelSchema schema;
    private final ExcelExportPlanner planner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "customer";
    }

    @Override
    public String name() {
        return "客户档案";
    }

    @Override
    public String permission() {
        return "demo:customer:export";
    }

    @Override
    public ExcelExportPlanner.ExportPlan plan(Map<String, String> params) {
        CustomerQuery query = new CustomerQuery();
        query.setKeyword(params.get("keyword"));
        query.setSort(params.get("sort"));
        query.setOrder(params.get("order"));
        query.setPage(1);
        query.setPageSize(ExcelExportPlanner.SYNC_MAX_ROWS);
        PageResult<Customer> result = customerRepository.page(query);
        // 快照：异步任务只回放这些参数（与既有导出模块同口径）
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("keyword", query.getKeyword());
        snapshot.put("sort", query.getSort());
        snapshot.put("order", query.getOrder());
        List<Customer> rows = result.getList();
        String queryParams;
        try {
            // 异步通道靠这段 JSON 回放筛选条件，序列化失败必须当场报错而不是静默导出全量
            queryParams = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出参数序列化失败");
        }
        return planner.plan(schema, name(), result.getTotal(), queryParams, () -> rows,
                ExportFieldKeys.parse(params.get("fields")));
    }
}