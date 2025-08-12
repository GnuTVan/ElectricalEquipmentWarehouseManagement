package com.eewms.services.impl;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SaleOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.*;
import com.eewms.services.ISaleOrderService;
import com.eewms.services.IWarehouseReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eewms.entities.SaleOrder;
import com.eewms.services.ISaleOrderService;
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

    private final SaleOrderRepository saleOrderRepository;
    private final ISaleOrderService saleOrderService;
    @Transactional
    @Override
    public void saveReceipt(WarehouseReceiptDTO dto, PurchaseOrder order, User user) {
        // 1. Tạo phiếu nhập kho
        if (warehouseReceiptRepository.existsByPurchaseOrder(order)) {
            throw new RuntimeException("Đơn hàng đã được nhập kho trước đó.");
        }
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
//        tryUpdateSaleOrdersAfterReceipt();
    }

//    @Transactional
//    protected void tryUpdateSaleOrdersAfterReceipt() {
//        // Lấy tất cả đơn đang PROCESSING
//        List<SaleOrder> processingOrders = saleOrderRepository.findByStatus(SaleOrder.SaleOrderStatus.PROCESSING);
//        if (processingOrders.isEmpty()) return;
//
//        for (SaleOrder so : processingOrders) {
//            boolean enough = true;
//
//            // Duyệt từng dòng sản phẩm trong đơn
//            for (SaleOrderDetail d : so.getDetails()) {
//                Product product = d.getProduct();
//
//                // Tìm tồn kho hiện tại cho sản phẩm (ở kho mặc định/ kho bán)
//                // Nếu hệ thống bạn đang bán từ 1 kho duy nhất, lấy kho đó.
//                // Nếu nhiều kho, bạn có thể lấy tổng tồn tất cả kho.
//                ProductWarehouseStock stock = productWarehouseStockRepository
//                        .findByProductAndWarehouse(product, /* TODO: kho bán của bạn */ so.getWarehouse());
//
//                int available = (stock != null ? stock.getQuantity() : 0);
//
//                // Nếu có logic "đã giao một phần", thay d.getQuantity() bằng (d.getQuantity() - d.getDeliveredQty())
//                if (available < d.getQuantity()) {
//                    enough = false;
//                    break;
//                }
//            }
//
//            // Nếu mọi sản phẩm đều đủ tồn => chuyển PENDING
//            if (enough) {
//                saleOrderService.updateOrderStatus(so.getId(), SaleOrder.SaleOrderStatus.PENDING);
//            }
//        }
//    }


    private String generateCode() {
        long count = warehouseReceiptRepository.count() + 1;
        return String.format("RN%05d", count); // VD: RN00001
    }



}
