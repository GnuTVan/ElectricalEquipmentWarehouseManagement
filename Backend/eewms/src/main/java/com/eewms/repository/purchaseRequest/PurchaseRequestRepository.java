package com.eewms.repository.purchaseRequest;

import com.eewms.constant.PRStatus;
import com.eewms.entities.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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

    Optional<PurchaseRequest> findBySaleOrder_SoId(Integer soId);

    // ==== NEW: lấy PR mở theo KH để tái sử dụng/gộp ====
    Optional<PurchaseRequest> findFirstByCustomer_IdAndStatusInOrderByIdDesc(
            Long customerId, Collection<PRStatus> statuses);

    // Lọc theo người tạo + ngày tạo
    @Query("""
        SELECT pr FROM PurchaseRequest pr
        WHERE (:creator IS NULL OR LOWER(pr.createdByName) LIKE LOWER(CONCAT('%', :creator, '%')))
          AND (:start IS NULL OR pr.createdAt >= :start)
          AND (:end   IS NULL OR pr.createdAt <= :end)
    """)
    Page<PurchaseRequest> filter(
            @Param("creator") String creator,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    boolean existsBySaleOrder_SoId(Integer soId);
    @Query("""
    select coalesce(sum(i.quantityNeeded),0)
    from PurchaseRequest pr
    join pr.items i
    where pr.status in (com.eewms.constant.PRStatus.MOI_TAO, com.eewms.constant.PRStatus.DA_DUYET)
      and i.product.id = :productId
""")
    Integer sumRequestedQtyOpenPRByProduct(@Param("productId") Integer productId);

    @Query("""
    select coalesce(sum(i.quantityNeeded),0)
    from PurchaseRequest pr
    join pr.items i
    where pr.status in (com.eewms.constant.PRStatus.MOI_TAO, com.eewms.constant.PRStatus.DA_DUYET)
      and i.product.id = :productId
      and (:start is null or pr.createdAt >= :start)
      and (:end   is null or pr.createdAt <= :end)
""")
    Integer sumRequestedQtyOpenPRByProductInRange(@Param("productId") Integer productId,
                                                  @Param("start") java.time.LocalDateTime start,
                                                  @Param("end")   java.time.LocalDateTime end);
}
