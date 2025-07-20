package com.eewms.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailDTO {
    private String productCode;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
}
