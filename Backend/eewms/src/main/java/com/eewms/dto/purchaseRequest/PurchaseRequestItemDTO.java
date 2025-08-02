package com.eewms.dto.purchaseRequest;

import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantityNeeded;
    private Long suggestedSupplierId;
    private String suggestedSupplierName;
    private String note;
}