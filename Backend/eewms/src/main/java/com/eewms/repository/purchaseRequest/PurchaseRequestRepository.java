package com.eewms.repository.purchaseRequest;

import com.eewms.entities.PurchaseRequest;
import com.eewms.constant.PRStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {
    @Query("""
    SELECT pr FROM PurchaseRequest pr
    LEFT JOIN FETCH pr.items i
    LEFT JOIN FETCH i.product
    LEFT JOIN FETCH i.suggestedSupplier
    WHERE pr.id = :id
""")
    Optional<PurchaseRequest> findWithItemsById(@Param("id") Long id);
    List<PurchaseRequest> findByStatus(PRStatus status);
}
