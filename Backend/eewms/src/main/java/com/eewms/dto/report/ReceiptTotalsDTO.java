package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTotalsDTO {
    private long receiptCount;
    private int totalQuantity;
    private BigDecimal totalAmount;
}
