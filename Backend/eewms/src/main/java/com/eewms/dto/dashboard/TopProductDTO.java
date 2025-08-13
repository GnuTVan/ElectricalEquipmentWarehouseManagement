package com.eewms.dto.dashboard;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopProductDTO {
    private Integer productId;
    private String productName;
    private long totalQuantity;
}
