package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.cache.CacheNamespaces;
import com.fasterxml.jackson.core.type.TypeReference;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.DictDtos;
import com.adminstarter.system.domain.model.DictItem;
import com.adminstarter.system.domain.model.DictType;
import com.adminstarter.system.domain.query.DictTypeQuery;
import com.adminstarter.system.domain.repository.DictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictAppService {

    /**
     * 字典项缓存 TTL。字典是「读多写少」的基础数据，但改动必须立刻可见：
     * 写操作一律 {@link #evictDictCache()} 立即失效，TTL 只是兜底（例如别的实例改了库）。
     */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    /**
     * 「代际键」失效法：缓存键带上代际号，写操作只需删除这一个键，旧代际的缓存自然不可达。
     *
     * <p>为什么不用「按前缀删除」：{@code CacheService} 只有 get/put/delete，前缀删除是 Redis 专属能力
     * （{@code CacheAdminRepository} 才有）。为了字典失效给缓存抽象加方法收益不大，代际键一次 delete 就够。</p>
     */
    private static final String GENERATION_KEY = CacheNamespaces.DICT_PREFIX + "generation";
    private static final Duration GENERATION_TTL = Duration.ofDays(365);

    private final DictRepository dictRepository;
    private final CacheService cacheService;

    public PageResponse<DictDtos.TypeResponse> pageTypes(PageQuery pageQuery, String keyword, Boolean isEnabled) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        DictTypeQuery query = new DictTypeQuery();
        query.setKeyword(keyword);
        query.setIsEnabled(isEnabled);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<DictType> result = dictRepository.pageTypes(query);
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toTypeResponse).toList(),
                result.getTotal());
    }

    public List<DictDtos.ItemResponse> items(Long typeId) {
        String key = CacheNamespaces.DICT_PREFIX + "items:" + generation() + ":" + typeId;
        List<DictDtos.ItemResponse> cached = cacheService.get(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        List<DictDtos.ItemResponse> fresh = dictRepository.findItemsByTypeId(typeId).stream()
                .map(this::toItemResponse).toList();
        cacheService.put(key, fresh, CACHE_TTL);
        return fresh;
    }

    /**
     * 按类型批量查询，前端一次拿多个字典。
     */
    public List<DictDtos.TypeBundle> bundles(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        String cacheKey = CacheNamespaces.DICT_PREFIX + "bundles:" + generation() + ":"
                + types.stream().sorted().collect(Collectors.joining(","));
        List<DictDtos.TypeBundle> cached = cacheService.get(cacheKey, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        List<DictItem> items = dictRepository.findItemsByTypeCodes(types);
        Map<String, List<DictItem>> groups = new LinkedHashMap<>();
        for (DictItem item : items) {
            if (item.getTypeCode() != null) {
                groups.computeIfAbsent(item.getTypeCode(), key -> new ArrayList<>()).add(item);
            }
        }
        List<DictDtos.TypeBundle> result = new ArrayList<>();
        for (String type : types) {
            DictDtos.TypeBundle bundle = new DictDtos.TypeBundle();
            bundle.setType(type);
            DictType dictType = dictRepository.findTypeByCode(type);
            bundle.setName(dictType == null ? type : dictType.getName());
            bundle.setItems(groups.getOrDefault(type, List.of()).stream().map(this::toItemResponse).toList());
            result.add(bundle);
        }
        cacheService.put(cacheKey, result, CACHE_TTL);
        return result;
    }

    /**
     * 字典任何写操作后调用：保证「改完立即生效」，而不是等 TTL 过期。
     *
     * <p>**必须递增而不是删除**：删除计数器会让下一次 {@link #generation()} 从 0 重新开始，
     * 生成与旧缓存**相同**的键，于是命中陈旧数据——本 CR 第一版就是删除，被集成测试直接抓到
     * （新增字典项后 items 仍返回空列表）。递增则旧键永远不可达，最多由 TTL 回收。</p>
     */
    private void evictDictCache() {
        cacheService.increment(GENERATION_KEY, GENERATION_TTL);
    }

    private long generation() {
        return cacheService.increment(GENERATION_KEY, GENERATION_TTL);
    }

    @Transactional
    public DictDtos.TypeResponse createType(DictDtos.TypeSaveRequest request) {
        if (dictRepository.findTypeByCode(request.getType()) != null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "字典类型已存在");
        }
        DictType dictType = new DictType();
        applyType(dictType, request);
        dictRepository.insertType(dictType);
        evictDictCache();
        return toTypeResponse(dictType);
    }

    @Transactional
    public DictDtos.TypeResponse updateType(Long id, DictDtos.TypeSaveRequest request) {
        DictType dictType = requireType(id);
        DictType sameCode = dictRepository.findTypeByCode(request.getType());
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "字典类型已存在");
        }
        applyType(dictType, request);
        dictRepository.updateType(dictType);
        evictDictCache();
        return toTypeResponse(dictType);
    }

    @Transactional
    public void deleteType(Long id) {
        requireType(id);
        if (dictRepository.existsItems(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "字典下存在字典项，不能删除");
        }
        dictRepository.deleteType(id);
        evictDictCache();
    }

    @Transactional
    public DictDtos.ItemResponse createItem(DictDtos.ItemSaveRequest request) {
        requireType(request.getDictTypeId());
        DictItem item = new DictItem();
        applyItem(item, request);
        dictRepository.insertItem(item);
        evictDictCache();
        return toItemResponse(item);
    }

    @Transactional
    public DictDtos.ItemResponse updateItem(Long id, DictDtos.ItemSaveRequest request) {
        DictItem item = dictRepository.findItemById(id);
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        requireType(request.getDictTypeId());
        applyItem(item, request);
        dictRepository.updateItem(item);
        evictDictCache();
        return toItemResponse(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        if (dictRepository.findItemById(id) == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        dictRepository.deleteItem(id);
        evictDictCache();
    }

    private DictType requireType(Long id) {
        DictType dictType = dictRepository.findTypeById(id);
        if (dictType == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典类型不存在");
        }
        return dictType;
    }

    private void applyType(DictType dictType, DictDtos.TypeSaveRequest request) {
        dictType.setName(request.getName());
        dictType.setType(request.getType());
        dictType.setRemark(request.getRemark());
        dictType.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private void applyItem(DictItem item, DictDtos.ItemSaveRequest request) {
        item.setDictTypeId(request.getDictTypeId());
        item.setLabel(request.getLabel());
        item.setValue(request.getValue());
        item.setSort(request.getSort() == null ? 0 : request.getSort());
        item.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
        item.setRemark(request.getRemark());
    }

    private DictDtos.TypeResponse toTypeResponse(DictType dictType) {
        DictDtos.TypeResponse response = new DictDtos.TypeResponse();
        response.setId(dictType.getId());
        response.setName(dictType.getName());
        response.setType(dictType.getType());
        response.setRemark(dictType.getRemark());
        response.setIsEnabled(dictType.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(dictType.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(dictType.getUpdatedAt()));
        return response;
    }

    private DictDtos.ItemResponse toItemResponse(DictItem item) {
        DictDtos.ItemResponse response = new DictDtos.ItemResponse();
        response.setId(item.getId());
        response.setDictTypeId(item.getDictTypeId());
        response.setLabel(item.getLabel());
        response.setValue(item.getValue());
        response.setSort(item.getSort());
        response.setIsEnabled(item.getIsEnabled());
        response.setRemark(item.getRemark());
        response.setCreatedAt(TimeSupport.toInstant(item.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(item.getUpdatedAt()));
        return response;
    }
}
