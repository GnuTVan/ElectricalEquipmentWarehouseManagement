package com.eewms.services.impl;

import com.eewms.entities.DebtTransaction;
import com.eewms.entities.DebtTransaction.Status;
import com.eewms.entities.DebtTransaction.Type;
import com.eewms.entities.DebtTransaction.PartnerType;
import com.eewms.repository.DebtTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DebtTransactionService {

    @Autowired
    private DebtTransactionRepository debtTransactionRepository;

    public DebtTransaction createDebt(Type type, PartnerType partnerType, Long partnerId, BigDecimal amount, String note, LocalDateTime dueDate) {
        DebtTransaction debt = DebtTransaction.builder()
                .type(type)
                .partnerType(partnerType)
                .partnerId(partnerId)
                .amount(amount)
                .paidAmount(BigDecimal.ZERO)
                .remaining(amount)
                .status(Status.UNPAID)
                .note(note)
                .createdDate(LocalDateTime.now())
                .dueDate(dueDate)
                .build();

        return debtTransactionRepository.save(debt);
    }

    public List<DebtTransaction> getAll() {
        return debtTransactionRepository.findAll();
    }

    public Optional<DebtTransaction> findById(Long id) {
        return debtTransactionRepository.findById(id);
    }

    public void updateDebtStatus(DebtTransaction debt) {
        BigDecimal remaining = debt.getAmount().subtract(debt.getPaidAmount());
        debt.setRemaining(remaining);

        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(Status.PAID);
        } else if (remaining.compareTo(debt.getAmount()) < 0) {
            debt.setStatus(Status.PARTIALLY_PAID);
        } else {
            debt.setStatus(Status.UNPAID);
        }

        debtTransactionRepository.save(debt);
    }
}
