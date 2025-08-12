package com.eewms.utils;

import com.eewms.dto.report.IssueReportRowDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class IssueReportExcelExporter {

    public static void export(List<IssueReportRowDTO> rows,
                              java.time.LocalDate fromDate, java.time.LocalDate toDate,
                              HttpServletResponse resp) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Bao cao xuat kho");
            int lastCol = 7; // 0..7 = 8 cột

            // Fonts & styles
            Font bold = wb.createFont(); bold.setBold(true);
            CellStyle boldLeft = wb.createCellStyle(); boldLeft.setFont(bold);
            CellStyle centerBold = wb.createCellStyle(); centerBold.setFont(bold); centerBold.setAlignment(HorizontalAlignment.CENTER);

            DataFormat fmt = wb.createDataFormat();
            CellStyle th = wb.createCellStyle();
            th.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            th.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            th.setAlignment(HorizontalAlignment.CENTER);
            th.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorderAll(th);

            CellStyle td = wb.createCellStyle(); td.setVerticalAlignment(VerticalAlignment.TOP); setBorderAll(td);
            CellStyle tdInt = wb.createCellStyle(); tdInt.cloneStyleFrom(td); tdInt.setDataFormat(fmt.getFormat("#,##0"));
            CellStyle tdMoney = wb.createCellStyle(); tdMoney.cloneStyleFrom(td); tdMoney.setDataFormat(fmt.getFormat("#,##0"));

            int r = 0;

            // Header công ty
            r = writeCompanyHeader(sh, r, lastCol, boldLeft);

            // Title
            Row title = sh.createRow(r++);
            Cell cTitle = title.createCell(0);
            cTitle.setCellValue("BÁO CÁO XUẤT KHO");
            cTitle.setCellStyle(centerBold);
            sh.addMergedRegion(new CellRangeAddress(title.getRowNum(), title.getRowNum(), 0, lastCol));

            // Kỳ báo cáo
            Row fl = sh.createRow(r++);
            Cell cfl = fl.createCell(0);
            String fd = fromDate != null ? fromDate.toString() : "__/__/____";
            String tdStr = toDate != null ? toDate.toString() : "__/__/____";
            cfl.setCellValue("Kỳ báo cáo: từ " + fd + " đến " + tdStr);
            sh.addMergedRegion(new CellRangeAddress(fl.getRowNum(), fl.getRowNum(), 0, lastCol));

            r++; // dòng trống

            // Header bảng
            Row h = sh.createRow(r++);
            String[] heads = {"STT", "Mã PXK", "Ngày xuất", "Khách hàng", "Người tạo", "Mã ĐH", "Số lượng", "Tổng tiền"};
            for (int i = 0; i <= lastCol; i++) {
                Cell cell = h.createCell(i);
                cell.setCellValue(heads[i]);
                cell.setCellStyle(th);
            }

            // Body
            int stt = 1;
            for (IssueReportRowDTO row : rows) {
                Row tr = sh.createRow(r++);

                putNumber(tr, 0, stt++, tdInt); // STT (SỬA DÒNG NÀY)

                put(tr, 1, nvl(row.getIssueCode()), td);
                put(tr, 2, row.getIssueDate() != null ? row.getIssueDate().toString() : "", td);
                put(tr, 3, nvl(row.getCustomerName()), td);
                put(tr, 4, nvl(row.getCreatedByName()), td);
                put(tr, 5, nvl(row.getSaleOrderCode()), td);
                putNumber(tr, 6, row.getTotalQuantity() != null ? row.getTotalQuantity() : 0, tdInt);
                putNumber(tr, 7, row.getTotalAmount() != null ? row.getTotalAmount().doubleValue() : 0d, tdMoney);
            }

            r++;

            // Footer phải: ngày/giám đốc
            int colRight = Math.max(4, lastCol - 2);
            Row f1 = sh.createRow(r++), f2 = sh.createRow(r++);
            String dateStr = "Hải Phòng, ngày " + pad(LocalDateTime.now().getDayOfMonth())
                    + "  tháng " + pad(LocalDateTime.now().getMonthValue())
                    + "  năm " + LocalDateTime.now().getYear();
            mergeSet(sh, f1, colRight, lastCol, dateStr, centerBold);
            mergeSet(sh, f2, colRight, lastCol, "Giám đốc", centerBold);

            for (int i = 0; i <= lastCol; i++) sh.autoSizeColumn(i);
            sh.createFreezePane(0, h.getRowNum()+1);

            String fileName = "bao-cao-xuat-kho-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx";
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            try (OutputStream os = resp.getOutputStream()) { wb.write(os); os.flush(); }
        } catch (Exception e) {
            throw new RuntimeException("Export Excel PXK lỗi: " + e.getMessage(), e);
        }
    }

    // ===== helpers =====
    private static void put(Row r, int c, String v, CellStyle st){ Cell cell=r.createCell(c); cell.setCellValue(v); cell.setCellStyle(st); }
    private static void putNumber(Row r, int c, double v, CellStyle st){ Cell cell=r.createCell(c); cell.setCellValue(v); cell.setCellStyle(st); }

    private static int writeCompanyHeader(Sheet sh, int r, int lastCol, CellStyle st) {
        String[] lines = {
                "Công ty TNHH XD TM thiết bị điện Hải Phòng",
                "VPGD: 10A/46 phố Chợ Đồn, phường Nghĩa Xá, quận Lê Chân, Hải Phòng",
                "Mã số thuế: 0201624632",
                "Email: sale@thietbidienhp.com",
                "Website: thietbidienhp.com"
        };
        for (String s: lines) {
            Row row = sh.createRow(r++);
            Cell c = row.createCell(0); c.setCellValue(s); c.setCellStyle(st);
            sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, lastCol));
        }
        r++; return r;
    }
    private static void mergeSet(Sheet sh, Row r, int c1, int c2, String text, CellStyle st){
        Cell c = r.createCell(c1); c.setCellValue(text); c.setCellStyle(st);
        sh.addMergedRegion(new CellRangeAddress(r.getRowNum(), r.getRowNum(), c1, c2));
    }
    private static void setBorderAll(CellStyle st){
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
    }
    private static String nvl(String s){ return s == null ? "" : s; }
    private static String pad(int v){ return (v<10 ? "0" : "") + v; }
}
