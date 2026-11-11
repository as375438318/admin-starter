package com.adminstarter.system.application.excel;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.log.OperLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 通用模板下载：模板由 {@link ExcelSchema} 派生，业务模块不得手写表头。
 */
@Service
@RequiredArgsConstructor
public class ExcelTemplateAppService {

    private final ExcelTemplateWriter templateWriter;

    public TemplateFile generate(ExcelSchema<?> schema) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            templateWriter.write(schema, output);
            OperLogContext.put("fileName", schema.templateFileName());
            OperLogContext.put("fileSize", output.size());
            return new TemplateFile(schema.templateFileName(), output.toByteArray());
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "模板生成失败");
        }
    }

    public record TemplateFile(String fileName, byte[] content) {
    }
}
