package com.eewms.services;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.User;
import org.springframework.transaction.annotation.Transactional;

public interface IWarehouseReceiptService {

    @Transactional
    void saveReceipt(WarehouseReceiptDTO dto, PurchaseOrder order, User user);
}