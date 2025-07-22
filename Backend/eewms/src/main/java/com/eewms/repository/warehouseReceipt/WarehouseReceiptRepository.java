package com.eewms.repository.warehouseReceipt;

import com.eewms.entities.WarehouseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseReceiptRepository extends JpaRepository<WarehouseReceipt, Long> {
    Optional<WarehouseReceipt> findByCode(String code);

}