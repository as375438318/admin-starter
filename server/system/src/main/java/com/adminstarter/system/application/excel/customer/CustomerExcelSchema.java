package com.adminstarter.system.application.excel.customer;

import com.adminstarter.system.application.excel.ExcelField;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.domain.model.Customer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 客户档案的导出字段元数据（仅导出，无导入：本模块由生成器产出，导入能力按需再接）。
 *
 * <p>字段 key 必须与前端列表列定义一致（前端列的 {@code exportKey}，断言 #104 ④）——
 * 不一致时导出会被判 400「未知的导出列」。</p>
 */
@Component
public class CustomerExcelSchema implements ExcelSchema<Customer> {

    @Override
    public String key() {
        return "demo-customer";
    }

    @Override
    public String title() {
        return "客户档案";
    }

    @Override
    public String templateFileName() {
        return "客户档案导出.xlsx";
    }

    @Override
    public String dataSheetName() {
        return "客户档案";
    }

    @Override
    public List<ExcelField<Customer>> fields() {
        return List.of(
                ExcelField.<Customer>builder()
                        .key("id")
                        .exportHeader("客户ID")
                        .exporter(row -> row.getId() == null ? "" : String.valueOf(row.getId()))
                        .build(),
                ExcelField.<Customer>builder()
                        .key("name")
                        .exportHeader("客户名称")
                        .exporter(Customer::getName)
                        .build(),
                ExcelField.<Customer>builder()
                        .key("level")
                        .exportHeader("客户等级")
                        .exporter(Customer::getLevel)
                        .build(),
                ExcelField.<Customer>builder()
                        .key("contact")
                        .exportHeader("联系人")
                        .exporter(Customer::getContact)
                        .build(),
                ExcelField.<Customer>builder()
                        .key("phone")
                        .exportHeader("联系电话")
                        .exporter(Customer::getPhone)
                        .build(),
                ExcelField.<Customer>builder()
                        .key("isEnabled")
                        .exportHeader("状态")
                        .exporter(row -> Boolean.TRUE.equals(row.getIsEnabled()) ? "启用" : "停用")
                        .build(),
                ExcelField.<Customer>builder()
                        .key("remark")
                        .exportHeader("备注")
                        .exporter(Customer::getRemark)
                        .build(),
                ExcelField.<Customer>builder()
                        .key("createdAt")
                        .exportHeader("创建时间")
                        .exporter(row -> row.getCreatedAt() == null ? "" : row.getCreatedAt().toString())
                        .build());
    }
}