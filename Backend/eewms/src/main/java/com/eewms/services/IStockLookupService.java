package com.eewms.services;

import java.util.List;
import java.util.Map;

public interface IStockLookupService {

    /**
     * Lấy warehouseId của user hiện tại (ưu tiên supervisor, sau đó staff).
     */
    Integer resolveWarehouseIdForCurrentUser();

    /**
     * Lấy warehouseId theo userId (ưu tiên supervisor, sau đó staff).
     */
    Integer resolveWarehouseIdForUser(Long userId);

    /**
     * Map productId -> stockQty tại 1 kho (fallback empty nếu warehouseId = null).
     */
    Map<Integer, Integer> getStockByProductAtWarehouse(Integer warehouseId);

    /**
     * Build list products cho view (quantity = tồn theo kho).
     */
    List<Map<String, Object>> buildProductListWithStock(Integer warehouseId);

    /**
     * Tên kho của user hiện tại (null nếu chưa gán).
     */
    String resolveWarehouseNameForCurrentUser();
}
