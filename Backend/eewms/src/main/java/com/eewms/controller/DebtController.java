package com.eewms.controller;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.repository.DebtRepository;
import com.eewms.services.IDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin/debts")
@RequiredArgsConstructor
public class DebtController {

    private final IDebtService debtService;
    private final DebtRepository debtRepository;

    /* ================== Xem chi tiết công nợ ================== */
    @GetMapping("/view/{id}")
    public String viewDebt(@PathVariable("id") Long id, Model model) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + id));
        model.addAttribute("debt", debt);
        return "debts/view"; // => templates/debts/view.html (nếu bạn có trang này)
    }
    /* ========================================================== */

    /* ============ Tạo công nợ từ ĐƠN BÁN (KH) =================
       Nhận thêm ginId để tạo xong quay lại trang chi tiết phiếu xuất.
     */
    @PostMapping("/create-from-sale-order/{soId}")
    public String createFromSaleOrder(@PathVariable("soId") Long soId,
                                      @RequestParam(value = "termDays", required = false, defaultValue = "30") int termDays,
                                      @RequestParam(value = "ginId", required = false) Long ginId,
                                      RedirectAttributes ra) {
        try {
            Debt debt = debtService.createDebtForSaleOrder(soId, termDays);
            ra.addFlashAttribute("message", "Đã tạo công nợ cho đơn bán (ID=" + soId + ").");
            ra.addFlashAttribute("messageType", "success");
            return (ginId != null) ? ("redirect:/good-issue/view/" + ginId)
                    : ("redirect:/admin/debts/view/" + debt.getId());
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Không thể tạo công nợ cho đơn bán: " + ex.getMessage());
            ra.addFlashAttribute("messageType", "danger");
            return (ginId != null) ? ("redirect:/good-issue/view/" + ginId) : "redirect:/good-issue";
        }
    }
    /* ========================================================== */

    /* ============ (Tuỳ chọn) Tạo công nợ từ PHIẾU NHẬP (NCC) === */
    @PostMapping("/create-from-receipt/{wrId}")
    public String createFromReceipt(@PathVariable("wrId") Long wrId,
                                    @RequestParam(value = "termDays", required = false, defaultValue = "30") int termDays,
                                    RedirectAttributes ra) {
        try {
            debtService.createDebtForReceipt(wrId, termDays);
            ra.addFlashAttribute("message", "Đã tạo công nợ cho phiếu nhập #" + wrId);
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/admin/warehouse-receipts/view/" + wrId;
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Không thể tạo công nợ: " + ex.getMessage());
            ra.addFlashAttribute("messageType", "danger");
            return "redirect:/admin/warehouse-receipts/view/" + wrId;
        }
    }
    /* ========================================================== */

    /* ================== Thanh toán công nợ ====================
       Hỗ trợ ginId để thanh toán xong quay lại trang chi tiết GIN.
     */
    @PostMapping("/{debtId}/pay")
    public String pay(@PathVariable Long debtId,
                      @RequestParam BigDecimal amount,
                      @RequestParam(required = false) DebtPayment.Method method,
                      @RequestParam(required = false) String referenceNo,
                      @RequestParam(required = false) String note,
                      @RequestParam(required = false) String paymentDate,
                      @RequestParam(required = false) Long ginId,
                      RedirectAttributes ra) {

        Debt d = debtRepository.findById(debtId).orElseThrow();

        // Ưu tiên quay lại trang chi tiết GIN nếu có ginId
        String back = (ginId != null) ? "redirect:/good-issue/view/" + ginId : null;
        if (back == null) {
            if (d.getWarehouseReceipt() != null) {
                Long wrId = d.getWarehouseReceipt().getId();
                back = (wrId != null)
                        ? "redirect:/admin/warehouse-receipts/view/" + wrId
                        : "redirect:/admin/warehouse-receipts";
            } else if (d.getDocumentType() == Debt.DocumentType.GOOD_ISSUE) {
                back = "redirect:/good-issue/view/" + d.getDocumentId();
            } else {
                back = "redirect:/admin/debts";
            }
        }

        try {
            LocalDate date = (paymentDate == null || paymentDate.isBlank())
                    ? LocalDate.now()
                    : LocalDate.parse(paymentDate);

            debtService.pay(
                    debtId,
                    amount,
                    (method == null ? DebtPayment.Method.CASH : method),
                    date,
                    referenceNo,
                    note
            );

            ra.addFlashAttribute("message", "Thanh toán công nợ thành công.");
            ra.addFlashAttribute("messageType", "success");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            ra.addFlashAttribute("message", ex.getMessage());
            ra.addFlashAttribute("messageType", "warning");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Có lỗi xảy ra khi thanh toán.");
            ra.addFlashAttribute("messageType", "danger");
        }

        return back;
    }
    /* ========================================================== */

    /* ================== Cập nhật hạn thanh toán (đặt ngày cụ thể) ================ */
    @PostMapping("/{debtId}/due-date")
    public String updateDueDate(@PathVariable Long debtId,
                                @RequestParam String dueDate,
                                @RequestParam(required = false) Long ginId,
                                RedirectAttributes ra) {
        try {
            debtService.updateDueDate(debtId, LocalDate.parse(dueDate));
            ra.addFlashAttribute("message", "Cập nhật hạn thanh toán thành công.");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Không thể cập nhật hạn thanh toán.");
            ra.addFlashAttribute("messageType", "danger");
        }

        if (ginId != null) {
            return "redirect:/good-issue/view/" + ginId;
        }

        Debt d = debtRepository.findById(debtId).orElseThrow();
        if (d.getWarehouseReceipt() != null) {
            Long wrId = d.getWarehouseReceipt().getId();
            return (wrId != null)
                    ? "redirect:/admin/warehouse-receipts/view/" + wrId
                    : "redirect:/admin/warehouse-receipts";
        }
        if (d.getDocumentType() == Debt.DocumentType.GOOD_ISSUE) {
            return "redirect:/good-issue/view/" + d.getDocumentId();
        }
        return "redirect:/admin/debts";
    }
    /* =========================================================================== */

    /* ============= Tạo công nợ từ PHIẾU XUẤT (KH từ GIN) ====================== */
    @PostMapping("/create-from-good-issue/{ginId}")
    public String createFromGoodIssue(@PathVariable("ginId") Long ginId,
                                      @RequestParam(value = "termDays", required = false, defaultValue = "30") int termDays,
                                      RedirectAttributes ra) {
        try {
            debtService.createDebtForGoodIssue(ginId, termDays);
            ra.addFlashAttribute("message", "Đã tạo công nợ khách hàng từ phiếu xuất #" + ginId);
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Không thể tạo công nợ: " + ex.getMessage());
            ra.addFlashAttribute("messageType", "danger");
        }
        return "redirect:/good-issue/view/" + ginId;
    }
    /* =========================================================================== */

    /* =============== ĐỔI KỲ HẠN theo số ngày (0/10/20/30) ======================
       Endpoint này khớp với form ở giao diện: /admin/debts/{id}/update-terms
       Tính dueDate = invoiceDate + termDays (nếu invoiceDate null thì dùng today).
     */
    @PostMapping("/{id}/update-terms")
    public String updateTerms(@PathVariable("id") Long debtId,
                              @RequestParam("termDays") int termDays,
                              @RequestParam(value = "redirectGinId", required = false) Long ginId,
                              RedirectAttributes ra) {
        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Debt not found: " + debtId));

        var baseDate = (debt.getInvoiceDate() != null) ? debt.getInvoiceDate() : LocalDate.now();
        debtService.updateDueDate(debtId, baseDate.plusDays(Math.max(0, termDays)));

        ra.addFlashAttribute("message", "Đã cập nhật kỳ hạn thanh toán.");
        ra.addFlashAttribute("messageType", "success");

        if (ginId != null) return "redirect:/good-issue/view/" + ginId;
        if (debt.getDocumentType() == Debt.DocumentType.GOOD_ISSUE) {
            return "redirect:/good-issue/view/" + debt.getDocumentId();
        }
        return "redirect:/admin/debts";
    }
    /* =========================================================================== */
}
