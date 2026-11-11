package com.adminstarter.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档补充声明：只影响 /v3/api-docs*，不改变任何接口行为。
 * 413 由 GlobalExceptionHandler 产出，Springdoc 无法从异常处理器推导，故在此追加到上传接口。
 */
@Configuration
public class OpenApiDocsConfig {

    @Bean
    public OpenApiCustomizer uploadPayloadTooLargeCustomizer() {
        return openApi -> {
            PathItem pathItem = path(openApi, "/api/v1/files");
            Operation upload = pathItem == null ? null : pathItem.getPost();
            if (upload == null) {
                return;
            }
            ApiResponses responses = upload.getResponses() == null ? new ApiResponses() : upload.getResponses();
            responses.addApiResponse("413", new ApiResponse()
                    .description("文件超过当前业务上限（sys.upload.max-size，来自 /api/v1/public/configs 的 uploadMaxSize）；"
                            + "data.maxSize 为当前上限字节数"));
            upload.setResponses(responses);
        };
    }

    private PathItem path(OpenAPI openApi, String path) {
        return openApi.getPaths() == null ? null : openApi.getPaths().get(path);
    }
}
