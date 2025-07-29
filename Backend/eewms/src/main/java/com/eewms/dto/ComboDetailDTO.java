package com.eewms.dto;

import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboDetailDTO {
    private Integer productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
}