package com.eewms.controller;

import com.eewms.entities.DebtTransaction;
import com.eewms.entities.DebtPayment;
import com.eewms.entities.DebtTransaction.Type;
import com.eewms.entities.DebtTransaction.PartnerType;


import com.eewms.services.impl.DebtPaymentService;
import com.eewms.services.impl.DebtTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/debts")
public class DebtController {

    @Autowired
    private DebtTransactionService debtTransactionService;

    @Autowired
    private DebtPaymentService debtPaymentService;

    // Hiển thị danh sách công nợ
    @GetMapping
    public String listDebts(Model model) {
        model.addAttribute("debts", debtTransactionService.getAll());
        return "debt/list";
    }

    // Hiển thị form tạo mới công nợ
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("debt", new DebtTransaction());
        return "debt/create";
    }

    // Xử lý form tạo mới công nợ
    @PostMapping("/create")
    public String createDebt(@RequestParam("type") Type type,
                             @RequestParam("partnerType") PartnerType partnerType,
                             @RequestParam("partnerId") Long partnerId,
                             @RequestParam("amount") BigDecimal amount,
                             @RequestParam("note") String note,
                             @RequestParam("dueDate") String dueDate,
                             RedirectAttributes redirectAttributes) {

        debtTransactionService.createDebt(type, partnerType, partnerId, amount, note, LocalDateTime.parse(dueDate));

        // Set message
        redirectAttributes.addFlashAttribute("message", "Tạo công nợ thành công!");
        redirectAttributes.addFlashAttribute("messageType", "success"); // success, error, info, warning

        return "redirect:/debts";
    }


    // Hiển thị form thanh toán cho công nợ
    @GetMapping("/{id}/pay")
    public String showPaymentForm(@PathVariable Long id, Model model) {
        Optional<DebtTransaction> debtOpt = debtTransactionService.findById(id);
        if (debtOpt.isPresent()) {
            model.addAttribute("debt", debtOpt.get());
            return "debt/pay";
        }
        return "redirect:/debts";
    }

    // Xử lý form thanh toán
    @PostMapping("/{id}/pay")
    public String processPayment(@PathVariable Long id,
                                 @RequestParam("amount") BigDecimal amount,
                                 @RequestParam("method") String method,
                                 @RequestParam("note") String note,
                                 RedirectAttributes redirectAttributes) {

        Optional<DebtTransaction> debtOpt = debtTransactionService.findById(id);
        if (debtOpt.isPresent()) {
            debtPaymentService.createPayment(debtOpt.get(), amount, method, note);

            redirectAttributes.addFlashAttribute("message", "Thanh toán thành công!");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } else {
            redirectAttributes.addFlashAttribute("message", "Không tìm thấy công nợ!");
            redirectAttributes.addFlashAttribute("messageType", "error");
        }

        return "redirect:/debts";
    }

}
