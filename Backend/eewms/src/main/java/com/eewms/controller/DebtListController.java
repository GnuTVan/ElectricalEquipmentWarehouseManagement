package com.eewms.controller;

import com.eewms.entities.Debt;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/debts") // <-- URL bạn đang bấm
@RequiredArgsConstructor
public class DebtListController {

    private final DebtRepository debtRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;

    @GetMapping
    public String list(Model model) {
        // Lấy tất cả công nợ nhưng chỉ hiển thị loại gắn với phiếu nhập (supplier side)
        List<Row> rows = debtRepository.findAll().stream()
                .filter(d -> d.getDocumentType() == Debt.DocumentType.WAREHOUSE_RECEIPT)
                .map(d -> {
                    // Lấy mã phiếu nhập (nếu có)
                    String docCode = "";
                    Long receiptId = null;
                    if (d.getDocumentId() != null) {
                        WarehouseReceipt wr = warehouseReceiptRepository.findById(d.getDocumentId()).orElse(null);
                        if (wr != null) {
                            docCode = wr.getCode();
                            receiptId = wr.getId();
                        }
                    }

                    BigDecimal total = nz(d.getTotalAmount());
                    BigDecimal paid  = nz(d.getPaidAmount());
                    BigDecimal remain = total.subtract(paid);
                    if (remain.signum() < 0) remain = BigDecimal.ZERO;

                    String supplierName = d.getSupplier() != null ? d.getSupplier().getName() : "";

                    return new Row(
                            d.getId(),
                            supplierName,
                            docCode,
                            total,
                            paid,
                            remain,
                            d.getDueDate(),
                            d.getStatus(),
                            receiptId
                    );
                })
                .toList();

        model.addAttribute("rows", rows);
        return "debt/debt-list"; // -> templates/debt/debt-list.html
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    // View model đơn giản cho bảng
    public record Row(
            Long id,
            String supplierName,
            String docCode,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal remain,
            LocalDate dueDate,
            Debt.Status status,
            Long receiptId
    ) {}
}
