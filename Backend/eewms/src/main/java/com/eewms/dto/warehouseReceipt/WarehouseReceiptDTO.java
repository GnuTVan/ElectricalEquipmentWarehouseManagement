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

    @NotNull(message = "Vui lòng chọn kho")
    private Long warehouseId;

    private String note;

    private LocalDateTime createdAt;

    private String createdByName;

    private List<WarehouseReceiptItemDTO> items;
}
