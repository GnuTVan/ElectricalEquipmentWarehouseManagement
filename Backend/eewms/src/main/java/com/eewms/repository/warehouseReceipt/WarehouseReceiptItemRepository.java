package com.eewms.repository.warehouseReceipt;

import com.eewms.entities.WarehouseReceipt;
import com.eewms.entities.WarehouseReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface WarehouseReceiptItemRepository extends JpaRepository<WarehouseReceiptItem, Long> {
    List<WarehouseReceiptItem> findByWarehouseReceipt(WarehouseReceipt receipt);

    @Query("""
      select coalesce(sum(i.actualQuantity),0)
      from WarehouseReceiptItem i
      where i.warehouseReceipt.purchaseOrder.id = :poId
        and i.product.id = :productId
    """)
    Integer sumReceivedByPoAndProduct(@Param("poId") Long poId,
                                      @Param("productId") Integer productId);

    @Query("""
    select i.product.id as pid, coalesce(sum(i.actualQuantity),0) as qty
    from WarehouseReceiptItem i
    where (i.condition is null or i.condition = com.eewms.constant.ProductCondition.NEW)
    group by i.product.id
  """)
    List<Object[]> sumNewByProduct();

    // Tổng hoàn
    @Query("""
    select i.product.id as pid, coalesce(sum(i.actualQuantity),0) as qty
    from WarehouseReceiptItem i
    where i.condition = com.eewms.constant.ProductCondition.RETURNED
    group by i.product.id
  """)
    List<Object[]> sumReturnedByProduct();
}
