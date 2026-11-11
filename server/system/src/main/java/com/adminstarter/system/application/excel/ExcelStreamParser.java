package com.adminstarter.system.application.excel;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件流解析器：用 {@link XSSFReader} + SAX 逐行读取，不把整个工作簿载入内存。
 * <p>只保留轻量文本行（行号 + 表头 → 单元格文本），供整批校验后单事务写入。</p>
 */
@Component
public class ExcelStreamParser {

    public ParseResult parse(InputStream input, ExcelSchema<?> schema, int maxRows) {
        try (OPCPackage pkg = OPCPackage.open(input)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            InputStream sheet = openSheet(reader, schema.dataSheetName());
            SheetHandler handler = new SheetHandler(sharedStrings, maxRows);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(handler);
            xmlReader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            try (sheet) {
                xmlReader.parse(new InputSource(sheet));
            }
            if (handler.header == null) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "数据页缺少表头，请使用下载的模板填写");
            }
            return new ParseResult(handler.header, handler.rows);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件无法解析，请确认是有效的 .xlsx 模板");
        }
    }

    private InputStream openSheet(XSSFReader reader, String sheetName) throws Exception {
        Iterator<InputStream> sheets = reader.getSheetsData();
        while (sheets.hasNext()) {
            InputStream candidate = sheets.next();
            if (sheets instanceof XSSFReader.SheetIterator iterator
                    && sheetName.equals(iterator.getSheetName())) {
                return candidate;
            }
            candidate.close();
        }
        throw new BizException(ErrorCode.INVALID_PARAMETER, "缺少「" + sheetName + "」数据页，请使用下载的模板");
    }

    public record ParseResult(List<String> header, List<RawRow> rows) {
    }

    private static final class SheetHandler extends DefaultHandler {

        private final SharedStrings sharedStrings;
        private final int maxRows;
        private final List<RawRow> rows = new ArrayList<>();

        private List<String> header;
        private int rowNumber;
        private Map<Integer, String> currentRow;
        private String cellRef;
        private String cellType;
        private boolean inCell;
        private boolean inValue;
        private boolean inInlineString;
        private final StringBuilder text = new StringBuilder();
        private final Map<Integer, String> cellValues = new LinkedHashMap<>();

        private SheetHandler(SharedStrings sharedStrings, int maxRows) {
            this.sharedStrings = sharedStrings;
            this.maxRows = maxRows;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (qName) {
                case "row" -> {
                    rowNumber = parseInt(attributes.getValue("r"), rowNumber + 1);
                    currentRow = new LinkedHashMap<>();
                    cellValues.clear();
                }
                case "c" -> {
                    cellRef = attributes.getValue("r");
                    cellType = attributes.getValue("t");
                    inCell = true;
                    text.setLength(0);
                }
                case "v" -> {
                    inValue = true;
                    text.setLength(0);
                }
                case "is" -> {
                    inInlineString = true;
                    text.setLength(0);
                }
                case "t" -> {
                    if (inInlineString) {
                        text.setLength(0);
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue || inInlineString) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case "v" -> {
                    if (inCell) {
                        cellValues.put(columnIndex(cellRef), resolve(cellType, text.toString()));
                    }
                    inValue = false;
                }
                case "t" -> {
                    if (inInlineString) {
                        cellValues.put(columnIndex(cellRef), text.toString());
                    }
                }
                case "is" -> inInlineString = false;
                case "c" -> {
                    if (!cellValues.containsKey(columnIndex(cellRef)) && text.length() > 0) {
                        cellValues.put(columnIndex(cellRef), text.toString());
                    }
                    inCell = false;
                    text.setLength(0);
                }
                case "row" -> finishRow();
                default -> {
                }
            }
        }

        private void finishRow() {
            if (header == null) {
                header = buildHeader();
                return;
            }
            if (cellValues.isEmpty()) {
                return;
            }
            Map<String, String> cells = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> entry : cellValues.entrySet()) {
                if (entry.getKey() < header.size() && header.get(entry.getKey()) != null) {
                    cells.put(header.get(entry.getKey()), entry.getValue());
                }
            }
            if (cells.values().stream().allMatch(value -> value == null || value.isBlank())) {
                return;
            }
            if (rows.size() >= maxRows) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "数据行数超过上限 " + maxRows + " 行，请缩小文件后重试");
            }
            rows.add(new RawRow(rowNumber, cells));
        }

        private List<String> buildHeader() {
            int size = cellValues.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            List<String> values = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                String value = cellValues.get(index);
                String normalized = value == null ? "" : value.trim();
                if (normalized.endsWith("*")) {
                    normalized = normalized.substring(0, normalized.length() - 1).trim();
                }
                values.add(normalized);
            }
            while (!values.isEmpty() && values.get(values.size() - 1).isEmpty()) {
                values.remove(values.size() - 1);
            }
            return values;
        }

        private String resolve(String type, String raw) {
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            if ("s".equals(type)) {
                int index = parseInt(raw, -1);
                return index >= 0 ? sharedStrings.getItemAt(index).getString() : "";
            }
            return raw;
        }

        private static int columnIndex(String reference) {
            if (reference == null || reference.isEmpty()) {
                return 0;
            }
            int column = 0;
            for (int index = 0; index < reference.length(); index++) {
                char ch = reference.charAt(index);
                if (ch < 'A' || ch > 'Z') {
                    break;
                }
                column = column * 26 + (ch - 'A' + 1);
            }
            return Math.max(0, column - 1);
        }

        private static int parseInt(String value, int fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}
