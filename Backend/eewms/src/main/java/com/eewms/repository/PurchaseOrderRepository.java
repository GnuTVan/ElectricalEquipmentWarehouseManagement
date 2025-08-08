package com.eewms.repository;

import com.eewms.entities.PurchaseOrder;
import com.eewms.constant.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByCode(String code);

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
    @Query("""
    SELECT po FROM PurchaseOrder po
    WHERE (:keyword IS NULL OR LOWER(po.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(po.createdByName) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:status IS NULL OR po.status = :status)
      AND (:from IS NULL OR po.createdAt >= :from)
      AND (:to IS NULL OR po.createdAt <= :to)
    ORDER BY po.code ASC
""")
    Page<PurchaseOrder> searchWithFilters(
            @Param("keyword") String keyword,
            @Param("status") PurchaseOrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
