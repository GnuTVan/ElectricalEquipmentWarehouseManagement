package com.eewms.services;

import com.eewms.dto.payOS.PayOsOrderResponse;
import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface IDebtService {

    /**
     * Tạo công nợ khi xác nhận phiếu nhập: termDays = 0 | 7 | 10
     */
    Debt createDebtForReceipt(Long warehouseReceiptId, int termDays);

    /**
     * Thanh toán 1 phần / toàn bộ cho một công nợ
     */
    DebtPayment pay(Long debtId, BigDecimal amount, DebtPayment.Method method,
                    LocalDate paymentDate, String referenceNo, String note);

    /**
     * Cập nhật hạn thanh toán
     */
    Debt updateDueDate(Long debtId, LocalDate newDueDate);

    /**
     * Đồng bộ lại trạng thái từ paidAmount/dueDate
     */
    Debt recomputeStatus(Long debtId);

    Debt createDebtForSaleOrder(Long saleOrderId, int termDays);

    Debt createDebtForGoodIssue(Long ginId, int termDays);

    /**
     * Tạo QR PayOS cho 1 khoản thanh toán công nợ.
     * Không cộng vào 'đã thanh toán' tại thời điểm tạo; chỉ cộng khi webhook xác nhận PAID.
     *
     * @param debtId          ID công nợ
     * @param amount          Số tiền thanh toán
     * @param goodIssueNoteId ID phiếu xuất liên quan (có thể null)
     * @param createdBy       Người tạo
     * @return PayOsOrderResponse (checkoutUrl/qrCode/orderCode/...)
     */
    PayOsOrderResponse createDebtPaymentQR(Long debtId,
                                           BigDecimal amount,
                                           Long goodIssueNoteId,
                                           String createdBy);

    /**
     * Đánh dấu thanh toán QR là PAID (gọi từ webhook sau khi verify).
     */
    void markPayOsPaymentPaid(String payosOrderCode,
                              String referenceNo,   // có thể null
                              LocalDate paidDate);  // có thể null -> dùng today

    /**
     * Đánh dấu thanh toán QR là FAILED (gọi từ webhook).
     */
    void markPayOsPaymentFailed(String payosOrderCode, String reason);

    /**
     * Huỷ thanh toán QR đang PENDING (người dùng bấm huỷ).
     */
    void cancelPayOsPayment(String payosOrderCode);

    BigDecimal adjustCustomerDebtBySaleOrder(Long saleOrderId, BigDecimal adjustmentAmount, String note);


    @Transactional
    BigDecimal adjustCustomerDebtForSaleOrderPreferGIN(Long saleOrderId,
                                                       BigDecimal adjustmentAmount,
                                                       String note);

}