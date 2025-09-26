package com.eewms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private BigDecimal quantity;
}
