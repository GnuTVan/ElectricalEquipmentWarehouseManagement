package com.eewms.repository.warehouseReceipt;

import com.eewms.entities.WarehouseReceipt;
import com.eewms.entities.WarehouseReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseReceiptItemRepository extends JpaRepository<WarehouseReceiptItem, Long> {
    List<WarehouseReceiptItem> findByWarehouseReceipt(WarehouseReceipt receipt);
}
