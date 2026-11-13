package com.adminstarter.system.application.audit;

import com.adminstarter.common.util.MaskUtils;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.IpUtils;
import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.domain.repository.ChangeAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 配置面变更留痕 recorder（CR-20261114-042）。
 *
 * <p>三条口径（改之前先看断言 #109）：</p>
 * <ol>
 *   <li><b>只记"变了什么"</b>：UPDATE 只保留值发生变化的字段，避免整行快照把差异埋掉；</li>
 *   <li><b>敏感对象整体打码</b>：{@code sensitive=true} 时 before/after 的值一律掩码，
 *       旧值同样要掩——只掩新值等于把密文换成明文（{@code sys_config.is_sensitive}）；</li>
 *   <li><b>留痕失败不阻断业务</b>：异常吞掉并 warn 日志，与登录审计同口径；
 *       但留痕与业务在同一事务里——业务回滚则留痕一并回滚，保证"没改成就不留痕"。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeAuditRecorder {

    public static final String TYPE_CONFIG = "CONFIG";
    public static final String TYPE_DICT_TYPE = "DICT_TYPE";
    public static final String TYPE_DICT_ITEM = "DICT_ITEM";
    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_ROLE_GRANT = "ROLE_GRANT";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_GRANT = "GRANT";

    private final ChangeAuditRepository repository;
    private final ObjectMapper objectMapper;

    /** 新增：只记新增后的关键字段 */
    public void recordCreated(String type, String key, String name, Map<String, Object> snapshot, boolean sensitive) {
        write(type, key, name, ACTION_CREATE, null, snapshot, sensitive);
    }

    /** 修改：只记值发生变化的字段；无变化则不写（避免"点了一下保存"污染轨迹） */
    public void recordUpdated(String type, String key, String name,
                              Map<String, Object> before, Map<String, Object> after, boolean sensitive) {
        Map<String, Object> diffBefore = new LinkedHashMap<>();
        Map<String, Object> diffAfter = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object oldValue = before.get(entry.getKey());
            if (!Objects.equals(oldValue, entry.getValue())) {
                diffBefore.put(entry.getKey(), oldValue);
                diffAfter.put(entry.getKey(), entry.getValue());
            }
        }
        if (diffAfter.isEmpty()) {
            return;
        }
        write(type, key, name, ACTION_UPDATE, diffBefore, diffAfter, sensitive);
    }

    /** 删除：只记删除前快照 */
    public void recordDeleted(String type, String key, String name, Map<String, Object> snapshot, boolean sensitive) {
        write(type, key, name, ACTION_DELETE, snapshot, null, sensitive);
    }

    /** 授权变更（角色 ↔ 菜单）：快照形如 {"menuIds": [...]} */
    public void recordGranted(String type, String key, String name,
                              Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diffBefore = new LinkedHashMap<>();
        Map<String, Object> diffAfter = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object oldValue = before.get(entry.getKey());
            if (!Objects.equals(oldValue, entry.getValue())) {
                diffBefore.put(entry.getKey(), oldValue);
                diffAfter.put(entry.getKey(), entry.getValue());
            }
        }
        if (diffAfter.isEmpty()) {
            return;
        }
        write(type, key, name, ACTION_GRANT, diffBefore, diffAfter, false);
    }

    private void write(String type, String key, String name, String action,
                       Map<String, Object> before, Map<String, Object> after, boolean sensitive) {
        try {
            ChangeAudit audit = new ChangeAudit();
            audit.setTargetType(type);
            audit.setTargetKey(key);
            audit.setTargetName(name);
            audit.setAction(action);
            audit.setIsSensitive(sensitive);
            audit.setBeforeValue(serialize(before, sensitive));
            audit.setAfterValue(serialize(after, sensitive));
            CurrentUser current = DataScopeContext.get();
            if (current != null) {
                audit.setOperatorId(current.getUserId());
                audit.setOperatorName(current.getUsername());
            }
            audit.setIp(resolveIp());
            audit.setTraceId(MDC.get("traceId"));
            repository.insert(audit);
        } catch (RuntimeException e) {
            // 留痕不能反过来把业务搞挂（与登录审计同口径）
            log.warn("配置变更留痕写入失败：type={} key={} action={}", type, key, action, e);
        }
    }

    private String serialize(Map<String, Object> snapshot, boolean sensitive) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            Object value = entry.getValue();
            payload.put(entry.getKey(), sensitive && value != null ? MaskUtils.MASK : value);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("配置变更快照序列化失败", e);
            return null;
        }
    }

    private String resolveIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        try {
            return IpUtils.clientIp(attributes.getRequest());
        } catch (RuntimeException e) {
            return null;
        }
    }
}