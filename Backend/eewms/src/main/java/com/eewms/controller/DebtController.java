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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequestMapping({"/admin/debts", "/debts"}) // khớp link ở sidebar
@RequiredArgsConstructor
public class DebtController {

    private final IDebtService debtService;
    private final DebtRepository debtRepository;

    /* ================== Helpers (reflection an toàn) ================== */
    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) { return null; }
    }

    private static String tryGetString(Object target, String... candidateMethods) {
        for (String m : candidateMethods) {
            Object v = invokeNoArg(target, m);
            if (v != null) return String.valueOf(v);
        }
        return "";
    }

    private static Object tryGet(Object target, String... candidateMethods) {
        for (String m : candidateMethods) {
            Object v = invokeNoArg(target, m);
            if (v != null) return v;
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }

    /* ================== NCC list ================== */
    @Transactional(readOnly = true)
    @GetMapping("/suppliers")
    public String suppliersList(Model model) {

        class SupplierRow {
            public String supplierName;
            public String supplierPhone; // NEW
            public String docCode;
            public java.math.BigDecimal total;
            public java.math.BigDecimal paid;
            public java.math.BigDecimal remain;
            public java.time.LocalDate dueDate;
            public Object status;   // r.status.label trong template
            public Long receiptId;

            SupplierRow(String supplierName, String supplierPhone, String docCode,
                        java.math.BigDecimal total, java.math.BigDecimal paid, java.math.BigDecimal remain,
                        java.time.LocalDate dueDate, Object status, Long receiptId) {
                this.supplierName = supplierName;
                this.supplierPhone = supplierPhone; // NEW
                this.docCode = docCode;
                this.total = total;
                this.paid = paid;
                this.remain = remain;
                this.dueDate = dueDate;
                this.status = status;
                this.receiptId = receiptId;
            }
        }

        var rows = debtRepository
                // preload supplier để tránh N+1 (repo đã có @EntityGraph(attributePaths={"supplier"}))
                .findAllByDocumentType(Debt.DocumentType.WAREHOUSE_RECEIPT)
                .stream()
                .filter(d -> {
                    var pt = d.getPartyType();
                    return pt != null && (pt.name().equals("SUPPLIER") || pt.name().equals("PROVIDER") || pt.name().equals("VENDOR"));
                })
                .map(d -> {
                    var total  = nz(d.getTotalAmount());
                    var paid   = nz(d.getPaidAmount());
                    var remain = total.subtract(paid);

                    String docCode = "WR#" + d.getDocumentId();
                    Long receiptId = (d.getWarehouseReceipt() != null)
                            ? d.getWarehouseReceipt().getId()
                            : d.getDocumentId();

                    String supplierName  = "";
                    String supplierPhone = "";
                    if (d.getSupplier() != null) {
                        if (d.getSupplier().getName() != null)
                            supplierName = d.getSupplier().getName();
                        if (d.getSupplier().getContactMobile() != null)
                            supplierPhone = d.getSupplier().getContactMobile(); // NEW
                    }

                    String statusLabel = (d.getStatus() == null) ? "" : String.valueOf(d.getStatus());
                    Object statusObj = java.util.Map.of("label", statusLabel);

                    return new SupplierRow(
                            supplierName, supplierPhone, // NEW
                            docCode,
                            total, paid, remain,
                            d.getDueDate(),
                            statusObj,
                            receiptId
                    );
                })
                .toList();

        model.addAttribute("rows", rows);
        return "debt/debt-supplier-list";
    }

    /* ========= KH list: dùng customerRef (read-only) ========= */
    public static class CustomerRow {
        public String customer;   // tên KH
        public String phone;      // SĐT KH
        public String docCode;    // Mã chứng từ (GIN#/SO#)
        public BigDecimal total;
        public BigDecimal paid;
        public BigDecimal remain;
        public LocalDate dueDate;
        public Object status;
        public Long goodIssueId;
        public Long debtId;

        public CustomerRow(String customer, String phone, String docCode,
                           BigDecimal total, BigDecimal paid, BigDecimal remain,
                           LocalDate dueDate, Object status, Long goodIssueId, Long debtId) {
            this.customer = customer;
            this.phone = phone;
            this.docCode = docCode;
            this.total = total;
            this.paid = paid;
            this.remain = remain;
            this.dueDate = dueDate;
            this.status = status;
            this.goodIssueId = goodIssueId;
            this.debtId = debtId;
        }
    }

    @Transactional(readOnly = true)
    @GetMapping("/customers")
    public String listCustomerDebts(Model model) {
        var rows = debtRepository.findAllByPartyType(Debt.PartyType.CUSTOMER).stream()
                .map(d -> {
                    BigDecimal total  = nz(d.getTotalAmount());
                    BigDecimal paid   = nz(d.getPaidAmount());
                    BigDecimal remain = total.subtract(paid);

                    String docCode;
                    Long goodIssueId = null;
                    var dt = d.getDocumentType();
                    if (dt == Debt.DocumentType.GOOD_ISSUE) {
                        docCode = "GIN#" + d.getDocumentId();
                        goodIssueId = d.getDocumentId();
                    } else if (dt == Debt.DocumentType.SALES_INVOICE) {
                        docCode = "SO#" + d.getDocumentId();
                    } else if (dt == Debt.DocumentType.WAREHOUSE_RECEIPT) {
                        docCode = "WR#" + d.getDocumentId();
                    } else {
                        docCode = String.valueOf(d.getDocumentId());
                    }

                    String name  = "";
                    String phone = "";
                    if (d.getCustomerRef() != null) {
                        if (d.getCustomerRef().getFullName() != null) name  = d.getCustomerRef().getFullName();
                        if (d.getCustomerRef().getPhone()    != null) phone = d.getCustomerRef().getPhone();
                    }

                    return new CustomerRow(
                            name,
                            phone,
                            docCode,
                            total, paid, remain,
                            d.getDueDate(),
                            d.getStatus(),
                            goodIssueId,
                            d.getId()
                    );
                })
                .toList();

        model.addAttribute("rows", rows);
        return "debt/debt-customer-list";
    }
    /* ================================================================ */

    /* ================== Xem chi tiết công nợ ================== */
    @GetMapping("/view/{id}")
    public String viewDebt(@PathVariable("id") Long id, Model model) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + id));
        model.addAttribute("debt", debt);
        return "debt/view";
    }

    /* ============ Tạo công nợ từ ĐƠN BÁN (KH) ================= */
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

    /* ================== Thanh toán công nợ ==================== */
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

    /* ================== Cập nhật hạn thanh toán ================== */
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

    /* =============== ĐỔI KỲ HẠN theo số ngày (0/10/20/30) ====================== */
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

    /* ================== Tạo QR PayOS cho công nợ (GIN) ==================== */
    @PostMapping("/{debtId}/payos/create")
    public String createPayOsForDebt(@PathVariable Long debtId,
                                     @RequestParam("amount") BigDecimal amount,
                                     RedirectAttributes ra) {

        Debt d = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + debtId));

        if (d.getPartyType() != Debt.PartyType.CUSTOMER || d.getDocumentType() != Debt.DocumentType.GOOD_ISSUE) {
            ra.addFlashAttribute("message", "Chỉ hỗ trợ PayOS cho công nợ KH từ Phiếu xuất.");
            ra.addFlashAttribute("messageType", "warning");
            return "redirect:/admin/debts/view/" + debtId;
        }

        BigDecimal total = d.getTotalAmount() == null ? BigDecimal.ZERO : d.getTotalAmount();
        BigDecimal paid = d.getPaidAmount() == null ? BigDecimal.ZERO : d.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);

        if (remaining.compareTo(BigDecimal.ONE) < 0) {
            ra.addFlashAttribute("message", "Công nợ đã thanh toán đủ hoặc còn lại quá nhỏ.");
            ra.addFlashAttribute("messageType", "info");
            return "redirect:/good-issue/view/" + d.getDocumentId();
        }

        if (amount == null || amount.signum() <= 0) {
            ra.addFlashAttribute("message", "Số tiền thanh toán phải > 0.");
            ra.addFlashAttribute("messageType", "warning");
            return "redirect:/good-issue/view/" + d.getDocumentId();
        }
        if (amount.compareTo(remaining) > 0) {
            ra.addFlashAttribute("message", "Số tiền vượt quá phần còn lại (" + remaining.longValue() + ").");
            ra.addFlashAttribute("messageType", "warning");
            return "redirect:/good-issue/view/" + d.getDocumentId();
        }

        try {
            var res = debtService.createDebtPaymentQR(d.getId(), amount, d.getDocumentId(), "SYSTEM");
            String link = (res.getCheckoutUrl() != null) ? res.getCheckoutUrl() : res.getPaymentLink();

            ra.addFlashAttribute("paymentLink", link);
            ra.addFlashAttribute("payOsOrderCode", String.valueOf(res.getOrderCode()));
            ra.addFlashAttribute("message", "Đã tạo lệnh thanh toán PayOS " + amount.longValue() + "đ. Vui lòng quét QR.");
            ra.addFlashAttribute("messageType", "success");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            ra.addFlashAttribute("message", ex.getMessage());
            ra.addFlashAttribute("messageType", "warning");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Lỗi tạo PayOS: " + ex.getMessage());
            ra.addFlashAttribute("messageType", "danger");
        }

        return "redirect:/good-issue/view/" + d.getDocumentId();
    }

    /* ================== Hủy QR PayOS đang PENDING ==================== */
    @PostMapping("/{debtId}/payos/cancel")
    public String cancelPayOsForDebt(@PathVariable Long debtId,
                                     @RequestParam("orderCode") String payosOrderCode,
                                     RedirectAttributes ra) {
        try {
            debtService.cancelPayOsPayment(payosOrderCode);
            ra.addFlashAttribute("message", "Đã hủy lệnh thanh toán QR.");
            ra.addFlashAttribute("messageType", "info");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            ra.addFlashAttribute("message", ex.getMessage());
            ra.addFlashAttribute("messageType", "warning");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Có lỗi khi hủy QR.");
            ra.addFlashAttribute("messageType", "danger");
        }

        var d = debtRepository.findById(debtId).orElse(null);
        if (d != null && d.getDocumentType() == Debt.DocumentType.GOOD_ISSUE) {
            return "redirect:/good-issue/view/" + d.getDocumentId();
        }
        return "redirect:/admin/debts/view/" + debtId;
    }
}
