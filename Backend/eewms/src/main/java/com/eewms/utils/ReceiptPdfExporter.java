package com.eewms.utils;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.util.StreamUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ReceiptPdfExporter {

    // ★ NEW: formatter thời gian rõ ràng
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static void export(WarehouseReceiptDTO dto, OutputStream os) throws Exception {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, os);
        doc.open();

        // Fonts Unicode từ classpath
        Font fTxt   = loadFont("fonts/DejaVuSans.ttf",      10f, Font.NORMAL);
        Font fLbl   = loadFont("fonts/DejaVuSans-Bold.ttf", 10f, Font.BOLD);
        Font fTitle = loadFont("fonts/DejaVuSans-Bold.ttf", 16f, Font.BOLD);
        Font fCompany = loadFont("fonts/DejaVuSans-Bold.ttf", 12f, Font.BOLD);

        Paragraph company = new Paragraph("CÔNG TY TNHH XD/TM THIẾT BỊ ĐIỆN HẢI PHÒNG", fCompany);
        company.setAlignment(Element.ALIGN_CENTER);   // hoặc ALIGN_LEFT nếu muốn
        company.setSpacingAfter(2f);
        doc.add(company);

        // Title
        Paragraph title = new Paragraph("PHIẾU NHẬP KHO", fTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8f);
        doc.add(title);



        // Info block
        PdfPTable info = new PdfPTable(new float[]{28, 72});
        info.setWidthPercentage(100);
        addInfo(info, "Mã phiếu:", nvl(dto.getCode()), fLbl, fTxt);
        addInfo(info, "Đơn mua (PO):", nvl(dto.getPurchaseOrderCode(), "—"), fLbl, fTxt);
        addInfo(info, "Người tạo:", nvl(dto.getCreatedByName()), fLbl, fTxt);
        // ★ CHANGED: định dạng thời gian dd/MM/yyyy HH:mm:ss
        addInfo(info, "Thời gian:", dto.getCreatedAt()!=null ? dto.getCreatedAt().format(DT_FMT) : "—", fLbl, fTxt);
        addInfo(info, "Ghi chú:", nvl(dto.getNote()), fLbl, fTxt);
        info.setSpacingAfter(8f);
        doc.add(info);

        // Items table
        PdfPTable tbl = new PdfPTable(new float[]{8, 62, 15, 15});
        tbl.setWidthPercentage(100);

        // ★ CHANGED: header theo từng căn lề cụ thể
        addHead(tbl, "Id", fLbl, Element.ALIGN_CENTER);
        addHead(tbl, "Tên sản phẩm", fLbl, Element.ALIGN_LEFT);
        addHead(tbl, "SL nhập", fLbl, Element.ALIGN_RIGHT);
        addHead(tbl, "Đơn giá", fLbl, Element.ALIGN_RIGHT);

        List<WarehouseReceiptItemDTO> items = dto.getItems() != null ? dto.getItems() : List.of();
        int stt = 1;
        BigDecimal total = BigDecimal.ZERO;

        for (var it : items) {
            int qty = it.getActualQuantity() != null ? it.getActualQuantity()
                    : (it.getQuantity() != null ? it.getQuantity() : 0);
            BigDecimal price = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;

            tbl.addCell(cell(String.valueOf(stt++), fTxt, Element.ALIGN_CENTER));
            tbl.addCell(cell(nvl(it.getProductName()), fTxt, Element.ALIGN_LEFT));   // ★ LEFT
            tbl.addCell(cell(String.valueOf(qty), fTxt, Element.ALIGN_RIGHT));       // ★ RIGHT
            // ★ CHANGED: đơn giá có VNĐ
            tbl.addCell(cell(formatMoney(price) + " VNĐ", fTxt, Element.ALIGN_RIGHT));

            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        tbl.setSpacingAfter(6f);
        doc.add(tbl);

        // Tổng cộng
        PdfPTable sum = new PdfPTable(new float[]{70, 30});
        sum.setWidthPercentage(100);
        sum.addCell(noBorder(cell("Tổng cộng:", fLbl, Element.ALIGN_RIGHT)));
        // ★ CHANGED: tổng cộng có VNĐ & đậm
        sum.addCell(noBorder(cell(formatMoney(total) + " VNĐ", fLbl, Element.ALIGN_RIGHT)));
        doc.add(sum);

        doc.close();
    }

    // ---------- helpers ----------
    private static void addInfo(PdfPTable t, String k, String v, Font fk, Font fv){
        PdfPCell c1 = new PdfPCell(new Phrase(k, fk));
        PdfPCell c2 = new PdfPCell(new Phrase(v, fv));
        c1.setBorder(Rectangle.NO_BORDER);
        c2.setBorder(Rectangle.NO_BORDER);
        c1.setPadding(3f); c2.setPadding(3f);                 // ★ padding để không “lệch”
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c1); t.addCell(c2);
    }

    private static void addHead(PdfPTable t, String txt, Font f, int align){
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);                                     // ★ đồng nhất padding
        t.addCell(c);
    }

    private static PdfPCell cell(String txt, Font f, int align){
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);                                     // ★ padding
        return c;
    }

    private static PdfPCell noBorder(PdfPCell c){ c.setBorder(Rectangle.NO_BORDER); return c; }

    private static String nvl(String s){ return s==null? "": s; }
    private static String nvl(String s, String d){ return (s==null||s.isBlank())? d: s; }

    // ★ CHANGED: format số theo vi-VN, không chữ VNĐ (để tái sử dụng), thêm “ VNĐ” ở nơi hiển thị
    private static String formatMoney(BigDecimal v){
        if (v==null) return "0";
        var nf = java.text.NumberFormat.getInstance(new Locale("vi","VN"));
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    private static Font loadFont(String cpPath, float size, int style) {
        try (var in = ReceiptPdfExporter.class.getClassLoader().getResourceAsStream(cpPath)) {
            if (in == null) throw new RuntimeException("Font not found: " + cpPath);
            byte[] bytes = StreamUtils.copyToByteArray(in);
            BaseFont bf = BaseFont.createFont(
                    cpPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    false, bytes, null
            );
            return new Font(bf, size, style);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, style);
        }
    }
}
