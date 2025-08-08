package com.eewms.repository;

import com.eewms.entities.DebtTransaction;
import com.eewms.entities.DebtTransaction.Type;
import com.eewms.entities.DebtTransaction.PartnerType;
import com.eewms.entities.DebtTransaction.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebtTransactionRepository extends JpaRepository<DebtTransaction, Long> {

    List<DebtTransaction> findByType(Type type);

    List<DebtTransaction> findByPartnerTypeAndPartnerId(PartnerType partnerType, Long partnerId);

    List<DebtTransaction> findByStatus(Status status);
}
