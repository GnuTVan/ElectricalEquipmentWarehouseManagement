package com.eewms.utils;

import com.eewms.dto.report.ReceiptReportRowDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReceiptReportExcelExporter {

    public static void export(List<ReceiptReportRowDTO> rows,
                              LocalDate fromDate, LocalDate toDate,
                              HttpServletResponse resp) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Bao cao nhap kho");
            int colCount = 7;                // 0..7
            int lastCol = colCount;

            // Styles
            Font bold = wb.createFont(); bold.setBold(true);
            CellStyle boldLeft = wb.createCellStyle(); boldLeft.setFont(bold);
            boldLeft.setVerticalAlignment(VerticalAlignment.TOP);

            CellStyle centerBold = wb.createCellStyle(); centerBold.setFont(bold);
            centerBold.setAlignment(HorizontalAlignment.CENTER);

            DataFormat fmt = wb.createDataFormat();
            CellStyle th = wb.createCellStyle();
            th.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            th.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            th.setAlignment(HorizontalAlignment.CENTER);
            th.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorderAll(th);

            CellStyle td = wb.createCellStyle();
            td.setVerticalAlignment(VerticalAlignment.TOP);
            setBorderAll(td);

            CellStyle tdInt = wb.createCellStyle();
            tdInt.cloneStyleFrom(td);
            tdInt.setDataFormat(fmt.getFormat("#,##0"));

            CellStyle tdMoney = wb.createCellStyle();
            tdMoney.cloneStyleFrom(td);
            tdMoney.setDataFormat(fmt.getFormat("#,##0")); // để số thuần; mở file sẽ hiển thị có tách ngàn

            // ROW INDEX pointer
            int r = 0;

            // ===== HEADER COMPANY INFO =====
            r = writeHeaderCompany(sh, r, lastCol, boldLeft);

            // Title
            Row title = sh.createRow(r++);
            Cell cTitle = title.createCell(0);
            cTitle.setCellValue("BÁO CÁO NHẬP KHO");
            cTitle.setCellStyle(centerBold);
            sh.addMergedRegion(new CellRangeAddress(title.getRowNum(), title.getRowNum(), 0, lastCol));

            // Filter line
            Row fline = sh.createRow(r++);
            Cell cf = fline.createCell(0);
            String fd = fromDate != null ? fromDate.toString() : "__/__/____";
            String tdStr = toDate != null ? toDate.toString() : "__/__/____";
            cf.setCellValue("Kỳ báo cáo: từ " + fd + " đến " + tdStr);
            sh.addMergedRegion(new CellRangeAddress(fline.getRowNum(), fline.getRowNum(), 0, lastCol));

            r++; // blank line

            // ===== TABLE HEADER =====
            Row h = sh.createRow(r++);
            String[] heads = {"STT", "Mã phiếu", "Ngày nhập", "Kho", "Nhà cung cấp", "Người tạo", "Số lượng", "Tổng tiền"};
            for (int i = 0; i <= lastCol; i++) {
                Cell cell = h.createCell(i);
                cell.setCellValue(heads[i]);
                cell.setCellStyle(th);
            }

            // ===== TABLE BODY =====
            int idx = 1;
            for (ReceiptReportRowDTO row : rows) {
                Row tr = sh.createRow(r++);
                // STT
                Cell c0 = tr.createCell(0); c0.setCellValue(idx++); c0.setCellStyle(tdInt);
                // Mã phiếu
                Cell c1 = tr.createCell(1); c1.setCellValue(nvl(row.getReceiptCode())); c1.setCellStyle(td);
                // Ngày nhập
                Cell c2 = tr.createCell(2); c2.setCellValue(row.getReceiptDate() != null ? row.getReceiptDate().toString() : ""); c2.setCellStyle(td);
                // Kho
                Cell c3 = tr.createCell(3); c3.setCellValue(nvl(row.getWarehouseName())); c3.setCellStyle(td);
                // NCC
                Cell c4 = tr.createCell(4); c4.setCellValue(nvl(row.getSupplierName())); c4.setCellStyle(td);
                // Người tạo
                Cell c5 = tr.createCell(5); c5.setCellValue(nvl(row.getCreatedByName())); c5.setCellStyle(td);
                // SL
                Cell c6 = tr.createCell(6); c6.setCellValue(safeInt(row.getTotalQuantity())); c6.setCellStyle(tdInt);
                // Tổng tiền
                Cell c7 = tr.createCell(7); c7.setCellValue(safeDouble(row.getTotalAmount())); c7.setCellStyle(tdMoney);
            }

            r++; // blank line

            // ===== FOOTER (right) =====
            Row f1 = sh.createRow(r++);
            Row f2 = sh.createRow(r++);
            int colRight = Math.max(4, lastCol - 2); // kê bên phải
            LocalDateTime now = LocalDateTime.now();
            String dateStr = "Hải Phòng, ngày " + pad(now.getDayOfMonth()) + "  tháng " + pad(now.getMonthValue()) + "  năm " + now.getYear();

            Cell cf1 = f1.createCell(colRight);
            cf1.setCellValue(dateStr);
            cf1.setCellStyle(centerBold);
            sh.addMergedRegion(new CellRangeAddress(f1.getRowNum(), f1.getRowNum(), colRight, lastCol));

            Cell cf2 = f2.createCell(colRight);
            cf2.setCellValue("Giám đốc");
            cf2.setCellStyle(centerBold);
            sh.addMergedRegion(new CellRangeAddress(f2.getRowNum(), f2.getRowNum(), colRight, lastCol));

            // Widths, freeze
            for (int i = 0; i <= lastCol; i++) sh.autoSizeColumn(i);
            sh.createFreezePane(0, h.getRowNum()+1);

            // Write response
            String fileName = "bao-cao-nhap-kho-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx";
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            try (OutputStream os = resp.getOutputStream()) {
                wb.write(os);
                os.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException("Export Excel lỗi: " + e.getMessage(), e);
        }
    }

    private static int writeHeaderCompany(Sheet sh, int r, int lastCol, CellStyle style) {
        String[] lines = {
                "Công ty TNHH XD TM thiết bị điện Hải Phòng",
                "VPGD: 10A/46 phố Chợ Đồn, phường Nghĩa Xá, quận Lê Chân, Hải Phòng",
                "Mã số thuế: 0201624632",
                "Email: sale@thietbidienhp.com",
                "Website: thietbidienhp.com"
        };
        for (String s : lines) {
            Row row = sh.createRow(r++);
            Cell c = row.createCell(0);
            c.setCellValue(s);
            c.setCellStyle(style);
            sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, lastCol));
        }
        r++; // blank line
        return r;
    }

    private static void setBorderAll(CellStyle st) {
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    private static int safeInt(Integer i) { return i == null ? 0 : i; }
    private static double safeDouble(BigDecimal b) { return b == null ? 0d : b.doubleValue(); }
    private static String pad(int v){ return (v < 10 ? "0" : "") + v; }
}
