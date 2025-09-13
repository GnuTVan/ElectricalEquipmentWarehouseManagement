package com.eewms.services.impl;

import com.eewms.constant.ProductCondition;
import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IWarehouseReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseReceiptServiceImpl implements IWarehouseReceiptService {

    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final ProductRepository productRepository;

    // cần để cập nhật lũy kế actualQuantity & trạng thái PO
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    private final com.eewms.repository.returning.SalesReturnRepository salesReturnRepository;
    private final WarehouseRepository warehouseRepository;
    private final com.eewms.repository.ProductWarehouseStockRepository stockRepo;
    /**
     * Tạo GRN cho 1 đợt giao từ PurchaseOrder.
     * - Không yêu cầu kho đích (warehouse có thể null).
     * - Một PO có thể có N GRN (bỏ chặn existsByPurchaseOrder).
     * - Kiểm tra không vượt hợp đồng theo từng product (tính cả lũy kế đã nhập).
     * - Cộng tồn tổng (Product.quantity) theo số thực nhập.
     * - Cập nhật actualQuantity lũy kế trên PurchaseOrderItem.
     * - Tự động trạng thái PO: DA_GIAO_MOT_PHAN / HOAN_THANH.
     * - Idempotent theo dto.requestId (nếu trùng thì bỏ qua).
     */
    @Override
    @Transactional
    public void saveReceipt(WarehouseReceiptDTO dto, PurchaseOrder order, User user) {
        // 🚀 Luôn reload để có đầy đủ supplier + items + product
        PurchaseOrder po = purchaseOrderRepository.findWithDetailById(order.getId())
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        if (po.getStatus() == PurchaseOrderStatus.HUY || po.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
            throw new InventoryException("Trạng thái đơn hiện tại không cho phép nhập kho");
        }
        if (po.getStatus() == PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Đơn hàng chưa được duyệt");
        }

        // ✅ idempotent theo requestId
        String requestId = (dto.getRequestId() != null && !dto.getRequestId().isBlank())
                ? dto.getRequestId()
                : java.util.UUID.randomUUID().toString();
        if (warehouseReceiptRepository.findByRequestId(requestId).isPresent()) {
            return; // đã tạo rồi → bỏ qua
        }

        // map POItem theo productId
        Map<Integer, PurchaseOrderItem> poItemByProductId = po.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), i -> i));

        // ===== Validate không vượt hợp đồng =====
        for (WarehouseReceiptItemDTO line : (dto.getItems() == null ? List.<WarehouseReceiptItemDTO>of() : dto.getItems())) {
            int deliver = toNonNegative(line.getActualQuantity() != null ? line.getActualQuantity() : line.getQuantity());
            if (deliver <= 0) continue;

            Integer productId = (line.getProductId() == null) ? null : line.getProductId().intValue();
            if (productId == null) throw new InventoryException("Thiếu productId");

            PurchaseOrderItem poItem = poItemByProductId.get(productId);
            if (poItem == null) throw new InventoryException("Sản phẩm không thuộc PO: productId=" + productId);

            int contract = toNonNegative(poItem.getContractQuantity());
            Integer receivedBefore = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(po.getId(), productId);
            if (receivedBefore == null) receivedBefore = 0;

            if (receivedBefore + deliver > contract) {
                throw new InventoryException("Giao vượt hợp đồng (ID=" + productId +
                        "). Đã nhận: " + receivedBefore + ", giao thêm: " + deliver + ", HĐ: " + contract);
            }
        }

        // ===== Tạo phiếu nhập kho =====
        WarehouseReceipt receipt = WarehouseReceipt.builder()
                .code(generateCode())
                .purchaseOrder(po)
                .warehouse(null) // bỏ kho tổng
                .note(dto.getNote())
                .createdAt(LocalDateTime.now())
                .createdBy(user != null ? user.getFullName() : "SYSTEM")
                .requestId(requestId)
                .build();
        warehouseReceiptRepository.save(receipt);

        // ===== Lưu item + cộng tồn + cập nhật lũy kế =====
        for (WarehouseReceiptItemDTO line : (dto.getItems() == null ? List.<WarehouseReceiptItemDTO>of() : dto.getItems())) {
            int deliver = toNonNegative(line.getActualQuantity() != null ? line.getActualQuantity() : line.getQuantity());
            if (deliver <= 0) continue;

            Integer productId = (line.getProductId() == null) ? null : line.getProductId().intValue();
            if (productId == null) continue;

            PurchaseOrderItem poItem = poItemByProductId.get(productId);
            if (poItem == null) continue;

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new InventoryException("Không tìm thấy sản phẩm id=" + productId));

            Warehouse warehouse = warehouseRepository.findById(line.getWarehouseId())
                    .orElseThrow(() -> new InventoryException("Không tìm thấy kho id=" + line.getWarehouseId()));

            WarehouseReceiptItem gri = WarehouseReceiptItem.builder()
                    .warehouseReceipt(receipt)
                    .product(product)
                    .warehouse(warehouse)   // ✅ kho đích
                    .quantity(deliver)
                    .actualQuantity(deliver)
                    .price(poItem.getPrice())
                    .condition(ProductCondition.NEW)
                    .build();
            warehouseReceiptItemRepository.save(gri);

            // cộng tồn tổng
            product.setQuantity(product.getQuantity() + deliver);
            productRepository.save(product);

            // cộng tồn theo kho
            ProductWarehouseStock stock = stockRepo.findByProductAndWarehouse(product, warehouse)
                    .orElse(ProductWarehouseStock.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .quantity(0)
                            .build());
            stock.setQuantity(stock.getQuantity() + deliver);
            stockRepo.save(stock);

            // cập nhật actual lũy kế trên POItem
            Integer receivedAfter = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(po.getId(), productId);
            if (receivedAfter == null) receivedAfter = deliver;
            poItem.setActualQuantity(receivedAfter);
            purchaseOrderItemRepository.save(poItem);
        }

        // ===== Trạng thái PO =====
        boolean allDone = po.getItems().stream()
                .allMatch(it -> toNonNegative(it.getActualQuantity()) >= toNonNegative(it.getContractQuantity()));
        po.setStatus(allDone ? PurchaseOrderStatus.HOAN_THANH : PurchaseOrderStatus.DA_GIAO_MOT_PHAN);

        try {
            purchaseOrderRepository.save(po);
        } catch (OptimisticLockingFailureException e) {
            throw new InventoryException("Đơn hàng vừa được cập nhật bởi người khác, vui lòng tải lại.");
        }
    }


    private String generateCode() {
        long count = warehouseReceiptRepository.count() + 1;
        return String.format("RN%05d", count);
    }

    private int toNonNegative(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    @Override
    @Transactional
    public WarehouseReceipt createFromSalesReturn(Long salesReturnId, String createdByUsername) {
        var sr = salesReturnRepository.findById(salesReturnId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng: " + salesReturnId));
        if (sr.getItems() == null || sr.getItems().isEmpty()) {
            throw new IllegalStateException("Phiếu hoàn hàng không có dòng nào.");
        }

        // Idempotent theo requestId = "SRN-{id}"
        String requestId = "SRN-" + sr.getId();
        if (warehouseReceiptRepository.findByRequestId(requestId).isPresent()) {
            // đã tạo rồi → return lại GRN cũ
            return warehouseReceiptRepository.findByRequestId(requestId).get();
        }

        WarehouseReceipt receipt = WarehouseReceipt.builder()
                .code(generateCode())
                .purchaseOrder(null)
                .warehouse(null) // chưa chọn kho đích → giữ null như hiện tại
                .note("Nhập từ hoàn hàng " + sr.getCode())
                .createdAt(LocalDateTime.now())
                .createdBy(createdByUsername)
                .requestId(requestId)
                .build();
        warehouseReceiptRepository.save(receipt);

        // Lưu item + cộng tồn (condition=RETURNED)
        for (var line : sr.getItems()) {
            var product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new InventoryException("Không tìm thấy sản phẩm id=" + line.getProduct().getId()));

            int qty = Math.max(0, line.getQuantity());
            if (qty <= 0) continue;

            var gri = WarehouseReceiptItem.builder()
                    .warehouseReceipt(receipt)
                    .product(product)
                    .quantity(qty)
                    .actualQuantity(qty)
                    .price(line.getUnitPrice() == null ? java.math.BigDecimal.ZERO : line.getUnitPrice())
                    .condition(com.eewms.constant.ProductCondition.RETURNED)
                    .build();
            warehouseReceiptItemRepository.save(gri);

            // cộng tồn tổng
            product.setQuantity((product.getQuantity() == null ? 0 : product.getQuantity()) + qty);
            productRepository.save(product);
        }

        return receipt;
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseReceiptDTO getViewDTO(Long id) {
        var wr = warehouseReceiptRepository.findByIdWithView(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập: " + id));

        return WarehouseReceiptDTO.builder()
                .id(wr.getId())
                .code(wr.getCode())
                .purchaseOrderId(wr.getPurchaseOrder() != null ? wr.getPurchaseOrder().getId() : null)
                .purchaseOrderCode(wr.getPurchaseOrder() != null ? wr.getPurchaseOrder().getCode() : null)
                .warehouseName(wr.getWarehouse() != null ? wr.getWarehouse().getName() : null)
                .note(wr.getNote())
                .createdAt(wr.getCreatedAt())
                .createdByName(wr.getCreatedBy())
                .requestId(wr.getRequestId())
                .items((wr.getItems() == null ? List.<WarehouseReceiptItem>of() : wr.getItems())
                        .stream()
                        .map(it -> WarehouseReceiptItemDTO.builder()
                                .productId(it.getProduct() != null ? it.getProduct().getId() : null)
                                .productName(it.getProduct() != null ? it.getProduct().getName() : null)
                                .quantity(it.getQuantity())
                                .price(it.getPrice())
                                .actualQuantity(it.getActualQuantity())
                                .build()
                        ).toList())
                .build();
    }

}
