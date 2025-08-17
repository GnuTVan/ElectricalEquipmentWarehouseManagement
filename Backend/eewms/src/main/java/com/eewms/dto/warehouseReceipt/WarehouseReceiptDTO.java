package com.eewms.dto.warehouseReceipt;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseReceiptDTO {

    private Long id;

    @NotNull(message = "Vui lòng chọn đơn hàng")
    private Long purchaseOrderId;

    // ❌ BỎ nếu bạn không còn dùng kho đích:
    // private Long warehouseId;

    private String note;

    private LocalDateTime createdAt;
    private String createdByName;

    // ✅ thêm để idempotent
    private String requestId;

    private List<WarehouseReceiptItemDTO> items;
}
