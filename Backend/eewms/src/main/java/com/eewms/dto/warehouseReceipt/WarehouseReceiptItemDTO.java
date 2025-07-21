package com.eewms.dto.warehouseReceipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseReceiptItemDTO {
    private Long productId;
    private Integer quantity;
}
