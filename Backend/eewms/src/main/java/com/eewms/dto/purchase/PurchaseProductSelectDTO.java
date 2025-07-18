package com.eewms.dto.purchase;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseProductSelectDTO {

    private Integer id;
    private String name;
    private BigDecimal originPrice;
}
