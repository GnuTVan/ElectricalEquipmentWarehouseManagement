package com.eewms.dto.returning;

import com.eewms.constant.ReturnReason;
import com.eewms.constant.ReturnStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesReturnDTO {
    private Long id;
    private String code;

    private Integer saleOrderId;
    private String saleOrderCode;

    private String createdBy;
    private LocalDateTime createdAt;

    private ReturnStatus status;
    private ReturnReason reason;     // chỉ HANG_LOI | HANG_HONG
    private String managerNote;
    private boolean needsReplacement;

    private BigDecimal totalAmount;
    private List<SalesReturnItemDTO> items;
}
