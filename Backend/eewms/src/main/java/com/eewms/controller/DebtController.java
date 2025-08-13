package com.eewms.controller;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.repository.DebtRepository;
import com.eewms.services.IDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
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

    @PostMapping("/{debtId}/pay")
    public String pay(@PathVariable Long debtId,
                      @RequestParam BigDecimal amount,
                      @RequestParam(required = false) DebtPayment.Method method,
                      @RequestParam(required = false) String referenceNo,
                      @RequestParam(required = false) String note,
                      @RequestParam(required = false) String paymentDate,
                      RedirectAttributes ra) {

        // Lấy trước URL quay lại (phiếu nhập của công nợ)
        Debt d = debtRepository.findById(debtId).orElseThrow();
        Long wrId = (d.getWarehouseReceipt() != null) ? d.getWarehouseReceipt().getId() : null;
        String back = (wrId != null)
                ? "redirect:/admin/warehouse-receipts/view/" + wrId
                : "redirect:/admin/warehouse-receipts";

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
            // Các lỗi do đã đủ tiền / trả vượt / số tiền không hợp lệ...
            ra.addFlashAttribute("message", ex.getMessage());
            ra.addFlashAttribute("messageType", "warning");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Có lỗi xảy ra khi thanh toán.");
            ra.addFlashAttribute("messageType", "danger");
        }

        return back;
    }

    @PostMapping("/{debtId}/due-date")
    public String updateDueDate(@PathVariable Long debtId,
                                @RequestParam String dueDate,
                                RedirectAttributes ra) {
        try {
            debtService.updateDueDate(debtId, LocalDate.parse(dueDate));
            ra.addFlashAttribute("message", "Cập nhật hạn thanh toán thành công.");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Không thể cập nhật hạn thanh toán.");
            ra.addFlashAttribute("messageType", "danger");
        }

        Debt d = debtRepository.findById(debtId).orElseThrow();
        Long wrId = (d.getWarehouseReceipt() != null) ? d.getWarehouseReceipt().getId() : null;
        return (wrId != null)
                ? "redirect:/admin/warehouse-receipts/view/" + wrId
                : "redirect:/admin/warehouse-receipts";
    }
}
