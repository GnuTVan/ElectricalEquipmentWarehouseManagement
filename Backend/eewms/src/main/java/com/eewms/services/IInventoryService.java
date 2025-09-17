package com.eewms.services;

import com.eewms.dto.inventory.WarehouseStockDetailDTO;
import com.eewms.dto.inventory.WarehouseStockRowDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service tồn kho đa kho (giai đoạn này: chỉ hiển thị).
 */
public interface IInventoryService {

    /**
     * Danh sách tồn của từng sản phẩm trong một kho (phân trang).
     * Lọc keyword & sắp xếp thực hiện ở Repository.
     */
    Page<WarehouseStockRowDTO> listStockByWarehouse(Integer warehouseId, String keyword, Pageable pageable);

    /**
     * Chi tiết "kho -> số lượng" cho một sản phẩm qua tất cả kho (list).
     * Sắp xếp thực hiện ở Repository.
     */
    List<WarehouseStockDetailDTO> detailsByProduct(Long productId);

    /**
     * Số lượng on-hand của một sản phẩm tại một kho (Optional).
     */
    Optional<Integer> getOnHand(Long productId, Integer warehouseId);
}
