package com.eewms.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrderDetailDTO {
    private Integer productId;
    private String productCode;
    private String productName;
    private BigDecimal price;
    private Integer orderedQuantity;
    private Integer availableQuantity;
}

