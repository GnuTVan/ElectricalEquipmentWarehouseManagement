package com.eewms.utils;

import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.dto.GoodIssueDetailDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.util.StreamUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class GoodIssuePdfExporter {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static void export(GoodIssueNoteDTO dto, OutputStream os) throws Exception {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, os);
        doc.open();

        // Fonts Unicode
        Font fTxt     = loadFont("fonts/DejaVuSans.ttf",      10f, Font.NORMAL);
        Font fLbl     = loadFont("fonts/DejaVuSans-Bold.ttf", 10f, Font.BOLD);
        Font fTitle   = loadFont("fonts/DejaVuSans-Bold.ttf", 16f, Font.BOLD);
        Font fCompany = loadFont("fonts/DejaVuSans-Bold.ttf", 12f, Font.BOLD);

        // Company
        Paragraph company = new Paragraph("CÔNG TY TNHH XD/TM THIẾT BỊ ĐIỆN HẢI PHÒNG", fCompany);
        company.setAlignment(Element.ALIGN_CENTER);
        company.setSpacingAfter(2f);
        doc.add(company);

        // Title
        Paragraph title = new Paragraph("PHIẾU XUẤT KHO", fTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8f);
        doc.add(title);

        // Info block
        PdfPTable info = new PdfPTable(new float[]{28, 72});
        info.setWidthPercentage(100);
        addInfo(info, "Mã phiếu:", nvl(dto.getCode()), fLbl, fTxt);
        addInfo(info, "Đơn bán (SO):", nvl(dto.getSaleOrderCode()), fLbl, fTxt);
        addInfo(info, "Khách hàng:", nvl(dto.getCustomerName()), fLbl, fTxt);
        addInfo(info, "Người tạo:", nvl(dto.getCreatedBy()), fLbl, fTxt);
        addInfo(info, "Thời gian:", dto.getIssueDate()!=null ? dto.getIssueDate().format(DT_FMT) : "—", fLbl, fTxt);
        addInfo(info, "Ghi chú:", nvl(dto.getDescription()), fLbl, fTxt);
        info.setSpacingAfter(8f);
        doc.add(info);

        // Table: STT, Tên SP, SL xuất, Đơn giá, Thành tiền
        PdfPTable tbl = new PdfPTable(new float[]{8, 52, 15, 12, 13});
        tbl.setWidthPercentage(100);
        addHead(tbl, "STT", fLbl, Element.ALIGN_CENTER);
        addHead(tbl, "Tên sản phẩm", fLbl, Element.ALIGN_LEFT);
        addHead(tbl, "SL xuất", fLbl, Element.ALIGN_RIGHT);
        addHead(tbl, "Đơn giá", fLbl, Element.ALIGN_RIGHT);
        addHead(tbl, "Thành tiền", fLbl, Element.ALIGN_RIGHT);

        List<GoodIssueDetailDTO> items = dto.getDetails() != null ? dto.getDetails() : List.of();
        int stt = 1;
        BigDecimal total = BigDecimal.ZERO;

        for (var it : items) {
            int qty = it.getQuantity() != null ? it.getQuantity() : 0;
            BigDecimal price = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;
            BigDecimal line = price.multiply(BigDecimal.valueOf(qty));

            tbl.addCell(cell(String.valueOf(stt++), fTxt, Element.ALIGN_CENTER));
            tbl.addCell(cell(nvl(it.getProductName()), fTxt, Element.ALIGN_LEFT));
            tbl.addCell(cell(String.valueOf(qty), fTxt, Element.ALIGN_RIGHT));
            tbl.addCell(cell(formatMoney(price) + " VNĐ", fTxt, Element.ALIGN_RIGHT));
            tbl.addCell(cell(formatMoney(line) + " VNĐ", fTxt, Element.ALIGN_RIGHT));

            total = total.add(line);
        }
        tbl.setSpacingAfter(6f);
        doc.add(tbl);

        // Tổng cộng
        PdfPTable sum = new PdfPTable(new float[]{70, 30});
        sum.setWidthPercentage(100);
        sum.addCell(noBorder(cell("Tổng cộng:", fLbl, Element.ALIGN_RIGHT)));
        sum.addCell(noBorder(cell(formatMoney(total) + " VNĐ", fLbl, Element.ALIGN_RIGHT)));
        doc.add(sum);

        doc.close();
    }

    // helpers
    private static void addInfo(PdfPTable t, String k, String v, Font fk, Font fv){
        PdfPCell c1 = new PdfPCell(new Phrase(k, fk));
        PdfPCell c2 = new PdfPCell(new Phrase(v, fv));
        c1.setBorder(Rectangle.NO_BORDER);
        c2.setBorder(Rectangle.NO_BORDER);
        c1.setPadding(3f); c2.setPadding(3f);
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c1); t.addCell(c2);
    }
    private static void addHead(PdfPTable t, String txt, Font f, int align){
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);
        t.addCell(c);
    }
    private static PdfPCell cell(String txt, Font f, int align){
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);
        return c;
    }
    private static PdfPCell noBorder(PdfPCell c){ c.setBorder(Rectangle.NO_BORDER); return c; }
    private static String nvl(String s){ return s==null? "" : s; }

    private static String formatMoney(BigDecimal v){
        if (v==null) return "0";
        var nf = java.text.NumberFormat.getInstance(new Locale("vi","VN"));
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    private static Font loadFont(String cpPath, float size, int style) {
        try (var in = GoodIssuePdfExporter.class.getClassLoader().getResourceAsStream(cpPath)) {
            if (in == null) throw new RuntimeException("Font not found: " + cpPath);
            byte[] bytes = StreamUtils.copyToByteArray(in);
            BaseFont bf = BaseFont.createFont(cpPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null);
            return new Font(bf, size, style);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, style);
        }
    }
}
