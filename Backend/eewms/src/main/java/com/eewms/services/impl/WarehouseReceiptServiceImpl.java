package com.eewms.services.impl;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.*;
import com.eewms.services.IWarehouseReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WarehouseReceiptServiceImpl implements IWarehouseReceiptService {

    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final ProductWarehouseStockRepository productWarehouseStockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    @Transactional
    @Override
    public void saveReceipt(WarehouseReceiptDTO dto, PurchaseOrder order, User user) {
        // 1. Tạo phiếu nhập kho
        String newCode = generateCode();
        Warehouse warehouse = warehouseRepository.findById(dto.getWarehouseId())
                .orElseThrow();

        WarehouseReceipt receipt = WarehouseReceipt.builder()
                .code(newCode)
                .purchaseOrder(order)
                .warehouse(warehouse)
                .note(dto.getNote())
                .createdAt(LocalDateTime.now())
                .createdBy(user.getFullName())
                .build();
        warehouseReceiptRepository.save(receipt);

        // 2. Lưu từng dòng sản phẩm
        for (WarehouseReceiptItemDTO itemDTO : dto.getItems()) {
            Product product = productRepository.findById(itemDTO.getProductId().intValue())
                    .orElseThrow();

            // 2.1 Lưu chi tiết phiếu nhập
            WarehouseReceiptItem item = WarehouseReceiptItem.builder()
                    .warehouseReceipt(receipt)
                    .product(product)
                    .quantity(itemDTO.getQuantity())
                    .build();
            warehouseReceiptItemRepository.save(item);

            // 2.2 Cập nhật tồn kho
            ProductWarehouseStock stock = productWarehouseStockRepository
                    .findByProductAndWarehouse(product, warehouse)
                    .orElseGet(() -> ProductWarehouseStock.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .quantity(0)
                            .build());

            stock.setQuantity(stock.getQuantity() + itemDTO.getQuantity());
            productWarehouseStockRepository.save(stock);
        }

    }

    private String generateCode() {
        long count = warehouseReceiptRepository.count() + 1;
        return String.format("RN%05d", count); // VD: RN00001
    }

}
