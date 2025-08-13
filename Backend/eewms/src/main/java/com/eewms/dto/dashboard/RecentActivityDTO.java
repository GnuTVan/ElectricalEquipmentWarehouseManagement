package com.eewms.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentActivityDTO {
    public enum Type { RECEIPT, ISSUE }
    private Type type;
    private String code;
    private LocalDateTime dateTime;
    private String partnerName; // NCC hoặc Khách hàng
    private BigDecimal amount;
}
