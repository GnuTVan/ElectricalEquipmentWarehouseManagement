package com.eewms.services.impl;

import com.eewms.entities.DebtPayment;
import com.eewms.entities.DebtTransaction;
import com.eewms.repository.DebtPaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DebtPaymentService {

    @Autowired
    private DebtPaymentRepository debtPaymentRepository;

    @Autowired
    private DebtTransactionService debtTransactionService;

    public DebtPayment createPayment(DebtTransaction debt, BigDecimal amount, String method, String note) {
        // Tạo bản ghi thanh toán
        DebtPayment payment = DebtPayment.builder()
                .debtTransaction(debt)
                .paymentDate(LocalDateTime.now())
                .amount(amount)
                .method(method)
                .note(note)
                .build();

        debtPaymentRepository.save(payment);

        // Cập nhật tổng đã thanh toán
        BigDecimal newPaidAmount = debt.getPaidAmount().add(amount);
        debt.setPaidAmount(newPaidAmount);

        // Cập nhật trạng thái và remaining
        debtTransactionService.updateDebtStatus(debt);

        return payment;
    }

    public List<DebtPayment> getPaymentsByDebt(DebtTransaction debt) {
        return debtPaymentRepository.findByDebtTransaction(debt);
    }
}
