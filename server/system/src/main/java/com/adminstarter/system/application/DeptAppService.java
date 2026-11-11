package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.DeptDtos;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.PostRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeptAppService {

    private final DeptRepository deptRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final DataScopeProvider dataScopeProvider;

    public List<DeptDtos.Response> tree() {
        return buildTree(deptRepository.findAll(), 0L);
    }

    public List<DeptDtos.Option> options() {
        return buildOptions(deptRepository.findAll(), 0L);
    }

    @Transactional
    public DeptDtos.Response create(DeptDtos.SaveRequest request) {
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Dept parent = parentId == 0L ? null : deptRepository.findById(parentId);
        if (parentId != 0L && parent == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "上级部门不存在");
        }
        Dept dept = new Dept();
        dept.setParentId(parentId);
        dept.setAncestors(parent == null ? "0" : parent.getAncestors() + "," + parent.getId());
        apply(dept, request);
        deptRepository.insert(dept);
        dataScopeProvider.evictAll();
        return toResponse(dept);
    }

    @Transactional
    public DeptDtos.Response update(Long id, DeptDtos.SaveRequest request) {
        Dept dept = requireDept(id);
        Long newParentId = request.getParentId() == null ? 0L : request.getParentId();
        if (newParentId != 0L) {
            if (newParentId.equals(id)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "上级部门不能是自身");
            }
            Dept parent = deptRepository.findById(newParentId);
            if (parent == null) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "上级部门不存在");
            }
            if (ancestorsContain(parent.getAncestors(), id)) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "不能把部门移动到自己的下级（会形成环）");
            }
        }

        boolean parentChanged = !dept.getParentId().equals(newParentId);
        String oldAncestors = dept.getAncestors();
        if (parentChanged) {
            Dept parent = newParentId == 0L ? null : deptRepository.findById(newParentId);
            String newAncestors = parent == null ? "0" : parent.getAncestors() + "," + parent.getId();
            dept.setParentId(newParentId);
            dept.setAncestors(newAncestors);
            updateDescendantAncestors(id, oldAncestors, newAncestors);
        }
        apply(dept, request);
        deptRepository.update(dept);

        if (Boolean.FALSE.equals(dept.getIsEnabled())) {
            cascadeDisable(id);
        }
        dataScopeProvider.evictAll();
        return toResponse(dept);
    }

    @Transactional
    public void delete(Long id) {
        Dept dept = requireDept(id);
        if (deptRepository.existsByParentId(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "存在下级部门，不能删除");
        }
        if (userRepository.countByDeptId(id) > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "部门下存在用户，不能删除");
        }
        if (postRepository.countByDeptId(id) > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "部门下存在岗位，不能删除");
        }
        deptRepository.deleteById(id);
        dataScopeProvider.evictAll();
    }

    private void cascadeDisable(Long id) {
        List<Dept> all = deptRepository.findAll();
        for (Dept dept : all) {
            if (ancestorsContain(dept.getAncestors(), id)) {
                if (Boolean.TRUE.equals(dept.getIsEnabled())) {
                    dept.setIsEnabled(false);
                    deptRepository.update(dept);
                }
            }
        }
    }

    private void updateDescendantAncestors(Long id, String oldAncestors, String newAncestors) {
        String oldChain = oldAncestors + "," + id;
        List<Dept> all = deptRepository.findAll();
        for (Dept dept : all) {
            String ancestors = dept.getAncestors();
            if (ancestors == null) {
                continue;
            }
            if (ancestors.equals(oldChain) || ancestors.startsWith(oldChain + ",")) {
                dept.setAncestors(newAncestors + ancestors.substring(oldChain.length()));
                deptRepository.update(dept);
            }
        }
    }

    /**
     * 按逗号分段精确比较祖先链，避免 contains 子串匹配把部门 1 与 10 误判为同一条链。
     */
    private boolean ancestorsContain(String ancestors, Long id) {
        if (ancestors == null || id == null) {
            return false;
        }
        for (String part : ancestors.split(",")) {
            if (part.trim().equals(String.valueOf(id))) {
                return true;
            }
        }
        return false;
    }

    private void apply(Dept dept, DeptDtos.SaveRequest request) {
        String name = request.getName();
        // 部门全路径以 / 拼接，名称含分隔符会让「研发/测试」与「研发」+子部门「测试」拼出同一路径。
        if (name != null && (name.contains("/") || name.contains("\\"))) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "部门名称不能包含 / 或 \\");
        }
        dept.setName(name);
        dept.setSort(request.getSort() == null ? 0 : request.getSort());
        dept.setLeader(request.getLeader());
        dept.setPhone(request.getPhone());
        dept.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private Dept requireDept(Long id) {
        Dept dept = deptRepository.findById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        return dept;
    }

    private List<DeptDtos.Response> buildTree(List<Dept> depts, Long parentId) {
        Map<Long, List<Dept>> groups = new LinkedHashMap<>();
        for (Dept dept : depts) {
            groups.computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(),
                    key -> new ArrayList<>()).add(dept);
        }
        List<Dept> children = groups.getOrDefault(parentId, List.of());
        List<DeptDtos.Response> result = new ArrayList<>();
        for (Dept dept : children) {
            DeptDtos.Response response = toResponse(dept);
            List<DeptDtos.Response> childTree = buildTree(depts, dept.getId());
            response.setChildren(childTree.isEmpty() ? null : childTree);
            result.add(response);
        }
        return result;
    }

    private List<DeptDtos.Option> buildOptions(List<Dept> depts, Long parentId) {
        Map<Long, List<Dept>> groups = new HashMap<>();
        for (Dept dept : depts) {
            groups.computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(),
                    key -> new ArrayList<>()).add(dept);
        }
        List<Dept> children = groups.getOrDefault(parentId, List.of());
        List<DeptDtos.Option> result = new ArrayList<>();
        for (Dept dept : children) {
            DeptDtos.Option option = new DeptDtos.Option();
            option.setId(dept.getId());
            option.setName(dept.getName());
            option.setParentId(dept.getParentId());
            option.setIsEnabled(dept.getIsEnabled());
            List<DeptDtos.Option> childOptions = buildOptions(depts, dept.getId());
            option.setChildren(childOptions.isEmpty() ? null : childOptions);
            result.add(option);
        }
        return result;
    }

    private DeptDtos.Response toResponse(Dept dept) {
        DeptDtos.Response response = new DeptDtos.Response();
        response.setId(dept.getId());
        response.setParentId(dept.getParentId());
        response.setAncestors(dept.getAncestors());
        response.setName(dept.getName());
        response.setSort(dept.getSort());
        response.setLeader(dept.getLeader());
        response.setPhone(dept.getPhone());
        response.setIsEnabled(dept.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(dept.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(dept.getUpdatedAt()));
        return response;
    }
}
