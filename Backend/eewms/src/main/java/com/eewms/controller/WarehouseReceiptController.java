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

    // Công nợ
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final IDebtService debtService;

    /* ====== LIST ====== */
    @GetMapping
    public String list(Model model) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAll();
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
    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        WarehouseReceipt receipt = warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));
        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);

        model.addAttribute("receipt", receipt);
        model.addAttribute("items", items);

        debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, id).ifPresent(d -> {
            model.addAttribute("debt", d);
            model.addAttribute("payments", debtPaymentRepository.findByDebt(d));
        });
        return "warehouse/warehouse-receipt-view";
    }

    /* ====== CONFIRM (tạo công nợ) ====== */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(defaultValue = "7") int termDays,
                          RedirectAttributes ra) {
        warehouseReceiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));

        debtService.createDebtForReceipt(id, termDays);
        ra.addFlashAttribute("message", "Đã xác nhận nhập kho và tạo công nợ (hạn " + termDays + " ngày).");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/warehouse-receipts";
    }

    /* ====== EXPORT PDF (giữ nguyên logic, chỉ null-safe kho) ====== */
    // Giữ lại phương thức export bạn đang có.
}
