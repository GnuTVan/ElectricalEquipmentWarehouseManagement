package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseReceiptReportDTO {

    private String receiptCode;
    private LocalDateTime createdAt;

    private String warehouseName;
    private String supplierName;

    private int totalQuantity;
    private BigDecimal totalAmount;
}