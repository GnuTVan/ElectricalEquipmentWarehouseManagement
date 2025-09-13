package com.eewms.controller;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDebtService;
import com.eewms.services.IWarehouseReceiptService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
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
    private final UserRepository userRepository;
    private final IWarehouseReceiptService warehouseReceiptService;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final WarehouseRepository warehouseRepository;
    // Công nợ
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final IDebtService debtService;


    /* ====== LIST ====== */
    @GetMapping
    public String list(Model model) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAllWithPurchaseOrder();
        Map<Long, Boolean> hasDebt = receipts.stream()
                .collect(Collectors.toMap(
                        WarehouseReceipt::getId,
                        r -> debtRepository.existsByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, r.getId())
                ));
        model.addAttribute("receipts", receipts);
        model.addAttribute("hasDebt", hasDebt);
        return "warehouse/warehouse-receipt-list";
    }

    /* ====== FORM TẠO GRN TỪ PO (tuỳ chọn dùng) ====== */
    @GetMapping("/form")
    public String showForm(@RequestParam("purchaseOrderId") Long purchaseOrderId,
                           Model model,
                           RedirectAttributes ra) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // ⚠️ Cho phép N GRN/PO: không chặn nữa
        List<PurchaseOrderItem> orderItems = purchaseOrderItemRepository.findByPurchaseOrderId(purchaseOrderId);

        model.addAttribute("purchaseOrder", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("warehouseReceiptDTO", new WarehouseReceiptDTO());
        model.addAttribute("warehouses", warehouseRepository.findAll());
        return "warehouse/warehouse-receipt-form";
    }

    /* ====== SAVE GRN (bỏ kho đích, idempotent theo requestId nếu có) ====== */
    @PostMapping("/save")
    public String save(@ModelAttribute WarehouseReceiptDTO dto,
                       Principal principal,
                       RedirectAttributes ra) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        PurchaseOrder order = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedByName(user.getFullName());
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            dto.setRequestId(java.util.UUID.randomUUID().toString());
        }

        warehouseReceiptService.saveReceipt(dto, order, user);

        ra.addFlashAttribute("message", "Tạo phiếu nhập kho thành công!");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/warehouse-receipts";
    }

    /* ====== VIEW ====== */
    @Transactional(readOnly = true)
    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        var dto = warehouseReceiptService.getViewDTO(id);
        model.addAttribute("receipt", dto);
        model.addAttribute("items", dto.getItems());

        debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, id).ifPresent(d -> {
            model.addAttribute("debt", d);
            model.addAttribute("payments", debtPaymentRepository.findByDebt(d));
        });
        return "warehouse/warehouse-receipt-view";
    }

    /* ====== CONFIRM (tạo công nợ) ====== */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam int termDays,
                          RedirectAttributes ra) {
        WarehouseReceipt wr = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập: " + id));

        // Không tạo công nợ cho phiếu nhập từ HOÀN HÀNG
        if (wr.getRequestId() != null && wr.getRequestId().startsWith("SR-RECV-")) {
            ra.addFlashAttribute("warning", "Phiếu nhập hàng hoàn – không tạo công nợ.");
            return "redirect:/admin/warehouse-receipts";
        }

        debtService.createDebtForReceipt(id, termDays);
        ra.addFlashAttribute("success", "Đã tạo công nợ.");
        return "redirect:/admin/warehouse-receipts";
    }

    /* ====== EXPORT PDF (giữ nguyên logic, chỉ null-safe kho) ====== */
    @GetMapping("/export/{id}")
    @Transactional(readOnly = true)
    public void exportPdf(@PathVariable Long id, HttpServletResponse response) {
        var dto = warehouseReceiptService.getViewDTO(id); // đã load đủ items
        try {
            String filename = "phieu-nhap-" + (dto.getCode() != null ? dto.getCode() : id) + ".pdf";
            response.setContentType("application/pdf");
            // inline để mở tab mới; đổi thành attachment nếu muốn tải về
            response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

            com.eewms.utils.ReceiptPdfExporter.export(dto, response.getOutputStream()); // ★ util mới
            response.flushBuffer();
        } catch (Exception e) {
            throw new RuntimeException("Xuất PDF lỗi: " + e.getMessage(), e);
        }
    }

}
