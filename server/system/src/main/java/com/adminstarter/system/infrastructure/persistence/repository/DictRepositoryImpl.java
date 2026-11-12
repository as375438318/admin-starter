package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.DictItem;
import com.adminstarter.system.domain.model.DictType;
import com.adminstarter.system.domain.query.DictTypeQuery;
import com.adminstarter.system.domain.repository.DictRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.DictConverter;
import com.adminstarter.system.infrastructure.persistence.entity.DictItemDO;
import com.adminstarter.system.infrastructure.persistence.entity.DictTypeDO;
import com.adminstarter.system.infrastructure.persistence.mapper.DictItemMapper;
import com.adminstarter.system.infrastructure.persistence.mapper.DictTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DictRepositoryImpl implements DictRepository {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "type", "type",
            "id", "id"
    );

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final DictConverter converter;

    @Override
    public PageResult<DictType> pageTypes(DictTypeQuery query) {
        QueryWrapper<DictTypeDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("type", query.getKeyword()));
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq("is_enabled", query.getIsEnabled());
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.NEWEST_FIRST));
        Page<DictTypeDO> page = dictTypeMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    @Override
    public DictType findTypeById(Long id) {
        return converter.toDomain(dictTypeMapper.selectById(id));
    }

    @Override
    public DictType findTypeByCode(String type) {
        DictTypeDO row = dictTypeMapper.selectOne(new QueryWrapper<DictTypeDO>().eq("type", type).last("limit 1"));
        return converter.toDomain(row);
    }

    @Override
    public void insertType(DictType dictType) {
        DictTypeDO row = converter.toDO(dictType);
        dictTypeMapper.insert(row);
        dictType.setId(row.getId());
        dictType.setCreatedAt(row.getCreatedAt());
        dictType.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void updateType(DictType dictType) {
        dictTypeMapper.updateById(converter.toDO(dictType));
    }

    @Override
    public void deleteType(Long id) {
        dictTypeMapper.deleteById(id);
    }

    @Override
    public boolean existsItems(Long dictTypeId) {
        return dictItemMapper.selectCount(new QueryWrapper<DictItemDO>().eq("dict_type_id", dictTypeId)) > 0;
    }

    @Override
    public List<DictItem> findItemsByTypeId(Long dictTypeId) {
        return dictItemMapper.selectList(new QueryWrapper<DictItemDO>()
                        .eq("dict_type_id", dictTypeId).orderByAsc("sort", "id"))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public DictItem findItemById(Long id) {
        return converter.toDomain(dictItemMapper.selectById(id));
    }

    @Override
    public void insertItem(DictItem item) {
        DictItemDO row = converter.toDO(item);
        dictItemMapper.insert(row);
        item.setId(row.getId());
        item.setCreatedAt(row.getCreatedAt());
        item.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void updateItem(DictItem item) {
        dictItemMapper.updateById(converter.toDO(item));
    }

    @Override
    public void deleteItem(Long id) {
        dictItemMapper.deleteById(id);
    }

    @Override
    public List<DictItem> findItemsByTypeCodes(Collection<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return dictItemMapper.selectByTypeCodes(types).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<String> findTypeCodesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dictTypeMapper.selectBatchIds(ids).stream().map(DictTypeDO::getType).toList();
    }
}
