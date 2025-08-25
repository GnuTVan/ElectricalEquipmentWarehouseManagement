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
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/debts") // <-- URL bạn đang bấm
@RequiredArgsConstructor
public class DebtListController {

    private final DebtRepository debtRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;

    @GetMapping
    public String list(Model model) {
        // Lấy đúng loại cần hiển thị + supplier đã nạp sẵn
        List<Debt> debts = debtRepository.findAllByDocumentType(Debt.DocumentType.WAREHOUSE_RECEIPT);

        // Gom tất cả documentId (WR) để batch-load một lần
        List<Long> wrIds = debts.stream()
                .map(Debt::getDocumentId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // Tải 1 lần tất cả WR rồi map theo id để tránh N+1
        Map<Long, WarehouseReceipt> wrMap = warehouseReceiptRepository.findAllById(wrIds).stream()
                .collect(Collectors.toMap(WarehouseReceipt::getId, wr -> wr));

        List<Row> rows = debts.stream()
                .map(d -> {
                    WarehouseReceipt wr = d.getDocumentId() != null ? wrMap.get(d.getDocumentId()) : null;
                    String docCode = wr != null ? wr.getCode() : "";
                    Long receiptId = wr != null ? wr.getId() : null;

                    BigDecimal total = nz(d.getTotalAmount());
                    BigDecimal paid = nz(d.getPaidAmount());
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
        return "debt/debt-list";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

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
    ) {
    }
}
