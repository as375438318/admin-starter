package com.adminstarter.system.application.excel.post;

import com.adminstarter.system.application.excel.ExcelImportHandler;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.application.excel.ImportErrorCollector;
import com.adminstarter.system.application.excel.RawRow;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位导入钩子：部门与状态由框架的引用翻译解析，这里只做编码唯一性校验、实体组装与批量插入。
 */
@Component
@RequiredArgsConstructor
public class PostImportHandler implements ExcelImportHandler<Post, PostImportHandler.Context> {

    private final PostExcelSchema schema;
    private final PostRepository postRepository;

    @Override
    public ExcelSchema<?> schema() {
        return schema;
    }

    @Override
    public Context prepare(List<RawRow> rows) {
        Set<String> existingCodes = postRepository.findAll().stream()
                .map(Post::getCode)
                .collect(Collectors.toSet());
        return new Context(existingCodes, new HashSet<>());
    }

    @Override
    public void validate(RawRow row, Map<String, Object> values, Context context, ImportErrorCollector errors) {
        Object code = values.get("code");
        if (code instanceof String value) {
            if (context.existingCodes().contains(value)) {
                errors.add(row.rowNumber(), PostExcelSchema.HEADER_CODE, "VALIDATION_FAILED", "岗位编码已存在");
            } else if (!context.seenCodes().add(value)) {
                errors.add(row.rowNumber(), PostExcelSchema.HEADER_CODE, "VALIDATION_FAILED", "岗位编码在文件内重复");
            }
        }
    }

    @Override
    public Post toEntity(Map<String, Object> values) {
        Post post = new Post();
        post.setDeptId((Long) values.get("deptId"));
        post.setCode((String) values.get("code"));
        post.setName((String) values.get("name"));
        Object sort = values.get("sort");
        post.setSort(sort == null ? 0 : (Integer) sort);
        Object isEnabled = values.get("isEnabled");
        post.setIsEnabled(isEnabled == null || Boolean.parseBoolean(String.valueOf(isEnabled)));
        return post;
    }

    @Override
    public void persist(List<Post> entities) {
        postRepository.insertBatch(entities);
    }

    public record Context(Set<String> existingCodes, Set<String> seenCodes) {
    }
}
