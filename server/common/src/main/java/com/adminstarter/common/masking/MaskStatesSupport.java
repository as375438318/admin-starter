package com.adminstarter.common.masking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应对象的状态标记：只在出现掩码/隐藏字段时返回 maskStates，明文不登记。
 * 新响应 DTO 继承本类即可获得该字段，无需在服务层手工填充。
 */
public abstract class MaskStatesSupport {

    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    @JsonProperty("maskStates")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getMaskStates() {
        List<Field> fields = FIELD_CACHE.computeIfAbsent(getClass(), MaskStatesSupport::scan);
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<String, String> states = new LinkedHashMap<>();
        for (Field field : fields) {
            MaskedField meta = field.getAnnotation(MaskedField.class);
            String raw;
            try {
                raw = (String) field.get(this);
            } catch (IllegalAccessException exception) {
                continue;
            }
            MaskResolver.Resolution resolution = MaskResolver.resolve(meta.value(), meta.format(), raw);
            if (resolution.state() != null) {
                states.put(field.getName(), resolution.state().name());
            }
        }
        return states;
    }

    private static List<Field> scan(Class<?> type) {
        List<Field> result = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == String.class && field.isAnnotationPresent(MaskedField.class)) {
                    field.setAccessible(true);
                    result.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return List.copyOf(result);
    }
}
