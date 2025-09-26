package com.eewms.dto.inventory;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountItemDTO {
    private Integer id;       // đổi sang Integer
    private Integer productId; // đổi sang Integer
    private String productCode;
    private String productName;
    private Integer expectedQty;
    private Integer countedQty;
    private Integer variance;
    private String note;
}
