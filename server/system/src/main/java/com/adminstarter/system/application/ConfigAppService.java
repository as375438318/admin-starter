package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.MaskUtils;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.ConfigDtos;
import com.adminstarter.system.domain.model.Config;
import com.adminstarter.system.domain.query.ConfigQuery;
import com.adminstarter.system.domain.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfigAppService {

    private final ConfigRepository configRepository;
    private final PublicConfigAppService publicConfigAppService;

    public PageResponse<ConfigDtos.Response> page(PageQuery pageQuery, String keyword, Boolean isSensitive) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        ConfigQuery query = new ConfigQuery();
        query.setKeyword(keyword);
        query.setIsSensitive(isSensitive);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<Config> result = configRepository.page(query);
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toResponse).toList(),
                result.getTotal());
    }

    public ConfigDtos.Response getById(Long id) {
        Config config = configRepository.findById(id);
        if (config == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "参数不存在");
        }
        return toResponse(config);
    }

    @Transactional
    public ConfigDtos.Response create(ConfigDtos.SaveRequest request) {
        validateKnownValues(request);
        if (configRepository.existsByKey(request.getConfigKey(), null)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "参数键已存在");
        }
        Config config = new Config();
        apply(config, request);
        configRepository.insert(config);
        publicConfigAppService.evict();
        return toResponse(config);
    }

    @Transactional
    public ConfigDtos.Response update(Long id, ConfigDtos.SaveRequest request) {
        validateKnownValues(request);
        Config config = configRepository.findById(id);
        if (config == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "参数不存在");
        }
        if (configRepository.existsByKey(request.getConfigKey(), id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "参数键已存在");
        }
        boolean keepMaskedValue = Boolean.TRUE.equals(config.getIsSensitive())
                && request.getConfigValue() != null && request.getConfigValue().equals(MaskUtils.MASK);
        String oldValue = config.getConfigValue();
        apply(config, request);
        if (keepMaskedValue) {
            config.setConfigValue(oldValue);
        }
        configRepository.update(config);
        publicConfigAppService.evict();
        return toResponse(config);
    }

    @Transactional
    public void delete(Long id) {
        if (configRepository.findById(id) == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "参数不存在");
        }
        configRepository.deleteById(id);
        publicConfigAppService.evict();
    }

    /** 已知参数的值域校验：避免“能保存但实际不可用”的配置 */
    private void validateKnownValues(ConfigDtos.SaveRequest request) {
        if (PublicConfigAppService.KEY_UPLOAD_MAX_SIZE.equals(request.getConfigKey())) {
            PublicConfigAppService.validateUploadMaxSize(request.getConfigValue());
        }
    }

    private void apply(Config config, ConfigDtos.SaveRequest request) {
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setName(request.getName());
        config.setRemark(request.getRemark());
        config.setIsSensitive(request.getIsSensitive() == null ? Boolean.FALSE : request.getIsSensitive());
        config.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private ConfigDtos.Response toResponse(Config config) {
        ConfigDtos.Response response = new ConfigDtos.Response();
        response.setId(config.getId());
        response.setConfigKey(config.getConfigKey());
        boolean sensitive = Boolean.TRUE.equals(config.getIsSensitive());
        response.setConfigValue(sensitive ? MaskUtils.mask(config.getConfigValue()) : config.getConfigValue());
        response.setName(config.getName());
        response.setRemark(config.getRemark());
        response.setIsSensitive(config.getIsSensitive());
        response.setIsEnabled(config.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(config.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(config.getUpdatedAt()));
        return response;
    }
}
