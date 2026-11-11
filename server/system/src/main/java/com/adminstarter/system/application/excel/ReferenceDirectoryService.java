package com.adminstarter.system.application.excel;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.DataScopeProvider;
import com.adminstarter.system.domain.model.DataScopeCondition;
import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 引用字段目录：一次查询建立「显示值 ↔ 存储值」双向表，模板下拉、导入反查、导出正译共用同一份实现。
 * <p>约束：</p>
 * <ul>
 *   <li>部门路径由父链在内存拼装（{@code sys_dept} 无 path 字段），一次 {@code findAll()}。</li>
 *   <li>用户信息一次轻量全量查询（不读密码），显示值为「姓名（部门完整路径）」。</li>
 *   <li>反查一律整串精确匹配，不解析字符串。</li>
 *   <li>导入可指定部门 = 当前用户数据权限可见部门；下拉建议范围同此集合。负责人下拉建议范围为本部门及下级，
 *       但导入只校验存在性与唯一性，下拉缺项不会造成「填对却报错」。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReferenceDirectoryService {

    public static final String PATH_SEPARATOR = "/";
    private static final String NO_DEPT = "无部门";

    private final DeptRepository deptRepository;
    private final UserRepository userRepository;
    private final DataScopeProvider dataScopeProvider;

    /** 一组字段所需的最小目录；没有引用字段时两个目录都为 null，不产生查询。 */
    public ReferenceContext context(List<? extends ExcelField<?>> fields) {
        boolean needsDept = fields.stream()
                .anyMatch(field -> field.getKind() == FieldKind.REF_DEPT || field.getKind() == FieldKind.REF_USER);
        boolean needsUser = fields.stream().anyMatch(field -> field.getKind() == FieldKind.REF_USER);
        if (!needsDept) {
            return new ReferenceContext(null, null);
        }
        DeptDirectory depts = deptDirectory();
        return new ReferenceContext(depts, needsUser ? userDirectory(depts) : null);
    }

    public DeptDirectory deptDirectory() {
        List<Dept> all = deptRepository.findAll();
        Map<Long, Dept> byId = new HashMap<>();
        all.forEach(dept -> byId.put(dept.getId(), dept));
        Map<Long, String> pathById = new LinkedHashMap<>();
        Map<String, List<Long>> idsByPath = new HashMap<>();
        for (Dept dept : all) {
            String path = buildPath(dept, byId);
            pathById.put(dept.getId(), path);
            idsByPath.computeIfAbsent(path, key -> new ArrayList<>()).add(dept.getId());
        }
        return new DeptDirectory(pathById, idsByPath, visibleDeptIds());
    }

    public UserDirectory userDirectory(DeptDirectory depts) {
        Map<Long, String> displayById = new LinkedHashMap<>();
        Map<String, List<Long>> idsByDisplay = new HashMap<>();
        Map<String, Long> idByUsername = new HashMap<>();
        Map<String, List<Long>> idsByNickname = new HashMap<>();
        Set<Long> selectableIds = new HashSet<>();
        Set<Long> scope = ownDeptAndDescendants();
        for (User user : userRepository.findAllForReference()) {
            String deptPath = user.getDeptId() == null
                    ? NO_DEPT
                    : depts.pathById().getOrDefault(user.getDeptId(), NO_DEPT);
            String display = user.getNickname() + "（" + deptPath + "）";
            displayById.put(user.getId(), display);
            idsByDisplay.computeIfAbsent(display, key -> new ArrayList<>()).add(user.getId());
            idByUsername.put(user.getUsername(), user.getId());
            idsByNickname.computeIfAbsent(user.getNickname(), key -> new ArrayList<>()).add(user.getId());
            if (user.getDeptId() != null && scope.contains(user.getDeptId())
                    && !Boolean.FALSE.equals(user.getIsEnabled())) {
                selectableIds.add(user.getId());
            }
        }
        return new UserDirectory(displayById, idsByDisplay, idByUsername, idsByNickname, selectableIds);
    }

    /**
     * 可见部门集合：ALL 或无登录上下文返回 null（表示不限），否则为数据权限命中的部门并集；
     * SELF 档补入当前用户所属部门，避免「只能看本人数据」的角色无法指定自己所在部门。
     */
    private Set<Long> visibleDeptIds() {
        DataScopeCondition condition = dataScopeProvider.current();
        if (condition == null || condition.isUnrestricted()) {
            return null;
        }
        Set<Long> ids = new HashSet<>(condition.getDeptIds() == null ? List.of() : condition.getDeptIds());
        CurrentUser current = DataScopeContext.get();
        if (condition.isSelfIncluded() && current != null && current.getDeptId() != null) {
            ids.add(current.getDeptId());
        }
        return ids;
    }

    private Set<Long> ownDeptAndDescendants() {
        CurrentUser current = DataScopeContext.get();
        if (current == null || current.getDeptId() == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        ids.add(current.getDeptId());
        ids.addAll(deptRepository.findDescendantIds(current.getDeptId()));
        return ids;
    }

    private String buildPath(Dept dept, Map<Long, Dept> byId) {
        Deque<String> names = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Dept cursor = dept;
        while (cursor != null && visited.add(cursor.getId())) {
            names.addFirst(cursor.getName());
            Long parentId = cursor.getParentId();
            cursor = parentId == null || parentId == 0L ? null : byId.get(parentId);
        }
        return String.join(PATH_SEPARATOR, names);
    }

    /** 部门目录：ID → 完整路径、完整路径 → ID（可能多义）、可指定部门集合。 */
    public record DeptDirectory(Map<Long, String> pathById, Map<String, List<Long>> idsByPath,
                                Set<Long> visibleIds) {

        /** null 表示不限。 */
        public boolean visible(Long id) {
            return visibleIds == null || visibleIds.contains(id);
        }
    }

    /** 用户目录：ID → 显示文本、显示文本/账号/姓名 → ID、下拉建议集合。 */
    public record UserDirectory(Map<Long, String> displayById, Map<String, List<Long>> idsByDisplay,
                                Map<String, Long> idByUsername, Map<String, List<Long>> idsByNickname,
                                Set<Long> selectableIds) {
    }

    /** 一次导入/导出复用的目录快照，禁止按行重新加载。 */
    public record ReferenceContext(DeptDirectory depts, UserDirectory users) {

        public String deptPath(Long deptId) {
            return depts == null || deptId == null ? "" : depts.pathById().getOrDefault(deptId, "");
        }

        public String userDisplay(Long userId) {
            return users == null || userId == null ? "" : users.displayById().getOrDefault(userId, "");
        }
    }
}
