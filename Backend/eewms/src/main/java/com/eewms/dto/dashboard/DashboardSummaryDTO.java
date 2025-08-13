package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDTO {
    private long receiptCount;
    private BigDecimal receiptAmount;

    private long issueCount;
    private BigDecimal issueAmount;

    private long qtyIn;
    private long qtyOut;
    private long netQty;

    private BigDecimal inventoryValue;
    private int lowStockCount;
    private int pendingOrdersCount;
}
