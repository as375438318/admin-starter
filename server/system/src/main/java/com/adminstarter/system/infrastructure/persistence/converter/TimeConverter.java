package com.adminstarter.system.infrastructure.persistence.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间映射：领域/DO 使用 LocalDateTime（按 UTC 存储），接口层统一 Instant，
 * 由 Jackson 序列化为 ISO 8601 UTC（CT-FMT-001）。
 */
public interface TimeConverter {

    default Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    default LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
