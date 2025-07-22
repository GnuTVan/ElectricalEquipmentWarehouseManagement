package com.eewms.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ExcelExporterUtil {

    public static InputStream exportWarehouseReceiptReport(
            List<String> headers,
            List<List<String>> rows,
            String reportTitle
    ) throws Exception {
        return exportSimpleReport(headers, rows, reportTitle, "BaoCaoNhapKho");
    }

    public static InputStream exportSimpleReport(
            List<String> headers,
            List<List<String>> rows,
            String reportTitle,
            String sheetName
    ) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        int rowIdx = 0;

        // Create styles
        CellStyle boldStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
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

        // 1. Header công ty
        Row r0 = sheet.createRow(rowIdx++);
        Cell c0 = r0.createCell(0);
        c0.setCellValue("Electric Equipment");
        c0.setCellStyle(boldStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));

        String[] companyInfo = new String[]{
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

        rowIdx++; // dòng trống

        // 2. Tiêu đề có border toàn vùng merge
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

        int titleColStart = 0;
        int titleColEnd = headers.size() - 1;
        for (int i = titleColStart; i <= titleColEnd; i++) {
            Cell cell = titleRow.createCell(i);
            if (i == titleColStart) {
                cell.setCellValue(reportTitle);
            }
            cell.setCellStyle(titleStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), titleColStart, titleColEnd));

        rowIdx++; // dòng trống

        // 3. Header bảng
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        // 4. Dữ liệu bảng
        for (List<String> rowData : rows) {
            Row dataRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < rowData.size(); i++) {
                Cell cell = dataRow.createCell(i);
                if (i == rowData.size() - 1) {
                    try {
                        double value = Double.parseDouble(rowData.get(i));
                        cell.setCellValue(value);
                        cell.setCellStyle(currencyStyle);
                    } catch (NumberFormatException e) {
                        cell.setCellValue(rowData.get(i));
                        cell.setCellStyle(borderStyle);
                    }
                } else {
                    cell.setCellValue(rowData.get(i));
                    cell.setCellStyle(borderStyle);
                }
            }
        }

        rowIdx += 2;

        // 5. Ký tên và ngày tháng
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

        // Auto size
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return new ByteArrayInputStream(out.toByteArray());
    }
}
