package com.eewms.controller;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDebtService;
import com.eewms.services.IWarehouseReceiptService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.awt.Color;
import java.io.File;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/warehouse-receipts")
@RequiredArgsConstructor
public class WarehouseReceiptController {

    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final IWarehouseReceiptService warehouseReceiptService;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;

    // Công nợ
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final IDebtService debtService;

    @GetMapping
    public String listReceipts(Model model) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAll();

        // Map receiptId -> có công nợ?
        Map<Long, Boolean> hasDebt = receipts.stream()
                .collect(Collectors.toMap(
                        WarehouseReceipt::getId,
                        r -> debtRepository.existsByDocumentTypeAndDocumentId(
                                Debt.DocumentType.WAREHOUSE_RECEIPT, r.getId())
                ));

        model.addAttribute("receipts", receipts);
        model.addAttribute("hasDebt", hasDebt);
        return "warehouse/warehouse-receipt-list";
    }

    @GetMapping("/form")
    public String showCreateForm(@RequestParam("purchaseOrderId") Long purchaseOrderId,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (warehouseReceiptRepository.existsByPurchaseOrder(order)) {
            redirectAttributes.addFlashAttribute("message", "Đơn hàng này đã được nhập kho!");
            redirectAttributes.addFlashAttribute("messageType", "warning");
            return "redirect:/admin/purchase-orders";
        }

        List<PurchaseOrderItem> orderItems = purchaseOrderItemRepository.findByPurchaseOrderId(purchaseOrderId);
        List<Warehouse> warehouses = warehouseRepository.findAll();

        model.addAttribute("purchaseOrder", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("warehouseReceiptDTO", new WarehouseReceiptDTO());
        return "warehouse/warehouse-receipt-form";
    }

    @PostMapping("/save")
    public String saveReceipt(@ModelAttribute WarehouseReceiptDTO dto,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        PurchaseOrder order = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedByName(user.getFullName());

        warehouseReceiptService.saveReceipt(dto, order, user);

        redirectAttributes.addFlashAttribute("message", "Tạo phiếu nhập kho thành công!");
        redirectAttributes.addFlashAttribute("messageType", "success");
        return "redirect:/admin/warehouse-receipts";
    }

    @GetMapping("/view/{id}")
    public String viewReceipt(@PathVariable Long id, Model model) {
        WarehouseReceipt receipt = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));

        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);
        model.addAttribute("receipt", receipt);
        model.addAttribute("items", items);

        // Công nợ + lịch sử thanh toán
        debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, id).ifPresent(d -> {
            model.addAttribute("debt", d);
            model.addAttribute("payments", debtPaymentRepository.findByDebt(d));
        });

        return "warehouse/warehouse-receipt-view";
    }

    /** In phiếu PDF (dùng font hệ điều hành, không cần thêm font vào project) */
    @GetMapping("/export/{id}")
    public void exportReceiptToPDF(@PathVariable Long id, HttpServletResponse response) throws Exception {
        WarehouseReceipt receipt = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));
        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);
        PurchaseOrder order = receipt.getPurchaseOrder();
        Supplier supplier = (order != null) ? order.getSupplier() : null;

        // === Trạng thái thanh toán & số liệu công nợ ===
        var optDebt = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, id);
        String paymentStatus = "Chưa thanh toán";
        if (optDebt.isPresent()) {
            Debt.Status st = optDebt.get().getStatus();
            switch (st) {
                case PAID -> paymentStatus = "Đã thanh toán";
                case PARTIAL -> paymentStatus = "Thanh toán một phần";
                case UNPAID -> paymentStatus = "Chưa thanh toán";
                case OVERDUE -> paymentStatus = "Chưa thanh toán (Quá hạn)";
            }
        }

        response.setContentType("application/pdf");
        String fileName = URLEncoder.encode("phieu-nhap-" + receipt.getCode() + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");

        try (OutputStream os = response.getOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, os);
            doc.open();

            // Font hệ điều hành
            BaseFont bf = loadSystemVnFont();
            BaseFont bfBold = loadSystemVnBoldFontFallback(bf);
            Font H1 = new Font(bfBold, 16);
            Font H2 = new Font(bfBold, 12);
            Font N  = new Font(bf, 10);
            Font NB = new Font(bfBold, 11);

            // Header
            Paragraph company = new Paragraph("Công Ty Thiết Bị Điện Hải Phòng", H2);
            company.setAlignment(Element.ALIGN_LEFT);
            doc.add(company);

            Paragraph title = new Paragraph("PHIẾU NHẬP KHO", H1);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(6);
            title.setSpacingAfter(10);
            doc.add(title);

            // Thông tin chung
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            info.setWidths(new float[]{1.2f, 2.0f});
            info.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            info.addCell(cellLabel("Mã phiếu:", H2));
            info.addCell(cellText(receipt.getCode(), N));
            info.addCell(cellLabel("Đơn hàng:", H2));
            info.addCell(cellText(order != null ? order.getCode() : "", N));
            info.addCell(cellLabel("Kho:", H2));
            info.addCell(cellText(receipt.getWarehouse().getName(), N));
            info.addCell(cellLabel("Ngày tạo:", H2));
            info.addCell(cellText(fmtDateTime(receipt.getCreatedAt()), N));
            info.addCell(cellLabel("Người tạo:", H2));
            info.addCell(cellText(receipt.getCreatedBy(), N));
            info.addCell(cellLabel("Trạng thái TT:", H2));
            info.addCell(cellText(paymentStatus, N));
            info.addCell(cellLabel("Ghi chú:", H2));
            info.addCell(cellText(receipt.getNote(), N));

            info.setSpacingAfter(10);
            doc.add(info);

            // Nhà cung cấp
            PdfPTable sup = new PdfPTable(2);
            sup.setWidthPercentage(100);
            sup.setWidths(new float[]{1.2f, 2.0f});
            sup.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            sup.addCell(cellLabel("Nhà cung cấp:", H2));
            sup.addCell(cellText(supplier != null ? supplier.getName() : "", N));
            sup.setSpacingAfter(8);
            doc.add(sup);

            // Bảng sản phẩm
            PdfPTable tbl = new PdfPTable(5);
            tbl.setWidthPercentage(100);
            tbl.setWidths(new float[]{0.8f, 3.0f, 1.0f, 1.2f, 1.4f});

            tbl.addCell(headerCell("STT", bfBold));
            tbl.addCell(headerCell("Sản phẩm", bfBold));
            tbl.addCell(headerCell("Số lượng", bfBold));
            tbl.addCell(headerCell("Đơn giá", bfBold));
            tbl.addCell(headerCell("Thành tiền", bfBold));

            BigDecimal itemsSum = BigDecimal.ZERO;
            int idx = 1;
            for (WarehouseReceiptItem it : items) {
                BigDecimal qty = BigDecimal.valueOf(
                        it.getActualQuantity() != null ? it.getActualQuantity()
                                : (it.getQuantity() != null ? it.getQuantity() : 0)
                );
                BigDecimal unit = it.getPrice() != null ? it.getPrice()
                        : (it.getProduct() != null && it.getProduct().getOriginPrice() != null
                        ? it.getProduct().getOriginPrice() : BigDecimal.ZERO);
                BigDecimal line = unit.multiply(qty);
                itemsSum = itemsSum.add(line);

                tbl.addCell(bodyCell(String.valueOf(idx++), N, Element.ALIGN_CENTER));
                tbl.addCell(bodyCell(it.getProduct() != null ? it.getProduct().getName() : "", N, Element.ALIGN_LEFT));
                tbl.addCell(bodyCell(fmtInt(qty), N, Element.ALIGN_RIGHT));
                tbl.addCell(bodyCell(fmtMoney(unit), N, Element.ALIGN_RIGHT));
                tbl.addCell(bodyCell(fmtMoney(line), N, Element.ALIGN_RIGHT));
            }

            // ---- Tổng / Đã trả / Còn lại ----
            BigDecimal grand = optDebt.map(Debt::getTotalAmount).orElse(itemsSum);
            BigDecimal paid = optDebt.map(d -> {
                BigDecimal p = d.getPaidAmount();
                if (p != null) return p;
                return debtPaymentRepository.findByDebt(d).stream()
                        .map(DebtPayment::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }).orElse(BigDecimal.ZERO);

            if (paid.compareTo(BigDecimal.ZERO) < 0) paid = BigDecimal.ZERO;
            if (paid.compareTo(grand) > 0) paid = grand;
            BigDecimal remain = grand.subtract(paid);
            if (remain.compareTo(BigDecimal.ZERO) < 0) remain = BigDecimal.ZERO;

            // Tổng cộng
            PdfPCell totalLabel = new PdfPCell(new Phrase("Tổng cộng", NB));
            totalLabel.setColspan(4);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setPadding(6);
            tbl.addCell(totalLabel);

            PdfPCell totalVal = new PdfPCell(new Phrase(fmtMoney(grand), NB));
            totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalVal.setPadding(6);
            tbl.addCell(totalVal);

            // Đã trả
            PdfPCell paidLabel = new PdfPCell(new Phrase("Đã trả", NB));
            paidLabel.setColspan(4);
            paidLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidLabel.setPadding(6);
            tbl.addCell(paidLabel);

            PdfPCell paidVal = new PdfPCell(new Phrase(fmtMoney(paid), NB));
            paidVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidVal.setPadding(6);
            tbl.addCell(paidVal);

            // Còn lại
            PdfPCell remainLabel = new PdfPCell(new Phrase("Còn lại", NB));
            remainLabel.setColspan(4);
            remainLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            remainLabel.setPadding(6);
            tbl.addCell(remainLabel);

            PdfPCell remainVal = new PdfPCell(new Phrase(fmtMoney(remain), NB));
            remainVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            remainVal.setPadding(6);
            tbl.addCell(remainVal);

            tbl.setSpacingBefore(8);
            doc.add(tbl);

            // Chữ ký
            Paragraph sign = new Paragraph(
                    "Người lập phiếu __________    Thủ kho __________    Kế toán __________", N);
            sign.setSpacingBefore(14);
            doc.add(sign);

            doc.close();
        }
    }

    /** Xác nhận phiếu nhập + tạo công nợ (hạn 0/7/10 ngày) */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(defaultValue = "7") int termDays,
                          RedirectAttributes ra) {

        warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));

        debtService.createDebtForReceipt(id, termDays);

        ra.addFlashAttribute("message",
                "Đã xác nhận nhập kho và tạo công nợ (hạn " + termDays + " ngày).");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/warehouse-receipts";
    }

    /* ============================ Helpers ============================ */

    // Font hệ điều hành (không cần resources)
    private BaseFont loadSystemVnFont() throws Exception {
        List<String> candidates = Arrays.asList(
                // Windows
                "C:/Windows/Fonts/arial.ttf",
                "C:/Windows/Fonts/tahoma.ttf",
                "C:/Windows/Fonts/times.ttf",
                "C:/Windows/Fonts/timesnewroman.ttf",
                // Linux
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                // macOS
                "/Library/Fonts/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf"
        );
        for (String p : candidates) {
            if (new File(p).exists()) { // <-- đã sửa bỏ dấu ')' thừa
                return BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        // Fallback: không hỗ trợ tiếng Việt đầy đủ
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private BaseFont loadSystemVnBoldFontFallback(BaseFont normal) throws Exception {
        List<String> candidates = Arrays.asList(
                "C:/Windows/Fonts/arialbd.ttf",
                "C:/Windows/Fonts/tahomabd.ttf",
                "C:/Windows/Fonts/timesbd.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
                "/Library/Fonts/Arial Bold.ttf",
                "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
        );
        for (String p : candidates) {
            if (new File(p).exists()) {
                return BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        return normal;
    }

    private PdfPCell headerCell(String text, BaseFont bf) {
        Font f = new Font(bf, 11);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBackgroundColor(new Color(242, 242, 242));
        c.setPadding(6);
        return c;
    }

    private PdfPCell bodyCell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        return c;
    }

    private PdfPCell cellLabel(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(3);
        return c;
    }

    private PdfPCell cellText(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(3);
        return c;
    }

    private String fmtDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String fmtMoney(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    private String fmtInt(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }
}
