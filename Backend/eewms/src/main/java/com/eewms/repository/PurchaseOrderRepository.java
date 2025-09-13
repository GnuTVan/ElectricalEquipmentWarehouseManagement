package com.eewms.repository;

import com.eewms.entities.PurchaseOrder;
import com.eewms.constant.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByCode(String code);

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Dùng cho trang EDIT: nạp đủ các to-one/to-many cần thiết trong 1 query để tránh LazyInitializationException.
     * Lưu ý: CHỈ fetch to-many (details/product) nếu trang edit thực sự cần; nếu không thì bỏ 2 dòng JOIN dưới để tránh nhân bản kết quả.
     */
    @Query("""
                SELECT DISTINCT po FROM PurchaseOrder po
                LEFT JOIN FETCH po.supplier
                LEFT JOIN FETCH po.items i
                LEFT JOIN FETCH i.product
                WHERE po.id = :id
            """)
    Optional<PurchaseOrder> findByIdForEdit(@Param("id") Long id);

    @EntityGraph(attributePaths = {"supplier"})
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
    @EntityGraph(attributePaths = {"supplier", "items", "items.product"})
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> findWithDetailById(@Param("id") Long id);
}
