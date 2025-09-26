package com.eewms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class StockFlatDTO {
    private Integer warehouseId;
    private Integer productId;
    private BigDecimal quantity;
    //Dùng để preload toàn bộ tồn kho (warehouseId, productId, quantity) của chuyển kho
}
