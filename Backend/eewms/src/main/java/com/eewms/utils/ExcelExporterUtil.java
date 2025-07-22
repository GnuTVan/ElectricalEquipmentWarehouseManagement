package com.eewms.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ExcelExporterUtil {

    public static InputStream exportReportWithInfo(
            String reportTitle,
            String sheetName,
            Map<String, String> infoBlock,
            List<String> headers,
            List<List<String>> rows,
            List<String> columnTypes
    ) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        int rowIdx = 0;

        // Styles
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        CellStyle boldStyle = workbook.createCellStyle();
        boldStyle.setFont(boldFont);

        CellStyle borderStyle = workbook.createCellStyle();
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        borderStyle.setAlignment(HorizontalAlignment.LEFT);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.cloneStyleFrom(borderStyle);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.cloneStyleFrom(borderStyle);
        DataFormat format = workbook.createDataFormat();
        currencyStyle.setDataFormat(format.getFormat("#,##0 ₫"));

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.cloneStyleFrom(borderStyle);
        numberStyle.setDataFormat(format.getFormat("#,##0"));

        // 1. Company Header
        Row companyRow = sheet.createRow(rowIdx++);
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellStyle(boldStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));

        String[] companyInfo = {
                "Công ty TNHH XD TM thiết bị điện Hải Phòng",
                "VPGD: 10A/46 phố Chợ Đồn, phường Nghĩa Xá, quận Lê Chân, Hải Phòng",
                "Mã số thuế: 0201624632",
                "Email: sale@thietbidienhp.com",
                "Website: thietbidienhp.com"
        };
        for (String line : companyInfo) {
            Row row = sheet.createRow(rowIdx++);
            Cell cell = row.createCell(0);
            cell.setCellValue(line);
            cell.setCellStyle(boldStyle);
        }

        rowIdx++;

        // 2. Report Title
        Row titleRow = sheet.createRow(rowIdx++);
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setBold(true);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setBorderTop(BorderStyle.THIN);
        titleStyle.setBorderBottom(BorderStyle.THIN);
        titleStyle.setBorderLeft(BorderStyle.THIN);
        titleStyle.setBorderRight(BorderStyle.THIN);

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = titleRow.createCell(i);
            if (i == 0) {
                cell.setCellValue(reportTitle);
            }
            cell.setCellStyle(titleStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, headers.size() - 1));

        // 3. Info Block
        if (infoBlock != null && !infoBlock.isEmpty()) {
            for (Map.Entry<String, String> entry : infoBlock.entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                Cell keyCell = row.createCell(0);
                Cell valCell = row.createCell(1);
                keyCell.setCellValue(entry.getKey());
                valCell.setCellValue(entry.getValue());
                keyCell.setCellStyle(boldStyle);
                valCell.setCellStyle(borderStyle);
            }
            rowIdx++;
        }

        // 4. Header Row
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        // 5. Data Rows
        for (List<String> rowData : rows) {
            Row dataRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < rowData.size(); i++) {
                Cell cell = dataRow.createCell(i);
                String type = columnTypes.get(i);
                String value = rowData.get(i);
                try {
                    switch (type) {
                        case "currency" -> {
                            cell.setCellValue(Double.parseDouble(value));
                            cell.setCellStyle(currencyStyle);
                        }
                        case "number" -> {
                            cell.setCellValue(Double.parseDouble(value));
                            cell.setCellStyle(numberStyle);
                        }
                        default -> {
                            cell.setCellValue(value);
                            cell.setCellStyle(borderStyle);
                        }
                    }
                } catch (Exception e) {
                    cell.setCellValue(value);
                    cell.setCellStyle(borderStyle);
                }
            }
        }

        rowIdx += 2;

        // 6. Signature
        Row dateRow = sheet.createRow(rowIdx++);
        Cell dateCell = dateRow.createCell(headers.size() - 3);
        dateCell.setCellValue("Hải Phòng, ngày    tháng    năm");
        dateCell.setCellStyle(boldStyle);
        sheet.addMergedRegion(new CellRangeAddress(dateRow.getRowNum(), dateRow.getRowNum(), headers.size() - 3, headers.size() - 1));

        Row signRow = sheet.createRow(rowIdx++);
        Cell signCell = signRow.createCell(headers.size() - 3);
        signCell.setCellValue("Giám đốc");
        CellStyle signStyle = workbook.createCellStyle();
        signStyle.setFont(boldFont);
        signStyle.setAlignment(HorizontalAlignment.CENTER);
        signCell.setCellStyle(signStyle);
        sheet.addMergedRegion(new CellRangeAddress(signRow.getRowNum(), signRow.getRowNum(), headers.size() - 3, headers.size() - 1));

        // Auto-size columns
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return new ByteArrayInputStream(out.toByteArray());
    }
}
