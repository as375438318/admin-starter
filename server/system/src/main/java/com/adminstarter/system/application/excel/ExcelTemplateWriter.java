package com.adminstarter.system.application.excel;

import com.adminstarter.system.application.excel.ReferenceDirectoryService.ReferenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模板生成器：表头、必填标记、格式说明、示例行、填写须知、下拉数据全部从 {@link ExcelSchema} 派生。
 * <p>模板小，使用内存构建；导出大文件走 {@link ExcelExportWriter} 的流式路径。</p>
 * <p>下拉用隐藏 sheet「引用数据」+ 区域引用，而非内联列表（内联受 255 字符上限）。
 * 下拉只是引导，允许手工填写，不是校验；最终以导入时刻库中数据为准。</p>
 */
@Component
@RequiredArgsConstructor
public class ExcelTemplateWriter {

    private static final String REFERENCE_SHEET = "引用数据";
    private static final String REFERENCE_HINT = "（请勿修改）";
    private static final int MAX_DATA_ROW = 20000;

    private final ReferenceDirectoryService referenceDirectoryService;

    public void write(ExcelSchema<?> schema, OutputStream output) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            List<? extends ExcelField<?>> fields = schema.importFields();
            writeDataSheet(workbook, schema, fields, headerStyle);
            writeGuideSheet(workbook, schema, fields, headerStyle);
            writeReferenceSheet(workbook, fields);
            workbook.write(output);
        }
    }

    private void writeDataSheet(XSSFWorkbook workbook, ExcelSchema<?> schema,
                                List<? extends ExcelField<?>> fields, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(schema.dataSheetName());
        Row header = sheet.createRow(0);
        XSSFDrawing drawing = ((XSSFSheet) sheet).createDrawingPatriarch();
        for (int index = 0; index < fields.size(); index++) {
            ExcelField<?> field = fields.get(index);
            Cell cell = header.createCell(index);
            // 表头用富文本：必填标记 * 单独一段并标红。纯文本做不到"只有星号是红的"，
            // 而整列标红又会让表头失去层次——用户明确要求"* 号红色"。
            cell.setCellValue(headerText(workbook, field));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, columnWidth(field.getImportHeader()));
            String comment = headerComment(field);
            if (comment != null) {
                XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, index, 0, index + 2, 3);
                XSSFComment cellComment = drawing.createCellComment(anchor);
                cellComment.setString(new XSSFRichTextString(comment));
                cell.setCellComment(cellComment);
            }
        }
        sheet.createFreezePane(0, 1);
    }

    /**
     * 隐藏的「引用数据」页：每列一组下拉源，数据页按区域引用绑定。
     * 页被锁定只是引导（无密码可解除），不作为安全措施；真正的校验在导入时刻。
     */
    private void writeReferenceSheet(XSSFWorkbook workbook, List<? extends ExcelField<?>> fields) {
        List<Integer> listColumns = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            ExcelField<?> field = fields.get(index);
            if (!field.getEnums().isEmpty()
                    || field.getKind() == FieldKind.REF_DEPT
                    || field.getKind() == FieldKind.REF_USER) {
                listColumns.add(index);
            }
        }
        if (listColumns.isEmpty()) {
            return;
        }
        ReferenceContext refs = referenceDirectoryService.context(fields);
        XSSFSheet helper = workbook.createSheet(REFERENCE_SHEET);
        Row headerRow = helper.createRow(0);
        DataValidationHelper validator = helper.getDataValidationHelper();
        Sheet dataSheet = workbook.getSheetAt(0);
        int column = 0;
        for (int fieldIndex : listColumns) {
            ExcelField<?> field = fields.get(fieldIndex);
            List<String> options = options(field, refs);
            if (options.isEmpty()) {
                continue;
            }
            headerRow.createCell(column).setCellValue(field.getImportHeader() + REFERENCE_HINT);
            for (int offset = 0; offset < options.size(); offset++) {
                Row row = helper.getRow(offset + 1);
                if (row == null) {
                    row = helper.createRow(offset + 1);
                }
                row.createCell(column).setCellValue(options.get(offset));
            }
            String quoted = "'" + REFERENCE_SHEET + "'";
            String range = quoted + "!$" + columnLetter(column) + "$2:$"
                    + columnLetter(column) + "$" + (options.size() + 1);
            DataValidationConstraint constraint = validator.createFormulaListConstraint(range);
            DataValidation validation = validator.createValidation(
                    constraint, new CellRangeAddressList(1, MAX_DATA_ROW, fieldIndex, fieldIndex));
            validation.setEmptyCellAllowed(!field.isRequired());
            // 下拉只是引导，不拦手工输入：不弹错误框，但要**显示箭头**（否则用户根本看不到有下拉）
            validation.setShowErrorBox(false);
            // ⚠️ OOXML 的 showDropDown 语义是反的（true = 隐藏箭头）。POI 的 setSuppressDropDownArrow
            // 直接落到该属性上，所以这里必须传 true —— 传 false 会让模板"看起来有下拉其实箭头被隐藏"。
            validation.setSuppressDropDownArrow(true);
            if (field.isRequired()) {
                // POI 的 DataValidation 没有 setPromptTitle/setPrompt，只有 createPromptBox(title, text)
                validation.setShowPromptBox(true);
                validation.createPromptBox("必填列", "该列每一行都必须填写，留空会导致整批导入失败");
            }
            dataSheet.addValidationData(validation);
            helper.setColumnWidth(column, columnWidth(field.getImportHeader()) * 2);
            column++;
        }
        helper.protectSheet("");
        workbook.setSheetHidden(workbook.getSheetIndex(helper), true);
    }

    private List<String> options(ExcelField<?> field, ReferenceContext refs) {
        if (!field.getEnums().isEmpty()) {
            return field.getEnums().stream().map(EnumOption::label).toList();
        }
        if (field.getKind() == FieldKind.REF_DEPT && refs.depts() != null) {
            return refs.depts().pathById().entrySet().stream()
                    .filter(entry -> refs.depts().visible(entry.getKey()))
                    .filter(entry -> refs.depts().idsByPath()
                            .getOrDefault(entry.getValue(), List.of()).size() == 1)
                    .map(Map.Entry::getValue)
                    .distinct()
                    .sorted()
                    .toList();
        }
        if (field.getKind() == FieldKind.REF_USER && refs.users() != null) {
            return refs.users().selectableIds().stream()
                    .map(refs.users().displayById()::get)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private void writeGuideSheet(XSSFWorkbook workbook, ExcelSchema<?> schema,
                                 List<? extends ExcelField<?>> fields, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("填写须知");
        int rowIndex = 0;
        Row title = sheet.createRow(rowIndex++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue(schema.title() + " 填写须知");
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        List<String> rules = new ArrayList<>();
        rules.add("1. 只在「" + schema.dataSheetName() + "」页填写数据，首行为表头，请勿删除或改名。");
        rules.add("2. 表头带红色 * 的列为必填，每一行都要有值；有下拉箭头的列请优先从下拉里选。"
                + "整批校验通过后才会写入，任一行有错整批不落库。");
        rules.add("3. 单文件不超过 50MB，数据行不超过 20000 行；只支持 .xlsx。");
        rules.add("4. 「" + REFERENCE_SHEET + "」页是下拉数据来源，已隐藏并锁定，请勿修改；"
                + "下拉只是建议，允许手工填写，最终以导入时刻系统数据为准。");
        for (ExcelField<?> field : fields) {
            StringBuilder rule = new StringBuilder();
            rule.append(field.getImportHeader()).append("：").append(field.isRequired() ? "必填" : "选填");
            if (field.getMaxLength() > 0) {
                rule.append("，最多 ").append(field.getMaxLength()).append(" 字");
            }
            if (!field.getEnums().isEmpty()) {
                rule.append("，可选值：").append(field.enumText());
            }
            if (field.getFormatHint() != null && !field.getFormatHint().isBlank()) {
                rule.append("，").append(field.getFormatHint());
            }
            rules.add("   - " + rule);
        }
        fields.stream().map(ExcelField::getUniqueHint).filter(hint -> hint != null && !hint.isBlank())
                .findFirst().ifPresent(hint -> rules.add("   - 唯一性：" + hint));
        for (String rule : rules) {
            sheet.createRow(rowIndex++).createCell(0).setCellValue(rule);
        }

        rowIndex++;
        Row exampleTitle = sheet.createRow(rowIndex++);
        exampleTitle.createCell(0).setCellValue("示例数据（仅供参照，请勿直接粘贴到数据页）");
        exampleTitle.getCell(0).setCellStyle(titleStyle);
        Row exampleHeader = sheet.createRow(rowIndex++);
        Row exampleRow = sheet.createRow(rowIndex++);
        for (int index = 0; index < fields.size(); index++) {
            ExcelField<?> field = fields.get(index);
            Cell headerCell = exampleHeader.createCell(index);
            headerCell.setCellValue(field.getImportHeader());
            headerCell.setCellStyle(headerStyle);
            exampleRow.createCell(index).setCellValue(field.getExample() == null ? "" : field.getExample());
        }
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        for (int index = 2; index < fields.size(); index++) {
            sheet.setColumnWidth(index, 20 * 256);
        }

        for (TemplateSection section : schema.templateSections()) {
            rowIndex++;
            Row sectionTitle = sheet.createRow(rowIndex++);
            sectionTitle.createCell(0).setCellValue(section.title());
            sectionTitle.getCell(0).setCellStyle(titleStyle);
            Row sectionHeader = sheet.createRow(rowIndex++);
            for (int index = 0; index < section.headers().size(); index++) {
                Cell cell = sectionHeader.createCell(index);
                cell.setCellValue(section.headers().get(index));
                cell.setCellStyle(headerStyle);
            }
            for (List<String> values : section.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int index = 0; index < values.size(); index++) {
                    row.createCell(index).setCellValue(values.get(index));
                }
            }
        }
    }

    /** 表头富文本：可见文字 + 必填星号（红色加粗）。 */
    private XSSFRichTextString headerText(XSSFWorkbook workbook, ExcelField<?> field) {
        String header = field.getImportHeader();
        if (!field.isRequired()) {
            return new XSSFRichTextString(header);
        }
        Font base = workbook.createFont();
        base.setBold(true);
        Font required = workbook.createFont();
        required.setBold(true);
        required.setColor(IndexedColors.RED.getIndex());
        XSSFRichTextString text = new XSSFRichTextString(header + "*");
        text.applyFont(0, header.length(), base);
        text.applyFont(header.length(), header.length() + 1, required);
        return text;
    }

    /**
     * 表头批注：必填列先说"每行必填"，再给格式提示。
     * 必填信息不只写进「填写须知」页——用户填数据时看的是数据页，须在数据页就能看到。
     */
    private String headerComment(ExcelField<?> field) {
        List<String> parts = new ArrayList<>();
        if (field.isRequired()) {
            parts.add("【必填】每一行都要填写，不能留空");
        }
        if (field.getFormatHint() != null && !field.getFormatHint().isBlank()) {
            parts.add(field.getFormatHint());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return field.getImportHeader() + "：" + String.join("；", parts);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        return style;
    }

    private String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        int current = index;
        while (current >= 0) {
            letters.insert(0, (char) ('A' + current % 26));
            current = current / 26 - 1;
        }
        return letters.toString();
    }

    private int columnWidth(String header) {
        int width = Math.max(12, header.length() * 2 + 6);
        return Math.min(60, width) * 256;
    }
}
