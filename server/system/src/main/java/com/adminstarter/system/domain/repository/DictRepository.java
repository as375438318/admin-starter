package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.DictItem;
import com.adminstarter.system.domain.model.DictType;
import com.adminstarter.system.domain.query.DictTypeQuery;

import java.util.Collection;
import java.util.List;

public interface DictRepository {

    PageResult<DictType> pageTypes(DictTypeQuery query);

    DictType findTypeById(Long id);

    DictType findTypeByCode(String type);

    void insertType(DictType dictType);

    void updateType(DictType dictType);

    void deleteType(Long id);

    boolean existsItems(Long dictTypeId);

    List<DictItem> findItemsByTypeId(Long dictTypeId);

    DictItem findItemById(Long id);

    void insertItem(DictItem item);

    void updateItem(DictItem item);

    void deleteItem(Long id);

    List<DictItem> findItemsByTypeCodes(Collection<String> types);

    List<String> findTypeCodesByIds(Collection<Long> ids);
}
