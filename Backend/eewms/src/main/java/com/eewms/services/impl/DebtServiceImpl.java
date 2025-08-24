package com.eewms.services.impl;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDebtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.eewms.services.IPayOsService;
import com.eewms.dto.payOS.PayOsOrderResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;

@Service
@RequiredArgsConstructor
public class DebtServiceImpl implements IDebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final GoodIssueNoteRepository goodIssueNoteRepository;

    private final IPayOsService payOsService;

    @Override
    @Transactional
    public Debt createDebtForReceipt(Long warehouseReceiptId, int termDays) {
        var wr = warehouseReceiptRepository.findById(warehouseReceiptId)
                .orElseThrow(() -> new IllegalArgumentException("WarehouseReceipt not found: " + warehouseReceiptId));

        // Guard: GRN từ hoàn hàng -> không tạo công nợ
        if (wr.getRequestId() != null && wr.getRequestId().startsWith("SR-RECV-")) {
            throw new IllegalStateException("Phiếu nhập hàng hoàn – không tạo công nợ.");
        }


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
                .paidAmount(ZERO)
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

    /**
     * ================== NEW: Công nợ cho đơn bán ==================
     */
    @Transactional
    public Debt createDebtForSaleOrder(Long saleOrderId, int termDays) {
        var order = saleOrderRepository.findById(saleOrderId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("SaleOrder not found: " + saleOrderId));

        Long soId = (order.getSoId() != null) ? order.getSoId().longValue() : order.getId();

        // 1) Tìm theo khóa mới (soId)
        var existedBySoId = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, soId);
        if (existedBySoId.isPresent()) return existedBySoId.get();

        // 2) Fallback: tìm bản ghi cũ (document_id = order.id), nếu có thì migrate sang soId
        var existedByOrderId = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, order.getId());
        if (existedByOrderId.isPresent()) {
            Debt d = existedByOrderId.get();
            d.setDocumentId(soId);              // migrate khóa
            return recomputeAndSave(d);
        }

        // 3) Tạo mới theo soId
        java.time.LocalDate invDate = (order.getOrderDate() != null) ? order.getOrderDate().toLocalDate() : java.time.LocalDate.now();
        Debt debt = Debt.builder()
                .partyType(Debt.PartyType.CUSTOMER)
                .documentType(Debt.DocumentType.SALES_INVOICE)
                .documentId(soId) // <-- khóa chuẩn
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : java.math.BigDecimal.ZERO)
                .paidAmount(java.math.BigDecimal.ZERO)
                .status(Debt.Status.UNPAID)
                .invoiceDate(invDate)
                .dueDate(invDate.plusDays(Math.max(0, termDays)))
                .note("Công nợ từ đơn bán " + order.getSoCode())
                .build();

        debt = debtRepository.save(debt);
        if (termDays == 0) {
            pay(debt.getId(), debt.getTotalAmount(), com.eewms.entities.DebtPayment.Method.CASH, invDate,
                    "AUTO-IMMEDIATE", "Auto pay on sale order confirm");
        } else {
            recomputeAndSave(debt);
        }
        return debt;
    }

    /**
     * =============================================================
     */

    @Override
    @Transactional
    public DebtPayment pay(Long debtId, BigDecimal amount, DebtPayment.Method method,
                           LocalDate paymentDate, String referenceNo, String note) {

        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Số tiền phải > 0");

        var debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + debtId));

        BigDecimal total = debt.getTotalAmount() == null ? ZERO : debt.getTotalAmount();
        BigDecimal paid = debt.getPaidAmount() == null ? ZERO : debt.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);

        if (remaining.signum() <= 0) {
            throw new IllegalStateException("Công nợ đã được thanh toán đủ, không thể thanh toán tiếp.");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Số tiền vượt quá số còn lại (" + remaining + ").");
        }

        // ===== NEW: chuẩn hoá & validate mã tham chiếu =====
        DebtPayment.Method m = (method == null ? DebtPayment.Method.CASH : method);
        String ref = (referenceNo == null) ? null : referenceNo.trim();

        // Chuyển khoản: bắt buộc có mã tham chiếu
        if (m == DebtPayment.Method.BANK_TRANSFER) {
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException("Vui lòng nhập mã tham chiếu (số UNC/mã giao dịch) cho thanh toán chuyển khoản.");
            }
        }

        // Tiền mặt: nếu chưa nhập, tự sinh số phiếu thu (RCPT-yyyymmdd-001…)
        if (m == DebtPayment.Method.CASH && (ref == null || ref.isBlank())) {
            ref = generateReceiptNo(debtId);
        }

        // Nếu có ref thì chống trùng trong cùng 1 debt (ignore case)
        if (ref != null && !ref.isBlank()) {
            boolean duplicated = debtPaymentRepository.existsByDebtIdAndReferenceNoIgnoreCase(debtId, ref);
            if (duplicated) {
                throw new IllegalArgumentException("Mã tham chiếu đã tồn tại cho công nợ này.");
            }
        }
        // ===================================================

        DebtPayment p = DebtPayment.builder()
                .debt(debt)
                .amount(amount)
                .method(m)
                .paymentDate(paymentDate == null ? LocalDate.now() : paymentDate)
                .referenceNo(ref)
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
        BigDecimal total = (debt.getTotalAmount() == null) ? ZERO : debt.getTotalAmount();
        BigDecimal paid = (debt.getPaidAmount() == null) ? ZERO : debt.getPaidAmount();

        Debt.Status s;
        int cmp = paid.compareTo(total);
        if (paid.signum() == 0) s = Debt.Status.UNPAID;
        else if (cmp < 0) s = Debt.Status.PARTIAL;
        else s = Debt.Status.PAID;

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
        var wrItems = warehouseReceiptItemRepository.findByWarehouseReceipt(wr);
        if (wrItems != null && !wrItems.isEmpty()) {
            return wrItems.stream()
                    .map(i -> {
                        BigDecimal price = (i.getPrice() != null) ? i.getPrice() : ZERO;
                        long qty = (i.getActualQuantity() != null) ? i.getActualQuantity().longValue()
                                : (i.getQuantity() != null) ? i.getQuantity().longValue()
                                : 0L;
                        return price.multiply(BigDecimal.valueOf(qty));
                    })
                    .reduce(ZERO, BigDecimal::add);
        }

        var po = wr.getPurchaseOrder();
        if (po != null) {
            var poItems = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
            if (poItems != null && !poItems.isEmpty()) {
                return poItems.stream()
                        .map(i -> {
                            BigDecimal price = (i.getPrice() != null) ? i.getPrice() : ZERO;
                            long qty = (i.getActualQuantity() != null) ? i.getActualQuantity().longValue()
                                    : (i.getContractQuantity() != null) ? i.getContractQuantity().longValue()
                                    : 0L;
                            return price.multiply(BigDecimal.valueOf(qty));
                        })
                        .reduce(ZERO, BigDecimal::add);
            }
            if (po.getTotalAmount() != null) return po.getTotalAmount();
        }
        return ZERO;
    }

    @Override
    @Transactional
    public Debt createDebtForGoodIssue(Long ginId, int termDays) {
        var gin = goodIssueNoteRepository.findById(ginId)
                .orElseThrow(() -> new IllegalArgumentException("GoodIssueNote not found: " + ginId));

        if (gin.getGinCode() != null && gin.getGinCode().startsWith("RPL")) {
            throw new IllegalStateException("Phiếu xuất đổi hàng – không tạo công nợ.");
        }
        // Tổng tiền ưu tiên từ GIN
        BigDecimal total = (gin.getTotalAmount() != null) ? gin.getTotalAmount() : ZERO;

        // Dedupe theo (GOOD_ISSUE, ginId)
        var existed = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.GOOD_ISSUE, ginId);
        if (existed.isPresent()) return existed.get();

        // Ngày chứng từ & hạn
        LocalDate invoiceDate = (gin.getIssueDate() != null) ? gin.getIssueDate().toLocalDate() : LocalDate.now();
        LocalDate dueDate = invoiceDate.plusDays(Math.max(0, termDays));

        // Thông tin KH
        Long customerId = (gin.getCustomer() != null) ? gin.getCustomer().getId() : null;
        if (customerId == null) {
            throw new IllegalStateException("Phiếu xuất chưa gắn khách hàng, không thể tạo công nợ.");
        }

        Debt debt = Debt.builder()
                .partyType(Debt.PartyType.CUSTOMER)
                .documentType(Debt.DocumentType.GOOD_ISSUE)
                .documentId(ginId)
                .customerId(customerId)
                .totalAmount(total)
                .paidAmount(ZERO)
                .status(Debt.Status.UNPAID)
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .note("Công nợ từ phiếu xuất " + gin.getGinCode())
                .build();

        debt = debtRepository.save(debt);

        if (termDays == 0) {
            pay(debt.getId(), total, DebtPayment.Method.CASH, invoiceDate,
                    "AUTO-IMMEDIATE", "Auto pay on GIN confirm");
        } else {
            recomputeAndSave(debt);
        }
        return debt;
    }

    // ===== NEW: Sinh số phiếu thu tự động cho CASH, đảm bảo không trùng trong cùng debt =====
    private String generateReceiptNo(Long debtId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyymmdd
        String base = "RCPT-" + date + "-";
        int seq = 1;
        String candidate;
        do {
            candidate = base + String.format("%03d", seq++);
        } while (debtPaymentRepository.existsByDebtIdAndReferenceNoIgnoreCase(debtId, candidate));
        return candidate;
    }

    /* ================== NEW: QR for DebtPayment via PayOS ================== */
    @Transactional
    public PayOsOrderResponse createDebtPaymentQR(Long debtId,
                                                  java.math.BigDecimal amount,
                                                  Long goodIssueNoteId,
                                                  String createdBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền phải > 0");
        }

        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công nợ: " + debtId));

        java.math.BigDecimal total = debt.getTotalAmount() == null ? ZERO : debt.getTotalAmount();
        java.math.BigDecimal paid = debt.getPaidAmount() == null ? ZERO : debt.getPaidAmount();
        java.math.BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() <= 0) {
            throw new IllegalStateException("Công nợ đã thanh toán đủ.");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Số tiền vượt quá số còn lại (" + remaining + ").");
        }

        // 1) Khởi tạo bản ghi DebtPayment ở trạng thái PENDING (chưa cộng vào paidAmount)
        DebtPayment payment = new DebtPayment();
        payment.setDebt(debt);
        payment.setAmount(amount);
        payment.setMethod(DebtPayment.Method.PAYOS_QR);          // enum của bạn
        payment.setStatus(DebtPayment.Status.PENDING);           // chờ webhook
        payment.setPaymentDate(java.time.LocalDate.now());       // ngày tạo yêu cầu
        payment.setCreatedAt(java.time.LocalDateTime.now());     // nếu entity có
        payment.setCreatedBy(createdBy);                         // nếu entity có
        // tham chiếu/ghi chú để null, sẽ điền sau khi có PayOS

        // 2) Mô tả ngắn <= 25 ký tự theo PayOS
        String base = (goodIssueNoteId != null)
                ? String.format("[PAYOS] DEBT-%d GIN#%d", debtId, goodIssueNoteId)
                : String.format("[PAYOS] DEBT-%d", debtId);
        String desc = (base.length() > 25) ? base.substring(0, 25) : base;

        // 3) Sinh orderCode numeric (unique đủ dùng)
        long orderCodeNum = (System.currentTimeMillis() / 100) % 1_000_000_000L;

        // 4) Gọi PayOS
        long amountVnd = amount.longValueExact();
        com.eewms.dto.payOS.PayOsOrderResponse resp =
                payOsService.createOrder(String.valueOf(orderCodeNum), amountVnd, desc);

        if (resp == null || !resp.isSuccess()) {
            String reason = (resp == null) ? "payRes=null"
                    : ("code=" + resp.getCode() + ", desc=" + resp.getDesc());
            throw new IllegalStateException("Không tạo được QR PayOS: " + reason);
        }

        // 5) Lưu thông tin PayOS vào bản ghi payment (không cộng tiền)
        payment.setPayosOrderCode(String.valueOf(resp.getOrderCode()));
        payment.setPayosPaymentLinkId(resp.getPaymentLinkId());
        String checkout = (resp.getCheckoutUrl() != null) ? resp.getCheckoutUrl() : resp.getPaymentLink();
        payment.setPayosCheckoutUrl(checkout);
        payment.setPayosQrCode(resp.getQrCode());
        debtPaymentRepository.save(payment);

        // KHÔNG cập nhật debt.paidAmount ở đây — chờ webhook PAID.
        org.slf4j.LoggerFactory.getLogger(DebtServiceImpl.class).info(
                "[Debt][QR][create] debtId={} amount={} orderCode={} link={}",
                debtId, amount, payment.getPayosOrderCode(), checkout
        );

        return resp;
    }
    /* ====================================================================== */

    /* ================== WEBHOOK/APIs: update PayOS payment ================== */
    @jakarta.transaction.Transactional
    @Override
    public void markPayOsPaymentPaid(String payosOrderCode,
                                     String referenceNo,
                                     LocalDate paidDate) {
        DebtPayment p = debtPaymentRepository.findByPayosOrderCode(payosOrderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy DebtPayment với orderCode=" + payosOrderCode));

        if (p.getMethod() != DebtPayment.Method.PAYOS_QR) {
            throw new IllegalStateException("Bản ghi không phải thanh toán QR.");
        }

        // Idempotent
        if (p.getStatus() == DebtPayment.Status.PAID) {
            return;
        }
        if (p.getStatus() != DebtPayment.Status.PENDING) {
            throw new IllegalStateException("Chỉ thanh toán được khi trạng thái là PENDING (hiện tại: " + p.getStatus() + ").");
        }

        // Gắn referenceNo nếu có và chưa trùng trong cùng debt
        if (referenceNo != null && !referenceNo.isBlank()) {
            String ref = referenceNo.trim();
            boolean duplicated = debtPaymentRepository.existsByDebtIdAndReferenceNoIgnoreCase(p.getDebt().getId(), ref);
            if (!duplicated) {
                p.setReferenceNo(ref);
            }
        }

        // Cập nhật payment
        p.setStatus(DebtPayment.Status.PAID);
        p.setPaymentDate(Optional.ofNullable(paidDate).orElse(LocalDate.now()));
        debtPaymentRepository.save(p);

        // Cộng tiền vào công nợ
        Debt debt = p.getDebt();
        BigDecimal paid = Optional.ofNullable(debt.getPaidAmount()).orElse(ZERO);
        debt.setPaidAmount(paid.add(Optional.ofNullable(p.getAmount()).orElse(ZERO)));
        recomputeAndSave(debt);
    }

    @Transactional
    @Override
    public void markPayOsPaymentFailed(String payosOrderCode, String reason) {
        DebtPayment p = debtPaymentRepository.findByPayosOrderCode(payosOrderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy DebtPayment với orderCode=" + payosOrderCode));

        if (p.getMethod() != DebtPayment.Method.PAYOS_QR) {
            throw new IllegalStateException("Bản ghi không phải thanh toán QR.");
        }
        if (p.getStatus() == DebtPayment.Status.PAID) {
            // Đã ghi nhận tiền rồi thì bỏ qua (idempotent)
            return;
        }
        if (p.getStatus() != DebtPayment.Status.PENDING) {
            // Nếu đã FAILED/CANCELED/EXPIRED trước đó thì bỏ qua
            return;
        }

        p.setStatus(DebtPayment.Status.FAILED);
        if (reason != null && !reason.isBlank()) {
            String note = Optional.ofNullable(p.getNote()).orElse("");
            p.setNote((note.isBlank() ? "" : (note + " | ")) + "[PAYOS] " + reason.trim());
        }
        debtPaymentRepository.save(p);
        // Không thay đổi Debt.paidAmount
    }

    @Transactional
    @Override
    public void cancelPayOsPayment(String payosOrderCode) {
        DebtPayment p = debtPaymentRepository.findByPayosOrderCode(payosOrderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy DebtPayment với orderCode=" + payosOrderCode));

        if (p.getMethod() != DebtPayment.Method.PAYOS_QR) {
            throw new IllegalStateException("Bản ghi không phải thanh toán QR.");
        }
        if (p.getStatus() == DebtPayment.Status.PAID) {
            throw new IllegalStateException("Giao dịch đã PAID, không thể hủy.");
        }
        if (p.getStatus() != DebtPayment.Status.PENDING) {
            // Đã FAILED/CANCELED/EXPIRED thì không làm gì thêm
            return;
        }

        p.setStatus(DebtPayment.Status.CANCELED);
        debtPaymentRepository.save(p);
        // Không thay đổi Debt.paidAmount
    }
    /* ======================================================================= */

    @jakarta.transaction.Transactional
    @Override
    public BigDecimal adjustCustomerDebtBySaleOrder(Long saleOrderId, BigDecimal adjustmentAmount, String note) {
        if (saleOrderId == null || adjustmentAmount == null || adjustmentAmount.signum() <= 0) return ZERO;

        var opt = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, saleOrderId);
        if (opt.isEmpty()) return ZERO;

        Debt debt = opt.get();
        BigDecimal total = debt.getTotalAmount() == null ? ZERO : debt.getTotalAmount();
        BigDecimal paid  = debt.getPaidAmount()  == null ? ZERO : debt.getPaidAmount();
        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() <= 0) return ZERO;

        BigDecimal applied = adjustmentAmount.min(remaining);

        debt.setPaidAmount(paid.add(applied));
        // nối note nếu có
        if (note != null && !note.isBlank()) {
            String old = debt.getNote();
            debt.setNote((old == null || old.isBlank()) ? note : (old + " | " + note));
        }
        recomputeAndSave(debt);  // đã có sẵn trong lớp của bạn
        return applied;
    }
    @Transactional
    @Override
    public BigDecimal adjustCustomerDebtForSaleOrderPreferGIN(Long soId,
                                                              BigDecimal adjustmentAmount,
                                                              String note) {
        if (soId == null || adjustmentAmount == null || adjustmentAmount.signum() <= 0)
            return BigDecimal.ZERO;

        // 1) Ưu tiên công nợ gắn với các GIN của đơn này (mới nhất trước)
        var ginIds = goodIssueNoteRepository.findGinIdsBySoIdOrderByIssueDateDesc(soId.intValue());
        for (Long ginId : ginIds) {
            var dOpt = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.GOOD_ISSUE, ginId);
            if (dOpt.isEmpty()) continue;

            BigDecimal applied = applyOffsetAndLog(dOpt.get(), adjustmentAmount, note);
            if (applied.signum() > 0) return applied;  // ✅ khấu trừ xong và đã ghi lịch sử
        }

        // 2) Fallback: công nợ theo SALES_INVOICE (nếu bạn còn dùng)
        var soDebtOpt = debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, soId);
        if (soDebtOpt.isPresent()) {
            return applyOffsetAndLog(soDebtOpt.get(), adjustmentAmount, note);
        }

        return BigDecimal.ZERO;
    }

    /** Khấu trừ vào 1 Debt cụ thể + tạo bản ghi DebtPayment (RETURN_OFFSET) */
    private BigDecimal applyOffsetAndLog(Debt debt, BigDecimal amount, String note) {
        BigDecimal total = Optional.ofNullable(debt.getTotalAmount()).orElse(BigDecimal.ZERO);
        BigDecimal paid  = Optional.ofNullable(debt.getPaidAmount()).orElse(BigDecimal.ZERO);
        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal applied = amount.min(remaining);

        // 1) Lưu lịch sử thanh toán (đánh dấu đã trả)
        DebtPayment p = DebtPayment.builder()
                .debt(debt)
                .amount(applied)
                .method(DebtPayment.Method.RETURN_OFFSET)  // ✅ Hiện ở cột “Phương thức”
                .status(DebtPayment.Status.PAID)
                .paymentDate(java.time.LocalDate.now())
                .referenceNo(note)                         // ✅ ví dụ: "[RETURN] SRN00025"
                .note((note == null || note.isBlank())
                        ? "Khấu trừ công nợ do hoàn hàng"
                        : "Khấu trừ công nợ do hoàn hàng: " + note)
                .build();
        debtPaymentRepository.save(p);

        // 2) Cập nhật số đã thanh toán & trạng thái công nợ
        debt.setPaidAmount(paid.add(applied));
        if (note != null && !note.isBlank()) {
            String old = debt.getNote();
            debt.setNote((old == null || old.isBlank()) ? note : (old + " | " + note));
        }
        recomputeAndSave(debt);

        return applied;
    }

}
