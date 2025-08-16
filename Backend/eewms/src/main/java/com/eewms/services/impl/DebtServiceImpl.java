package com.eewms.services.impl;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.repository.DebtPaymentRepository;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.SaleOrderRepository;
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
    private final SaleOrderRepository saleOrderRepository;

    @Override
    @Transactional
    public Debt createDebtForReceipt(Long warehouseReceiptId, int termDays) {
        var wr = warehouseReceiptRepository.findById(warehouseReceiptId)
                .orElseThrow(() -> new IllegalArgumentException("WarehouseReceipt not found: " + warehouseReceiptId));

        // 1) Tổng tiền
        BigDecimal total = computeTotal(wr);

        // 2) Supplier (qua PO)
        var po = wr.getPurchaseOrder();
        var supplier = (po != null) ? po.getSupplier() : null;
        if (supplier == null) {
            throw new IllegalStateException("Supplier is required on receipt (via Purchase Order).");
        }

        // 3) Dedupe theo documentType + documentId
        var existed = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.WAREHOUSE_RECEIPT, wr.getId());
        if (existed.isPresent()) return existed.get();

        // 4) Ngày & hạn
        LocalDate invoiceDate = (wr.getCreatedAt() != null) ? wr.getCreatedAt().toLocalDate() : LocalDate.now();
        LocalDate dueDate = invoiceDate.plusDays(Math.max(0, termDays));

        // 5) Tạo debt
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

        // 6) Trả ngay nếu termDays == 0
        if (termDays == 0) {
            pay(debt.getId(), total, DebtPayment.Method.CASH, invoiceDate,
                    "AUTO-IMMEDIATE", "Auto pay on receipt confirm");
        } else {
            recomputeAndSave(debt);
        }
        return debt;
    }

    /** ================== NEW: Công nợ cho đơn bán ================== */
    @Transactional
    public Debt createDebtForSaleOrder(Long saleOrderId, int termDays) {
        var order = saleOrderRepository.findById(saleOrderId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("SaleOrder not found: " + saleOrderId));

        // Tổng tiền từ đơn bán
        BigDecimal total = (order.getTotalAmount() != null) ? order.getTotalAmount() : BigDecimal.ZERO;

        // Check duplicate
        var existed = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, order.getId());
        if (existed.isPresent()) return existed.get();

        LocalDate invoiceDate = (order.getOrderDate() != null) ? order.getOrderDate().toLocalDate() : LocalDate.now();
        LocalDate dueDate = invoiceDate.plusDays(Math.max(0, termDays));

        Debt debt = Debt.builder()
                .partyType(Debt.PartyType.CUSTOMER)
                .documentType(Debt.DocumentType.SALES_INVOICE)
                .documentId(order.getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .totalAmount(total)
                .paidAmount(BigDecimal.ZERO)
                .status(Debt.Status.UNPAID)
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .note("Công nợ từ đơn bán " + order.getSoCode())
                .build();

        debt = debtRepository.save(debt);

        if (termDays == 0) {
            pay(debt.getId(), total, DebtPayment.Method.CASH, invoiceDate,
                    "AUTO-IMMEDIATE", "Auto pay on sale order confirm");
        } else {
            recomputeAndSave(debt);
        }
        return debt;
    }
    /** ============================================================= */

    @Override
    @Transactional
    public DebtPayment pay(Long debtId, BigDecimal amount, DebtPayment.Method method,
                           LocalDate paymentDate, String referenceNo, String note) {

        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Số tiền phải > 0");

        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + debtId));

        BigDecimal total = debt.getTotalAmount() == null ? BigDecimal.ZERO : debt.getTotalAmount();
        BigDecimal paid  = debt.getPaidAmount()  == null ? BigDecimal.ZERO : debt.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);

        if (remaining.signum() <= 0) {
            throw new IllegalStateException("Công nợ đã được thanh toán đủ, không thể thanh toán tiếp.");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Số tiền vượt quá số còn lại (" + remaining + ").");
        }

        DebtPayment p = DebtPayment.builder()
                .debt(debt)
                .amount(amount)
                .method(method == null ? DebtPayment.Method.CASH : method)
                .paymentDate(paymentDate == null ? LocalDate.now() : paymentDate)
                .referenceNo(referenceNo)
                .note(note)
                .build();

        p = debtPaymentRepository.save(p);

        debt.setPaidAmount(paid.add(amount));
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

    /* ===== Helpers ===== */

    private Debt recomputeAndSave(Debt debt) {
        BigDecimal total = (debt.getTotalAmount() == null) ? BigDecimal.ZERO : debt.getTotalAmount();
        BigDecimal paid  = (debt.getPaidAmount() == null) ? BigDecimal.ZERO : debt.getPaidAmount();

        Debt.Status s;
        int cmp = paid.compareTo(total);
        if (paid.signum() == 0) s = Debt.Status.UNPAID;
        else if (cmp < 0)       s = Debt.Status.PARTIAL;
        else                    s = Debt.Status.PAID;

        if (s != Debt.Status.PAID && debt.getDueDate() != null && LocalDate.now().isAfter(debt.getDueDate())) {
            s = Debt.Status.OVERDUE;
        }
        debt.setStatus(s);
        return debtRepository.save(debt);
    }

    /** Tính tổng: ưu tiên WR items -> PO items -> PO.totalAmount */
    private BigDecimal computeTotal(WarehouseReceipt wr) {
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
            if (po.getTotalAmount() != null) return po.getTotalAmount();
        }
        return BigDecimal.ZERO;
    }
}
