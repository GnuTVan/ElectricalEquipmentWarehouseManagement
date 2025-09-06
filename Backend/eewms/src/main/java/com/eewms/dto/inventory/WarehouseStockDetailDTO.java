package com.eewms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Chi tiết "kho -> số lượng" của MỘT sản phẩm qua CÁC kho.
 * Dùng cho trang: /admin/inventory/products/{id}/stock
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class WarehouseStockDetailDTO {
    private Integer warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Integer quantity;
}
