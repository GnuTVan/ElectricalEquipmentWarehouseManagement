package com.eewms.controller;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.*;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IWarehouseReceiptService;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.util.List;

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

    @GetMapping
    public String listReceipts(Model model) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAll();
        model.addAttribute("receipts", receipts);
        return "warehouse-receipt-list";
    }

    @GetMapping("/form")
    public String showCreateForm(@RequestParam("purchaseOrderId") Long purchaseOrderId, Model model,RedirectAttributes redirectAttributes) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (warehouseReceiptRepository.existsByPurchaseOrder(order)) {
            redirectAttributes.addFlashAttribute("error", "Đơn hàng này đã được nhập kho!");
            return "redirect:/admin/purchase-orders";
        }

        List<PurchaseOrderItem> orderItems = purchaseOrderItemRepository.findByPurchaseOrderId(purchaseOrderId);
        List<Warehouse> warehouses = warehouseRepository.findAll();

        model.addAttribute("purchaseOrder", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("warehouseReceiptDTO", new WarehouseReceiptDTO());

        return "warehouse-receipt-form"; // trỏ đến templates/warehouse-receipt-form.html
    }

    @PostMapping("/save")
    public String saveReceipt(@ModelAttribute WarehouseReceiptDTO dto,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {

        // Lấy user hiện tại
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Lấy đơn hàng
        PurchaseOrder order = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Gắn người tạo + thời gian
        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedByName(user.getFullName());

        // Gọi service để lưu
        warehouseReceiptService.saveReceipt(dto, order, user);

        // Chuyển hướng + thông báo
        redirectAttributes.addFlashAttribute("message", "Tạo phiếu nhập kho thành công!");
        return "redirect:/admin/warehouse-receipts";
    }


    @GetMapping("/view/{id}")
    public String viewReceipt(@PathVariable Long id, Model model) {
        WarehouseReceipt receipt = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));

        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);

        model.addAttribute("receipt", receipt);
        model.addAttribute("items", items);
        return "warehouse-receipt-view";
    }

    @GetMapping("/export/{id}")
    public void exportReceiptToPDF(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {


        WarehouseReceipt receipt = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));
        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);
        PurchaseOrder order = receipt.getPurchaseOrder();
        Supplier supplier = order.getSupplier();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=phieu-nhap-" + receipt.getCode() + ".pdf");

        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 12);

        document.add(new Paragraph("Công Ty Thiết Bị Điện Hải Phòng", headerFont));
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("PHIẾU NHẬP KHO", headerFont));
        document.add(new Paragraph("Mã phiếu: " + receipt.getCode(), normalFont));
        document.add(new Paragraph("Kho: " + receipt.getWarehouse().getName(), normalFont));
        document.add(new Paragraph("Ngày tạo: " + receipt.getCreatedAt(), normalFont));
        document.add(new Paragraph("Người tạo: " + receipt.getCreatedBy(), normalFont));
        document.add(new Paragraph("Ghi chú: " + (receipt.getNote() == null ? "" : receipt.getNote()), normalFont));
        document.add(new Paragraph("\n"));

        document.add(new Paragraph("Nhà cung cấp: " + supplier.getName(), normalFont));
        document.add(new Paragraph("Người đại diện: " + supplier.getContactName(), normalFont));
        document.add(new Paragraph("Số điện thoại: " + supplier.getContactMobile(), normalFont));
        document.add(new Paragraph("\n"));

        // Bảng sản phẩm
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10);

        table.addCell("STT");
        table.addCell("Tên sản phẩm");
        table.addCell("Số lượng");

        int index = 1;
        for (WarehouseReceiptItem item : items) {
            table.addCell(String.valueOf(index++));
            table.addCell(item.getProduct().getName());
            table.addCell(String.valueOf(item.getQuantity()));
        }

        document.add(table);
        document.close();
    }


}
