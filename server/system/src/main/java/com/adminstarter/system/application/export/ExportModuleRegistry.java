package com.adminstarter.system.application.export;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 导出模块注册表：启动时收集所有 {@link ExportModule}，按键索引；重复键直接启动失败。 */
@Component
public class ExportModuleRegistry {

    private final Map<String, ExportModule> modules;

    public ExportModuleRegistry(List<ExportModule> discovered) {
        Map<String, ExportModule> indexed = new LinkedHashMap<>();
        for (ExportModule module : discovered) {
            ExportModule previous = indexed.put(module.key(), module);
            if (previous != null) {
                throw new IllegalStateException("导出模块键重复：" + module.key()
                        + "（" + previous.getClass().getSimpleName() + " 与 " + module.getClass().getSimpleName() + "）");
            }
        }
        this.modules = Map.copyOf(indexed);
    }

    public ExportModule require(String key) {
        ExportModule module = modules.get(key);
        if (module == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "不支持的导出模块：" + key);
        }
        return module;
    }

    public boolean contains(String key) {
        return modules.containsKey(key);
    }

    public Map<String, ExportModule> all() {
        return modules;
    }
}