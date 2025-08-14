package com.eewms.services.impl;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import com.eewms.repository.DebtPaymentRepository;
import com.eewms.services.IDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DebtPaymentServiceImpl {

    private final DebtPaymentRepository debtPaymentRepository;
    private final IDebtService debtService;

    public DebtPayment createPayment(Long debtId, BigDecimal amount,
                                     DebtPayment.Method method, LocalDate paymentDate,
                                     String referenceNo, String note) {
        return debtService.pay(debtId, amount, method, paymentDate, referenceNo, note);
    }

    public List<DebtPayment> getPaymentsByDebt(Debt debt) {
        return debtPaymentRepository.findByDebt(debt);
    }
}
