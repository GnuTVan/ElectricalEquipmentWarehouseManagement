package com.eewms.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodIssueDetailDTO {
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal total;
}
