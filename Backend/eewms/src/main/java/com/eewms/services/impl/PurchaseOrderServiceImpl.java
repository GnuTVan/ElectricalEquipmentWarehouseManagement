package com.eewms.services.impl;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.purchase.PurchaseOrderMapper;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IPurchaseOrderService;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements IPurchaseOrderService {

    private final PurchaseOrderRepository orderRepo;
    private final PurchaseOrderItemRepository itemRepo;
    private final SupplierRepository supplierRepo;
    private final ProductRepository productRepo;
    private final ImageUploadService uploadService;
    private final WarehouseRepository warehouseRepository;
    private final ProductWarehouseStockRepository stockRepo;
    // Repos thêm cho nghiệp vụ đợt nhập (GRN)
    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;

    /* -----------------------------------------------------------
     * TẠO PO (set status theo ROLE): Manager -> CHO_GIAO_HANG, Staff -> CHO_DUYET
     * ----------------------------------------------------------- */
    @Override
    @Transactional
    public PurchaseOrder create(PurchaseOrderDTO dto) throws Exception {
        Supplier supplier = supplierRepo.findById(dto.getSupplierId())
                .orElseThrow(() -> new InventoryException("Nhà cung cấp không tồn tại"));

        String attachmentUrl = null;
        if (dto.getAttachmentFile() != null && !dto.getAttachmentFile().isEmpty()) {
            attachmentUrl = uploadService.uploadImage(dto.getAttachmentFile());
        }

        PurchaseOrder order = PurchaseOrderMapper.toEntity(dto, supplier, attachmentUrl);
        order.setCreatedByName(dto.getCreatedByName());
        order.setCode(generateOrderCode());
        order.setStatus(isCurrentUserManager() ? PurchaseOrderStatus.CHO_GIAO_HANG : PurchaseOrderStatus.CHO_DUYET);

        // Lấy & validate sản phẩm thuộc NCC
        List<Integer> productIds = dto.getItems().stream()
                .filter(Objects::nonNull)
                .map(PurchaseOrderItemDTO::getProductId)
                .collect(Collectors.toList());

        List<Product> products = productRepo.findAllById(productIds);
        boolean allBelong = products.stream().allMatch(p ->
                p.getSuppliers() != null &&
                        p.getSuppliers().stream().anyMatch(s -> s.getId().equals(dto.getSupplierId()))
        );
        if (!allBelong) {
            throw new InventoryException("Có sản phẩm không thuộc nhà cung cấp đã chọn!");
        }

        // Tạo items + tổng tiền hợp đồng
        List<PurchaseOrderItem> items = PurchaseOrderMapper.toItemEntities(dto.getItems(), order, products);
        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getContractQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(total);
        order.setItems(items);

        return orderRepo.save(order);
    }

    /* -----------------------------------------------------------
     * DUYỆT PO: CHO_DUYET -> CHO_GIAO_HANG
     * ----------------------------------------------------------- */
    @Override
    @Transactional
    public PurchaseOrder approve(Long poId, String approverName) {
        if (!isCurrentUserManager()) throw new InventoryException("Chỉ Manager được phép duyệt đơn hàng.");

        PurchaseOrder po = orderRepo.findById(poId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        if (po.getStatus() != PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Chỉ duyệt đơn ở trạng thái CHO_DUYET");
        }

        po.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);
        po.setApprovedByName(approverName);
        po.setApprovedAt(LocalDateTime.now());

        return orderRepo.save(po);
    }

    /* -----------------------------------------------------------
     * HỦY PO: chỉ khi chưa phát sinh GRN
     * ----------------------------------------------------------- */
    @Override
    @Transactional
    public PurchaseOrder cancel(Long poId, String reason, String actorName) {
        if (!isCurrentUserManager()) throw new InventoryException("Chỉ Manager được phép hủy đơn hàng.");

        if (reason == null || reason.isBlank()) {
            throw new InventoryException("Vui lòng nhập lý do hủy đơn");
        }
        PurchaseOrder po = orderRepo.findById(poId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        // Kiểm tra đã có GRN?
        boolean hasAnyGrn = warehouseReceiptRepository.findAll().stream()
                .anyMatch(r -> r.getPurchaseOrder() != null && r.getPurchaseOrder().getId().equals(poId));
        if (hasAnyGrn) {
            throw new InventoryException("Đơn hàng đã phát sinh nhập kho, không thể hủy");
        }

        po.setStatus(PurchaseOrderStatus.HUY);
        po.setCanceledByName(actorName);
        po.setCanceledAt(LocalDateTime.now());
        po.setCancelReason(reason);

        return orderRepo.save(po);
    }

    /* -----------------------------------------------------------
     * NHẬN HÀNG THEO ĐỢT (tạo GRN + cộng tồn + cập nhật actual + auto status)
     * Idempotent theo requestId
     * ----------------------------------------------------------- */
    @Override
    @Transactional
    public PurchaseOrder receiveDelivery(Long poId,
                                         List<PurchaseOrderItemDTO> deliveryLines,
                                         String actorName,
                                         String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new InventoryException("Thiếu requestId để chống tạo trùng đợt nhập");
        }
        if (warehouseReceiptRepository.findByRequestId(requestId).isPresent()) {
            // Idempotency: đã xử lý rồi -> trả về PO hiện tại
            return orderRepo.findById(poId).orElseThrow();
        }

        PurchaseOrder po = orderRepo.findById(poId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));
        if (po.getStatus() == PurchaseOrderStatus.HUY || po.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
            throw new InventoryException("Trạng thái đơn không cho phép nhận hàng");
        }
        if (po.getStatus() == PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Đơn hàng chưa được duyệt");
        }

        // Map POItem theo productId
        Map<Integer, PurchaseOrderItem> poItemByProductId = po.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), i -> i));

        // Validate không vượt hợp đồng
        for (PurchaseOrderItemDTO line : deliveryLines) {
            int qty = line.getDeliveryQuantity() != null ? line.getDeliveryQuantity() : 0;
            if (qty <= 0) continue;

            PurchaseOrderItem poItem = poItemByProductId.get(line.getProductId());
            if (poItem == null) {
                throw new InventoryException("Sản phẩm không thuộc đơn mua: productId=" + line.getProductId());
            }
            int contract = poItem.getContractQuantity();
            Integer receivedBefore = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(poId, line.getProductId());
            if (receivedBefore == null) receivedBefore = 0;

            if (receivedBefore + qty > contract) {
                throw new InventoryException("Giao vượt số lượng hợp đồng cho sản phẩm ID=" + line.getProductId());
            }
        }

        // Tạo 1 GRN cho cả đợt
        WarehouseReceipt grn = WarehouseReceipt.builder()
                .code(generateGrnCode())
                .purchaseOrder(po)
                .createdAt(LocalDateTime.now())
                .createdBy(actorName)
                .note("Nhập đợt từ PO " + po.getCode())
                .requestId(requestId)
                .build();
        warehouseReceiptRepository.save(grn);

        // Lưu GRN items + cộng tồn + cập nhật actual của POItem
        for (PurchaseOrderItemDTO line : deliveryLines) {
            int qty = line.getDeliveryQuantity() != null ? line.getDeliveryQuantity() : 0;
            if (qty <= 0) continue;

            PurchaseOrderItem poItem = poItemByProductId.get(line.getProductId());
            if (poItem == null) continue; // an toàn

            Product product = poItem.getProduct();

            // Tính đã nhận trước đó (trước khi thêm dòng này)
            Integer receivedBefore = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(poId, product.getId());
            if (receivedBefore == null) receivedBefore = 0;

            WarehouseReceiptItem gri = WarehouseReceiptItem.builder()
                    .warehouseReceipt(grn)
                    .product(product)
                    .quantity(qty)               // nếu còn dùng field này
                    .actualQuantity(qty)         // chuẩn thực nhận
                    .price(poItem.getPrice())
                    .condition(com.eewms.constant.ProductCondition.NEW)// lấy giá từ PO
                    .build();
            warehouseReceiptItemRepository.save(gri);

            // Cộng tồn tổng (bỏ đa kho)
            product.setQuantity(product.getQuantity() + qty);
            productRepo.save(product);

            // Cập nhật actualQuantity lũy kế trên POItem
            poItem.setActualQuantity(receivedBefore + qty);
            itemRepo.save(poItem);
        }

        // Auto chuyển trạng thái
        boolean allDone = po.getItems().stream()
                .allMatch(it -> {
                    Integer a = it.getActualQuantity();
                    return a != null && a >= it.getContractQuantity();
                });
        po.setStatus(allDone ? PurchaseOrderStatus.HOAN_THANH : PurchaseOrderStatus.DA_GIAO_MOT_PHAN);

        try {
            return orderRepo.save(po);
        } catch (OptimisticLockingFailureException ex) {
            throw new InventoryException("Dữ liệu vừa được cập nhật bởi người khác, vui lòng tải lại.");
        }
    }

    /* -----------------------------------------------------------
     * GIỮ NGUYÊN: findAll, findById, searchWithFilters, generateOrderCode
     * (Lưu ý: KHÔNG cộng tồn kho trong updateStatus/updateOrder nữa)
     * ----------------------------------------------------------- */
    @Transactional(readOnly = true)
    @Override
    public List<PurchaseOrderDTO> findAll() {
        return orderRepo.findAll().stream()
                .map(PurchaseOrderMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findById(Long id) {
        return orderRepo.findWithDetailById(id); // ✅ thay vì findById(id)
    }

    /** Deprecated trong luồng mới: chỉ dùng để đổi nhãn trạng thái, KHÔNG cộng tồn */
    @Transactional
    @Override
    public void updateStatus(Long id, PurchaseOrderStatus status, PurchaseOrderDTO dto) throws Exception {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));
        // KHÔNG cộng tồn kho tại đây nữa
        order.setStatus(status);
        orderRepo.save(order);
    }

    @Override
    public String generateOrderCode() {
        List<PurchaseOrder> all = orderRepo.findAll();
        long nextNumber = all.stream()
                .map(PurchaseOrder::getCode)
                .filter(code -> code != null && code.matches("P\\d+"))
                .map(code -> Long.parseLong(code.replace("P", "")))
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;

        return String.format("P%05d", nextNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> searchWithFilters(String keyword, PurchaseOrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return orderRepo.searchWithFilters(keyword, status, from, to, pageable)
                .map(PurchaseOrderMapper::toDTO);
    }

    /* -----------------------------------------------------------
     * Helpers
     * ----------------------------------------------------------- */
    private boolean isCurrentUserManager() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return false;
            return auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_MANAGER".equalsIgnoreCase(a.getAuthority()) || "MANAGER".equalsIgnoreCase(a.getAuthority())
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private String generateGrnCode() {
        long count = warehouseReceiptRepository.count() + 1;
        return String.format("RN%05d", count);
    }

    @Override
    @Transactional
    public PurchaseOrder updateBeforeApprove(PurchaseOrderDTO dto) {
        PurchaseOrder po = orderRepo.findById(dto.getId())
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        if (po.getStatus() != PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Chỉ được sửa khi đơn hàng đang CHO_DUYET");
        }

        // Chặn nếu đã phát sinh phiếu nhập
        boolean hasAnyGrn = warehouseReceiptRepository.findAll().stream()
                .anyMatch(r -> r.getPurchaseOrder() != null && Objects.equals(r.getPurchaseOrder().getId(), po.getId()));
        if (hasAnyGrn) {
            throw new InventoryException("Đơn đã phát sinh nhập kho, không thể sửa");
        }

        // Cập nhật NCC
        Supplier supplier = supplierRepo.findById(dto.getSupplierId())
                .orElseThrow(() -> new InventoryException("Nhà cung cấp không tồn tại"));
        po.setSupplier(supplier);

        // Lọc & validate items
        List<PurchaseOrderItemDTO> itemDTOs = Optional.ofNullable(dto.getItems()).orElse(List.of()).stream()
                .filter(i -> i != null && i.getProductId() != null && i.getContractQuantity() != null && i.getContractQuantity() > 0)
                .toList();
        if (itemDTOs.isEmpty()) throw new InventoryException("Vui lòng chọn ít nhất 1 sản phẩm hợp lệ");

        List<Integer> productIds = itemDTOs.stream().map(PurchaseOrderItemDTO::getProductId).toList();
        List<Product> products = productRepo.findAllById(productIds);

        // SP phải thuộc NCC
        boolean allBelong = products.stream().allMatch(p ->
                p.getSuppliers() != null && p.getSuppliers().stream().anyMatch(s -> s.getId().equals(supplier.getId()))
        );
        if (!allBelong) throw new InventoryException("Có sản phẩm không thuộc nhà cung cấp đã chọn");

        // Thay thế toàn bộ items (vì còn CHO_DUYET nên chưa nhận hàng)
        po.getItems().clear();
        List<PurchaseOrderItem> newItems = PurchaseOrderMapper.toItemEntities(itemDTOs, po, products);
        // reset actualQuantity về null/0
        newItems.forEach(i -> i.setActualQuantity(0));
        po.getItems().addAll(newItems);

        // Tính lại tổng tiền
        BigDecimal total = newItems.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getContractQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setTotalAmount(total);

        return orderRepo.save(po);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrder getForEdit(Long id) {
        return orderRepo.findByIdForEdit(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng: " + id));
    }
    @Transactional
    @Override
    public WarehouseReceiptDTO prepareReceipt(Long poId,
                                              List<PurchaseOrderItemDTO> deliveryLines,
                                              String actorName,
                                              String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new InventoryException("Thiếu requestId để chống tạo trùng đợt nhập");
        }

        PurchaseOrder po = orderRepo.findWithDetailById(poId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        if (po.getStatus() == PurchaseOrderStatus.HUY || po.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
            throw new InventoryException("Trạng thái đơn không cho phép nhận hàng");
        }
        if (po.getStatus() == PurchaseOrderStatus.CHO_DUYET) {
            throw new InventoryException("Đơn hàng chưa được duyệt");
        }

        // map để check hợp đồng
        Map<Integer, PurchaseOrderItem> poItemByProductId = po.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), i -> i));

        // validate không vượt hợp đồng
        for (PurchaseOrderItemDTO line : deliveryLines) {
            int qty = line.getDeliveryQuantity() != null ? line.getDeliveryQuantity() : 0;
            if (qty <= 0) continue;

            PurchaseOrderItem poItem = poItemByProductId.get(line.getProductId());
            if (poItem == null) {
                throw new InventoryException("Sản phẩm không thuộc đơn mua: productId=" + line.getProductId());
            }
            int contract = poItem.getContractQuantity();
            Integer receivedBefore = warehouseReceiptItemRepository
                    .sumReceivedByPoAndProduct(poId, line.getProductId());
            if (receivedBefore == null) receivedBefore = 0;

            if (receivedBefore + qty > contract) {
                throw new InventoryException("Giao vượt số lượng hợp đồng cho sản phẩm ID=" + line.getProductId());
            }
        }

        // build DTO để chuyển sang form nhập kho
        return WarehouseReceiptDTO.builder()
                .purchaseOrderId(po.getId())
                .purchaseOrderCode(po.getCode())
                .createdByName(actorName)
                .requestId(requestId)
                .note("Nhập đợt từ PO " + po.getCode())
                .items(deliveryLines.stream()
                        .map(line -> {
                            PurchaseOrderItem poItem = poItemByProductId.get(line.getProductId());
                            return WarehouseReceiptItemDTO.builder()
                                    .productId(line.getProductId())
                                    .productName(poItem.getProduct().getName())
                                    .quantity(line.getDeliveryQuantity())
                                    .actualQuantity(line.getDeliveryQuantity())
                                    .price(poItem.getPrice())
                                    .contractQuantity(poItem.getContractQuantity()) // 👈 thêm dòng này
                                    .build();
                        })
                        .toList())
                .build();
    }
    @Transactional
    @Override
    public WarehouseReceiptDTO prepareFastComplete(Long poId, String actorName, String requestId) {
        PurchaseOrder po = orderRepo.findWithDetailById(poId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        Map<Integer, PurchaseOrderItem> poItemByProductId = po.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), i -> i));

        // chỉ lấy phần còn thiếu
        List<WarehouseReceiptItemDTO> items = po.getItems().stream()
                .map(it -> {
                    int contract = it.getContractQuantity() != null ? it.getContractQuantity() : 0;
                    int actual = it.getActualQuantity() != null ? it.getActualQuantity() : 0;
                    int remain = contract - actual;
                    if (remain <= 0) return null;
                    return WarehouseReceiptItemDTO.builder()
                            .productId(it.getProduct().getId())
                            .productName(it.getProduct().getName())
                            .quantity(remain)
                            .actualQuantity(remain)
                            .price(it.getPrice())
                            .contractQuantity(contract)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return WarehouseReceiptDTO.builder()
                .purchaseOrderId(po.getId())
                .purchaseOrderCode(po.getCode())
                .createdByName(actorName)
                .requestId(requestId)
                .note("Nhập nhanh toàn bộ phần còn lại từ PO " + po.getCode())
                .items(items)
                .build();
    }

}
