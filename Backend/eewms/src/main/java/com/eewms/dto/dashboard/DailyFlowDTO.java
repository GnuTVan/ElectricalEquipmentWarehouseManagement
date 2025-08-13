package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyFlowDTO {
    private LocalDate day;
    private long receiptQty;
    private long issueQty;
    private BigDecimal receiptAmount;
    private BigDecimal issueAmount;
}