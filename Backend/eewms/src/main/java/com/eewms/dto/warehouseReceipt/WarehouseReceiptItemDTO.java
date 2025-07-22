package com.eewms.dto.warehouseReceipt;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseReceiptItemDTO {
    private Long productId;
    private Integer quantity;
    private BigDecimal price;        // Giá nhập hàng thực tế
    private Integer actualQuantity;
}
