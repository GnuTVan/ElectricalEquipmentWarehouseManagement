package com.eewms.services.impl;

import com.eewms.dto.inventory.WarehouseStockDetailDTO;
import com.eewms.dto.inventory.WarehouseStockRowDTO;
import com.eewms.entities.Product;
import com.eewms.entities.ProductWarehouseStock;
import com.eewms.entities.Warehouse;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.ProductWarehouseStockRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements IInventoryService {

    private final ProductWarehouseStockRepository pwsRepo;
    private final ProductRepository productRepo;
    private final WarehouseRepository warehouseRepo;

    /**
     * Danh sách tồn theo 1 kho (phân trang).
     * Lọc theo keyword (mã/tên SP) và ORDER BY đã xử lý ở Repository.
     */
    @Override
    public Page<WarehouseStockRowDTO> listStockByWarehouse(Integer warehouseId, String keyword, Pageable pageable) {
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new NoSuchElementException("Warehouse not found: " + warehouseId));

        return pwsRepo.pageCatalogWithStockAtWarehouse(warehouseId, keyword, pageable);

    }

    /**
     * Chi tiết phân bổ của 1 sản phẩm qua các kho (list).
     * ORDER BY theo tên kho đã xử lý ở Repository.
     */
    @Override
    public List<WarehouseStockDetailDTO> detailsByProduct(Long productId) {
        Product product = productRepo.findById(Math.toIntExact(productId))
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        List<ProductWarehouseStock> list =
                pwsRepo.findByProductOrderByWarehouseNameAsc(product);

        return list.stream()
                .map(this::toDetailDTO)
                .toList();
    }

    /**
     * Tồn on-hand của 1 sản phẩm tại 1 kho (Optional).
     */
    @Override
    public Optional<BigDecimal> getOnHand(Long productId, Integer warehouseId) {
        Product product = productRepo.findById(Math.toIntExact(productId))
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new NoSuchElementException("Warehouse not found: " + warehouseId));

        return pwsRepo.findByProductAndWarehouse(product, warehouse)
                .map(pws -> nz(pws.getQuantity()));
    }



    // ----------------- Mappers -----------------

    private WarehouseStockRowDTO toRowDTO(ProductWarehouseStock pws) {
        Product p = pws.getProduct();
        return new WarehouseStockRowDTO(
                p.getId(),
                nz(p.getCode()),
                nz(p.getName()),
                nz(pws.getQuantity())
        );
    }

    private WarehouseStockDetailDTO toDetailDTO(ProductWarehouseStock pws) {
        Warehouse w = pws.getWarehouse();
        return new WarehouseStockDetailDTO(
                w.getId(),
                String.valueOf(w.getId()),       // tạm dùng id làm "code" (Warehouse entity không có field code)
                nz(w.getName()),
                nz(pws.getQuantity())
        );
    }

    // ----------------- Helpers -----------------

    private static String nz(String s) { return s == null ? "" : s; }
    private static BigDecimal nz(BigDecimal i) { return i == null ? BigDecimal.valueOf(0) : i; }
}
