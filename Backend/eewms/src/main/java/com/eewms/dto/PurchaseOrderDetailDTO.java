package com.eewms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDetailDTO {
    private Long poDetailId;
    private Long materialId;
    private String productCode;
    private String productName;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private Integer remainingQuantity;
}
