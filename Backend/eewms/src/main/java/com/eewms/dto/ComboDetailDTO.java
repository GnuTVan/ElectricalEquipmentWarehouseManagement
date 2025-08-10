package com.eewms.dto;

import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboDetailDTO {
    private Long comboId;
    private Integer productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private Integer availableQuantity; // tồn kho hiện tại
}