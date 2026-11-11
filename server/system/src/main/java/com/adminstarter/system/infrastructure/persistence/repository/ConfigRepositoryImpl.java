package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Config;
import com.adminstarter.system.domain.query.ConfigQuery;
import com.adminstarter.system.domain.repository.ConfigRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.ConfigConverter;
import com.adminstarter.system.infrastructure.persistence.entity.ConfigDO;
import com.adminstarter.system.infrastructure.persistence.mapper.ConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "configKey", "config_key",
            "id", "id"
    );

    private final ConfigMapper configMapper;
    private final ConfigConverter converter;

    @Override
    public PageResult<Config> page(ConfigQuery query) {
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("config_key", query.getKeyword()));
        }
        if (query.getIsSensitive() != null) {
            wrapper.eq("is_sensitive", query.getIsSensitive());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        wrapper.orderBy(true, SortSupport.isAsc(query.getOrder()), column);
        Page<ConfigDO> page = configMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public Config findById(Long id) {
        return converter.toDomain(configMapper.selectById(id));
    }

    @Override
    public Config findByKey(String configKey) {
        return converter.toDomain(configMapper.selectOne(
                new QueryWrapper<ConfigDO>().eq("config_key", configKey).last("limit 1")));
    }

    @Override
    public List<Config> findByKeys(Collection<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return List.of();
        }
        return configMapper.selectList(new QueryWrapper<ConfigDO>().in("config_key", configKeys))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void insert(Config config) {
        ConfigDO row = converter.toDO(config);
        configMapper.insert(row);
        config.setId(row.getId());
        config.setCreatedAt(row.getCreatedAt());
        config.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Config config) {
        configMapper.updateById(converter.toDO(config));
    }

    @Override
    public void deleteById(Long id) {
        configMapper.deleteById(id);
    }

    @Override
    public boolean existsByKey(String configKey, Long excludeId) {
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<ConfigDO>().eq("config_key", configKey);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return configMapper.selectCount(wrapper) > 0;
    }
}
