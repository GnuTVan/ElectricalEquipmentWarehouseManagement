package com.eewms.dto;

import lombok.*;
import java.math.BigDecimal;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDTO {

    @NotNull
    private Integer productId;

    private String productCode;
    private String productName;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    private BigDecimal price;

    @NotNull(message = "Số lượng đặt không được để trống")
    @Min(value = 1, message = "Số lượng đặt phải lớn hơn 0")
    private Integer orderedQuantity;

    private Integer availableQuantity;
}
