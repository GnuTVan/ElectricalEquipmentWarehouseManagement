package com.eewms.services.impl;

import com.eewms.dto.report.WarehouseReceiptReportDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.*;
import com.eewms.services.IWarehouseReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        //lay gia tu don hang goc
            PurchaseOrderItem orderItem = order.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .orElseThrow();

            // 2.1 Lưu chi tiết phiếu nhập
            WarehouseReceiptItem item = WarehouseReceiptItem.builder()
                    .warehouseReceipt(receipt)
                    .product(product)
                    .quantity(itemDTO.getQuantity())
                    // gán actualQuantity và price
                    .actualQuantity(itemDTO.getActualQuantity() != null ? itemDTO.getActualQuantity() : itemDTO.getQuantity())
                    .price(orderItem.getPrice())
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

    @Override
    public List<WarehouseReceiptReportDTO> getReceiptReport(LocalDate fromDate, LocalDate toDate, Long warehouseId, Long supplierId) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAll();

        return receipts.stream()
                .filter(r -> {
                    boolean match = true;
                    if (fromDate != null) {
                        match &= !r.getCreatedAt().toLocalDate().isBefore(fromDate);
                    }
                    if (toDate != null) {
                        match &= !r.getCreatedAt().toLocalDate().isAfter(toDate);
                    }
                    if (warehouseId != null) {
                        match &= r.getWarehouse().getId().equals(warehouseId);
                    }
                    if (supplierId != null) {
                        match &= r.getPurchaseOrder().getSupplier().getId().equals(supplierId);
                    }
                    return match;
                })
                .map(r -> {
                    List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(r);
                    int totalQuantity = items.stream().mapToInt(WarehouseReceiptItem::getActualQuantity).sum();
                    BigDecimal totalAmount = items.stream()
                            .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getActualQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new WarehouseReceiptReportDTO(
                            r.getCode(),
                            r.getCreatedAt(),
                            r.getWarehouse().getName(),
                            r.getPurchaseOrder().getSupplier().getName(),
                            totalQuantity,
                            totalAmount
                    );
                })
                .toList();
    }

    private String generateCode() {
        long count = warehouseReceiptRepository.count() + 1;
        return String.format("RN%05d", count); // VD: RN00001
    }



}
