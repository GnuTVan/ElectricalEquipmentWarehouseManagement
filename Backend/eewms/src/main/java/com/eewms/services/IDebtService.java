package com.eewms.services;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface IDebtService {

    /** Tạo công nợ khi xác nhận phiếu nhập: termDays = 0 | 7 | 10 */
    Debt createDebtForReceipt(Long warehouseReceiptId, int termDays);

    /** Thanh toán 1 phần / toàn bộ cho một công nợ */
    DebtPayment pay(Long debtId, BigDecimal amount, DebtPayment.Method method,
                    LocalDate paymentDate, String referenceNo, String note);

    /** Cập nhật hạn thanh toán */
    Debt updateDueDate(Long debtId, LocalDate newDueDate);

    /** Đồng bộ lại trạng thái từ paidAmount/dueDate */
    Debt recomputeStatus(Long debtId);
    Debt createDebtForSaleOrder(Long saleOrderId, int termDays);
    Debt createDebtForGoodIssue(Long ginId, int termDays);


}