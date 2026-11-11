package com.adminstarter.web.log;

import com.adminstarter.common.cache.CacheNamespace;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.IpUtils;
import com.adminstarter.system.application.OperLogAppService;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.web.filter.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存清理审计：在操作日志中记录“谁、何时、哪个命名空间、删了多少 key”。
 * 敏感命名空间不记录完整 key 名（令牌/会话标识），只记录命名空间与数量。
 */
@Component
@RequiredArgsConstructor
public class CacheAuditRecorder {

    private static final String MODULE = "缓存管理";

    private final OperLogAppService operLogAppService;
    private final ObjectMapper objectMapper;

    public void record(String action, CacheNamespace namespace, String key, long deleted, long elapsedMs) {
        try {
            OperLog operLog = new OperLog();
            operLog.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
            CurrentUser current = DataScopeContext.get();
            if (current != null) {
                operLog.setUserId(current.getUserId());
                operLog.setUsername(current.getUsername());
                operLog.setDeptId(current.getDeptId());
                operLog.setCreateBy(current.getUserId());
            }
            operLog.setModule(MODULE);
            operLog.setAction(action);
            operLog.setMethod("CacheController");
            HttpServletRequest request = currentRequest();
            if (request != null) {
                operLog.setRequestUri(request.getRequestURI());
                operLog.setRequestMethod(request.getMethod());
                operLog.setIp(IpUtils.clientIp(request));
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("namespaceCode", namespace.code());
            params.put("namespacePrefix", namespace.prefix());
            if (key != null && !namespace.sensitive()) {
                params.put("key", key);
            }
            params.put("deletedCount", deleted);
            operLog.setParams(objectMapper.writeValueAsString(params));
            operLog.setResultCode(ErrorCode.SUCCESS.getCode());
            operLog.setResultMessage("success");
            operLog.setElapsedMs(elapsedMs);
            operLogAppService.record(operLog);
        } catch (Exception ignored) {
            // 审计写入失败不阻断清理；与 OperLogAspect 口径一致（见 docs/design/cache-management.md §8）
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
