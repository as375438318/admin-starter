package com.adminstarter.system.application.excel.demo;

import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出行装配：只补充导出需要的原始值（部门 ID、负责人 ID、手机号），
 * 部门路径与负责人显示文本由框架的引用翻译统一生成，避免两处各拼一份。
 */
@Component
@RequiredArgsConstructor
public class DemoTaskExportRowAssembler {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final UserRepository userRepository;

    public List<DemoTaskExportRow> assemble(List<DemoTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Set<Long> ownerIds = tasks.stream()
                .map(DemoTask::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> users = new HashMap<>();
        userRepository.findByIds(ownerIds).forEach(user -> users.put(user.getId(), user));

        return tasks.stream().map(task -> {
            DemoTaskExportRow row = new DemoTaskExportRow();
            row.setId(task.getId());
            row.setTitle(task.getTitle());
            row.setStatus(task.getStatus());
            row.setDeptId(task.getDeptId());
            row.setOwnerId(task.getOwnerId());
            User owner = task.getOwnerId() == null ? null : users.get(task.getOwnerId());
            row.setOwnerPhone(owner == null ? null : owner.getPhone());
            row.setCreatedAt(format(task.getCreatedAt()));
            return row;
        }).toList();
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : TIME_FORMATTER.format(value.atZone(ZoneId.of("UTC")));
    }
}
