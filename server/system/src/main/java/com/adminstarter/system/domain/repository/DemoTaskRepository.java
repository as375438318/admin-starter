package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.query.DemoTaskQuery;

import java.util.List;

public interface DemoTaskRepository {

    DemoTask findById(Long id);

    void insert(DemoTask task);

    void insertBatch(List<DemoTask> tasks);

    void update(DemoTask task);

    void deleteById(Long id);

    PageResult<DemoTask> page(DemoTaskQuery query);

    /** 导出前计数，必须与导出查询使用同一套过滤条件。 */
    long count(DemoTaskQuery query);

    /** 导出分页查询：不进入分页插件，用 offset/limit 递增读取。 */
    List<DemoTask> findForExport(DemoTaskQuery query, int offset, int limit);
}
