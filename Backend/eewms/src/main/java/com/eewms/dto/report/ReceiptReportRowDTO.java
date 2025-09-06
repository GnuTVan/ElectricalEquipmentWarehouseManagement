package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptReportRowDTO {
    private Long receiptId;
    private String receiptCode;
    private LocalDate receiptDate;
    private LocalDateTime receiptDateTime;
    private Integer warehouseId;
    private String warehouseName;
    private Long supplierId;
    private String supplierName;
    private String createdByName;
    private int totalQuantity;
    private BigDecimal totalAmount;
}
