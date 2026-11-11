package com.adminstarter.system.application.excel.post;

import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 岗位导出行装配：只保留实体原始值，部门路径由框架的引用翻译统一生成。
 */
@Component
@RequiredArgsConstructor
public class PostExportRowAssembler {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    public List<PostExportRow> assemble(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        return posts.stream().map(post -> {
            PostExportRow row = new PostExportRow();
            row.setId(post.getId());
            row.setDeptId(post.getDeptId());
            row.setCode(post.getCode());
            row.setName(post.getName());
            row.setSort(post.getSort());
            row.setIsEnabled(post.getIsEnabled());
            row.setCreatedAt(post.getCreatedAt() == null ? ""
                    : TIME_FORMATTER.format(TimeSupport.toInstant(post.getCreatedAt())));
            return row;
        }).toList();
    }
}
