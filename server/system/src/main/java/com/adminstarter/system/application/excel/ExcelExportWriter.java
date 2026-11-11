package com.adminstarter.system.application.excel;

import com.adminstarter.common.masking.MaskResolver;
import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 导出写表器：列与单元格值都从 {@link ExcelSchema} 派生。
 * <ul>
 *   <li>同步快通道（≤2000 行）：{@link #writeSync} 内存构建。</li>
 *   <li>异步大文件（≤100000 行）：{@link #writeStreaming} 用 SXSSF 边写边落盘，内存上界为窗口行数（100 行）
 *       + 当前页行数，不随总行数增长。</li>
 * </ul>
 * 隐藏态字段整列不输出：列可见性由此处的 {@link MaskResolver} 三态判定统一决定。
 */
@Component
@RequiredArgsConstructor
public class ExcelExportWriter {

    private static final int SXSSF_WINDOW = 100;

    private final ReferenceDirectoryService referenceDirectoryService;

    public interface RowSource<T> {
        /** 返回下一页；没有更多返回 null。 */
        List<T> next();
    }

    public interface ProgressListener {
        void onProgress(long written);
    }

    public <T> void writeSync(ExcelSchema<T> schema, List<T> rows, OutputStream output) throws IOException {
        List<ExcelField<T>> columns = visibleColumns(schema);
        ReferenceContext refs = referenceDirectoryService.context(columns);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(schema.dataSheetName());
            CellStyle headerStyle = headerStyle(workbook);
            writeHeader(sheet, columns, headerStyle);
            int rowIndex = 1;
            for (T row : rows) {
                writeRow(sheet.createRow(rowIndex++), columns, row, refs);
            }
            workbook.write(output);
        }
    }

    public <T> long writeStreaming(ExcelSchema<T> schema, RowSource<T> source, OutputStream output,
                                   ProgressListener listener) throws IOException {
        List<ExcelField<T>> columns = visibleColumns(schema);
        ReferenceContext refs = referenceDirectoryService.context(columns);
        SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW);
        try {
            Sheet sheet = workbook.createSheet(schema.dataSheetName());
            CellStyle headerStyle = headerStyle(workbook);
            writeHeader(sheet, columns, headerStyle);
            long written = 0;
            List<T> page;
            int rowIndex = 1;
            while ((page = source.next()) != null) {
                for (T row : page) {
                    writeRow(sheet.createRow(rowIndex++), columns, row, refs);
                    written++;
                }
                if (listener != null) {
                    listener.onProgress(written);
                }
            }
            workbook.write(output);
            return written;
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private <T> List<ExcelField<T>> visibleColumns(ExcelSchema<T> schema) {
        return schema.exportFields().stream()
                .filter(field -> field.getMaskFieldKey() == null
                        || MaskResolver.canView(field.getMaskFieldKey())
                        || MaskResolver.canMask(field.getMaskFieldKey()))
                .toList();
    }

    private <T> void writeHeader(Sheet sheet, List<ExcelField<T>> columns, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            ExcelField<T> field = columns.get(index);
            Cell cell = header.createCell(index);
            cell.setCellValue(field.getExportHeader());
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, Math.min(50, Math.max(12, field.getExportHeader().length() * 2 + 6)) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private <T> void writeRow(Row row, List<ExcelField<T>> columns, T entity, ReferenceContext refs) {
        for (int index = 0; index < columns.size(); index++) {
            ExcelField<T> field = columns.get(index);
            String value = value(field, entity, refs);
            row.createCell(index).setCellValue(value == null ? "" : value);
        }
    }

    private <T> String value(ExcelField<T> field, T entity, ReferenceContext refs) {
        String raw = switch (field.getKind()) {
            case REF_DEPT -> refs.deptPath(asLong(field, entity));
            case REF_USER -> refs.userDisplay(asLong(field, entity));
            case ENUM -> enumLabel(field, field.getRefValue() == null ? null : field.getRefValue().apply(entity));
            case TEXT -> field.getExporter() == null ? "" : field.getExporter().apply(entity);
        };
        if (field.getMaskFieldKey() == null) {
            return raw;
        }
        return MaskResolver.resolve(field.getMaskFieldKey(), field.getMaskFormat(), raw).value();
    }

    private <T> Long asLong(ExcelField<T> field, T entity) {
        Object value = field.getRefValue() == null ? null : field.getRefValue().apply(entity);
        return value instanceof Number number ? number.longValue() : null;
    }

    /** 枚举导出：按存储值找中文含义；找不到时原样输出，不丢数据。 */
    private <T> String enumLabel(ExcelField<T> field, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return field.getEnums().stream()
                .filter(option -> option.value().equals(text))
                .map(EnumOption::label)
                .findFirst()
                .orElse(text);
    }

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
