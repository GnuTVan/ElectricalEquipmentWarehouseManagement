package com.eewms.repository;

import com.eewms.constant.InventoryCountStatus;
import com.eewms.entities.InventoryCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, Integer> {
    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    List<InventoryCount> findAll();

    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    List<InventoryCount> findByAssignedStaff_Id(Long staffId);

    @Query("SELECT ic FROM InventoryCount ic " +
            "JOIN FETCH ic.warehouse " +
            "LEFT JOIN FETCH ic.assignedStaff " +
            "LEFT JOIN FETCH ic.items it " +
            "LEFT JOIN FETCH it.product " +
            "WHERE ic.id = :id")
    Optional<InventoryCount> findWithDetailsById(@Param("id") Integer id);
    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    @Query("""
    SELECT c
    FROM InventoryCount c
    WHERE (:warehouseId IS NULL OR c.warehouse.id = :warehouseId)
      AND (:status IS NULL OR c.status = :status)
      AND (:staffId IS NULL OR c.assignedStaff.id = :staffId)
      AND (:keyword IS NULL OR c.code LIKE %:keyword% OR c.note LIKE %:keyword%)
      AND (:createdAtFrom IS NULL OR c.createdAt >= :createdAtFrom)
      AND (:createdAtTo IS NULL OR c.createdAt <= :createdAtTo)
    ORDER BY c.updatedAt DESC
""")
    List<InventoryCount> filter(
            @Param("warehouseId") Integer warehouseId,
            @Param("status") InventoryCountStatus status,   // 👈 Đổi String → Enum
            @Param("staffId") Integer staffId,
            @Param("keyword") String keyword,
            @Param("createdAtFrom") LocalDate createdAtFrom,
            @Param("createdAtTo") LocalDate createdAtTo
    );
    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    @Query("""
        SELECT c FROM InventoryCount c
        WHERE c.assignedStaff.id = :staffId
          AND (:warehouseId IS NULL OR c.warehouse.id = :warehouseId)
          AND (:status IS NULL OR c.status = :status)
          AND (:keyword IS NULL OR c.code LIKE %:keyword% OR c.note LIKE %:keyword%)
          AND (:createdAtFrom IS NULL OR c.createdAt >= :createdAtFrom)
          AND (:createdAtTo IS NULL OR c.createdAt <= :createdAtTo)
          ORDER BY c.updatedAt DESC
    """)
    List<InventoryCount> filterForStaff(@Param("staffId") Long staffId,
                                        @Param("warehouseId") Integer warehouseId,
                                        @Param("status") InventoryCountStatus status,
                                        @Param("keyword") String keyword,
                                        @Param("createdAtFrom") LocalDate createdAtFrom,
                                        @Param("createdAtTo") LocalDate createdAtTo);

}