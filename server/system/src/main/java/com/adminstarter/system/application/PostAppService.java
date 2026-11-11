package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.PostDtos;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.query.PostQuery;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostAppService {

    private final PostRepository postRepository;
    private final DeptRepository deptRepository;

    public PageResponse<PostDtos.Response> page(PageQuery pageQuery, String keyword, Long deptId, Boolean isEnabled) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        PostQuery query = new PostQuery();
        query.setKeyword(keyword);
        query.setDeptId(deptId);
        query.setIsEnabled(isEnabled);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<Post> result = postRepository.page(query);
        Map<Long, Dept> depts = deptMap();
        List<PostDtos.Response> list = result.getList().stream().map(post -> toResponse(post, depts)).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    @Transactional
    public PostDtos.Response create(PostDtos.SaveRequest request) {
        validateDept(request.getDeptId());
        checkCodeUnique(request.getCode(), null);
        Post post = new Post();
        apply(post, request);
        postRepository.insert(post);
        return toResponse(post, deptMap());
    }

    @Transactional
    public PostDtos.Response update(Long id, PostDtos.SaveRequest request) {
        Post post = postRepository.findById(id);
        if (post == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        validateDept(request.getDeptId());
        checkCodeUnique(request.getCode(), id);
        apply(post, request);
        postRepository.update(post);
        return toResponse(post, deptMap());
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id);
        if (post == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        postRepository.deleteById(id);
    }

    private void apply(Post post, PostDtos.SaveRequest request) {
        post.setDeptId(request.getDeptId());
        post.setCode(request.getCode());
        post.setName(request.getName());
        post.setSort(request.getSort() == null ? 0 : request.getSort());
        post.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private void validateDept(Long deptId) {
        if (deptRepository.findById(deptId) == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "所属部门不存在");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        postRepository.findAll().stream()
                .filter(post -> post.getCode().equals(code))
                .filter(post -> excludeId == null || !post.getId().equals(excludeId))
                .findFirst()
                .ifPresent(post -> {
                    throw new BizException(ErrorCode.STATE_CONFLICT, "岗位编码已存在");
                });
    }

    private Map<Long, Dept> deptMap() {
        Map<Long, Dept> map = new HashMap<>();
        deptRepository.findAll().forEach(dept -> map.put(dept.getId(), dept));
        return map;
    }

    private PostDtos.Response toResponse(Post post, Map<Long, Dept> depts) {
        PostDtos.Response response = new PostDtos.Response();
        response.setId(post.getId());
        response.setDeptId(post.getDeptId());
        Dept dept = depts.get(post.getDeptId());
        response.setDeptName(dept == null ? null : dept.getName());
        response.setCode(post.getCode());
        response.setName(post.getName());
        response.setSort(post.getSort());
        response.setIsEnabled(post.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(post.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(post.getUpdatedAt()));
        return response;
    }
}
