package com.eewms.dto.inventory;

import com.eewms.constant.InventoryCountStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCountDTO {
    private Integer id;
    private String code;
    private InventoryCountStatus status;

    private Integer warehouseId;
    private String warehouseName;

    private Long staffId;
    private String staffName;

    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InventoryCountItemDTO> items;

    // 👉 thêm field này để dùng cho list
    private Integer totalExpected;

    private Integer totalVariance;

}

