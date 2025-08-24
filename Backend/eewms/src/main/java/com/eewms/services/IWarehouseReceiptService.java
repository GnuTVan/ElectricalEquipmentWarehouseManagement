package com.eewms.services;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.User;
import com.eewms.entities.WarehouseReceipt;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface IWarehouseReceiptService {

    @Transactional
    void saveReceipt(WarehouseReceiptDTO dto, PurchaseOrder order, User user);
    WarehouseReceipt createFromSalesReturn(Long salesReturnId, String createdByUsername);

}