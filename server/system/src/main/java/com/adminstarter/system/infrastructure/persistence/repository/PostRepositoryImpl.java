package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.query.PostQuery;
import com.adminstarter.system.domain.repository.PostRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.PostConverter;
import com.adminstarter.system.infrastructure.persistence.entity.PostDO;
import com.adminstarter.system.infrastructure.persistence.mapper.PostMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    /**
     * 单条多行 INSERT 的行数上限。取值依据见 docs/audit/perf-2w-2026-11-10.md 的批次对比。
     */
    private static final int BATCH_SIZE = 1000;

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "code", "code",
            "sort", "sort",
            "id", "id"
    );

    private final PostMapper postMapper;
    private final PostConverter converter;

    @Override
    public Post findById(Long id) {
        return converter.toDomain(postMapper.selectById(id));
    }

    @Override
    public void insert(Post post) {
        PostDO row = converter.toDO(post);
        postMapper.insert(row);
        post.setId(row.getId());
        post.setCreatedAt(row.getCreatedAt());
        post.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void insertBatch(List<Post> posts) {
        if (posts.isEmpty()) {
            return;
        }
        // 多行 INSERT 不经过 MetaObjectHandler，公共时间字段在这里显式填充（与单条插入同口径：UTC）。
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<PostDO> rows = new ArrayList<>(posts.size());
        for (Post post : posts) {
            PostDO row = converter.toDO(post);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            postMapper.insertBatch(rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
    }

    @Override
    public void update(Post post) {
        postMapper.updateById(converter.toDO(post));
    }

    @Override
    public void deleteById(Long id) {
        postMapper.deleteById(id);
    }

    @Override
    public PageResult<Post> page(PostQuery query) {
        QueryWrapper<PostDO> wrapper = wrapper(query);
        Page<PostDO> page = postMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public long count(PostQuery query) {
        return postMapper.selectCount(wrapper(query));
    }

    @Override
    public List<Post> findForExport(PostQuery query, int offset, int limit) {
        QueryWrapper<PostDO> wrapper = wrapper(query);
        wrapper.last("limit " + limit + " offset " + offset);
        return postMapper.selectList(wrapper).stream().map(converter::toDomain).toList();
    }

    private QueryWrapper<PostDO> wrapper(PostQuery query) {
        QueryWrapper<PostDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("code", query.getKeyword()));
        }
        if (query.getDeptId() != null) {
            wrapper.eq("dept_id", query.getDeptId());
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq("is_enabled", query.getIsEnabled());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "sort");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column);
        return wrapper;
    }

    @Override
    public List<Post> findAll() {
        return postMapper.selectList(new QueryWrapper<PostDO>().orderByAsc("sort", "id"))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public long countByDeptId(Long deptId) {
        return postMapper.selectCount(new QueryWrapper<PostDO>().eq("dept_id", deptId));
    }
}
