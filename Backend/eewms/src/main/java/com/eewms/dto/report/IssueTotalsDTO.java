package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class IssueTotalsDTO {
    private long issueCount;
    private int totalQuantity;
    private BigDecimal totalAmount;
    private int totalCombos;
}
