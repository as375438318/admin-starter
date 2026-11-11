package com.adminstarter.system.application.excel.demo;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.excel.ExcelImportHandler;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.application.excel.ImportErrorCollector;
import com.adminstarter.system.application.excel.RawRow;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.repository.DemoTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 示例任务导入钩子：部门与负责人已由框架的引用翻译解析成 ID，这里只做实体组装与批量插入。
 */
@Component
@RequiredArgsConstructor
public class DemoTaskImportHandler implements ExcelImportHandler<DemoTask, Void> {

    private final DemoTaskExcelSchema schema;
    private final DemoTaskRepository demoTaskRepository;

    @Override
    public ExcelSchema<?> schema() {
        return schema;
    }

    @Override
    public Void prepare(List<RawRow> rows) {
        return null;
    }

    @Override
    public void validate(RawRow row, Map<String, Object> values, Void context, ImportErrorCollector errors) {
        // 引用字段（部门/负责人/状态）的存在性、唯一性、越权均由框架统一校验，无业务附加规则。
    }

    @Override
    public DemoTask toEntity(Map<String, Object> values) {
        CurrentUser current = DataScopeContext.get();
        Long currentUserId = current == null ? null : current.getUserId();
        DemoTask task = new DemoTask();
        task.setTitle((String) values.get("title"));
        task.setStatus(values.get("status") == null ? "TODO" : (String) values.get("status"));
        task.setDeptId((Long) values.get("deptId"));
        Object ownerId = values.get("ownerId");
        task.setOwnerId(ownerId instanceof Long id ? id : currentUserId);
        task.setCreateBy(currentUserId);
        return task;
    }

    @Override
    public void persist(List<DemoTask> entities) {
        demoTaskRepository.insertBatch(entities);
    }
}
