package com.eewms.dto.warehouseReceipt;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseReceiptItemDTO {
    private Integer productId;
    private Integer quantity;
    private String productName;
    private BigDecimal price;        // Giá nhập hàng thực tế
    private Integer actualQuantity;
    private Long warehouseId;
    private String warehouseName;
    private Integer contractQuantity;
}
