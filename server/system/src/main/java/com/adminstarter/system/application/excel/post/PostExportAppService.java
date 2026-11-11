package com.adminstarter.system.application.excel.post;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.application.export.ExcelExportPlanner;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.query.PostQuery;
import com.adminstarter.system.domain.repository.PostRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位导出查询钩子：复用岗位列表查询（同一条件、同一排序、同一可见范围），
 * 通道选择与写表由 {@link ExcelExportPlanner} 统一完成。
 * <p>岗位列表本身没有数据范围过滤，导出沿用同一查询，不新增可见性规则。</p>
 */
@Service
@RequiredArgsConstructor
public class PostExportAppService {

    private final PostRepository postRepository;
    private final PostExportRowAssembler rowAssembler;
    private final PostExcelSchema schema;
    private final ExcelExportPlanner exportPlanner;
    private final ObjectMapper objectMapper;

    public ExcelExportPlanner.ExportPlan plan(String keyword, Long deptId, Boolean isEnabled,
                                              String sort, String order) {
        PostQuery query = new PostQuery();
        query.setKeyword(keyword);
        query.setDeptId(deptId);
        query.setIsEnabled(isEnabled);
        query.setSort(sort);
        query.setOrder(order);
        query.setPage(1);
        query.setPageSize(ExcelExportPlanner.SYNC_MAX_ROWS);

        long totalRows = postRepository.count(query);
        String queryParams;
        try {
            queryParams = objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出条件序列化失败");
        }
        return exportPlanner.plan(schema, "岗位", totalRows, queryParams,
                () -> assembleSyncRows(query));
    }

    private List<PostExportRow> assembleSyncRows(PostQuery query) {
        List<Post> posts = postRepository.findForExport(query, 0, ExcelExportPlanner.SYNC_MAX_ROWS);
        return rowAssembler.assemble(posts);
    }
}
