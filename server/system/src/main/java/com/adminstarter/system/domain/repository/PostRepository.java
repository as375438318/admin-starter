package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.query.PostQuery;

import java.util.List;

public interface PostRepository {

    Post findById(Long id);

    void insert(Post post);

    void insertBatch(List<Post> posts);

    void update(Post post);

    void deleteById(Long id);

    PageResult<Post> page(PostQuery query);

    long count(PostQuery query);

    List<Post> findForExport(PostQuery query, int offset, int limit);

    List<Post> findAll();

    long countByDeptId(Long deptId);
}
