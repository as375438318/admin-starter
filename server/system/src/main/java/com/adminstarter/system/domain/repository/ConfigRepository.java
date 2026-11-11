package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Config;
import com.adminstarter.system.domain.query.ConfigQuery;

import java.util.Collection;
import java.util.List;

public interface ConfigRepository {

    PageResult<Config> page(ConfigQuery query);

    Config findById(Long id);

    Config findByKey(String configKey);

    List<Config> findByKeys(Collection<String> configKeys);

    void insert(Config config);

    void update(Config config);

    void deleteById(Long id);

    boolean existsByKey(String configKey, Long excludeId);
}
