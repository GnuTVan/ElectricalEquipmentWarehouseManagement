package com.eewms.repository.warehouseReceipt;

import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.WarehouseReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WarehouseReceiptRepository extends JpaRepository<WarehouseReceipt, Long> {
    Optional<WarehouseReceipt> findByRequestId(String requestId);

    // LIST (phân trang)
    @EntityGraph(attributePaths = "purchaseOrder")
    Page<WarehouseReceipt> findAll(Pageable pageable);

    // LIST (không phân trang)
    @EntityGraph(attributePaths = "purchaseOrder")
    @Query("select wr from WarehouseReceipt wr")
    List<WarehouseReceipt> findAllWithPurchaseOrder();

    // VIEW
    @EntityGraph(attributePaths = "purchaseOrder")
    Optional<WarehouseReceipt> findById(Long id);

    @EntityGraph(attributePaths = {
            "warehouse",
            "purchaseOrder",
            "items",
            "items.product"
    })
    @Query("select wr from WarehouseReceipt wr where wr.id = :id")
    Optional<WarehouseReceipt> findByIdWithView(@Param("id") Long id);

    // --- REPORT: FETCH Graph đầy đủ cho PO + SUPPLIER (dùng cho report) ---
    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier"
    })
    @Query("""
           select r
           from WarehouseReceipt r
           where r.createdAt between :from and :to
           """)
    List<WarehouseReceipt> findByCreatedAtBetweenWithPoAndSupplier(LocalDateTime from, LocalDateTime to);
}
