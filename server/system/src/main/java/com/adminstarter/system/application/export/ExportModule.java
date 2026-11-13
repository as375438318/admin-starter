package com.adminstarter.system.application.export;

import java.util.Map;

/**
 * 通用导出中心的一个可导出模块（SPI）。
 *
 * <p>为什么要有这层：导出能力原本长在具体模块的 AppService 里（示例任务、岗位各一套入口），
 * 于是每加一个模块就要复制一遍"组装查询 → 计数 → 规划同步/异步 → 裁剪列 → 记任务"的流程。
 * 这里把差异收成四个方法，公共流程由 {@link GenericExportAppService} 走一遍。</p>
 *
 * <p>三条约定：</p>
 * <ol>
 *   <li><b>权限码由模块自己声明</b>，入口统一用 {@code @PreAuthorize("@exportGuard.allowed(#moduleKey)")} 校验——
 *       不能因为"走了通用入口"就绕过各自的导出权限；</li>
 *   <li>过滤参数只从 {@code params} 里取，**未知参数一律忽略**（不做反射绑定，避免参数注入式筛选）；</li>
 *   <li>返回的 {@link ExcelExportPlanner.ExportPlan} 决定同步还是异步——模块不自己判断行数上限。</li>
 * </ol>
 */
public interface ExportModule {

    /** 模块键（URL 里用），如 {@code customer} */
    String key();

    /** 模块中文名（文件名前缀与前端提示用） */
    String name();

    /** 导出所需权限码，如 {@code demo:customer:export} */
    String permission();

    /** 组装查询并规划导出；{@code params} 为原始请求参数 */
    ExcelExportPlanner.ExportPlan plan(Map<String, String> params);
}