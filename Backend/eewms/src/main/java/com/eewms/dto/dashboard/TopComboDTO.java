package com.eewms.dto.dashboard;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopComboDTO {
    private Long comboId;
    private String comboName;
    private long totalQuantity;
}
