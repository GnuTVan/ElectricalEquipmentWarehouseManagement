package com.eewms.dto.dashboard;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentActivityDTO {
    @Getter
    public enum Type {
        RECEIPT("Nhập kho"),
        ISSUE("Xuất kho");
        private final String label;

        Type(String label) {
            this.label = label;
        }
    }

    private Type type;
    private String code;
    private LocalDateTime dateTime;
    private String partnerName; // NCC hoặc Khách hàng
    private BigDecimal amount;
}
