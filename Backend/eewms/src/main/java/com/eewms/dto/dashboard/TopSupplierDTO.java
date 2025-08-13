package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopSupplierDTO {
    private Long supplierId;
    private String supplierName;
    private BigDecimal totalAmount;
}
