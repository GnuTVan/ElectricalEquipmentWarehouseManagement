package com.eewms.dto.purchase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItemDTO {

    private Long id;

    @NotNull(message = "Sản phẩm không được để trống")
    private Integer productId;

    @NotNull(message = "Số lượng theo hợp đồng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer contractQuantity;

    private Integer actualQuantity; // Có thể null nếu chưa nhận

    @NotNull(message = "Giá nhập không được để trống")
    private BigDecimal price;
}
