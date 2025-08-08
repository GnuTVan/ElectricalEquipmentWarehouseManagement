package com.eewms.repository;

import com.eewms.entities.DebtPayment;
import com.eewms.entities.DebtTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {

    List<DebtPayment> findByDebtTransaction(DebtTransaction debtTransaction);
}
