package com.eewms.repository.warehouseReceipt;

import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.WarehouseReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WarehouseReceiptRepository extends JpaRepository<WarehouseReceipt, Long> {
    Optional<WarehouseReceipt> findByCode(String code);
    Optional<WarehouseReceipt> findByRequestId(String requestId);


    @Query("""
           select r
           from WarehouseReceipt r
           where r.createdAt between :from and :to
           """)
    List<WarehouseReceipt> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    // ✅ Tính top NCC theo tổng tiền của PurchaseOrder, lọc theo thời gian của WarehouseReceipt.createdAt
    @Query("""
           select po.supplier.id, po.supplier.name, coalesce(sum(po.totalAmount), 0)
           from WarehouseReceipt r
           join r.purchaseOrder po
           where r.createdAt between :from and :to
           group by po.supplier.id, po.supplier.name
           order by coalesce(sum(po.totalAmount), 0) desc
           """)
    List<Object[]> topSuppliers(LocalDateTime from, LocalDateTime to);
    Page<WarehouseReceipt> findByCreatedAtIsNotNullOrderByCreatedAtDesc(Pageable p);
}
