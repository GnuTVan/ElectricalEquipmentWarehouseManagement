package com.eewms.repository.inventoryTransfer;

import com.eewms.entities.InventoryTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;


import java.time.LocalDateTime;

public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"fromWarehouse", "toWarehouse", "createdBy"})
    @Query("""
        select t from InventoryTransfer t
        where (:keyword is null or :keyword = ''
               or lower(t.code) like lower(concat('%', :keyword, '%'))
               or lower(t.note) like lower(concat('%', :keyword, '%')))
          and (:status is null or t.status = :status)
          and (:fromDate is null or t.createdAt >= :fromDate)
          and (:toDate   is null or t.createdAt <= :toDate)
          and (:fromWarehouseId is null or t.fromWarehouse.id = :fromWarehouseId)
          and (:toWarehouseId   is null or t.toWarehouse.id   = :toWarehouseId)
        order by t.createdAt desc, t.id desc
    """)
    Page<InventoryTransfer> search(
            @Param("keyword") String keyword,
            @Param("status") InventoryTransfer.Status status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("fromWarehouseId") Integer fromWarehouseId,
            @Param("toWarehouseId") Integer toWarehouseId,
            Pageable pageable
    );

    /**
     * Truy vấn cho trang CHI TIẾT: fetch-join các quan hệ cần dùng trong view
     * tránh LazyInitializationException
     *
     * DISTINCT để tránh trùng record do join collection items.
     */
    @Query("""
    select distinct t
    from InventoryTransfer t
      join fetch t.fromWarehouse fw
      join fetch t.toWarehouse tw
      join fetch t.createdBy cb
      left join fetch t.fromApprovedBy fab
      left join fetch t.toApprovedBy tab
      left join fetch t.exportedBy exb
      left join fetch t.importedBy imb
      left join fetch t.items i
      left join fetch i.product p
"""+ " where t.id = :id")
    Optional<InventoryTransfer> findDetailById(@Param("id") Long id);
}
