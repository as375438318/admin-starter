package com.adminstarter.system.application;

import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.error.FieldValidationException;
import com.adminstarter.system.application.dto.PublicConfigDtos;
import com.adminstarter.system.domain.model.Config;
import com.adminstarter.system.domain.repository.ConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开配置读取：只返回白名单键，值来自 sys_config，走缓存。
 * 系统参数新增/修改/删除后必须调用 {@link #evict()}，保证下一次请求读到新值。
 */
@Service
@RequiredArgsConstructor
public class PublicConfigAppService {

    /** 公开展示的系统名称 */
    public static final String KEY_NAME = "sys.name";
    /** 登录页副标题 */
    public static final String KEY_SLOGAN = "sys.name.slogan";
    /** 页脚版权 */
    public static final String KEY_COPYRIGHT = "sys.copyright";
    /** 页脚备案号 */
    public static final String KEY_ICP = "sys.icp";
    /** 单个文件上传上限（字节） */
    public static final String KEY_UPLOAD_MAX_SIZE = "sys.upload.max-size";

    /** 上传上限下限 1MB */
    public static final long MIN_UPLOAD_MAX_SIZE = 1024L * 1024L;
    /** 上传上限上限 200MB：容器层天花板，业务值不得超过 */
    public static final long MAX_UPLOAD_MAX_SIZE = 200L * 1024L * 1024L;
    /** 缺省上传上限 10MB */
    public static final long DEFAULT_UPLOAD_MAX_SIZE = 10L * 1024L * 1024L;

    /** 白名单之外的键一律不返回；新增公开项必须同时改这里与文档 */
    private static final List<String> PUBLIC_KEYS = List.of(KEY_NAME, KEY_SLOGAN, KEY_COPYRIGHT, KEY_ICP,
            KEY_UPLOAD_MAX_SIZE);
    private static final String CACHE_KEY = com.adminstarter.common.cache.CacheNamespaces.PUBLIC_CONFIG_KEY;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ConfigRepository configRepository;
    private final CacheService cacheService;

    public PublicConfigDtos.Response current() {
        PublicConfigDtos.Response cached = cacheService.get(CACHE_KEY, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        PublicConfigDtos.Response response = load();
        cacheService.put(CACHE_KEY, response, CACHE_TTL);
        return response;
    }

    public void evict() {
        cacheService.delete(CACHE_KEY);
    }

    /** 当前生效的单个文件上传上限（字节） */
    public long uploadMaxSize() {
        Long value = current().getUploadMaxSize();
        return value == null ? DEFAULT_UPLOAD_MAX_SIZE : value;
    }

    /**
     * 保存系统参数时校验上传上限：必须为 1MB–200MB 之间的字节整数。
     * 200MB 是容器层天花板，业务值超过它没有意义。
     */
    public static void validateUploadMaxSize(String value) {
        long parsed;
        try {
            parsed = Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new FieldValidationException("configValue", "VALIDATION_FAILED", "上传上限必须填写字节整数");
        }
        if (parsed < MIN_UPLOAD_MAX_SIZE || parsed > MAX_UPLOAD_MAX_SIZE) {
            throw new FieldValidationException("configValue", "VALIDATION_FAILED",
                    "上传上限必须在 1MB（1048576）到 200MB（209715200）之间，按字节填写");
        }
    }

    /** 字节数转 MB 展示值，整数去掉小数位 */
    public static String formatMb(long bytes) {
        BigDecimal mb = BigDecimal.valueOf(bytes)
                .divide(BigDecimal.valueOf(1024L * 1024L), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return mb.toPlainString();
    }

    private static long effectiveUploadMaxSize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_UPLOAD_MAX_SIZE;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return Math.max(MIN_UPLOAD_MAX_SIZE, Math.min(MAX_UPLOAD_MAX_SIZE, parsed));
        } catch (NumberFormatException e) {
            return DEFAULT_UPLOAD_MAX_SIZE;
        }
    }

    private PublicConfigDtos.Response load() {
        List<Config> configs = configRepository.findByKeys(PUBLIC_KEYS);
        Map<String, String> values = new HashMap<>();
        for (Config config : configs) {
            // 敏感项与停用项即便误加入白名单也不对外返回，双重兜底。
            if (Boolean.TRUE.equals(config.getIsSensitive()) || !Boolean.TRUE.equals(config.getIsEnabled())) {
                continue;
            }
            values.put(config.getConfigKey(), config.getConfigValue());
        }
        PublicConfigDtos.Response response = new PublicConfigDtos.Response();
        response.setName(values.get(KEY_NAME));
        response.setSlogan(values.get(KEY_SLOGAN));
        response.setCopyright(values.get(KEY_COPYRIGHT));
        response.setIcp(values.get(KEY_ICP));
        response.setUploadMaxSize(effectiveUploadMaxSize(values.get(KEY_UPLOAD_MAX_SIZE)));
        return response;
    }
}
