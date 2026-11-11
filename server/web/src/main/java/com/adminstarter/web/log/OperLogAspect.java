package com.adminstarter.web.log;

import com.adminstarter.common.log.OperLogContext;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.error.ImportValidationException;
import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.IpUtils;
import com.adminstarter.system.application.OperLogAppService;
import com.adminstarter.system.domain.model.OperLog;
import com.adminstarter.web.filter.TraceIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作日志切面：记录操作人、IP、耗时与结果，失败也记录。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final String MASK_FALLBACK = "******";

    private final OperLogAppService operLogAppService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(record)")
    public Object around(ProceedingJoinPoint joinPoint, OperLogRecord record) throws Throwable {
        long start = System.currentTimeMillis();
        int resultCode = ErrorCode.SUCCESS.getCode();
        String resultMessage = "success";
        try {
            return joinPoint.proceed();
        } catch (BizException exception) {
            resultCode = exception.getErrorCode().getCode();
            resultMessage = exception.getMessage();
            throw exception;
        } catch (ImportValidationException exception) {
            resultCode = ErrorCode.VALIDATION_FAILED.getCode();
            resultMessage = exception.getMessage();
            throw exception;
        } catch (Throwable exception) {
            resultCode = ErrorCode.SYSTEM_ERROR.getCode();
            resultMessage = ErrorCode.SYSTEM_ERROR.getMessage();
            throw exception;
        } finally {
            save(joinPoint, record, resultCode, resultMessage, System.currentTimeMillis() - start);
        }
    }

    private void save(ProceedingJoinPoint joinPoint, OperLogRecord record, int resultCode, String resultMessage,
                      long elapsedMs) {
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
            operLog.setModule(record.module());
            operLog.setAction(record.action());
            operLog.setMethod(joinPoint.getSignature().toShortString());
            HttpServletRequest request = currentRequest();
            if (request != null) {
                operLog.setRequestUri(request.getRequestURI());
                operLog.setRequestMethod(request.getMethod());
                operLog.setIp(IpUtils.clientIp(request));
            }
            operLog.setParams(serializeArgs(joinPoint.getArgs()));
            operLog.setResultCode(resultCode);
            operLog.setResultMessage(resultMessage);
            operLog.setElapsedMs(elapsedMs);
            operLogAppService.record(operLog);
        } catch (RuntimeException ignored) {
            // 审计写入失败不阻断业务
        } finally {
            OperLogContext.clear();
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String serializeArgs(Object[] args) {
        List<Object> serializable = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof MultipartFile) {
                continue;
            }
            serializable.add(arg);
        }
        try {
            JsonNode root = objectMapper.valueToTree(serializable);
            Map<String, Object> extra = OperLogContext.snapshot();
            if (!extra.isEmpty()) {
                ObjectNode envelope = objectMapper.createObjectNode();
                envelope.set("args", root);
                envelope.set("ext", objectMapper.valueToTree(extra));
                root = envelope;
            }
            maskSensitiveFields(root);
            String json = objectMapper.writeValueAsString(root);
            return json.length() > MAX_PARAMS_LENGTH ? json.substring(0, MAX_PARAMS_LENGTH) : json;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 审计快照按字段名脱敏：password 全掩码；phone/email 复用同一套掩码函数，
     * 格式不匹配时用通用掩码串，禁止明文落库（见 docs/design/field-masking.md §8）。
     */
    private void maskSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                String masked = child.isTextual() ? snapshotMask(name, child.asText()) : null;
                if (masked != null) {
                    object.put(name, masked);
                } else {
                    maskSensitiveFields(child);
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::maskSensitiveFields);
        }
    }

    private String snapshotMask(String name, String value) {
        if ("password".equalsIgnoreCase(name)) {
            return MASK_FALLBACK;
        }
        if ("phone".equalsIgnoreCase(name)) {
            String masked = MaskFormat.PHONE.mask(value);
            return masked == null ? MASK_FALLBACK : masked;
        }
        if ("email".equalsIgnoreCase(name)) {
            String masked = MaskFormat.EMAIL.mask(value);
            return masked == null ? MASK_FALLBACK : masked;
        }
        return null;
    }
}
