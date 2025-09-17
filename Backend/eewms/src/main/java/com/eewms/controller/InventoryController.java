package com.eewms.controller;

import com.eewms.dto.inventory.WarehouseStockDetailDTO;
import com.eewms.dto.inventory.WarehouseStockRowDTO;
import com.eewms.entities.Product;
import com.eewms.entities.Warehouse;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final IInventoryService inventoryService;
    private final WarehouseRepository warehouseRepo;
    private final ProductRepository productRepo;

    /**
     * Tồn của 1 kho: bảng Mã SP | Tên SP | Số lượng (phân trang).
     * Ví dụ: /admin/inventory/warehouses/1/stock?keyword=but&page=0&size=20
     */
    @GetMapping("/warehouses/{id}/stock")
    public String warehouseStock(@PathVariable("id") Integer warehouseId,
                                 @RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                 Model model) {

        // Lấy thông tin kho (nếu không có -> 404 đơn giản bằng NoSuchElementException)
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new NoSuchElementException("Warehouse not found: " + warehouseId));

        // Phân trang cơ bản; sort đã xử lý ở truy vấn repo
        Pageable pageable = PageRequest.of(page, size);

        Page<WarehouseStockRowDTO> data =
                inventoryService.listStockByWarehouse(warehouseId, keyword, pageable);

        // Danh sách kho cho dropdown đổi kho (không sort ở controller)
        List<Warehouse> warehouses = warehouseRepo.findAll();

        model.addAttribute("warehouse", warehouse);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("page", data);
        model.addAttribute("keyword", keyword == null ? "" : keyword);

        return "inventory/warehouse-stock";
    }

    /**
     * Breakdown 1 sản phẩm theo các kho.
     * Ví dụ: /admin/inventory/products/101/stock
     */
    @GetMapping("/products/{id}/stock")
    public String productStock(@PathVariable("id") Long productId, Model model) {
        Product product = productRepo.findById(Math.toIntExact(productId))
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        List<WarehouseStockDetailDTO> details = inventoryService.detailsByProduct(productId);

        // Tính tổng on-hand (không sort/loc ở controller)
        int totalOnHand = details.stream()
                .map(dto -> dto.getQuantity() == null ? 0 : dto.getQuantity())
                .reduce(0, Integer::sum);

        model.addAttribute("product", product);
        model.addAttribute("details", details);
        model.addAttribute("totalOnHand", totalOnHand);

        return "inventory/product-stock";
    }
}
