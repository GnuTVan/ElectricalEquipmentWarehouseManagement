package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopCustomerDTO {
    private Long customerId;
    private String customerName;
    private BigDecimal totalSales;
    private long orderCount;
}
