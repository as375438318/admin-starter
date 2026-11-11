package com.adminstarter.system.application.job;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 已注册任务注册表：启动时收集所有 {@link ScheduledJob} Bean，标识重复直接启动失败（fail-fast）。
 * <p>运行时只允许按注册标识取 Bean，不存在动态加载。</p>
 */
@Component
public class JobRegistry {

    public record RegisteredJob(String key, String description) {
    }

    private final Map<String, ScheduledJob> jobs;

    public JobRegistry(List<ScheduledJob> beans) {
        Map<String, ScheduledJob> map = new TreeMap<>();
        for (ScheduledJob bean : beans) {
            String key = bean.key() == null ? "" : bean.key().trim();
            if (key.isEmpty()) {
                throw new IllegalStateException("ScheduledJob 标识不能为空: " + bean.getClass().getName());
            }
            if (map.putIfAbsent(key, bean) != null) {
                throw new IllegalStateException("ScheduledJob 标识重复: " + key);
            }
        }
        this.jobs = Map.copyOf(map);
    }

    public boolean contains(String key) {
        return jobs.containsKey(key);
    }

    public ScheduledJob require(String key) {
        return jobs.get(key);
    }

    public List<RegisteredJob> list() {
        return jobs.entrySet().stream()
                .map(entry -> new RegisteredJob(entry.getKey(), entry.getValue().description()))
                .toList();
    }
}
