package com.adminstarter.web.security;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.export.ExportModule;
import com.adminstarter.system.application.export.ExportModuleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 导出权限守卫：给通用导出入口用 SpEL 校验。
 *
 * <p>为什么需要它：通用入口 {@code /api/v1/exports/{moduleKey}} 的权限码**按模块不同**
 * （示例任务是 {@code demo:task:export}、岗位是 {@code system:post:export}…），
 * 没法在方法上写死一个 {@code @PreAuthorize("hasAuthority('...')")}。
 * 这里把"模块 → 权限码"的查表放进 SpEL，既保留按模块鉴权，也避免为了通用入口放宽成"登录即可导出"。</p>
 *
 * <p>未知模块直接返回 false：不该因为"模块没注册"而暴露成 500 或放行。</p>
 */
@Component("exportGuard")
@RequiredArgsConstructor
public class ExportGuard {

    private final ExportModuleRegistry registry;

    public boolean allowed(String moduleKey) {
        CurrentUser current = DataScopeContext.get();
        if (current == null) {
            return false;
        }
        if (!registry.contains(moduleKey)) {
            return false;
        }
        ExportModule module = registry.require(moduleKey);
        return current.hasPermission(module.permission());
    }
}