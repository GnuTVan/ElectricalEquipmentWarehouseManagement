package com.eewms.repository;

import com.eewms.entities.Debt;
import com.eewms.entities.DebtPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {
    List<DebtPayment> findByDebt(Debt debt);
    List<DebtPayment> findByDebtId(Long debtId);
    List<DebtPayment> findByDebtIdOrderByPaymentDateDesc(Long debtId);

    // NEW: Chống trùng mã tham chiếu (không phân biệt hoa/thường) trong cùng 1 công nợ
    boolean existsByDebtIdAndReferenceNoIgnoreCase(Long debtId, String referenceNo);

    // NEW: dùng cho webhook PayOS
    Optional<DebtPayment> findByPayosOrderCode(String payosOrderCode);
    List<DebtPayment> findByDebtIdAndMethodOrderByIdDesc(Long debtId, DebtPayment.Method method);
    List<DebtPayment> findByDebtIdAndMethodAndStatusInOrderByIdDesc(
            Long debtId, DebtPayment.Method method, Collection<DebtPayment.Status> statuses);
}
