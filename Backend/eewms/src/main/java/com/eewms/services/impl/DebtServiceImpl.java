package com.eewms.services.impl;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.repository.DebtPaymentRepository;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDebtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DebtServiceImpl implements IDebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;

    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Override
    @Transactional
    public Debt createDebtForReceipt(Long warehouseReceiptId, int termDays) {
        var wr = warehouseReceiptRepository.findById(warehouseReceiptId)
                .orElseThrow(() -> new IllegalArgumentException("WarehouseReceipt not found: " + warehouseReceiptId));

        // 1) Tính tổng tiền
        BigDecimal total = computeTotal(wr);

        // 2) Lấy nhà cung cấp từ PO
        var po = wr.getPurchaseOrder();
        var supplier = (po != null) ? po.getSupplier() : null;
        if (supplier == null) {
            throw new IllegalStateException("Supplier is required on receipt (via Purchase Order).");
        }

        // 3) Dedupe theo documentType + documentId (chuẩn hóa)
        var existed = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, wr.getId());
        if (existed.isPresent()) return existed.get();

        // 4) Ngày hóa đơn & hạn thanh toán
        LocalDate invoiceDate = (wr.getCreatedAt() != null) ? wr.getCreatedAt().toLocalDate() : LocalDate.now();
        LocalDate dueDate = invoiceDate.plusDays(Math.max(0, termDays));

        // 5) Tạo Debt
        Debt debt = Debt.builder()
                .partyType(Debt.PartyType.SUPPLIER)
                .documentType(Debt.DocumentType.WAREHOUSE_RECEIPT)
                .documentId(wr.getId())

                .supplier(supplier)
                .warehouseReceipt(wr)
                .purchaseOrder(po)

                .totalAmount(total)
                .paidAmount(BigDecimal.ZERO)
                .status(Debt.Status.UNPAID)
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .build();

        debt = debtRepository.save(debt);

        // 6) Nếu trả ngay (termDays == 0) -> tạo payment full; ngược lại cập nhật trạng thái
        if (termDays == 0) {
            pay(debt.getId(), total, DebtPayment.Method.CASH, invoiceDate,
                    "AUTO-IMMEDIATE", "Auto pay on receipt confirm");
        } else {
            recomputeAndSave(debt);
        }

        return debt;
    }

    @Override
    @Transactional
    public DebtPayment pay(Long debtId, BigDecimal amount, DebtPayment.Method method,
                           LocalDate paymentDate, String referenceNo, String note) {

        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Amount must be > 0");

        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Debt not found: " + debtId));

        BigDecimal remaining = debt.getTotalAmount().subtract(debt.getPaidAmount());
        if (amount.compareTo(remaining) > 0) amount = remaining; // chặn trả vượt

        DebtPayment p = DebtPayment.builder()
                .debt(debt)
                .amount(amount)
                .method(method == null ? DebtPayment.Method.CASH : method)
                .paymentDate(paymentDate == null ? LocalDate.now() : paymentDate)
                .referenceNo(referenceNo)
                .note(note)
                .build();

        p = debtPaymentRepository.save(p);

        debt.setPaidAmount(debt.getPaidAmount().add(amount));
        recomputeAndSave(debt);
        return p;
    }

    @Override
    @Transactional
    public Debt updateDueDate(Long debtId, LocalDate newDueDate) {
        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Debt not found: " + debtId));
        debt.setDueDate(newDueDate);
        return recomputeAndSave(debt);
    }

    @Override
    @Transactional
    public Debt recomputeStatus(Long debtId) {
        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Debt not found: " + debtId));
        return recomputeAndSave(debt);
    }

    /* ============================ Helpers ============================ */

    private Debt recomputeAndSave(Debt debt) {
        BigDecimal total = (debt.getTotalAmount() == null) ? BigDecimal.ZERO : debt.getTotalAmount();
        BigDecimal paid  = (debt.getPaidAmount() == null) ? BigDecimal.ZERO : debt.getPaidAmount();

        Debt.Status s;
        int cmp = paid.compareTo(total);
        if (paid.signum() == 0) s = Debt.Status.UNPAID;
        else if (cmp < 0)       s = Debt.Status.PARTIAL;
        else                    s = Debt.Status.PAID;

        // Quá hạn nếu chưa PAID và đã quá dueDate
        if (s != Debt.Status.PAID && debt.getDueDate() != null && LocalDate.now().isAfter(debt.getDueDate())) {
            s = Debt.Status.OVERDUE;
        }
        debt.setStatus(s);
        return debtRepository.save(debt);
    }

    /**
     * Tính tổng: ưu tiên WR items -> PO items -> PO.totalAmount
     */
    private BigDecimal computeTotal(WarehouseReceipt wr) {
        // a) Tổng trên dòng phiếu nhập
        var wrItems = warehouseReceiptItemRepository.findByWarehouseReceipt(wr);
        if (wrItems != null && !wrItems.isEmpty()) {
            return wrItems.stream()
                    .map(i -> {
                        BigDecimal price = (i.getPrice() != null) ? i.getPrice() : BigDecimal.ZERO;
                        long qty = (i.getActualQuantity() != null) ? i.getActualQuantity().longValue()
                                : (i.getQuantity() != null) ? i.getQuantity().longValue()
                                : 0L;
                        return price.multiply(BigDecimal.valueOf(qty));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // b) Fallback: tổng theo dòng PO
        var po = wr.getPurchaseOrder();
        if (po != null) {
            var poItems = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
            if (poItems != null && !poItems.isEmpty()) {
                return poItems.stream()
                        .map(i -> {
                            BigDecimal price = (i.getPrice() != null) ? i.getPrice() : BigDecimal.ZERO;
                            long qty = (i.getActualQuantity() != null) ? i.getActualQuantity().longValue()
                                    : (i.getContractQuantity() != null) ? i.getContractQuantity().longValue()
                                    : 0L;
                            return price.multiply(BigDecimal.valueOf(qty));
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            // c) Fallback cuối: tổng trên PO (nếu đã có)
            if (po.getTotalAmount() != null) return po.getTotalAmount();
        }

        return BigDecimal.ZERO;
    }
}
