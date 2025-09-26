package com.eewms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một dòng tồn của MỘT sản phẩm tại MỘT kho.
 * Dùng cho trang: /admin/inventory/warehouses/{id}/stock
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class WarehouseStockRowDTO {
    private Integer productId;
    private String productCode;
    private String productName;
    private BigDecimal quantity;
}
