package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptReportFilter {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long warehouseId;
    private Long supplierId;
    private Long productId;
    private Long userId;
    private String receiptCode;
    private String poCode;
}
