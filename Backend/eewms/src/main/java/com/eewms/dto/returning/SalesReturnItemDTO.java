package com.eewms.dto.returning;

import com.eewms.constant.ReturnReason;
import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesReturnItemDTO {
    private Long productId;
    private String productName; // optional (hiển thị)
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private String note;
    private ReturnReason reason;
}
