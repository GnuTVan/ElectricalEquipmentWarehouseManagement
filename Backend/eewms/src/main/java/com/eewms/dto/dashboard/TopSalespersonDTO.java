package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopSalespersonDTO {
    private Long userId;
    private String userName;
    private BigDecimal totalSales;
    private long orderCount;
}
