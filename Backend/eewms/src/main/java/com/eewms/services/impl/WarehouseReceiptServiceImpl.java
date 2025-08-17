package com.eewms.services.impl;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IWarehouseReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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
        if (order == null) throw new InventoryException("Không tìm thấy đơn hàng");
        if (order.getStatus() == PurchaseOrderStatus.HUY || order.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
            throw new InventoryException("Trạng thái đơn hiện tại không cho phép nhập kho");
        }
        if (order.getStatus() == PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Đơn hàng chưa được duyệt");
        }

        // ✅ idempotent theo requestId từ DTO
        String requestId = (dto.getRequestId() != null && !dto.getRequestId().isBlank())
                ? dto.getRequestId()
                : java.util.UUID.randomUUID().toString();
        if (warehouseReceiptRepository.findByRequestId(requestId).isPresent()) {
            return; // đã tạo rồi → bỏ qua
        }

        // map POItem theo productId (Integer)
        java.util.Map<Integer, PurchaseOrderItem> poItemByProductId = order.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getProduct().getId(), i -> i));

        // ===== Validate không vượt hợp đồng =====
        for (WarehouseReceiptItemDTO line : (dto.getItems() == null ? java.util.List.<WarehouseReceiptItemDTO>of() : dto.getItems())) {
            int deliver = toNonNegative(line.getActualQuantity() != null ? line.getActualQuantity() : line.getQuantity());
            if (deliver <= 0) continue;

            Integer productId = (line.getProductId() == null) ? null : line.getProductId().intValue(); // nếu DTO là Long
            if (productId == null) throw new InventoryException("Thiếu productId");

            PurchaseOrderItem poItem = poItemByProductId.get(productId);
            if (poItem == null) throw new InventoryException("Sản phẩm không thuộc PO: productId=" + productId);

            int contract = toNonNegative(poItem.getContractQuantity());
            Integer receivedBefore = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(order.getId(), productId);
            if (receivedBefore == null) receivedBefore = 0;

            if (receivedBefore + deliver > contract) {
                throw new InventoryException("Giao vượt hợp đồng (ID=" + productId +
                        "). Đã nhận: " + receivedBefore + ", giao thêm: " + deliver + ", HĐ: " + contract);
            }
        }

        // ===== Tạo GRN =====
        WarehouseReceipt receipt = WarehouseReceipt.builder()
                .code(generateCode())
                .purchaseOrder(order)
                .warehouse(null) // bỏ kho đích
                .note(dto.getNote())
                .createdAt(java.time.LocalDateTime.now())
                .createdBy(user != null ? user.getFullName() : "SYSTEM")
                .requestId(requestId)
                .build();
        warehouseReceiptRepository.save(receipt);

        // ===== Lưu item + cộng tồn + cập nhật lũy kế =====
        for (WarehouseReceiptItemDTO line : (dto.getItems() == null ? java.util.List.<WarehouseReceiptItemDTO>of() : dto.getItems())) {
            int deliver = toNonNegative(line.getActualQuantity() != null ? line.getActualQuantity() : line.getQuantity());
            if (deliver <= 0) continue;

            Integer productId = (line.getProductId() == null) ? null : line.getProductId().intValue();
            if (productId == null) continue;

            PurchaseOrderItem poItem = poItemByProductId.get(productId);
            if (poItem == null) continue;

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new InventoryException("Không tìm thấy sản phẩm id=" + productId));

            WarehouseReceiptItem gri = WarehouseReceiptItem.builder()
                    .warehouseReceipt(receipt)
                    .product(product)
                    .quantity(deliver)
                    .actualQuantity(deliver)
                    .price(poItem.getPrice())
                    .build();
            warehouseReceiptItemRepository.save(gri);

            // cộng tồn tổng
            product.setQuantity(product.getQuantity() + deliver);
            productRepository.save(product);

            // cập nhật actual lũy kế trên POItem
            Integer receivedAfter = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(order.getId(), productId);
            if (receivedAfter == null) receivedAfter = deliver;
            poItem.setActualQuantity(receivedAfter);
            purchaseOrderItemRepository.save(poItem);
        }

        // ===== Trạng thái PO =====
        boolean allDone = order.getItems().stream()
                .allMatch(it -> toNonNegative(it.getActualQuantity()) >= toNonNegative(it.getContractQuantity()));
        order.setStatus(allDone ? PurchaseOrderStatus.HOAN_THANH : PurchaseOrderStatus.DA_GIAO_MOT_PHAN);

        try {
            purchaseOrderRepository.save(order);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
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
}
