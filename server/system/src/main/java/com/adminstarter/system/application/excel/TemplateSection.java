package com.adminstarter.system.application.excel;

import java.util.List;

/**
 * 模板「填写须知」页的附加区块（枚举清单、关联 ID 清单等），由业务 schema 提供。
 */
public record TemplateSection(String title, List<String> headers, List<List<String>> rows) {
}
